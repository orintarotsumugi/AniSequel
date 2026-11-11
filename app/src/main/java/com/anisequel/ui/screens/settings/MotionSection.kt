package com.anisequel.ui.screens.settings

import android.animation.ValueAnimator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.icons.motionGlyph
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
                    icon = motionGlyph(style),
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
    // The glyph is the only part of the chip that is a picture of the choice, so
    // it is the part that reacts to being chosen: it springs up to full size while
    // the unselected marks sit slightly back. Read inside `graphicsLayer` below, so
    // the spring is a draw-phase invalidation and not a recomposition of the chip.
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.82f,
        animationSpec = ExpressiveMotion.SelectionScale,
        label = "motion_chip_icon_scale"
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
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
 * One stage that plays the chosen style's own physics.
 *
 * Two pieces, because the app's motion is two interactions: a piece travels to a
 * marked point with a ghost trail behind it, then the style's own glyph on a
 * capsule takes a press. Both drivers are the style's real tokens -
 * [ExpressiveMotion.DefaultSpatial] for the travel and
 * [ExpressiveMotion.PressSpatialIn] / [ExpressiveMotion.PressSpatialOut] for the
 * press - so the sample cannot flatter a style the app does not use.
 *
 * The travel is **not** clamped to the marker. Chill and Floaty are defined by the
 * way they pass it and come back, and the old sample clamped the dot so an
 * overshoot could not leave the card - which hid the one frame this preview exists
 * to show. The marker stops short of the stage's end instead, so the overshoot has
 * somewhere to go. Tapping replays it: one-shot, never infinite, because a sample
 * that never stops is a screen that never settles.
 */
@Composable
private fun MotionSample(style: MotionStyle) {
    var replay by remember(style) { mutableIntStateOf(0) }

    // Two drivers rather than one: the travel and the press are two springs with
    // different jobs, and a single progress value could only ever approximate one of
    // them. Both live inside the sample, so leaving the page stops the animation.
    val travel = remember(style) { Animatable(0f) }
    val press = remember(style) { Animatable(1f) }

    LaunchedEffect(style, replay) {
        press.snapTo(1f)
        travel.snapTo(0f)
        // The token is the preview: INSTANT snaps, SMOOTH arrives clean, SNAPPY is
        // quicker than SMOOTH, CHILL overshoots, FLOATY drifts. No bespoke spec
        // here, or the sample could flatter a style the app does not actually use.
        travel.animateTo(1f, ExpressiveMotion.DefaultSpatial)
        // Sequenced after the travel rather than with it: two springs overlapping in
        // a card this small read as one shaky shape, not as two behaviours.
        press.animateTo(0.9f, ExpressiveMotion.PressSpatialIn)
        press.animateTo(1f, ExpressiveMotion.PressSpatialOut)
    }

    val stageBase = MaterialTheme.colorScheme.surfaceContainerHigh
    val stageLit = MaterialTheme.colorScheme.secondaryContainer
    val railColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.26f)
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    val dotColor = MaterialTheme.colorScheme.primary
    val nearGhost = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val farGhost = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val capsuleColor = MaterialTheme.colorScheme.primary
    val onCapsule = MaterialTheme.colorScheme.onPrimary

    Surface(
        shape = MaterialTheme.shapes.large,
        color = stageBase,
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
                .height(150.dp)
                // The stage's colour used to be lerped in composition, so this
                // whole sample recomposed on every frame of the animation it was
                // previewing - a preview of motion that made the screen do more
                // work than the motion. The driver is read in the draw phase now.
                .drawBehind {
                    drawRect(
                        color = lerp(
                            stageBase,
                            stageLit,
                            travel.value.coerceIn(0f, 1f)
                        )
                    )
                }
                .padding(horizontal = 16.dp)
        ) {
            val dotSize = 30.dp
            // 78% of the inner width, not 100%: the marker is the settle point and
            // the fifth left over is the spring's room to overshoot into.
            val travelPx = with(LocalDensity.current) {
                ((maxWidth - dotSize) * 0.78f).toPx().coerceAtLeast(0f)
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        // The rail and the marker never move, so they are drawn rather
                        // than laid out - one shape each, no boxes to measure.
                        .drawBehind {
                            val centreY = size.height / 2f
                            val startX = dotSize.toPx() / 2f
                            drawLine(
                                color = railColor,
                                start = Offset(startX, centreY),
                                end = Offset(startX + travelPx, centreY),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = markerColor,
                                start = Offset(startX + travelPx, centreY - 9.dp.toPx()),
                                end = Offset(startX + travelPx, centreY + 9.dp.toPx()),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                ) {
                    // The trail, behind the piece and shrinking with distance. A
                    // stationary dot on a line reads as a slider; the ghosts are what
                    // make the track read as movement at every speed, including the
                    // slow one, where the piece itself barely appears to move.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(dotSize)
                            .graphicsLayer {
                                translationX = travelPx * (travel.value - 0.10f).coerceAtLeast(0f)
                                scaleX = 0.64f
                                scaleY = 0.64f
                            }
                            .clip(CircleShape)
                            .background(farGhost)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(dotSize)
                            .graphicsLayer {
                                translationX = travelPx * (travel.value - 0.05f).coerceAtLeast(0f)
                                scaleX = 0.8f
                                scaleY = 0.8f
                            }
                            .clip(CircleShape)
                            .background(nearGhost)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(dotSize)
                            .graphicsLayer {
                                // Raw, unclamped progress: the overshoot is the point.
                                translationX = travelPx * travel.value
                                val dotScale = 1f + 0.07f * travel.value
                                scaleX = dotScale
                                scaleY = dotScale
                            }
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                // The press tokens, not a scale chosen for the
                                // drawing: this is the same squash every button in
                                // the app gives under a finger.
                                scaleX = press.value
                                scaleY = press.value
                            }
                            .clip(ExpressiveShapes.pill)
                            .background(capsuleColor)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = motionGlyph(style),
                            contentDescription = null,
                            tint = onCapsule,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Press",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = onCapsule
                        )
                    }
                }

                Text(
                    text = "Tap to replay · ${style.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = hintColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )
            }
        }
    }
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
