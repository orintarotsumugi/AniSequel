package com.anisequel.ui.screens.settings

import android.animation.ValueAnimator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.expressive.expressiveShape
import com.anisequel.ui.components.rememberIsResumed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Full-screen mode, and the system bars it hides.
 *
 * Its own card rather than a row inside Appearance: the switch does not change
 * how the app looks, it changes how much of the screen the app is given, and the
 * window applies it from `MainActivity` rather than from the theme.
 */
@Composable
internal fun DisplaySection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    SectionCard(
        title = "Display",
        icon = AppVectorIcons.FullscreenExpand,
        subtitle = "Full screen and the system bars",
        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
        iconContent = MaterialTheme.colorScheme.onSecondaryContainer,
        // A Material burst on the tile: it is a fixed 34dp square, the one shape of
        // surface a RoundedPolygon is authored for - see ExpressiveShapes.
        iconShape = expressiveShape(ExpressiveShapes.burst)
    ) {
        SwitchRow(
            title = "Full-screen mode",
            subtitle = "Hide the status and navigation bars. Swipe from an edge " +
                "to bring them back for a moment.",
            checked = settings.immersive,
            onCheckedChange = { enabled ->
                scope.launch { themePreferences.setImmersive(enabled) }
            },
            modifier = Modifier.testTag("immersive_mode_row")
        )
    }
}

/**
 * The motion picker, with a sample that plays the chosen physics on the spot.
 *
 * The old control was three named segments over a sentence. A name is not a feel:
 * "Chill" and "Floaty" read as two words for the same thing until you watch one
 * overshoot and the other drift. The sample above the picker runs the *chosen*
 * style's own spring - not a copy of its numbers - so what plays is what every
 * route, card and press in the app will do.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MotionSection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    SectionCard(
        title = "Motion",
        icon = AppVectorIcons.SectionMotion,
        subtitle = "How much the app animates",
        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
        iconContent = MaterialTheme.colorScheme.onTertiaryContainer,
        iconShape = expressiveShape(ExpressiveShapes.flower)
    ) {
        MotionSample(style = settings.motionStyle)
        Spacer(modifier = Modifier.height(10.dp))

        // A wrapping row rather than a horizontally scrolling one: the scrolling
        // row hid two of the five styles off the right edge on a phone, which read
        // as "Smooth and Chill were removed" for anyone who did not think to swipe a
        // row that looks like a static control. All five are visible at once, two
        // rows at worst.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("motion_style_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MotionStyle.entries.forEach { style ->
                MotionChip(
                    label = style.displayName,
                    icon = motionIcon(style),
                    selected = style == settings.motionStyle,
                    onClick = {
                        if (style != settings.motionStyle) {
                            scope.launch { themePreferences.setMotionStyle(style) }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        // Crossfaded rather than swapped: changing the style retitles the sample
        // and replaces this sentence, and a hard cut on both is the one frame in
        // this card that does not move on the token it is describing.
        Crossfade(
            targetState = settings.motionStyle,
            animationSpec = ExpressiveMotion.FastEffects,
            label = "motion_description"
        ) { style ->
            Text(
                text = motionDescription(style),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // The picker and the phone disagree whenever the platform's animation
        // scale is zero: the choice below is still stored, but the app is
        // drawing every token as `snap()`. Saying so is the difference between
        // "this setting is broken" and "this phone asked for no motion".
        //
        // `areAnimatorsEnabled` is a plain static read, not snapshot state, so it
        // has to sit behind the resumed state to be re-read when the reader comes
        // back from the system's Developer Options screen. Without that the notice
        // could stay on screen after they turned animations back on.
        val resumed = rememberIsResumed()
        val systemAnimationsEnabled = remember(resumed) { ValueAnimator.areAnimatorsEnabled() }
        if (!systemAnimationsEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your phone's animations are turned off, so AniSequel is " +
                    "staying still. Your style is kept for when they are back on.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MotionChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "motion_chip_container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "motion_chip_content"
    )

    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(container)
            // 48dp rather than the 38dp the padding alone gives: five chips in a
            // wrapping row are the page's whole motion decision, so they are a
            // primary target and not a tag cluster.
            .defaultMinSize(minHeight = 48.dp)
            // Same press physics as every other control in the app; the chips are
            // not inside a scroll, so the fallback pointer loop cannot spring under
            // a scrolling thumb.
            .bouncyPress(pressedScale = 0.94f)
            .clickable(
                onClickLabel = "Use the $label motion style",
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("motion_style_${label.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1
        )
    }
}

/**
 * One sample that plays the chosen style's actual physics.
 *
 * A dot travels a track and grows; the stage surface shifts colour with it; both
 * read [progress] directly rather than animating toward it, so there is exactly
 * one animation running and it is the one being previewed. Tapping replays it -
 * a one-shot loop rather than an infinite one, because a sample that never stops
 * is a screen that never settles.
 */
