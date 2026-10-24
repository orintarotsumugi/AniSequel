package com.anisequel.ui.screens.tier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The zoom sheet: a slider, presets and the drag lock, within thumb reach.
 *
 * The stepper on the board is precise but slow to cross a 35-200% range, and a
 * pinch needs two fingers on a poster - which the drag gesture used to win. This
 * is the discoverable version of both: one slider for the range, presets for the
 * three sizes people actually use, and the lock in the same place as the pinch
 * that needs it. The board's local zoom is the value written here, so the slider
 * moves the board live rather than committing on dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TierZoomSheet(
    zoom: Float,
    locked: Boolean,
    onZoomChange: (Float) -> Unit,
    onLockChange: (Boolean) -> Unit,
    onResetZoom: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A landscape phone has less height than this sheet wants once
                // the lock row and the presets are on it. Bounding and scrolling
                // keeps the reset action reachable instead of letting the bottom
                // of the sheet leave the display.
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .testTag("tier_zoom_sheet")
        ) {
            Text(
                text = "Zoom",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pinch anywhere on the board, or drag the slider. If a " +
                    "long-press keeps taking the pinch, lock the board first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${(zoom * 100).roundToInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = zoom,
                onValueChange = onZoomChange,
                valueRange = TIER_MIN_ZOOM..TIER_MAX_ZOOM,
                modifier = Modifier.testTag("tier_zoom_slider")
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${(TIER_MIN_ZOOM * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${(TIER_MAX_ZOOM * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZoomPresets.forEach { preset ->
                    ExpressiveStateChip(
                        label = "${(preset * 100).roundToInt()}%",
                        selected = abs(zoom - preset) < 0.01f,
                        onClick = { onZoomChange(preset) },
                        minTouchTargetHeight = 48.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lock tile dragging",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pinch and scroll without picking a poster up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = locked,
                    onCheckedChange = onLockChange,
                    modifier = Modifier.testTag("tier_zoom_lock_switch")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onResetZoom,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Reset to 100%")
            }
        }
    }
}

/** The sizes the zoom sheet offers: the floor, an overview, normal, and two ups. */
private val ZoomPresets = listOf(TIER_MIN_ZOOM, 0.5f, 1f, 1.5f, TIER_MAX_ZOOM)
