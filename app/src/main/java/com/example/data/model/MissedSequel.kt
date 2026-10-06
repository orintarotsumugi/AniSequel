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

    /**
     * The watched entry's cover, so a card can show what this is a sequel *to*.
     *
     * Free, and deliberately so: the parent is the entry the relation edge was
     * walked from, and it is already in hand when this is built. The shape
     * previously carried only the parent's id and title, so the UI had to
     * render "Sequel to Some Show" with nothing to look at - and the obvious
     * fix, a second query for the parent's cover, would have spent AniList
     * budget on a picture it already sent us.
     */
    val parentCoverUrl: String? = null,

    /** AniList's dominant colour for the parent's key visual. */
    val parentCoverColor: String? = null,

    /**
     * The year the *viewer* marked the parent finished, or null when they
     * never did.
     *
     * From the entry's `completedAt`, so it answers "when did I complete
     * this?" rather than "when did this air?" - which is what the filter
     * asking the question means.
     */
    val parentCompletedYear: Int? = null,
    val sequelMedia: MediaNode,
    /** AniList's own relation name: SEQUEL, PREQUEL, SIDE_STORY, SPIN_OFF... */
    val relationType: String = RelationKind.SEQUEL.apiValue,
    val isAddingToPlanning: Boolean = false,
    val isAddedToPlanning: Boolean = false
) {
    // Derived values that walk the whole media node, memoised per instance.
    //
    // These are read inside `SequelCard`, which is the single hottest composable
    // in the app - it runs for every visible row, on every recomposition, and a
    // recomposition happens on every scroll frame and every keystroke while the
    // filter chips animate. Recomputing them from scratch each time meant the
    // description's regex ran twice, the tag list was filtered, sorted and
    // mapped again, and the ranking and season strings were rebuilt - per card,
    // per frame. `by lazy` computes each exactly once per instance and the
    // result is reused until the entry itself changes (a detail load, a status
    // toggle), at which point it is a new instance anyway.
    //
    // `by lazy` on a data class is safe here because every one of these reads
    // only `val` fields and is therefore genuinely immutable. SYNCHRONIZED is
    // the default and is what keeps this correct when a detail load patches a
    // card from a background dispatcher while the main thread is drawing it.

    /** The synopsis, with AniList's HTML stripped. See [description]. */
    private val cachedDescription: String? by lazy { buildDescription() }

    /** The most relevant thematic tags, highest ranked first. */
    private val cachedTopTags: List<String> by lazy {
        sequelMedia.tags
            ?.filter { it.isMediaSpoiler != true && !it.name.isNullOrBlank() }
            ?.sortedByDescending { it.rank ?: 0 }
            ?.mapNotNull { it.name }
            ?.take(MAX_TAGS) ?: emptyList()
    }

    /** AniList's best ranking for this entry, formatted, or null. */
    private val cachedTopRanking: String? by lazy {
        val best = rankings.firstOrNull { it.rank != null } ?: return@lazy null
        val rankNum = best.rank ?: return@lazy null
        val context = best.context?.takeIf { it.isNotBlank() } ?: "Ranked"
        "#$rankNum $context"
    }

    /** "Winter 2019", from AniList's `season` and `seasonYear`. */
    private val cachedAiringSeason: String? by lazy {
        val name = sequelMedia.season?.takeIf { it.isNotBlank() } ?: return@lazy null
        val readable = name.lowercase(java.util.Locale.ROOT).replaceFirstChar { it.uppercase(java.util.Locale.ROOT) }
        sequelMedia.seasonYear?.let { "$readable $it" } ?: readable
    }

    /**
     * Everything the search box matches against, lowercased exactly once.
     *
     * A keystroke runs `contains` over this for every candidate on the list,
     * and the old code rebuilt a lowercased copy of six fields per candidate
     * per keystroke - on a large account that was the single most repeated
     * string work in the app. The fields are joined with '\n' rather than a
     * space because a space can be the end of one title and the start of the
     * next: without a separator a query could match across a field boundary
     * that no single field contains. The single-line search field cannot
     * produce a '\n' of its own, so a query never spans them either.
     */
    private val cachedSearchableText: String by lazy {
        val titleFields = listOfNotNull(
            sequelMedia.title?.romaji,
            sequelMedia.title?.english,
            sequelMedia.title?.native
        )
        (listOf(sequelTitle, parentTitle) + titleFields + synonyms)
            .joinToString(separator = "\n") { it.lowercase(java.util.Locale.ROOT) }
    }

    /**
     * [cachedSearchableText], for [com.example.domain.usecase.FindMissedSequelsUseCase.applyFilters].
     */
    val searchableText: String
        get() = cachedSearchableText

    val sequelId: Int get() = sequelMedia.id
    val sequelTitle: String get() = sequelMedia.title?.displayTitle ?: "Unknown Sequel"
    val englishTitle: String? get() = sequelMedia.title?.english?.takeIf { it.isNotBlank() }
    val romajiTitle: String? get() = sequelMedia.title?.romaji?.takeIf { it.isNotBlank() }
    val nativeTitle: String? get() = sequelMedia.title?.native?.takeIf { it.isNotBlank() }
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
        get() = sequelMedia.source?.replace('_', ' ')?.lowercase(java.util.Locale.ROOT)?.split(' ')
            ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase(java.util.Locale.ROOT) } }
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
        get() = cachedDescription

    private fun buildDescription(): String? =
        sequelMedia.description
            ?.replace(TAG_PATTERN, "")
            ?.replace(WHITESPACE_PATTERN, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

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
     * How long until the next episode, or null when nothing is scheduled.
     *
     * Delegates to [AiringCountdown], which the arriving section shares - see
     * the note there on why this is one implementation.
     */
    fun nextAiringCountdown(nowMillis: Long = System.currentTimeMillis()): String? =
        AiringCountdown.format(nextAiringAt, nowMillis)

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
        get() = cachedAiringSeason

    val isUnreleased: Boolean
        get() = status == "NOT_YET_RELEASED"

    val isAiring: Boolean
        get() = status == "RELEASING"

    val isHiatus: Boolean
        get() = status.equals("HIATUS", ignoreCase = true)

    val rankings: List<MediaRanking>
        get() = sequelMedia.rankings ?: emptyList()

    val topRanking: String?
        get() = cachedTopRanking

    val topTags: List<String>
        get() = cachedTopTags

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

        /** How many thematic tags the detail sheet renders. */
        const val MAX_TAGS = 8
    }
}
