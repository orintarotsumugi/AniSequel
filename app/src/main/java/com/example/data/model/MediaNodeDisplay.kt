package com.example.data.model

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

val MediaNode.formatLabel: String get() = format ?: if (isManga) "MANGA" else "ANIME"

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
val MediaNode.studioName: String? get() = studios?.nodes?.firstOrNull()?.name

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

val MediaNode.isUpcoming: Boolean get() = status == "NOT_YET_RELEASED"
val MediaNode.isAiringNow: Boolean get() = status == "RELEASING"
val MediaNode.isOnHiatus: Boolean get() = status.equals("HIATUS", ignoreCase = true)

val MediaNode.rankingList: List<MediaRanking> get() = rankings ?: emptyList()

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
