package com.anisequel.ui.screens.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.ui.components.expressive.ExpressiveMotion

/**
 * One Library mode, drawn as the page it produces.
 *
 * ## Why the preview is a page with a header
 *
 * The seven modes differ in more than density: the grouped list has headings, the
 * table has a header row, the cover wall has no captions at all. Drawn without the
 * page around them, they read as seven arrangements of the same grey rows - the one
 * thing a reader choosing a mode already knows. So the miniature opens with the
 * header every mode shares, and each mode then draws its own shape underneath: the
 * heading, the aligned columns, the captions.
 *
 * ## Why 236dp
 *
 * 168dp was one row of covers and their names. A mode with two rows of text - a list
 * row, a grouped section - was cut off inside its own preview, and the difference
 * between "list" and "grouped" was exactly the part that got cut. 236dp fits the
 * header, a full row of a grid with its caption, and three rows of either list at a
 * size the words are still readable.
 */
@Composable
internal fun LibraryViewPreview(
    mode: LibraryViewMode,
    entries: List<MediaListEntryItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Preview \u00b7 ${mode.displayName}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(236.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(10.dp)
                .testTag("library_view_preview")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LibraryPreviewHeader(mode)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Crossfade(
                        targetState = mode,
                        animationSpec = ExpressiveMotion.FastEffects,
                        label = "library_view_preview_mode"
                    ) { current ->
                        when (current) {
                            LibraryViewMode.GRID -> MiniGrid(entries, 3, true)
                            LibraryViewMode.COMPACT -> MiniGrid(entries, 4, false)
                            LibraryViewMode.LIST -> MiniList(entries)
                            LibraryViewMode.GROUPED -> MiniGrouped(entries)
                            LibraryViewMode.CARDS -> MiniGrid(entries, 2, true)
                            LibraryViewMode.SHELF -> MiniGrid(entries, 5, false)
                            LibraryViewMode.TABLE -> MiniTable(entries)
                            LibraryViewMode.COVERS -> MiniCoverWall(entries)
                            LibraryViewMode.MOSAIC -> MiniMosaic(entries)
                            LibraryViewMode.GROUPED_COVERS -> MiniGroupedCovers(entries)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The bar every mode sits under, with the mode's own density in it.
 *
 * The count on the right is the one thing the header can say that a picture cannot:
 * five across a shelf is a decision about how many titles fit a screen, and "5
 * across" is the number the reader would use to describe it.
 */
@Composable
private fun LibraryPreviewHeader(mode: LibraryViewMode) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "My library",
                style = miniBodyStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "Preview of your library",
                style = miniCaptionStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = if (mode.columns <= 1) mode.displayName else "${mode.columns} across",
            style = miniCaptionStyle(),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

/**
 * The poster modes: one row of covers, each with its caption.
 *
 * The caption carries the progress line as well as the name, because that is what
 * separates the poster grid from the cover wall in the real page - and because a
 * reader picking a density is also picking whether a caption will have room to show
 * the number.
 */
@Composable
private fun MiniGrid(entries: List<MediaListEntryItem>, columns: Int, showTitle: Boolean) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(columns) { index ->
            Column(modifier = Modifier.weight(1f)) {
                MiniCover(
                    entry = previewEntry(entries, index),
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                )
                if (showTitle) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = previewTitle(previewEntry(entries, index)),
                        style = miniCaptionStyle(),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${index + 1}/24 \u00b7 ${88 - index * 4}%",
                        style = miniCaptionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * The grouped mode, drawn as two headings over two rows each.
 *
 * A miniature of the real thing: the heading carries the same weight and a count, so
 * the choice is "a list with sections" rather than "another density of grid". The
 * preview box is too short for all six statuses, and showing the first two is exactly
 * what the first screen of the real list does.
 */
@Composable
private fun MiniGrouped(entries: List<MediaListEntryItem>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        listOf("Watching", "Completed").forEachIndexed { groupIndex, heading ->
            Text(
                text = "$heading \u00b7 2".uppercase(),
                style = miniCaptionStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = if (groupIndex == 0) 0.dp else 2.dp)
            )
            repeat(2) { rowIndex ->
                val entry = previewEntry(entries, groupIndex * 2 + rowIndex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniCover(
                        entry = entry,
                        modifier = Modifier.width(26.dp).fillMaxHeight()
                    )
                    Column(modifier = Modifier.padding(horizontal = 7.dp)) {
                        Text(
                            text = previewTitle(entry),
                            style = miniCaptionStyle(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Episode ${rowIndex * 6 + 4} of 24",
                            style = miniCaptionStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * A table miniature: one header and three aligned rows.
 *
 * The point of the mode is that the numbers line up, so the preview draws the header
 * even at this size. Three rows is enough to see a column; a fourth would be a smudge.
 */
@Composable
private fun MiniTable(entries: List<MediaListEntryItem>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(28.dp))
            Text(
                text = "TITLE",
                style = miniCaptionStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "PROGRESS",
                style = miniCaptionStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "SCORE",
                style = miniCaptionStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        repeat(3) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniCover(
                    entry = previewEntry(entries, index),
                    modifier = Modifier.width(28.dp).fillMaxHeight()
                )
                Text(
                    text = previewTitle(previewEntry(entries, index)),
                    style = miniCaptionStyle(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 7.dp)
                )
                Text(
                    text = "${index * 6 + 12}/24",
                    style = miniCaptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${88 - index * 5}%",
                    style = miniCaptionStyle(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, end = 7.dp)
                )
            }
        }
    }
}

/** Three caption-free covers, the cover wall's whole argument. */
@Composable
private fun MiniCoverWall(entries: List<MediaListEntryItem>) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(3) { index ->
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                MiniCover(
                    entry = previewEntry(entries, index),
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "${(index + 1) * 4}/24",
                    style = miniCaptionStyle(),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * The list mode: a cover, a name, and the line under it.
 *
 * The grey bar this replaces said "there is a second line". A real progress line says
 * what the second line is and how far it sits from the name, which is what a reader
 * comparing List with Table is actually looking at.
 */
@Composable
private fun MiniList(entries: List<MediaListEntryItem>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(3) { index ->
            val entry = previewEntry(entries, index)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniCover(
                    entry = entry,
                    modifier = Modifier.width(36.dp).fillMaxHeight()
                )
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = previewTitle(entry),
                        style = miniBodyStyle(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (index == 0) {
                            "Watching \u00b7 episode 12 of 24"
                        } else {
                            "Completed \u00b7 ${88 - index * 5}%"
                        },
                        style = miniCaptionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Mosaic: one wide anchor cover over a row of two.
 *
 * The preview has room for one repetition of the pattern, which is enough - the
 * thing being chosen is the rhythm, and a second hero row would show the same
 * rhythm twice.
 */
@Composable
private fun MiniMosaic(entries: List<MediaListEntryItem>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.15f)
                .clip(RoundedCornerShape(10.dp))
        ) {
            MiniCover(
                entry = previewEntry(entries, 0),
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(2) { index ->
                Column(modifier = Modifier.weight(1f)) {
                    MiniCover(
                        entry = previewEntry(entries, index + 1),
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = previewTitle(previewEntry(entries, index + 1)),
                        style = miniCaptionStyle(),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Grouped covers: the status headings of Grouped, with poster rows under them.
 *
 * Two headings with one row each rather than one heading with two: the mode's whole
 * claim is that the wall is *sorted*, and a single section cannot show that.
 */
@Composable
private fun MiniGroupedCovers(entries: List<MediaListEntryItem>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        listOf("Watching", "Completed").forEachIndexed { groupIndex, heading ->
            Text(
                text = heading.uppercase(),
                style = miniCaptionStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { index ->
                    MiniCover(
                        entry = previewEntry(entries, groupIndex * 3 + index),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}
