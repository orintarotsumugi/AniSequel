package com.anisequel

import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_PILE_DEPTH
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_WIND_STRENGTH
import com.anisequel.domain.seasonal.MAX_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.MAX_SEASONAL_PARTICLES
import com.anisequel.domain.seasonal.MAX_SEASONAL_PILE_DEPTH
import com.anisequel.domain.seasonal.MAX_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.MAX_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.MAX_SEASONAL_WIND_STRENGTH
import com.anisequel.domain.seasonal.MIN_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.MIN_SEASONAL_PARTICLES
import com.anisequel.domain.seasonal.MIN_SEASONAL_PILE_DEPTH
import com.anisequel.domain.seasonal.MIN_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.MIN_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.MIN_SEASONAL_WIND_STRENGTH
import com.anisequel.domain.seasonal.ParticleDirection
import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.SeasonalSeason
import com.anisequel.domain.seasonal.SeasonalSettings
import com.anisequel.domain.seasonal.defaultEffect
import com.anisequel.domain.seasonal.particleCountFor
import com.anisequel.domain.seasonal.particleSizeDp
import com.anisequel.domain.seasonal.particleSizeScale
import com.anisequel.domain.seasonal.particleSpeedScale
import com.anisequel.domain.seasonal.pileDepthScale
import com.anisequel.domain.seasonal.seasonalEffectsIn
import com.anisequel.domain.seasonal.seasonalParticles
import com.anisequel.domain.seasonal.seasonalSeasonForMonth
import com.anisequel.domain.seasonal.windStrengthScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seasonal overlay's pure half: which shapes exist, how a season groups them,
 * how many a density asks for, and that the same seed always draws the same sky.
 *
 * ## The failures this pins
 *
 * The overlay is mounted above every page, so a list regenerated on a
 * recomposition would re-roll every flake the moment anything anywhere in the app
 * changed. That is invisible in review (a random list looks random either way)
 * and obvious in the app (the sky jumps while the reader types).
 *
 * The other trap is a preference file. A `NaN` stored by a truncated write passes
 * `coerceIn` untouched - `NaN` compares false against both bounds - and a `NaN`
 * radius reaches the canvas, where Compose throws rather than drawing nothing. So
 * the clamping here is tested with non-finite values specifically, not just with
 * a number outside the range.
 *
 * Since 1.8.1 the shape list also carries the facts the drawing layer must not
 * duplicate: which season owns a shape, which shapes can settle into a drift, how
 * many drawings each one has, and how much a gust moves it. Those are requirements,
 * not preferences - a firefly in a pile is a bug, and a variant index outside the
 * shape's count is a palette lookup that should never happen.
 */
class SeasonalEffectTest {

    @Test
    fun `every shape survives a storage round trip`() {
        SeasonalEffect.entries.forEach { effect ->
            assertEquals(
                "a shape that cannot read back the value it writes is lost on restart",
                effect,
                SeasonalEffect.fromStorage(effect.storageValue)
            )
        }
    }

    @Test
    fun `an unknown stored shape falls back instead of throwing`() {
        assertEquals(SeasonalEffect.DEFAULT, SeasonalEffect.fromStorage("confetti"))
        assertEquals(SeasonalEffect.DEFAULT, SeasonalEffect.fromStorage(null))
    }

    @Test
    fun `a removed bubble selection becomes the default snow`() {
        // Bubbles shipped in 1.8.0 and were removed in 1.8.1. An install that had
        // them selected must land somewhere readable rather than on a blank sky.
        assertEquals(SeasonalEffect.SNOW, SeasonalEffect.fromStorage("bubble"))
    }

    @Test
    fun `every season owns at least one shape`() {
        SeasonalSeason.entries.forEach { season ->
            assertTrue(
                "$season has no shape, so the season group would render as an empty heading",
                seasonalEffectsIn(season).isNotEmpty()
            )
        }
        assertTrue(seasonalEffectsIn(SeasonalSeason.SPRING).contains(SeasonalEffect.SAKURA))
        assertTrue(seasonalEffectsIn(SeasonalSeason.SPRING).contains(SeasonalEffect.CLOVER))
        assertTrue(seasonalEffectsIn(SeasonalSeason.AUTUMN).contains(SeasonalEffect.MAPLE))
        assertTrue(seasonalEffectsIn(SeasonalSeason.AUTUMN).contains(SeasonalEffect.OAK))
        assertTrue(seasonalEffectsIn(SeasonalSeason.AUTUMN).contains(SeasonalEffect.GINKGO))
        assertEquals(listOf(SeasonalEffect.SNOW), seasonalEffectsIn(SeasonalSeason.WINTER))
        assertEquals(listOf(SeasonalEffect.STAR), seasonalEffectsIn(SeasonalSeason.ANYTIME))
    }

