package com.anisequel.data.model

import java.util.Locale

/**
 * The display derivations the detail sheet reads off a [MediaNode].
 *
 * They live on the node rather than in the sheet because two shapes render a
 * media as a detail view: a [MissedSequel] (a recommendation from the list,
 * which wraps the node it is a sequel to) and a bare [MediaNode] (an arriving
 * row or a profile activity item, which has no parent). Deriving them here lets
 * both go through the same components instead of a row with no parent inventing
 * a fake [MissedSequel] - the exact thing [ArrivingEntry]'s doc says not to do.
 *
 * The names avoid the node's own fields (`format`, `status`, `episodes`,
 * `genres`, `source`, `duration`, `synonyms`, `meanScore`, `rankings`,
 * `description`) so a derived value cannot be mistaken for the raw one.
 */

/** The title to show, falling back the way [MediaTitle.displayTitle] does. */
val MediaNode.displayName: String get() = title?.displayTitle ?: "Unknown Title"

val MediaNode.romajiName: String? get() = title?.romaji?.takeIf { it.isNotBlank() }
val MediaNode.englishName: String? get() = title?.english?.takeIf { it.isNotBlank() }
val MediaNode.nativeName: String? get() = title?.native?.takeIf { it.isNotBlank() }

/** The key visual, at the best quality AniList sent. */
val MediaNode.posterUrl: String? get() = coverImage?.bestUrl

/**
 * AniList's dominant colour for the key visual, as `#RRGGBB`.
 *
 * Used to tint the placeholder behind a poster that has not loaded, so a view
 * waiting on art is recognisably the right show instead of a grey rectangle.
 */
val MediaNode.posterColor: String? get() = coverImage?.color

val MediaNode.isManga: Boolean get() = type.equals("MANGA", ignoreCase = true)

/**
 * Whether this title has not aired yet, so there is no episode of it to have watched.
 *
 * AniList's own `status`, not an inference from a missing start date: a show can
 * carry a date and still be unreleased, and a planner entry for a title two years
 * out is exactly the case the entry editor must not ask "how many episodes?".
 *
 * `CANCELLED` is included because a cancelled show is not going to air either.
 * `null` status is *not* treated as unaired: AniList leaves it null for media it
 * has little data on, and hiding the row on a missing field would take the
 * control away from a title the user can plainly see episodes of. Guessing
 * wrong in that direction is worse than asking one extra question.
 */
val MediaNode.hasNotAired: Boolean
    get() = status.equals("NOT_YET_RELEASED", ignoreCase = true) ||
        status.equals("CANCELLED", ignoreCase = true)

/**
 * The format, in the words a person uses for it.
 *
 * Was the raw enum with an underscore in it - `TV_SHORT`, `ONE_SHOT` - uppercased
 * at the call site into `TV_SHORT`, printed in a hero chip and in the Details grid
 * beside "Episodes" and "Season". That is the raw GraphQL vocabulary of the API
 * appearing in the same typeface as the show's name, and it is exactly what the
 * KDoc on [statusLabel] says was fixed for status and left broken for format.
 *
 * `MANGA` and `NOVEL` are kept as the words rather than lowercased, because that is
 * how they are written on a shelf; `TV` and `OVA`/`ONA` are acronyms and stay
 * upper. An unrecognised value is passed through untouched rather than replaced,
 * for the same reason [statusLabel] does it: an unfamiliar word honestly signals
 * that it is unfamiliar, where a confident wrong label does not.
 */
val MediaNode.formatLabel: String
    get() {
        val raw = format?.trim()?.takeIf { it.isNotBlank() } ?: return if (isManga) "Manga" else "TV"
        return when (raw.uppercase(Locale.ROOT)) {
            "TV" -> "TV"
            "TV_SHORT" -> "TV Short"
            "MOVIE" -> "Movie"
            "SPECIAL" -> "Special"
            "OVA" -> "OVA"
            "ONA" -> "ONA"
            "MANGA" -> "Manga"
            "NOVEL" -> "Novel"
            "ONE_SHOT" -> "One Shot"
            "MUSIC_VIDEO" -> "Music Video"
            else -> raw.lowercase(Locale.ROOT)
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase(Locale.ROOT) }
        }
    }

