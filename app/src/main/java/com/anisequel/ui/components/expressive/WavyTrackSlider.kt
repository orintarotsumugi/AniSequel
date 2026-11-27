package com.anisequel.ui.components.expressive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.domain.motion.WaveGeometry
import com.anisequel.ui.components.rememberIsResumed
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A [Slider] whose track is a travelling sine wave.
 *
 * ## Why a wave and not [androidx.compose.material3.LinearWavyProgressIndicator]
 *
 * The progress indicator is a determinate bar: it fills left to right and its
 * wave height follows the value. A slider also needs a thumb the reader can
 * grab, snapping stops, and a track that does not draw a second rounded bar
 * underneath the wave. The indicator cannot be the track of a [Slider], and
 * stacking one behind the default track hides the movement under that bar. So
 * the wave is drawn here with [WaveGeometry], the same arithmetic the Info page's
 * progress card uses, and the default track is made transparent.
 *
 * ## Why it only moves while touched
 *
 * An indefinite transition on a Settings card keeps a frame clock running for a
 * control nobody is holding - the seasonal layers gate their loops on visibility
 * for the same reason. The phase is a loop that starts with the press, finishes
 * the turn it is on when the press ends, and then stops at the seam (phase 1 is
 * phase 0, so the shape is unchanged). The card is still when it is not being
 * used, and the wave travels exactly while the reader is holding it.
 *
 * The wave also stands taller while held, which gives the press a visible answer
 * before the thumb has crossed a stop.
 */
@Composable
internal fun WavyTrackSlider(
    state: SliderState,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    trackHeight: Dp = 44.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val engaged = pressed || dragged
    // The default track fills from the right in RTL, and the thumb is placed on
    // that side; a canvas that always filled left-to-right would light the end
    // the thumb is not on.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Both gates the file was missing: the loop ran while the app was behind another
    // one (AGENTS.md section 3 caps indefinite animations on visibility) and it ran
    // under Instant motion, where every token in `ExpressiveMotion` is a `snap()` -
    // so this was the one control in the app that still travelled for a reader who
    // had asked for no travel.
    val motionAllowed = rememberIsResumed() && ExpressiveMotion.speed != MotionStyle.INSTANT
    val phase = remember { Animatable(0f) }
    LaunchedEffect(engaged, motionAllowed) {
        if (!motionAllowed) return@LaunchedEffect
        // One turn at a time, rather than a snap back to the start on every press:
        // the effect that replaces this one on release finishes the turn already
        // running, and a press that arrives while that finish is in flight
        // continues the wave from where it is instead of making the crests jump
        // sideways.
        while (true) {
            val remaining = (1f - phase.value).coerceIn(0f, 1f)
            if (remaining > 0f) {
                phase.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        // The tail of a turn keeps the wave's speed: the shorter
                        // the distance left, the shorter the animation.
                        durationMillis = (remaining * CYCLE_MILLIS).roundToInt()
                            .coerceAtLeast(1),
                        easing = LinearEasing
                    )
                )
            }
            // Phase 1 and phase 0 draw the same sine, so the seam is invisible.
            phase.snapTo(0f)
            // `engaged` is this effect's key, so it is constant for this run; a
            // release falls out after finishing the turn it inherited.
            if (!engaged) break
        }
    }

    // 0 is the wave's resting height; WaveGeometry puts the floor there. The
    // animation is what makes the press answer before the thumb crosses a stop.
    // Read inside the draw lambda rather than here: reading it in composition
    // would recompose this control on every frame of the spring, and nothing it
    // composes depends on the value.
    val presence = animateFloatAsState(
        targetValue = if (engaged) 1f else 0f,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "wavy_slider_presence"
    )

    Slider(
        state = state,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = SliderDefaults.colors(
            thumbColor = activeColor,
            // The wave below is the track. The default track draws a rounded
            // bar over it, which hides the only part of this control that moves.
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        ),
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = SliderDefaults.colors(thumbColor = activeColor),
                enabled = enabled
            )
        },
        track = { sliderState ->
            Canvas(
                // The stroke's round caps overhang the track by half its width;
                // without this they would paint into the card's padding.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clipToBounds()
            ) {
                drawWavyTrack(
                    fraction = sliderState.coercedValueAsFraction,
                    phase = phase.value,
                    presence = presence.value,
                    isRtl = isRtl,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor
                )
            }
        }
    )
}

private fun DrawScope.drawWavyTrack(
    fraction: Float,
    phase: Float,
    presence: Float,
    isRtl: Boolean,
    activeColor: Color,
    inactiveColor: Color
) {
    if (size.width <= 0f || size.height <= 0f) return

    val strokeWidth = 4.dp.toPx()
    // A crest that reaches the track's edge is clipped; the stroke's own width
    // is part of the budget, not a margin after it.
    val peakAmplitude = min(size.height / 2f - strokeWidth, 7.dp.toPx()).coerceAtLeast(0f)
    // The same rest-to-full curve the Info page's bar uses, floor included: a
    // wave that lies flat at rest would read as a different track from the one a
    // held thumb shows.
    val amplitude = WaveGeometry.amplitude(peakAmplitude, presence)
    val wavelength = max(size.width / 3.5f, 72.dp.toPx())
    val radians = WaveGeometry.phase(phase)
    val midY = size.height / 2f

    drawPath(
        path = wavePath(0f, size.width, midY, wavelength, amplitude, radians),
        color = inactiveColor,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    val clamped = fraction.coerceIn(0f, 1f)
    if (clamped <= 0f) return
    val activeLength = size.width * clamped
    // Painted as its own run of the same sine rather than a clip of the full
    // one: a clip cuts the stroke and the glow vertically at the thumb, and the
    // M3 handle is narrower than either, so the cut would be visible on both
    // sides of it. A separate path ends in a round cap and reads as the wave
    // growing out from under the handle.
    val activeStart = if (isRtl) size.width - activeLength else 0f
    val activeWave = wavePath(
        startX = activeStart,
        endX = activeStart + activeLength,
        midY = midY,
        wavelength = wavelength,
        amplitude = amplitude,
        radians = radians
    )

    // A wider, translucent stroke under the solid one. It gives the wave a soft
    // edge without a second colour role to keep in step with the theme.
    drawPath(
        path = activeWave,
        color = activeColor.copy(alpha = 0.18f),
        style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
    )
    drawPath(
        path = activeWave,
        color = activeColor,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

/** One full turn of the wave. Slow enough to read as travel, not as a vibration. */
private const val CYCLE_MILLIS = 1400

/**
 * The sine from [startX] to [endX], sampled at one point every 3dp.
 *
 * The ceiling keeps a tablet-width track from building a thousand-point path;
 * the floor keeps a short active run (the first stop) curved rather than a
 * single straight segment.
 */
private fun DrawScope.wavePath(
    startX: Float,
    endX: Float,
    midY: Float,
    wavelength: Float,
    amplitude: Float,
    radians: Float
): Path {
    val path = Path()
    val span = endX - startX
    val samples = (span / 3.dp.toPx()).roundToInt().coerceIn(8, 320)
    for (index in 0..samples) {
        val x = startX + span * index / samples
        val y = midY + WaveGeometry.offset(x, wavelength, amplitude, radians)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}
