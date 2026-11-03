package com.anisequel.ui.components.seasonal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.SeasonalPile
import kotlin.math.sin

/**
 * The ink the drift is drawn with.
 *
 * Separate from [ParticlePalette] because a pile is not a bigger particle: a leaf
 * lying in a heap is lit from above and shadowed underneath, while a falling leaf
 * is a flat silhouette. The three tones here are the whole surface, the lit top
 * edge and the dark grain along the bottom.
 */
private class PileInk(val fill: Color, val rim: Color, val shadow: Color)

private fun pileInk(effect: SeasonalEffect): PileInk = when (effect) {
    SeasonalEffect.SNOW -> PileInk(
        fill = Color(0xFFDCEBF8),
        rim = Color(0xFFFBFDFF),
        shadow = Color(0xFFA8C8E2)
    )
    SeasonalEffect.SAKURA -> PileInk(
        fill = Color(0xFFEFB4C9),
        rim = Color(0xFFFFDDE9),
        shadow = Color(0xFFC77E99)
    )
    SeasonalEffect.CLOVER -> PileInk(
        fill = Color(0xFF8FCB90),
        rim = Color(0xFFD8F0CB),
        shadow = Color(0xFF4F8A55)
    )
    SeasonalEffect.MAPLE -> PileInk(
        fill = Color(0xFFD18446),
        rim = Color(0xFFF2BE7B),
        shadow = Color(0xFF93552A)
    )
    SeasonalEffect.OAK -> PileInk(
        fill = Color(0xFFC29645),
        rim = Color(0xFFEBCE8A),
        shadow = Color(0xFF84622A)
    )
    SeasonalEffect.GINKGO -> PileInk(
        fill = Color(0xFFDBB743),
        rim = Color(0xFFF6E59B),
        shadow = Color(0xFF9E7C21)
    )
    // Nothing else settles, so these are only reached if a caller ignores
    // SeasonalEffect.settles. Neutral rather than absent keeps the drawing total.
    SeasonalEffect.FIREFLY, SeasonalEffect.DANDELION, SeasonalEffect.STAR -> PileInk(
        fill = Color(0xFFD9D9D9),
        rim = Color(0xFFF5F5F5),
        shadow = Color(0xFF9A9A9A)
    )
}

/**
 * A drift of settled particles along the bottom edge.
 *
 * ## The shape
 *
 * The top is a quadratic-smoothed sample of the pile's columns, not a staircase:
 * each segment ends at the midpoint of the next pair, with the column itself as
 * the control point. That is the difference between a snow bank and a graph, and
 * it costs no path stroke per sample.
 *
 * [shear] is the wind, -1..1. Sampling the field through a horizontal offset
 * makes the whole bank slide downwind with the sky instead of staying pinned to
 * the glass - a pile that ignores the gust reads as a sticker. The offset is a
 * small fraction of the width so the drift never looks detached from its ground.
 *
 * ## Why the resting shapes are drawn from the same glyph code
 *
 * A drift of flat colour reads as a hill. A few leaves or flakes lying on the
 * surface, in the active shape's own drawing and its other variants, is what
 * says the pile is made of the things that have been falling. They are sampled
 * every third column, not one per column: at 48 columns that would be a carpet
 * of shapes and the silhouette would disappear under them, and each resting
 * glyph is a full path build per frame.
 */
