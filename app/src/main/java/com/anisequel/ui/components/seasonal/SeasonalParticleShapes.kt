package com.anisequel.ui.components.seasonal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.anisequel.domain.seasonal.SeasonalEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The shapes themselves: the dispatcher, sakura and clover.
 *
 * ## Why this file exists separately from the layer
 *
 * The layer's job is time: one clock, one placement function, one draw loop. The
 * shapes' job is ink. They were in the same file, and the layer had become the
 * place where both a frame budget and a maple leaf were reviewed. Splitting them
 * also makes the Settings glyph and the falling particle literally the same
 * drawing code rather than two illustrations that can drift apart.
 *
 * Where the rest of the ink lives: the palettes in `SeasonalParticlePalettes.kt`, the
 * autumn leaves and the dandelion seed in `SeasonalLeafShapes.kt`, the five snowflakes in
 * `SeasonalSnowShapes.kt`, and the chip pose in `SeasonalGlyphLegend.kt`. The snow family
 * and the glyph both left this file when the chip became a legend: that change added a
 * pose table and a stroke floor to a file already at the seven-hundred-line ceiling, and
 * the two halves it split off have genuinely different reasons to change - one is routing,
 * one is weather, one is a pictogram.
 */

/**
 * One shape, at one point, drawn with [path] as scratch space.
 *
 * Shared by the overlay, the pile and the Settings chips, so a chip can only ever show a
 * drawing the sky falls with - the chip fixes the [variant] and the pose itself, in
 * `SeasonalGlyphLegend.kt`, and never the ink. Lose that sharing and a settings icon starts
 * promising a shape the overlay does not draw.
 *
 * [variant] selects which of the shape's drawings this is; losing an out-of-range
 * value lands on a palette variant rather than throwing, because the variants
 * exist to be pretty, not to be a contract a restored particle has to satisfy.
 *
 * [flatten] is the shape's own horizontal squeeze: what [tumble] turns a rotation
 * into a roll-over. One is the shape as drawn, less than one is turning edge-on, and
 * negative is the other face coming back round.
 *
 * [strokeFloor] is the thinnest line this shape may emit, in device pixels. Zero -
 * what the sky and the drift pass - leaves every shape on its own hairline minimum,
 * unchanged. The Settings chip asks for one, because eighteen dp on a low-density
 * phone puts a sakura midrib under half a pixel, and a legend with invisible veins is
 * a silhouette of a shape rather than the shape. Capped to a fraction of [radius]
 * below so the ask can never become a fill.
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
    flatten: Float = 1f,
    strokeFloor: Float = 0f,
    /**
     * Pre-resolved ink for [variant].
     *
     * The sky already knows the palette list and passes the entry in; the pile
     * and the Settings glyph use the default lookup. Resolving it here per draw
     * cost every falling particle a `when` and a modulo on every frame.
     */
    palette: ParticlePalette = particlePalette(effect, variant)
) {
    if (radius <= 0.2f || alpha <= 0.001f) return
    // Six percent of the radius, because the filaments of a seed and the ribs of a
    // ginkgo are spaced in radii: a floor that outran their spacing would fuse the
    // crown into one pale fan, which is the smudge this is here to remove, not add.
    val floor = strokeFloor.coerceAtMost(radius * 0.06f)
    when (effect) {
        SeasonalEffect.SNOW, SeasonalEffect.POWDER, SeasonalEffect.CRYSTAL, SeasonalEffect.NEEDLE ->
            tumble(rotationDegrees, center, flatten) {
                drawSnowflake(
                    center, radius, palette, alpha, variant, path, effect, highDetail, floor
                )
            }
        SeasonalEffect.SAKURA -> tumble(rotationDegrees, center, flatten) {
            drawSakura(path, center, radius, palette, alpha, variant, highDetail, floor)
        }
        SeasonalEffect.CLOVER -> tumble(rotationDegrees, center, flatten) {
            drawClover(path, center, radius, palette, alpha, variant, floor)
        }
        SeasonalEffect.MAPLE -> tumble(rotationDegrees, center, flatten) {
            drawMaple(path, center, radius, palette, alpha, variant, highDetail, floor)
        }
        SeasonalEffect.OAK -> tumble(rotationDegrees, center, flatten) {
            drawOak(path, center, radius, palette, alpha, variant, highDetail, floor)
        }
        SeasonalEffect.GINKGO -> tumble(rotationDegrees, center, flatten) {
            drawGinkgo(path, center, radius, palette, alpha, variant, floor)
        }
        // A seed on a parachute points at the ground and yaws rather than rolling, so
        // it turns at most far enough to show the filaments swing round.
        SeasonalEffect.DANDELION -> tumble(rotationDegrees * 0.6f, center, flatten) {
            drawDandelion(path, center, radius, palette, alpha, variant, highDetail, floor)
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
private inline fun DrawScope.tumble(
    rotationDegrees: Float,
    center: Offset,
    flatten: Float,
    crossinline block: DrawScope.() -> Unit
) {
    withTransform({
        rotate(rotationDegrees, center)
        if (flatten != 1f) scale(flatten, 1f, center)
    }, block)
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
    highDetail: Boolean,
    strokeFloor: Float
) {
    if (variant % 3 == 2) {
        drawSakuraBlossom(path, center, radius, palette, alpha, highDetail, strokeFloor)
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
            strokeWidth = (radius * 0.055f).coerceAtLeast(max(0.4f, strokeFloor)),
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
                    strokeWidth = (radius * 0.035f).coerceAtLeast(max(0.35f, strokeFloor * 0.6f)),
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
 *
 * This is the sakura chip's legend. Five petals read as a flower at eighteen dp; a single
 * petal at that size is a scrap of pink, which is why the row used to look arbitrary.
 */
private fun DrawScope.drawSakuraBlossom(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    highDetail: Boolean,
    strokeFloor: Float
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
            strokeWidth = (radius * 0.032f).coerceAtLeast(max(0.3f, strokeFloor * 0.6f)),
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
    path.quadraticTo(
        center.x - radius * 0.10f, center.y - radius * 1.02f,
        center.x, center.y - radius * 0.55f
    )
    path.quadraticTo(
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
 *
 * The chip shows the four-leaf form. The three-leaf variant exists to make the four feel
 * like a find in the sky, and on a chip it is the same picture with a gap where the shape
 * was supposed to be.
 */
private fun DrawScope.drawClover(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int,
    strokeFloor: Float
) {
    val stem = palette.deep.copy(alpha = alpha * 0.85f)
    drawLine(
        color = stem,
        start = Offset(center.x, center.y + radius * 0.18f),
        end = Offset(center.x, center.y + radius * 1.12f),
        strokeWidth = (radius * 0.14f).coerceAtLeast(max(0.5f, strokeFloor)),
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
                drawPath(
                    path,
                    rim,
                    style = Stroke(width = (leaf * 0.06f).coerceAtLeast(max(0.35f, strokeFloor)))
                )
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
    path.quadraticTo(0f, -size * 0.86f, 0f, -size * 0.30f)
    path.quadraticTo(0f, -size * 0.86f, size * 0.32f, -size * 0.86f)
    path.cubicTo(
        size * 0.92f, -size * 0.86f,
        size * 1.15f, size * 0.18f,
        0f, size * 0.95f
    )
    path.close()
}
