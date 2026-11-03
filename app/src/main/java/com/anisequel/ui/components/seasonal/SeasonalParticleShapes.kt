package com.anisequel.ui.components.seasonal

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.anisequel.domain.seasonal.SeasonalEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The shapes themselves: the dispatcher, the chip glyph, snow, sakura and clover.
 *
 * ## Why this file exists separately from the layer
 *
 * The layer's job is time: one clock, one placement function, one draw loop. The
 * shapes' job is ink. They were in the same file, and the layer had become the
 * place where both a frame budget and a maple leaf were reviewed. Splitting them
 * also makes the Settings glyph and the falling particle literally the same
 * drawing code rather than two illustrations that can drift apart.
 *
 * The palettes live in `SeasonalParticlePalettes.kt`, and the autumn leaves and
 * the light shapes in `SeasonalLeafShapes.kt`. This file is the dispatcher, the
 * glyph, and the three shapes that are drawn with strokes and petals rather than
 * a single leaf path.
 */

/**
 * One shape, at one point, drawn with [path] as scratch space.
 *
 * Shared by the overlay, the pile and the Settings chips so the glyph on a shape
 * chip is the thing that will fall past the reader - not a separate illustration
 * that can drift from it.
 *
 * [variant] selects which of the shape's drawings this is; losing an out-of-range
 * value lands on a palette variant rather than throwing, because the variants
 * exist to be pretty, not to be a contract a restored particle has to satisfy.
 */
internal fun DrawScope.drawSeasonalParticle(
    effect: SeasonalEffect,
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    alpha: Float,
    variant: Int,
    path: Path,
    twinkle: Float = 1f
) {
    if (radius <= 0.2f || alpha <= 0.001f) return
    val palette = particlePalette(effect, variant)
    when (effect) {
        SeasonalEffect.SNOW ->
            withTransform({ rotate(rotationDegrees, center) }) {
                drawSnowflake(center, radius, palette, alpha, variant, path)
            }
        SeasonalEffect.SAKURA ->
            withTransform({ rotate(rotationDegrees, center) }) {
                drawSakura(path, center, radius, palette, alpha, variant)
            }
        SeasonalEffect.CLOVER ->
            withTransform({ rotate(rotationDegrees, center) }) {
                drawClover(path, center, radius, palette, alpha, variant)
            }
        SeasonalEffect.MAPLE ->
            withTransform({ rotate(rotationDegrees, center) }) {
                drawMaple(path, center, radius, palette, alpha)
            }
        SeasonalEffect.OAK ->
            withTransform({ rotate(rotationDegrees, center) }) {
                drawOak(path, center, radius, palette, alpha, variant)
            }
        SeasonalEffect.GINKGO ->
            withTransform({ rotate(rotationDegrees, center) }) {
                drawGinkgo(path, center, radius, palette, alpha, variant)
            }
        SeasonalEffect.FIREFLY -> drawFirefly(center, radius, palette, alpha, twinkle)
        SeasonalEffect.DANDELION ->
            withTransform({ rotate(rotationDegrees * 0.6f, center) }) {
                drawDandelion(center, radius, palette, alpha, variant)
            }
        SeasonalEffect.STAR ->
            withTransform({ rotate(rotationDegrees * 0.4f, center) }) {
                drawStar(path, center, radius, palette, alpha, variant, twinkle)
            }
    }
}

/** A chip-sized sample of one shape, used instead of emoji on the shape chips. */
@Composable
fun SeasonalEffectGlyph(effect: SeasonalEffect, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawSeasonalParticle(
            effect = effect,
            center = center,
            radius = size.minDimension * 0.32f,
            rotationDegrees = 0f,
            alpha = 1f,
            variant = 0,
            path = Path()
        )
    }
}

// ---------------------------------------------------------------------------
// Snow
// ---------------------------------------------------------------------------

private fun DrawScope.drawSnowflake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int,
    path: Path
) {
    when (variant % 3) {
        1 -> drawFernFlake(center, radius, palette, alpha)
        2 -> drawPlateFlake(center, radius, palette, alpha, path)
        else -> drawStellarFlake(center, radius, palette, alpha)
    }
}

/**
 * The classic dendrite: six arms, two branch pairs and a dot at each tip.
 *
 * Every arm is drawn twice - a wide translucent pass under a crisp one. That is
 * a soft edge without a `BlurMaskFilter` or a gradient per flake, both of which
 * would allocate inside a loop that runs dozens of times per frame.
 */