internal fun DrawScope.drawSeasonalPile(
    effect: SeasonalEffect,
    pile: SeasonalPile,
    maxHeightPx: Float,
    shear: Float,
    alpha: Float,
    /** Where the chrome starts, measured from the bottom of the layer, in px. */
    bottomInsetPx: Float,
    path: Path,
    glyphPath: Path
) {
    if (maxHeightPx <= 0.5f || alpha <= 0.01f) return
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    // The drift sits on the top edge of the floating bar when there is one, and
    // on the screen's own bottom edge when there is not. Clamped so a bar taller
    // than the layer (a short landscape window) cannot put the ground offscreen.
    val ground = (height - bottomInsetPx).coerceIn(1f, height)
    val ink = pileInk(effect)
    val columns = pile.columns
    val samplingShift = shear.coerceIn(-1f, 1f) * 0.07f

    // A slow settle in the silhouette makes the surface look loose without
    // re-depositing anything: this is grain, not the pile growing and shrinking.
    fun surfaceHeight(index: Int): Float {
        val fraction = (index.toFloat() / columns + samplingShift).coerceIn(0f, 1f)
        val grain = sin(index * 1.7f) * 0.012f
        return (pile.heightAt(fraction) + grain).coerceIn(0f, 1f)
    }

    path.reset()
    path.moveTo(-2f, ground)
    var previousX = 0f
    var previousY = ground - surfaceHeight(0) * maxHeightPx
    path.lineTo(previousX, previousY)
    for (index in 1..columns) {
        val x = width * index / columns
        val y = ground - surfaceHeight(index) * maxHeightPx
        val midX = (previousX + x) / 2f
        val midY = (previousY + y) / 2f
        path.quadraticBezierTo(previousX, previousY, midX, midY)
        previousX = x
        previousY = y
    }
    path.lineTo(previousX, previousY)
    path.lineTo(width + 2f, ground)
    path.close()

    drawPath(path, ink.fill.copy(alpha = alpha * 0.94f))
    // The lit top edge. Clipped to above the ground so the closed path's base line
    // is not drawn across the bar or the bottom of the screen as a seam.
    clipRect(top = 0f, bottom = ground - 1f) {
        drawPath(
            path = path,
            color = ink.rim.copy(alpha = alpha * 0.75f),
            style = Stroke(width = (1.1f + maxHeightPx * 0.012f))
        )
    }
    // A darker band under the surface gives the heap enough weight to sit on the
    // screen rather than float over it.
    clipRect(top = ground - maxHeightPx * 0.18f) {
        drawPath(path, ink.shadow.copy(alpha = alpha * 0.10f))
    }

    val glyphRadius = (maxHeightPx * 0.055f).coerceIn(2.2f, 7.5f)
    for (index in 0 until columns step 3) {
        val level = surfaceHeight(index)
        if (level < 0.10f) continue
        val fraction = index.toFloat() / columns
        val x = width * fraction
        val y = ground - level * maxHeightPx
        val variant = (index * 31 + effect.ordinal * 7) % effect.variantCount
        val rotation = sin(index * 2.31f) * 38f + shear * 26f
        val radius = glyphRadius * (0.78f + level * 0.5f)
        drawSeasonalParticle(
            effect = effect,
            center = Offset(x, y - radius * 0.25f),
            radius = radius,
            rotationDegrees = rotation,
            alpha = alpha * (0.48f + level * 0.35f),
            variant = variant,
            path = glyphPath
        )
    }

    // Two or three grains lifted off the crest when the wind is up. They are
    // drawn rather than simulated: a resting flake that visibly takes off would
    // need a second particle state, and the pile only has to *look* alive.
    if (kotlin.math.abs(shear) > 0.18f) {
        val lift = (kotlin.math.abs(shear) - 0.18f) / 0.82f
        for (index in 0 until 4) {
            val fraction = (index + 0.5f) / 4f
            val column = (fraction * (columns - 1)).toInt()
            val x = width * fraction
            val y = ground - surfaceHeight(column) * maxHeightPx - glyphRadius * 1.4f
            drawCircle(
                color = ink.rim.copy(alpha = alpha * 0.34f * lift),
                radius = (glyphRadius * 0.24f).coerceAtLeast(0.6f),
                center = Offset(x + shear * 8f, y - index * 2.4f)
            )
        }
    }

    // A faint line of colour along the very bottom, so the drift reads as thick
    // where it meets the edge of the screen even on a very light theme.
    drawLine(
        color = ink.shadow.copy(alpha = alpha * 0.22f),
        start = Offset(0f, ground - 1f),
        end = Offset(width, ground - 1f),
        strokeWidth = 1.2f,
        cap = StrokeCap.Round
    )
}
