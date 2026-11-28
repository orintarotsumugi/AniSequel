package com.anisequel.ui.components.detail.info

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.domain.motion.WaveGeometry
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.rememberIsResumed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The page's second wavy mark: a ring, closed, for a number that is itself a share.
 *
 * ## Why a ring and not another bar
 *
 * The score is the one value on the summary strip that is already a fraction of
 * something - 76 of 100 - and a reader compares it against the next title's rather
 * than reading it as a quantity. A ring is the shape that says "share of the whole"
 * without a label explaining it, which is why the reference puts the score inside
 * one. It is drawn from the same [WaveGeometry] arithmetic as the progress bar, so
 * the page's two wavy marks are one family rather than two squiggles that happen to
 * be near each other.
 *
 * ## The two states
 *
 * [progress] given means determinate: the track is the whole wavy ring and the
 * accent is the first share of it, starting at twelve o'clock and finishing with a
 * round cap. The sweep springs up from nothing once, so the ring arrives as a
 * measurement being taken rather than as a picture that was already there - the
 * same entrance the progress bar makes, from the same token.
 *
 * [progress] `null` means **loading**: the track stays, and a short accent arc
 * travels around it forever. That is the "circle loading" on this page - one moving
 * wavy ring in the score cell instead of a shimmer over a number that does not exist
 * yet, and the same mark inside the trailer frame while the embed paints.
 *
 * ## The wobble travels
 *
 * The outline is not a fixed squiggle. Its lobes drift around the ring the way the
 * progress bar's crests travel along its track, through the same
 * [WaveGeometry.phase] - which is what makes the page's two wavy marks one wave seen
 * from two directions rather than a moving bar beside a frozen ring.
 *
 * It used to be built at phase zero, always: the entrance swept the share in and the
 * loading arc turned, and between those two the score cell was static, so next to a
 * progress bar whose crests were visibly travelling the ring read as a picture of a
 * wave. The track and the accent are shifted by the *same* phase, because they are
 * the same outline drawn twice - a second phase would float the accent off the ring
 * it is supposed to be a share of, which is the mistake `WavyProgressBar` documents.
 *
 * ## Motion rules this obeys
 *
 * Two loops on **one** `rememberInfiniteTransition`: the loading arc's turn and the
 * slow drift of the wobble, which the motion rules cap at one transition per screen.
 * Both values are read only inside the draw lambda, so a frame invalidates this
 * Canvas and nothing else, and both exist only while the view is resumed and the
 * reader has not asked for Instant motion - an idle loader, or a score on a page
 * nobody is looking at, is a loop the motion rules cap. The drift is slow on
 * purpose: a seven lobe ring moving at a loader's rate reads as a spinner around a
 * number that is not loading.
 *
 * The entrance, the spin and the drawing are each their own function, so this
 * composable is only the slot and the content inside the ring rather than the whole
 * of the animation and the geometry as well.
 */
@Composable
internal fun NovaWavyRing(
    modifier: Modifier = Modifier,
    /**
     * The share of the ring to fill, 0..1 - or null for the loading state.
     *
     * Named for the loading case's absence rather than taking a boolean beside it:
     * "a ring with no progress yet" and "a ring showing no progress" are different
     * pictures, and a `Boolean isLoading` would let one call site mean the other.
     */
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.24f),
    strokeWidth: Dp = 4.dp,
    entranceKey: Any? = null,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val target = progress?.coerceIn(0f, 1f)
    val fill = rememberNovaRingFill(target = target, animate = animate, entranceKey = entranceKey)
    // One question, asked once: every loop this ring can run is gated on the same
    // three facts - the caller allows motion, the view is on screen, and the reader
    // has not asked for Instant.
    val motionEnabled = animate && rememberIsResumed() &&
        ExpressiveMotion.speed != MotionStyle.INSTANT
    val loops = rememberNovaRingLoops(enabled = motionEnabled)

    // Two paths for the life of the ring, rewound in place: the outline and the
    // accent share the arithmetic but not the object, and allocating either per
    // frame is the class of thing that turns a fling into a GC pause.
    val outline = remember { Path() }
    val accent = remember { Path() }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // `fill.value` and both loop values are read here, in the draw lambda: the
            // animations redraw this Canvas and nothing else.
            drawNovaRing(
                outline = outline,
                accent = accent,
                target = target,
                fill = fill.value,
                spin = loops?.spin?.value,
                wave = loops?.wave?.value ?: 0f,
                color = color,
                trackColor = trackColor,
                strokeWidth = strokeWidth.toPx()
            )
        }

        content()
    }
}

/**
 * The determinate ring's entrance: a spring from nothing to the share on screen.
 *
 * The "already played" flag is saved for the same reason the progress bar saves its
 * own: a `LazyColumn` disposes the items it has scrolled past, so a plain `remember`
 * replayed the rise every time the strip came back into view - a second measurement
 * of a score that had not changed.
 */
@Composable
private fun rememberNovaRingFill(
    target: Float?,
    animate: Boolean,
    entranceKey: Any?
): Animatable<Float, AnimationVector1D> {
    var played by rememberSaveable(entranceKey) { mutableStateOf(false) }
    val fill = remember(entranceKey) {
        Animatable(if (target == null || !animate || played) target ?: 0f else 0f)
    }
    LaunchedEffect(entranceKey, target, animate) {
        if (target == null) {
            // The loading state has no fraction to own; the filled share is the
            // travelling arc, drawn from the clock instead.
            fill.snapTo(0f)
            return@LaunchedEffect
        }
        played = true
        if (animate) fill.animateTo(target, ExpressiveMotion.FastSpatial) else fill.snapTo(target)
    }
    return fill
}

