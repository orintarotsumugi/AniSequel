package com.anisequel.data.network

import com.anisequel.data.model.MediaCoverKey

/**
 * GraphQL documents sent to `graphql.anilist.co`.
 *
 * Field selection here is a performance decision, not a style one. AniList
 * resolves `MediaListCollection` entries eagerly, so every field asked for is
 * paid for *hundreds* of times - once per list entry, and again for each of
 * that entry's related nodes.
 *
 * Two rules follow from that:
 *
 *  1. Ask only for fields that are rendered somewhere. A 476-entry list
 *     produces roughly 1,500 relation nodes; one unused field on each is
 *     megabytes of JSON parsed for nothing.
 *  2. Never ask for a relation field that makes the server resolve a second
 *     entity. `studios(isMain: true)` inside `relations` does exactly that -
 *     a studio lookup for every node of every entry - and on a real 476-entry
 *     account it does not slow the response down, it **fails**: AniList
 *     returns HTTP 500 "Internal Server Error" and the app shows an error
 *     screen instead of the user's list.
 *
 * `getMediaDetailQuery` exists because of rule 1. `description`, `bannerImage`,
 * `studios`, `trailer`, and `synonyms` are only ever rendered for the one entry
 * a user taps open, so they are fetched on demand rather than shipped for all
 * ~1,500 relation nodes on every load.
 */
object GraphQLQueries {

    val GET_VIEWER = """
        query GetViewer {
          Viewer {
            id
            name
            avatar {
              medium
              large
            }
            bannerImage
            statistics {
              anime {
                count
                episodesWatched
                minutesWatched
              }
            }
          }
        }
    """.trimIndent()

    val GET_USER_BY_NAME = """
        query GetUserByName(${'$'}userName: String) {
          User(name: ${'$'}userName) {
            id
            name
            avatar {
              medium
              large
            }
            bannerImage
            statistics {
              anime {
                count
                episodesWatched
                minutesWatched
              }
            }
          }
        }
    """.trimIndent()

    /**
     * Fields for a favourited media node.
     *
     * `genres` and `popularity` are absent: the favourite rows show a cover, a
     * title, the format and the AniList score, and nothing else. Both of those
     * are asked for on the *list* query's relation nodes already, where they are
     * read - asking for them again per favourite would be the same field twice
     * for two different reasons.
     */
    private const val FAVOURITE_MEDIA_FIELDS = """
                id
                title { english romaji native }
                format
                status
                averageScore
                coverImage { extraLarge large }
                siteUrl
    """

