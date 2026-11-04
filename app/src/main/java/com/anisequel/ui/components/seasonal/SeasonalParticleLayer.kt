package com.anisequel.ui.components.seasonal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.SeasonalParticle
import com.anisequel.domain.seasonal.SeasonalPile
import com.anisequel.domain.seasonal.SeasonalQualityTier
import com.anisequel.domain.seasonal.isWinterSnow
import com.anisequel.domain.seasonal.particleSizeDp
import com.anisequel.domain.seasonal.particleSpeedScale
import com.anisequel.domain.seasonal.pileDepthScale
import com.anisequel.domain.seasonal.seasonalParticles
import com.anisequel.domain.seasonal.windStrengthScale
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.rememberIsResumed
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The seed every layer shares, so the sky is the same on every launch. */
private const val SEASONAL_SEED = 20261103

private const val TAU = 6.2831855f

/** One cycle of travel is a little taller than the screen, so nothing pops. */
private const val TRAVEL_FACTOR = 1.3f
private const val TRAVEL_MARGIN = 0.15f

/** The band at each end of a particle's travel over which it fades in or out. */
private const val EDGE_FADE = 0.08f

/** Below this a flake is a rounding error on the background, not a drawn shape. */
private const val MIN_VISIBLE_ALPHA = 0.01f

/** How many times faster than the sway a shape rolls over: one roll is two sways. */
private const val TUMBLE_RATE = 1.4f

/** The narrowest a turning shape is ever drawn, as a fraction of its own width. */
private const val MIN_FACE_RATIO = 0.24f

