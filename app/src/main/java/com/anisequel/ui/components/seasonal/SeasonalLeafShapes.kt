package com.anisequel.ui.components.seasonal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The autumn leaves and the shapes that are light rather than matter.
 *
 * ## Why the leaves share a file
 *
 * Maple, oak and ginkgo are three answers to the same drawing problem: a flat
 * leaf seen face-on, falling, at roughly fourteen dp. They share a stem, a set of
 * veins and a rule that the silhouette has to survive at seven dp. Putting them
 * together means the next leaf is a shape in the family rather than a fourth
 * dialect of leaf.
 *
 * Firefly, dandelion and starlight are the other half of the same question: a
 * shape made of glow or filaments rather than an opaque outline. They are drawn
 * with stacked translucent fills because a gradient or a blur mask would allocate
 * a shader per particle per frame.
 */

/**
 * A five-lobed maple with shoulders, not a five-point star.
 *
 * The first version alternated one outer and one inner point around a circle,
 * which is a star with a squash. A maple leaf's silhouette comes from three
 * points per lobe - shoulder, tip, shoulder - with a deep V between lobes, and
 * the veins are what stop the bigger silhouette reading as a flat sticker.
 */
internal fun DrawScope.drawMaple(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float
) {
    path.reset()
    val lobes = 5
    val step = (2f * PI.toFloat()) / lobes
    val start = -PI.toFloat() / 2f
    for (lobe in 0 until lobes) {
        val tipAngle = start + lobe * step
        val shoulderLeft = tipAngle - 0.26f
        val shoulderRight = tipAngle + 0.26f
        val notch = tipAngle + step * 0.5f
        val shoulder = Offset(
            center.x + cos(shoulderLeft) * radius * 0.64f,
            center.y + sin(shoulderLeft) * radius * 0.64f
        )
        val tip = Offset(
            center.x + cos(tipAngle) * radius,
            center.y + sin(tipAngle) * radius
        )
        val shoulder2 = Offset(
            center.x + cos(shoulderRight) * radius * 0.64f,
            center.y + sin(shoulderRight) * radius * 0.64f
        )
        val notchPoint = Offset(
            center.x + cos(notch) * radius * 0.30f,
            center.y + sin(notch) * radius * 0.30f
        )
        if (lobe == 0) path.moveTo(shoulder.x, shoulder.y) else path.lineTo(shoulder.x, shoulder.y)
        path.lineTo(tip.x, tip.y)
        path.lineTo(shoulder2.x, shoulder2.y)
        path.lineTo(notchPoint.x, notchPoint.y)
    }
    path.close()
    drawPath(path, palette.base.copy(alpha = alpha))

    // Stem and veins in the deep tone. The stem hangs below the leaf, which is
    // what says "this came off a tree" at a size where the lobes are only a few
    // pixels wide.
    drawLine(
        color = palette.deep.copy(alpha = alpha * 0.8f),
        start = Offset(center.x, center.y + radius * 0.10f),
        end = Offset(center.x, center.y + radius * 1.18f),
        strokeWidth = (radius * 0.10f).coerceAtLeast(0.45f),
        cap = StrokeCap.Round
    )
    val vein = palette.deep.copy(alpha = alpha * 0.42f)
    for (lobe in 0 until lobes) {
        val angle = start + lobe * step
        drawLine(
            color = vein,
            start = Offset(center.x, center.y + radius * 0.08f),
            end = Offset(
                center.x + cos(angle) * radius * 0.82f,
                center.y + sin(angle) * radius * 0.82f
            ),
            strokeWidth = (radius * 0.045f).coerceAtLeast(0.35f),
            cap = StrokeCap.Round
        )
    }
}