    /**
     * The profile screen's query: bio, statistics, favourites, and AniList's own
     * aggregate stats.
     *
     * A second `User` query rather than more fields on [GET_VIEWER] because the
     * two have different lifetimes. `GET_VIEWER` runs on every dashboard load and
     * renders four values; this runs when someone opens their profile and returns
     * a favourites tree plus a distribution set. Folding them together would mean
     * every cold start parsed all of it to draw a title bar.
     *
     * The favourites are capped at 12 per branch because that is all a horizontal
     * row shows. AniList allows 25 and will happily send them; the extra thirteen
     * per branch is five branches' worth of image URLs parsed for art that never
     * leaves the screen.
     *
     * ## The `stats` block
     *
     * `stats` is the field AniList marks deprecated, and it is here because the
     * replacement is not served: `statistics.anime.statuses`, `.genres`,
     * `.formats` and `.releaseYears` all answer `[]` for every account tried,
     * including ones with hundreds of scored entries, while the same query's
     * `count`, `meanScore` and `minutesWatched` come back populated. The
     * per-distribution data the Stats tab is built around only exists under
     * `stats`. It costs about 6 KB in the same request - one round trip, not two.
     *
     * `UserOverviewQueryTest` asserts the block is present, so that whoever finds
     * this the day AniList retires `stats` sees the removal noted rather than
     * discovering it as an empty Stats tab.
     */
    val GET_USER_OVERVIEW = """
        query GetUserOverview(${'$'}userId: Int, ${'$'}userName: String) {
          User(id: ${'$'}userId, name: ${'$'}userName) {
            id
            name
            about(asHtml: false)
            siteUrl
            avatar {
              medium
              large
            }
            bannerImage
            createdAt
            updatedAt
            statistics {
              anime {
                count
                episodesWatched
                minutesWatched
                meanScore
              }
              manga {
                count
                chaptersRead
                volumesRead
                meanScore
              }
            }
            stats {
              watchedTime
              chaptersRead
              activityHistory { date amount level }
              animeStatusDistribution { status amount }
              mangaStatusDistribution { status amount }
              animeScoreDistribution { score amount }
              animeListScores { meanScore standardDeviation }
              mangaListScores { meanScore standardDeviation }
              favouredFormats { format amount }
              favouredYears { year amount meanScore }
              favouredGenres { genre amount meanScore }
              favouredTags { tag { id name } amount meanScore }
            }
            favourites {
              anime(perPage: 12) { nodes { $FAVOURITE_MEDIA_FIELDS } }
              manga(perPage: 12) { nodes { $FAVOURITE_MEDIA_FIELDS } }
              characters(perPage: 12) {
                nodes {
                  id
                  name { full native alternative }
                  image { large }
                  favourites
                  siteUrl
                }
              }
              staff(perPage: 12) {
                nodes {
                  id
                  name { full native alternative }
                  image { large }
                  primaryOccupations
                  favourites
                  siteUrl
                }
              }
              studios(perPage: 12) { nodes { id name isAnimationStudio } }
            }
          }
        }
    """.trimIndent()

