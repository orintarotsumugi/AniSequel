package com.anisequel.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.data.repository.DevicePreferences
import com.anisequel.data.repository.SeasonalPreferences
import com.anisequel.domain.seasonal.MAX_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.MAX_SEASONAL_PILE_DEPTH
import com.anisequel.domain.seasonal.MAX_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.MAX_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.MAX_SEASONAL_WIND_STRENGTH
import com.anisequel.domain.seasonal.MIN_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.MIN_SEASONAL_PILE_DEPTH
import com.anisequel.domain.seasonal.MIN_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.MIN_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.MIN_SEASONAL_WIND_STRENGTH
import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.SeasonalQuality
import com.anisequel.domain.seasonal.SeasonalQualityTier
import com.anisequel.domain.seasonal.SeasonalSeason
import com.anisequel.domain.seasonal.SeasonalSettings
import com.anisequel.domain.seasonal.availableSeasonalFrameRates
import com.anisequel.domain.seasonal.defaultEffect
import com.anisequel.domain.seasonal.particleCountFor
import com.anisequel.domain.seasonal.seasonalEffectsIn
import com.anisequel.domain.seasonal.seasonalFrameHz
import com.anisequel.domain.seasonal.seasonalSeasonForMonth
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.rememberBoundSliderState
import com.anisequel.ui.components.rememberIsResumed
import com.anisequel.ui.components.seasonal.SeasonalEffectGlyph
import com.anisequel.ui.components.seasonal.SeasonalParticleLayer
import com.anisequel.ui.components.seasonal.detectSeasonalQualityTier
import com.anisequel.ui.components.seasonal.detectSeasonalScreen
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Seasonal effects: a sky of falling or rising shapes over every page.
 *
 * ## Why this is a page of its own
 *
 * It has a preview, seven controls and nine shapes. Every one of those is a choice
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
 * size, speed, settle and wind can be judged before the overlay covers the app.
 * Turning the switch on is the last step, not the first.
 *
 * ## Why the controls are split across three cards
 *
 * The shape and the season are one decision, the amount of weather is another,
 * and the way it reacts to the reader is a third. Putting all seven in one card
 * made a page that had to be read top to bottom to find the switch at the bottom;
 * the groups are the three questions a reader actually arrives with.
 */
