package com.example.data.model

/**
 * The relationship AniList reports between two entries that the user is being
 * shown. AniList has more relation kinds than this; these are the ones that
 * mean "a franchise entry this person's list is missing".
 */
enum class RelationKind(val apiValue: String, val displayName: String) {
    SEQUEL("SEQUEL", "Sequels"),
    PREQUEL("PREQUEL", "Prequels"),
    SIDE_STORY("SIDE_STORY", "Side stories"),
    SPIN_OFF("SPIN_OFF", "Spin-offs");

    companion object {
        /** Unknown relation types fall back to SEQUEL rather than vanishing. */
        fun fromApi(value: String?): RelationKind =
            entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) } ?: SEQUEL
    }
}

data class MissedSequel(
    val parentId: Int,
    val parentTitle: String,
    val sequelMedia: MediaNode,
    /** AniList's own relation name: SEQUEL, PREQUEL, SIDE_STORY, SPIN_OFF... */
    val relationType: String = RelationKind.SEQUEL.apiValue,
    val isAddingToPlanning: Boolean = false,
    val isAddedToPlanning: Boolean = false
) {
    val sequelId: Int get() = sequelMedia.id
    val sequelTitle: String get() = sequelMedia.title?.displayTitle ?: "Unknown Sequel"
    val sequelCoverUrl: String? get() = sequelMedia.coverImage?.bestUrl
    val bannerUrl: String? get() = sequelMedia.bannerImage
    val format: String get() = sequelMedia.format ?: "ANIME"
    val status: String get() = sequelMedia.status ?: "UNKNOWN"
    val episodes: String get() = sequelMedia.episodes?.let { "$it eps" } ?: "Episodes TBA"
    val score: String get() = sequelMedia.averageScore?.let { "$it%" } ?: "N/A"
    val releaseDate: String get() = sequelMedia.startDate?.formatted ?: "TBA"
    val genres: List<String> get() = sequelMedia.genres ?: emptyList()
    val studioName: String? get() = sequelMedia.studios?.nodes?.firstOrNull()?.name
    val siteUrl: String get() = sequelMedia.siteUrl ?: "https://anilist.co/anime/$sequelId"
    val source: String?
        get() = sequelMedia.source?.replace('_', ' ')?.lowercase()?.split(' ')
            ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    val duration: String?
        get() = sequelMedia.duration?.let { "$it mins / ep" }
    val trailerUrl: String?
        get() = sequelMedia.trailer?.youtubeUrl
    val synonyms: List<String>
        get() = sequelMedia.synonyms?.filter { it.isNotBlank() } ?: emptyList()
    val meanScore: String?
        get() = sequelMedia.meanScore?.let { "$it%" }

    /**
     * The synopsis, with AniList's HTML stripped.
     *
     * The regex is a compiled constant rather than a literal at each call site:
     * this runs once per visible card on every recomposition while the filter
     * chips animate, and `Regex("…")` in a property body compiles a new pattern
     * each time rather than reusing one.
     */
    val description: String?
        get() = sequelMedia.description
            ?.replace(TAG_PATTERN, "")
            ?.replace(WHITESPACE_PATTERN, " ")
            ?.trim()

    /**
     * AniList's dominant colour for this entry's artwork, as `#RRGGBB`.
     *
     * Used to tint the placeholder behind a poster that has not loaded. AniList
     * computes it from the key visual, so a card waiting on art is recognisably
     * the right show instead of a grey rectangle.
     */
    val coverColor: String? get() = sequelMedia.coverImage?.color

    val nextEpisodeNumber: Int? get() = sequelMedia.nextAiringEpisode?.episode
    val nextAiringAt: Long? get() = sequelMedia.nextAiringEpisode?.airingAt

    /**
     * How many episodes of *this* entry the viewer has already watched.
     *
     * AniList returns it as `mediaListEntry { status }` for anything on their
     * list; for an entry the app is offering to add, that is only ever
     * `progress`/`current`, which is what makes a card able to say "you're 4
     * episodes in" rather than only "not on your list".
     */
    val watchedEpisodes: Int? get() = sequelMedia.mediaListEntry?.progress

    /**
     * `airingAt` is seconds since the epoch, from AniList.
     */
    fun nextAiringCountdown(nowMillis: Long = System.currentTimeMillis()): String? {
        val airingAt = nextAiringAt ?: return null
        // AniList reports seconds; the arithmetic below is in milliseconds.
        val airingAtMillis = airingAt * 1000L
        val remaining = airingAtMillis - nowMillis
        if (remaining <= 0L) return null

        val minutes = remaining / 60_000
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days >= 1 -> "in ${days}d"
            hours >= 1 -> "in ${hours}h"
            minutes >= 1 -> "in ${minutes}m"
            else -> "now"
        }
    }

    /**
     * How far through this entry the viewer is, as a 0..1 fraction.
     *
     * Null when there is nothing meaningful to show - no total episode count, or
     * no progress recorded - so callers can skip the bar entirely rather than
     * drawing an empty one.
     */
    fun watchProgress(): Float? {
        val total = sequelMedia.episodes ?: return null
        if (total <= 0) return null
        val watched = watchedEpisodes ?: return null
        return (watched.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /** "6 / 12 eps watched" - null when the viewer has not started it. */
    fun watchProgressLabel(): String? {
        val total = sequelMedia.episodes ?: return null
        val watched = watchedEpisodes ?: return null
        if (watched <= 0) return null
        return "$watched / $total eps"
    }

    /**
     * "Winter 2019", from AniList's `season` and `seasonYear`.
     *
     * AniList reports the two separately and `season` is an enum string, so the
     * pair is joined here rather than in the UI. Null unless AniList knows both
     * - an unannounced entry has neither.
     */
    val airingSeason: String?
        get() {
            val name = sequelMedia.season?.takeIf { it.isNotBlank() } ?: return null
            val readable = name.lowercase().replaceFirstChar { it.uppercase() }
            return sequelMedia.seasonYear?.let { "$readable $it" } ?: readable
        }

    val isUnreleased: Boolean
        get() = status == "NOT_YET_RELEASED"

    val isAiring: Boolean
        get() = status == "RELEASING"

    val isHiatus: Boolean
        get() = status.equals("HIATUS", ignoreCase = true)

    val rankings: List<MediaRanking>
        get() = sequelMedia.rankings ?: emptyList()

    val topRanking: String?
        get() {
            val best = rankings.firstOrNull { it.rank != null } ?: return null
            val rankNum = best.rank ?: return null
            val context = best.context?.takeIf { it.isNotBlank() } ?: "Ranked"
            return "#$rankNum $context"
        }

    val topTags: List<String>
        get() = sequelMedia.tags
            ?.filter { it.isMediaSpoiler != true && !it.name.isNullOrBlank() }
            ?.sortedByDescending { it.rank ?: 0 }
            ?.mapNotNull { it.name }
            ?.take(8) ?: emptyList()

    /**
     * How this entry relates to the one the user watched.
     *
     * A prequel is not a sequel: "you finished season 2 but never saw season 1"
     * is a real gap, and calling it "Sequel to: Season 2" would be wrong. The
     * label is derived so the UI cannot get it backwards.
     */
    val relationLabel: String
        get() = when (RelationKind.fromApi(relationType)) {
            RelationKind.PREQUEL -> "Prequel to"
            RelationKind.SIDE_STORY -> "Side story of"
            RelationKind.SPIN_OFF -> "Spin-off of"
            RelationKind.SEQUEL -> "Sequel to"
        }

    /** True when the user's list is missing an earlier entry in the franchise. */
    val isEarlierInFranchise: Boolean
        get() = RelationKind.fromApi(relationType) == RelationKind.PREQUEL

    /** This entry with the lazily-fetched description, banner and studio folded in. */
    fun withDetail(detail: MediaNode): MissedSequel =
        copy(sequelMedia = sequelMedia.withDetail(detail))

    private companion object {
        /** HTML tags AniList interleaves into `description`. */
        val TAG_PATTERN = Regex("<[^>]*>")

        /** Runs of whitespace left behind once the tags are gone. */
        val WHITESPACE_PATTERN = Regex("\\s+")
    }
}
