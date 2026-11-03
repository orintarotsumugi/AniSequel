package com.anisequel.ui.components.seasonal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_PILE_DEPTH
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_WIND_STRENGTH
import com.anisequel.domain.seasonal.MAX_SEASONAL_PILE_DP
import com.anisequel.domain.seasonal.ParticleDirection
import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.SeasonalParticle
import com.anisequel.domain.seasonal.SeasonalPile
import com.anisequel.domain.seasonal.particleSizeDp
import com.anisequel.domain.seasonal.particleSpeedScale
import com.anisequel.domain.seasonal.pileDepthScale
import com.anisequel.domain.seasonal.seasonalParticles
import com.anisequel.domain.seasonal.windStrengthScale
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.rememberIsResumed
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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

/** How much of the layer's width a full gust from one side can displace a shape. */
private const val GUST_WIDTH = 0.16f

/** How much of the layer's height a full vertical gust can displace a shape. */
private const val GUST_HEIGHT = 0.06f

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
 * it is pinned by a test. The gestures that *do* move this sky are observed by
 * [rememberSeasonalAmbientModifier] on the app's root, where a parent observer can
 * see them without competing with the page.
 *
 * ## What 1.8.1 added
 *
 * Three things that all read the same clock:
 *
 * - **Variants.** Each particle carries a [SeasonalParticle.variant], so a sky is
 *   a mix of three snowflakes or three oak leaves rather than one stamp.
 * - **Settling.** When [settle] is on and the shape can land, each cycle that
 *   wraps at the bottom deposits mass into a [SeasonalPile]. The drift is drawn
 *   from the same shape code, so the things lying on it are the things falling.
 * - **Forces.** A gust from scrolling shifts and tilts the sky, and a tap sends a
 *   radial pulse out from the finger. Both are gated by [reactToWind], because a
 *   reader who turned interaction off should not be able to shake the decoration.
 */
@Composable
fun SeasonalParticleLayer(
    effect: SeasonalEffect,
    density: Float,
    sizeScale: Float = 1f,
    speedScale: Float = 1f,
    settle: Boolean = true,
    pileDepth: Float = DEFAULT_SEASONAL_PILE_DEPTH,
    reactToWind: Boolean = true,
    windStrength: Float = DEFAULT_SEASONAL_WIND_STRENGTH,
    /**
     * Whether the drift sits on the app's main bar rather than the layer's edge.
     *
     * True for the overlay, false for the Settings preview: the preview is a card
     * in the middle of a scrolling page, and inheriting the bar's height there
     * lifted its little drift by a bar that is not under it.
     */
    settleOnChrome: Boolean = true,
    /**
     * Whether a tap draws its ripple in this layer.
     *
     * True for the overlay, false for the Settings preview: the tap position is
     * normalised against the whole screen, and drawing that ring inside a card in
     * the middle of a scrolling page puts it somewhere the reader did not touch.
     */
    showTapRipple: Boolean = true,
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
    val depth = remember(pileDepth) { pileDepthScale(pileDepth) }
    val strongWind = remember(windStrength) { windStrengthScale(windStrength) }

    var clock by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    // Switching the wind must clear the gust it was carrying, in both
    // directions. The ambient observers keep running (they have to, or the next
    // gesture after switching it back on would not be seen), so without this the
    // sky would snap to a stale lean the moment the switch returned.
    LaunchedEffect(reactToWind) {
        SeasonalWind.reset()
    }

    // The drift is keyed on the shape and the seed - deliberately not on the
    // depth slider or the window size. Depth is a drawing multiplier on a
    // normalised field, and the field is normalised so a rotation or a keyboard
    // resize has no reason to throw away what has already piled up.
    val pile = remember(effect, settle, seed) {
        if (!settle || !effect.settles) {
            null
        } else {
            SeasonalPile(seed = seed + effect.ordinal * 97 + 17)
        }
    }

    LaunchedEffect(
        animating, effect, density, speed, settle, reactToWind, windStrength, viewport
    ) {
        if (!animating) return@LaunchedEffect
        seasonalClock { step, frameNanos ->
            val previous = clock
            clock += step
            if (reactToWind) SeasonalWind.decay(step, frameNanos)
            val field = pile ?: return@seasonalClock
            settlePile(
                pile = field,
                particles = particles,
                effect = effect,
                speedScale = speed,
                previousSeconds = previous,
                seconds = clock,
                viewportWidth = viewport.width,
                wind = thisWind(reactToWind),
                windStrength = strongWind
            )
        }
    }

    val falling = effect.direction == ParticleDirection.FALL
    val settleVisible = settle && effect.settles && pile != null

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
    ) {
        // `clock` is read here, in the draw phase, and nowhere above it: the whole
        // point of the layer is that a frame costs a repaint rather than a
        // recomposition of the page behind it.
        drawSeasonalSky(
            effect = effect,
            particles = particles,
            pile = pile,
            baseDp = baseDp,
            seconds = clock,
            speedScale = speed,
            falling = falling,
            settleVisible = settleVisible,
            settleOnChrome = settleOnChrome,
            showTapRipple = showTapRipple,
            wind = thisWind(reactToWind),
            windStrength = strongWind,
            pileDepthScale = depth
        )
    }
}

