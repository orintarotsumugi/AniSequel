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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.anisequel.data.repository.MotionStyle
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.rememberIsResumed

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
 * The fill springs up from zero once per bar, with [ExpressiveMotion.FastSpatial]
 * - a bar that fills on arrival says "this is how far in you are" without the
 * viewer having to read the number beside it. The travelling phase is one
 * `rememberInfiniteTransition`, and it only runs when there is progress to show:
 * an idle bar has nothing to travel, and a looping animation on a screenful of
 * cards is exactly what the motion rules cap.
 *
 * Both halves of that description were wrong in the code that this KDoc was
 * written for: the travelling phase was read in composition, so the card did
 * recompose on every frame of a loop that never ends, and the entrance was held
 * in a plain `remember`, which a `LazyColumn` discards with the item - so
 * scrolling past the card and back replayed the whole fill from zero. The reads
 * now happen where this says they do, and the entrance is saved; the comments at
 * each site carry the details.
 */
@Composable
fun WavyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    /**
     * The un-watched part of the curve.
     *
     * 0.45 rather than the 0.28 this carried: at the old alpha the track all but
     * vanished on a dark themed surface, so a title at zero progress showed a
     * faint squiggle with nothing above it and the card read as a rendering
     * failure rather than as an empty bar.
     */
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
    /**
     * Re-runs the entrance when this changes - the title's identity, in practice.
     *
     * The "already played" flag is saved so scrolling the card off screen and back
     * does not replay the fill, but it was saved for the *sheet* rather than for the
     * title: walking a franchise reuses the same list item, so a new show's bar
     * appeared already full instead of rising from zero.
     *
     * An id is not an identity on its own, either. A walk crosses the anime/manga
     * boundary, where `anime/7` and `manga/7` are different shows, so the caller
     * passes type *and* id (see [InfoProgressCard]); keyed on the id alone the saved
     * flag survived the crossing and the second title's bar arrived full again.
     */
    entranceKey: Any? = null
) {
    val target = progress.coerceIn(0f, 1f)

    // The entrance happens once per bar, and the flag that says so is
    // `rememberSaveable` for the same reason `sectionReveal`'s is: a `LazyColumn`
    // disposes the items it has scrolled past and keeps their saved state under the
    // item's key, so this flag outlives the bar leaving the screen. A plain
    // `remember` lost it with the item, and the bar replayed the entire fill from
    // zero on the way back up the page - a second counting of a progress the reader
    // had already watched arrive.
    //
    // Keyed on the title, because the saved flag was scoped to the sheet: following
    // a relation to the next title reuses this list item, so the restored flag said
    // "already played" and the new title's bar arrived full instead of rising.
    var played by rememberSaveable(entranceKey) { mutableStateOf(false) }
    // A `Float` rather than `animateFloatAsState`: that initialises *at* its
    // target, so the bar would simply appear full rather than filling. Where this
    // one starts is the flag's decision - at nothing for a bar that has never
    // played, at the progress it is about to show for one that has, which is how a
    // bar restored from a scroll keeps the height it already had instead of rising
    // a second time.
    val fill = remember(entranceKey) { Animatable(if (played) target else 0f) }
    // Unconditional, and it checks the flag's meaning rather than being wrapped in
    // `if (!played)`: a `LaunchedEffect` skipped on the played path would also never
    // see a *later* progress - the entry editor's own writes - and the bar would sit
    // on a number that is no longer true. Entering from zero and re-targeting from
    // the current value are the same call; the flag only decides where the fill
    // starts.
    //
    // Keyed on the entrance too, and that is not belt-and-braces: two titles can
    // have the same fraction - a 6/12 show followed by another 6/12 show - and an
    // effect keyed on the value alone would not restart, leaving the freshly
    // remembered fill sitting at zero for a bar that should be half full.
    LaunchedEffect(entranceKey, target) {
        played = true
        fill.animateTo(target, ExpressiveMotion.FastSpatial)
    }

    // The travelling phase, held as a `State` and read only in the draw lambda
    // below.
    //
    // It used to be read here, in composition - `val value by
    // transition.animateFloat(...)` - which subscribed this card to its own
    // animation and made every frame of a loop that never ends a recomposition of
    // the card and its neighbours, on a page that already asked too much of the
    // frame. The KDoc above claimed the draw-phase read the whole time; the code
    // did not do it. As a `State`, only the `.value` in the draw lambda touches
    // the animation, so a frame invalidates this Canvas and nothing else.
    //
    // Null while there is nothing to travel: an idle bar draws a still wave, and a
    // looping animation on a screenful of cards is exactly what the motion rules
    // cap.
    //
    // Gated on the *clock*, not on the progress. It used to be `if (target > 0f)`,
    // which flipped the composable shape every time a title went from untouched to
    // one episode in - and back on the way through a list - disposing and
    // rebuilding the transition at the exact moment the reader interacts with the
    // bar. Whether the loop is allowed to exist at all is a visibility and a
    // motion-style question, which is what [rememberIsResumed] and the Instant
    // style answer; `target` only decides whether the phase is *read*, below.
    val animates = rememberIsResumed() && ExpressiveMotion.speed != MotionStyle.INSTANT
    val cycle: State<Float>? = if (animates) {
        val transition = rememberInfiniteTransition(label = "wavy_progress_bar")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = WAVE_CYCLE_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
    } else {
        null
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

        // `fill.value` and `cycle?.value` are read here, in the draw lambda: the
        // animations redraw this Canvas and nothing else.
        val filled = size.width * fill.value
        // The height the wave stands at is the reader's progress - at rest when
        // nothing is watched, full height at the end - and both halves are drawn
        // from this one amplitude, so the wave is continuous across the seam.
        val amplitude = WaveGeometry.amplitude(maxAmplitude, progress = fill.value)
        // Null when the bar was idle at composition time, which is a still wave -
        // zero phase, the shape the track has always had. `target` is read here
        // rather than at the transition above: a bar nobody has started does not
        // pay for a moving phase.
        val phase = WaveGeometry.phase(if (target > 0f) cycle?.value ?: 0f else 0f)

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

        if (filled > 0.5f) {
            // The first `filled` of the same path again, in the fill colour. A clip
            // and not a second path, so the fill cannot drift away from the track:
            // it is that curve, drawn twice.
            clipRect(right = filled) {
                drawPath(path = wave, color = waveColor, style = stroke)
            }
        }

        // A round head on the leading edge, so the fill ends at a point rather
        // than at wherever the sine happened to be when the clip cut it. Sampled
        // from the same amplitude and phase as the path, so it sits on it.
        //
        // Drawn at zero too, half a stroke in from the left edge: a bar at 0%
        // used to return before this and draw nothing but a dim wave, which on a
        // freshly added title looked like a broken control rather than a bar that
        // has not started. The dot is the start of the fill and it is in the fill's
        // colour, so the empty state still says "this is the beginning".
        val headX = if (filled > 0.5f) filled else stroke.width / 2f
        drawCircle(
            color = waveColor,
            radius = stroke.width / 2f,
            center = Offset(
                x = headX,
                y = midY + WaveGeometry.offset(headX, wavelength, amplitude, phase)
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

/** How long the travelling phase takes to come round. */
private const val WAVE_CYCLE_MILLIS = 2400

/** Crest-to-trough is a third of the bar's height, which leaves room for the 5dp stroke. */
private const val MAX_AMPLITUDE_RATIO = 0.32f

/** Straight segments per point sampled. At a phone width this is sub-pixel. */
private const val WAVE_SAMPLES = 96