    @Test
    fun `the four seasons and the shapes outside the calendar cover every effect`() {
        val grouped = SeasonalSeason.entries.flatMap { seasonalEffectsIn(it) }
        assertEquals(
            "a shape missing from every group would be unreachable on the settings page",
            SeasonalEffect.entries.size,
            grouped.size
        )
        assertEquals(SeasonalEffect.entries.toSet(), grouped.toSet())
    }

    @Test
    fun `the four seasons have a default shape and it belongs to them`() {
        SeasonalSeason.entries.forEach { season ->
            val fallback = season.defaultEffect()
            assertEquals(
                "$season's default shape is not in $season",
                season,
                fallback.season
            )
        }
        assertEquals(SeasonalEffect.SNOW, SeasonalSeason.WINTER.defaultEffect())
        assertEquals(SeasonalEffect.SAKURA, SeasonalSeason.SPRING.defaultEffect())
        assertEquals(SeasonalEffect.FIREFLY, SeasonalSeason.SUMMER.defaultEffect())
        assertEquals(SeasonalEffect.MAPLE, SeasonalSeason.AUTUMN.defaultEffect())
        assertEquals(SeasonalEffect.STAR, SeasonalSeason.ANYTIME.defaultEffect())
    }

    @Test
    fun `the calendar maps months to the season a reader would name`() {
        assertEquals(SeasonalSeason.WINTER, seasonalSeasonForMonth(1))
        assertEquals(SeasonalSeason.WINTER, seasonalSeasonForMonth(2))
        assertEquals(SeasonalSeason.SPRING, seasonalSeasonForMonth(3))
        assertEquals(SeasonalSeason.SPRING, seasonalSeasonForMonth(5))
        assertEquals(SeasonalSeason.SUMMER, seasonalSeasonForMonth(6))
        assertEquals(SeasonalSeason.SUMMER, seasonalSeasonForMonth(8))
        assertEquals(SeasonalSeason.AUTUMN, seasonalSeasonForMonth(9))
        assertEquals(SeasonalSeason.AUTUMN, seasonalSeasonForMonth(11))
        assertEquals(SeasonalSeason.WINTER, seasonalSeasonForMonth(12))
        // A strange number is clamped rather than thrown out of a decoration.
        assertEquals(SeasonalSeason.WINTER, seasonalSeasonForMonth(-4))
        assertEquals(SeasonalSeason.WINTER, seasonalSeasonForMonth(99))
    }

    @Test
    fun `only falling shapes settle, and the falling set is not empty`() {
        val settling = SeasonalEffect.entries.filter { it.settles }
        assertTrue("nothing can pile up", settling.isNotEmpty())
        settling.forEach { effect ->
            assertEquals(
                "$effect settles but does not fall: a rising shape has no floor to land on",
                ParticleDirection.FALL,
                effect.direction
            )
        }
        // The three that must never produce a drift of lights or seeds.
        listOf(SeasonalEffect.FIREFLY, SeasonalEffect.DANDELION, SeasonalEffect.STAR).forEach {
            assertTrue("$it must not settle", !it.settles)
        }
        assertTrue(SeasonalEffect.SNOW.settles)
        assertTrue(SeasonalEffect.MAPLE.settles)
    }

    @Test
    fun `every shape has at least one variant and particles stay inside that count`() {
        SeasonalEffect.entries.forEach { effect ->
            assertTrue("$effect has no drawing", effect.variantCount >= 1)
            val variants = seasonalParticles(effect, 1f, seed = 21).map { it.variant }.toSet()
            assertTrue(
                "$effect can produce a variant its palette does not know",
                variants.all { it in 0 until effect.variantCount }
            )
            assertEquals(
                "$effect never uses all of its variants, so a variant is dead drawing",
                effect.variantCount,
                variants.size
            )
        }
    }

    @Test
    fun `particle count is bounded at both ends`() {
        assertEquals(MIN_SEASONAL_PARTICLES, particleCountFor(-10f))
        assertEquals(MIN_SEASONAL_PARTICLES, particleCountFor(MIN_SEASONAL_DENSITY))
        assertEquals(MAX_SEASONAL_PARTICLES, particleCountFor(MAX_SEASONAL_DENSITY))
        assertEquals(MAX_SEASONAL_PARTICLES, particleCountFor(10f))
    }

