package com.anisequel.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.namespaceKey
import com.anisequel.data.model.posterUrl
import com.anisequel.domain.list.ListStatuses
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaProgressPill
import com.anisequel.ui.components.nova.NovaSurface
import com.anisequel.ui.navigation.mainBarClearance
import com.anisequel.ui.screens.profile.PendingDataNote
import com.anisequel.ui.screens.profile.libraryProgressLabel

/**
 * The surfaces the new Library scrolls: a list, a grid, and the two rows they
 * draw. Split from [NovaLibraryScreen] so that file is the page's state and
 * layout rather than 300 lines of item drawing.
 */
@Composable
internal fun NovaLibraryList(
    visible: List<MediaListEntryItem>,
    isManga: Boolean,
    awaitingAnime: Boolean,
    scopedError: String?,
    onOpenDetail: (MediaNode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = mainBarClearance() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        notesFor(awaitingAnime = awaitingAnime, scopedError = scopedError)
            .forEach { note ->
                item(key = "note_$note") {
                    PendingDataNote(note)
                }
            }
        items(
            items = visible,
            key = { it.media.namespaceKey },
            contentType = { "library_row" }
        ) { entry ->
            NovaLibraryRow(
                entry = entry,
                isManga = isManga,
                onClick = { onOpenDetail(entry.media) },
                modifier = Modifier.animateItem(
                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                )
            )
        }
    }
}

@Composable
internal fun NovaLibraryGrid(
    visible: List<MediaListEntryItem>,
    isManga: Boolean,
    columns: Int,
    compact: Boolean,
    awaitingAnime: Boolean,
    scopedError: String?,
    gridState: LazyGridState,
    onOpenDetail: (MediaNode) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .testTag("nova_library_grid"),
        state = gridState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = mainBarClearance() + 20.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        notesFor(awaitingAnime = awaitingAnime, scopedError = scopedError)
            .forEach { note ->
                item(
                    key = "note_$note",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    PendingDataNote(note)
                }
            }
        gridItems(
            items = visible,
            key = { it.media.namespaceKey },
            contentType = { "library_poster" }
        ) { entry ->
            NovaPoster(
                entry = entry,
                isManga = isManga,
                compact = compact,
                onClick = { onOpenDetail(entry.media) },
                modifier = Modifier.animateItem(
                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                )
            )
        }
    }
}

/**
 * The waiting and failure notes a populated list still carries above its rows.
 *
 * Returned as strings because the grid and the list place them differently - one
 * full-span grid item, one list item - and the wording is the part that must not
 * diverge.
 */
private fun notesFor(awaitingAnime: Boolean, scopedError: String?): List<String> =
    buildList {
        if (awaitingAnime) {
            add("Still fetching this list - these counts are not final yet")
        }
        if (scopedError != null && !awaitingAnime) {
            add("Couldn't load this list - $scopedError")
        }
    }

/**
 * One cover in the new grid.
 *
 * The progress capsule is on the cover, top-left, exactly as the reference draws
 * it. The title is under the artwork, not over it: a second caption on a cover
 * that already carries its own typography is the failure `ProfileLibraryTab`
 * documents. Semibold, two lines - a bold label that clips at the first line
 * makes two seasons look like the same title.
 */
@Composable
private fun NovaPoster(
    entry: MediaListEntryItem,
    isManga: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = entry.media
    val interactionSource = remember { MutableInteractionSource() }
    val total = if (isManga) media.chapters else media.episodes
    val completed = entry.status.equals("COMPLETED", ignoreCase = true)
    val progress = entry.progress ?: if (completed) total else null

    Column(
        modifier = modifier
            .bouncyPress(pressedScale = 0.95f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("nova_library_poster")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // A ratio rather than a fixed height: the grid is three columns
                // or four, so the same card is measured at two widths - and a
                // height literal would turn the wider one into a portrait and the
                // narrower into a strip.
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
                .background(media.coverImage?.color.toCoverColorOrNull() ?: Color.Transparent)
        ) {
            AsyncImage(
                model = media.posterUrl,
                contentDescription = media.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (progress != null) {
                NovaProgressPill(
                    progress = progress,
                    total = total,
                    isManga = isManga,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = media.displayName,
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The same entry as a row, for the List density.
 *
 * This is the mode that makes a long library readable rather than browsable:
 * progress, status and score are all in one line, and the cover is a thumbnail
 * rather than the page.
 */
@Composable
private fun NovaLibraryRow(
    entry: MediaListEntryItem,
    isManga: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = entry.media
    val interactionSource = remember { MutableInteractionSource() }
    val score = media.averageScore?.takeIf { it > 0 }
    val progressLabel = libraryProgressLabel(entry)

    NovaSurface(
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("nova_library_row"),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(media.coverImage?.color.toCoverColorOrNull() ?: Color.Transparent)
            ) {
                AsyncImage(
                    model = media.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = ExpressiveShapes.pill,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = ListStatuses.label(entry.status, isManga),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                    if (progressLabel != null) {
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            if (score != null) {
                Text(
                    text = "★ $score",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
