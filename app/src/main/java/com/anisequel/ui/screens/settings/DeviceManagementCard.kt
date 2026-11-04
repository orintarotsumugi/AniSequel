package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.domain.seasonal.SeasonalFrameRate
import com.anisequel.domain.seasonal.SeasonalQuality
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.seasonal.SeasonalScreen
import kotlin.math.roundToInt

/**
 * The seasonal page's choice pills, and the card that puts a ceiling on how often
 * the sky is allowed to move.
 *
 * Split out of [SeasonalSettingsTab] because the file was over the ceiling and the
 * two halves change for different reasons: the page assembles previews and sliders
 * around the current settings, while this is the control vocabulary the page draws
 * with and the one card that reads the phone rather than the preference file.
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
internal fun FrameCard(
    rate: SeasonalFrameRate,
    offered: List<SeasonalFrameRate>,
    resolvedHz: Int,
    screen: SeasonalScreen,
    tierName: String,
    onRateChange: (SeasonalFrameRate) -> Unit
) {
    SectionCard(
        title = "Frame rate",
        icon = AppVectorIcons.Refresh,
        subtitle = "How often the sky may move, and what this screen reports"
    ) {
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
    }
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

    // One interaction source shared by the press spring and the click, and no
    // ripple: `bouncyPress` without a source installs its own pointer loop on the
    // element `clickable` already drives, so a finger that lands on a pill and
    // starts to scroll this page keeps the pill squashed for the whole drag.
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
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