    @Test
    fun `particle count never falls as the slider rises`() {
        var previous = -1
        for (step in 0..100) {
            val count = particleCountFor(step / 100f)
            assertTrue(
                "density $step% asked for $count after $previous - a slider that " +
                    "removes particles when dragged right reads as broken",
                count >= previous
            )
            previous = count
        }
    }

    @Test
    fun `a NaN density is repaired rather than propagated`() {
        val count = particleCountFor(Float.NaN)
        assertTrue(count in MIN_SEASONAL_PARTICLES..MAX_SEASONAL_PARTICLES)
        assertEquals(particleCountFor(DEFAULT_SEASONAL_DENSITY), count)
    }

    @Test
    fun `the list has exactly the count the density asks for`() {
        for (density in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            assertEquals(
                "a density that says one number and draws another is a slider lying",
                particleCountFor(density),
                seasonalParticles(SeasonalEffect.SNOW, density, seed = 11).size
            )
        }
    }

    @Test
    fun `raising the density adds flakes instead of re-rolling the sky`() {
        val sparse = seasonalParticles(SeasonalEffect.SNOW, 0.2f, seed = 99)
        val dense = seasonalParticles(SeasonalEffect.SNOW, 1f, seed = 99)
        assertTrue("the dense sky is larger", dense.size > sparse.size)
        assertEquals(
            "the flakes already on screen must not move when the slider is dragged",
            sparse,
            dense.take(sparse.size)
        )
    }

    @Test
    fun `the same seed draws the same sky`() {
        assertEquals(
            seasonalParticles(SeasonalEffect.SAKURA, 0.6f, seed = 42),
            seasonalParticles(SeasonalEffect.SAKURA, 0.6f, seed = 42)
        )
    }

    @Test
    fun `a different seed draws a different sky`() {
        assertNotEquals(
            seasonalParticles(SeasonalEffect.SAKURA, 0.6f, seed = 42),
            seasonalParticles(SeasonalEffect.SAKURA, 0.6f, seed = 43)
        )
    }

    @Test
    fun `each shape defaults to its own seed`() {
        // Not a guarantee about any one particle - a guarantee that two shapes do
        // not start from the identical random stream and fall in step.
        assertNotEquals(
            seasonalParticles(SeasonalEffect.SNOW, 0.8f),
            seasonalParticles(SeasonalEffect.MAPLE, 0.8f)
        )
    }

    @Test
    fun `every generated particle stays inside its documented range`() {
        SeasonalEffect.entries.forEach { effect ->
            seasonalParticles(effect, 1f, seed = 7).forEach { particle ->
                assertTrue("x is a fraction of the width", particle.x in 0f..1f)
                assertTrue("y is a fraction of one cycle", particle.y in 0f..1f)
                assertTrue("size is a multiplier", particle.size in 0.5f..1.4f)
                assertTrue("speed is positive", particle.speed > 0f)
                assertTrue("sway is a fraction of the width", particle.sway in 0f..0.1f)
                assertTrue("sway rate is positive", particle.swayRate > 0f)
                assertTrue("alpha is a real opacity", particle.alpha in 0f..1f)
                assertTrue(
                    "a shape with no spin would all rotate in lockstep",
                    particle.spin in -0.9f..0.9f
                )
                assertTrue(
                    "a variant outside the palette is a lookup that must never happen",
                    particle.variant in 0 until effect.variantCount
                )
            }
        }
    }

    @Test
    fun `rising shapes travel slower than falling ones`() {
        val fall = seasonalParticles(SeasonalEffect.SNOW, 1f, seed = 5)
        val rise = seasonalParticles(SeasonalEffect.FIREFLY, 1f, seed = 5)
        assertTrue(
            "falling speeds come from the slow-to-brisk band",
            fall.all { it.speed in 0.07f..0.17f }
        )
        assertTrue(
            "rising speeds come from the slower band",
            rise.all { it.speed in 0.025f..0.075f }
        )
        assertTrue(
            "a firefly that crossed the screen at a snowflake's speed is a spark " +
                "thrown upwards, not a living light",
            rise.map { it.speed }.average() < fall.map { it.speed }.average()
        )
        assertEquals(
            "a dandelion seed is a rising shape",
            ParticleDirection.RISE,
            SeasonalEffect.DANDELION.direction
        )
    }

