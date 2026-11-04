package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.CoverCacheCeiling
import com.anisequel.domain.seasonal.SeasonalFrameRate
import com.anisequel.domain.seasonal.SeasonalQuality
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.seasonal.SeasonalScreen
import kotlin.math.roundToInt

/**
 * The settings page's choice pills, and the card that manages the phone.
 *
 * Split out of [SeasonalSettingsTab] because the file was over the ceiling and the
 * two halves change for different reasons: the page assembles previews and sliders
 * around the current settings, while this is the control vocabulary the page draws
 * with and the one card that reads the *device* rather than the preference file.
 *
 * ## Why the device facts live on the Seasonal page
 *
 * They belong to the heaviest decoration in the app, not to a settings tab of their
 * own. A reader who comes here is asking "is this sky costing me frames, and is this
 * app eating my storage" - and the two answers are a refresh rate and a cache size,
 * which is why they are one card and why the cache pills sit under the rate pills.
 */

/**
 * The frame-rate ceiling, and what this phone says about itself.
 *
 * ## Why the pills appear and disappear
 *
 * A 60 Hz panel cannot run the sky at 120 - the pacing helper clamps the choice to
 * what the screen reports, so the pill would be a control with exactly one effect:
 * none. The list comes from `availableSeasonalFrameRates`, and a rate the panel has
 * never had is not drawn. The reader who unlocks 120 Hz in the system settings comes
 * back to find the pill expanded into place, because the page re-reads the screen on
 * resume - a value cached at first composition would keep it hidden until restart.
 *
 * A choice that has *become* unavailable stays drawn and still selected. The system
 * setting can come back, and a selected option that has gone silent reads as the app
 * forgetting what was picked rather than as a phone currently running slower.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeviceCard(
    rate: SeasonalFrameRate,
    offered: List<SeasonalFrameRate>,
    resolvedHz: Int,
    screen: SeasonalScreen,
    tierName: String,
    ceiling: CoverCacheCeiling,
    usedBytes: Long,
    onRateChange: (SeasonalFrameRate) -> Unit,
    onCeilingChange: (CoverCacheCeiling) -> Unit,
    onClearCache: () -> Unit
) {
    SectionCard(
        title = "Device management",
        icon = AppVectorIcons.Refresh,
        subtitle = "What this phone reports, and how hard the app may work on it"
    ) {
        Text(
            text = "Frame rate",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = screenReport(screen, resolvedHz, tierName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("seasonal_frame_rate_row")
        ) {
            // Enum order, so the row runs Auto - 30 - 60 - 120 - 144 and does not
            // reshuffle itself when the panel changes rate under the reader.
            SeasonalFrameRate.entries.forEach { option ->
                AnimatedVisibility(
                    visible = option in offered || option == rate,
                    enter = fadeIn(ExpressiveMotion.FastEffects) +
                        expandHorizontally(
                            animationSpec = ExpressiveMotion.DefaultSpatial,
                            expandFrom = Alignment.Start
                        ) +
                        scaleIn(
                            animationSpec = ExpressiveMotion.BouncySpatial,
                            initialScale = 0.82f
                        ),
                    exit = fadeOut(ExpressiveMotion.FastEffects) +
                        shrinkHorizontally(
                            animationSpec = ExpressiveMotion.FastSpatial,
                            shrinkTowards = Alignment.Start
                        ) +
                        scaleOut(
                            animationSpec = ExpressiveMotion.FastSpatial,
                            targetScale = 0.82f
                        )
                ) {
                    SeasonalPill(
                        label = option.displayName,
                        selected = option == rate,
                        testTag = "seasonal_frame_rate_${option.storageValue}",
                        colourLabel = "seasonal_frame_rate",
                        onClick = { onRateChange(option) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = rate.blurb,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Cover cache",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("device_cache_row")
        ) {
            CoverCacheCeiling.entries.forEach { option ->
                SeasonalPill(
                    label = option.displayName,
                    selected = option == ceiling,
                    testTag = "device_cache_${option.storageValue}",
                    colourLabel = "device_cache",
                    onClick = { onCeilingChange(option) }
                )
            }
        }
        // The byte count and the note travel with the rung that produced them, so a
        // tap on 1 GB animates the line under it instead of replacing it.
        Column(modifier = Modifier.animateContentSize(ExpressiveMotion.FastSpatial)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = cacheReport(ceiling, usedBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Coil fixes a disk cache's ceiling when it builds the loader, so a " +
                    "new ceiling takes hold the next time the app opens. Clearing empties " +
                    "what is on disk now.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onClearCache, modifier = Modifier.testTag("device_cache_clear")) {
                Text("Clear cover cache")
            }
        }
    }
}

/** "412 MB used of 1 GB" - rounded the way a reader reads a storage number. */
private fun cacheReport(ceiling: CoverCacheCeiling, usedBytes: Long): String {
    val usedMb = (usedBytes / (1024L * 1024L)).coerceAtLeast(0L)
    val ceilingMb = ceiling.bytes / (1024L * 1024L)
    return "$usedMb MB of the $ceilingMb MB you allow is saved on this device."
}

