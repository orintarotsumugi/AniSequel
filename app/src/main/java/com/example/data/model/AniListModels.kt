package com.example.data.model

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

@JsonClass(generateAdapter = true)
data class UserStatistics(
    val anime: AnimeStats? = null
)

@JsonClass(generateAdapter = true)
data class AnimeStats(
    val count: Int? = null,
    val episodesWatched: Int? = null,
    val minutesWatched: Long? = null
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
    val media: MediaNode
)

@JsonClass(generateAdapter = true)
data class MediaNode(
    val id: Int,
    val title: MediaTitle? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
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
    val synonyms: List<String>? = null,
    val meanScore: Int? = null,
    val rankings: List<MediaRanking>? = null,
    val tags: List<MediaTag>? = null
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
        description = detail.description ?: description,
        bannerImage = detail.bannerImage ?: bannerImage,
        studios = detail.studios ?: studios,
        source = detail.source ?: source,
        duration = detail.duration ?: duration,
        trailer = detail.trailer ?: trailer,
        synonyms = detail.synonyms ?: synonyms,
        meanScore = detail.meanScore ?: meanScore,
        rankings = detail.rankings ?: rankings,
        tags = detail.tags ?: tags,
        coverImage = when {
            detail.coverImage?.large != null -> coverImage?.copy(large = detail.coverImage.large) ?: detail.coverImage
            else -> coverImage
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

@JsonClass(generateAdapter = true)
data class StudioConnection(
    val nodes: List<StudioNode>? = null
)

@JsonClass(generateAdapter = true)
data class StudioNode(
    val id: Int? = null,
    val name: String? = null
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

@JsonClass(generateAdapter = true)
data class SimpleMediaListEntry(
    val id: Int? = null,
    val status: String? = null,
    val mediaId: Int? = null,
    /** Episodes watched. Lets a card say "you're 4 episodes in" rather than only "not on your list". */
    val progress: Int? = null,
    val score: Double? = null
)

@JsonClass(generateAdapter = true)
data class SaveMediaListEntryData(
    @Json(name = "SaveMediaListEntry") val entry: SimpleMediaListEntry?
)
