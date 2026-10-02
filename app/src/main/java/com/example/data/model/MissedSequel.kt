package com.example.data.model

data class MissedSequel(
    val parentId: Int,
    val parentTitle: String,
    val parentCoverUrl: String?,
    val parentStatus: String?,
    val sequelMedia: MediaNode,
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
}
