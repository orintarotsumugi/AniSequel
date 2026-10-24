package com.anisequel.ui.screens.tier

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.domain.tier.TierColorPalette
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierDisplay
import com.anisequel.domain.tier.TierTitleLength
import com.anisequel.domain.tier.TierTitlePosition
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import kotlin.math.roundToInt

/**
 * The board's display, colour and rating-band dialogs.
 *
 * Filters, zoom and export live in their own files: they are three different
 * sheets with three different reasons to change, and keeping them beside the
 * display sheet had grown this file past the point where a reviewer could hold
 * it in one pass.
 */

/**
 * Names under the cover, on it, or not at all - plus a way back to 100%.
 *
 * The zoom readout is repeated here because the board's stepper floats over the
 * posters and can be mistaken for part of a tier; this sheet is where somebody
 * who has pinched to 180% and wants it back goes looking. [display] carries the
 * board's *live* zoom rather than the stored one, because the stored one lags a
 * pinch by the write debounce.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierDisplaySheet(
    display: TierDisplay,
    onDisplayChange: (TierDisplay) -> Unit,
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
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .testTag("tier_display_sheet")
        ) {
            Text(
                text = "Display",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show titles",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Draw the name for each cover.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = display.showTitles,
                    onCheckedChange = { onDisplayChange(display.copy(showTitles = it)) },
                    modifier = Modifier.testTag("tier_titles_switch")
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Title position",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveStateChip(
                    label = "Under the cover",
                    selected = display.titlePosition == TierTitlePosition.UNDER_COVER,
                    onClick = {
                        onDisplayChange(
                            display.copy(titlePosition = TierTitlePosition.UNDER_COVER)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    minTouchTargetHeight = 48.dp
                )
                ExpressiveStateChip(
                    label = "On the cover",
                    selected = display.titlePosition == TierTitlePosition.ON_COVER,
                    onClick = {
                        onDisplayChange(display.copy(titlePosition = TierTitlePosition.ON_COVER))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    minTouchTargetHeight = 48.dp
                )
            }
            if (!display.showTitles) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Position applies when titles are shown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Name length",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                // The length is shared by the three renderers rather than being
                // a screen-only preference: an export that cut names differently
                // from the board it came from was the divergence this closes.
                text = "How much of each name a caption shows, on the board and in " +
                    "the exported image.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TierTitleLength.entries.forEach { option ->
                    ExpressiveStateChip(
                        label = option.label,
                        selected = display.titleLength == option,
                        onClick = {
                            onDisplayChange(display.copy(titleLength = option))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("tier_title_length_${option.name}"),
                        minTouchTargetHeight = 48.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Zoom ${(display.zoom * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onResetZoom,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Reset zoom")
                }
            }
        }
    }
}

/**
 * The tier colour picker.
 *
 * A fixed palette rather than an HSV wheel: the row's label is white text on the
 * fill, so a colour that looks good in a wheel can still be unreadable at 14sp.
 * These are the app's own tier colours - the standard six plus six in the same
 * family - so a recoloured row still belongs to the board, and the six it started
 * with are one tap away.
 */
@Composable
internal fun TierColorDialog(
    tier: TierDefinition,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colour for ${tier.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TierColorPalette.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { color ->
                            val shape = RoundedCornerShape(percent = 50)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(shape)
                                    .background(tierRowColor(color))
                                    .then(
                                        if (color == tier.colorArgb) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = shape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onPick(color) }
                                    .testTag("tier_colour_${color.toString(16)}")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * A tier's rating band, as a 0-100 score floor.
 *
 * The band is what "rank from AniList scores" reads. Editing it here rather than
 * in a settings screen is the point: it belongs to the row it describes, and
 * moving a row or renaming it should not leave its band behind.
 */
@Composable
internal fun TierScoreFloorDialog(
    tier: TierDefinition,
    onSet: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember(tier.id) { mutableStateOf(tier.scoreFloor != null) }
    // 75 rather than 0 when the band is off: if the person switches it on, the
    // slider should start somewhere meaningful instead of at "everything".
    var value by remember(tier.id) { mutableFloatStateOf((tier.scoreFloor ?: 75).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rating band for ${tier.label}") },
        text = {
            Column {
                Text(
                    text = "When you rank from AniList scores, titles at or above this " +
                        "score land in this tier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (enabled) "${value.roundToInt()}+" else "No band",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        modifier = Modifier.testTag("tier_band_switch")
                    )
                }
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..100f,
                    // 19 steps between the ends is a 5-point grid: scores are
                    // whole numbers, and a band of 83.7 is a precision the
                    // seeding rule cannot use.
                    steps = 19,
                    enabled = enabled
                )
                Text(
                    text = if (enabled) {
                        "Titles below this band fall to the next tier down."
                    } else {
                        "A tier with no band is never filled by seeding. You can still " +
                            "drag titles into it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSet(if (enabled) value.roundToInt() else null)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
