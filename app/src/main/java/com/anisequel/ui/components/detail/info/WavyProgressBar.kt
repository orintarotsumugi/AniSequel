package com.anisequel.ui.components.detail.info

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.domain.motion.WaveGeometry
import com.anisequel.ui.components.expressive.ExpressiveMotion

/**
 * The wavy determinate progress bar of the Info page's progress card.
 *
 * Hand-drawn rather than `LinearWavyProgressIndicator` - which this app *can*
 * reach, being on a Material 3 Expressive alpha - because the control on that page
 * is a wave whose *height* is the progress: it rises as the reader works through
 * the show and the part they have watched is the coloured part of it. The M3
 * component's amplitude, gap and wavelength parameters describe an indeterminate
 * wave with a separate determinate track, and cannot express "the wave you are
 * standing on is the amount you have watched". The geometry it shares with that
 * idea lives in [com.anisequel.domain.motion.WaveGeometry] so it can be tested.
 *
 * ## One wave, drawn twice
 *
 * The track and the fill are the *same* path, with the coloured part produced by a
 * clip rather than by a second path. That is not an optimisation - building the
 * fill as its own wave was a visible bug. It was given its own amplitude and its
 * own (travelling) phase, so the coloured stretch sat at a different height from
 * the track it was supposed to be the first part of: on screen it read as a short
 * squiggle floating over the beginning of a longer one, and the round head that
 * caps the fill landed off the curve it was capping. Sharing one path makes them
 * the same curve by construction, so they cannot drift apart again.
 *
 * ## Motion
 *
 * The fill springs up from zero once per media, with [ExpressiveMotion.FastSpatial]
 * - a bar that fills on arrival says "this is how far in you are" without the
 * viewer having to read the number beside it. The travelling phase is one
 * `rememberInfiniteTransition`, and it only runs when there is progress to show:
 * an idle bar has nothing to travel, and a looping animation on a screenful of
 * cards is exactly what the motion rules cap. Both reads happen inside the draw
 * lambda, so the animation never recomposes this card or its neighbours.
 */
@Composable
fun WavyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
) {
    val target = progress.coerceIn(0f, 1f)

    // A `Float` rather than `animateFloatAsState`: that initialises *at* its
    // target, so the bar would simply appear full. This starts at nothing and
    // travels, which is the whole entrance.
    val fill = remember(target) { Animatable(0f) }
    LaunchedEffect(target) {
        fill.animateTo(target, ExpressiveMotion.FastSpatial)
    }

    val cycle = if (target > 0f) {
        val transition = rememberInfiniteTransition(label = "wavy_progress_bar")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
        value
    } else {
        0f
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val midY = size.height / 2f
        val wavelength = size.width / WAVES_ACROSS
        val maxAmplitude = size.height * MAX_AMPLITUDE_RATIO
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)

        // `fill.value` is read here, in the draw lambda: the animation redraws this
        // Canvas and nothing else.
        val filled = size.width * fill.value
        // The height the wave stands at is the reader's progress - at rest when
        // nothing is watched, full height at the end - and both halves are drawn
        // from this one amplitude, so the wave is continuous across the seam.
        val amplitude = WaveGeometry.amplitude(maxAmplitude, progress = fill.value)
        val phase = WaveGeometry.phase(cycle)

        val wave = wavePath(
            width = size.width,
            wavelength = wavelength,
            amplitude = amplitude,
            phase = phase,
            midY = midY
        )

        // The whole wave, in the track colour. Drawn even at zero progress: this is
        // the wave an untouched title shows, and it is the same shape the fill will
        // start eating into.
        drawPath(path = wave, color = trackColor, style = stroke)

        if (filled <= 0.5f) return@Canvas

        // The first `filled` of the same path again, in the fill colour. A clip and
        // not a second path, so the fill cannot drift away from the track: it is
        // that curve, drawn twice.
        clipRect(right = filled) {
            drawPath(path = wave, color = waveColor, style = stroke)
        }

        // A round head on the leading edge, so the fill ends at a point rather
        // than at wherever the sine happened to be when the clip cut it. Sampled
        // from the same amplitude and phase as the path, so it sits on it.
        drawCircle(
            color = waveColor,
            radius = stroke.width / 2f,
            center = Offset(
                x = filled,
                y = midY + WaveGeometry.offset(filled, wavelength, amplitude, phase)
            )
        )
    }
}

private fun wavePath(
    width: Float,
    wavelength: Float,
    amplitude: Float,
    phase: Float,
    midY: Float
): Path {
    val path = Path()
    val step = width / WAVE_SAMPLES
    path.moveTo(0f, midY + WaveGeometry.offset(0f, wavelength, amplitude, phase))
    var x = step
    while (x < width) {
        path.lineTo(x, midY + WaveGeometry.offset(x, wavelength, amplitude, phase))
        x += step
    }
    path.lineTo(width, midY + WaveGeometry.offset(width, wavelength, amplitude, phase))
    return path
}

/** Two and a half periods across the bar: a wave, not a zigzag, at any width. */
private const val WAVES_ACROSS = 2.5f

/** Crest-to-trough is a third of the bar's height, which leaves room for the 5dp stroke. */
private const val MAX_AMPLITUDE_RATIO = 0.32f

/** Straight segments per point sampled. At a phone width this is sub-pixel. */
private const val WAVE_SAMPLES = 96