/**
 * Airing status, worded the way a person writes it.
 *
 * Was `status ?: "UNKNOWN"`, which printed the **raw GraphQL enum name** into
 * the Info tab: a currently-airing show read "RELEASING", a finished one
 * "FINISHED", and an unrecognised or absent value read "UNKNOWN". Every one of
 * those is an implementation detail of the API's vocabulary, set in the same
 * typeface as "24 episodes · Winter 2023" beside it.
 *
 * [formatLabel] already solved this for `format` - it is why the grid says "TV
 * Short" and not "TV_SHORT". Status needed the same treatment and did not get
 * it, which is the same class of drift the favourites rows used to have.
 *
 * Unknown values are passed through untouched rather than replaced. A status
 * AniList adds later should read as what it is, not be silently rewritten into
 * "Unknown" - the honest failure is showing an unfamiliar word, and the
 * dishonest one is a confident label that is wrong.
 */
val MediaNode.statusLabel: String get() = when (status?.uppercase(Locale.ROOT)) {
    "FINISHED" -> "Finished"
    "RELEASING" -> "Airing"
    "NOT_YET_RELEASED" -> "Upcoming"
    "CANCELLED" -> "Cancelled"
    "HIATUS" -> "On hiatus"
    null -> "Unknown"
    else -> status
}
val MediaNode.episodesLabel: String get() = if (isManga) {
    chapters?.let { "$it chs" } ?: "Chapters TBA"
} else {
    episodes?.let { "$it eps" } ?: "Episodes TBA"
}
val MediaNode.chaptersLabel: String? get() = chapters?.let { "$it chs" }
val MediaNode.volumesLabel: String? get() = volumes?.let { "$it vols" }
val MediaNode.scoreLabel: String get() = averageScore?.let { "$it%" } ?: "N/A"
val MediaNode.meanScoreLabel: String? get() = meanScore?.let { "$it%" }
val MediaNode.releaseLabel: String get() = startDate?.formatted ?: "TBA"
val MediaNode.genreList: List<String> get() = genres ?: emptyList()

/**
 * One studio credited on a title.
 *
 * [id] is nullable because not every source carries it - the *list* query's
 * `studios { nodes { id name } }` does, but a fixture or a hand-built node may
 * not - and a credit with no id is a name that cannot be tapped rather than a
 * name that opens the wrong studio.
 */
data class StudioCredit(
    val id: Int?,
    val name: String,
    val isMain: Boolean
)

/**
 * Every studio credited on this title, the animation studio first.
 *
 * The title page used to draw one comma-joined line of names and nothing was
 * tappable, because the detail query asked AniList only for its **main** studios
 * and had no id to route to for the rest. AniList answers `studios` with both:
 * an `edges` connection flagged `isMain`, and a flat `nodes` list. Reading the
 * edges is what makes the page able to say "Shaft made this, Kodansha published
 * it" instead of printing six names as if they were six studios of equal weight.
 *
 * `nodes` is still read as the fallback, because the list query and the bio
 * covers carry no edges at all and a title with no studios shown is worse than a
 * title whose credits are unranked.
 */
val MediaNode.studioCredits: List<StudioCredit>
    get() {
        val fromEdges = studios?.edges.orEmpty().mapNotNull { edge ->
            val name = edge.node?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            StudioCredit(id = edge.node?.id, name = name, isMain = edge.isMain == true)
        }
        val credits = fromEdges.ifEmpty {
            // No edges means the answer is *unranked*, not that every credit is a
            // supporting one - so these are marked main, and the page draws them at
            // full weight. The alternative dims the animation studio on every path
            // that carries a flat list, which reads as AniList having told us it
            // was secondary when it said nothing at all.
            studios?.nodes.orEmpty().mapNotNull { node ->
                node.name?.takeIf { it.isNotBlank() }
                    ?.let { StudioCredit(id = node.id, name = it, isMain = true) }
            }
        }
        // Main first, and stable within each half, so the first row of chips is
        // the studio that actually made the thing even when the edges arrive in
        // AniList's own order.
        return credits.sortedByDescending { it.isMain }
    }

/**
 * The name of the studio that made this, for a one-line chip.
 *
 * Prefers a main credit, so a title whose `studios` now arrives with the
 * publisher first still headlines its animation studio - which is what it did
 * when the query asked for main studios only.
 */
val MediaNode.studioName: String? get() = studioCredits.firstOrNull()?.name

/** The entry's page on AniList, derived from the id when the query omitted it. */
val MediaNode.pageUrl: String get() = siteUrl ?: if (isManga) "https://anilist.co/manga/$id" else "https://anilist.co/anime/$id"

/** AniList's `MediaSource` enum, spelled the way a person writes it. */
val MediaNode.sourceLabel: String?
    get() = source?.replace('_', ' ')?.lowercase(Locale.ROOT)?.split(' ')
        ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase(Locale.ROOT) } }

