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
import com.anisequel.domain.seasonal.isWinterSnow
import kotlin.math.abs
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

private val SNOW_INK = PileInk(
    fill = Color(0xFFDCEBF8),
    rim = Color(0xFFFBFDFF),
    shadow = Color(0xFFA8C8E2)
)
private val CRYSTAL_INK = PileInk(
    fill = Color(0xFFD2E8F8),
    rim = Color(0xFFF7FCFF),
    shadow = Color(0xFF8FB6D4)
)
private val NEEDLE_INK = PileInk(
    fill = Color(0xFFD8ECF6),
    rim = Color(0xFFF4FAFF),
    shadow = Color(0xFF9CBFD8)
)
private val SAKURA_INK = PileInk(
    fill = Color(0xFFEFB4C9),
    rim = Color(0xFFFFDDE9),
    shadow = Color(0xFFC77E99)
)
private val CLOVER_INK = PileInk(
    fill = Color(0xFF8FCB90),
    rim = Color(0xFFD8F0CB),
    shadow = Color(0xFF4F8A55)
)
private val MAPLE_INK = PileInk(
    fill = Color(0xFFD18446),
    rim = Color(0xFFF2BE7B),
    shadow = Color(0xFF93552A)
)
private val OAK_INK = PileInk(
    fill = Color(0xFFC29645),
    rim = Color(0xFFEBCE8A),
    shadow = Color(0xFF84622A)
)
private val GINKGO_INK = PileInk(
    fill = Color(0xFFDBB743),
    rim = Color(0xFFF6E59B),
    shadow = Color(0xFF9E7C21)
)
private val DANDELION_INK = PileInk(
    fill = Color(0xFFE7DCC0),
    rim = Color(0xFFFAF4E2),
    shadow = Color(0xFFA28A62)
)

/**
 * The ink for a drift, built once per shape.
 *
 * These used to be constructed inside the per-frame draw, which is eight small
 * objects a second at worst - not a leak, but pure garbage from a draw callback that
 * has no reason to allocate anything. `Color` is an inline value class, so the cache
 * itself is nothing but the handful of `PileInk` objects below.
 */
private fun pileInk(effect: SeasonalEffect): PileInk = when (effect) {
    SeasonalEffect.SNOW, SeasonalEffect.POWDER -> SNOW_INK
    SeasonalEffect.CRYSTAL -> CRYSTAL_INK
    SeasonalEffect.NEEDLE -> NEEDLE_INK
    SeasonalEffect.SAKURA -> SAKURA_INK
    SeasonalEffect.CLOVER -> CLOVER_INK
    SeasonalEffect.MAPLE -> MAPLE_INK
    SeasonalEffect.OAK -> OAK_INK
    SeasonalEffect.GINKGO -> GINKGO_INK
    // The seed is the one shape that does not settle, so this is only reached if a
    // caller ignores SeasonalEffect.settles. Kept rather than left out because the
    // drawing reads a colour for every shape, and a drift of pale seeds is what it
    // would look like if that ever happened.
    SeasonalEffect.DANDELION -> DANDELION_INK
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
    path: Path,
    glyphPath: Path,
    glyphStep: Int = 3,
    highDetail: Boolean = true,
    seconds: Float = 0f
) {
    if (maxHeightPx <= 0.5f || alpha <= 0.01f) return
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val wind = shear.coerceIn(-1f, 1f)
    val step = glyphStep.coerceAtLeast(1)
    // The drift sits on the screen's own bottom edge, behind the floating bar. It used
    // to be lifted to the bar's top edge, which left a transparent gap the height of the
    // bar's own padding between the snow and the screen - and made a drift that should
    // look like it gathered on the floor look like a shelf. Nothing is drawn on the
    // bar itself: the snow that used to cap the pill covered the control's icons at
    // a deep drift, and the reader asked the weather to stay off the chrome.
    val frame = PileFrame(
        width = width,
        ground = height,
        maxHeightPx = maxHeightPx,
        shear = wind,
        alpha = alpha,
        samplingShift = wind * 0.07f,
        columns = pile.columns
    )
    val ink = pileInk(effect)
    val glyphRadius = pileGlyphRadius(effect, maxHeightPx)

    buildPileSilhouette(pile, frame, path)
    drawPileBody(path, ink, frame)
    drawRestingGlyphs(effect, pile, frame, glyphPath, glyphRadius, step, highDetail)
    if (effect.isWinterSnow && highDetail) {
        drawPileSparkle(pile, ink, frame, step + 1, seconds)
    }
    if (abs(frame.shear) > 0.18f) {
        drawBlownGrains(pile, ink, frame, glyphRadius)
    }
    drawPileGroundLine(ink, frame)
}