@Composable
private fun MotionSample(style: MotionStyle) {
    var replay by remember(style) { mutableIntStateOf(0) }
    val progress = remember(style) { Animatable(0f) }

    LaunchedEffect(style, replay) {
        progress.snapTo(0f)
        // The token is the preview: INSTANT snaps, SMOOTH arrives clean, SNAPPY is
        // quicker than SMOOTH, CHILL overshoots, FLOATY drifts. No bespoke spec
        // here, or the sample could flatter a style the app does not actually use.
        progress.animateTo(1f, ExpressiveMotion.DefaultSpatial)
    }

    val stage = lerp(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.secondaryContainer,
        progress.value.coerceIn(0f, 1f)
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = stage,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            // A tap target with no role is announced as "text" on some readers
            // and skipped on others. It is a replay button, so it says so.
            .clickable(
                role = Role.Button,
                onClickLabel = "Replay the ${style.displayName} motion sample"
            ) { replay++ }
            .testTag("motion_preview")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(horizontal = 16.dp)
        ) {
            val travelPx = with(LocalDensity.current) {
                (maxWidth - DotSize).toPx().coerceAtLeast(0f)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(DotSize)
                    .graphicsLayer {
                        // Travel is clamped; the grow is not. A spring overshoots
                        // *past* its target, and letting the dot carry that
                        // overshoot off the end of the track clipped it against the
                        // card's rounded edge - which read as a rendering bug. The
                        // scale keeps the raw value, so Chill and Floaty still show
                        // their bounce as a pulse at the end.
                        translationX = travelPx * progress.value.coerceIn(0f, 1f)
                        val dotScale = 1f + 0.14f * progress.value
                        scaleX = dotScale
                        scaleY = dotScale
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = "Tap to replay · ${style.displayName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

private val DotSize = 34.dp

private fun motionIcon(style: MotionStyle): ImageVector = when (style) {
    MotionStyle.INSTANT -> AppVectorIcons.MotionInstant
    MotionStyle.SMOOTH -> AppVectorIcons.MotionSmooth
    // Snappy reuses the smooth glyph: both are no-overshoot curves, and the pair
    // reads as "the same family, one is quicker" rather than as two unrelated ideas.
    MotionStyle.SNAPPY -> AppVectorIcons.MotionSmooth
    MotionStyle.CHILL -> AppVectorIcons.MotionChill
    MotionStyle.FLOATY -> AppVectorIcons.MotionChill
}

private fun motionDescription(style: MotionStyle): String = when (style) {
    MotionStyle.INSTANT ->
        "No movement at all: screen changes, sheets and presses land where they end. " +
            "Long titles stay still and ellipsize."
    MotionStyle.SMOOTH ->
        "Movement without overshoot: everything eases to its place and stops."
    MotionStyle.SNAPPY ->
        "Quicker than Smooth with the same clean landing - for readers who want the " +
            "app to feel immediate but not abrupt."
    MotionStyle.CHILL ->
        "The full expressive physics: springs with a visible overshoot on presses, " +
            "sheets and pages."
    MotionStyle.FLOATY ->
        "Slow and soft, with a low bounce: every move drifts into place."
}
