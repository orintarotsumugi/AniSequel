package com.anisequel.ui.components.seasonal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.domain.seasonal.ParticleDirection
import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.SeasonalParticle
import com.anisequel.domain.seasonal.particleSizeDp
import com.anisequel.domain.seasonal.particleSpeedScale
import com.anisequel.domain.seasonal.seasonalParticles
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.rememberIsResumed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The seed every layer shares, so the sky is the same on every launch. */
private const val SEASONAL_SEED = 20261103

private const val TAU = 6.2831855f
private const val RADIANS_TO_DEGREES = 57.29578f

/** One cycle of travel is a little taller than the screen, so nothing pops. */
private const val TRAVEL_FACTOR = 1.3f
private const val TRAVEL_MARGIN = 0.15f

/** The band at each end of a particle's travel over which it fades in or out. */
private const val EDGE_FADE = 0.08f

/** Below this a flake is a rounding error on the background, not a drawn shape. */
private const val MIN_VISIBLE_ALPHA = 0.01f

/** The frame budget: update the clock at most sixty times a second. */
private const val FRAME_SECONDS = 1f / 60f

/**
 * A step longer than this is a stall, not a frame - after a pause the clock jumps
 * by the length of the pause and every particle teleports.
 */
private const val MAX_STEP_SECONDS = 0.1f

/** The two sides a snowflake's branches fork towards. Hoisted so a sky does not
 * allocate an array per arm per frame. */
private val SNOW_BRANCH_SIDES = intArrayOf(-1, 1)

/**
 * The shapes themselves.
 *
 * Fixed colours rather than theme roles, on purpose. A snowflake tinted
 * `onSurface` is black on the light theme and white on the dark one, which reads
 * as debris rather than as snow, and a seasonal effect that changes colour with
 * the wallpaper is no longer the season the reader picked.
 *
 * What that trade costs, measured against `#FFFFFF` and `#000000`: only Maple
 * (3.06) clears 3:1 on a light surface - Snow 2.12, Bubble 2.66, Clover 2.73,
 * Sakura 2.57 and Firefly 1.53 do not, and every particle is then drawn at
 * 0.55 to 1.0 alpha, which takes them lower still. All six clear 3:1 on black.
 * That is the honest shape of it: a flake dark enough to hit 3:1 on white is a
 * grey smudge on a true-black page, and this layer carries no information, so
 * it is allowed to be a decoration that is quieter on light than on dark.
 */
internal fun effectColor(effect: SeasonalEffect): Color = when (effect) {
    SeasonalEffect.SNOW -> Color(0xFF7FB8E6)
    SeasonalEffect.SAKURA -> Color(0xFFEC7FA9)
    SeasonalEffect.CLOVER -> Color(0xFF5DAE63)
    SeasonalEffect.MAPLE -> Color(0xFFE2762E)
    SeasonalEffect.FIREFLY -> Color(0xFFFFC94D)
    SeasonalEffect.BUBBLE -> Color(0xFF4AA8D8)
}

/**
 * The seasonal overlay's drawing surface.
 *
 * ## Why the clock is a draw-phase read
 *
 * Every particle's position is computed from one `Float` - seconds since the
 * layer started - and that value is read only inside the `Canvas` draw lambda.
 * A snapshot read in the draw phase invalidates the draw, not the composition, so
 * sixty frames a second cost sixty repaints of one layer and zero recompositions
 * of the page underneath. Reading it in the composable body instead would
 * recompose the entire feature tree behind the overlay on every frame.
 *
 * ## Why it stops
 *
 * Two gates, both required. [rememberIsResumed] is false while the Activity is
 * paused or covered, so the loop does not keep drawing (or waking the display)
 * behind another app. [MotionStyle.INSTANT] is the reader asking the app to stop
 * animating - the clock freezes and the particles hold exactly where they are,
 * which is a still picture rather than a feature that ignores the setting.
 *
 * ## Why there is no pointer input anywhere in this file
 *
 * The overlay sits above every page. A single `clickable`, `pointerInput` or
 * gesture detector here would eat the first tap everywhere in the app. A `Canvas`
 * with only a draw block cannot consume a touch, and that is the whole contract -
 * it is pinned by a test.
 */
