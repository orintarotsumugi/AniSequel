package com.anisequel.ui.screens.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.ui.components.expressive.ExpressiveMotion

@Composable
internal fun LibraryViewPreview(
    mode: LibraryViewMode,
    entries: List<MediaListEntryItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Preview · ${mode.displayName}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(10.dp)
                .testTag("library_view_preview")
        ) {
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
                }
            }
        }
    }
}

@Composable
private fun MiniGrid(entries: List<MediaListEntryItem>, columns: Int, showTitle: Boolean) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(columns) { index ->
            Column(modifier = Modifier.weight(1f)) {
                MiniCover(
                    entry = entries.getOrNull(index),
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                )
                if (showTitle) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = previewTitle(entries.getOrNull(index)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * The grouped mode, drawn as two headings over two rows each.
 *
 * A miniature of the real thing: the heading carries the same weight and a
 * count, so the choice is "a list with sections" rather than "another density of
 * grid". The preview box is too short for all six statuses, and showing the first
 * two is exactly what the first screen of the real list does.
 */
@Composable
private fun MiniGrouped(entries: List<MediaListEntryItem>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        listOf("Watching", "Completed").forEachIndexed { groupIndex, heading ->
            Text(
                text = "$heading · 2".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = if (groupIndex == 0) 0.dp else 2.dp)
            )
            repeat(2) { rowIndex ->
                val entry = entries.getOrNull(groupIndex * 2 + rowIndex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniCover(
                        entry = entry,
                        modifier = Modifier.width(22.dp).fillMaxHeight()
                    )
                    Text(
                        text = previewTitle(entry),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniList(entries: List<MediaListEntryItem>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        repeat(3) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniCover(
                    entry = entries.getOrNull(index),
                    modifier = Modifier.width(34.dp).fillMaxHeight()
                )
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = previewTitle(entries.getOrNull(index)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}
