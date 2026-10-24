package com.anisequel.ui.screens.tier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveShapes
import kotlin.math.roundToInt

/**
 * The board's floating toolbars: undo/redo with export, and the zoom stepper.
 *
 * These were the bottom half of `TierBoardChrome`, which had grown past what one
 * file should hold once the lock and the export action joined them. They are
 * separate from the header and the empty states because they float over the
 * board and change at a different rate: a zoom step recomposes this file and
 * nothing above it.
 */

internal val TIER_CONTROLS_HEIGHT: Dp = 40.dp

/** The gap between that toolbar and the tray, or the screen edge when there is none. */
internal val TIER_CONTROLS_GAP: Dp = 12.dp

/**
 * The board's bottom-end toolbar: undo/redo beside the zoom stepper.
 *
 * At the bottom rather than in the app bar for two reasons. A drop is made with a
 * thumb near the bottom of the screen, so the control that takes it back belongs
 * within reach of the hand that made it; and the app bar already carries the
 * list's name, the filter and the tier editor, which is as many 48dp targets as a
 * 360dp bar holds without truncating the one piece of text that says which list
 * you are in.
 *
 * Undo and redo stay drawn when their stacks are empty, disabled rather than
 * hidden: a control that appears and disappears under a thumb is a control that
 * gets pressed by accident the moment it appears.
 */
@Composable
internal fun TierHistoryControls(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactTierButton(
            icon = AppVectorIcons.Undo,
            contentDescription = "Undo",
            enabled = canUndo,
            onClick = onUndo,
            modifier = Modifier.testTag("tier_undo_button")
        )
        CompactTierButton(
            icon = AppVectorIcons.Redo,
            contentDescription = "Redo",
            enabled = canRedo,
            onClick = onRedo,
            modifier = Modifier.testTag("tier_redo_button")
        )
        CompactTierButton(
            icon = AppVectorIcons.Export,
            contentDescription = "Export image",
            onClick = onExport,
            modifier = Modifier.testTag("tier_export_button")
        )
    }
}

/**
 * The spreadsheet zoom stepper: lock, out, the percentage, in.
 *
 * ## Why it is 36dp and not 48dp
 *
 * Seven controls sit in the two pills, which is 292dp including the gaps and the
 * end padding: four 36dp buttons, a 44dp readout and the two pills' own padding.
 * At the material minimum the pair would be over 400dp and would not fit a 360dp
 * screen beside the board at all. 36dp gives the board back the width while
 * keeping the whole target pressable. The lock is the exception to "small is
 * fine": it is the control that makes pinch zoom reachable, so it is tinted
 * while it is on rather than being any larger than its neighbours.
 *
 * The percentage is a button as well as a readout, because the common case after
 * pinching to an unreadable zoom is "put it back to normal", and a tap on the
 * number is one gesture rather than counting presses. Pinch still works; this is
 * the discoverable, precise version of the same control.
 */
@Composable
internal fun TierZoomControls(
    zoom: Float,
    locked: Boolean,
    onToggleLock: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onResetZoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 2.dp)
            .testTag("tier_zoom_controls"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactTierButton(
            icon = if (locked) AppVectorIcons.Privacy else AppVectorIcons.LockOpen,
            contentDescription = if (locked) "Unlock tile dragging" else "Lock tile dragging",
            selected = locked,
            onClick = onToggleLock,
            modifier = Modifier.testTag("tier_zoom_lock")
        )
        CompactTierButton(
            icon = AppVectorIcons.StepDown,
            contentDescription = "Zoom out",
            enabled = zoom > TIER_MIN_ZOOM,
            onClick = onZoomOut,
            modifier = Modifier.testTag("tier_zoom_out")
        )
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(role = Role.Button, onClick = onResetZoom)
                .testTag("tier_zoom_reset"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${(zoom * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CompactTierButton(
            icon = AppVectorIcons.StepUp,
            contentDescription = "Zoom in",
            enabled = zoom < TIER_MAX_ZOOM,
            onClick = onZoomIn,
            modifier = Modifier.testTag("tier_zoom_in")
        )
    }
}

/**
 * One 36dp icon in the floating toolbars.
 *
 * A plain `IconButton` cannot be this small: material3 wraps it in the 48dp
 * minimum interactive component, so `Modifier.size(36.dp)` changes the glyph's
 * box and not the space the row takes. A `Box` with `clickable` is the honest
 * way to ask for a smaller target; the content description on the icon is what
 * keeps it the same control to TalkBack.
 */
@Composable
private fun CompactTierButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
