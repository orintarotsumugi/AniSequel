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
    val description: String? get() = sequelMedia.description?.replace(Regex("<[^>]*>"), "")?.trim()
    val coverColor: String? get() = sequelMedia.coverImage?.color
    
    val nextEpisodeNumber: Int? get() = sequelMedia.nextAiringEpisode?.episode
    val nextAiringAt: Long? get() = sequelMedia.nextAiringEpisode?.airingAt

    val isUnreleased: Boolean
        get() = status == "NOT_YET_RELEASED"
        
    val isAiring: Boolean
        get() = status == "RELEASING"

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
}
