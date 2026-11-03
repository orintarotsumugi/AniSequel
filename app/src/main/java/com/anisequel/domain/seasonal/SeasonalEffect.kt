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
 * The part of the year a sky belongs to, plus the shapes that belong to none.
 *
 * ## Why this is a group and not the whole setting
 *
 * The overlay used to be a flat list of six shapes, which made "snow" and
 * "bubbles" read as alternatives in the same sense. They are not: three of the
 * shapes are a season, one is a month nobody wants to remember, and two are
 * simply pretty. Grouping them is what lets the page say "this is the winter sky"
 * without pretending the calendar is part of the preference file.
 *
 * The names are the four seasons and [ANYTIME], which is the honest word for a
 * shape that does not take the month personally. No auto-switching: a sky that
 * changed itself in October would remove a choice, not make one.
 */
enum class SeasonalSeason(val displayName: String, val blurb: String) {
    SPRING("Spring", "Petals and luck on a warming breeze"),
    SUMMER("Summer", "Living sparks and seeds in the evening air"),
    AUTUMN("Autumn", "Leaves turning as they come down"),
    WINTER("Winter", "Snow on a cold, quiet sky"),
    ANYTIME("Anytime", "Shapes that do not belong to a month")
}

/**
 * The shapes the seasonal overlay can draw.
 *
 * ## What changed in 1.8.1, and why
 *
 * The first set was six flat silhouettes built from circles and straight arms.
 * At the size a particle is actually drawn - around fourteen dp, often less -
 * a circle clover and a five-point star maple both read as "a shape", not as the
 * thing they name. Each entry now carries the number of drawings it can produce
 * ([variantCount]) so a sky is a mix rather than sixty-four copies of one
 * drawing, and the shapes that settle carry [settles] so the pile layer does not
 * have to keep a second list of "which ones land".
 *
 * Bubbles are gone. They were the one shape with no season, no direction of
 * travel that matched a real thing, and a rim that vanished at small sizes. The
 * shape slots went to leaves - oak and ginkgo beside the maple - and to a
 * dandelion seed, which is the summer sky the bubbles were standing in for.
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
    val season: SeasonalSeason,
    val direction: ParticleDirection,
    /** How many distinct drawings this shape can produce. At least one. */
    val variantCount: Int,
    /**
     * True when the shape can come to rest in the drift at the bottom edge.
     *
     * Snow, petals and leaves land. A firefly, a dandelion seed and a sparkle do
     * not - a pile of lights would be a bug with a nice colour.
     */
    val settles: Boolean,
    /**
     * How much of a gust this shape takes, relative to a paper-light one.
     *
     * A ginkgo fan catches more air than a snowflake; a firefly flies through it.
     * This is a property of the shape, so it lives here rather than in a `when`
     * branch inside the draw loop.
     */
    val windResponse: Float
) {
    SAKURA(
        storageValue = "sakura",
        displayName = "Sakura",
        blurb = "Five-petal blossoms and single petals tumbling on a spring breeze.",
        season = SeasonalSeason.SPRING,
        direction = ParticleDirection.FALL,
        variantCount = 3,
        settles = true,
        windResponse = 1.15f
    ),
    CLOVER(
        storageValue = "clover",
        displayName = "Clover",
        blurb = "Heart-shaped clovers, most of them lucky enough to carry four leaves.",
        season = SeasonalSeason.SPRING,
        direction = ParticleDirection.FALL,
        variantCount = 2,
        settles = true,
        windResponse = 0.72f
    ),
    FIREFLY(
        storageValue = "firefly",
        displayName = "Fireflies",
        blurb = "Warm sparks rising out of a summer evening.",
        season = SeasonalSeason.SUMMER,
        direction = ParticleDirection.RISE,
        variantCount = 2,
        settles = false,
        windResponse = 0.42f
    ),
    DANDELION(
        storageValue = "dandelion",
        displayName = "Dandelion",
        blurb = "Seeds riding a warm updraft, parachutes spread.",
        season = SeasonalSeason.SUMMER,
        direction = ParticleDirection.RISE,
        variantCount = 2,
        settles = false,
        windResponse = 1.3f
    ),
    MAPLE(
        storageValue = "maple",
        displayName = "Maple",
        blurb = "Five-lobed maple leaves turning as they come down.",
        season = SeasonalSeason.AUTUMN,
        direction = ParticleDirection.FALL,
        variantCount = 2,
        settles = true,
        windResponse = 0.95f
    ),
    OAK(
        storageValue = "oak",
        displayName = "Oak",
        blurb = "Round-lobed oak leaves, heavier than the maple's.",
        season = SeasonalSeason.AUTUMN,
        direction = ParticleDirection.FALL,
        variantCount = 3,
        settles = true,
        windResponse = 0.78f
    ),
    GINKGO(
        storageValue = "ginkgo",
        displayName = "Ginkgo",
        blurb = "Golden ginkgo fans, still on their little stems.",
        season = SeasonalSeason.AUTUMN,
        direction = ParticleDirection.FALL,
        variantCount = 2,
        settles = true,
        windResponse = 1.1f
    ),
    SNOW(
        storageValue = "snow",
        displayName = "Snow",
        blurb = "Six-armed flakes - branches, ferns and plates - on a quiet sky.",
        season = SeasonalSeason.WINTER,
        direction = ParticleDirection.FALL,
        variantCount = 3,
        settles = true,
        windResponse = 0.85f
    ),
    STAR(
        storageValue = "star",
        displayName = "Starlight",
        blurb = "Four-point sparkles for a sky that is not on the calendar.",
        season = SeasonalSeason.ANYTIME,
        direction = ParticleDirection.FALL,
        variantCount = 2,
        settles = false,
        windResponse = 0.34f
    );

    companion object {
        val DEFAULT = SNOW

        /**
         * Reads a stored name, falling back rather than throwing.
         *
         * A preference written by a newer build, or a hand-edited file, must not
         * crash the app that reads it. Unknown values land on the shipped shape.
         * This is also where the removed `bubble` value lands: an install that had
         * bubbles selected upgrades to snow rather than to a blank sky.
         */
        fun fromStorage(value: String?): SeasonalEffect =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/** The shapes a season offers, in enum order. */
fun seasonalEffectsIn(season: SeasonalSeason): List<SeasonalEffect> =
    SeasonalEffect.entries.filter { it.season == season }

/**
 * The season a month falls in, for the "use this season's sky" shortcut.
 *
 * [month] is 1-based, the way `LocalDate.monthValue` and `Calendar.MONTH + 1`
 * both count. Clamped rather than validated: a caller passing a strange number
 * should get winter, not an exception from a decoration picker.
 *
 * Northern-hemisphere months on purpose. The app has one calendar and the
 * shortcut is a convenience, not a claim about where the reader lives: anybody
 * in the other hemisphere can pick any shape at any time, which is why this
 * never writes automatically.
 */
fun seasonalSeasonForMonth(month: Int): SeasonalSeason = when (month.coerceIn(1, 12)) {
    3, 4, 5 -> SeasonalSeason.SPRING
    6, 7, 8 -> SeasonalSeason.SUMMER
    9, 10, 11 -> SeasonalSeason.AUTUMN
    else -> SeasonalSeason.WINTER
}

/** The shape a season opens with, used by the "use this season" shortcut. */
fun SeasonalSeason.defaultEffect(): SeasonalEffect = when (this) {
    SeasonalSeason.SPRING -> SeasonalEffect.SAKURA
    SeasonalSeason.SUMMER -> SeasonalEffect.FIREFLY
    SeasonalSeason.AUTUMN -> SeasonalEffect.MAPLE
    SeasonalSeason.WINTER -> SeasonalEffect.SNOW
    SeasonalSeason.ANYTIME -> SeasonalEffect.STAR
}

/** Below this, even the lightest sky looks empty. */
const val MIN_SEASONAL_PARTICLES = 8

/**
 * The hard ceiling, per layer.
 *
 * The app draws two of these at once while the Settings page is open - the page's
 * own preview and the overlay behind it - so a per-layer cap of 64 is 128 drawn
 * shapes at the worst setting. Higher counts stop reading as snow and start
 * reading as a texture, and the cost is a path per flake per frame. The pile adds
 * a fixed few dozen column humps on top of that, not one per particle, so the
 * ceiling did not have to move when settling was added.
 */
const val MAX_SEASONAL_PARTICLES = 64

const val MIN_SEASONAL_DENSITY = 0f
const val MAX_SEASONAL_DENSITY = 1f
// On the slider's grid, not merely in its range: the density slider is 0..1 with
// seven steps, so its stops are 0.125 apart, and a default of 0.45 opens the page
// with the thumb between two of them - it jumps to 0.5 on the first touch.
const val DEFAULT_SEASONAL_DENSITY = 0.5f

/** The scale the flake, petal or leaf is drawn at, relative to its base size. */
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
 * How tall the drift at the bottom edge can grow, in dp, at full pile depth.
 *
 * Before the slider: about two and a half rows of the bar's own height at the
 * heaviest. The drift is decoration and its job is to look like the sky came to
 * rest, not to cover the page - a pile tall enough to reach a card's text is a
 * pile tall enough to hide it.
 */
const val MAX_SEASONAL_PILE_DP = 120f

const val MIN_SEASONAL_PILE_DEPTH = 0.2f
const val MAX_SEASONAL_PILE_DEPTH = 1f
// On the five-stop slider (0.2, 0.4, 0.6, 0.8, 1.0).
const val DEFAULT_SEASONAL_PILE_DEPTH = 0.6f

const val MIN_SEASONAL_WIND_STRENGTH = 0f
const val MAX_SEASONAL_WIND_STRENGTH = 1f
// On the five-stop slider (0, 0.25, 0.5, 0.75, 1).
const val DEFAULT_SEASONAL_WIND_STRENGTH = 0.5f

/**
 * Everything the seasonal overlay remembers.
 *
 * Off by default. A decorative layer over every page is something a reader asks
 * for, not something an update switches on for them; the Settings page draws a
 * preview precisely so the choice can be made before it covers the app.
 *
 * [settle] and [reactToWind] are on by default *within* an effect that is already
 * on: a sky that does not land and does not move is a still picture, and the two
 * switches exist to turn those behaviours off, not to make the reader find them.
 */
data class SeasonalSettings(
    val enabled: Boolean = false,
    val effect: SeasonalEffect = SeasonalEffect.DEFAULT,
    val density: Float = DEFAULT_SEASONAL_DENSITY,
    val sizeScale: Float = DEFAULT_SEASONAL_SIZE_SCALE,
    val speedScale: Float = DEFAULT_SEASONAL_SPEED_SCALE,
    val settle: Boolean = true,
    val pileDepth: Float = DEFAULT_SEASONAL_PILE_DEPTH,
    val reactToWind: Boolean = true,
    val windStrength: Float = DEFAULT_SEASONAL_WIND_STRENGTH
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
        speedScale = particleSpeedScale(speedScale),
        pileDepth = pileDepthScale(pileDepth),
        windStrength = windStrengthScale(windStrength)
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
    val alpha: Float,
    /**
     * Which of the shape's [SeasonalEffect.variantCount] drawings this is.
     *
     * Stored on the particle rather than derived from its index so raising the
     * density keeps the variants the sky already had - the first `n` particles
     * are still the same list.
     */
    val variant: Int
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

/** The drift's depth, repaired and clamped. */
fun pileDepthScale(pileDepth: Float): Float =
    finiteOr(pileDepth, DEFAULT_SEASONAL_PILE_DEPTH)
        .coerceIn(MIN_SEASONAL_PILE_DEPTH, MAX_SEASONAL_PILE_DEPTH)

/** How hard the sky takes a gust, repaired and clamped. */
fun windStrengthScale(windStrength: Float): Float =
    finiteOr(windStrength, DEFAULT_SEASONAL_WIND_STRENGTH)
        .coerceIn(MIN_SEASONAL_WIND_STRENGTH, MAX_SEASONAL_WIND_STRENGTH)

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
    // A leaf rocks further than a flake because it is a flat surface falling
    // edge-on; a firefly keeps its own path. The direction's base range is the
    // same, the multiplier is the shape's.
    val swayScale = when (effect) {
        SeasonalEffect.SNOW -> 0.9f
        SeasonalEffect.SAKURA -> 1.3f
        SeasonalEffect.CLOVER -> 0.8f
        SeasonalEffect.MAPLE -> 1.15f
        SeasonalEffect.OAK -> 0.85f
        SeasonalEffect.GINKGO -> 1.2f
        SeasonalEffect.FIREFLY -> 0.65f
        SeasonalEffect.DANDELION -> 1.35f
        SeasonalEffect.STAR -> 0.55f
    }
    return List(count) {
        SeasonalParticle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = 0.55f + random.nextFloat() * 0.8f,
            speed = minSpeed + random.nextFloat() * (maxSpeed - minSpeed),
            sway = (0.008f + random.nextFloat() * 0.042f) * swayScale,
            swayRate = 0.07f + random.nextFloat() * 0.23f,
            phase = random.nextFloat() * (2f * PI.toFloat()),
            spin = (random.nextFloat() - 0.5f) * 1.6f,
            alpha = 0.55f + random.nextFloat() * 0.45f,
            variant = random.nextInt(effect.variantCount.coerceAtLeast(1))
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