/** How much of its light a shape keeps once it has turned onto its back. */
private const val UNDER_FACE_LIGHT = 0.78f

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
    settle: Boolean = false,
    pileDepth: Float = DEFAULT_SEASONAL_PILE_DEPTH,
    reactToWind: Boolean = false,
    windStrength: Float = DEFAULT_SEASONAL_WIND_STRENGTH,
    quality: SeasonalQualityTier = SeasonalQualityTier.HIGH,
    /**
     * Whether this layer is the app-wide overlay, and may publish the drift to the
     * navigation bar for its snow cap.
     *
     * True for the overlay, false for the Settings preview. The preview is a card in
     * the middle of a scrolling page with no bar under it, and publishing from it
     * would put the preview's drift on a bar it is not part of - or, worse, clear the
     * real overlay's published drift when the Settings page is torn down.
     */
    publishesBarTrail: Boolean = true,
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

    // Regenerated only when the shape, the count or the tier changes, and never on
    // a frame. The seed is fixed, so raising the density adds flakes instead of
    // re-rolling the ones already on screen. The tier goes into the generator
    // rather than truncating afterwards: a low-end sky used to build sixty-four
    // particles and draw eighteen of them.
    val particles = remember(effect, density, seed, quality) {
        seasonalParticles(effect = effect, density = density, seed = seed, quality = quality)
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

    // The one app-wide layer owns the bar's snow cap. Published from composition
    // rather than from the frame loop: the pile only changes when the shape or the
    // switch does, and the trail reads the field's heights directly on every frame.
    if (publishesBarTrail) {
        DisposableEffect(effect, pile) {
            SeasonalChrome.publishSky(effect = effect, pile = pile)
            onDispose { SeasonalChrome.clearSky() }
        }
    }

    LaunchedEffect(
        animating, effect, density, speed, settle, reactToWind, windStrength, viewport, quality
    ) {
        if (!animating) return@LaunchedEffect
        seasonalClock(1f / quality.frameHz) { step, frameNanos ->
            val previous = clock
            clock += step
            if (reactToWind) SeasonalWind.decay(step, frameNanos)
            if (publishesBarTrail) SeasonalChrome.tick(clock)
            val field = pile ?: return@seasonalClock
            val landed = settlePile(
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
            // Only after a landing. Relaxing on every frame smoothed the drift into a
            // flat band a minute in, and spent a full pass over the columns doing it.
            // The pass also compacts, so a low-end sky still runs it with no diffusion
            // - without that it reaches the cap and turns into a white band.
            if (landed > 0) field.relax(amount = if (quality.relaxPiles) 0.16f else 0f)
        }
    }

    val settleVisible = settle && effect.settles && pile != null

    // Scratch space for the frame, held for the life of the layer. Two paths and one
    // placement object per frame used to be a steady trickle of garbage from a draw
    // callback that runs sixty times a second; none of them is allowed to escape the
    // draw, so there is no reason to build them there either.
    val path = remember { Path() }
    val glyphPath = remember { Path() }
    val placement = remember { Placement() }

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
            settleVisible = settleVisible,
            showTapRipple = showTapRipple,
            wind = thisWind(reactToWind),
            windStrength = strongWind,
            pileDepthScale = depth,
            quality = quality,
            path = path,
            glyphPath = glyphPath,
            placement = placement
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
        WindSample(
            gust = (SeasonalWind.gust + SeasonalWind.breeze * 0.62f).coerceIn(-1f, 1f),
            lift = SeasonalWind.lift,
            pulse = SeasonalWind.pulse
        )
    }

/**
 * Deposits the shapes whose cycle wrapped during this step.
 *
 * The wrap is the only moment a falling shape visibly reaches the floor, so it is the
 * only moment it adds to the drift: depositing every step would build a pile out of
 * particles still mid-air. A layer that has not been measured yet has no width to
 * land *across*, so it leaves the field untouched.
 *
 * Returns how many shapes landed, which is what tells the caller whether the drift
 * is worth a smoothing pass this frame.
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
): Int {
    if (viewportWidth <= 0) return 0
    // The same strength multiplier the draw uses, so a landing lands where the
    // reader saw the shape cross the edge.
    val gust = wind.gust * windStrength
    var landed = 0
    particles.forEach { particle ->
        val previousProgress = wrap(particle.y + particle.speed * speedScale * previousSeconds)
        val nextProgress = wrap(particle.y + particle.speed * speedScale * seconds)
        if (nextProgress < previousProgress) {
            val angle = seconds * particle.swayRate * TAU + particle.phase
            val sway = (
                sinWrapped(angle) +
                    sinWrapped(angle * 2.15f) * particle.flutter * 0.42f
                ) * particle.sway
            val drift = effect.windResponse * (0.78f + 0.30f * particle.flutter)
            val landing = particle.x + sway + gust * drift * GUST_WIDTH
            pile.deposit(
                xFraction = landing.coerceIn(0f, 1f),
                mass = particle.size,
                spread = if (effect.isWinterSnow) 3 else 2
            )
            landed++
        }
    }
    return landed
}

/**
 * One frame of sky, drawn in the layer's own space: the drift, the shapes, the ring.
 *
 * The two paths live here rather than at the call site because they are scratch
 * space for this frame only - a fresh `Path` per particle per frame is sixty-four
 * allocations sixty times a second - and the pile's resting glyphs need a second one,
 * because drawing a shape resets the path it is handed.
 *
 * [placement] is the same scratch object for every particle in the frame, filled by
 * [particlePlacement] and read immediately after: the loop used to build one small
 * object per visible flake per frame, which is thirty-six hundred of them a second at
 * the heaviest setting, and they were all garbage by the next one.
 */
private fun DrawScope.drawSeasonalSky(
    effect: SeasonalEffect,
    particles: List<SeasonalParticle>,
    pile: SeasonalPile?,
    baseDp: Float,
    seconds: Float,
    speedScale: Float,
    settleVisible: Boolean,
    showTapRipple: Boolean,
    wind: WindSample,
    windStrength: Float,
    pileDepthScale: Float,
    quality: SeasonalQualityTier,
    path: Path,
    glyphPath: Path,
    placement: Placement
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val basePx = baseDp.dp.toPx()

    if (settleVisible && pile != null) {
        drawSeasonalPile(
            effect = effect,
            pile = pile,
            maxHeightPx = (MAX_SEASONAL_PILE_DP * pileDepthScale).dp.toPx(),
            shear = wind.gust * windStrength,
            alpha = 1f,
            path = path,
            glyphPath = glyphPath,
            glyphStep = quality.pileGlyphStep,
            highDetail = quality.detailedDrawing,
            seconds = seconds
        )
    }

    particles.forEach { particle ->
        particlePlacement(
            particle = particle,
            effect = effect,
            seconds = seconds,
            speedScale = speedScale,
            width = width,
            height = height,
            gust = wind.gust,
            lift = wind.lift,
            windStrength = windStrength,
            pulse = wind.pulse,
            pulseX = SeasonalWind.pulseX,
            pulseY = SeasonalWind.pulseY,
            into = placement
        ) ?: return@forEach
        drawSeasonalParticle(
            effect = effect,
            center = placement.center,
            radius = basePx * particle.size,
            rotationDegrees = placement.rotationDegrees,
            alpha = placement.alpha,
            variant = particle.variant,
            path = path,
            flatten = placement.flatten,
            highDetail = quality.detailedDrawing
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
private suspend fun seasonalClock(
    frameSeconds: Float = FRAME_SECONDS,
    onStep: (step: Float, frameNanos: Long) -> Unit
) {
    var lastFrame = 0L
    var accumulated = 0f
    val budget = frameSeconds.coerceAtLeast(1f / 60f)
    while (true) {
        val frame = withFrameNanos { it }
        val elapsed = if (lastFrame == 0L) 0L else frame - lastFrame
        lastFrame = frame
        accumulated += elapsed / 1_000_000_000f
        if (accumulated < budget) continue
        onStep(accumulated.coerceAtMost(MAX_STEP_SECONDS), frame)
        accumulated = 0f
    }
}

/**
 * Where one particle sits and how it is turned, filled in by [particlePlacement].
 *
 * A class rather than a data class because it is scratch space: the draw loop hands
 * the same instance to every flake in the frame and reads it straight back.
 */
private class Placement {
    var center: Offset = Offset.Zero
    var alpha: Float = 0f
    var rotationDegrees: Float = 0f

    /**
     * The shape's own horizontal squeeze, negative when it has turned past edge-on.
     *
     * This is the rotation the reader actually sees on a leaf: the drawing narrows to
     * its stem axis and comes back mirrored, which is a turn over rather than a spin.
     */
    var flatten: Float = 1f
}

/**
 * One particle's place at [seconds], or null when it has faded past drawing.
 *
 * The travel is one cycle taller than the screen ([TRAVEL_FACTOR]) and starts
 * [TRAVEL_MARGIN] above it, so a flake is never born or killed on screen - it
 * enters from above, and the [EDGE_FADE] band at each end of the cycle is what the
 * reader actually sees.
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
    gust: Float,
    lift: Float,
    windStrength: Float,
    pulse: Float,
    pulseX: Float,
    pulseY: Float,
    into: Placement
): Placement? {
    val travel = height * TRAVEL_FACTOR
    val progress = wrap(particle.y + particle.speed * speedScale * seconds)
    // A little gravity within one fall. The cycle's position is still linear in time -
    // the wrap depends on that - but the drawn height eases in, so a flake covers the
    // top of the screen slightly slower than the bottom. It is a 14% difference at
    // mid-screen, enough that the field does not look like it is on rails; the fade at
    // each end hides the restart.
    val fall = progress * (0.72f + 0.28f * progress)
    var y = fall * travel - height * TRAVEL_MARGIN
    // The sine is evaluated on a phase wrapped into one turn. `seconds` grows for as
    // long as the layer is alive, so every harmonic's argument would otherwise drift
    // into the part of a float where a fraction of a degree is below the representable
    // step. Only the argument is wrapped - the frequency stays the same, which is why
    // the harmonics each wrap their own scaled angle instead of sharing one wrapped
    // value (wrapping once and multiplying would restart the wave every cycle).
    val swayAngle = seconds * particle.swayRate * TAU + particle.phase
    // Primary sway plus a faster, smaller figure-eight. A single sine is a
    // pendulum; the second harmonic is what makes a leaf tumble instead of
    // slide, and a flake drift instead of march.
    var x = width * particle.x +
        sinWrapped(swayAngle) * particle.sway * width +
        sinWrapped(swayAngle * 2.15f) * particle.sway * particle.flutter * width * 0.42f
    y += sinWrapped(swayAngle * 0.85f + 0.6f) * height * 0.014f * particle.flutter

    // A leaf catches more air than a flake. The multiplier comes from the shape so
    // this loop has no per-shape branches; the flutter term makes the gust reach an
    // individual particle slightly differently, so the sky bends as a field rather
    // than every shape sliding by the same number of pixels.
    val drift = effect.windResponse * windStrength * (0.78f + 0.30f * particle.flutter)
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

    // The turn over. A flat shape is drawn squeezed across its own axis as it rolls
    // and mirrored for the second half, so the reader sees the underside come round.
    // [SeasonalEffect.tumbleResponse] decides how far, because a six-armed flake keeps
    // the same face whichever way it drifts. Never squeezed to nothing: a particle
    // that vanishes for a frame reads as a dropped frame, not as an edge-on leaf.
    val roll = sinWrapped(swayAngle * TUMBLE_RATE + particle.phase)
    val face = (1f - effect.tumbleResponse * (1f - abs(roll))).coerceAtLeast(MIN_FACE_RATIO)
    val underside = roll < 0f

    val edge = min(progress, 1f - progress) / EDGE_FADE
    // The far half of a turn is the underside: the same silhouette, less light. The
    // squeeze alone said "turning", but a leaf coming back round was drawn as bright
    // as the face it left, which read as a mirror rather than a back side.
    val alpha = particle.alpha * edge.coerceIn(0f, 1f) *
        if (underside) UNDER_FACE_LIGHT else 1f
    if (alpha <= MIN_VISIBLE_ALPHA) return null

    into.center = Offset(x, y)
    into.alpha = alpha
    into.rotationDegrees = wrap((particle.spin * seconds + particle.phase) / TAU) * 360f +
        gust * drift * 34f +
        sinWrapped(swayAngle * 1.35f) * particle.flutter * 22f
    into.flatten = if (underside) -face else face
    return into
}

/** Folds a value into `0 until 1`, including a negative one. */
private fun wrap(value: Float): Float {
    val wrapped = value % 1f
    return if (wrapped < 0f) wrapped + 1f else wrapped
}

/** `sin` of an angle folded into one turn, so a long uptime cannot erode the wave. */
private fun sinWrapped(angle: Float): Float {
    var turn = angle % TAU
    if (turn < 0f) turn += TAU
    return sin(turn)
}