    /**
     * A page of somebody's list activity: "watched 3 episodes", "completed X",
     * "added Y to planning".
     *
     * `Page.activities` returns `[ActivityUnion]`, so this selects the
     * `ListActivity` member with an inline fragment and filters to the two list
     * types with `type_in`. Without the filter the same query answers with
     * `MessageActivity` - forum posts by the user, which is not what the feed is
     * for. The parser drops anything that is still not a list update.
     *
     * `type_in` takes both media types in one request rather than one query per
     * medium: the response carries each item's own `media.type`, and the screen
     * can split on that afterwards. Two requests would also mean two chances to
     * hit AniList's rate limit on a screen the user opens to browse.
     */
    val GET_USER_ACTIVITY = """
        query GetUserActivity(${'$'}userId: Int, ${'$'}page: Int) {
          Page(page: ${'$'}page, perPage: 25) {
            activities(userId: ${'$'}userId, type_in: [ANIME_LIST, MANGA_LIST], sort: [ID_DESC]) {
              ... on ListActivity {
                id
                status
                progress
                createdAt
                likeCount
                replyCount
                isLiked
                siteUrl
                media {
                  id
                  type
                  format
                  episodes
                  chapters
                  volumes
                  title { english romaji native }
                  coverImage { extraLarge large color }
                  siteUrl
                }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * The people who follow this account.
     *
     * Followers live on the `Page` query and not on `User` - there is no
     * `User.followers` field at all - which is why this is its own document. It
     * also cannot be combined with `following`: the `Page` query accepts one data
     * field, so "Followers" and "Following" are two round trips.
     *
     * `$userId` is `Int!`, not `Int`. The argument is non-null and declaring the
     * variable nullable compiles and then fails at runtime with "Variable
     * \"$userId\" of type \"Int\" used in position expecting type \"Int!\"".
     */
    val GET_USER_FOLLOWERS = """
        query GetUserFollowers(${'$'}userId: Int!) {
          Page(perPage: 50) {
            followers(userId: ${'$'}userId) {
              id
              name
              avatar { medium }
            }
          }
        }
    """.trimIndent()

    /**
     * The people this account follows. Separate from [GET_USER_FOLLOWERS]
     * because `Page` takes one data field.
     */
    val GET_USER_FOLLOWING = """
        query GetUserFollowing(${'$'}userId: Int!) {
          Page(perPage: 50) {
            following(userId: ${'$'}userId) {
              id
              name
              avatar { medium }
            }
          }
        }
    """.trimIndent()

    /**
     * Fields for a related node, rendered in the card list and on the title page.
     *
     * **Shared by both queries that carry relations**, deliberately. The detail
     * query used to carry its own, much thinner, relation node - `id`, `title`,
     * `format`, `status`, `episodes` and the cover - which was enough for the
     * poster-and-title card the title page drew and nothing else. So the one place
     * in the app whose job is to show you the rest of a franchise could say what a
     * related title was *called* and not when it aired, what it scored, or whether
     * it is out yet: the same household facts its own hero chip spells out above.
     *
     * It is the same `Media` type, so the fix is the same field list rather than a
     * second one - and a field list that exists twice is one that eventually
     * forgets half a relation. The cost is bounded the way it is here on purpose:
     * this runs once per *open* and over at most twelve nodes, unlike the list
     * query, which asks it once per page of a user's whole list. Widening the
     * list query's copy of this would be the expensive change, and is not what
     * happened.
     */
    private const val RELATED_NODE_FIELDS = """
                id
                type
                title { romaji english native userPreferred }
                format
                status
                episodes
                season
                seasonYear
                averageScore
                popularity
                genres
                siteUrl
                startDate { year month day }
                coverImage { extraLarge large color }
                nextAiringEpisode { episode airingAt }
                mediaListEntry { status progress }
    """

    /**
     * Parent fields, by consumer:
     *
     *  - `episodes` is what the watched detector needs: an entry counts as
     *    watched when its progress reaches the episode count even if the user
     *    never set a status.
     *  - `status`, `startDate` and `nextAiringEpisode` drive the "Currently
     *    arriving" section - the user's own list, split into what is airing now
     *    and what has not started yet.
     *  - `coverImage` is the parent's key visual on the card that says "Sequel
     *    to X". It was always meant to be here; without it every one of those
     *    cards rendered with no artwork at all.
     *  - `completedAt` on the entry (not the media) is the year the *viewer*
     *    marked it finished, which is what the "completed this year" filter
     *    asks about.
     *
     * `id` and `score` on the entry are gone because nothing reads them - the
     * media's own id is the identity used everywhere, and the score the UI
     * renders is the media's `averageScore`. They cost one value per entry on
     * the hottest request in the app for nothing.
     *
     * `format` and `countryOfOrigin` on the parent were added for the profile
     * screen, and they are the only fields this query has gained. Format feeds the
     * Format Distribution; country feeds the Country Distribution, and AniList
     * exposes no aggregate for country anywhere - not in `User.stats`, whose
     * distribution fields cover status, format, score and year. So it is either
     * asked for once per entry here, where the parent media is already being
     * fetched, or the chart does not exist.
     *
     * Measured on a real 142-entry list: 480,034 bytes without the field, 483,990
     * with it. About 0.8% of the most expensive request the app makes, for a chart
     * that is otherwise impossible to draw.
     */
    /**
     * The user's media list for one [MediaType].
     *
     * One builder rather than an ANIME constant and a MANGA constant: the field
     * list is long enough that a second copy would be a second thing to forget to
     * widen when a new field is needed, and the only difference between the two
     * lists is the `type` argument. `MediaType` is a real GraphQL enum, so it is
     * passed as a variable and validated by AniList rather than pasted into the
     * query text.
     *
     * `relations` is here for the same reason it is in the anime list - the
     * detail sheet opens from a Library row and draws a sequel banner from it -
     * and it is the reason this is the app's heaviest query. It is asked for once
     * per *page* of a user's own list rather than once per relation node, so the
     * cost is bounded by the page size.
     */
    private fun mediaListQuery(queryName: String): String = """
        query $queryName(${'$'}userId: Int, ${'$'}userName: String, ${'$'}chunk: Int, ${'$'}type: MediaType) {
          MediaListCollection(userId: ${'$'}userId, userName: ${'$'}userName, type: ${'$'}type, chunk: ${'$'}chunk) {
            hasNextChunk
            lists {
              name
              status
              entries {
                status
                progress
                updatedAt
                score(format: POINT_100)
                completedAt { year month day }
                media {
                  id
                  type
                  title { romaji english native userPreferred }
                  episodes
                  chapters
                  volumes
                  status
                  format
                  countryOfOrigin
                  season
                  seasonYear
                  averageScore
                  startDate { year month day }
                  coverImage { extraLarge large color }
                  nextAiringEpisode { episode airingAt }
                  relations {
                    edges {
                      relationType
                      node { $RELATED_NODE_FIELDS }
                    }
                  }
                }
              }
            }
          }
        }
    """.trimIndent()

    val GET_USER_ANIME_LIST: String = mediaListQuery("GetUserAnimeList")

    /** The same list for manga, which the Library tab filters to on demand. */
    val GET_USER_MANGA_LIST: String = mediaListQuery("GetUserMangaList")

    /**
     * Many media in one request, one aliased root field each.
     *
     * The preview cards in a bio each name an AniList entity, and the obvious
     * implementation - one `getMediaDetail` per card - spends one of AniList's 30
     * requests a minute per card. A typical "hall of fame" bio has a dozen links,
     * so opening one profile could exhaust the budget and hand the next person who
     * opens anything a 429.
     *
     * `Media` takes a single id *per field*, but aliasing the *root* field is
     * ordinary GraphQL: `m0: Page { media(id: 1, type: ANIME) }`, `m1: Page {
     * media(id: 1, type: MANGA) }` in one document, one round trip. Aliasing the
     * field is what makes it possible to ask for both sides of an id that is used
     * by *both* media namespaces in the same batch - a bare id would have
     * collapsed them to one entry and answered with only one of the two posters.
     * See [MediaCoverKey] for why the type cannot be dropped.
     *
     * ## Why each alias resolves `Page.media` and not the `Media` root field
     *
     * `Media(id:)` is *nullable*, and AniList does not answer it with null: a
     * batch in which one id has no media - a link to a deleted entry, or an id
     * that belongs to the other media namespace - comes back as **HTTP 404** with
     * `errors: ["Not Found."]`, and the whole response is a failure even though
     * the other aliases resolved. Every poster in the bio then fell back to the
     * placeholder, because one of a dozen links was stale, and the failure named
     * nothing the reader could act on. Verified against the live API: the same
     * request written with `m0: Media(...)` answers 404, and written with
     * `m0: Page { media(...) }` answers 200 with `"m0": { "media": [] }`.
     *
     * `Page.media` is a *connection*, so it filters instead of failing, and an id
     * with no media is an empty list. That is the one shape this batch can be
     * asked in without letting the weakest link in a bio take the whole document
     * down with it. The caller reads `media.firstOrNull()`, which is null for the
     * empty case - the same "nothing to draw here" it already handled.
     *
     * This is distinct from the `Page` limitation noted on
     * [AniListApiService.getUserFollowers], which is about two child fields
     * *inside* a single `Page` and not about two fields at the root.
     *
     * Only the fields a preview card draws. The full detail query
     * ([getMediaDetailQuery]) carries a
     * description, banner and studio per entry, and a bio's dozen cards would
     * turn that into a payload the screen cannot use.
     */
    fun getMediaCoversQuery(keys: List<MediaCoverKey>): String {
        require(keys.isNotEmpty()) { "a cover batch needs at least one id" }
        val fields = keys.indices.joinToString("\n") { index ->
            """
            m$index: Page {
              media(id: ${'$'}id$index, type: ${'$'}type$index) {
                id
                type
                title { romaji english userPreferred }
                format
                averageScore
                coverImage { extraLarge large }
                siteUrl
              }
            }
            """.trimIndent()
        }
        // `type` is nullable in AniList's `Media` field, so the variable is too.
        // Declaring it `MediaType!` would make the whole document invalid.
        //
        // The `$` sigil is `\$` here because a declaration needs one and a
        // template is not one: `"id$it: Int"` produced `id0: Int`, which is not
        // a variable declaration at all. The use sites below were written with
        // the sigil, so the document asked for a variable it never declared -
        // and AniList answered **HTTP 400, "Expected $, found Name \"id0\""**
        // for the whole batch. Every poster in every bio therefore stayed on its
        // placeholder, with nothing on screen saying why, because the covers
        // request is one of the few the user never sees fail. Verified against
        // the live API: the same batch written with `\$id0` answers 200.
        val variables = keys.indices.joinToString(", ") { index ->
            "${'$'}id$index: Int, ${'$'}type$index: MediaType"
        }
        return """
            query MediaCovers($variables) {
            $fields
            }
        """.trimIndent()
    }

    /**
     * The alias [getMediaCoversQuery] gives the nth id, so results can be read back.
     *
     * Indexed by *position* in the requested list rather than by media id, because
     * two entries in one batch may share an id across the anime and manga
     * namespaces - a single key per alias would collide and drop one of them.
     */
    fun mediaCoverAlias(index: Int): String = "m$index"

    /**
     * The single media record the detail sheet renders.
     *
     * This was once the *heavy* fields only - description, banner, studios - on
     * the reasoning that the list query already carried the rest. That held for
     * a missed sequel, whose list node has title, format, status, episodes and
     * cover. It does not hold for the two other sources that now open this
     * sheet: an activity row's media and an arriving summary carry a handful of
     * those fields at most, so the sheet had no title to render until this
     * answered. Widening the query costs nothing worth protecting - it is one
     * item per open, not one per relation node - whereas the alternative was a
     * null-filled header on the profile feed.
     *
     * ## Why `type` is a variable and not a constant
     *
     * This asked `Media(id: $id)` with no type, and AniList media ids are **not
     * unique across namespaces**: `anilist.co/anime/1` and `anilist.co/manga/1`
     * are different entities. A bare id is resolved against both, and AniList
     * answers with whichever it prefers.
     *
     * The failure was silent and looked like missing content rather than wrong
     * content, which is why it survived a release. Opening a *manga* from the
     * Library resolved to the anime sharing that id, so the sheet rendered the
     * anime's title, synopsis, cast and relations; where no media matched at all,
     * AniList answered 404, `require("That entry is no longer available on
     * AniList.")` threw, and `MediaDetailStore` swallowed the failure by design -
     * leaving the thin summary node, with no cast, no staff and no relations.
     * That is the "the relations and the character list do not work when I open
     * a title" report, from a sheet that was working perfectly for every anime.
     *
     * [MediaCoverKey] documents the same collision for covers, and
     * [getMediaCoversQuery] is the fix there: `Page.media(id:, type:)`, which
     * filters rather than 404ing. The type is threaded in as a variable so the
     * caller that already knows the media's namespace decides it, rather than
     * this document hardcoding the one it happened to be written for.
     *
     * Nullable, because a summary node from a source that never carried `type`
     * cannot supply one; passing null reproduces the old untyped behaviour
     * rather than failing the document, which is why it is declared `MediaType`
     * and not `MediaType!`.
     */
    fun getMediaDetailQuery(mediaType: String?): String {
        val typed = mediaType != null
        val queryName = if (typed) "GetMediaDetailTyped" else "GetMediaDetail"
        val variables = if (typed) "\$id: Int, \$type: MediaType" else "\$id: Int"
        val arguments = if (typed) "\$id, type: \$type" else "\$id"
        return buildString {
            appendLine("query $queryName($variables) {")
            appendLine("  Media(id: $arguments) {")
            append(MEDIA_DETAIL_FIELDS)
            appendLine("  }")
            appendLine("}")
        }
    }

    /**
     * One studio, and one page of everything it has worked on.
     *
     * ## Why this is `Studio.media` and not `Page.media`
     *
     * AniList's `Page.media` has no `studioId` argument - its documented filter
     * list runs from `id` to `sort` and never mentions a studio - so the studio's
     * own `media` connection is the only documented way to ask the question at
     * all. Getting that wrong looks like a working feature until it is called: a
     * `Page` query written with a studio filter is a **400**, not an empty page.
     *
     * ## Why `perPage` is a variable with a hard ceiling of 25
     *
     * 25 is the documented maximum on this connection (the root `Page` allows 50,
     * which is where a copy-pasted page size would come from). Asking for more is
     * another 400, so the ViewModel clamps rather than trusting its caller; the
     * cap is asserted in `StudioQueryTest` for exactly that reason.
     *
     * ## Why the sort is `START_DATE_DESC` and not left to the server
     *
     * `Studio.media` has no default ordering worth relying on, and the screen
     * draws *year shelves*, so the order it wants is by date, newest first. Titles
     * with no start date have no documented position in that sort - the screen
     * separates them into its own "TBA" shelf client side, because only the screen
     * knows that a title with no date is the most interesting thing on the page
     * rather than a sorting accident.
     */
    fun getStudioQuery(): String = """
        query StudioWork(${'$'}id: Int, ${'$'}page: Int, ${'$'}perPage: Int) {
          Studio(id: ${'$'}id) {
            id
            name
            isAnimationStudio
            favourites
            siteUrl
            media(page: ${'$'}page, perPage: ${'$'}perPage, sort: [START_DATE_DESC]) {
              nodes {
    $STUDIO_MEDIA_FIELDS
              }
            }
          }
        }
    """.trimIndent()

    /**
     * The fields one studio-page card draws, and nothing else.
     *
     * A studio can have hundreds of titles, and this document is fetched again for
     * every page of them - so it is the *list* shape rather than the detail shape:
     * no cast, no staff, no relations, no description. `startDate` and `seasonYear`
     * are both here because the year shelf groups on the first and falls back to
     * the second; `type` travels with each node because tapping a card opens the
     * detail sheet, and an id without its namespace resolves to whatever AniList
     * prefers (see `MediaCoverKey`).
     */
    private const val STUDIO_MEDIA_FIELDS = """
                id
                type
                idMal
                title { romaji english native userPreferred }
                format
                status
                episodes
                chapters
                volumes
                season
                seasonYear
                averageScore
                popularity
                siteUrl
                startDate { year month day }
                coverImage { extraLarge large color }
    """

    /**
     * The `Media` selection both forms of the detail query share.
     *
     * One constant rather than a second copy in the typed branch: the untyped and
     * typed documents differ only in whether they pass `type:` to the field, and a
     * field list that exists twice is one that eventually forgets half a relation
     * or a sort argument.
     *
     * Assembled with [buildString] rather than by concatenating two `trimIndent()`
     * literals. Concatenation silently eats the newline at each seam, which
     * GraphQL tolerates - it ignores whitespace - but which produces a document
     * reading `Media(id: $id) {id`, and makes any test that compares the two forms
     * line by line compare the seam instead of the fields.
     *
     * `idMal` is kept because it is the field that distinguishes an entry from its
     * adult variant, and the parser already knows to drop one.
     *
     * The relation nodes interpolate [RELATED_NODE_FIELDS] rather than listing a
     * handful of fields of their own - see that constant for what the title page's
     * relation cards were unable to say before.
     *
     * `title` is selected here for a reason that is easy to miss: for a while it
     * was not, on the assumption that the summary the sheet was opened from already
     * had one. That holds for the dashboard, which folds this response into the list
     * node with `withDetail` - and it does **not** hold for the profile, where the
     * fetched detail *replaces* the activity row's node (`withSummaryProgress`
     * returns the detail when the detail has an entry). Opening a title from an
     * Activity row therefore rendered it as "Unknown Title" while its own studio,
     * status, synopsis and cast arrived correctly beneath that heading, which read
     * as a broken title page rather than as a missing field. A title is not optional
     * on a node this app uses as a display record.
     */
    private val MEDIA_DETAIL_FIELDS = """
        id
        type
        idMal
        title { romaji english native userPreferred }
        format
        status
        episodes
        chapters
        volumes
        duration
        countryOfOrigin
        isAdult
        averageScore
        meanScore
        popularity
        season
        seasonYear
        siteUrl
        genres
        synonyms
        source
        description(asHtml: false)
        studios(perPage: 25) { edges { isMain node { id name isAnimationStudio } } }
        coverImage { extraLarge large color }
        bannerImage
        nextAiringEpisode { episode airingAt timeUntilAiring }
        startDate { year month day }
        mediaListEntry { status progress score(format: POINT_100) }
        trailer { id site }
        rankings {
          id
          rank
          type
          context
          year
          season
          allTime
        }
        tags {
          id
          name
          rank
          isMediaSpoiler
        }
        relations {
          edges {
            relationType
            node { $RELATED_NODE_FIELDS }
          }
        }
        characters(sort: [ROLE, RELEVANCE, ID], perPage: 12) {
          edges {
            role
            node {
              id
              name { full native }
              image { large medium }
            }
            voiceActors(language: JAPANESE) {
              id
              name { full native }
              image { large medium }
            }
          }
        }
        staff(sort: [RELEVANCE, ID], perPage: 12) {
          edges {
            role
            node {
              id
              name { full native }
              image { large medium }
            }
          }
        }
    """

    val ADD_TO_PLANNING = """
        mutation AddToPlanning(${'$'}mediaId: Int) {
          SaveMediaListEntry(mediaId: ${'$'}mediaId, status: PLANNING) {
            id
            status
            mediaId
          }
        }
    """.trimIndent()

    val ADD_TO_WATCHING = """
        mutation AddToWatching(${'$'}mediaId: Int) {
          SaveMediaListEntry(mediaId: ${'$'}mediaId, status: CURRENT) {
            id
            status
            mediaId
          }
        }
    """.trimIndent()

    /**
     * The viewer's own entry for one media, with every field the editor can change.
     *
     * `userId` is passed explicitly rather than left to the server. AniList's
     * `MediaList` does not implicitly select the authenticated user when the id is
     * omitted - the answer is the same shape whether it is yours or a stranger's -
     * so the caller supplies the id it already holds and the editor can never
     * silently open somebody else's entry.
     */
    val GET_MEDIA_LIST_ENTRY = """
        query MediaListEntry(${'$'}mediaId: Int, ${'$'}userId: Int) {
          MediaList(mediaId: ${'$'}mediaId, userId: ${'$'}userId) {
            id
            mediaId
            status
            progress
            progressVolumes
            score
            private
            startedAt { year month day }
            completedAt { year month day }
          }
        }
    """.trimIndent()

    /**
     * Creates or replaces the viewer's entry with every field the editor owns.
     *
     * A complete snapshot rather than a diff: the editor's "Save changes" has one
     * button and the user has just seen the whole form, so sending every field is
     * what makes clearing a date or un-privating an entry actually stick. A
     * partial mutation would leave the removed value on the server, which reads as
     * the toggle springing back.
     *
     * `completedAt`, not `finishedAt` - AniList has no `finishedAt` argument, and a
     * document that sent one would be rejected outright with no partial save.
     */
    val SAVE_MEDIA_LIST_ENTRY = """
        mutation SaveMediaListEntry(
            ${'$'}mediaId: Int
            ${'$'}status: MediaListStatus
            ${'$'}progress: Int
            ${'$'}progressVolumes: Int
            ${'$'}score: Float
            ${'$'}startedAt: FuzzyDateInput
            ${'$'}completedAt: FuzzyDateInput
            ${'$'}private: Boolean
        ) {
          SaveMediaListEntry(
              mediaId: ${'$'}mediaId
              status: ${'$'}status
              progress: ${'$'}progress
              progressVolumes: ${'$'}progressVolumes
              score: ${'$'}score
              startedAt: ${'$'}startedAt
              completedAt: ${'$'}completedAt
              private: ${'$'}private
          ) {
            id
            mediaId
            status
            progress
            progressVolumes
            score
            private
            startedAt { year month day }
            completedAt { year month day }
          }
        }
    """.trimIndent()

    /** Removes one entry from the viewer's list by its own entry id. */
    val DELETE_MEDIA_LIST_ENTRY = """
        mutation DeleteMediaListEntry(${'$'}id: Int) {
          DeleteMediaListEntry(id: ${'$'}id) {
            deleted
          }
        }
    """.trimIndent()

    /**
     * One activity and its comment thread.
     *
     * Its own request rather than a `replies` selection on the feed query: a
     * feed page is twenty-five activities, and asking for every reply on every
     * one of them would fetch threads nobody opened. The sheet is the only thing
     * that needs them, so it is the only thing that asks.
     *
     * `Activity(id:)` returns `ActivityUnion`, so the fragment is required.
     */
    val GET_ACTIVITY_REPLIES = """
        query GetActivityReplies(${'$'}activityId: Int) {
          Activity(id: ${'$'}activityId) {
            ... on ListActivity {
              id
              isLiked
              likeCount
              replyCount
              replies {
                id
                text
                likeCount
                isLiked
                createdAt
                user {
                  id
                  name
                  avatar { large medium }
                }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * The people who liked one activity.
     *
     * `ListActivity.likes` is a plain `[User]`, not a paginated connection, so
     * unlike replies this needs no page argument and always returns everyone who
     * liked the activity. That is what lets the sheet print "Liked by N" from the
     * list it just drew instead of trusting a count that could disagree with it.
     *
     * Its own document rather than a field on the feed query, for the same reason
     * [GET_ACTIVITY_REPLIES] is: a feed page carries twenty-five activities, and
     * every liker of every one of them is a list nobody asked to see until the
     * sheet opens.
     *
     * `Activity(id:)` returns `ActivityUnion`, so the `ListActivity` fragment is
     * required.
     */
    val GET_ACTIVITY_LIKES = """
        query GetActivityLikes(${'$'}activityId: Int) {
          Activity(id: ${'$'}activityId) {
            ... on ListActivity {
              id
              likeCount
              likes {
                id
                name
                avatar { large medium }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * Likes or un-likes an activity.
     *
     * `ToggleLikeV2` and not `ToggleLike`: the older field took the same
     * arguments but the V2 name is the one AniList documents, and the reply
     * likes this app will add next use the identical shape. `type: ACTIVITY`
     * disambiguates the id, which is not unique across the likeable types.
     *
     * The union is fragmented on `ListActivity`, the only member this can return
     * for `ACTIVITY`; a reply like (not sent yet) would add an `ActivityReply`
     * fragment here.
     */
    val TOGGLE_ACTIVITY_LIKE = """
        mutation ToggleActivityLike(${'$'}activityId: Int) {
          ToggleLikeV2(id: ${'$'}activityId, type: ACTIVITY) {
            ... on ListActivity {
              id
              isLiked
              likeCount
            }
          }
        }
    """.trimIndent()

    /**
     * Posts a comment on an activity.
     *
     * `SaveActivityReply`, **not** `SaveActivityMessage`. The latter is for
     * direct messages and takes a `messengerId`; posting a comment through it
     * would look like a reply to this thread and deliver a private message
     * instead. The names are close enough that this is worth stating.
     *
     * The response is the created reply, which is exactly the shape the sheet
     * appends to its list, so a successful post needs no refetch.
     */
    val SAVE_ACTIVITY_REPLY = """
        mutation SaveActivityReply(${'$'}activityId: Int, ${'$'}text: String) {
          SaveActivityReply(activityId: ${'$'}activityId, text: ${'$'}text) {
            id
            text
            likeCount
            isLiked
            createdAt
            user {
              id
              name
              avatar { large medium }
            }
          }
        }
    """.trimIndent()
}
