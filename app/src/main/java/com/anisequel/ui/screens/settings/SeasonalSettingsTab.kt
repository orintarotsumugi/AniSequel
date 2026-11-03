package com.anisequel.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.data.repository.SeasonalPreferences
import com.anisequel.domain.seasonal.MAX_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.MAX_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.MAX_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.MIN_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.MIN_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.MIN_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.SeasonalSettings
import com.anisequel.domain.seasonal.particleCountFor
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.seasonal.SeasonalEffectGlyph
import com.anisequel.ui.components.seasonal.SeasonalParticleLayer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Seasonal effects: a sky of falling or rising shapes over every page.
 *
 * ## Why this is a page of its own
 *
 * It has a preview, four controls and six shapes. Every one of those is a choice
 * that only makes sense next to the drawing it changes - a shape chip without its
 * glyph, or a density slider without flakes to count, asks the reader to set a
 * number and then go and find out what it did.
 *
 * ## Why every slider commits on release
 *
 * The same reason [TypeSettingsTab] does: the drag is a draft, and a DataStore
 * write per frame would put a disk round trip inside the animation the thumb is
 * driving. The draft is what the preview reads, so the only thing the reader
 * needs to be live is the drawing.
 *
 * ## Why the preview ignores the on switch
 *
 * The preview always draws, even while the effect is off, so the shape, density,
 * size and speed can be judged before the overlay covers the app. Turning the
 * switch on is the last step, not the first.
 */