/**
 * One frame of the drift: the numbers every part of it asks about.
 *
 * Passed down rather than recomputed because the silhouette, the resting glyphs,
 * the sparkle and the blown grains all measure from the same ground line and the
 * same shear. [surfaceAt] is the one place the pile is sampled, so the grain that
 * makes the surface look loose - rather than extruded - lives with the sample.
 */
private class PileFrame(
    val width: Float,
    val ground: Float,
    val maxHeightPx: Float,
    val shear: Float,
    val alpha: Float,
    val samplingShift: Float,
    val columns: Int
) {
    /** The drift's normalised height at [index], with a slow settle worked into it. */
    fun surfaceAt(pile: SeasonalPile, index: Int): Float {
        val fraction = (index.toFloat() / columns + samplingShift).coerceIn(0f, 1f)
        // Two long swells, not one high-frequency jitter. The old `sin(index * 1.7)`
        // completed a cycle every ~two columns, which at 48 columns is a 40-pixel zigzag
        // along the crest - a bank drawn with a comb. These wavelengths are 20 and 8
        // columns, so the surface undulates instead of buzzing.
        val grain = sin(index * 0.31f + 0.7f) * 0.012f + sin(index * 0.77f + 2.1f) * 0.006f
        return (pile.heightAt(fraction) + grain).coerceIn(0f, 1f)
    }

    fun xAt(index: Int): Float = width * index / columns

    fun heightY(level: Float): Float = ground - level * maxHeightPx
}

/** The resting glyph's size: snow grains are smaller than leaves, and both bounded. */
private fun pileGlyphRadius(effect: SeasonalEffect, maxHeightPx: Float): Float {
    val winter = effect.isWinterSnow
    val ceiling = if (winter) 6.8f else 9.2f
    return (maxHeightPx * if (winter) 0.048f else 0.072f).coerceIn(2.4f, ceiling)
}

/**
 * The crest as a smooth curve: each column becomes a control point and each
 * quadratic lands on the midpoint of the pair around it.
 *
 * That is the difference between a snow bank and a bar chart, and it costs no path
 * stroke per sample.
 */
private fun DrawScope.buildPileSilhouette(pile: SeasonalPile, frame: PileFrame, path: Path) {
    path.reset()
    path.moveTo(-2f, frame.ground)
    var previousX = 0f
    var previousY = frame.heightY(frame.surfaceAt(pile, 0))
    path.lineTo(previousX, previousY)
    for (index in 1..frame.columns) {
        val x = frame.xAt(index)
        val y = frame.heightY(frame.surfaceAt(pile, index))
        path.quadraticBezierTo(previousX, previousY, (previousX + x) / 2f, (previousY + y) / 2f)
        previousX = x
        previousY = y
    }
    path.lineTo(previousX, previousY)
    path.lineTo(frame.width + 2f, frame.ground)
    path.close()
}

/**
 * The fills and the lit edge.
 *
 * A second, slightly darker fill clipped to the lower half of the heap is what makes
 * snow look banked rather than like a sticker of one colour, and the rim is clipped
 * to above the ground so the closed path's base line is not drawn across the bar or
 * the bottom of the screen as a seam.
 */
private fun DrawScope.drawPileBody(path: Path, ink: PileInk, frame: PileFrame) {
    val alpha = frame.alpha
    drawPath(path, ink.fill.copy(alpha = alpha * 0.96f))
    // One shadow band, not two: the second pass was a full re-tessellation of the
    // silhouette for a difference the eye cannot separate at this depth, and the rim
    // stroke below already gives the crest its edge.
    clipRect(top = frame.ground - frame.maxHeightPx * 0.55f) {
        drawPath(path, ink.shadow.copy(alpha = alpha * 0.16f))
    }
    clipRect(top = 0f, bottom = frame.ground - 1f) {
        drawPath(
            path = path,
            color = ink.rim.copy(alpha = alpha * 0.84f),
            style = Stroke(width = 1.4f + frame.maxHeightPx * 0.016f)
        )
    }
}

