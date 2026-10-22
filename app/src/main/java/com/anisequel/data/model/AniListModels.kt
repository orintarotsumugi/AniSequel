package com.anisequel.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class GraphQLResponse<T>(
    val data: T?,
    val errors: List<GraphQLError>? = null
)

@JsonClass(generateAdapter = true)
data class GraphQLError(
    val message: String,
    val status: Int? = null
)

/**
 * The shape AniList uses for an **error** response body.
 *
 * AniList answers a GraphQL error with a non-2xx status *and* the same
 * `{"errors": [...]}` envelope it uses on success, so the body is the only place
 * the reason is written down. Retrofit never hands that body to a suspend
 * function whose return type is not `Response<T>`, though - it throws
 * `HttpException` first - so the repository has to parse this itself to find out
 * what AniList actually objected to.
 */
@JsonClass(generateAdapter = true)
data class AniListErrorEnvelope(
    val errors: List<GraphQLError>? = null
)

/**
 * The response to the reachability probe.
 *
 * `__typename` is a GraphQL meta-field every server answers, so the probe costs
 * no resolver work on AniList's side - it is the cheapest question that still
 * proves a real answer came back rather than a cached page or a proxy's login
 * screen. The value is read only to force Moshi to parse it; a null body with
 * no errors is treated as a malformed answer by the repository.
 */
@JsonClass(generateAdapter = true)
data class ApiStatusData(
    @Json(name = "__typename") val typeName: String?
)

@JsonClass(generateAdapter = true)
data class ViewerData(
    @Json(name = "Viewer") val viewer: ViewerProfile?
)

@JsonClass(generateAdapter = true)
data class UserByNameData(
    @Json(name = "User") val user: ViewerProfile?
)

@JsonClass(generateAdapter = true)
data class ViewerProfile(
    val id: Int,
    val name: String,
    val avatar: UserAvatar? = null,
    val bannerImage: String? = null,
    val statistics: UserStatistics? = null
)

@JsonClass(generateAdapter = true)
data class UserAvatar(
    val medium: String? = null,
    val large: String? = null
)

/**
 * AniList's `UserStatisticTypes`: the per-medium breakdown of a user's list.
 *
 * The type is named `UserStatisticTypes` upstream and is reached as
 * `User.statistics`. It is modelled here under a shorter local name that already
 * existed, because `ViewerProfile.statistics` has pointed at it since the
 * dashboard first read a list count and renaming it would be churn for no
 * behaviour change.
 *
 * [manga] was added with the profile screen, which is the first thing to render
 * a manga list. `anime` and `manga` are the *same* upstream type
 * (`UserStatistics`), so the local `AnimeStats`/`MangaStats` split is only about
 * which fields each side carries: `episodesWatched` and `minutesWatched` are
 * anime-only in practice and `chaptersRead`/`volumesRead` manga-only, and AniList
 * reports zeros rather than nulls for the ones that do not apply.
 *
 * `meanScore` is read from here rather than from `stats.animeListScores` - two
 * sources for one number is how the Stats tab and the header card end up quoting
 * different values.
 *
 * The distributions used to be absent from this type, on the documented belief
 * that AniList answered them with `[]`. That is not true any more (re-checked
 * against the live API on 2026-10-22), and it has to be false in practice: the
 * four `stats.favoured*` fields the app used instead now answer HTTP 500 for the
 * whole document. See [com.anisequel.data.network.GraphQLQueries.GET_USER_OVERVIEW].
 * So `formats`, `releaseYears`, `genres` and `tags` live here now, and the profile
 * screen's charts are built from them.
 */
@JsonClass(generateAdapter = true)
data class UserStatistics(
    val anime: AnimeStats? = null,
    val manga: MangaStats? = null
)

