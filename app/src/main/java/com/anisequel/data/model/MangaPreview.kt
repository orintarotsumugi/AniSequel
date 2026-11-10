package com.anisequel.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The first manga on the viewer's list, used as the Manga shortcut's cover.
 *
 * A dedicated one-row query rather than [MediaListCollection]: the Home shortcut
 * needs one image and a title, and downloading a whole manga list to draw a 48dp
 * strip would make the shortcut cost more than the page it sits on.
 */
@JsonClass(generateAdapter = true)
data class MangaPreviewData(
    @Json(name = "Page") val page: MangaPreviewPage? = null
)

@JsonClass(generateAdapter = true)
data class MangaPreviewPage(
    val mediaList: List<MangaPreviewEntry>? = null
)

@JsonClass(generateAdapter = true)
data class MangaPreviewEntry(
    val media: MediaNode? = null
)
