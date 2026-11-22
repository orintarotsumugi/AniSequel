package com.anisequel.ui.components.detail.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.domain.ui.ProgressBarStyle
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.theme.LocalProgressBarStyle
import com.anisequel.ui.theme.LocalSetProgressBarStyle
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The progress card's bar, in whichever style the reader chose.
 *
 * The choice is held in [LocalProgressBarStyle] rather than passed down from the
 * detail sheet because three surfaces read it - the Nova card, the classic card
 * and the picker's own previews - and threading one enum through the sheet, both
 * pages and their section lists would be three places to forget it.
 *
 * A long press of [HOLD_TO_EDIT_MILLIS] expands the three choices underneath the
 * bar, inside the same card. The delay is deliberate: this is a bar inside a
 * scrolling page, so an ordinary tap or the first moment of a fling must not take
 * over the screen. Choosing a style writes it and collapses the row immediately;
 * holding the bar again closes it without changing anything.
 */
@Composable
fun StyledProgressBar(
    progress: Float,
    entranceKey: Any?,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = AniSequelTheme.emphasis.track,
    animate: Boolean = true
) {
    val style = LocalProgressBarStyle.current
    val setStyle = LocalSetProgressBarStyle.current
    // Keyed to the title like the bar's entrance: following a relation reuses the
    // same list item, and an expanded row from the previous title must not arrive
    // open on a page the reader has just opened.
    var showPicker by rememberSaveable(entranceKey) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .holdToPickStyle { showPicker = !showPicker }
        ) {
            InfoProgressBar(
                progress = progress,
                style = style,
                modifier = Modifier.fillMaxWidth(),
                height = height,
                progressColor = progressColor,
                trackColor = trackColor,
                entranceKey = entranceKey,
                animate = animate
            )
        }

        AnimatedVisibility(
            visible = showPicker,
            enter = expandVertically(animationSpec = ExpressiveMotion.FastSpatialSize) +
                fadeIn(animationSpec = ExpressiveMotion.FastEffects),
            exit = shrinkVertically(animationSpec = ExpressiveMotion.FastSpatialSize) +
                fadeOut(animationSpec = ExpressiveMotion.FastEffects)
        ) {
            ProgressStyleChoices(
                selected = style,
                onSelect = { picked ->
                    setStyle(picked)
                    showPicker = false
                },
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

/** Dispatches to the chosen drawing. Animation and entrance handling stay per style. */
@Composable
internal fun InfoProgressBar(
    progress: Float,
    style: ProgressBarStyle,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = AniSequelTheme.emphasis.track,
    entranceKey: Any? = null,
    animate: Boolean = true
) {
    when (style) {
        ProgressBarStyle.WAVE -> WavyProgressBar(
            progress = progress,
            modifier = modifier,
            height = height,
            waveColor = progressColor,
            trackColor = trackColor,
            entranceKey = entranceKey,
            animate = animate
        )
        ProgressBarStyle.BAR -> SimpleProgressBar(
            progress = progress,
            modifier = modifier,
            height = height,
            progressColor = progressColor,
            trackColor = trackColor,
            entranceKey = entranceKey,
            animate = animate
        )
        ProgressBarStyle.ROCKET -> RocketProgressBar(
            progress = progress,
            modifier = modifier,
            height = height,
            progressColor = progressColor,
            trackColor = trackColor,
            entranceKey = entranceKey,
            animate = animate
        )
    }
}

/**
 * The plain reading: a rounded track with the watched share filled.
 *
 * The fill is a two-stop ramp from the page's primary to its tertiary rather than
 * a flat primary, because this bar has no wave to carry the eye and a single
 * rectangle reads as a loading indicator. The end cap is drawn only once there is
 * a filled length to cap, so a zero-progress bar shows an honest empty track.
 */
@Composable
private fun SimpleProgressBar(
    progress: Float,
    modifier: Modifier,
    height: Dp,
    progressColor: Color,
    trackColor: Color,
    entranceKey: Any?,
    animate: Boolean
) {
    val fill by rememberAnimatedProgress(progress, entranceKey, animate)
    val endColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val trackHeight = minOf(9.dp.toPx(), size.height * 0.34f)
        val top = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeight),
            cornerRadius = radius
        )

        val filled = size.width * fill
        if (filled > 0.5f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(progressColor, endColor),
                    startX = 0f,
                    endX = size.width
                ),
                topLeft = Offset(0f, top),
                size = Size(filled, trackHeight),
                cornerRadius = radius
            )
            drawCircle(
                color = endColor,
                radius = trackHeight / 2f,
                center = Offset(filled, size.height / 2f)
            )
        }
    }
}