@JsonClass(generateAdapter = true)
data class AnimeStats(
    val count: Int? = null,
    val episodesWatched: Int? = null,
    val minutesWatched: Long? = null,
    /** AniList reports `Float`; Moshi reads a JSON number into a `Double`. */
    val meanScore: Double? = null,
    /**
     * Per-format tallies, replacing `stats.favouredFormats`.
     *
     * [UserFormatStatistic.count] is the entry count the old `amount` was, and it
     * is what the chart draws.
     */
    val formats: List<UserFormatStatistic>? = null,
    /** Per-release-year tallies, replacing `stats.favouredYears`. */
    val releaseYears: List<UserReleaseYearStatistic>? = null,
    /** Per-genre tallies, replacing `stats.favouredGenres`. */
    val genres: List<UserGenreStatistic>? = null,
    /** Per-tag tallies, replacing `stats.favouredTags`. */
    val tags: List<UserTagStatistic>? = null
)

/**
 * One row of `UserStatistics.formats`.
 *
 * `count` rather than `amount` is the API's own name here, unlike the deprecated
 * block's spelling; the mapping to the chart's `amount` happens once, in
 * `UserOverviewViewModel`.
 */
@JsonClass(generateAdapter = true)
data class UserFormatStatistic(
    /** `TV`, `MOVIE`, `OVA`, `ONA`, `SPECIAL`, `TV_SHORT`, `MUSIC` or null. */
    val format: String? = null,
    val count: Int? = null,
    val meanScore: Double? = null
)

@JsonClass(generateAdapter = true)
data class UserReleaseYearStatistic(
    val releaseYear: Int? = null,
    val count: Int? = null,
    val meanScore: Double? = null
)

@JsonClass(generateAdapter = true)
data class UserGenreStatistic(
    val genre: String? = null,
    val count: Int? = null,
    val meanScore: Double? = null,
    val minutesWatched: Long? = null
)

@JsonClass(generateAdapter = true)
data class UserTagStatistic(
    val tag: MediaTag? = null,
    val count: Int? = null,
    val meanScore: Double? = null
)

@JsonClass(generateAdapter = true)
data class MangaStats(
    val count: Int? = null,
    val chaptersRead: Long? = null,
    val volumesRead: Long? = null,
    val meanScore: Double? = null
)

@JsonClass(generateAdapter = true)
data class MediaListCollectionData(
    @Json(name = "MediaListCollection") val collection: MediaListCollection?
)

/** Payload of the on-demand detail query for a single media. */
@JsonClass(generateAdapter = true)
data class MediaDetailData(
    @Json(name = "Media") val media: MediaNode? = null
)

/**
 * Payload of the studio query: one studio, with one page of its work.
 *
 * `Studio` and not `Page`, because AniList's `Page.media` has no `studioId`
 * argument at all - the studio's own `media` connection is the only documented way
 * to ask "everything this studio made". See [GraphQLQueries.getStudioQuery].
 */
@JsonClass(generateAdapter = true)
data class StudioData(
    @Json(name = "Studio") val studio: StudioNode? = null
)