/** An oak leaf: a long wavy outline rather than five points. */
internal fun DrawScope.drawOak(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int
) {
    val width = when (variant % 3) {
        1 -> 0.86f
        2 -> 1.10f
        else -> 1f
    }
    val r = radius
    path.reset()
    path.moveTo(center.x, center.y - r)
    // Right side, tip to base: each pair of quadratics is one rounded lobe.
    path.quadraticBezierTo(
        center.x + width * 0.52f * r, center.y - r * 0.86f,
        center.x + width * 0.54f * r, center.y - r * 0.62f
    )
    path.quadraticBezierTo(
        center.x + width * 0.26f * r, center.y - r * 0.52f,
        center.x + width * 0.44f * r, center.y - r * 0.36f
    )
    path.quadraticBezierTo(
        center.x + width * 0.94f * r, center.y - r * 0.26f,
        center.x + width * 0.80f * r, center.y - r * 0.02f
    )
    path.quadraticBezierTo(
        center.x + width * 0.44f * r, center.y + r * 0.06f,
        center.x + width * 0.60f * r, center.y + r * 0.24f
    )
    path.quadraticBezierTo(
        center.x + width * 0.66f * r, center.y + r * 0.44f,
        center.x + width * 0.40f * r, center.y + r * 0.58f
    )
    path.quadraticBezierTo(
        center.x + width * 0.22f * r, center.y + r * 0.78f,
        center.x, center.y + r * 0.96f
    )
    // Left side, base to tip: the mirror of the same six curves.
    path.quadraticBezierTo(
        center.x - width * 0.22f * r, center.y + r * 0.78f,
        center.x - width * 0.40f * r, center.y + r * 0.58f
    )
    path.quadraticBezierTo(
        center.x - width * 0.66f * r, center.y + r * 0.44f,
        center.x - width * 0.60f * r, center.y + r * 0.24f
    )
    path.quadraticBezierTo(
        center.x - width * 0.44f * r, center.y + r * 0.06f,
        center.x - width * 0.80f * r, center.y - r * 0.02f
    )
    path.quadraticBezierTo(
        center.x - width * 0.94f * r, center.y - r * 0.26f,
        center.x - width * 0.44f * r, center.y - r * 0.36f
    )
    path.quadraticBezierTo(
        center.x - width * 0.26f * r, center.y - r * 0.52f,
        center.x - width * 0.54f * r, center.y - r * 0.62f
    )
    path.quadraticBezierTo(
        center.x - width * 0.52f * r, center.y - r * 0.86f,
        center.x, center.y - r
    )
    path.close()
    drawPath(path, palette.base.copy(alpha = alpha))

    val vein = palette.deep.copy(alpha = alpha * 0.40f)
    drawLine(
        color = palette.deep.copy(alpha = alpha * 0.72f),
        start = Offset(center.x, center.y + r * 0.20f),
        end = Offset(center.x, center.y + r * 1.14f),
        strokeWidth = (r * 0.09f).coerceAtLeast(0.4f),
        cap = StrokeCap.Round
    )
    drawLine(
        color = vein,
        start = Offset(center.x, center.y + r * 0.88f),
        end = Offset(center.x, center.y - r * 0.88f),
        strokeWidth = (r * 0.05f).coerceAtLeast(0.35f)
    )
    for (level in 0 until 3) {
        val at = -0.52f + level * 0.46f
        val sideVein = width * (0.48f + level * 0.09f) * r
        for (side in -1..1 step 2) {
            drawLine(
                color = vein,
                start = Offset(center.x, center.y + r * at),
                end = Offset(center.x + side * sideVein, center.y + r * (at - 0.26f)),
                strokeWidth = (r * 0.035f).coerceAtLeast(0.3f)
            )
        }
    }
}

/** A ginkgo fan on its stem: a wide arc with a notch and radiating veins. */
internal fun DrawScope.drawGinkgo(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int
) {
    val notch = if (variant % 2 == 0) 0.58f else 0.70f
    path.reset()
    path.moveTo(center.x, center.y + radius * 0.52f)
    path.lineTo(center.x - radius * 0.95f, center.y - radius * 0.34f)
    path.quadraticBezierTo(
        center.x - radius * 0.48f, center.y - radius * 0.98f,
        center.x - radius * 0.13f, center.y - radius * 0.72f
    )
    path.lineTo(center.x, center.y - radius * notch)
    path.lineTo(center.x + radius * 0.13f, center.y - radius * 0.72f)
    path.quadraticBezierTo(
        center.x + radius * 0.48f, center.y - radius * 0.98f,
        center.x + radius * 0.95f, center.y - radius * 0.34f
    )
    path.lineTo(center.x, center.y + radius * 0.52f)
    path.close()
    drawPath(path, palette.base.copy(alpha = alpha))

    drawLine(
        color = palette.deep.copy(alpha = alpha * 0.75f),
        start = Offset(center.x, center.y + radius * 0.42f),
        end = Offset(center.x, center.y + radius * 1.10f),
        strokeWidth = (radius * 0.08f).coerceAtLeast(0.4f),
        cap = StrokeCap.Round
    )
    val vein = palette.deep.copy(alpha = alpha * 0.34f)
    // Five ribs fanning out of the apex, which is what makes the fan read as a
    // leaf rather than as a triangle with a bite out of it.
    for (index in 0 until 5) {
        val spread = (index - 2) * 0.33f
        drawLine(
            color = vein,
            start = Offset(center.x, center.y + radius * 0.40f),
            end = Offset(
                center.x + sin(spread) * radius * 0.82f,
                center.y + radius * 0.40f - cos(spread) * radius * 0.92f
            ),
            strokeWidth = (radius * 0.04f).coerceAtLeast(0.3f),
            cap = StrokeCap.Round
        )
    }
}

