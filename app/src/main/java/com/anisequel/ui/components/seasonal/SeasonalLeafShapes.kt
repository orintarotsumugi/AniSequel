package com.anisequel.ui.components.seasonal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The autumn leaves and the dandelion seed.
 *
 * ## Why the leaves share a file
 *
 * Maple, oak and ginkgo are three answers to the same drawing problem: a flat leaf
 * seen face-on, falling, at roughly fourteen dp. They share a stem, a set of veins
 * and a rule that the silhouette has to survive at seven dp. Putting them together
 * means the next leaf is a shape in the family rather than a fourth dialect of leaf.
 *
 * ## What 1.8.2 changed about them
 *
 * The first versions were polygonal. The maple alternated one outer and one inner
 * point around a circle - a five-point star with a squash, straight lines between every
 * corner. The oak hardcoded twelve quadratics for one outline, so every oak in the sky
 * was the same oak. The ginkgo was four straight edges with a triangle's bite out of the
 * top. A real leaf margin is a curve that happens to be wavy, so the oak and the ginkgo
 * now walk an outline sampled from a width function, and the maple keeps its five points
 * but joins every shoulder and every notch with a quadratic. Both use one construction:
 * each sample is a control point and each curve lands on the midpoint of the pair around
 * it, which makes the tangent continuous wherever the reader can see it and costs one
 * quadratic per sample instead of a spline solve per segment.
 *
 * Firefly and starlight are gone, and with them the shapes that were glow rather than
 * matter. The dandelion stays: it is the one figure built from filaments instead of an
 * outline, and it is drawn with strokes and dots because a radial gradient per seed per
 * frame would allocate a shader inside the draw loop.
 *
 * Nothing here allocates per particle. The path is the caller's scratch space, the
 * samples are computed in place, and a captured local helper would have meant a closure
 * per particle per frame - so an outline walk is a loop over an index and the width
 * functions take their numbers as arguments.
 *
 * ## Why these drawings take a stroke floor at all
 *
 * Every width here is a fraction of the radius, which is exactly the trouble the Settings
 * chip ran into: at eighteen dp on a low-density screen the ginkgo's ribs and the maple's
 * veins land under half a device pixel, and the rasteriser fades them out. The fan arrives
 * with no ribs, the seed with no crown - a smudge in the right colour. `strokeFloor` lets
 * that one caller ask for a hairline; the sky and the drift pass zero and every shape's own
 * minimum stands where it was. The dispatcher caps the ask at six percent of the radius, so
 * a floor can rescue a filament without ever fusing two neighbouring ones into a fan.
 */

/** Two radians, in the single precision the rest of the file works in. */
private const val TAU = 6.2831855f

/** How far a maple's shoulder sits from its tip, in radians. */
private const val MAPLE_SHOULDER = 0.30f

/** The maple's five lobes: longest at the top, shortest at the base. */
private val MAPLE_REACH = floatArrayOf(1.00f, 0.86f, 0.66f, 0.66f, 0.86f)

/** A point at [reach] from [center] at [angle], in canvas space. */
private fun pointAt(center: Offset, angle: Float, reach: Float): Offset = Offset(
    center.x + cos(angle) * reach,
    center.y + sin(angle) * reach
)

/**
 * A five-lobed maple with rounded shoulders and a deep notch between each pair.
 *
 * [variant] trades blade width against notch depth - the difference between a sugar
 * maple's broad palm and a softer, more cut-leaved one. The veins are palmate, all five
 * running from where the stem meets the blade, because that is what says maple at a size
 * where each lobe is three pixels across. The chip legend takes the most-cut variant for
 * that reason: at a chip's width the notch depth is the only thing separating a maple from
 * an oak, and the palmate veins have already merged into the blade.
 */
