package com.anisequel.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Recently updated manga on the viewer's list, used for the Manga shortcut's cover.
 *
 * A dedicated small-page query rather than [MediaListCollection]: the Home
 * shortcut needs a handful of images, and downloading a whole manga list to draw
 * a 48dp strip would make the shortcut cost more than the page it sits on. More
 * than one row so the card can show a different cover from the reader's shelf on
 * each visit.
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