@JsonClass(generateAdapter = true)
data class MediaListCollection(
    val lists: List<MediaListGroup>? = null,
    val hasNextChunk: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class MediaListGroup(
    val name: String? = null,
    val status: String? = null,
    val entries: List<MediaListEntryItem>? = null
)

@JsonClass(generateAdapter = true)
data class MediaListEntryItem(
    val id: Int? = null,
    val status: String? = null,
    val score: Double? = null,
    val progress: Int? = null,
    /**
     * When *the viewer* marked this entry finished, not when it aired.
     *
     * The list query asks for it; `id` and `score` are still modelled but no
     * longer requested, because fixtures and older cached responses carry them
     * and Moshi leaves an unasked-for field null rather than failing.
     */
    val completedAt: FuzzyDate? = null,
    /**
     * When the viewer last touched this entry, as a Unix timestamp in seconds.
     *
     * This is the only real recency signal an AniList list entry carries: the
     * collection arrives grouped by list *status*, with no date on the entry and
     * no sort argument on the query, which is why "List order" cannot honestly be
     * called "Recent". `updatedAt` is what AniList itself orders by, and it is
     * what the Library's "Recently updated" sort reads - "you last updated this
     * title on ...".
     */
    val updatedAt: Int? = null,
    val media: MediaNode
)

@JsonClass(generateAdapter = true)
data class MediaNode(
    val id: Int,
    val title: MediaTitle? = null,
    val format: String? = null,
    /**
     * `ANIME` or `MANGA`.
     *
     * Only the activity feed asks for it, to label an item "Watched" or "Read"
     * - the same status means different things for the two media types and the
     * feed cannot tell them apart without it. Also asked for by the media list
     * query now, so the Library page can filter by the media's own type
     * instead of guessing from the list an entry arrived in.
     */
    val type: String? = null,
    /**
     * `JP`, `KR`, `CN` or `US`.
     *
     * Requested only by the list query, for the profile screen's country
     * distribution. AniList reports this nowhere in its aggregate statistics, so
     * the entry is the only place it can come from.
     */
    val countryOfOrigin: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    /**
     * AniList's airing season: `WINTER`, `SPRING`, `SUMMER` or `FALL`.
     *
     * A **string**, not a number. This was declared `Int?` on the assumption
     * that it was the "season number within the franchise", which is not a
     * field AniList exposes at all. The list query asks for `season`, the
     * server answers `"WINTER"`, and Moshi - correctly - refuses to read an
     * enum string as an int:
     *
     *     Expected an int but was WINTER at path
     *     $.data.MediaListCollection.lists[0].entries[1].media.relations.edges[2].node.season
     *
     * That threw out of the *only* call that fetches the user's list, so the
     * app could not render anything for anyone whose list contained such an
     * entry. The type has to match what the server actually sends.
     */
    val season: String? = null,
    /** The year of [season]. AniList reports these separately, not combined. */
    val seasonYear: Int? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val siteUrl: String? = null,
    val bannerImage: String? = null,
    val startDate: FuzzyDate? = null,
    val coverImage: MediaCoverImage? = null,
    val studios: StudioConnection? = null,
    val relations: MediaRelations? = null,
    val mediaListEntry: SimpleMediaListEntry? = null,
    val nextAiringEpisode: NextAiringEpisode? = null,
    val source: String? = null,
    val duration: Int? = null,
    val trailer: MediaTrailer? = null,
    /**
     * Streaming, social and reference links, as AniList files them.
     *
     * Detail query only. The card that draws them filters to `STREAMING`; the
     * rest are kept in the model rather than dropped at parse time so a future
     * social row does not need a second query.
     */
    val externalLinks: List<MediaExternalLink>? = null,
    val synonyms: List<String>? = null,
    val meanScore: Int? = null,
    val rankings: List<MediaRanking>? = null,
    val tags: List<MediaTag>? = null,
    /**
     * The cast, each edge carrying the Japanese voice actor for that character.
     *
     * Only the detail query asks for it. It is deliberately not on the list
     * query: a cast is up to twelve more people with two portraits each, and the
     * list query runs for every entry on the dashboard.
     */
    val characters: MediaCharacterConnection? = null,
    /** The crew, headlined by the director and the original creator. Detail query only. */
    val staff: MediaStaffConnection? = null
) {
    /**
     * Overlay of the lazily-fetched detail onto this node.
     *
     * The list query deliberately leaves `description`, `bannerImage` and
     * `studios` unset, so those arrive later in a much smaller response. Null
     * fields in [detail] must not erase what the list query already found -
     * that is what this merge is for.
     */
    fun withDetail(detail: MediaNode): MediaNode = copy(
        // Carried, not ignored. The detail query did not select `title` at all until
        // it had to, so this line is the other half of that fix: a summary node
        // built from a source that carries no title (an activity row's media, a
        // widget's fixture) used to stay "Unknown Title" through the merge even
        // once the answer was in hand. `?:` keeps whatever this node already had.
        title = detail.title ?: title,
        description = detail.description ?: description,
        bannerImage = detail.bannerImage ?: bannerImage,
        studios = detail.studios ?: studios,
        source = detail.source ?: source,
        duration = detail.duration ?: duration,
        trailer = detail.trailer ?: trailer,
        externalLinks = detail.externalLinks ?: externalLinks,
        synonyms = detail.synonyms ?: synonyms,
        meanScore = detail.meanScore ?: meanScore,
        rankings = detail.rankings ?: rankings,
        tags = detail.tags ?: tags,
        characters = detail.characters ?: characters,
        staff = detail.staff ?: staff,
        relations = detail.relations ?: relations,
        // Field-wise, and `extraLarge` is the field this was missing.
        //
        // It used to copy `detail.coverImage.large` and nothing else, so a node whose
        // own cover carried only the size its own row needed - a profile appearance
        // built from `coverImage { large }`, an arriving summary - kept drawing that
        // 230px-wide `large` in the detail hero even though the detail response had
        // just handed over the 460px `extraLarge`. The covers that arrived by a
        // thinner route were the only ones that showed it, which is why it survived:
        // a list node already had `extraLarge`, and a node that has it keeps it.
        coverImage = when {
            detail.coverImage == null -> coverImage
            coverImage == null -> detail.coverImage
            else -> coverImage.copy(
                extraLarge = detail.coverImage.extraLarge ?: coverImage.extraLarge,
                large = detail.coverImage.large ?: coverImage.large,
                medium = detail.coverImage.medium ?: coverImage.medium,
                color = detail.coverImage.color ?: coverImage.color
            )
        }
    )
}

@JsonClass(generateAdapter = true)
data class MediaRanking(
    val id: Int? = null,
    val rank: Int? = null,
    val type: String? = null,
    val context: String? = null,
    val year: Int? = null,
    val season: String? = null,
    val allTime: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class MediaTag(
    val id: Int? = null,
    val name: String? = null,
    val rank: Int? = null,
    val isMediaSpoiler: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class MediaTrailer(
    val id: String? = null,
    val site: String? = null,
    val thumbnail: String? = null
) {
    val youtubeUrl: String?
        get() = if (site.equals("youtube", ignoreCase = true) && !id.isNullOrBlank()) {
            "https://www.youtube.com/watch?v=$id"
        } else null
}

/**
 * One of AniList's external links for a title.
 *
 * [type] is the discriminator that matters: `STREAMING`, `SOCIAL` or `INFO`.
 * AniList files Netflix, Crunchyroll and a fan wiki in the same list, so a card
 * that drew every link as "where to watch" would offer a Facebook page as a
 * place to watch the show. [site] is the service's own name ("Crunchyroll"),
 * which is the label; [url] is where the tap goes.
 */
@JsonClass(generateAdapter = true)
data class MediaExternalLink(
    val id: Int? = null,
    val url: String? = null,
    val site: String? = null,
    val type: String? = null
)

/**
 * The studios credited on a media.
 *
 * Carries **both** shapes, because AniList answers both and they are not
 * interchangeable. `nodes` is the flat list several queries ask for and the shape
 * every existing derivation reads; `edges` is the only one that says whether a
 * studio was *main* or supporting, which is what lets the title page separate the
 * animation studio from the publishers that share the field. A `Studio` can be
 * either - AniList does not distinguish them in this connection - so the flag, not
 * the name, is what the page sorts on.
 *
 * `nodes` stays **first**, and that is not cosmetic: several callers - both demo
 * providers and two test fixtures - build this with a positional
 * `StudioConnection(listOf(...))`, and putting the new field in front of the old one
 * silently re-typed those arguments as edges. A field order is an API of its own.
 */
@JsonClass(generateAdapter = true)
data class StudioConnection(
    val nodes: List<StudioNode>? = null,
    val edges: List<StudioEdge>? = null
)

/** One credit: which studio, and whether the title was primarily theirs. */
@JsonClass(generateAdapter = true)
data class StudioEdge(
    val isMain: Boolean? = null,
    val node: StudioNode? = null
)

@JsonClass(generateAdapter = true)
data class StudioNode(
    val id: Int? = null,
    val name: String? = null,
    /**
     * Whether this is an animation studio, as opposed to a publisher or a
     * producer. The favourites query reads it to tell a manga house from an anime
     * one in the same row, and the studio page reads it to say which of the two it
     * is looking at.
     */
    val isAnimationStudio: Boolean? = null,
    /** How many AniList users have this studio in their favourites. Studio page only. */
    val favourites: Int? = null,
    val siteUrl: String? = null,
    /**
     * One page of this studio's work, newest first.
     *
     * On [StudioNode] rather than beside it because the studio query answers with
     * the studio as its root field, so there is nowhere else for the page to live.
     * Every other query that selects a studio leaves this null. See
     * [com.anisequel.domain.studio.StudioYearShelf] for how a page of these becomes
     * the year shelves the screen draws.
     */
    val media: StudioMediaConnection? = null
)

/**
 * A page of a studio's titles.
 *
 * No `pageInfo`: pagination here is decided by whether a full page came back,
 * which is how the activity feed already asks for its next one - see
 * `UserActivityViewModel.loadNextPage`. A `pageInfo` nobody reads is a field
 * AniList has to resolve on every request.
 */
@JsonClass(generateAdapter = true)
data class StudioMediaConnection(
    val nodes: List<MediaNode>? = null
)

@JsonClass(generateAdapter = true)
data class MediaTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
    val userPreferred: String? = null
) {
    val displayTitle: String
        get() = english?.takeIf { it.isNotBlank() }
            ?: userPreferred?.takeIf { it.isNotBlank() }
            ?: romaji?.takeIf { it.isNotBlank() }
            ?: native ?: "Unknown Title"

    /**
     * The same preference order, but null instead of `"Unknown Title"`.
     *
     * [displayTitle]'s placeholder is right for a list row - an entry with no
     * title at all still occupies a slot and needs *something* - and wrong for
     * the bio's preview cards. Those are only ever built for a media AniList
     * resolved, so a card whose title reads "Unknown Title" has learned nothing
     * and should fall back to what the URL gave it instead.
     */
    val bestTitleOrNull: String?
        get() = english?.takeIf { it.isNotBlank() }
            ?: userPreferred?.takeIf { it.isNotBlank() }
            ?: romaji?.takeIf { it.isNotBlank() }
            ?: native?.takeIf { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class MediaCoverImage(
    val medium: String? = null,
    val large: String? = null,
    val extraLarge: String? = null,
    val color: String? = null
) {
    val bestUrl: String?
        get() = extraLarge ?: large ?: medium
}

@JsonClass(generateAdapter = true)
data class FuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
) {
    val formatted: String
        get() {
            return when {
                year != null && month != null && day != null -> "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                year != null && month != null -> "$year-${month.toString().padStart(2, '0')}"
                year != null -> "$year"
                else -> "TBA"
            }
        }
}

@JsonClass(generateAdapter = true)
data class NextAiringEpisode(
    val episode: Int? = null,
    val airingAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class MediaRelations(
    val edges: List<MediaRelationEdge>? = null
)

@JsonClass(generateAdapter = true)
data class MediaRelationEdge(
    val relationType: String? = null,
    val node: MediaNode
)

/**
 * The `characters` connection of a media.
 *
 * `edges` rather than `nodes` because the voice actor hangs off the *pairing*:
 * AniList returns the same character with a different actor per language, so
 * only the edge can say "this actor plays this character".
 */
@JsonClass(generateAdapter = true)
data class MediaCharacterConnection(
    val edges: List<MediaCharacterEdge>? = null
)

@JsonClass(generateAdapter = true)
data class MediaCharacterEdge(
    /** `MAIN`, `SUPPORTING` or `BACKGROUND`. */
    val role: String? = null,
    val node: MediaCharacter? = null,
    /**
     * The Japanese cast for this character, in AniList's own order.
     *
     * Modelled on the edge, where the API puts it, rather than on
     * [MediaCharacter] - a character does not have one actor, it has one per
     * language, and the row the Info page draws is a character *played by* an
     * actor, which is exactly one edge.
     */
    val voiceActors: List<MediaStaff>? = null
)

@JsonClass(generateAdapter = true)
data class MediaCharacter(
    val id: Int? = null,
    val name: MediaName? = null,
    val image: MediaImage? = null,
    /**
     * How many AniList users have this character in their favourites.
     *
     * The one number that says whether a character is well known, and the reason the
     * character card can be a control rather than a caption - it is the same field the
     * studio page leads with. Null on a source that never asked for it.
     */
    val favourites: Int? = null,
    /** The character's own page on AniList, where the rest of its details live. */
    val siteUrl: String? = null,
    /**
     * AniList's own gender string: `Male`, `Female`, `Non-binary`, or null.
     *
     * A string and not an enum because AniList reports a *description* rather than a
     * closed set — it has values like `Non-Binary` and `N/A`, and a new one would
     * make the whole document fail rather than render one unfamiliar word. See the note
     * on `MediaNode.statusLabel` for the same reasoning applied to an enum.
     */
    val gender: String? = null,
    /**
     * Free text, not a number.
     *
     * "27" on most characters and not an int; Faye Valentine's is
     * "23 (Physical), 77 (Actual)" and Edward's changes across the series, so this is
     * printed as written rather than parsed. Parsing it into an `Int?` is exactly the
     * mistake `MediaNode.season` documents, where Moshi refused to read an enum string
     * as an int and threw out of the *only* call that fetches the list.
     */
    val age: String? = null,
    /**
     * The character's own AniList description, as markdown-with-HTML-glue.
     *
     * Fetched so a character is not a dead end, and deliberately **not** rendered on
     * the card: the detail query asks for the whole cast and twenty-five descriptions
     * is a wall of text in a horizontal row. It is here for the same reason a studio's
     * filmography is fetched but not drawn on its credit chip.
     */
    val description: String? = null
)

/** The `staff` connection of a media. */
@JsonClass(generateAdapter = true)
data class MediaStaffConnection(
    val edges: List<MediaStaffEdge>? = null
)

@JsonClass(generateAdapter = true)
data class MediaStaffEdge(
    /** The position, as AniList spells it - "Chief Director", "Original Creator". */
    val role: String? = null,
    val node: MediaStaff? = null
)

/** A person. The same type serves a voice actor and a crew member. */
@JsonClass(generateAdapter = true)
data class MediaStaff(
    val id: Int? = null,
    val name: MediaName? = null,
    val image: MediaImage? = null
)

/** AniList's `CharacterName`/`StaffName`: full, plus native script. */
@JsonClass(generateAdapter = true)
data class MediaName(
    val full: String? = null,
    val native: String? = null,
    /**
     * Other names the character is known by, for a character only.
     *
     * Spike Spiegel carries "Spike Spike" and "Swimming Bird"; the staff and the
     * character both use this type, and a staff member has no alternatives, so the
     * default is null rather than an empty list — Moshi leaves an unasked-for field
     * null, and a staff row must not gain an empty "Alternative names" section for a
     * field that was never asked for.
     */
    val alternative: List<String>? = null
)

/** AniList's `CharacterImage`/`StaffImage`. */
@JsonClass(generateAdapter = true)
data class MediaImage(
    val large: String? = null,
    val medium: String? = null
) {
    val bestUrl: String? get() = large?.takeIf { it.isNotBlank() } ?: medium?.takeIf { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class SimpleMediaListEntry(
    val id: Int? = null,
    val status: String? = null,
    val mediaId: Int? = null,
    /** Episodes watched. Lets a card say "you're 4 episodes in" rather than only "not on your list". */
    val progress: Int? = null,
    /**
     * Volumes read, for a manga entry.
     *
     * Null for anime, where the field does not apply. AniList answers it as null
     * rather than 0, which is why the editor's stepper hides itself rather than
     * showing a volume count of zero on a show.
     */
    val progressVolumes: Int? = null,
    val score: Double? = null,
    /**
     * Whether the entry is hidden from the public profile.
     *
     * `private` is a Kotlin modifier keyword, so the property is `isPrivate` and
     * the wire name is pinned with `@Json`. Reading a JSON `private` into a field
     * literally named `private` is not expressible, which is why the annotation
     * is load-bearing rather than cosmetic.
     */
    @Json(name = "private") val isPrivate: Boolean? = null,
    val notes: String? = null,
    val startedAt: FuzzyDate? = null,
    val completedAt: FuzzyDate? = null,
    /**
     * The account's score format, as `POINT_10`, `POINT_100`, and so on.
     *
     * The only field here that is not a property of the *entry*: `MediaList` has
     * no score format, it belongs to `Viewer.mediaListOptions`. It is carried on
     * this type because the entry query asks for both in one request and the
     * editor needs one object to build its form from - without it the score
     * slider has to guess its scale from the value, which is how a hundred-point
     * entry scored 8 was given a ten-point slider.
     *
     * Null wherever the value was not asked for: every `mediaListEntry` nested in
     * a media node, and the save mutation's response, which has no `Viewer`.
     * [com.anisequel.ui.components.detail.scoreScaleFor] is the fallback for
     * those, not a failure.
     */
    val scoreFormat: String? = null
)

@JsonClass(generateAdapter = true)
data class SaveMediaListEntryData(
    @Json(name = "SaveMediaListEntry") val entry: SimpleMediaListEntry?
)

@JsonClass(generateAdapter = true)
data class MediaListEntryData(
    @Json(name = "MediaList") val entry: SimpleMediaListEntry?,
    /**
     * The account's list options, asked for beside the entry.
     *
     * Not a convenience: the score control cannot be drawn correctly without the
     * format (see [SimpleMediaListEntry.scoreFormat]), and a second query for one
     * enum would spend one of AniList's ~30 requests a minute to learn something
     * the entry request can carry for free.
     */
    @Json(name = "Viewer") val viewer: ViewerListOptions? = null
)

/** The slice of `Viewer` the entry query needs: how this account scores titles. */
@JsonClass(generateAdapter = true)
data class ViewerListOptions(
    val mediaListOptions: MediaListOptions? = null
)

@JsonClass(generateAdapter = true)
data class MediaListOptions(
    val scoreFormat: String? = null
)

/**
 * The object both delete mutations answer with.
 *
 * `DeleteMediaListEntry` and `DeleteActivityReply` return AniList's shared
 * `Deleted` type, and it is an OBJECT with one `Boolean` field - confirmed with
 * `__type(name: "Deleted")` against the live schema rather than inferred from
 * the name. The two delete payloads used to flatten it into a bare
 * `deleted: Boolean?`, which decodes to nothing: Moshi refuses the object with
 * `Expected a boolean but was BEGIN_OBJECT`, `execute` classifies that as a
 * parse failure, and a delete the server has already performed is reported to
 * the caller as a failed call.
 *
 * Neither caller reads the value - they drop the row they asked to remove - but
 * the shape has to be right because the decode happens before anything else.
 */
@JsonClass(generateAdapter = true)
data class Deleted(val deleted: Boolean? = null)

@JsonClass(generateAdapter = true)
data class DeleteMediaListEntryData(
    @Json(name = "DeleteMediaListEntry") val deleted: Deleted? = null
)

/**
 * One edit to the viewer's list entry, as the editor tab hands it to the
 * repository.
 *
 * A type rather than a long parameter list because the mutation has ten
 * arguments and every one of them is optional in a different sense: a null
 * [status] means "do not change it" to AniList, whereas a null [startedAt] means
 * "clear the date". Passing the whole edit keeps that decision in one place
 * instead of at a call site that has to remember which nulls it is allowed to
 * send.
 *
 * The date fields are [FuzzyDate] - AniList's own input shape - so a partial date
 * (`year` but no `month`) is expressible. Reusing the read model rather than a
 * second `FuzzyDateInput` class means the shape written is provably the shape
 * read back.
 */
data class MediaEntryEdit(
    val mediaId: Int,
    val status: String? = null,
    val progress: Int? = null,
    val progressVolumes: Int? = null,
    val score: Double? = null,
    val startedAt: FuzzyDate? = null,
    val completedAt: FuzzyDate? = null,
    val isPrivate: Boolean? = null,
    /**
     * The private note on the entry.
     *
     * Null means "this caller does not edit notes", so the argument is omitted and
     * AniList leaves the stored note alone. The editor always passes a value -
     * including the empty string, which is how a note is cleared; omitting the
     * argument would leave the old note in place and read as the field ignoring
     * the edit.
     */
    val notes: String? = null
)
