package com.anisequel.ui.screens.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.extensions.PlayerPreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.rememberBoundSliderState
import java.util.Locale

/**
 * Playback speed, with the reference's slider and removable preset chips.
 *
 * The slider writes the speed as it moves rather than behind a Confirm button:
 * the reader is watching the picture, and a speed that only applies after
 * dismissing the sheet is a second, invisible step. Presets are persisted by
 * [PlayerPreferences]; "Restore" brings back the eight defaults, which is the
 * only way to recover a preset list that has been trimmed to one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSpeedSheet(
    sheetState: SheetState,
    speed: Float,
    presets: List<Float>,
    onSpeedChange: (Float) -> Unit,
    onAddPreset: (Float) -> Unit,
    onRemovePreset: (Float) -> Unit,
    onRestorePresets: () -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(speed) { mutableFloatStateOf(speed.coerceIn(MIN, MAX)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Playback Speed",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(AppVectorIcons.Close, contentDescription = "Close speed settings")
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Current Speed",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    speedLabel(draft),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                ResetSpeedButton(
                    onClick = {
                        draft = 1f
                        onSpeedChange(1f)
                    }
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    speedLabel(MIN),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    state = rememberBoundSliderState(draft, trackRange = MIN..MAX),
                    onValueChange = { value ->
                        val snapped = (value * 20f).toInt() / 20f
                        draft = snapped
                        onSpeedChange(snapped)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    speedLabel(MAX),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Slow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Fast",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Speed Presets",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = onRestorePresets,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            AppVectorIcons.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Restore", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Surface(
                    onClick = {
                        val snapped = (draft * 20f).toInt() / 20f
                        onAddPreset(snapped)
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            AppVectorIcons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Add", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            presets.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { preset ->
                        SpeedPresetChip(
                            speed = preset,
                            selected = preset == draft,
                            onChoose = {
                                draft = preset
                                onSpeedChange(preset)
                            },
                            onRemove = { onRemovePreset(preset) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ResetSpeedButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                AppVectorIcons.Restore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Reset to 1\u00d7", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SpeedPresetChip(
    speed: Float,
    selected: Boolean,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onChoose)
                .padding(start = 12.dp, end = 5.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                speedLabel(speed),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AppVectorIcons.Close,
                    contentDescription = "Remove ${speedLabel(speed)} preset",
                    modifier = Modifier.size(13.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private fun speedLabel(value: Float): String {
    val rounded = (value * 100f).toInt() / 100f
    val text = if (rounded % 1f == 0f) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.ENGLISH, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }
    return "$text\u00d7"
}

private const val MIN = 0.25f
private const val MAX = 3f
