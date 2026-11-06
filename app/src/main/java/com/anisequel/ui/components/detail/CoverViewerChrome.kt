package com.anisequel.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.statusLabel
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveShapes

/**
 * The viewer's fixed furniture: the header row, the format chips, the save and
 * close controls.
 *
 * Split from `FullScreenCoverViewer` because it is the half that never touches
 * the zoom, the pager or the entrance geometry. It reads the *state* the viewer
 * owns - which source, which page, whether a save is in flight - and draws it;
 * that is a different reason to change from the window choreography next door.
 *
 * A save's lifecycle lives here too, because the button's glyph is its whole
 * acknowledgement: making the enum public to another package would invite a
 * second reader of a state that has exactly one display.
 */

/** The viewer's header: what the artwork is, and the two ways out of it. */
@Composable
internal fun CoverViewerHeader(
    media: MediaNode,
    saveState: CoverSaveState,
    source: CoverSource?,
    currentPage: Int,
    onDismiss: () -> Unit,
    startSave: () -> Unit
) {
    val imageCount = source?.urls?.size ?: 0
    val onScreen = source?.urls?.getOrNull(currentPage) != null
    CoverViewerTopBar(
        media = media,
        canSave = onScreen,
        saveState = saveState,
        pageLabel = if (imageCount > 1) {
            "${currentPage + 1} / $imageCount"
        } else {
            null
        },
        onSave = {
            if (onScreen && saveState != CoverSaveState.SAVING) startSave()
        },
        onDismiss = onDismiss
    )
}

/**
 * The header's own row: the format chip, the counter, save and close.
 *
 * Presentation only - [CoverViewerHeader] above it decides *what* the counter says
 * and *whether* a save may run, so this file's rule holds: the row draws what it
 * is handed.
 */
@Composable
private fun CoverViewerTopBar(
    media: MediaNode,
    canSave: Boolean,
    saveState: CoverSaveState,
    /** "2 / 4" while a multi-image gallery is selected, null for a single image. */
    pageLabel: String?,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    // One centred row with three anchored groups rather than a `SpaceBetween`
    // row: the counter is the only thing in the header whose position carries
    // meaning, and in a two-child row it drifted with the width of the format
    // chip - which is exactly the off-centre pill that was reported.
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = ExpressiveShapes.pill,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = "${media.formatLabel} · ${media.statusLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
            Surface(
                shape = ExpressiveShapes.pill,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "HD",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }

        if (pageLabel != null) {
            // `surfaceContainerHighest` on `onSurface` rather than black glass
            // with white text: both roles come from the scheme, so the counter is
            // readable in the light theme without a second hard-coded pair, and
            // it follows a wallpaper or a retained cover tint. The alpha keeps the
            // artwork visible through it over a bright banner.
            Surface(
                shape = ExpressiveShapes.pill,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("cover_page_counter")
            ) {
                Text(
                    text = pageLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // The two round controls share one container role, so the save and the
        // close cannot drift into two different shades of glass. `onSurface`
        // content rather than white: over a pale banner, white-on-white was the
        // pair that disappeared.
        val buttonContainer = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f)
        val buttonContent = MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canSave) {
                IconButton(
                    onClick = onSave,
                    enabled = saveState != CoverSaveState.SAVING,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(buttonContainer)
                        .testTag("cover_save_button")
                ) {
                    SaveButtonIcon(saveState, tint = buttonContent)
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(buttonContainer)
            ) {
                Icon(
                    imageVector = AppVectorIcons.Close,
                    contentDescription = "Close full screen",
                    tint = buttonContent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** The save button's glyph: the whole acknowledgement for one save. */
@Composable
private fun SaveButtonIcon(state: CoverSaveState, tint: Color) {
    when (state) {
        CoverSaveState.SAVING -> ExpressiveLoadingIndicator(
            modifier = Modifier.size(18.dp),
            color = tint
        )
        CoverSaveState.SAVED -> Icon(
            imageVector = AppVectorIcons.CheckCircle,
            contentDescription = "Cover saved to your gallery",
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        CoverSaveState.FAILED -> Icon(
            imageVector = AppVectorIcons.Warning,
            contentDescription = "Couldn't save the cover",
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        CoverSaveState.IDLE -> Icon(
            imageVector = AppVectorIcons.Download,
            contentDescription = "Save cover to your gallery",
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** One save's lifecycle: the button's glyph is the whole acknowledgement. */
internal enum class CoverSaveState { IDLE, SAVING, SAVED, FAILED }
