package com.anisequel.domain.seasonal

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Which way a shape travels across the screen.
 *
 * A snowflake falls; a firefly rises. Encoding it on the effect rather than in
 * the settings means the direction cannot be changed without also changing the
 * shape that implied it - a "rising snow" is not a preference, it is a bug.
 */
enum class ParticleDirection { FALL, RISE }

/**
 * The shapes the seasonal overlay can draw.
 *
 * Six rather than one because the point of a seasonal effect is that it belongs
 * to a time of year, and the app is read in both hemispheres: snow and sakura are
 * two different winters, and a clover in March is not the same sky as a maple
 * leaf in October. Fireflies and bubbles are the two that do not need a season at
 * all - they are the shapes a reader picks because they like them.
 *
 * Deliberately no colours, paths or Compose here. The domain decides *what*
 * exists and *how many*; `SeasonalParticleLayer` decides what it looks like. That
 * keeps this file testable on a plain JVM and keeps a palette change out of the
 * stored preferences.
 */
enum class SeasonalEffect(
    val storageValue: String,
    val displayName: String,
    val blurb: String,
    val direction: ParticleDirection
) {
    SNOW(
        storageValue = "snow",
        displayName = "Snow",
        blurb = "Six-armed flakes drifting down on a cold, quiet sky.",
        direction = ParticleDirection.FALL
    ),
    SAKURA(
        storageValue = "sakura",
        displayName = "Sakura",
        blurb = "Petals tumbling on a warm spring breeze.",
        direction = ParticleDirection.FALL
    ),
    CLOVER(
        storageValue = "clover",
        displayName = "Clover",
        blurb = "Four-leaf clovers for the luckiest season of the year.",
        direction = ParticleDirection.FALL
    ),
    MAPLE(
        storageValue = "maple",
        displayName = "Maple",
        blurb = "Autumn leaves turning as they fall.",
        direction = ParticleDirection.FALL
    ),
    FIREFLY(
        storageValue = "firefly",
        displayName = "Fireflies",
        blurb = "Warm sparks rising out of a summer evening.",
        direction = ParticleDirection.RISE
    ),
    BUBBLE(
        storageValue = "bubble",
        displayName = "Bubbles",
        blurb = "Soft bubbles floating up and away.",
        direction = ParticleDirection.RISE
    );

    companion object {
        val DEFAULT = SNOW

        /**
         * Reads a stored name, falling back rather than throwing.
         *
         * A preference written by a newer build, or a hand-edited file, must not
         * crash the app that reads it. Unknown values land on the shipped shape.
         */
        fun fromStorage(value: String?): SeasonalEffect =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/** Below this, even the lightest sky looks empty. */
const val MIN_SEASONAL_PARTICLES = 8

/**
 * The hard ceiling, per layer.
 *
 * The app draws two of these at once while the Settings page is open - the page's
 * own preview and the overlay behind it - so a per-layer cap of 64 is 128 drawn
 * shapes at the worst setting. Higher counts stop reading as snow and start
 * reading as a texture, and the cost is a path per flake per frame.
 */
const val MAX_SEASONAL_PARTICLES = 64

const val MIN_SEASONAL_DENSITY = 0f
const val MAX_SEASONAL_DENSITY = 1f
// On the slider's grid, not merely in its range: the density slider is 0..1 with
// seven steps, so its stops are 0.125 apart, and a default of 0.45 opens the page
// with the thumb between two of them - it jumps to 0.5 on the first touch.
const val DEFAULT_SEASONAL_DENSITY = 0.5f

/** The scale the flake, petal or bubble is drawn at, relative to its base size. */
const val MIN_SEASONAL_SIZE_SCALE = 0.5f
const val MAX_SEASONAL_SIZE_SCALE = 2f
const val DEFAULT_SEASONAL_SIZE_SCALE = 1f

/** How fast a particle travels, relative to the speed its shape asks for. */
const val MIN_SEASONAL_SPEED_SCALE = 0.4f
const val MAX_SEASONAL_SPEED_SCALE = 2f
const val DEFAULT_SEASONAL_SPEED_SCALE = 1f

/** The size a particle with a scale of 1 is drawn at, in dp. */
const val BASE_SEASONAL_PARTICLE_DP = 14f

/**
 * Everything the seasonal overlay remembers.
 *
 * Off by default. A decorative layer over every page is something a reader asks
 * for, not something an update switches on for them; the Settings page draws a
 * preview precisely so the choice can be made before it covers the app.
 */
data class SeasonalSettings(
    val enabled: Boolean = false,
    val effect: SeasonalEffect = SeasonalEffect.DEFAULT,
    val density: Float = DEFAULT_SEASONAL_DENSITY,
    val sizeScale: Float = DEFAULT_SEASONAL_SIZE_SCALE,
    val speedScale: Float = DEFAULT_SEASONAL_SPEED_SCALE
) {
    /**
     * A repaired copy of these settings.
     *
     * Applied on read as well as on write. A value that is not finite - a `NaN`
     * from a truncated preference file - would otherwise reach the canvas as a
     * `NaN` radius, and Compose throws on that rather than drawing nothing.
     */
    fun sanitised(): SeasonalSettings = copy(
        density = finiteOr(density, DEFAULT_SEASONAL_DENSITY)
            .coerceIn(MIN_SEASONAL_DENSITY, MAX_SEASONAL_DENSITY),
        sizeScale = particleSizeScale(sizeScale),
        speedScale = particleSpeedScale(speedScale)
    )
}

/** One particle's starting conditions. The angles are radians. */
data class SeasonalParticle(
    /** The lateral position, as a fraction of the layer's width. */
    val x: Float,
    /** Where in its travel the particle starts, as a fraction of one cycle. */
    val y: Float,
    /** A multiplier on the base size; 0.55 to 1.35. */
    val size: Float,
    /** Screen-heights per second. */
    val speed: Float,
    /** The sway's amplitude, as a fraction of the layer's width. */
    val sway: Float,
    /** The sway's frequency, in cycles per second. */
    val swayRate: Float,
    /** The starting phase, so no two particles sway in step. */
    val phase: Float,
    /** Radians per second of rotation; negative turns the other way. */
    val spin: Float,
    /** The shape's opacity at full brightness. */
    val alpha: Float
)

/**
 * How many particles a density asks for.
 *
 * Linear in density, which is the only mapping a slider labelled "density" can
 * honestly use: every millimetre of travel adds the same number of flakes. The
 * floor is not zero because zero is the switch's job, and a density slider whose
 * bottom end is indistinguishable from off is a second off switch.
 */
fun particleCountFor(density: Float): Int {
    val clamped = finiteOr(density, DEFAULT_SEASONAL_DENSITY)
        .coerceIn(MIN_SEASONAL_DENSITY, MAX_SEASONAL_DENSITY)
    val span = MAX_SEASONAL_PARTICLES - MIN_SEASONAL_PARTICLES
    return (MIN_SEASONAL_PARTICLES + span * clamped).roundToInt()
}

/** The base size, in dp, for a size multiplier. */
fun particleSizeDp(sizeScale: Float): Float =
    BASE_SEASONAL_PARTICLE_DP * particleSizeScale(sizeScale)

/** A size multiplier, repaired and clamped. */
fun particleSizeScale(sizeScale: Float): Float =
    finiteOr(sizeScale, DEFAULT_SEASONAL_SIZE_SCALE)
        .coerceIn(MIN_SEASONAL_SIZE_SCALE, MAX_SEASONAL_SIZE_SCALE)

/** A speed multiplier, repaired and clamped. */
fun particleSpeedScale(speedScale: Float): Float =
    finiteOr(speedScale, DEFAULT_SEASONAL_SPEED_SCALE)
        .coerceIn(MIN_SEASONAL_SPEED_SCALE, MAX_SEASONAL_SPEED_SCALE)

/**
 * A deterministic sky for a shape and a density.
 *
 * Deterministic on purpose: the overlay is mounted above every page, so a list
 * rebuilt on each recomposition would re-roll every flake the moment something
 * anywhere in the app changed. The same seed has to draw the same sky forever.
 * The layer moves the particles with a clock; it never regenerates them.
 *
 * `density` decides the length of the list, not the look of any one particle, so
 * raising the slider adds flakes to the sky rather than replacing it. That is why
 * the same seed still produces a coherent fall at every density - the first `n`
 * particles are the same list however many there are.
 */
fun seasonalParticles(
    effect: SeasonalEffect,
    density: Float,
    seed: Int = effect.ordinal * 7919 + 104729
): List<SeasonalParticle> {
    val count = particleCountFor(density)
    val random = Random(seed)
    // Rising shapes travel slower: a firefly that crossed the screen in the time
    // a snowflake does would be a spark thrown upwards, not a living light.
    val minSpeed: Float
    val maxSpeed: Float
    when (effect.direction) {
        ParticleDirection.FALL -> {
            minSpeed = 0.07f
            maxSpeed = 0.17f
        }
        ParticleDirection.RISE -> {
            minSpeed = 0.025f
            maxSpeed = 0.075f
        }
    }
    return List(count) {
        SeasonalParticle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = 0.55f + random.nextFloat() * 0.8f,
            speed = minSpeed + random.nextFloat() * (maxSpeed - minSpeed),
            sway = 0.008f + random.nextFloat() * 0.042f,
            swayRate = 0.07f + random.nextFloat() * 0.23f,
            phase = random.nextFloat() * (2f * PI.toFloat()),
            spin = (random.nextFloat() - 0.5f) * 1.6f,
            alpha = 0.55f + random.nextFloat() * 0.45f
        )
    }
}

/**
 * [value] when it is a real number, [fallback] when it is not.
 *
 * `NaN.coerceIn(0f, 1f)` is `NaN`, not a clamped value - every comparison against
 * it is false, so [Float.coerceIn] returns the receiver untouched. That is the
 * trap this exists to close.
 */
internal fun finiteOr(value: Float, fallback: Float): Float =
    if (value.isFinite()) value else fallback