@Composable
fun SeasonalParticleLayer(
    effect: SeasonalEffect,
    density: Float,
    sizeScale: Float = 1f,
    speedScale: Float = 1f,
    modifier: Modifier = Modifier,
    seed: Int = SEASONAL_SEED
) {
    val resumed = rememberIsResumed()
    val animating = resumed && ExpressiveMotion.speed != MotionStyle.INSTANT

    // Regenerated only when the shape or the count changes, and never on a frame.
    // The seed is fixed, so raising the density adds flakes instead of re-rolling
    // the ones already on screen.
    val particles = remember(effect, density, seed) {
        seasonalParticles(effect = effect, density = density, seed = seed)
    }
    val baseDp = remember(sizeScale) { particleSizeDp(sizeScale) }
    val speed = remember(speedScale) { particleSpeedScale(speedScale) }

    var clock by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(animating) {
        if (!animating) return@LaunchedEffect
        // The clock is not reset here. Pausing and resuming, or toggling Instant
        // and back, finds the sky where it was left rather than restarting it - a
        // layer that jumped to the top every time the app came back would read as
        // a stutter exactly when the reader returned to look at it.
        seasonalClock { step -> clock += step }
    }

    val color = effectColor(effect)
    val falling = effect.direction == ParticleDirection.FALL

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        val basePx = baseDp.dp.toPx()

        // One path for the whole frame: a fresh `Path` per particle per frame is
        // sixty-four allocations sixty times a second while the sky is on screen.
        val path = Path()
        val seconds = clock

        particles.forEach { particle ->
            val placed = particlePlacement(particle, seconds, speed, width, height, falling)
                ?: return@forEach
            drawSeasonalParticle(
                effect = effect,
                center = placed.center,
                radius = basePx * particle.size,
                rotationDegrees = placed.rotationDegrees,
                alpha = placed.alpha,
                color = color,
                path = path
            )
        }
    }
}

/**
 * The frame loop, paced.
 *
 * A step is only handed on once [FRAME_SECONDS] has accumulated, so a 120Hz display
 * drives the sky at the same rate as a 60Hz one, and a step is capped at
 * [MAX_STEP_SECONDS] so a stall - returning from a pause, a breakpoint, a cold
 * first frame - moves the particles a little rather than flinging them all the way
 * across the screen.
 */
private suspend fun seasonalClock(onStep: (Float) -> Unit) {
    var lastFrame = 0L
    var accumulated = 0f
    while (true) {
        val frame = withFrameNanos { it }
        val elapsed = if (lastFrame == 0L) 0L else frame - lastFrame
        lastFrame = frame
        accumulated += elapsed / 1_000_000_000f
        if (accumulated < FRAME_SECONDS) continue
        onStep(accumulated.coerceAtMost(MAX_STEP_SECONDS))
        accumulated = 0f
    }
}

/** Where one particle sits, and how visible it is. */
private data class Placement(
    val center: Offset,
    val alpha: Float,
    val rotationDegrees: Float
)

/**
 * One particle's place at [seconds], or null when it has faded past drawing.
 *
 * The travel is one cycle taller than the screen ([TRAVEL_FACTOR]) and starts
 * [TRAVEL_MARGIN] outside it, so a flake is never born or killed on screen - it
 * enters from above, and the [EDGE_FADE] band at each end of the cycle is what the
 * reader actually sees. Rising shapes run the same geometry backwards.
 *
 * Pure, and outside the draw lambda, because the loop that calls it is the layer's
 * whole cost: leaving the arithmetic inline made the composable the most branchy
 * function in the app for a computation with no decisions in it.
 */