val MediaNode.durationLabel: String? get() = duration?.let { "$it mins / ep" }
val MediaNode.trailerYoutubeUrl: String? get() = trailer?.youtubeUrl
val MediaNode.synonymList: List<String> get() = synonyms?.filter { it.isNotBlank() } ?: emptyList()

/** The synopsis, with AniList's HTML stripped. */
val MediaNode.synopsis: String?
    get() = description
        ?.replace(TAG_PATTERN, "")
        ?.replace(WHITESPACE_PATTERN, " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * Every non-spoiler tag AniList returned, highest ranked first, with the rank kept.
 *
 * [rankedTags] strips the rank and caps the list at eight because a list card has
 * room for eight chips; the Info page wants the whole distribution and the
 * percentage, so it reads this instead. The two exist side by side on purpose: a
 * card that showed thirty tags would drown the artwork it sits under.
 */
val MediaNode.weightedTags: List<MediaTag>
    get() = tags
        ?.filter { it.isMediaSpoiler != true && !it.name.isNullOrBlank() }
        ?.sortedByDescending { it.rank ?: 0 }
        ?: emptyList()

/**
 * AniList's tag `rank`, which is the share of viewers who applied the tag.
 *
 * "Urban Fantasy" at rank 96 is not a score - it means 96% of the people who
 * tagged this title tagged it that - which is why it is rendered as a percentage.
 */
fun MediaTag.weightLabel(): String? = rank?.let { "$name $it%" }

/** The cast, each entry with the actor who plays them. */
val MediaNode.characterList: List<MediaCharacterEdge> get() = characters?.edges ?: emptyList()

/** The crew, in AniList's relevance order. */
val MediaNode.staffList: List<MediaStaffEdge> get() = staff?.edges ?: emptyList()

/**
 * The related titles the Info page draws as a row.
 *
 * Filtered to anime, and filtered to relations a reader can *watch*: AniList
 * happily returns the light novel a show was adapted from, the guidebook and the
 * character song as relations, and a row of those next to the sequels reads as a
 * mistake rather than as information. Deduplicated by id, because the same title
 * can be related twice through two different arcs.
 */
val MediaNode.relatedAnime: List<MediaRelationEdge>
    get() = relations?.edges
        ?.filter { edge ->
            edge.node.type.equals("ANIME", ignoreCase = true) &&
                edge.node.id != id &&
                !edge.relationType.equals("CHARACTER", ignoreCase = true)
        }
        ?.distinctBy { it.node.id }
        ?.take(MAX_RELATIONS)
        ?: emptyList()

/**
 * AniList's `MediaRelation` enum, spelled the way a person writes it.
 *
 * Same rule as [statusLabel]: a relation the app has never seen reads as what it
 * is, rather than being flattened into "Related".
 */
val MediaRelationEdge.typeLabel: String
    get() {
        val raw = relationType?.takeIf { it.isNotBlank() } ?: return "Related"
        return when (raw.uppercase(Locale.ROOT)) {
            "PREQUEL" -> "Prequel"
            "SEQUEL" -> "Sequel"
            "SIDE_STORY" -> "Side story"
            "SPIN_OFF" -> "Spin-off"
            "PARENT" -> "Parent"
            "ALTERNATIVE" -> "Alternative"
            "SUMMARY" -> "Summary"
            "FULL_STORY" -> "Full story"
            "ADAPTATION" -> "Adaptation"
            "SOURCE" -> "Source"
            "COMPILATION" -> "Compilation"
            "OTHER" -> "Other"
            else -> raw.lowercase(Locale.ROOT)
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase(Locale.ROOT) }
        }
    }

/**
 * The one-line summary under a related title's name.
 *
 * The title page's relation cards used to be a poster, a relation label and a name,
 * which is what a poster *looks* like rather than what a reader needs in order to
 * decide whether to tap it. Format, how long it is, when it came out and whether it
 * is out yet are the four facts the hero chips already spell out for the title you
 * are looking at, and the one thing the row exists to answer - "is this the season I
 * have not watched, or the film I have not heard of" - is not answerable from any of
 * the three it had.
 *
 * Built from [formatLabel] and [statusLabel], not the raw AniList enums, so the row
 * cannot print `TV_SHORT` or `NOT_YET_RELEASED` next to a cleaned-up hero chip - the
 * same class of drift those two derivations exist to stop.
 *
 * The year prefers `seasonYear` and falls back to the start date's year: AniList sets
 * the season on anything it has scheduled, and a title old enough to have aired is
 * often missing it while carrying a full start date. `episodes` is only added when
 * it is a real count - a zero there is the API saying it does not know, and
 * "0 eps" is a claim rather than a gap. "eps" rather than "episodes" because the
 * whole line has to fit a 144dp card beside the format and the year.
 *
 * Status is shown only when it is *news* - airing, upcoming, on hiatus, cancelled.
 * A finished relation's status is the least interesting thing about it and its year
 * already says it, whereas a sequel that has not started is exactly the case where
 * the row should say so.
 */