@Composable
fun SeasonalSettingsTab(
    seasonalPreferences: SeasonalPreferences,
    devicePreferences: DevicePreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val settings by seasonalPreferences.settings.collectAsState(initial = SeasonalSettings())
    val context = LocalContext.current
    val ceiling by devicePreferences.ceiling.collectAsState()
    // `diskCache.size` is a counter the loader keeps, not a directory walk, so this
    // costs nothing; it is refreshed on resume because the number only moves when
    // something has actually been downloaded.
    var usedBytes by remember { mutableStateOf(context.coverCacheBytes()) }
    val detected = remember { detectSeasonalQualityTier(context) }
    val resolvedQuality = remember(settings.quality, detected) {
        settings.quality.resolve(detected)
    }

    // The panel's own numbers, re-read when the app comes back to the foreground.
    // A reader who goes to the system display settings to unlock 120 Hz and returns
    // should find the pill here on the way back in, animating into place - a value
    // taken once at first composition would keep it hidden until the app restarted.
    val resumed = rememberIsResumed()
    var screen by remember { mutableStateOf(detectSeasonalScreen(context)) }
    LaunchedEffect(resumed) {
        screen = detectSeasonalScreen(context)
        usedBytes = context.coverCacheBytes()
    }
    val offeredRates = availableSeasonalFrameRates(screen.currentHz, screen.supportedHz)
    val previewFrameHz = seasonalFrameHz(
        rate = settings.frameRate,
        tierHz = resolvedQuality.frameHz,
        deviceHz = screen.currentHz
    )

    // Drafts rather than the committed values, so a drag is smooth and the preview
    // follows the finger. Keyed on the committed value so a write that lands - or
    // a reset - re-seeds the thumb.
    var draftDensity by remember(settings.density) { mutableFloatStateOf(settings.density) }
    var draftSize by remember(settings.sizeScale) { mutableFloatStateOf(settings.sizeScale) }
    var draftSpeed by remember(settings.speedScale) { mutableFloatStateOf(settings.speedScale) }
    var draftPileDepth by remember(settings.pileDepth) {
        mutableFloatStateOf(settings.pileDepth)
    }
    var draftWind by remember(settings.windStrength) {
        mutableFloatStateOf(settings.windStrength)
    }

    SettingsScrollColumn(modifier) {
        PreviewCard(
            settings = settings,
            density = draftDensity,
            sizeScale = draftSize,
            speedScale = draftSpeed,
            pileDepth = draftPileDepth,
            windStrength = draftWind,
            quality = resolvedQuality,
            frameHz = previewFrameHz,
            onEnabledChange = { enabled ->
                scope.launch {
                    if (enabled && settings.quality == SeasonalQuality.AUTO) {
                        seasonalPreferences.setQuality(SeasonalQuality.of(detected))
                    }
                    seasonalPreferences.setEnabled(enabled)
                }
            },
            onReset = { scope.launch { seasonalPreferences.reset() } }
        )

        ShapeCard(
            effect = settings.effect,
            onEffectChange = { effect ->
                scope.launch { seasonalPreferences.setEffect(effect) }
            }
        )

        WeatherCard(
            settings = settings,
            pileDepth = draftPileDepth,
            windStrength = draftWind,
            onSettleChange = { settle ->
                scope.launch { seasonalPreferences.setSettle(settle) }
            },
            onPileDepthChange = { draftPileDepth = it },
            onPileDepthCommit = {
                val target = draftPileDepth
                scope.launch { seasonalPreferences.setPileDepth(target) }
            },
            onWindChange = { reactToWind ->
                scope.launch { seasonalPreferences.setReactToWind(reactToWind) }
            },
            onWindStrengthChange = { draftWind = it },
            onWindStrengthCommit = {
                val target = draftWind
                scope.launch { seasonalPreferences.setWindStrength(target) }
            }
        )

        DeviceCard(
            rate = settings.frameRate,
            offered = offeredRates,
            resolvedHz = previewFrameHz,
            screen = screen,
            tierName = SeasonalQuality.of(resolvedQuality).displayName.lowercase(),
            ceiling = ceiling,
            usedBytes = usedBytes,
            onRateChange = { rate ->
                scope.launch { seasonalPreferences.setFrameRate(rate) }
            },
            onCeilingChange = { option ->
                // Synchronous by design: one string in a preferences file, whose value
                // the image loader reads at the next process start.
                devicePreferences.setCeiling(option)
            },
            onClearCache = {
                context.clearCoverCache()
                usedBytes = context.coverCacheBytes()
            }
        )

        DensityCard(
            quality = settings.quality,
            detectedName = SeasonalQuality.of(detected).displayName,
            resolvedCount = particleCountFor(draftDensity, resolvedQuality),
            density = draftDensity,
            sizeScale = draftSize,
            speedScale = draftSpeed,
            onQualityChange = { quality ->
                scope.launch { seasonalPreferences.setQuality(quality) }
            },
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
    pileDepth: Float,
    windStrength: Float,
    quality: SeasonalQualityTier,
    frameHz: Int,
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
        icon = AppVectorIcons.Seasonal,
        subtitle = "Try a sky before it covers the app - tap or scroll it around",
        badge = resetAction
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
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
                settle = settings.settle,
                pileDepth = pileDepth,
                reactToWind = settings.reactToWind,
                windStrength = windStrength,
                quality = quality,
                frameHz = frameHz,
                showTapRipple = false,
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
                "It never steals a tap and stops when the app is not on screen.",
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
    // A shortcut to the sky the calendar is currently showing. It is a one-shot
    // button rather than a "follow the season" mode on purpose: a sky that
    // changed itself in October would remove a choice the reader made, and the
    // shortcut is only here so nobody has to remember that March is spring.
    val seasonNow = remember { seasonalSeasonForMonth(LocalDate.now().monthValue) }
    SectionCard(
        title = "Season & shape",
        icon = AppVectorIcons.AnimeSparkle,
        subtitle = "Petals, leaves and snow - grouped by the season that owns them",
        badge = {
            TextButton(
                onClick = { onEffectChange(seasonNow.defaultEffect()) },
                modifier = Modifier.testTag("seasonal_season_button")
            ) {
                Text("Use ${seasonNow.displayName}")
            }
        }
    ) {
        Text(
            text = effect.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        Column(modifier = Modifier.testTag("seasonal_shape_row")) {
            SeasonalSeason.entries.forEach { season ->
                val options = seasonalEffectsIn(season)
                if (options.isEmpty()) return@forEach
                Text(
                    text = season.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = season.blurb,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    options.forEach { option ->
                        SeasonalEffectChip(
                            effect = option,
                            selected = option == effect,
                            onClick = { onEffectChange(option) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
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

/**
 * Settling and wind.
 *
 * Two behaviours with a switch each and one amount under each switch: this is the
 * card that turns a decorative sky into something the reader can touch. The
 * switches are disabled rather than hidden for an effect that cannot do it - a
 * switch that vanishes when a shape is chosen makes the two cards feel unrelated,
 * while a disabled switch with a sentence says why.
 */
@Composable
private fun WeatherCard(
    settings: SeasonalSettings,
    pileDepth: Float,
    windStrength: Float,
    onSettleChange: (Boolean) -> Unit,
    onPileDepthChange: (Float) -> Unit,
    onPileDepthCommit: () -> Unit,
    onWindChange: (Boolean) -> Unit,
    onWindStrengthChange: (Float) -> Unit,
    onWindStrengthCommit: () -> Unit
) {
    val canSettle = settings.effect.settles
    SectionCard(
        title = "Settling & wind",
        icon = AppVectorIcons.SectionMotion,
        subtitle = "Let it gather, and let it move when you do"
    ) {
        SwitchRow(
            title = "Settle at the bottom",
            subtitle = if (canSettle) {
                "Snow, petals and leaves gather in a drift that grows while you read."
            } else {
                "${settings.effect.displayName} does not land - choose a falling shape to pile up."
            },
            checked = settings.settle && canSettle,
            onCheckedChange = onSettleChange,
            enabled = canSettle,
            modifier = Modifier.testTag("seasonal_settle_switch")
        )
        Spacer(modifier = Modifier.height(4.dp))
        SeasonalSlider(
            title = "Drift depth",
            valueLabel = percentLabel(pileDepth),
            value = pileDepth,
            range = MIN_SEASONAL_PILE_DEPTH..MAX_SEASONAL_PILE_DEPTH,
            steps = 3,
            testTag = "seasonal_pile_depth_slider",
            enabled = canSettle && settings.settle,
            onValueChange = onPileDepthChange,
            onValueChangeFinished = onPileDepthCommit
        )

        Spacer(modifier = Modifier.height(14.dp))
        SwitchRow(
            title = "Wind & touch",
            subtitle = "A breeze of its own, plus a gust when you scroll and a push when you tap.",
            checked = settings.reactToWind,
            onCheckedChange = onWindChange,
            modifier = Modifier.testTag("seasonal_wind_switch")
        )
        Spacer(modifier = Modifier.height(4.dp))
        SeasonalSlider(
            title = "Wind strength",
            valueLabel = scaleLabel(windStrength),
            value = windStrength,
            range = MIN_SEASONAL_WIND_STRENGTH..MAX_SEASONAL_WIND_STRENGTH,
            steps = 3,
            testTag = "seasonal_wind_strength_slider",
            enabled = settings.reactToWind,
            onValueChange = onWindStrengthChange,
            onValueChangeFinished = onWindStrengthCommit
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The drift gathers along the bottom edge of the screen, under the " +
                "navigation bar rather than on top of it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DensityCard(
    quality: SeasonalQuality,
    detectedName: String,
    resolvedCount: Int,
    density: Float,
    sizeScale: Float,
    speedScale: Float,
    onQualityChange: (SeasonalQuality) -> Unit,
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
        subtitle = "How hard this phone works, then how many, how big, and how fast"
    ) {
        Text(
            text = if (quality == SeasonalQuality.AUTO) {
                "Auto is drawing the $detectedName sky on this phone."
            } else {
                quality.blurb
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("seasonal_quality_row")
        ) {
            SeasonalQuality.entries.forEach { option ->
                SeasonalQualityChip(
                    quality = option,
                    selected = option == quality,
                    onClick = { onQualityChange(option) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SeasonalSlider(
            title = "Density",
            valueLabel = "$resolvedCount particles",
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
    onValueChangeFinished: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Slider(
        // Bound, not owned: every slider on this page is written back by Reset, and a
        // `SliderState` that only reads its initial value would leave the thumbs where the
        // reader left them while the numbers under them moved.
        state = rememberBoundSliderState(value, steps, range),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        modifier = Modifier.testTag(testTag)
    )
}

/** "×1.0" rather than a raw float: the multiplier is the thing being changed. */
private fun scaleLabel(value: Float): String =
    "\u00d7" + ((value * 10f).roundToInt() / 10f)

/** "60%" for a depth: it is a proportion of the tallest drift, not a multiplier. */
private fun percentLabel(value: Float): String = "${(value * 100f).roundToInt()}%"