private fun particlePlacement(
    particle: SeasonalParticle,
    seconds: Float,
    speedScale: Float,
    width: Float,
    height: Float,
    falling: Boolean
): Placement? {
    val travel = height * TRAVEL_FACTOR
    val startY = if (falling) -height * TRAVEL_MARGIN else height * (1f + TRAVEL_MARGIN)
    val progress = wrap(particle.y + particle.speed * speedScale * seconds)
    val y = if (falling) startY + progress * travel else startY - progress * travel
    val x = width * particle.x +
        sin(seconds * particle.swayRate * TAU + particle.phase) * particle.sway * width
    val edge = min(progress, 1f - progress) / EDGE_FADE
    val alpha = particle.alpha * edge.coerceIn(0f, 1f)
    if (alpha <= MIN_VISIBLE_ALPHA) return null
    return Placement(
        center = Offset(x, y),
        alpha = alpha,
        rotationDegrees = (particle.spin * seconds + particle.phase) * RADIANS_TO_DEGREES
    )
}

/**
 * One shape, at one point, drawn with [path] as scratch space.
 *
 * Shared by the overlay and the Settings chips so the glyph on a shape chip is
 * the thing that will fall past the reader - not a separate illustration that can
 * drift from it.
 */
internal fun DrawScope.drawSeasonalParticle(
    effect: SeasonalEffect,
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    alpha: Float,
    color: Color,
    path: Path
) {
    val paint = color.copy(alpha = color.alpha * alpha)
    when (effect) {
        SeasonalEffect.SNOW -> withTransform({ rotate(rotationDegrees, center) }) {
            drawSnowflake(center, radius, paint)
        }
        SeasonalEffect.SAKURA -> withTransform({ rotate(rotationDegrees, center) }) {
            buildSakura(path, center, radius)
            drawPath(path, paint)
        }
        SeasonalEffect.CLOVER -> withTransform({ rotate(rotationDegrees, center) }) {
            drawClover(center, radius, paint)
        }
        SeasonalEffect.MAPLE -> withTransform({ rotate(rotationDegrees, center) }) {
            buildMaple(path, center, radius)
            drawPath(path, paint)
        }
        SeasonalEffect.FIREFLY -> drawFirefly(center, radius, paint)
        SeasonalEffect.BUBBLE -> drawBubble(center, radius, paint)
    }
}

/** A chip-sized sample of one shape, used instead of emoji on the shape chips. */
@Composable
fun SeasonalEffectGlyph(effect: SeasonalEffect, modifier: Modifier = Modifier) {
    val color = effectColor(effect)
    Canvas(modifier = modifier) {
        // The glyph is drawn a little smaller than a full particle so the
        // snowflake's arms and the clover's stem still fit inside the canvas.
        drawSeasonalParticle(
            effect = effect,
            center = center,
            radius = size.minDimension * 0.32f,
            rotationDegrees = 0f,
            alpha = 1f,
            color = color,
            path = Path()
        )
    }
}