/**
 * The journey: a dashed route, a moon at the destination and the rocket at the
 * reader's place on it.
 *
 * The rocket is at the head of the filled stretch instead of beside it, so
 * "where am I" is answered by the ship's position and by how much route is lit
 * behind it. The moon is always drawn: a destination that only appeared at 100%
 * would make an unfinished bar look like a bar with no end.
 */
@Composable
private fun RocketProgressBar(
    progress: Float,
    modifier: Modifier,
    height: Dp,
    progressColor: Color,
    trackColor: Color,
    entranceKey: Any?,
    animate: Boolean
) {
    val fill by rememberAnimatedProgress(progress, entranceKey, animate)
    val moonColor = MaterialTheme.colorScheme.tertiary
    val craterColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.34f)
    val windowColor = MaterialTheme.colorScheme.surface
    val flameColor = MaterialTheme.colorScheme.secondary
    val routeEndColor = MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val midY = size.height / 2f
        val moonRadius = minOf(7.dp.toPx(), size.height * 0.19f)
        val moonCenter = Offset(size.width - moonRadius - 3.dp.toPx(), midY)
        val startX = 7.dp.toPx()
        val endX = (moonCenter.x - moonRadius - 10.dp.toPx()).coerceAtLeast(startX + 1f)
        val rocketX = startX + (endX - startX) * fill

        // The route nobody has travelled yet. Dotted rather than solid so the
        // moon reads as a destination rather than as the end of a filled track.
        drawLine(
            color = trackColor.copy(alpha = 0.58f),
            start = Offset(startX, midY),
            end = Offset(endX, midY),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(5.dp.toPx(), 5.dp.toPx())
            )
        )

        drawCircle(
            color = moonColor.copy(alpha = 0.16f),
            radius = moonRadius * 1.85f,
            center = moonCenter
        )
        drawCircle(color = moonColor, radius = moonRadius, center = moonCenter)
        drawCircle(
            color = craterColor,
            radius = moonRadius * 0.23f,
            center = Offset(moonCenter.x - moonRadius * 0.32f, moonCenter.y - moonRadius * 0.2f)
        )
        drawCircle(
            color = craterColor,
            radius = moonRadius * 0.16f,
            center = Offset(moonCenter.x + moonRadius * 0.3f, moonCenter.y + moonRadius * 0.3f)
        )

        if (rocketX > startX + 1f) {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(progressColor, routeEndColor),
                    startX = startX,
                    endX = endX
                ),
                start = Offset(startX, midY),
                end = Offset(rocketX, midY),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        drawRocket(
            center = Offset(rocketX, midY),
            barHeight = size.height,
            body = progressColor,
            window = windowColor,
            flame = flameColor
        )
    }
}

/**
 * The ship, drawn pointing right so its travel is the page's reading direction.
 *
 * The fins and the flame are separate paths from the body: a single path cannot
 * carry both a body colour and a flame colour through the painter's tint, and the
 * window's cut-out would fill with the body colour at the small sizes this bar
 * uses.
 */
