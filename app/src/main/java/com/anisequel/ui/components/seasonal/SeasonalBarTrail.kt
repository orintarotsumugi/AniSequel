package com.anisequel.ui.components.seasonal

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.SeasonalPile
import com.anisequel.domain.seasonal.isWinterSnow
import kotlin.math.PI
import kotlin.math.sin

/**
 * The snow the sky leaves on the navigation bar.
 *
 * ## Why a cap rather than a taller drift
 *
 * The drift is drawn from the screen's bottom edge and the bar floats over it, so
 * the bar is a hole in the snow. A drift alone therefore reads as the bar sitting
 * *behind* the weather rather than in it. This is the other half: a strip gathered
 * along the capsule's top edge, a few drips over its face and a slow sparkle, so
 * the bar looks like one of the things the sky has been landing on.
 *
 * The cap takes its thickness from the same [SeasonalPile] the drift is drawn
 * from. That is the point of drawing it at all: a cap that always had the same
 * height would be a decoration stuck to the bar, while one that thickens where the
 * drift below it is thickest is the heap seen from a different angle. When
 * Settling is off the pile is null and the cap falls back to a thin dusting, so a
 * snow sky still leaves a little behind on the bar.
 *
 * ## Coordinates
 *
 * [SeasonalChrome.barBounds] is the pill's own rectangle in root coordinates, not
 * the outer row that carries the navigation inset and the twelve dp of padding.
 * The layer and this canvas are both match-parent overlays of the same root, so
 * those bounds are already in this draw's space. Nothing here measures, guesses or
 * assumes the bar's width: the actual capsule is the geometry.
 *
 * ## Cost
 *
 * One path, one shadow pass and at most four drips and six dots. It is drawn only
 * while a winter sky is on and the bar is visible, and it is not animated by an
 * extra clock - it reads the sky's existing one, so a frame of the cap costs no
 * more than a frame the sky was already paying for.
 */
@Composable
internal fun SeasonalBarTrail(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    // One path for the life of the bar, not one per frame. The cap is rebuilt in the
    // path every frame anyway; building the path object itself was the only allocation
    // in an otherwise allocation-free draw.
    val path = remember { Path() }
    Canvas(modifier = modifier) {
        if (!visible) return@Canvas
        // Read in the draw body, not the composable: these invalidate the draw, which
        // the layer already does sixty times a second anyway, rather than recomposing
        // the chrome host.
        val bounds = SeasonalChrome.barBounds ?: return@Canvas
        val effect = SeasonalChrome.skyEffect ?: return@Canvas
        if (!effect.isWinterSnow) return@Canvas
        // Observing the clock is what makes the drips breathe and the pinch of light
        // move. With the sky paused or set to Instant the clock stops, and the cap is a
        // still picture - which is what a still sky should look like.
        val seconds = SeasonalChrome.seconds
        drawBarSnowCap(effect, SeasonalChrome.skyPile, bounds, seconds, path)
    }
}

private class TrailInk(val fill: Color, val rim: Color, val shadow: Color)

private val POWDER_TRAIL = TrailInk(
    fill = Color(0xFFF1F9FF),
    rim = Color(0xFFFFFFFF),
    shadow = Color(0xFFB7D5EC)
)
private val CRYSTAL_TRAIL = TrailInk(
    fill = Color(0xFFEAF6FF),
    rim = Color(0xFFFFFFFF),
    shadow = Color(0xFF8FB6D4)
)
private val NEEDLE_TRAIL = TrailInk(
    fill = Color(0xFFE7F4FC),
    rim = Color(0xFFFCFEFF),
    shadow = Color(0xFF9CBFD8)
)

private fun trailInk(effect: SeasonalEffect): TrailInk = when (effect) {
    SeasonalEffect.CRYSTAL -> CRYSTAL_TRAIL
    SeasonalEffect.NEEDLE -> NEEDLE_TRAIL
    else -> POWDER_TRAIL
}

/** Fractions across the capsule that can carry a drip. Stable, not random per frame. */
private val DRIP_FRACTIONS = floatArrayOf(0.16f, 0.38f, 0.64f, 0.85f)

private fun DrawScope.drawBarSnowCap(
    effect: SeasonalEffect,
    pile: SeasonalPile?,
    bounds: Rect,
    seconds: Float,
    path: Path
) {
    if (bounds.width < 80f || bounds.height < 12f) return
    val ink = trailInk(effect)
    val radius = bounds.height / 2f
    // Stay inside the pill's flat top. A cap that reached the rounded ends would
    // straighten the capsule's own silhouette.
    val inset = radius * 0.62f
    val left = bounds.left + inset
    val right = bounds.right - inset
    if (right - left < 24f) return
    val top = bounds.top
    val lift = 1.2.dp.toPx()
    val baseLip = 1.5.dp.toPx()
    val lipSpan = 4.6.dp.toPx()
    // A pile of 0..1 grows the cap by about four dp at the deepest. The cap is a
    // garnish on a control, not a second drift: anything taller starts covering the
    // icons it is resting above.
    val pileDepth = { fraction: Float -> pile?.heightAt(fraction) ?: 0.055f }

    buildCap(path, left, right, top, lift, baseLip, lipSpan, pileDepth, seconds)
    // A soft shadow under the cap, so it reads as a layer of snow on a surface rather
    // than a white stripe printed on the pill.
    //
    // The offset is converted outside the `withTransform` lambda: that lambda runs
    // with a `DrawTransform` receiver, not a `DrawScope`, so `Dp.toPx()` has no
    // density in scope there - the first build failed on exactly this line.
    val shadowLift = 1.4.dp.toPx()
    withTransform({ translate(0f, shadowLift) }) {
        drawPath(path, ink.shadow.copy(alpha = 0.28f))
    }
    drawPath(path, ink.fill.copy(alpha = 0.96f))
    drawPath(path, ink.rim.copy(alpha = 0.72f), style = Stroke(width = 0.9.dp.toPx()))

    drawCapDrips(left, right, top, baseLip, lipSpan, pileDepth, ink, seconds)
    drawCapSparkle(left, right, top, lift, pileDepth, ink, seconds)
}