/** The wind's contribution to one frame, or nothing when interaction is off. */
private class WindSample(val gust: Float, val lift: Float, val pulse: Float) {
    companion object {
        val None = WindSample(0f, 0f, 0f)
    }
}

/**
 * The three wind values, read together.
 *
 * Read once per frame rather than at each place they are used: the draw, the drift's
 * shear and the settling all ask the same question, and four separate
 * `if (reactToWind)` is four chances for the sky and the pile to disagree about which
 * gust they are in - which shows up as the drift leaning while the flakes above it do
 * not.
 */
private fun thisWind(reactToWind: Boolean): WindSample =
    if (!reactToWind) {
        WindSample.None
    } else {
        WindSample(SeasonalWind.gust, SeasonalWind.lift, SeasonalWind.pulse)
    }

/**
 * Deposits the shapes whose cycle wrapped during this step.
 *
 * The wrap is the only moment a falling shape visibly reaches the floor, so it is the
 * only moment it adds to the drift: depositing every step would build a pile out of
 * particles still mid-air. A rising shape has no floor to land on, and a layer that
 * has not been measured yet has no width to land *across*, so both leave the field
 * untouched.
 */
private fun settlePile(
    pile: SeasonalPile,
    particles: List<SeasonalParticle>,
    effect: SeasonalEffect,
    speedScale: Float,
    previousSeconds: Float,
    seconds: Float,
    viewportWidth: Int,
    wind: WindSample,
    windStrength: Float
) {
    if (viewportWidth <= 0 || effect.direction != ParticleDirection.FALL) return
    // The same strength multiplier the draw uses, so a landing lands where the
    // reader saw the shape cross the edge.
    val gust = wind.gust * windStrength
    particles.forEach { particle ->
        val previousProgress = wrap(particle.y + particle.speed * speedScale * previousSeconds)
        val nextProgress = wrap(particle.y + particle.speed * speedScale * seconds)
        if (nextProgress < previousProgress) {
            val sway = sin(seconds * particle.swayRate * TAU + particle.phase) * particle.sway
            val landing = particle.x + sway + gust * effect.windResponse * GUST_WIDTH
            pile.deposit(landing.coerceIn(0f, 1f), particle.size)
        }
    }
}

/**
 * One frame of sky, drawn in the layer's own space: the drift, the shapes, the ring.
 *
 * The two paths live here rather than at the call site because they are scratch
 * space for this frame only - a fresh `Path` per particle per frame is sixty-four
 * allocations sixty times a second - and the pile's resting glyphs need a second one,
 * because drawing a shape resets the path it is handed.
 */
private fun DrawScope.drawSeasonalSky(
    effect: SeasonalEffect,
    particles: List<SeasonalParticle>,
    pile: SeasonalPile?,
    baseDp: Float,
    seconds: Float,
    speedScale: Float,
    falling: Boolean,
    settleVisible: Boolean,
    settleOnChrome: Boolean,
    showTapRipple: Boolean,
    wind: WindSample,
    windStrength: Float,
    pileDepthScale: Float
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val basePx = baseDp.dp.toPx()
    val path = Path()
    val glyphPath = Path()

    if (settleVisible && pile != null) {
        drawSeasonalPile(
            effect = effect,
            pile = pile,
            maxHeightPx = (MAX_SEASONAL_PILE_DP * pileDepthScale).dp.toPx(),
            shear = wind.gust * windStrength,
            alpha = 1f,
            bottomInsetPx = if (settleOnChrome) SeasonalChrome.barHeightPx.toFloat() else 0f,
            path = path,
            glyphPath = glyphPath
        )
    }

    particles.forEach { particle ->
        val placed = particlePlacement(
            particle = particle,
            effect = effect,
            seconds = seconds,
            speedScale = speedScale,
            width = width,
            height = height,
            falling = falling,
            gust = wind.gust,
            lift = wind.lift,
            windStrength = windStrength,
            pulse = wind.pulse,
            pulseX = SeasonalWind.pulseX,
            pulseY = SeasonalWind.pulseY
        ) ?: return@forEach
        drawSeasonalParticle(
            effect = effect,
            center = placed.center,
            radius = basePx * particle.size,
            rotationDegrees = placed.rotationDegrees,
            alpha = placed.alpha,
            variant = particle.variant,
            path = path,
            twinkle = placed.twinkle
        )
    }

    if (showTapRipple && wind.pulse > 0.01f) {
        drawTapRipple(
            effect = effect,
            pulse = wind.pulse,
            xFraction = SeasonalWind.pulseX,
            yFraction = SeasonalWind.pulseY
        )
    }
}

/**
 * A soft ring and two rings of colour leaving the point the reader touched.
 *
 * The particle displacement already answers a tap; the ring is what tells the
 * reader *why* the sky moved. It fades with the same pulse that pushes the
 * particles, so the two cannot drift apart.
 */