val MediaRelationEdge.metaLabel: String
    get() {
        val node = node
        val parts = buildList {
            add(node.formatLabel)
            node.episodes?.takeIf { it > 0 }
                ?.let { add(if (it == 1) "1 ep" else "$it eps") }
            (node.seasonYear ?: node.startDate?.year)?.let { add(it.toString()) }
            val status = node.statusLabel
            if (status != "Finished" && status != "Unknown") add(status)
        }
        return parts.joinToString(" · ")
    }

/** `MAIN`, `SUPPORTING` or `BACKGROUND`, worded for a credit line. */
val MediaCharacterEdge.roleLabel: String
    get() = when (role?.uppercase(Locale.ROOT)) {
        "MAIN" -> "Main"
        "SUPPORTING" -> "Supporting"
        "BACKGROUND" -> "Background"
        null -> "Cast"
        else -> role.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
    }

/**
 * The viewer's progress as bare numbers - "1 / 11".
 *
 * [watchFractionLabel] already says the same thing in a sentence ("6 / 12 eps"),
 * which is right in a card body and too wordy for the corner of a progress card
 * where the fact is already labelled "Watching Progress".
 */
val MediaNode.progressCountLabel: String?
    get() {
        val watched = watchedEpisodeCount ?: return null
        val total = if (isManga) chapters else episodes
        return total?.takeIf { it > 0 }?.let { "$watched / $it" }
    }

val MediaNode.isUpcoming: Boolean get() = status == "NOT_YET_RELEASED"
val MediaNode.isAiringNow: Boolean get() = status == "RELEASING"
val MediaNode.isOnHiatus: Boolean get() = status.equals("HIATUS", ignoreCase = true)

val MediaNode.rankingList: List<MediaRanking> get() = rankings ?: emptyList()

/**
 * Whether this title has a ranking worth drawing.
 *
 * The Info page adds its rankings row as a `LazyColumn` item, and an item that
 * renders nothing still costs a slot in the list's `Arrangement.spacedBy` - a
 * zero-height item is followed by the full gap anyway, so a title with no ranking
 * ended up with a 14dp hole above the extras card. The page has to be able to ask
 * this *before* it adds the item, and it has to get the same answer the section
 * gives, so the predicate is derived here rather than in the composable.
 *
 * Equivalent to "any ranking carries a rank", which is also exactly what
 * [topRankingLabel] and the section's own guard reduce to: a ranking with a null
 * rank has neither a badge to draw nor a fallback to print.
 */
val MediaNode.hasRankings: Boolean get() = rankingList.any { it.rank != null }

/**
 * Whether this title has a progress card worth drawing.
 *
 * The two conditions are the ones [com.anisequel.ui.components.detail.info.InfoProgressCard]
 * returns early on, kept together so the page cannot disagree with the card: a title
 * the viewer has not started is not at 0%, it is not being watched at all, and a
 * 0% bar would be a claim about a thing that does not exist.
 */
val MediaNode.hasProgressCard: Boolean
    get() = watchFraction() != null && progressCountLabel != null

/**
 * The alternative-titles rows the Info page shows, as label to value.
 *
 * Deduplicated against [displayName], which is the fix for a row that was
 * duplicated on most titles rather than on some. The card used to open with a
 * "Main title" row and then list Romaji, English and Native underneath it - and
 * `displayName` *prefers* English, so on any title AniList has an English name for
 * (nearly all of them) the first two rows read:
 *
 *     Main title   Cowboy Bebop
 *     English      Cowboy Bebop
 *
 * Verbatim, one under the other, in a card whose whole job is to list the names
 * this title goes by. Where the preferred title fell back to Romaji it was the
 * Romaji row that repeated instead. Listing only the names that differ from the
 * one the hero already shows in full removes the duplicate without losing
 * anything: a title whose three names are all identical now correctly shows
 * nothing rather than the same string three times.
 *
 * The labels stay on the *named* forms rather than dropping a single
 * "Main title", because "Romaji / Cowboy Bebop" and "Native / カウボーイビバップ"
 * are the facts a reader is looking up this card for.
 */