/**
 * Builds a wavy snow strip whose top and bottom edges both taper to nothing at the
 * ends, so the cap sits on the capsule instead of ending in two cut-off corners.
 *
 * Both edges are sampled at the same fractions and joined with midpoint quadratics,
 * the construction the drift itself uses - one curve per sample, tangent-continuous,
 * no spline solve in a per-frame path.
 */
private fun buildCap(
    path: Path,
    left: Float,
    right: Float,
    top: Float,
    lift: Float,
    baseLip: Float,
    lipSpan: Float,
    pileDepth: (Float) -> Float,
    seconds: Float
) {
    val samples = 22
    fun xAt(index: Int): Float = left + (right - left) * index / samples
    fun taperAt(index: Int): Float {
        val t = index.toFloat() / samples
        return sin(t * PI.toFloat()).coerceIn(0f, 1f)
    }
    fun lipAt(index: Int): Float {
        val t = index.toFloat() / samples
        val level = pileDepth(t)
        return (baseLip + lipSpan * (0.35f + level)) * (0.30f + 0.70f * taperAt(index))
    }
    fun topAt(index: Int): Float {
        val t = index.toFloat() / samples
        val slow = sin(t * PI.toFloat() * 3f + seconds * 0.22f) * 0.55f
        return top - lift * (0.55f + 0.45f * taperAt(index)) + slow
    }
    fun bottomAt(index: Int): Float {
        val t = index.toFloat() / samples
        val ripple = sin(t * PI.toFloat() * 4f - seconds * 0.33f) * 0.45f
        return top + lipAt(index) + ripple
    }

    path.reset()
    var px = xAt(0)
    var py = topAt(0)
    path.moveTo(px, py)
    for (index in 1..samples) {
        val nx = xAt(index)
        val ny = topAt(index)
        path.quadraticBezierTo(px, py, (px + nx) * 0.5f, (py + ny) * 0.5f)
        px = nx
        py = ny
    }
    path.lineTo(px, bottomAt(samples))
    for (index in samples - 1 downTo 0) {
        val nx = xAt(index)
        val ny = bottomAt(index)
        path.quadraticBezierTo(px, py, (px + nx) * 0.5f, (py + ny) * 0.5f)
        px = nx
        py = ny
    }
    path.close()
}

/**
 * The drips over the bar's face.
 *
 * They are short on purpose: the bar's icon row starts about seventeen dp below the
 * capsule's top edge, and a drip that reached it would read as a defect on the
 * control. The length follows the drift directly above the drip, so the bar's snow
 * answers the heap on the floor rather than animating on its own schedule.
 */
private fun DrawScope.drawCapDrips(
    left: Float,
    right: Float,
    top: Float,
    baseLip: Float,
    lipSpan: Float,
    pileDepth: (Float) -> Float,
    ink: TrailInk,
    seconds: Float
) {
    val width = right - left
    val base = baseLip + lipSpan * 0.35f
    for (index in DRIP_FRACTIONS.indices) {
        val fraction = DRIP_FRACTIONS[index]
        val level = pileDepth(fraction)
        if (level < 0.10f) continue
        val breathe = 0.85f + 0.15f * sin(seconds * 0.7f + index * 1.9f)
        val length = (1.5.dp.toPx() + level * 5.2.dp.toPx()) * breathe
        val x = left + width * fraction
        val startY = top + base * (0.72f + 0.28f * sin(fraction * PI.toFloat()))
        val endY = startY + length
        drawLine(
            color = ink.rim.copy(alpha = 0.72f),
            start = Offset(x, startY),
            end = Offset(x, endY),
            strokeWidth = 1.9.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = ink.fill.copy(alpha = 0.92f),
            radius = 1.25.dp.toPx(),
            center = Offset(x, endY)
        )
    }
}

/** A pinch of light on the cap, so the surface has grain rather than a flat fill. */
private fun DrawScope.drawCapSparkle(
    left: Float,
    right: Float,
    top: Float,
    lift: Float,
    pileDepth: (Float) -> Float,
    ink: TrailInk,
    seconds: Float
) {
    val width = right - left
    for (index in 0 until 6) {
        val fraction = 0.10f + index * 0.16f
        val level = pileDepth(fraction)
        val twinkle = 0.45f + 0.55f * sin(seconds * 1.5f + index * 1.27f)
        if (twinkle < 0.62f) continue
        drawCircle(
            color = ink.rim.copy(alpha = 0.34f * twinkle * (0.4f + level)),
            radius = 0.85.dp.toPx(),
            center = Offset(
                left + width * fraction,
                top - lift * 0.35f - level * 1.2.dp.toPx()
            )
        )
    }
}
