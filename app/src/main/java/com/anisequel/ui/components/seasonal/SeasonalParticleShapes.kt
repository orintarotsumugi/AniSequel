package com.anisequel.ui.components.seasonal

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * The palettes live in `SeasonalParticlePalettes.kt`, and the autumn leaves and the
 * dandelion seed in `SeasonalLeafShapes.kt`. This file is the dispatcher, the glyph,
 * and the shapes drawn with strokes and petals rather than a single leaf path.
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
 *
 * [flatten] is the shape's own horizontal squeeze: what [tumble] turns a rotation
 * into a roll-over. One is the shape as drawn, less than one is turning edge-on, and
 * negative is the other face coming back round.
 */
internal fun DrawScope.drawSeasonalParticle(
    effect: SeasonalEffect,
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    alpha: Float,
    variant: Int,
    path: Path,
    highDetail: Boolean = true,
    flatten: Float = 1f
) {
    if (radius <= 0.2f || alpha <= 0.001f) return
    val palette = particlePalette(effect, variant)
    when (effect) {
        SeasonalEffect.SNOW, SeasonalEffect.POWDER, SeasonalEffect.CRYSTAL, SeasonalEffect.NEEDLE ->
            tumble(rotationDegrees, center, flatten) {
                drawSnowflake(center, radius, palette, alpha, variant, path, effect, highDetail)
            }
        SeasonalEffect.SAKURA -> tumble(rotationDegrees, center, flatten) {
            drawSakura(path, center, radius, palette, alpha, variant, highDetail)
        }
        SeasonalEffect.CLOVER -> tumble(rotationDegrees, center, flatten) {
            drawClover(path, center, radius, palette, alpha, variant)
        }
        SeasonalEffect.MAPLE -> tumble(rotationDegrees, center, flatten) {
            drawMaple(path, center, radius, palette, alpha, variant, highDetail)
        }
        SeasonalEffect.OAK -> tumble(rotationDegrees, center, flatten) {
            drawOak(path, center, radius, palette, alpha, variant, highDetail)
        }
        SeasonalEffect.GINKGO -> tumble(rotationDegrees, center, flatten) {
            drawGinkgo(path, center, radius, palette, alpha, variant)
        }
        // A seed on a parachute points at the ground and yaws rather than rolling, so
        // it turns at most far enough to show the filaments swing round.
        SeasonalEffect.DANDELION -> tumble(rotationDegrees * 0.6f, center, flatten) {
            drawDandelion(path, center, radius, palette, alpha, variant, highDetail)
        }
    }
}

/**
 * Rotates about [center], then squeezes across the shape's own axis.
 *
 * The squeeze goes after the rotation and that ordering is the whole effect: a leaf
 * turning over narrows along its stem, not along the side of the screen. Doing it as
 * a scale rather than a re-parameterised path costs one matrix and no allocations,
 * which matters because this runs for every particle on every frame.
 */
private fun DrawScope.tumble(
    rotationDegrees: Float,
    center: Offset,
    flatten: Float,
    block: DrawScope.() -> Unit
) {
    withTransform({
        rotate(rotationDegrees, center)
        if (flatten != 1f) scale(flatten, 1f, center)
    }, block)
}

/**
 * A chip-sized sample of one shape, used instead of emoji on the shape chips.
 *
 * The `Path` is remembered for the life of the chip rather than built in the draw
 * lambda: the shape code resets whatever path it is handed, so one instance is
 * enough, and the Settings page draws a glyph per chip. A fresh path per draw was
 * small, but it was per-chip and per-frame on a page that is nothing but chips.
 */