val MediaNode.alternativeTitleRows: List<Pair<String, String>>
    get() {
        val preferred = displayName
        return buildList {
            romajiName?.let { add("Romaji" to it) }
            englishName?.let { add("English" to it) }
            nativeName?.let { add("Native" to it) }
            if (synonymList.isNotEmpty()) add("Synonyms" to synonymList.joinToString(", "))
        }.filterNot { (_, value) -> value == preferred }
    }

/** AniList's best ranking for this entry, formatted, or null. */
val MediaNode.topRankingLabel: String?
    get() {
        val best = rankingList.firstOrNull { it.rank != null } ?: return null
        val rank = best.rank ?: return null
        val context = best.context?.takeIf { it.isNotBlank() } ?: "Ranked"
        return "#$rank $context"
    }

/** The most relevant thematic tags, highest ranked first. */
val MediaNode.rankedTags: List<String>
    get() = tags
        ?.filter { it.isMediaSpoiler != true && !it.name.isNullOrBlank() }
        ?.sortedByDescending { it.rank ?: 0 }
        ?.mapNotNull { it.name }
        ?.take(MAX_TAGS) ?: emptyList()

/**
 * "Winter 2019", from AniList's `season` and `seasonYear`.
 *
 * AniList reports the two separately and `season` is an enum string, so the
 * pair is joined here rather than in the UI. Null unless AniList knows both -
 * an unannounced entry has neither.
 */
val MediaNode.seasonLabel: String?
    get() {
        val name = season?.takeIf { it.isNotBlank() } ?: return null
        val readable = name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
        return seasonYear?.let { "$readable $it" } ?: readable
    }

/**
 * How many episodes of this entry the viewer has already watched.
 *
 * AniList returns it as `mediaListEntry { progress }` for anything on their
 * list; for an entry the app is offering to add, that is only ever
 * `progress`/`current`, which is what makes a card able to say "you're 4
 * episodes in" rather than only "not on your list".
 */
val MediaNode.watchedEpisodeCount: Int? get() = mediaListEntry?.progress
val MediaNode.airingEpisodeNumber: Int? get() = nextAiringEpisode?.episode

/** AniList's `airingAt`: seconds since the epoch, not milliseconds. */
val MediaNode.airingAtSeconds: Long? get() = nextAiringEpisode?.airingAt

/** How long until the next episode, or null when nothing is scheduled. */
fun MediaNode.airingCountdown(nowMillis: Long = System.currentTimeMillis()): String? =
    AiringCountdown.format(airingAtSeconds, nowMillis)

/**
 * How far through this entry the viewer is, as a 0..1 fraction.
 *
 * Null when there is nothing meaningful to show - no total episode count, or no
 * progress recorded - so callers can skip the bar entirely rather than drawing
 * an empty one.
 */
fun MediaNode.watchFraction(): Float? {
    if (isManga) {
        val total = chapters ?: return null
        if (total <= 0) return null
        val read = watchedEpisodeCount ?: return null
        return (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
    val total = episodes ?: return null
    if (total <= 0) return null
    val watched = watchedEpisodeCount ?: return null
    return (watched.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/** "6 / 12 eps watched" or "14 / 32 chs read" - null when the viewer has not started it. */
fun MediaNode.watchFractionLabel(): String? {
    if (isManga) {
        val total = chapters ?: return null
        val read = watchedEpisodeCount ?: return null
        if (read <= 0) return null
        return "$read / $total chs read"
    }
    val total = episodes ?: return null
    val watched = watchedEpisodeCount ?: return null
    if (watched <= 0) return null
    return "$watched / $total eps"
}

/**
 * The full record once the sheet's detail query has answered, keeping the
 * viewer's progress from the summary the tap came from.
 *
 * A sequel's list node is the richer of the two and merges through
 * [MediaNode.withDetail]; an arriving or activity row is the thinner one, so
 * the fetched detail is the display record there. Progress is the exception:
 * the detail query is not viewer-scoped, so a progress the summary already knew
 * must survive.
 */
fun MediaNode.withSummaryProgress(summary: MediaNode): MediaNode =
    if (mediaListEntry != null || summary.mediaListEntry == null) this
    else copy(mediaListEntry = summary.mediaListEntry)

/** HTML tags AniList interleaves into `description`. */
private val TAG_PATTERN = Regex("<[^>]*>")

/** Runs of whitespace left behind once the tags are gone. */
private val WHITESPACE_PATTERN = Regex("\\s+")

/** How many thematic tags the detail sheet renders. */
private const val MAX_TAGS = 8

/** How many related titles the Info page's relation row draws before it stops. */
private const val MAX_RELATIONS = 12
