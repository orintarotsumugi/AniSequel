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
import com.anisequel.domain.tier.TierAiringStatusOptions
import com.anisequel.domain.tier.TierColorPalette
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierDisplay
import com.anisequel.domain.tier.TierFilter
import com.anisequel.domain.tier.TierListStatusOptions
import com.anisequel.domain.tier.TierStatusOption
import com.anisequel.domain.tier.TierTitlePosition
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import kotlin.math.roundToInt

/**
 * The board's settings sheets: filters, display, a tier's colour and its band.
 *
 * Separate from `TierPlacementSheets` because these are *view* decisions while
 * those are placement decisions. The split is not cosmetic: the placement sheets
 * change the ranking and therefore the undo stack, and these do not.
 */

/**
 * Which list and airing statuses are on the board.
 *
 * A chip is *selected when the status is shown*, not when it is hidden. That is
 * the reading people arrive with - a row of lit chips means "these are in" - and
 * the alternative ("lit means hidden") makes the default state, with planning
 * unlit, look like the only thing the screen is doing is hiding something. The
 * sentence above the chips says it outright anyway, because a control whose
 * polarity has to be inferred is a control that gets used backwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierFilterSheet(
    filter: TierFilter,
    onFilterChange: (TierFilter) -> Unit,
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
                .testTag("tier_filter_sheet")
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Unselected statuses are left out of the board and the unranked " +
                    "shelf. Nothing is removed from your list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))
            TierFilterGroup(
                title = "On your list",
                options = TierListStatusOptions,
                hidden = filter.hiddenListStatuses,
                onToggle = { value, hide ->
                    onFilterChange(filter.withListStatusHidden(value, hide))
                }
            )

            Spacer(modifier = Modifier.height(18.dp))
            TierFilterGroup(
                title = "Airing",
                options = TierAiringStatusOptions,
                hidden = filter.hiddenAiringStatuses,
                onToggle = { value, hide ->
                    onFilterChange(filter.withAiringStatusHidden(value, hide))
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onFilterChange(TierFilter.Default) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Default")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { onFilterChange(TierFilter(emptySet(), emptySet())) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Show everything", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TierFilterGroup(
    title: String,
    options: List<TierStatusOption>,
    hidden: Set<String>,
    onToggle: (value: String, hide: Boolean) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Two columns built from `chunked` rather than `FlowRow`: the chips are
        // short and the set is fixed, so a flow layout's wrapping would make two
        // lists of different lengths look like different controls.
        options.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { option ->
                    val shown = option.value !in hidden
                    ExpressiveStateChip(
                        label = option.label,
                        selected = shown,
                        // Tapping a shown chip hides it, and the other way round.
                        // `shown` is the current state, which is exactly the new
                        // "hidden" value.
                        onClick = { onToggle(option.value, shown) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        minTouchTargetHeight = 48.dp
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

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
