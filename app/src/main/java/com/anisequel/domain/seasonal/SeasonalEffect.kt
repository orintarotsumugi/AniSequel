package com.anisequel.domain.seasonal

import kotlin.math.PI
import kotlin.random.Random

/**
 * The part of the year a sky belongs to.
 *
 * ## Why this is a group and not the whole setting
 *
 * The overlay used to be a flat list of six shapes, which made "snow" and
 * "bubbles" read as alternatives in the same sense. They are not: three of the
 * shapes are a season, one is a month nobody wants to remember, and two are
 * simply pretty. Grouping them is what lets the page say "this is the winter sky"
 * without pretending the calendar is part of the preference file.
 *
 * Four names, because every shape the overlay draws now belongs to one of them.
 * No auto-switching: a sky that changed itself in October would remove a choice,
 * not make one.
 */
enum class SeasonalSeason(val displayName: String, val blurb: String) {
    SPRING("Spring", "Petals and luck on a warming breeze"),
    SUMMER("Summer", "Seeds on the air of a long evening"),
    AUTUMN("Autumn", "Leaves turning as they come down"),
    WINTER("Winter", "Snow on a cold, quiet sky")
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
 * ## What changed in 1.8.2, and why
 *
 * Fireflies and starlight are gone, and nothing rises any more. Both were glow
 * rather than matter: at the size the overlay draws, a firefly is a soft dot with
 * two slivers on it and a sparkle is a dot with four, so neither is a shape the
 * reader can name - and the two of them were the whole reason the layer had a
 * `ParticleDirection` and a second set of speed bands. Removing them deletes a
 * sine per particle per frame (the twinkle only ever drove those two) and leaves
 * one honest motion: everything falls, and what is flat enough to catch air turns
 * over as it comes down, which is [tumbleResponse].
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
    /** How many distinct drawings this shape can produce. At least one. */
    val variantCount: Int,
    /**
     * True when the shape can come to rest in the drift at the bottom edge.
     *
     * Snow, petals and leaves land. A dandelion seed does not: it arrives with its
     * parachute spread, and a drift of them reads as a mistake rather than as
     * settle. The pile asks this instead of keeping its own list of landers.
     */
    val settles: Boolean,
    /**
     * How much of a gust this shape takes, relative to a paper-light one.
     *
     * A ginkgo fan catches more air than a snowflake. This is a property of the
     * shape, so it lives here rather than in a `when` branch inside the draw loop.
     */
    val windResponse: Float,
    /**
     * How wide the sway is for this shape, as a multiplier on the layer's band.
     *
     * A leaf falls edge-on and rocks; a powder grain barely drifts sideways. Was a
     * twelve-branch `when` in [seasonalParticles], which any new shape had to
     * remember to extend - and a shape that forgot took the default silently.
     */
    val swayResponse: Float,
    /**
     * How far this shape turns over as it falls, 0 for none to 1 for a full flip.
     *
     * A flat leaf caught edge-on shows its underside; a six-armed flake keeps its
     * face to the reader. The layer reads this to squeeze the drawing across its own
     * axis, which is what makes the rotation read as a fall rather than a spin.
     */
    val tumbleResponse: Float
) {
    SAKURA(
        storageValue = "sakura",
        displayName = "Sakura",
        blurb = "Five-petal blossoms and single petals tumbling on a spring breeze.",
        season = SeasonalSeason.SPRING,
        variantCount = 3,
        settles = true,
        windResponse = 1.15f,
        swayResponse = 1.3f,
        tumbleResponse = 0.95f
    ),
    CLOVER(
        storageValue = "clover",
        displayName = "Clover",
        blurb = "Heart-shaped clovers, most of them lucky enough to carry four leaves.",
        season = SeasonalSeason.SPRING,
        variantCount = 2,
        settles = true,
        windResponse = 0.72f,
        swayResponse = 0.8f,
        tumbleResponse = 0.7f
    ),
    DANDELION(
        storageValue = "dandelion",
        displayName = "Dandelion",
        blurb = "Seeds on a long evening, parachutes spread and turning in the air.",
        season = SeasonalSeason.SUMMER,
        variantCount = 2,
        settles = false,
        windResponse = 1.3f,
        swayResponse = 1.35f,
        tumbleResponse = 0.45f
    ),
    MAPLE(
        storageValue = "maple",
        displayName = "Maple",
        blurb = "Five-lobed maple leaves, amber to red, turning as they come down.",
        season = SeasonalSeason.AUTUMN,
        variantCount = 3,
        settles = true,
        windResponse = 0.95f,
        swayResponse = 1.15f,
        tumbleResponse = 1f
    ),
    OAK(
        storageValue = "oak",
        displayName = "Oak",
        blurb = "Round-lobed oak leaves, heavier than the maple's.",
        season = SeasonalSeason.AUTUMN,
        variantCount = 3,
        settles = true,
        windResponse = 0.78f,
        swayResponse = 0.85f,
        tumbleResponse = 0.9f
    ),
    GINKGO(
        storageValue = "ginkgo",
        displayName = "Ginkgo",
        blurb = "Golden ginkgo fans, still on their little stems.",
        season = SeasonalSeason.AUTUMN,
        variantCount = 2,
        settles = true,
        windResponse = 1.1f,
        swayResponse = 1.2f,
        tumbleResponse = 0.85f
    ),
    SNOW(
        storageValue = "snow",
        displayName = "Snow",
        blurb = "A mixed winter sky: dendrites, ferns, plates, needles and powder.",
        season = SeasonalSeason.WINTER,
        variantCount = 5,
        settles = true,
        windResponse = 0.85f,
        swayResponse = 0.95f,
        tumbleResponse = 0.3f
    ),
    POWDER(
        storageValue = "powder",
        displayName = "Powder",
        blurb = "Soft round grains, the snow that falls when the air is still.",
        season = SeasonalSeason.WINTER,
        variantCount = 3,
        settles = true,
        windResponse = 1.08f,
        swayResponse = 0.55f,
        tumbleResponse = 0.15f
    ),
    CRYSTAL(
        storageValue = "crystal",
        displayName = "Crystal",
        blurb = "Large stellar dendrites, each flake its own little window.",
        season = SeasonalSeason.WINTER,
        variantCount = 3,
        settles = true,
        windResponse = 0.92f,
        swayResponse = 1.15f,
        tumbleResponse = 0.35f
    ),
    NEEDLE(
        storageValue = "needle",
        displayName = "Needles",
        blurb = "Thin ice columns tumbling end over end.",
        season = SeasonalSeason.WINTER,
        variantCount = 2,
        settles = true,
        windResponse = 0.62f,
        swayResponse = 0.7f,
        tumbleResponse = 0.7f
    );

    companion object {
        val DEFAULT = SNOW

        /**
         * Reads a stored name, falling back rather than throwing.
         *
         * A preference written by a newer build, or a hand-edited file, must not
         * crash the app that reads it. Unknown values land on the shipped shape.
         * This is also where the removed `bubble`, `firefly` and `star` values
         * land: an install that had any of them selected upgrades to snow rather
         * than to a blank sky, because there is no successor shape that is a
         * substitute for all three.
         */
        fun fromStorage(value: String?): SeasonalEffect =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/** The shapes a season offers, in enum order. */
fun seasonalEffectsIn(season: SeasonalSeason): List<SeasonalEffect> =
    SeasonalEffect.entries.filter { it.season == season }

/** True for every winter flake: mixed snow, powder, crystals and needles. */
val SeasonalEffect.isWinterSnow: Boolean
    get() = season == SeasonalSeason.WINTER

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
    SeasonalSeason.SUMMER -> SeasonalEffect.DANDELION
    SeasonalSeason.AUTUMN -> SeasonalEffect.MAPLE
    SeasonalSeason.WINTER -> SeasonalEffect.SNOW
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
 * [settle] and [reactToWind] are off until asked for. A drift along the bottom
 * of every page and a sky that leans on every scroll are the two things that
 * change how the app itself feels, not just how pretty it is; they stay behind
 * a switch so turning the overlay on is still only "shapes in the air".
 *
 * [quality] starts at Auto. The first time the overlay is switched on, Settings
 * writes the tier this phone actually is, so a low-end device never spends a
 * session on the flagship sky by accident.
 */
data class SeasonalSettings(
    val enabled: Boolean = false,
    val effect: SeasonalEffect = SeasonalEffect.DEFAULT,
    val density: Float = DEFAULT_SEASONAL_DENSITY,
    val sizeScale: Float = DEFAULT_SEASONAL_SIZE_SCALE,
    val speedScale: Float = DEFAULT_SEASONAL_SPEED_SCALE,
    val settle: Boolean = false,
    val pileDepth: Float = DEFAULT_SEASONAL_PILE_DEPTH,
    val reactToWind: Boolean = false,
    val windStrength: Float = DEFAULT_SEASONAL_WIND_STRENGTH,
    val quality: SeasonalQuality = SeasonalQuality.DEFAULT
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
    val variant: Int,
    /**
     * How much extra flutter this particle takes.
     *
     * Larger leaves and dendrites rock; powder barely does. Stored so raising
     * the density keeps the motion the sky already had, the same way [variant]
     * does.
     */
    val flutter: Float
)

/**
 * How many particles a density asks for on the top quality tier.
 *
 * Linear in density, which is the only mapping a slider labelled "density" can
 * honestly use: every millimetre of travel adds the same number of flakes. The
 * floor is not zero because zero is the switch's job, and a density slider whose
 * bottom end is indistinguishable from off is a second off switch.
 *
 * Delegates to the tiered overload rather than repeating the arithmetic: the two
 * formulas were already one edit apart from disagreeing about the default sky.
 */
fun particleCountFor(density: Float): Int = particleCountFor(density, SeasonalQualityTier.HIGH)

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
    seed: Int = effect.ordinal * 7919 + 104729,
    quality: SeasonalQualityTier = SeasonalQualityTier.HIGH
): List<SeasonalParticle> {
    val count = particleCountFor(density, quality)
    val random = Random(seed)
    // Screen-heights per second. Most shapes share one band and the four whose
    // mass-to-area ratio makes them a different animal name their own: a dandelion
    // seed that crossed the screen as fast as a snowflake is a thrown seed, and a
    // crystal that matched a flake would be the biggest thing in the sky landing
    // first.
    val minSpeed: Float
    val maxSpeed: Float
    when (effect) {
        SeasonalEffect.DANDELION -> {
            minSpeed = 0.028f
            maxSpeed = 0.062f
        }
        SeasonalEffect.POWDER -> {
            minSpeed = 0.10f
            maxSpeed = 0.20f
        }
        SeasonalEffect.CRYSTAL -> {
            minSpeed = 0.045f
            maxSpeed = 0.12f
        }
        SeasonalEffect.NEEDLE -> {
            minSpeed = 0.08f
            maxSpeed = 0.16f
        }
        else -> {
            minSpeed = 0.07f
            maxSpeed = 0.17f
        }
    }
    val minSize: Float
    val sizeSpan: Float
    when (effect) {
        SeasonalEffect.POWDER -> {
            minSize = 0.50f
            sizeSpan = 0.42f
        }
        SeasonalEffect.CRYSTAL -> {
            minSize = 0.78f
            sizeSpan = 0.56f
        }
        SeasonalEffect.NEEDLE -> {
            minSize = 0.52f
            sizeSpan = 0.58f
        }
        SeasonalEffect.DANDELION -> {
            minSize = 0.62f
            sizeSpan = 0.60f
        }
        else -> {
            minSize = 0.55f
            sizeSpan = 0.80f
        }
    }
    // A flat shape turns over as it falls, so the spin band is widest for the ones
    // that show an underside. Capped so the result stays inside the range the
    // particles are pinned to.
    val spinSpan = 0.8f + effect.tumbleResponse * 0.9f
    return List(count) {
        // Depth is the first number drawn, and the other three follow it. A sky used to
        // roll size, speed and opacity independently, so a flake could be the largest
        // thing on screen and the slowest - a big near flake drifting like a far one.
        // Tying them together costs nothing and gives the field a front and a back: the
        // large flakes fall faster and brighter, the small ones hang back and fade.
        val sizeT = random.nextFloat()
        val size = minSize + sizeT * sizeSpan
        val speedT = (sizeT * 0.68f + random.nextFloat() * 0.32f).coerceIn(0f, 1f)
        SeasonalParticle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = size,
            speed = minSpeed + speedT * (maxSpeed - minSpeed),
            sway = (0.008f + random.nextFloat() * 0.042f) * effect.swayResponse,
            swayRate = 0.07f + random.nextFloat() * 0.23f,
            phase = random.nextFloat() * (2f * PI.toFloat()),
            spin = (random.nextFloat() - 0.5f) * spinSpan,
            // Never below 0.55: a flake that fades to nothing at the near edge of the
            // field is invisible, and the depth cue is already carried by size and speed.
            alpha = (0.58f + sizeT * 0.36f + (random.nextFloat() - 0.5f) * 0.06f)
                .coerceIn(0.55f, 1f),
            variant = random.nextInt(effect.variantCount.coerceAtLeast(1)),
            flutter = 0.45f + random.nextFloat() * 0.85f
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
