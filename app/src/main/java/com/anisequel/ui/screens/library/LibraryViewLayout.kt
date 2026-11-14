package com.anisequel.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.displayName
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.isManga
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.domain.list.ListStatuses
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.screens.profile.libraryProgressLabel

/**
 * The column arithmetic and row surfaces the two Library looks share.
 *
 * The classic Library used to be a fixed three-column grid no matter what the
 * Interface page said, so choosing "List" or "Shelf" changed the preview and
 * nothing on the page. Both looks now read [LibraryViewMode] through the same
 * helpers, which is what makes the setting mean one thing rather than two.
 */

/**
 * The columns a mode asks for, narrowed until a compact cover is still readable.
 *
 * Only the modes that deliberately cram covers - compact, shelf and the cover
 * wall - are narrowed. A three-column poster grid at 96dp is already at the floor
 * a cover can show a face at; reducing it further would change the mode rather
 * than make it fit. The floor is the same 68dp the new Library documented.
 */
internal fun libraryColumns(mode: LibraryViewMode, width: Dp): Int {
    if (mode != LibraryViewMode.COMPACT && mode != LibraryViewMode.SHELF) {
        return mode.columns.coerceAtLeast(1)
    }
    var columns = mode.columns
    while (columns > 3) {
        val gaps = 8.dp * (columns - 1).toFloat()
        val cell = (width - 32.dp - gaps) / columns.toFloat()
        if (cell >= 68.dp) break
        columns -= 1
    }
    return columns
}

/**
 * One title as a dense row: the List mode.
 *
 * A fixed cover column, a title block that takes the remaining width, and one
 * progress column far enough right that the numbers line up down the page. The
 * score is a separate parameter rather than a second row type: Table and List are
 * the same row with one more column, and two composables would drift at the first
 * change to either.
 */
@Composable
internal fun LibraryListRow(
    entry: MediaListEntryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Table adds the score and the format line. List does not: the mode is for
     * finding a title, and a third number on every row is noise while scanning.
     */
    detailed: Boolean = false
) {
    val media = entry.media
    val interactionSource = remember { MutableInteractionSource() }
    val coverColor = media.posterColor.toCoverColorOrNull()
    val progress = libraryProgressLabel(entry)
    val score = media.averageScore?.takeIf { it > 0 }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open ${media.displayName}",
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(if (detailed) "library_table_row" else "library_list_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(coverColor ?: MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = media.posterUrl,
                // The title below is the row's accessible name; a description on
                // the thumbnail makes TalkBack announce every title twice.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildList {
                    add(ListStatuses.label(entry.status, media.isManga))
                    if (detailed) add(media.formatLabel)
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier.widthIn(min = 54.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = progress ?: "—",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (detailed) {
                Text(
                    text = score?.let { "★ $it" } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (score != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/**
 * The heading above a status group.
 *
 * Shared with the new Library's grouped view: one heading means the two looks
 * cannot spell a status or count it differently. A heading whose count disagreed
 * with the rows under it was the visible half of the old library-filter drift.
 */
@Composable
internal fun LibraryGroupHeading(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            shape = ExpressiveShapes.pill,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/** The row of column names above a Table view, so the alignment is readable. */
@Composable
internal fun LibraryTableHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(modifier = Modifier.width(40.dp))
        Text(
            text = "Title",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Progress",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 54.dp),
            textAlign = TextAlign.End
        )
    }
}