/**
 * Shapes lying on the crest, drawn with the same code as the shapes falling past it.
 *
 * Sampled every [step] columns rather than one per column: at 48 columns that would be
 * a carpet, and the silhouette would disappear under it. The second pass, at half a
 * step, sinks glyphs into the surface so the heap has a body rather than a top line.
 */
private fun DrawScope.drawRestingGlyphs(
    effect: SeasonalEffect,
    pile: SeasonalPile,
    frame: PileFrame,
    glyphPath: Path,
    glyphRadius: Float,
    step: Int,
    highDetail: Boolean
) {
    val buried = if (highDetail) 1 else 0
    for (pass in 0..buried) {
        val buriedPass = pass == 1
        // The buried pass is half as dense as the surface one. It exists to give the
        // heap a body, not a second carpet of shapes; at the old shared stride it drew
        // 36 full glyph paths per frame on the busiest sky.
        val stride = if (buriedPass) step * 2 else step
        val start = if (buriedPass) step / 2 else 0
        for (index in start until frame.columns step stride) {
            val level = frame.surfaceAt(pile, index)
            if (level < 0.08f) continue
            val jitter = sin(index * 1.13f + pass * 4.2f) * (frame.width / frame.columns) * 0.35f
            val sink = if (buriedPass) {
                glyphRadius * (0.55f + level * 0.4f)
            } else {
                glyphRadius * 0.18f
            }
            val radius = glyphRadius * (0.72f + level * 0.55f) * if (buriedPass) 0.82f else 1f
            drawSeasonalParticle(
                effect = effect,
                center = Offset(
                    frame.xAt(index) + jitter,
                    frame.heightY(level) + sink - radius * 0.18f
                ),
                radius = radius,
                rotationDegrees = sin(index * 2.31f + pass) * 48f + frame.shear * 22f,
                alpha = frame.alpha * (if (buriedPass) 0.32f else 0.58f) + level * 0.28f,
                variant = (index * 31 + effect.ordinal * 7 + pass * 13) % effect.variantCount,
                path = glyphPath,
                highDetail = highDetail
            )
        }
    }
}

/** Flakes of light on the crest, a little apart from the resting glyphs themselves. */
private fun DrawScope.drawPileSparkle(
    pile: SeasonalPile,
    ink: PileInk,
    frame: PileFrame,
    sparkleStride: Int,
    seconds: Float
) {
    for (index in 0 until frame.columns step sparkleStride) {
        val level = frame.surfaceAt(pile, index)
        if (level < 0.16f) continue
        val sparkle = 0.45f + 0.55f * sin(seconds * 2.1f + index * 0.7f)
        if (sparkle < 0.72f) continue
        drawCircle(
            color = ink.rim.copy(alpha = frame.alpha * 0.22f * sparkle),
            radius = 1.15f,
            center = Offset(frame.xAt(index), frame.heightY(level) - 1.6f)
        )
    }
}

/**
 * Grains lifted off the crest when the wind is up.
 *
 * Drawn rather than simulated: a resting flake that visibly takes off would need a
 * second particle state, and the pile only has to look alive.
 */
private fun DrawScope.drawBlownGrains(
    pile: SeasonalPile,
    ink: PileInk,
    frame: PileFrame,
    glyphRadius: Float
) {
    val lift = (abs(frame.shear) - 0.18f) / 0.82f
    for (index in 0 until 4) {
        val fraction = (index + 0.5f) / 4f
        val level = frame.surfaceAt(pile, (fraction * (frame.columns - 1)).toInt())
        drawCircle(
            color = ink.rim.copy(alpha = frame.alpha * 0.34f * lift),
            radius = (glyphRadius * 0.24f).coerceAtLeast(0.6f),
            center = Offset(
                frame.width * fraction + frame.shear * 8f,
                frame.heightY(level) - glyphRadius * 1.4f - index * 2.4f
            )
        )
    }
}

/** A faint line of colour along the very bottom, so the drift reads as thick where it
 * meets the edge of the screen even on a very light theme. */
private fun DrawScope.drawPileGroundLine(ink: PileInk, frame: PileFrame) {
    drawLine(
        color = ink.shadow.copy(alpha = frame.alpha * 0.22f),
        start = Offset(0f, frame.ground - 1f),
        end = Offset(frame.width, frame.ground - 1f),
        strokeWidth = 1.2f,
        cap = StrokeCap.Round
    )
}
