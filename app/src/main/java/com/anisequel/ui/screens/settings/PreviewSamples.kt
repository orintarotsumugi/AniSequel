package com.anisequel.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.displayName
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
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
        AsyncImage(
            model = entry?.media?.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

internal fun previewTitle(entry: MediaListEntryItem?): String =
    entry?.media?.displayName ?: "Dandadan"

/**
 * One of the reader's entries for preview slot [index], cycling when there are
 * fewer entries than slots.
 *
 * The Shelf preview used to draw `entries.getOrNull(4)` and showed two empty
 * gradient slots whenever the dashboard held fewer than five covers - the mode
 * looked broken at exactly the moment the reader had a small library, which is
 * when they are most likely to be choosing a view. Repeating real covers is
 * honest preview arithmetic, not invented content: every slot is a title they
 * own, and an empty library still falls back to the themed placeholder.
 */
internal fun previewEntry(
    entries: List<MediaListEntryItem>,
    index: Int
): MediaListEntryItem? =
    if (entries.isEmpty()) null else entries[index % entries.size]