private fun DrawScope.drawSnowflake(center: Offset, radius: Float, color: Color) {
    val stroke = radius * 0.16f
    // Branches are only legible past a few pixels; below that the six arms alone
    // read as a flake and the extra twelve lines would be invisible weight.
    val withBranches = radius >= 5f
    for (arm in 0 until 6) {
        val angle = arm * (PI.toFloat() / 3f)
        val dx = cos(angle)
        val dy = sin(angle)
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x + dx * radius, center.y + dy * radius),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        if (!withBranches) continue
        val joint = Offset(center.x + dx * radius * 0.62f, center.y + dy * radius * 0.62f)
        for (side in SNOW_BRANCH_SIDES) {
            val branch = angle + side * 0.66f
            drawLine(
                color = color,
                start = joint,
                end = Offset(
                    joint.x + cos(branch) * radius * 0.34f,
                    joint.y + sin(branch) * radius * 0.34f
                ),
                strokeWidth = stroke * 0.8f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun buildSakura(path: Path, center: Offset, radius: Float) {
    path.reset()
    path.moveTo(center.x, center.y + radius)
    path.cubicTo(
        center.x - radius * 1.05f, center.y + radius * 0.42f,
        center.x - radius * 0.82f, center.y - radius * 0.62f,
        center.x - radius * 0.30f, center.y - radius * 0.92f
    )
    // The notch at the tip is what says "petal" rather than "leaf".
    path.lineTo(center.x, center.y - radius * 0.52f)
    path.lineTo(center.x + radius * 0.30f, center.y - radius * 0.92f)
    path.cubicTo(
        center.x + radius * 0.82f, center.y - radius * 0.62f,
        center.x + radius * 1.05f, center.y + radius * 0.42f,
        center.x, center.y + radius
    )
    path.close()
}

private fun DrawScope.drawClover(center: Offset, radius: Float, color: Color) {
    val leaf = radius * 0.54f
    val offset = radius * 0.42f
    drawLine(
        color = color,
        start = Offset(center.x, center.y + offset),
        end = Offset(center.x, center.y + radius * 1.15f),
        strokeWidth = radius * 0.15f,
        cap = StrokeCap.Round
    )
    // Four leaves, unrolled: an array of offsets here would be four allocations
    // per clover per frame, and a sky can hold sixty-four clovers.
    drawCircle(color = color, radius = leaf, center = Offset(center.x - offset, center.y - offset))
    drawCircle(color = color, radius = leaf, center = Offset(center.x + offset, center.y - offset))
    drawCircle(color = color, radius = leaf, center = Offset(center.x - offset, center.y + offset))
    drawCircle(color = color, radius = leaf, center = Offset(center.x + offset, center.y + offset))
}

private fun buildMaple(path: Path, center: Offset, radius: Float) {
    path.reset()
    val points = 5
    val step = PI.toFloat() / points
    for (index in 0 until points * 2) {
        val lobe = index % 2 == 0
        val reach = if (lobe) radius else radius * 0.42f
        val angle = -PI.toFloat() / 2f + index * step
        val x = center.x + cos(angle) * reach
        // Squashed slightly so the five points read as a leaf rather than a star.
        val y = center.y + sin(angle) * reach * 0.94f
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

private fun DrawScope.drawFirefly(center: Offset, radius: Float, color: Color) {
    // Three rings rather than a blur: a radial gradient per firefly per frame is a
    // shader allocation inside the loop. Stacked translucent circles read the same
    // at this size and cost three fills.
    drawCircle(color = color.copy(alpha = color.alpha * 0.16f), radius = radius * 2.6f, center = center)
    drawCircle(color = color.copy(alpha = color.alpha * 0.30f), radius = radius * 1.55f, center = center)
    drawCircle(color = color.copy(alpha = color.alpha * 0.85f), radius = radius * 0.72f, center = center)
    drawCircle(
        color = Color.White.copy(alpha = color.alpha * 0.75f),
        radius = radius * 0.24f,
        center = center
    )
}

private fun DrawScope.drawBubble(center: Offset, radius: Float, color: Color) {
    drawCircle(color = color.copy(alpha = color.alpha * 0.16f), radius = radius, center = center)
    drawCircle(
        color = color.copy(alpha = color.alpha * 0.85f),
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.16f)
    )
    drawCircle(
        color = Color.White.copy(alpha = color.alpha * 0.7f),
        radius = radius * 0.2f,
        center = Offset(center.x - radius * 0.34f, center.y - radius * 0.38f)
    )
}

/** Folds a value into `0 until 1`, including a negative one. */
private fun wrap(value: Float): Float {
    val wrapped = value % 1f
    return if (wrapped < 0f) wrapped + 1f else wrapped
}
