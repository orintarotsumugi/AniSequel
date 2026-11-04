package com.anisequel.ui.components.seasonal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.anisequel.domain.seasonal.SeasonalEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The six snowflakes one dispatcher picks between.
 *
 * ## Why these are a file of their own
 *
 * They were in `SeasonalParticleShapes.kt` with the dispatcher and the petal shapes, and
 * that file had reached the point where a review of the chip legend, the sakura's veins and
 * the plate flake's twelve spokes was one scroll. The snow family has its own reason to
 * change: it is the only shape group where the *effect* does not decide the drawing, so a
 * change here moves four Settings chips and two skies at once - the dispatcher stays where
 * the routing is reviewed, and the drawings live where the ink is.
 *
 * ## The rule the dispatcher depends on
 *
 * [drawSnowflake] routes on `effect` first and on `variant` second, and every branch has to
 * stay inside the effect's own [SeasonalEffect.variantCount] or the palette the shape was
 * generated with belongs to a drawing nothing asked for. Powder has no arms - graupel is
 * stacked discs, and it is also what every flake falls back to when the detail flag is off,
 * which is why it takes no stroke floor: there is no line in it to floor. It is `internal`
 * rather than `private` because the dispatcher that reaches it lives in the other file; the
 * six drawings under it stay private, since nothing outside this file can name them.
 *
 * Each drawing's widths are fractions of the radius with an absolute hairline under them.
 * The [strokeFloor] that arrives here is the Settings chip's ask, capped by the dispatcher
 * to a fraction of the radius, and it raises the arm width only - branches, spokes and
 * facets are derived from that one number, so a flake cannot have arms twice as heavy as
 * its own branches.
 */
/**
 * The flake one variant of one winter shape is.
 *
 * [strokeFloor] reaches every drawing that has arms; the graupel grain is five discs and
 * no lines, so it is the one snow drawing that has nothing to floor and is not given it.
 */
internal fun DrawScope.drawSnowflake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int,
    path: Path,
    effect: SeasonalEffect,
    highDetail: Boolean,
    strokeFloor: Float
) {
    if (!highDetail) {
        drawPowderFlake(center, radius, palette, alpha, variant)
        return
    }
    when (effect) {
        SeasonalEffect.POWDER -> drawPowderFlake(center, radius, palette, alpha, variant)
        SeasonalEffect.CRYSTAL -> when (variant % 3) {
            1 -> drawFernFlake(center, radius, palette, alpha, strokeFloor)
            2 -> drawPlateFlake(center, radius, palette, alpha, path, strokeFloor)
            else -> drawCrystalFlake(center, radius, palette, alpha, strokeFloor)
        }
        SeasonalEffect.NEEDLE -> drawNeedleFlake(
            center, radius, palette, alpha, variant, strokeFloor
        )
        else -> when (variant % 5) {
            1 -> drawFernFlake(center, radius, palette, alpha, strokeFloor)
            2 -> drawPlateFlake(center, radius, palette, alpha, path, strokeFloor)
            3 -> drawNeedleFlake(center, radius, palette, alpha, 0, strokeFloor)
            4 -> drawPowderFlake(center, radius, palette, alpha, variant)
            else -> drawStellarFlake(center, radius, palette, alpha, strokeFloor)
        }
    }
}

/**
 * The classic dendrite: six arms, two branch pairs and a dot at each tip.
 *
 * Every arm is drawn twice - a wide translucent pass under a crisp one. That is
 * a soft edge without a `BlurMaskFilter` or a gradient per flake, both of which
 * would allocate inside a loop that runs dozens of times per frame.
 *
 * The `coerceAtLeast` on the arm width is the shape's own floor, and
 * [strokeFloor] raises it but never lowers it: the branch pairs and the tip dots are all
 * derived from the arm, so one floor at the arm carries the whole flake.
 */
private fun DrawScope.drawStellarFlake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    strokeFloor: Float
) {
    val stroke = (radius * 0.13f).coerceAtLeast(max(0.55f, strokeFloor))
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
    alpha: Float,
    strokeFloor: Float
) {
    val stroke = (radius * 0.10f).coerceAtLeast(max(0.5f, strokeFloor))
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

/**
 * A plate: two nested hexagons with short spokes, the flattest snow there is.
 *
 * This is also what the Snow chip shows, because it is the one winter drawing that cannot
 * be mistaken for the dendrite the Crystal chip shows two places along the same row.
 */
private fun DrawScope.drawPlateFlake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    path: Path,
    strokeFloor: Float
) {
    drawCircle(palette.light.copy(alpha = alpha * 0.075f), radius * 1.2f, center)
    val stroke = (radius * 0.09f).coerceAtLeast(max(0.5f, strokeFloor))
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
 * drawing for the Crystal sky, so it spends the extra strokes. It is also the Crystal
 * chip's legend, which is why the [strokeFloor] reaches here: three branch pairs at
 * eighteen dp is precisely the ink that turns to mush without one.
 */
private fun DrawScope.drawCrystalFlake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    strokeFloor: Float
) {
    val stroke = (radius * 0.11f).coerceAtLeast(max(0.55f, strokeFloor))
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

/**
 * Columnar ice: one long needle, or a small cluster of three.
 *
 * The chip shows the cluster. One line at eighteen dp is the thinnest thing in the app and
 * reads as a scratch, not as a shape; three bound columns are what the blurb promises.
 */
private fun DrawScope.drawNeedleFlake(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int,
    strokeFloor: Float
) {
    val stroke = (radius * 0.16f).coerceAtLeast(max(0.6f, strokeFloor))
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