/** The two phases [NovaWavyRing] draws with. See [rememberNovaRingLoops]. */
private class NovaRingLoops(val spin: State<Float>, val wave: State<Float>)

/**
 * The ring's two clocks - the loading arc's turn and the wobble's drift - or null
 * when there is nothing to drive.
 *
 * **One transition for both.** The motion rules cap indefinite animations at one per
 * screen because a second transition is a second clock; here the two values are read
 * in the same draw lambda and draw one mark, so they come out of one transition and
 * are animated by the same frame.
 *
 * Null rather than a stopped transition, which is the other half of that rule: a
 * ring on a page nobody is looking at, or one whose reader asked for Instant motion,
 * must not cost a running animation at all. The spin is animated even when the ring
 * is drawing a settled score and has no use for it - one float on a transition that
 * already exists for the wave, rather than a second transition gated separately.
 */
@Composable
private fun rememberNovaRingLoops(enabled: Boolean): NovaRingLoops? {
    if (!enabled) return null
    val transition = rememberInfiniteTransition(label = "nova_wavy_ring")
    val wave = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = WAVE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nova_wavy_ring_wave_phase"
    )
    val spin = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SPIN_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nova_wavy_ring_spin_phase"
    )
    return NovaRingLoops(spin = spin, wave = wave)
}

/**
 * The ring itself: the whole outline, then the accent share on top of it.
 *
 * [fill], [spin] and [wave] arrive as plain values from the caller's draw lambda, so
 * the animations redraw the caller's Canvas and nothing else.
 */
private fun DrawScope.drawNovaRing(
    outline: Path,
    accent: Path,
    target: Float?,
    fill: Float,
    spin: Float?,
    wave: Float,
    color: Color,
    trackColor: Color,
    strokeWidth: Float
) {
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    // Divided by the wobble's crest so the furthest a lobe reaches is still inside
    // the slot: a ring drawn to the edge and then wobbled outwards has its stroke
    // clipped, which reads as a gap in the outline.
    val radius = (minOf(size.width, size.height) - stroke.width) / 2f / (1f + WOBBLE)
    if (radius <= 0f) return
    val centre = Offset(size.width / 2f, size.height / 2f)
    // Negated because the accent grows clockwise from twelve o'clock: the wave has to
    // travel the way the sweep moves, or the ring reads as two motions fighting over
    // one shape. One phase for both paths - they are the same outline drawn twice.
    val wobblePhase = -WaveGeometry.phase(wave)

    buildRingPath(outline, radius, 0f, 360f, centre, wobblePhase)
    drawPath(path = outline, color = trackColor, style = stroke)

    if (target == null) {
        // The travelling share. A rotate rather than an offset because the ring is
        // closed - there is nowhere for it to travel to, so the whole mark turns,
        // which is how a circular loader has always been read.
        rotate(degrees = (spin ?: 0f) * 360f, pivot = centre) {
            buildRingPath(accent, radius, 0f, LOADING_SWEEP_DEGREES, centre, wobblePhase)
            drawPath(path = accent, color = color, style = stroke)
        }
        return
    }

    val degrees = WaveGeometry.ringSweepDegrees(fill)
    if (degrees > 0.5f) {
        // From twelve o'clock, clockwise: a share of a ring is read from the top,
        // the way a clock face is.
        buildRingPath(accent, radius, START_DEGREES, degrees, centre, wobblePhase)
        drawPath(path = accent, color = color, style = stroke)
    }
}

/**
 * The wavy outline from [startDegrees] to [endDegrees], sampled around the circle.
 *
 * A polyline rather than cubics, and the same choice the progress bar's wave makes:
 * the wobble is a sine, a cubic fit of one drifts at its crests, and the crests are
 * the only part of this shape the eye follows. Six degrees a segment is sub-pixel at
 * any cell size this is used at.
 */
private fun buildRingPath(
    path: Path,
    radius: Float,
    startDegrees: Float,
    sweepDegrees: Float,
    centre: Offset,
    /** Where the wobble has travelled to, in radians. See [drawNovaRing]. */
    phase: Float
) {
    path.rewind()
    val steps = maxOf(MIN_SEGMENTS, (abs(sweepDegrees) / DEGREES_PER_SEGMENT).toInt())
    var step = 0
    while (step <= steps) {
        val degrees = startDegrees + sweepDegrees * step / steps
        val angle = degrees * DEGREES_TO_RADIANS
        val r = WaveGeometry.ringRadius(
            baseRadius = radius,
            wobble = WOBBLE,
            lobes = LOBES,
            angle = angle,
            phase = phase
        )
        val x = centre.x + cos(angle) * r
        val y = centre.y + sin(angle) * r
        if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        step++
    }
}

/** Lobe count: enough that the outline reads as a wave, few enough that it is one. */
private const val LOBES = 7

/** Crest-to-trough of the wobble, as a share of the radius. */
private const val WOBBLE = 0.07f

/** Twelve o'clock, in degrees - where a share of a ring starts. */
private const val START_DEGREES = -90f

/** The share of the ring the loading arc covers. */
private const val LOADING_SWEEP_DEGREES = 110f

/** How long the loading arc takes to come round. */
private const val SPIN_MILLIS = 1500

/**
 * How long the wobble takes to travel one lobe's spacing.
 *
 * Five seconds, against the loading arc's 1.5: the arc is a clock and this is water.
 * Fast enough that the score cell is visibly alive beside a travelling progress bar,
 * slow enough that it is not a spinner around a number that has already arrived.
 */
private const val WAVE_MILLIS = 5000

private const val DEGREES_PER_SEGMENT = 6f

private const val MIN_SEGMENTS = 24

private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()