private fun DrawScope.drawTapRipple(
    effect: SeasonalEffect,
    pulse: Float,
    xFraction: Float,
    yFraction: Float
) {
    val palette = particlePalette(effect, 0)
    val age = (1f - pulse).coerceIn(0f, 1f)
    val center = Offset(xFraction * size.width, yFraction * size.height)
    val radius = size.minDimension * (0.05f + age * 0.30f)
    val fade = pulse * 0.38f
    drawCircle(
        color = palette.light.copy(alpha = fade * 0.7f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.5f + age * 2.4f)
    )
    drawCircle(
        color = palette.base.copy(alpha = fade * 0.35f),
        radius = radius * 0.68f,
        center = center,
        style = Stroke(width = 1.1f)
    )
}

/**
 * The frame loop, paced.
 *
 * A step is only handed on once [FRAME_SECONDS] has accumulated, so a 120Hz display
 * drives the sky at the same rate as a 60Hz one, and a step is capped at
 * [MAX_STEP_SECONDS] so a stall - returning from a pause, a breakpoint, a cold
 * first frame - moves the particles a little rather than flinging them all the way
 * across the screen. The raw frame timestamp goes with the step because the wind
 * has to decay exactly once per frame even when two layers are alive.
 */
private suspend fun seasonalClock(onStep: (step: Float, frameNanos: Long) -> Unit) {
    var lastFrame = 0L
    var accumulated = 0f
    while (true) {
        val frame = withFrameNanos { it }
        val elapsed = if (lastFrame == 0L) 0L else frame - lastFrame
        lastFrame = frame
        accumulated += elapsed / 1_000_000_000f
        if (accumulated < FRAME_SECONDS) continue
        onStep(accumulated.coerceAtMost(MAX_STEP_SECONDS), frame)
        accumulated = 0f
    }
}

/** Where one particle sits, how visible it is, and how bright it twinkles. */
private data class Placement(
    val center: Offset,
    val alpha: Float,
    val rotationDegrees: Float,
    val twinkle: Float
)

/**
 * One particle's place at [seconds], or null when it has faded past drawing.
 *
 * The travel is one cycle taller than the screen ([TRAVEL_FACTOR]) and starts
 * [TRAVEL_MARGIN] outside it, so a flake is never born or killed on screen - it
 * enters from above, and the [EDGE_FADE] band at each end of the cycle is what the
 * reader actually sees. Rising shapes run the same geometry backwards.
 *
 * Wind is layered on top of the closed-form path rather than integrated into it:
 * the gust is an offset that decays to zero, so the sky leans and comes back
 * without having to remember where every flake was. That is also what keeps a
 * paused-and-resumed sky in the right place.
 *
 * Pure, and outside the draw lambda, because the loop that calls it is the layer's
 * whole cost: leaving the arithmetic inline made the composable the most branchy
 * function in the app for a computation with no decisions in it.
 */
private fun particlePlacement(
    particle: SeasonalParticle,
    effect: SeasonalEffect,
    seconds: Float,
    speedScale: Float,
    width: Float,
    height: Float,
    falling: Boolean,
    gust: Float,
    lift: Float,
    windStrength: Float,
    pulse: Float,
    pulseX: Float,
    pulseY: Float
): Placement? {
    val travel = height * TRAVEL_FACTOR
    val startY = if (falling) -height * TRAVEL_MARGIN else height * (1f + TRAVEL_MARGIN)
    val progress = wrap(particle.y + particle.speed * speedScale * seconds)
    var y = if (falling) startY + progress * travel else startY - progress * travel
    var x = width * particle.x +
        sin(seconds * particle.swayRate * TAU + particle.phase) * particle.sway * width

    // A leaf catches more air than a flake, and a firefly barely notices. The
    // multiplier comes from the shape so this loop has no per-shape branches.
    val drift = effect.windResponse * windStrength
    x += gust * drift * width * GUST_WIDTH
    y -= lift * drift * height * GUST_HEIGHT

    if (pulse > 0.01f && windStrength > 0.01f) {
        val originX = pulseX * width
        val originY = pulseY * height
        val dx = x - originX
        val dy = y - originY
        val distance = sqrt(dx * dx + dy * dy)
        val reach = width * 0.42f
        if (distance < reach) {
            val falloff = 1f - distance / reach
            val push = falloff * falloff * pulse * windStrength * width * 0.085f
            val safeDistance = distance.coerceAtLeast(1f)
            x += dx / safeDistance * push
            y += dy / safeDistance * push
        }
    }

    val edge = min(progress, 1f - progress) / EDGE_FADE
    val alpha = particle.alpha * edge.coerceIn(0f, 1f)
    if (alpha <= MIN_VISIBLE_ALPHA) return null

    val twinkle = 0.72f + 0.28f * sin(seconds * 2.6f + particle.phase)
    return Placement(
        center = Offset(x, y),
        alpha = alpha,
        rotationDegrees = (particle.spin * seconds + particle.phase) * RADIANS_TO_DEGREES +
            gust * drift * 34f,
        twinkle = twinkle
    )
}

/** Folds a value into `0 until 1`, including a negative one. */
private fun wrap(value: Float): Float {
    val wrapped = value % 1f
    return if (wrapped < 0f) wrapped + 1f else wrapped
}