internal fun DrawScope.drawFirefly(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    twinkle: Float
) {
    val pulse = (0.72f + 0.28f * twinkle).coerceIn(0.5f, 1.2f)
    // Three rings rather than a blur: a radial gradient per firefly per frame is a
    // shader allocation inside the loop. Stacked translucent circles read the same
    // at this size and cost three fills.
    drawCircle(palette.base.copy(alpha = alpha * 0.10f * pulse), radius * 3.1f, center)
    drawCircle(palette.base.copy(alpha = alpha * 0.20f * pulse), radius * 1.8f, center)
    drawCircle(palette.base.copy(alpha = alpha * 0.78f), radius * 0.72f, center)
    drawCircle(palette.light.copy(alpha = alpha * 0.92f), radius * 0.26f, center)
    // The wings are two faint slivers; at this size they are the difference
    // between a glowing dot and a living thing.
    drawOval(
        color = palette.light.copy(alpha = alpha * 0.22f),
        topLeft = Offset(center.x - radius * 0.62f, center.y - radius * 0.32f),
        size = Size(radius * 0.58f, radius * 0.24f)
    )
    drawOval(
        color = palette.light.copy(alpha = alpha * 0.22f),
        topLeft = Offset(center.x + radius * 0.04f, center.y - radius * 0.32f),
        size = Size(radius * 0.58f, radius * 0.24f)
    )
}

/**
 * A dandelion seed: a small dark seed, a stalk, and a parachute of filaments.
 *
 * The filaments are drawn from a point above the seed and fan upward, each with a
 * tiny terminal dot and a secondary hair. At fourteen dp the pappus is a soft
 * halo; at twice that it is recognisably a seed clock, which is the point of
 * drawing it rather than sprinkling white circles.
 */
internal fun DrawScope.drawDandelion(
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int
) {
    val seed = Offset(center.x, center.y + radius * 0.62f)
    drawCircle(palette.light.copy(alpha = alpha * 0.07f), radius * 1.35f, center)
    drawOval(
        color = palette.deep.copy(alpha = alpha * 0.85f),
        topLeft = Offset(seed.x - radius * 0.09f, seed.y - radius * 0.20f),
        size = Size(radius * 0.18f, radius * 0.42f)
    )
    val crown = Offset(center.x, center.y + radius * 0.18f)
    drawLine(
        color = palette.deep.copy(alpha = alpha * 0.7f),
        start = seed,
        end = crown,
        strokeWidth = (radius * 0.055f).coerceAtLeast(0.35f),
        cap = StrokeCap.Round
    )

    val filaments = if (variant % 2 == 0) 7 else 9
    val spread = radius * 1.02f
    for (index in 0 until filaments) {
        val offset = index - (filaments - 1) / 2f
        val angle = -PI.toFloat() / 2f + offset * 0.23f
        val end = Offset(
            crown.x + cos(angle) * spread,
            crown.y + sin(angle) * spread
        )
        drawLine(
            color = palette.light.copy(alpha = alpha * 0.62f),
            start = crown,
            end = end,
            strokeWidth = (radius * 0.032f).coerceAtLeast(0.3f),
            cap = StrokeCap.Round
        )
        drawCircle(palette.light.copy(alpha = alpha * 0.85f), radius * 0.045f, end)
        if (radius > 5f) {
            for (side in -1..1 step 2) {
                val hairStart = Offset(
                    crown.x + cos(angle) * spread * 0.62f,
                    crown.y + sin(angle) * spread * 0.62f
                )
                val hairAngle = angle + side * 0.34f
                drawLine(
                    color = palette.light.copy(alpha = alpha * 0.34f),
                    start = hairStart,
                    end = Offset(
                        hairStart.x + cos(hairAngle) * radius * 0.22f,
                        hairStart.y + sin(hairAngle) * radius * 0.22f
                    ),
                    strokeWidth = (radius * 0.022f).coerceAtLeast(0.25f),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/** A four-point sparkle with a glow and a white core. */
internal fun DrawScope.drawStar(
    path: Path,
    center: Offset,
    radius: Float,
    palette: ParticlePalette,
    alpha: Float,
    variant: Int,
    twinkle: Float
) {
    val pulse = (0.7f + 0.3f * twinkle).coerceIn(0.5f, 1.15f)
    drawCircle(palette.base.copy(alpha = alpha * 0.10f * pulse), radius * 2.4f, center)
    drawCircle(palette.base.copy(alpha = alpha * 0.18f * pulse), radius * 1.35f, center)
    path.reset()
    val rotation = if (variant % 2 == 0) 0f else PI.toFloat() / 4f
    for (index in 0 until 8) {
        val angle = rotation + index * (PI.toFloat() / 4f) - PI.toFloat() / 2f
        val reach = if (index % 2 == 0) radius else radius * 0.18f
        val x = center.x + cos(angle) * reach
        val y = center.y + sin(angle) * reach
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, palette.base.copy(alpha = alpha * 0.95f))
    drawCircle(palette.light.copy(alpha = alpha), radius * 0.18f, center)
}