private fun DrawScope.drawStellarFlake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float
) {
    val stroke = (radius * 0.13f).coerceAtLeast(0.55f)
    val body = palette.base.copy(alpha = alpha)
    val soft = palette.light.copy(alpha = alpha * 0.16f)
    val vein = palette.deep.copy(alpha = alpha * 0.55f)
    // Branches and tip dots are invisible below a few pixels and cost four
    // draw calls an arm. A tiny flake is six arms and a bright centre, which is
    // what the previous version drew at every size.
    val detailed = radius >= 5f

    for (arm in 0 until 6) {
        val angle = arm * (PI.toFloat() / 3f)
        val dx = cos(angle)
        val dy = sin(angle)
        val tip = Offset(center.x + dx * radius, center.y + dy * radius)
        drawLine(soft, center, tip, strokeWidth = stroke * 2.6f, cap = StrokeCap.Round)
        drawLine(body, center, tip, strokeWidth = stroke, cap = StrokeCap.Round)

        if (!detailed) continue
        // Two branch pairs, shorter as they approach the tip. Drawn from the arm
        // rather than from the centre so the fork sits on the line, not beside it.
        for (pair in 0 until 2) {
            val jointAt = 0.54f + pair * 0.22f
            val branchLength = radius * (0.30f - pair * 0.09f)
            val joint = Offset(center.x + dx * radius * jointAt, center.y + dy * radius * jointAt)
            for (side in -1..1 step 2) {
                val branchAngle = angle + side * (0.62f - pair * 0.10f)
                drawLine(
                    color = if (pair == 0) body else vein,
                    start = joint,
                    end = Offset(
                        joint.x + cos(branchAngle) * branchLength,
                        joint.y + sin(branchAngle) * branchLength
                    ),
                    strokeWidth = stroke * (0.72f - pair * 0.12f),
                    cap = StrokeCap.Round
                )
            }
        }
        drawCircle(palette.light.copy(alpha = alpha * 0.9f), radius * 0.045f, tip)
    }
    drawCircle(palette.light.copy(alpha = alpha), radius * 0.10f, center)
    drawCircle(palette.deep.copy(alpha = alpha * 0.45f), radius * 0.05f, center)
}

/** A fern: three branch pairs per arm, the longest near the base. */
private fun DrawScope.drawFernFlake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float
) {
    val stroke = (radius * 0.10f).coerceAtLeast(0.5f)
    val body = palette.base.copy(alpha = alpha)
    val soft = palette.light.copy(alpha = alpha * 0.14f)
    val vein = palette.deep.copy(alpha = alpha * 0.62f)
    val detailed = radius >= 5f

    for (arm in 0 until 6) {
        val angle = arm * (PI.toFloat() / 3f)
        val dx = cos(angle)
        val dy = sin(angle)
        val tip = Offset(center.x + dx * radius, center.y + dy * radius)
        drawLine(soft, center, tip, strokeWidth = stroke * 3f, cap = StrokeCap.Round)
        drawLine(body, center, tip, strokeWidth = stroke, cap = StrokeCap.Round)
        if (!detailed) continue
        for (pair in 0 until 3) {
            val jointAt = 0.30f + pair * 0.21f
            val branchLength = radius * (0.34f - pair * 0.07f)
            val joint = Offset(center.x + dx * radius * jointAt, center.y + dy * radius * jointAt)
            for (side in -1..1 step 2) {
                val branchAngle = angle + side * 0.78f
                drawLine(
                    color = if (pair == 0) body else vein,
                    start = joint,
                    end = Offset(
                        joint.x + cos(branchAngle) * branchLength,
                        joint.y + sin(branchAngle) * branchLength
                    ),
                    strokeWidth = stroke * (0.85f - pair * 0.14f),
                    cap = StrokeCap.Round
                )
            }
        }
    }
    drawCircle(palette.light.copy(alpha = alpha), radius * 0.09f, center)
}

