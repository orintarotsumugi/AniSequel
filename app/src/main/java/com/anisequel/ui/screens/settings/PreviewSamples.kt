package com.anisequel.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anisequel.data.model.MediaCoverImage
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.displayName
import com.anisequel.data.model.posterColor
import com.anisequel.ui.components.cards.cardPosterUrl
import com.anisequel.ui.components.cards.toCoverColorOrNull

@Composable
internal fun MiniCover(
    entry: MediaListEntryItem?,
    modifier: Modifier = Modifier
) {
    // A preview with no library used to draw four flat grey boxes, so the reader
    // choosing a layout saw placeholders rather than poster-shaped covers. The
    // gradient is the same pair the app uses for cover fallbacks; it keeps the
    // shape readable without inventing a fake title.
    val cover = entry?.media?.posterColor.toCoverColorOrNull()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (cover != null) {
                    Modifier.background(cover)
                } else {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    )
                }
            )
    ) {
        // The same source the grid would draw. A preview that showed AniList
        // art while the reader's library showed btttr's would be answering a
        // question about a layout with somebody else's picture.
        // An empty library still gets a poster. The gradient below was a
        // placeholder for a cover, but every layout preview in Settings reads
        // this one cell, so an empty list made all of them look like the same
        // grey card and hid the difference between the modes.
        val url = entry?.media?.let { media -> cardPosterUrl(media) }
        val context = LocalContext.current
        // A remembered request with a crossfade, the same one MediaPosterCell
        // builds. An inline URL string is a new request on every recomposition,
        // so a cover could never hit Coil's cache and the previews flickered
        // white between frames while loading; the crossfade now dissolves the
        // artwork in over the gradient instead of popping it.
        val request = remember(url, context) {
            url?.let { ImageRequest.Builder(context).data(it).crossfade(true).build() }
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

internal fun previewTitle(entry: MediaListEntryItem?): String =
    entry?.media?.displayName ?: "Sample title"

/**
 * Sample entries for previews that have no real library data to draw from.
 *
 * Onboarding and an empty library need to show what a layout looks like, not a
 * row of anonymous placeholders: a title and a cover tint are enough for the
 * minis to demonstrate structure without pretending these are the reader's own
 * entries. Real entries always win when present.
 */
private val samplePreviewEntries: List<MediaListEntryItem> = listOf(
    samplePreviewEntry(
        id = 1,
        title = "Example Series",
        color = "#5B8DEF"
    ),
    samplePreviewEntry(
        id = 2,
        title = "Another Title",
        color = "#E06B8B"
    ),
    samplePreviewEntry(
        id = 3,
        title = "Sample Show",
        color = "#4DB6AC"
    )
)

private fun samplePreviewEntry(id: Int, title: String, color: String): MediaListEntryItem =
    MediaListEntryItem(
        id = id,
        progress = if (id == 1) 12 else 0,
        media = MediaNode(
            id = id,
            title = MediaTitle(english = title, romaji = title, userPreferred = title),
            coverImage = MediaCoverImage(color = color),
            episodes = 24
        )
    )

/**
 * One of the reader's entries for preview slot [index], cycling when there are
 * fewer entries than slots.
 *
 * The Shelf preview used to draw `entries.getOrNull(4)` and showed two empty
 * gradient slots whenever the dashboard held fewer than five covers - the mode
 * looked broken at exactly the moment the reader had a small library, which is
 * when they are most likely to be choosing a view. Repeating real covers is
 * honest preview arithmetic, not invented content: every slot is a title they
 * own. When there is no library yet, sample entries keep the layout previews
 * readable instead of collapsing to anonymous placeholders.
 */
internal fun previewEntry(
    entries: List<MediaListEntryItem>,
    index: Int
): MediaListEntryItem? {
    val source = if (entries.isEmpty()) samplePreviewEntries else entries
    return source[index % source.size]
}