internal fun DrawScope.drawMaple(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int,
    highDetail: Boolean,
    strokeFloor: Float
) {
    val detailed = highDetail && radius > 5f
    val shoulders = if (variant % 3 == 1) 0.68f else 0.60f
    val notch = when (variant % 3) {
        1 -> 0.24f
        2 -> 0.34f
        else -> 0.28f
    }
    // The outward bulge on the leading edge of a lobe. Sub-pixel at the default size, and
    // the thing that makes a large leaf look serrated rather than extruded.
    val serration = if (detailed) radius * 0.05f else 0f
    val step = TAU / 5f
    val start = -PI.toFloat() / 2f
    // The petiole joins at the bottom notch, so every vein, and the stem itself, runs out
    // of this one point rather than out of the middle of the blade.
    val base = Offset(center.x, center.y + radius * notch)

    val first = pointAt(center, start - MAPLE_SHOULDER, radius * MAPLE_REACH[0] * shoulders)
    path.reset()
    path.moveTo(first.x, first.y)
    for (lobe in 0 until 5) {
        val tipAngle = start + lobe * step
        val reach = radius * MAPLE_REACH[lobe]
        val nextLobe = if (lobe == 4) 0 else lobe + 1
        val joint = pointAt(
            center,
            start + nextLobe * step - MAPLE_SHOULDER,
            radius * MAPLE_REACH[nextLobe] * shoulders
        )
        val tip = pointAt(center, tipAngle, reach)
        val outer = pointAt(center, tipAngle - MAPLE_SHOULDER * 0.5f, reach * (shoulders + 0.20f))
        val right = pointAt(center, tipAngle + MAPLE_SHOULDER, reach * shoulders)
        val inner = pointAt(center, tipAngle + MAPLE_SHOULDER * 0.5f, reach * (shoulders + 0.04f))
        val notchPoint = pointAt(center, tipAngle + step * 0.5f, radius * notch)
        // Shoulder, tip, shoulder, notch - and the notch is a control point rather than a
        // corner, so the cut between two lobes keeps its depth and loses its cog-tooth.
        path.quadraticBezierTo(outer.x + serration, outer.y, tip.x, tip.y)
        path.quadraticBezierTo(inner.x, inner.y, right.x, right.y)
        path.quadraticBezierTo(notchPoint.x, notchPoint.y, joint.x, joint.y)
    }
    path.close()
    drawPath(path, palette.base.copy(alpha = alpha))

    if (detailed) {
        // The same outline stroked in the light tone is a rim that catches the sky. It
        // cannot drift out of step with the blade, because it is the same path.
        drawPath(path, palette.light.copy(alpha = alpha * 0.18f), style = Stroke(radius * 0.15f))
    }
    drawLine(
        color = palette.deep.copy(alpha = alpha * 0.8f),
        start = base,
        end = Offset(center.x, center.y + radius * 1.16f),
        strokeWidth = (radius * 0.09f).coerceAtLeast(max(0.45f, strokeFloor)),
        cap = StrokeCap.Round
    )
    val vein = palette.deep.copy(alpha = alpha * 0.40f)
    val veinWidth = (radius * 0.05f).coerceAtLeast(max(0.35f, strokeFloor))
    for (lobe in 0 until 5) {
        val angle = start + lobe * step
        val reach = radius * MAPLE_REACH[lobe]
        drawLine(
            color = vein,
            start = base,
            end = pointAt(center, angle, reach * 0.78f),
            strokeWidth = veinWidth,
            cap = StrokeCap.Round
        )
        if (!detailed) continue
        // One secondary vein per lobe, aimed at its own shoulder. Five extra strokes is
        // the whole cost, and it is what stops a large maple reading as a flat cut-out.
        drawLine(
            color = vein.copy(alpha = alpha * 0.24f),
            start = pointAt(center, angle, reach * 0.42f),
            end = pointAt(center, angle + MAPLE_SHOULDER, reach * 0.62f),
            strokeWidth = veinWidth * 0.6f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * An oak: a spindle with rounded lobes down each side and a point at the tip.
 *
 * One width function, walked up the right margin and back down the left, so a variant is
 * a different number of lobes or a different spread rather than a different set of
 * hand-placed curves. The outline, the fill and the veins are three functions: with all
 * three in one body the leaf was the most branchy drawing in the app, and only the
 * outline is worth reading when a silhouette looks wrong.
 *
 * The chip takes the fewest, widest lobes. Seven lobes is the handsomer oak in the sky and
 * a row of one-pixel bumps on a chip, where five at the widest spread still reads as a
 * margin that has been cut into.
 */
internal fun DrawScope.drawOak(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int,
    highDetail: Boolean,
    strokeFloor: Float
) {
    val detailed = highDetail && radius > 5f
    val lobes = when (variant % 3) {
        1 -> 5f
        2 -> 7f
        else -> 6f
    }
    val spread = when (variant % 3) {
        1 -> 0.44f
        2 -> 0.36f
        else -> 0.40f
    }
    buildOakOutline(path, center, radius, if (detailed) 10 else 6, spread, lobes)
    drawPath(path, palette.base.copy(alpha = alpha))
    if (detailed) {
        // The margin stroked in the light tone, from the same path as the fill.
        drawPath(path, palette.light.copy(alpha = alpha * 0.13f), style = Stroke(radius * 0.12f))
    }
    drawOakVeins(center, radius, palette, alpha, spread, lobes, detailed, strokeFloor)
}

/**
 * The oak's outline, walked up the right margin and back down the left in one loop.
 *
 * Each sample is a control point and each quadratic lands on the midpoint of the pair
 * around it, which makes the margin tangent-continuous for one quadratic per sample.
 * Six points a side when the detail flag is off - the lobes are under a pixel there
 * anyway - and ten when the leaf is big enough for them to be the drawing.
 */
private fun buildOakOutline(
    path: Path,
    center: Offset,
    radius: Float,
    steps: Int,
    spread: Float,
    lobes: Float
) {
    val baseY = center.y + radius * OAK_BASE_DROP
    val axis = radius * OAK_AXIS
    path.reset()
    path.moveTo(center.x, baseY)
    var lastX = center.x
    var lastY = baseY
    val total = steps * 2
    for (index in 1..total) {
        val ascending = index <= steps
        val u = if (ascending) {
            index.toFloat() / steps
        } else {
            (total - index).toFloat() / steps
        }
        val side = if (ascending) 1f else -1f
        val x = center.x + side * oakHalfWidth(u, radius, spread, lobes)
        val y = baseY - axis * u
        if (index == total) {
            path.quadraticBezierTo(lastX, lastY, x, y)
        } else {
            path.quadraticBezierTo(lastX, lastY, (lastX + x) * 0.5f, (lastY + y) * 0.5f)
        }
        lastX = x
        lastY = y
    }
    path.lineTo(center.x, baseY - axis)
    path.close()
}

/**
 * The stem, the midrib and the side veins.
 *
 * The side veins leave the midrib at the waist of a sinus and aim at the lobe beyond it,
 * which is the pattern the eye reads as oak at nine dp - and which the hardcoded three
 * pairs this replaced could not follow once a variant changed the number of lobes.
 */
private fun DrawScope.drawOakVeins(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    spread: Float,
    lobes: Float,
    detailed: Boolean,
    strokeFloor: Float
) {
    val baseY = center.y + radius * OAK_BASE_DROP
    val axis = radius * OAK_AXIS
    val vein = palette.deep.copy(alpha = alpha * 0.38f)
    val veinWidth = (radius * 0.045f).coerceAtLeast(max(0.32f, strokeFloor))
    drawLine(
        color = palette.deep.copy(alpha = alpha * 0.72f),
        start = Offset(center.x, baseY),
        end = Offset(center.x, center.y + radius * 1.24f),
        strokeWidth = (radius * 0.085f).coerceAtLeast(max(0.4f, strokeFloor)),
        cap = StrokeCap.Round
    )
    drawLine(
        color = vein,
        start = Offset(center.x, baseY - radius * 0.06f),
        end = Offset(center.x, baseY - axis * 0.92f),
        strokeWidth = veinWidth,
        cap = StrokeCap.Round
    )
    if (!detailed) return
    val pairs = (lobes.toInt() - 1).coerceAtLeast(2)
    for (pair in 0 until pairs) {
        val u = 0.18f + pair * (0.64f / pairs)
        val reach = oakHalfWidth(u, radius, spread, lobes) * 0.86f
        for (side in -1..1 step 2) {
            drawLine(
                color = vein.copy(alpha = alpha * 0.26f),
                start = Offset(center.x, baseY - axis * u),
                end = Offset(center.x + side * reach, baseY - axis * (u + 0.09f)),
                strokeWidth = veinWidth * 0.7f,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Where the stem joins the blade, and how tall the blade is, as fractions of the radius. */
private const val OAK_BASE_DROP = 0.98f
private const val OAK_AXIS = 1.98f

/** The oak's half-width at [u] along the blade: a taper with the lobes riding on it. */
private fun oakHalfWidth(u: Float, radius: Float, spread: Float, lobes: Float): Float {
    val taper = sin((u * 0.94f + 0.04f) * PI.toFloat())
    return radius * spread * taper * (1f + 0.20f * sin(u * PI.toFloat() * lobes))
}

/**
 * A ginkgo fan on its stem: a wavy arc, a notch at the top, ribs out of the apex.
 *
 * The margin is sampled in polar coordinates about the point the stem joins, which is
 * where the ribs converge - and why the first version, four straight edges and a bite
 * out of the top, read as a paper cutout rather than a leaf.
 *
 * The two variants differ in how deep that notch is pulled, which is the one difference a
 * chip can still show: the ribs and the wave are under a pixel of each other at that size.
 */
internal fun DrawScope.drawGinkgo(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int,
    strokeFloor: Float
) {
    val detailed = radius > 5f
    val notchReach = if (variant % 2 == 0) 0.62f else 0.74f
    val sweep = 1.78f
    val steps = if (detailed) 12 else 7
    val apex = Offset(center.x, center.y + radius * 0.46f)
    val margin = radius * 1.06f

    path.reset()
    path.moveTo(apex.x, apex.y)
    var lastX = apex.x
    var lastY = apex.y
    for (index in 0..steps) {
        val t = index.toFloat() / steps - 0.5f
        val angle = t * sweep
        // The notch is the middle of the margin pulled toward the apex, so each half of
        // the fan stays one curve and the dip has no corner in it.
        val notchShare = 1f - (abs(angle) / (sweep * 0.22f)).coerceIn(0f, 1f)
        val wave = if (detailed) 1f + 0.035f * sin(angle * 5f) else 1f
        val reach = margin * wave * (1f - notchShare * (1f - notchReach))
        val x = apex.x + sin(angle) * reach
        val y = apex.y - cos(angle) * reach
        if (index == 0) {
            path.lineTo(x, y)
        } else {
            path.quadraticBezierTo(lastX, lastY, (lastX + x) * 0.5f, (lastY + y) * 0.5f)
        }
        lastX = x
        lastY = y
    }
    path.lineTo(apex.x, apex.y)
    path.close()
    drawPath(path, palette.base.copy(alpha = alpha))

    if (detailed) {
        drawPath(path, palette.light.copy(alpha = alpha * 0.20f), style = Stroke(radius * 0.10f))
    }
    drawLine(
        color = palette.deep.copy(alpha = alpha * 0.75f),
        start = apex,
        end = Offset(apex.x, apex.y + radius * 0.66f),
        strokeWidth = (radius * 0.08f).coerceAtLeast(max(0.4f, strokeFloor)),
        cap = StrokeCap.Round
    )
    val vein = palette.deep.copy(alpha = alpha * 0.32f)
    val veinWidth = (radius * 0.038f).coerceAtLeast(max(0.3f, strokeFloor))
    val ribs = if (detailed) 7 else 4
    for (rib in 0 until ribs) {
        val angle = (rib.toFloat() / (ribs - 1) - 0.5f) * sweep * 0.92f
        // A ginkgo's ribs are radial - they fan straight out of the apex - so a line is
        // both the cheaper drawing and the truer one.
        drawLine(
            color = vein,
            start = apex,
            end = Offset(
                apex.x + sin(angle) * margin * 0.84f,
                apex.y - cos(angle) * margin * 0.84f
            ),
            strokeWidth = veinWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * A dandelion seed: a small dark seed, a curved beak, a parachute of filaments.
 *
 * It falls rather than rises, so the pappus is above the seed and the whole thing hangs.
 * The hairs are quadratics with a small alternating bow, which is what makes the crown
 * read as a globe of filaments instead of a flat fan of lines. At fourteen dp the pappus
 * is a soft halo; at twice that it is recognisably a seed clock, which is the point of
 * drawing it rather than sprinkling white circles.
 *
 * The chip is the exception the floor was added for: the hairs *are* the shape, and they
 * are the thinnest ink in the app relative to the radius, so at eighteen dp the crown used
 * to fade to a pale blur. [strokeFloor] keeps them separate, and the twelve-hair variant is
 * the one with room enough between them to show it.
 */
internal fun DrawScope.drawDandelion(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int,
    highDetail: Boolean,
    strokeFloor: Float
) {
    val detailed = highDetail && radius > 5f
    val seed = Offset(center.x, center.y + radius * 0.74f)
    val crown = Offset(center.x, center.y + radius * 0.16f)
    drawCircle(
        palette.light.copy(alpha = alpha * 0.07f),
        radius * 1.18f,
        Offset(center.x, center.y - radius * 0.10f)
    )
    drawOval(
        color = palette.deep.copy(alpha = alpha * 0.88f),
        topLeft = Offset(seed.x - radius * 0.10f, seed.y - radius * 0.20f),
        size = Size(radius * 0.20f, radius * 0.44f)
    )
    // The beak is curved because a seed hanging from a parachute is not on a plumb line.
    path.reset()
    path.moveTo(crown.x, crown.y)
    path.quadraticBezierTo(
        crown.x + radius * 0.07f,
        crown.y + radius * 0.22f,
        seed.x,
        seed.y - radius * 0.16f
    )
    drawPath(
        path = path,
        color = palette.deep.copy(alpha = alpha * 0.66f),
        style = Stroke(
            (radius * 0.05f).coerceAtLeast(max(0.3f, strokeFloor)),
            cap = StrokeCap.Round
        )
    )

    val rays = when {
        !detailed -> 8
        variant % 2 == 0 -> 12
        else -> 14
    }
    val spread = 1.62f
    val hair = palette.light.copy(alpha = alpha * 0.58f)
    // Eighty percent of the ask: the crown has to stay finer than the beak under it, and
    // the hairs are spaced by the radius too, so a floor taken whole would close the air
    // between them and put the blur back.
    val hairWidth = (radius * 0.026f).coerceAtLeast(max(0.25f, strokeFloor * 0.8f))
    for (index in 0 until rays) {
        val t = index.toFloat() / (rays - 1) - 0.5f
        val angle = -PI.toFloat() / 2f + t * spread
        val reach = radius * (0.96f + 0.10f * cos(t * PI.toFloat()))
        val tip = Offset(crown.x + sin(angle) * reach, crown.y - cos(angle) * reach)
        // Half the hairs bow one way and half the other, so the silhouette has depth
        // rather than one flat fan of radii.
        val bow = if (index % 2 == 0) 0.12f else -0.12f
        path.reset()
        path.moveTo(crown.x, crown.y)
        path.quadraticBezierTo(
            crown.x + sin(angle + bow) * reach * 0.55f,
            crown.y - cos(angle + bow) * reach * 0.55f,
            tip.x,
            tip.y
        )
        drawPath(path = path, color = hair, style = Stroke(hairWidth, cap = StrokeCap.Round))
        drawCircle(palette.light.copy(alpha = alpha * 0.8f), radius * 0.04f, tip)
        if (!detailed) continue
        for (side in -1..1 step 2) {
            val barbAngle = angle + side * 0.42f
            drawLine(
                color = palette.light.copy(alpha = alpha * 0.30f),
                start = Offset(
                    crown.x + sin(angle) * reach * 0.80f,
                    crown.y - cos(angle) * reach * 0.80f
                ),
                end = Offset(
                    crown.x + sin(barbAngle) * reach * 0.98f,
                    crown.y - cos(barbAngle) * reach * 0.98f
                ),
                strokeWidth = hairWidth * 0.8f,
                cap = StrokeCap.Round
            )
        }
    }
    drawCircle(palette.deep.copy(alpha = alpha * 0.55f), radius * 0.06f, crown)
}