    @Test
    fun `a leaf rocks further than a flake`() {
        val snow = seasonalParticles(SeasonalEffect.SNOW, 1f, seed = 5)
        val maple = seasonalParticles(SeasonalEffect.MAPLE, 1f, seed = 5)
        assertTrue(
            "a maple leaf is a flat surface falling edge-on and should sway more than a flake",
            maple.map { it.sway }.average() > snow.map { it.sway }.average()
        )
    }

    @Test
    fun `size scale is clamped and never non-finite`() {
        assertEquals(MIN_SEASONAL_SIZE_SCALE, particleSizeScale(-3f))
        assertEquals(MAX_SEASONAL_SIZE_SCALE, particleSizeScale(30f))
        assertEquals(DEFAULT_SEASONAL_SIZE_SCALE, particleSizeScale(Float.NaN))
        assertEquals(DEFAULT_SEASONAL_SIZE_SCALE, particleSizeScale(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `speed scale is clamped and never non-finite`() {
        assertEquals(MIN_SEASONAL_SPEED_SCALE, particleSpeedScale(-3f))
        assertEquals(MAX_SEASONAL_SPEED_SCALE, particleSpeedScale(30f))
        assertEquals(DEFAULT_SEASONAL_SPEED_SCALE, particleSpeedScale(Float.NaN))
        assertEquals(DEFAULT_SEASONAL_SPEED_SCALE, particleSpeedScale(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `pile depth and wind strength are clamped and never non-finite`() {
        assertEquals(MIN_SEASONAL_PILE_DEPTH, pileDepthScale(-3f))
        assertEquals(MAX_SEASONAL_PILE_DEPTH, pileDepthScale(30f))
        assertEquals(DEFAULT_SEASONAL_PILE_DEPTH, pileDepthScale(Float.NaN))
        assertEquals(DEFAULT_SEASONAL_PILE_DEPTH, pileDepthScale(Float.POSITIVE_INFINITY))

        assertEquals(MIN_SEASONAL_WIND_STRENGTH, windStrengthScale(-3f))
        assertEquals(MAX_SEASONAL_WIND_STRENGTH, windStrengthScale(30f))
        assertEquals(DEFAULT_SEASONAL_WIND_STRENGTH, windStrengthScale(Float.NaN))
        assertEquals(DEFAULT_SEASONAL_WIND_STRENGTH, windStrengthScale(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `the drawn size follows the scale`() {
        assertTrue(particleSizeDp(MAX_SEASONAL_SIZE_SCALE) > particleSizeDp(1f))
        assertTrue(particleSizeDp(1f) > particleSizeDp(MIN_SEASONAL_SIZE_SCALE))
    }

    @Test
    fun `sanitising repairs a stored setting without touching a valid one`() {
        val shipped = SeasonalSettings()
        assertEquals(
            "a default sky must survive its own sanitiser",
            shipped,
            shipped.sanitised()
        )

        val repaired = SeasonalSettings(
            density = Float.NaN,
            sizeScale = Float.NaN,
            speedScale = Float.NaN,
            pileDepth = Float.NaN,
            windStrength = Float.NaN
        ).sanitised()
        assertEquals(DEFAULT_SEASONAL_DENSITY, repaired.density)
        assertEquals(DEFAULT_SEASONAL_SIZE_SCALE, repaired.sizeScale)
        assertEquals(DEFAULT_SEASONAL_SPEED_SCALE, repaired.speedScale)
        assertEquals(DEFAULT_SEASONAL_PILE_DEPTH, repaired.pileDepth)
        assertEquals(DEFAULT_SEASONAL_WIND_STRENGTH, repaired.windStrength)

        val clamped = SeasonalSettings(
            density = 8f,
            sizeScale = 8f,
            speedScale = 8f,
            pileDepth = 8f,
            windStrength = 8f
        ).sanitised()
        assertEquals(MAX_SEASONAL_DENSITY, clamped.density)
        assertEquals(MAX_SEASONAL_SIZE_SCALE, clamped.sizeScale)
        assertEquals(MAX_SEASONAL_SPEED_SCALE, clamped.speedScale)
        assertEquals(MAX_SEASONAL_PILE_DEPTH, clamped.pileDepth)
        assertEquals(MAX_SEASONAL_WIND_STRENGTH, clamped.windStrength)
    }
}
