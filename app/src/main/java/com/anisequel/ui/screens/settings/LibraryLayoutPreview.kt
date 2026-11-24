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
import androidx.compose.ui.draw.clipToBounds
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
 * ## Why 300dp
 *
 * 168dp was one row of covers and their names. 236dp was chosen for a full row of a
 * grid "with its caption", but the arithmetic never held at the page width these
 * previews are drawn at: a two-up poster is about 210dp tall before its two caption
 * lines, so Big cards' caption - and with it the one difference between Big cards and
 * Cover wall - was clipped out of its own preview. 300dp keeps a two-up row and its
 * caption whole, and gives the list modes enough room to show the rows they promise.
 * The height is a frame, not a measurement of the device: the covers still keep their
 * own proportions inside it.
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
                .height(300.dp)
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
                            // The caption flag is the mode's own answer, not a copy
                            // written here: the Compact grid used to draw without
                            // the names the Library prints under it, so the reader
                            // choosing it was shown a caption-free page that does
                            // not exist. See `LibraryViewMode.showsCaption`.
                            LibraryViewMode.GRID -> MiniGrid(entries, 3, current.showsCaption)
                            LibraryViewMode.COMPACT -> MiniGrid(entries, 4, current.showsCaption)
                            LibraryViewMode.LIST -> MiniList(entries)
                            LibraryViewMode.GROUPED -> MiniGrouped(entries)
                            LibraryViewMode.CARDS -> MiniGrid(entries, 2, current.showsCaption)
                            LibraryViewMode.SHELF -> MiniGrid(entries, 5, current.showsCaption)
                            LibraryViewMode.TABLE -> MiniTable(entries)
                            LibraryViewMode.COVERS -> MiniCoverWall(entries)
                            LibraryViewMode.MOSAIC -> MiniMosaic(entries)
                            LibraryViewMode.GROUPED_COVERS -> MiniGroupedCovers(entries)
                            LibraryViewMode.SPOTLIGHT -> MiniSpotlight(entries)
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
 * The cover sits in a clipped slot that takes whatever the caption leaves, and the
 * caption is measured first. That is the fix for the preview that made this file
 * worth opening: the cover used to be sized by its own 2:3 ratio, which at two and
 * three across was taller than the whole frame, so the caption - the thing that
 * separates Big cards from Cover wall - was pushed out of the picture entirely.
 *
 * The progress pill is the same one the real poster prints, in the same corner and
 * colour, because it is the only mark on a Cover wall cover and the fastest thing to
 * read on a Big cards one.
 */
@Composable
private fun MiniGrid(entries: List<MediaListEntryItem>, columns: Int, showTitle: Boolean) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(columns) { index ->
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                ) {
                    MiniCover(
                        entry = previewEntry(entries, index),
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                    )
                    MiniProgressPill(
                        progress = index + 1,
                        total = 24,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                    )
                }
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
                        text = "Watching \u00b7 ${index + 1} of 24",
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
 * The progress capsule a real poster wears, drawn at the size a preview can read.
 *
 * `"12 | 24"` and `primaryContainer` are `NovaProgressPill`'s own answer, not a
 * second badge invented here - the previews used to print a plain `"4/24"` on a
 * scrim, which is a different mark from the one the Library draws.
 */
@Composable
private fun MiniProgressPill(
    progress: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$progress | $total",
        style = miniCaptionStyle(),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
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
                MiniProgressPill(
                    progress = (index + 1) * 4,
                    total = 24,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
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
        Column(modifier = Modifier.fillMaxWidth().weight(1.15f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                MiniCover(
                    entry = previewEntry(entries, 0),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.55f)
                )
                MiniProgressPill(
                    progress = 12,
                    total = 24,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            // The hero is a card too: the Library prints its name and its status
            // line like any other poster, and the preview used to show a bare
            // picture - the one thing that made Mosaic look like a banner instead
            // of the first cell of a repeating rhythm.
            Text(
                text = previewTitle(previewEntry(entries, 0)),
                style = miniCaptionStyle(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Watching \u00b7 12 of 24",
                style = miniCaptionStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(2) { index ->
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds()
                    ) {
                        MiniCover(
                            entry = previewEntry(entries, index + 1),
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                        )
                        MiniProgressPill(
                            progress = index + 4,
                            total = 24,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(3.dp)
                        )
                    }
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
            // The heading carries the count, and the pill is the app's own tone:
            // `LibraryGroupHeading` draws both, so a preview without the number
            // was showing a heading the Library never prints.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = heading.uppercase(),
                    style = miniCaptionStyle(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "3",
                    style = miniCaptionStyle(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { index ->
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clipToBounds()
                        ) {
                            MiniCover(
                                entry = previewEntry(entries, groupIndex * 3 + index),
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                            )
                            MiniProgressPill(
                                progress = groupIndex * 6 + index + 4,
                                total = 24,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        // Grouped covers is a poster grid, not a wall: the Library
                        // prints a name under every cover in this mode.
                        Text(
                            text = previewTitle(
                                previewEntry(entries, groupIndex * 3 + index)
                            ),
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
}

/**
 * Spotlight: one wide still, with the caption the Library prints underneath it.
 *
 * One cover, not a grid - the mode's column count is one, and a preview that drew a
 * row of posters under the Spotlight name would be Big cards with a different label.
 */
@Composable
private fun MiniSpotlight(entries: List<MediaListEntryItem>) {
    val entry = previewEntry(entries, 0)
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .clip(RoundedCornerShape(10.dp))
        ) {
            MiniCover(
                entry = entry,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.55f)
            )
            MiniProgressPill(
                progress = 12,
                total = 24,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = previewTitle(entry),
            style = miniBodyStyle(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Watching \u00b7 episode 12 of 24",
            style = miniCaptionStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