private fun DrawScope.drawRocket(
    center: Offset,
    barHeight: Float,
    body: Color,
    window: Color,
    flame: Color
) {
    val scale = (barHeight * 0.36f).coerceIn(6.5f, 13f)
    rotate(degrees = 90f, pivot = center) {
        val rocket = Path().apply {
            moveTo(center.x, center.y - scale)
            cubicTo(
                center.x + scale * 0.52f,
                center.y - scale * 0.58f,
                center.x + scale * 0.52f,
                center.y + scale * 0.42f,
                center.x + scale * 0.30f,
                center.y + scale * 0.68f
            )
            lineTo(center.x - scale * 0.30f, center.y + scale * 0.68f)
            cubicTo(
                center.x - scale * 0.52f,
                center.y + scale * 0.42f,
                center.x - scale * 0.52f,
                center.y - scale * 0.58f,
                center.x,
                center.y - scale
            )
            close()
        }
        drawPath(rocket, body)

        val leftFin = Path().apply {
            moveTo(center.x - scale * 0.30f, center.y + scale * 0.24f)
            lineTo(center.x - scale * 0.78f, center.y + scale * 0.72f)
            lineTo(center.x - scale * 0.24f, center.y + scale * 0.63f)
            close()
        }
        val rightFin = Path().apply {
            moveTo(center.x + scale * 0.30f, center.y + scale * 0.24f)
            lineTo(center.x + scale * 0.78f, center.y + scale * 0.72f)
            lineTo(center.x + scale * 0.24f, center.y + scale * 0.63f)
            close()
        }
        drawPath(leftFin, body.copy(alpha = 0.82f))
        drawPath(rightFin, body.copy(alpha = 0.82f))

        drawCircle(
            color = window,
            radius = scale * 0.23f,
            center = Offset(center.x, center.y - scale * 0.20f)
        )
        drawCircle(
            color = body.copy(alpha = 0.45f),
            radius = scale * 0.23f,
            center = Offset(center.x, center.y - scale * 0.20f),
            style = Stroke(width = 1.1.dp.toPx())
        )

        val exhaust = Path().apply {
            moveTo(center.x - scale * 0.22f, center.y + scale * 0.68f)
            lineTo(center.x, center.y + scale * 1.18f)
            lineTo(center.x + scale * 0.22f, center.y + scale * 0.68f)
            close()
        }
        drawPath(exhaust, flame)
    }
}

/**
 * The shared entrance for the two new styles.
 *
 * A plain `animateFloatAsState` would begin at its target, so the first frame of a
 * freshly opened title would already show the full rocket/bar position - the same
 * failure the wave's documented `Animatable` avoids. The saved flag keeps a scroll
 * away and back from replaying the entrance, and the key makes a new title start
 * from the beginning even when it has the same fraction as the previous one.
 */
@Composable
private fun rememberAnimatedProgress(
    progress: Float,
    entranceKey: Any?,
    animate: Boolean
): State<Float> {
    val target = progress.coerceIn(0f, 1f)
    var played by rememberSaveable(entranceKey) { mutableStateOf(false) }
    val fill = remember(entranceKey) {
        Animatable(if (!animate || played) target else 0f)
    }
    LaunchedEffect(entranceKey, target, animate) {
        played = true
        if (animate) {
            fill.animateTo(target, ExpressiveMotion.FastSpatial)
        } else {
            fill.snapTo(target)
        }
    }
    return remember(fill) { derivedStateOf { fill.value } }
}

/**
 * Holds [onHold] for [HOLD_TO_EDIT_MILLIS] before firing, and gives up the moment
 * the pointer becomes a scroll.
 *
 * `waitForUpOrCancellation` returns null when another gesture consumes the move,
 * which is exactly the slop-crossing case: a fling that starts on the bar scrolls
 * the page and never opens the picker. The timeout is not a system long press; it
 * is a deliberate three-second dwell, and the haptic at the end is what tells the
 * reader the sheet is about to arrive.
 */
private fun Modifier.holdToPickStyle(onHold: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val currentOnHold by rememberUpdatedState(onHold)
    this
        .semantics {
            onLongClick(label = "Change the progress bar style") {
                currentOnHold()
                true
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                // `waitForUpOrCancellation()` also returns null when scrolling
                // consumes the gesture. Keep that distinct from the timeout:
                // only an actual three-second hold should open the picker.
                val held = withTimeoutOrNull(HOLD_TO_EDIT_MILLIS) {
                    if (waitForUpOrCancellation() == null) {
                        return@withTimeoutOrNull false
                    }
                    false
                } ?: true
                if (held) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnHold()
                }
            }
        }
}

/** Long enough to be deliberate, short enough not to feel stuck. */
private const val HOLD_TO_EDIT_MILLIS = 3_000L