/**
 * What the page says about the phone it is running on.
 *
 * Rates are rounded to whole numbers because the panel reports 119.88 and 59.94 and
 * nobody reads those as two different things. The supported list is rounded and
 * de-duplicated for the same reason: a phone offering 59.94/119.88/120.01 is one
 * 60 and one 120 screen.
 */
private fun screenReport(screen: SeasonalScreen, resolvedHz: Int, tierName: String): String {
    val rates = (screen.supportedHz + listOf(screen.currentHz))
        .filter { it > 0f }
        .map { it.roundToInt() }
        .distinct()
        .sorted()
    return buildString {
        append(
            if (screen.currentHz > 0f) "This screen runs at ${screen.currentHz.roundToInt()} Hz"
            else "This screen reports no rate"
        )
        if (rates.isNotEmpty()) append(", and can be set to ${rates.joinToString(", ")} Hz")
        append(". The sky is stepping at $resolvedHz Hz on the $tierName drawing budget.")
    }
}

/**
 * The settings page's one pill shape.
 *
 * Extracted because the quality pills and the frame-rate pills are the same control
 * wearing two labels, and each copy had already drifted a little in padding and
 * border. The shape chips keep their own version: they carry a drawing inside the
 * pill and are eleven dp taller, which is a different control, not a duplicate of
 * this one.
 */
@Composable
internal fun SeasonalPill(
    label: String,
    selected: Boolean,
    testTag: String,
    colourLabel: String,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "${colourLabel}_container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "${colourLabel}_content"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "${colourLabel}_border"
    )
    // The chosen pill sits a hair larger than its neighbours, on the bouncy token
    // rather than a plain one: a selection that only changes colour is invisible to a
    // reader glancing past the row, and the overshoot is what makes it read as the
    // page *accepting* the tap. Drawn through a graphics layer so the spring never
    // re-measures the row - an animated value read by layout restarts layout every
    // frame, and this row is composed of pills that animate at the same time.
    val settleScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = ExpressiveMotion.SuperBouncy,
        label = "${colourLabel}_scale"
    )

    // One interaction source shared by the press spring and the click, and no
    // ripple: `bouncyPress` without a source installs its own pointer loop on the
    // element `clickable` already drives, so a finger that lands on a pill and
    // starts to scroll this page keeps the pill squashed for the whole drag.
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = settleScale
                scaleY = settleScale
            }
            .clip(ExpressiveShapes.pill)
            .background(container)
            .border(BorderStroke(1.dp, borderColor), shape = ExpressiveShapes.pill)
            .bouncyPress(pressedScale = 0.94f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content
        )
    }
}

@Composable
internal fun SeasonalQualityChip(
    quality: SeasonalQuality,
    selected: Boolean,
    onClick: () -> Unit
) {
    SeasonalPill(
        label = quality.displayName,
        selected = selected,
        testTag = "seasonal_quality_${quality.storageValue}",
        colourLabel = "seasonal_quality",
        onClick = onClick
    )
}