/** A plate: two nested hexagons with short spokes, the flattest snow there is. */
private fun DrawScope.drawPlateFlake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    path: Path
) {
    drawCircle(palette.light.copy(alpha = alpha * 0.075f), radius * 1.2f, center)
    val stroke = (radius * 0.09f).coerceAtLeast(0.5f)
    buildHexagon(path, center, radius * 0.82f)
    drawPath(path = path, color = palette.base.copy(alpha = alpha), style = Stroke(stroke))
    buildHexagon(path, center, radius * 0.34f)
    drawPath(
        path = path,
        color = palette.deep.copy(alpha = alpha * 0.65f),
        style = Stroke(stroke)
    )
    for (arm in 0 until 6) {
        val angle = arm * (PI.toFloat() / 3f) + (PI.toFloat() / 6f)
        val tip = Offset(
            center.x + cos(angle) * radius * 0.82f,
            center.y + sin(angle) * radius * 0.82f
        )
        drawLine(
            color = palette.base.copy(alpha = alpha * 0.7f),
            start = center,
            end = tip,
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawCircle(palette.light.copy(alpha = alpha * 0.85f), radius * 0.05f, tip)
    }
    drawCircle(palette.light.copy(alpha = alpha), radius * 0.08f, center)
}

private fun buildHexagon(path: Path, center: Offset, radius: Float) {
    path.reset()
    for (corner in 0 until 6) {
        val angle = corner * (PI.toFloat() / 3f) + (PI.toFloat() / 6f)
        val x = center.x + cos(angle) * radius
        val y = center.y + sin(angle) * radius
        if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

// ---------------------------------------------------------------------------
// Sakura
// ---------------------------------------------------------------------------

/**
 * A notched petal with a lit inner face and a single vein.
 *
 * [variant] changes the petal's width, which is the cheapest way to make a
 * blossom look hand-picked rather than stamped: a wide petal and a narrow one
 * side by side read as two flowers, while two rotated copies of one outline read
 * as a repeating pattern.
 */
private fun DrawScope.drawSakura(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int
) {
    val width = when (variant % 3) {
        1 -> 0.78f
        2 -> 1.14f
        else -> 1f
    }
    withTransform({ scale(width, 1f, center) }) {
        buildPetal(path, center, radius)
        drawPath(path, palette.base.copy(alpha = alpha))

        // The inner face sits low on the petal, toward the stem, which is where a
        // petal curls toward the light. Drawing it as a second, smaller petal is
        // what gives the flat outline a front and a back.
        buildPetal(path, Offset(center.x, center.y + radius * 0.10f), radius * 0.58f)
        drawPath(path, palette.light.copy(alpha = alpha * 0.42f))

        val vein = palette.deep.copy(alpha = alpha * 0.42f)
        drawLine(
            color = vein,
            start = Offset(center.x, center.y + radius * 0.82f),
            end = Offset(center.x, center.y - radius * 0.44f),
            strokeWidth = (radius * 0.055f).coerceAtLeast(0.4f),
            cap = StrokeCap.Round
        )
        if (radius > 5f) {
            for (side in -1..1 step 2) {
                val branchStart = Offset(center.x, center.y + radius * 0.05f)
                val branchEnd = Offset(
                    center.x + side * radius * 0.34f,
                    center.y - radius * 0.30f
                )
                drawLine(
                    color = vein.copy(alpha = alpha * 0.28f),
                    start = branchStart,
                    end = branchEnd,
                    strokeWidth = (radius * 0.035f).coerceAtLeast(0.35f),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun buildPetal(path: Path, center: Offset, radius: Float) {
    path.reset()
    path.moveTo(center.x, center.y + radius)
    path.cubicTo(
        center.x - radius * 1.04f, center.y + radius * 0.34f,
        center.x - radius * 0.90f, center.y - radius * 0.56f,
        center.x - radius * 0.34f, center.y - radius * 0.88f
    )
    // The notch at the tip is what says "petal" rather than "leaf".
    path.quadraticBezierTo(
        center.x - radius * 0.10f, center.y - radius * 1.02f,
        center.x, center.y - radius * 0.55f
    )
    path.quadraticBezierTo(
        center.x + radius * 0.10f, center.y - radius * 1.02f,
        center.x + radius * 0.34f, center.y - radius * 0.88f
    )
    path.cubicTo(
        center.x + radius * 0.90f, center.y - radius * 0.56f,
        center.x + radius * 1.04f, center.y + radius * 0.34f,
        center.x, center.y + radius
    )
    path.close()
}

// ---------------------------------------------------------------------------
// Clover
// ---------------------------------------------------------------------------

/**
 * Four heart-shaped leaves on a stem, with a lit rim and a bright pith.
 *
 * The heart is built once and drawn four times around the centre. Building it
 * four times with rotated coordinates would be four times the path work for an
 * identical outline; rotating the canvas moves the same path, and the leaves
 * still point inward because the heart's tip is at its bottom.
 */
private fun DrawScope.drawClover(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int
) {
    val stem = palette.deep.copy(alpha = alpha * 0.85f)
    drawLine(
        color = stem,
        start = Offset(center.x, center.y + radius * 0.18f),
        end = Offset(center.x, center.y + radius * 1.12f),
        strokeWidth = (radius * 0.14f).coerceAtLeast(0.5f),
        cap = StrokeCap.Round
    )

    val leaf = radius * if (variant % 2 == 0) 0.54f else 0.50f
    buildCloverHeart(path, leaf)
    val body = palette.base.copy(alpha = alpha)
    val rim = palette.light.copy(alpha = alpha * 0.34f)
    for (index in 0 until 4) {
        withTransform({
            rotate(index * 90f, center)
            translate(center.x, center.y - radius * 0.40f)
        }) {
            drawPath(path, body)
            if (leaf > 3.2f) {
                drawPath(path, rim, style = Stroke(width = (leaf * 0.06f).coerceAtLeast(0.35f)))
            }
        }
    }
    drawCircle(palette.light.copy(alpha = alpha * 0.95f), radius * 0.13f, center)
    drawCircle(palette.deep.copy(alpha = alpha * 0.7f), radius * 0.055f, center)
}

/** A heart whose tip is at the bottom, authored around the origin. */
private fun buildCloverHeart(path: Path, size: Float) {
    path.reset()
    path.moveTo(0f, size * 0.95f)
    path.cubicTo(
        -size * 1.15f, size * 0.18f,
        -size * 0.92f, -size * 0.86f,
        -size * 0.32f, -size * 0.86f
    )
    path.quadraticBezierTo(0f, -size * 0.86f, 0f, -size * 0.30f)
    path.quadraticBezierTo(0f, -size * 0.86f, size * 0.32f, -size * 0.86f)
    path.cubicTo(
        size * 0.92f, -size * 0.86f,
        size * 1.15f, size * 0.18f,
        0f, size * 0.95f
    )
    path.close()
}