@Composable
fun SeasonalEffectGlyph(effect: SeasonalEffect, modifier: Modifier = Modifier) {
    val path = remember { Path() }
    Canvas(modifier = modifier) {
        drawSeasonalParticle(
            effect = effect,
            center = center,
            radius = size.minDimension * 0.32f,
            rotationDegrees = 0f,
            alpha = 1f,
            variant = 0,
            path = path
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
    path: Path,
    effect: SeasonalEffect,
    highDetail: Boolean
) {
    if (!highDetail) {
        drawPowderFlake(center, radius, palette, alpha, variant)
        return
    }
    when (effect) {
        SeasonalEffect.POWDER -> drawPowderFlake(center, radius, palette, alpha, variant)
        SeasonalEffect.CRYSTAL -> when (variant % 3) {
            1 -> drawFernFlake(center, radius, palette, alpha)
            2 -> drawPlateFlake(center, radius, palette, alpha, path)
            else -> drawCrystalFlake(center, radius, palette, alpha)
        }
        SeasonalEffect.NEEDLE -> drawNeedleFlake(center, radius, palette, alpha, variant)
        else -> when (variant % 5) {
            1 -> drawFernFlake(center, radius, palette, alpha)
            2 -> drawPlateFlake(center, radius, palette, alpha, path)
            3 -> drawNeedleFlake(center, radius, palette, alpha, 0)
            4 -> drawPowderFlake(center, radius, palette, alpha, variant)
            else -> drawStellarFlake(center, radius, palette, alpha)
        }
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
    // Six shorter spokes in the gaps turn the plate from a hexagon with a cross in it
    // into a twelve-pointed plate. They stop short of the rim so the outline stays the
    // shape the eye reads first.
    for (arm in 0 until 6) {
        val angle = arm * (PI.toFloat() / 3f)
        drawLine(
            color = palette.base.copy(alpha = alpha * 0.44f),
            start = Offset(
                center.x + cos(angle) * radius * 0.25f,
                center.y + sin(angle) * radius * 0.25f
            ),
            end = Offset(
                center.x + cos(angle) * radius * 0.61f,
                center.y + sin(angle) * radius * 0.61f
            ),
            strokeWidth = stroke * 0.7f,
            cap = StrokeCap.Round
        )
    }
    drawCircle(palette.light.copy(alpha = alpha), radius * 0.08f, center)
}

/**
 * A stellar dendrite with a third, finer branch pair and a hexagonal core.
 *
 * The mixed snow already has a dendrite; this one is the "look at that flake"
 * drawing for the Crystal sky, so it spends the extra strokes.
 */
private fun DrawScope.drawCrystalFlake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float
) {
    val stroke = (radius * 0.11f).coerceAtLeast(0.55f)
    val body = palette.base.copy(alpha = alpha)
    val soft = palette.light.copy(alpha = alpha * 0.18f)
    val vein = palette.deep.copy(alpha = alpha * 0.5f)
    drawCircle(palette.light.copy(alpha = alpha * 0.08f), radius * 1.18f, center)
    val detailed = radius >= 5f
    for (arm in 0 until 6) {
        val angle = arm * (PI.toFloat() / 3f)
        val dx = cos(angle)
        val dy = sin(angle)
        val tip = Offset(center.x + dx * radius, center.y + dy * radius)
        drawLine(soft, center, tip, strokeWidth = stroke * 2.8f, cap = StrokeCap.Round)
        drawLine(body, center, tip, strokeWidth = stroke, cap = StrokeCap.Round)
        if (!detailed) continue
        for (pair in 0 until 3) {
            val jointAt = 0.38f + pair * 0.20f
            val branchLength = radius * (0.32f - pair * 0.07f)
            val joint = Offset(center.x + dx * radius * jointAt, center.y + dy * radius * jointAt)
            for (side in -1..1 step 2) {
                val branchAngle = angle + side * (0.68f - pair * 0.08f)
                drawLine(
                    color = if (pair == 0) body else vein,
                    start = joint,
                    end = Offset(
                        joint.x + cos(branchAngle) * branchLength,
                        joint.y + sin(branchAngle) * branchLength
                    ),
                    strokeWidth = stroke * (0.78f - pair * 0.14f),
                    cap = StrokeCap.Round
                )
            }
        }
        drawCircle(palette.light.copy(alpha = alpha * 0.92f), radius * 0.05f, tip)
    }
    drawCircle(palette.light.copy(alpha = alpha), radius * 0.12f, center)
    drawCircle(palette.deep.copy(alpha = alpha * 0.4f), radius * 0.045f, center)
}

/** Columnar ice: one long needle, or a small cluster of three. */
private fun DrawScope.drawNeedleFlake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int
) {
    val stroke = (radius * 0.16f).coerceAtLeast(0.6f)
    val body = palette.base.copy(alpha = alpha)
    val soft = palette.light.copy(alpha = alpha * 0.22f)
    val cluster = variant % 2 == 1
    val arms = if (cluster) 3 else 1
    val startAngle = if (cluster) -0.42f else 0.18f
    for (arm in 0 until arms) {
        val angle = startAngle + arm * 0.42f
        val dx = cos(angle)
        val dy = sin(angle)
        val length = radius * if (cluster && arm != 1) 0.78f else 1f
        val start = Offset(center.x - dx * length, center.y - dy * length)
        val end = Offset(center.x + dx * length, center.y + dy * length)
        drawLine(soft, start, end, strokeWidth = stroke * 2.2f, cap = StrokeCap.Round)
        drawLine(body, start, end, strokeWidth = stroke, cap = StrokeCap.Round)
        // One facet down the lit side of the column. Ice is a prism, and the single
        // highlight is what says so at the size this is drawn - a second outline would
        // just make the needle wider.
        val side = Offset(-dy, dx) * (stroke * 0.22f)
        drawLine(
            color = palette.light.copy(alpha = alpha * 0.55f),
            start = start + side,
            end = end + side,
            strokeWidth = stroke * 0.28f,
            cap = StrokeCap.Round
        )
        drawCircle(palette.light.copy(alpha = alpha * 0.85f), radius * 0.07f, start)
        drawCircle(palette.light.copy(alpha = alpha * 0.85f), radius * 0.07f, end)
    }
    if (cluster) {
        drawCircle(palette.light.copy(alpha = alpha * 0.7f), radius * 0.08f, center)
    }
}

/**
 * Graupel: stacked translucent circles, not arms.
 *
 * Powder at fourteen dp cannot carry a six-armed silhouette - the arms collapse
 * into a grey smudge. A soft grain is what falling snow actually looks like
 * from a window, and it is also the cheap drawing low-end quality falls back to.
 */
private fun DrawScope.drawPowderFlake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int
) {
    val size = radius * when (variant % 3) {
        1 -> 0.72f
        2 -> 1.12f
        else -> 0.90f
    }
    drawCircle(palette.light.copy(alpha = alpha * 0.10f), size * 1.45f, center)
    // A crescent of shade on the side away from the light. Stacked circles were a flat
    // disc; a grain of graupel is a small sphere, and one offset shadow is the cheapest
    // way to say that without a gradient shader per flake.
    drawCircle(
        palette.deep.copy(alpha = alpha * 0.20f),
        size * 0.94f,
        Offset(center.x + size * 0.16f, center.y + size * 0.18f)
    )
    drawCircle(palette.base.copy(alpha = alpha * 0.55f), size, center)
    drawCircle(palette.light.copy(alpha = alpha * 0.72f), size * 0.62f, center)
    drawCircle(
        palette.light.copy(alpha = alpha * 0.9f),
        size * 0.22f,
        Offset(center.x - size * 0.18f, center.y - size * 0.20f)
    )
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
 * Sakura: two single petals and, on the third variant, a five-petal blossom.
 *
 * The blossom was missing for a release while the blurb promised it. A single petal
 * is the right drawing most of the time - it is what a sakura sky is mostly made of -
 * but a sky of nothing but single petals reads as confetti. The blossom is five of
 * the same petal rotated around a pith, which is the cheapest way to get a flower
 * that still matches the petal beside it.
 */
private fun DrawScope.drawSakura(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int,
    highDetail: Boolean
) {
    if (variant % 3 == 2) {
        drawSakuraBlossom(path, center, radius, palette, alpha, highDetail)
        return
    }
    val width = when (variant % 3) {
        1 -> 0.78f
        else -> 1f
    }
    // A petal's face and veins are the cost, not its silhouette: below this the
    // reader has three pixels of pink to work with, and the strokes land on top of
    // each other as one darker smudge.
    val detailed = highDetail && radius > 5f
    withTransform({ scale(width, 1f, center) }) {
        buildPetal(path, center, radius)
        drawPath(path, palette.base.copy(alpha = alpha))

        if (!detailed) return@withTransform
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

/**
 * Five notched petals on a pith.
 *
 * The blossoms are built from the same [buildPetal] outline as the falling petals, so
 * a blossom in the air and a petal beside it are recognisably the same flower. At low
 * detail the petals are five overlapping circles: the notch is under a pixel there and
 * five cubic outlines would cost more than the flower is worth.
 */
private fun DrawScope.drawSakuraBlossom(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    highDetail: Boolean
) {
    val detailed = highDetail && radius > 5f
    val petalRadius = radius * 0.46f
    val petalCenter = Offset(center.x, center.y - radius * 0.43f)
    if (!detailed) {
        for (index in 0 until 5) {
            val angle = index * 72f
            withTransform({ rotate(angle, center) }) {
                drawCircle(
                    color = palette.base.copy(alpha = alpha),
                    radius = petalRadius * 0.72f,
                    center = petalCenter
                )
            }
        }
        drawCircle(palette.light.copy(alpha = alpha * 0.92f), radius * 0.14f, center)
        drawCircle(palette.deep.copy(alpha = alpha * 0.55f), radius * 0.06f, center)
        return
    }

    for (index in 0 until 5) {
        withTransform({ rotate(index * 72f, center) }) {
            buildPetal(path, petalCenter, petalRadius)
            drawPath(path, palette.base.copy(alpha = alpha))
            // The lit face sits toward the pith, which is where a petal curls up.
            buildPetal(
                path,
                Offset(petalCenter.x, petalCenter.y + petalRadius * 0.12f),
                petalRadius * 0.55f
            )
            drawPath(path, palette.light.copy(alpha = alpha * 0.36f))
        }
    }
    val stamen = palette.deep.copy(alpha = alpha * 0.38f)
    for (index in 0 until 5) {
        val angle = index * (PI.toFloat() * 2f / 5f)
        drawLine(
            color = stamen,
            start = center,
            end = Offset(
                center.x + sin(angle) * radius * 0.18f,
                center.y - cos(angle) * radius * 0.18f
            ),
            strokeWidth = (radius * 0.032f).coerceAtLeast(0.3f),
            cap = StrokeCap.Round
        )
    }
    drawCircle(palette.light.copy(alpha = alpha * 0.95f), radius * 0.13f, center)
    drawCircle(palette.deep.copy(alpha = alpha * 0.62f), radius * 0.055f, center)
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

    // Most clovers are lucky enough to carry four leaves; the odd three-leaf one is
    // what makes the lucky four feel like a find rather than a stamp. The three-leaf
    // variant uses the same heart, the same size and the same rim - only the count
    // changes - so the two read as the same plant.
    val fourLeaf = variant % 2 == 0
    val leaves = if (fourLeaf) 4 else 3
    val leaf = radius * if (fourLeaf) 0.54f else 0.58f
    buildCloverHeart(path, leaf)
    val body = palette.base.copy(alpha = alpha)
    val rim = palette.light.copy(alpha = alpha * 0.34f)
    for (index in 0 until leaves) {
        withTransform({
            rotate(index * (360f / leaves), center)
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
