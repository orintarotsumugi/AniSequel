package com.anisequel.domain.seasonal

import kotlin.math.roundToInt

/**
 * How hard the seasonal sky is allowed to work on this phone.
 *
 * ## Why this is a setting and not a hidden heuristic
 *
 * The overlay sits above every page and paints on every frame it is allowed.
 * A sky tuned for a flagship will hitch on a four-year-old phone, and a sky
 * tuned for that phone looks empty on a flagship. The reader has to be able to
 * pick, and the app has to pick *something* the first time the switch goes on
 * rather than ship the heavy sky to every device and wait for complaints.
 *
 * [SeasonalQuality.AUTO] is the shipped choice. It does not draw; it resolves
 * to a [SeasonalQualityTier] from RAM and core count, which is the cheapest
 * signal that actually tracks whether a phone will enjoy sixty detailed flakes.
 * The Settings page shows the three concrete tiers as well, so Auto is a
 * starting point rather than a lock.
 */
enum class SeasonalQuality(
    val storageValue: String,
    val displayName: String,
    val blurb: String
) {
    AUTO(
        storageValue = "auto",
        displayName = "Auto",
        blurb = "Picked from this phone when you turn the sky on."
    ),
    HIGH(
        storageValue = "high",
        displayName = "High-end",
        blurb = "Full count, full drawings, sixty updates a second."
    ),
    MID(
        storageValue = "mid",
        displayName = "Mid-range",
        blurb = "Fewer flakes, still detailed, a calmer frame budget."
    ),
    LOW(
        storageValue = "low",
        displayName = "Low-end",
        blurb = "A light sky that stays out of the way of the page."
    );

    fun resolve(detected: SeasonalQualityTier): SeasonalQualityTier = when (this) {
        AUTO -> detected
        HIGH -> SeasonalQualityTier.HIGH
        MID -> SeasonalQualityTier.MID
        LOW -> SeasonalQualityTier.LOW
    }

    companion object {
        val DEFAULT = AUTO

        fun fromStorage(value: String?): SeasonalQuality =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT

        fun of(tier: SeasonalQualityTier): SeasonalQuality = when (tier) {
            SeasonalQualityTier.HIGH -> HIGH
            SeasonalQualityTier.MID -> MID
            SeasonalQualityTier.LOW -> LOW
        }
    }
}

/**
 * The numbers a quality actually changes.
 *
 * Kept off the preference enum so Auto can resolve without inventing a fourth
 * set of caps, and so a test can ask "what does mid-range draw?" without
 * constructing a Settings object.
 */
enum class SeasonalQualityTier(
    /** Ceiling on how many particles a density of 1 is allowed to ask for. */
    val particleCap: Int,
    val minParticles: Int,
    /** How often the clock is allowed to step, in updates per second. */
    val frameHz: Int,
    /**
     * Whether snow gets its branches and piles get a dense scatter of glyphs.
     *
     * Low-end skips both: at fourteen dp the extra strokes are the cost, not
     * the silhouette, and a pile of sixteen leaves still reads as a pile.
     */
    val detailedDrawing: Boolean,
    /** Draw a resting glyph every N columns. Smaller is denser. */
    val pileGlyphStep: Int,
    /** Soften neighbouring columns after a landing so mounds spread. */
    val relaxPiles: Boolean
) {
    HIGH(
        particleCap = MAX_SEASONAL_PARTICLES,
        minParticles = MIN_SEASONAL_PARTICLES,
        frameHz = 60,
        detailedDrawing = true,
        pileGlyphStep = 2,
        relaxPiles = true
    ),
    MID(
        particleCap = 40,
        minParticles = MIN_SEASONAL_PARTICLES,
        frameHz = 45,
        detailedDrawing = true,
        pileGlyphStep = 3,
        relaxPiles = true
    ),
    LOW(
        particleCap = 18,
        minParticles = 6,
        frameHz = 30,
        detailedDrawing = false,
        pileGlyphStep = 5,
        relaxPiles = false
    )
}

/**
 * Picks a tier from what the device reports about itself.
 *
 * RAM and core count, not a GPU renderer string: the overlay is fill-rate and
 * path work, and those two numbers are the ones every device actually exposes
 * without a graphics lookup. A missing or zero reading lands on mid-range
 * rather than low-end, because guessing "this phone is weak" from a stub
 * ActivityManager (tests, a weird ROM) would silently degrade a flagship.
 *
 * Thresholds are about the phones this overlay has to live on, not a marketing
 * ladder. 3.5 GB or four cores is the class that hitch on sixty detailed
 * flakes; 7 GB and six cores is the class that does not notice them.
 */
fun seasonalQualityTierForDevice(totalMemMb: Long, cpuCores: Int): SeasonalQualityTier {
    val cores = cpuCores.coerceAtLeast(0)
    val mem = totalMemMb.coerceAtLeast(0L)
    if (mem == 0L) return SeasonalQualityTier.MID
    return when {
        mem < 3500L || cores in 1..4 -> SeasonalQualityTier.LOW
        mem >= 7000L && cores >= 6 -> SeasonalQualityTier.HIGH
        else -> SeasonalQualityTier.MID
    }
}

/** How many particles a density asks for on a given tier. */
fun particleCountFor(density: Float, quality: SeasonalQualityTier): Int {
    val clamped = finiteOr(density, DEFAULT_SEASONAL_DENSITY)
        .coerceIn(MIN_SEASONAL_DENSITY, MAX_SEASONAL_DENSITY)
    val floor = quality.minParticles
    val span = (quality.particleCap - floor).coerceAtLeast(0)
    return (floor + span * clamped).roundToInt()
}