@Composable
fun SeasonalSettingsTab(
    seasonalPreferences: SeasonalPreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val settings by seasonalPreferences.settings.collectAsState(initial = SeasonalSettings())

    // Drafts rather than the committed values, so a drag is smooth and the preview
    // follows the finger. Keyed on the committed value so a write that lands - or
    // a reset - re-seeds the thumb.
    var draftDensity by remember(settings.density) { mutableFloatStateOf(settings.density) }
    var draftSize by remember(settings.sizeScale) { mutableFloatStateOf(settings.sizeScale) }
    var draftSpeed by remember(settings.speedScale) { mutableFloatStateOf(settings.speedScale) }

    SettingsScrollColumn(modifier) {
        PreviewCard(
            settings = settings,
            density = draftDensity,
            sizeScale = draftSize,
            speedScale = draftSpeed,
            onEnabledChange = { enabled ->
                scope.launch { seasonalPreferences.setEnabled(enabled) }
            },
            onReset = { scope.launch { seasonalPreferences.reset() } }
        )

        ShapeCard(
            effect = settings.effect,
            onEffectChange = { effect ->
                scope.launch { seasonalPreferences.setEffect(effect) }
            }
        )

        DensityCard(
            density = draftDensity,
            sizeScale = draftSize,
            speedScale = draftSpeed,
            onDensityChange = { draftDensity = it },
            onDensityCommit = {
                val target = draftDensity
                scope.launch { seasonalPreferences.setDensity(target) }
            },
            onSizeChange = { draftSize = it },
            onSizeCommit = {
                val target = draftSize
                scope.launch { seasonalPreferences.setSizeScale(target) }
            },
            onSpeedChange = { draftSpeed = it },
            onSpeedCommit = {
                val target = draftSpeed
                scope.launch { seasonalPreferences.setSpeedScale(target) }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PreviewCard(
    settings: SeasonalSettings,
    density: Float,
    sizeScale: Float,
    speedScale: Float,
    onEnabledChange: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    val resetAction: (@Composable () -> Unit)? = if (settings != SeasonalSettings()) {
        {
            TextButton(onClick = onReset, modifier = Modifier.testTag("seasonal_reset_button")) {
                Text("Reset")
            }
        }
    } else {
        null
    }

    SectionCard(
        title = "Preview",
        icon = AppVectorIcons.Grass,
        subtitle = "Try a sky before it covers the app",
        badge = resetAction
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            SeasonalParticleLayer(
                effect = settings.effect,
                density = density,
                sizeScale = sizeScale,
                speedScale = speedScale,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("seasonal_preview")
            )
        }

        // The instant motion style freezes every animation in the app, and this
        // one too. Saying so here is the difference between "the preview is
        // broken" and "this is the setting you chose".
        if (ExpressiveMotion.speed == MotionStyle.INSTANT) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Motion style is Instant, so the particles hold still.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        SwitchRow(
            title = "Seasonal effect",
            subtitle = "Float ${settings.effect.displayName.lowercase()} over every page. " +
                "It never takes a tap and stops when the app is not on screen.",
            checked = settings.enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.testTag("seasonal_enabled_switch")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeCard(
    effect: SeasonalEffect,
    onEffectChange: (SeasonalEffect) -> Unit
) {
    SectionCard(
        title = "Shape",
        icon = AppVectorIcons.AnimeSparkle,
        subtitle = "What falls - or rises - past you"
    ) {
        Text(
            text = effect.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("seasonal_shape_row")
        ) {
            SeasonalEffect.entries.forEach { option ->
                SeasonalEffectChip(
                    effect = option,
                    selected = option == effect,
                    onClick = { onEffectChange(option) }
                )
            }
        }
    }
}

@Composable
private fun SeasonalEffectChip(
    effect: SeasonalEffect,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "seasonal_chip_container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "seasonal_chip_content"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "seasonal_chip_border"
    )

    // The press spring and the click share one interaction source, and the ripple
    // is off: `bouncyPress` without a source installs its own pointer loop on the
    // element `clickable` is already driving, so a finger that lands on a chip and
    // starts to scroll this page keeps the chip squashed for the whole drag - the
    // reason `AttachmentEmbedRows` and the comment row's controls pass one.
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(container)
            .border(BorderStroke(1.dp, borderColor), shape = ExpressiveShapes.pill)
            .bouncyPress(pressedScale = 0.94f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            // 18/11, the shape of `JokeChoiceChip`: a chip is the only way to choose
            // a shape, and the smaller of the two settings chips was the one with a
            // drawing inside it.
            .padding(horizontal = 18.dp, vertical = 11.dp)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .testTag("seasonal_effect_${effect.storageValue}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // The glyph is the same draw code as the overlay, so a chip can never
        // promise a snowflake the sky does not draw. It is a vector path, never an
        // emoji.
        SeasonalEffectGlyph(effect = effect, modifier = Modifier.size(18.dp))
        Text(
            text = effect.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content
        )
    }
}

@Composable
private fun DensityCard(
    density: Float,
    sizeScale: Float,
    speedScale: Float,
    onDensityChange: (Float) -> Unit,
    onDensityCommit: () -> Unit,
    onSizeChange: (Float) -> Unit,
    onSizeCommit: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSpeedCommit: () -> Unit
) {
    SectionCard(
        title = "Density & size",
        icon = AppVectorIcons.Tune,
        subtitle = "How many, how big, and how fast"
    ) {
        SeasonalSlider(
            title = "Density",
            valueLabel = "${particleCountFor(density)} particles",
            value = density,
            range = MIN_SEASONAL_DENSITY..MAX_SEASONAL_DENSITY,
            steps = 7,
            testTag = "seasonal_density_slider",
            onValueChange = onDensityChange,
            onValueChangeFinished = onDensityCommit
        )
        Spacer(modifier = Modifier.height(8.dp))
        SeasonalSlider(
            title = "Size",
            valueLabel = scaleLabel(sizeScale),
            value = sizeScale,
            range = MIN_SEASONAL_SIZE_SCALE..MAX_SEASONAL_SIZE_SCALE,
            steps = 5,
            testTag = "seasonal_size_slider",
            onValueChange = onSizeChange,
            onValueChangeFinished = onSizeCommit
        )
        Spacer(modifier = Modifier.height(8.dp))
        SeasonalSlider(
            title = "Drift speed",
            valueLabel = scaleLabel(speedScale),
            value = speedScale,
            range = MIN_SEASONAL_SPEED_SCALE..MAX_SEASONAL_SPEED_SCALE,
            steps = 7,
            testTag = "seasonal_speed_slider",
            onValueChange = onSpeedChange,
            onValueChangeFinished = onSpeedCommit
        )
    }
}

@Composable
private fun SeasonalSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    testTag: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = range,
        steps = steps,
        modifier = Modifier.testTag(testTag)
    )
}

/** "×1.0" rather than a raw float: the multiplier is the thing being changed. */
private fun scaleLabel(value: Float): String =
    "\u00d7" + ((value * 10f).roundToInt() / 10f)
