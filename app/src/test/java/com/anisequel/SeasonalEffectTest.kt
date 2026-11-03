package com.anisequel

import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.MAX_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.MAX_SEASONAL_PARTICLES
import com.anisequel.domain.seasonal.MAX_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.MAX_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.MIN_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.MIN_SEASONAL_PARTICLES
import com.anisequel.domain.seasonal.MIN_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.MIN_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.ParticleDirection
import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.SeasonalSettings
import com.anisequel.domain.seasonal.particleCountFor
import com.anisequel.domain.seasonal.particleSizeDp
import com.anisequel.domain.seasonal.particleSizeScale
import com.anisequel.domain.seasonal.particleSpeedScale
import com.anisequel.domain.seasonal.seasonalParticles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seasonal overlay's pure half: which shapes exist, how many a density asks
 * for, and that the same seed always draws the same sky.
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
    fun `the shapes include a winter, a spring, an autumn and two that rise`() {
        // Snow and sakura are different winters, and the rising pair is the reason
        // direction is a property of the shape rather than a setting.
        assertTrue(SeasonalEffect.entries.any { it.direction == ParticleDirection.FALL })
        assertTrue(SeasonalEffect.entries.any { it.direction == ParticleDirection.RISE })
        assertEquals(ParticleDirection.RISE, SeasonalEffect.FIREFLY.direction)
        assertEquals(ParticleDirection.RISE, SeasonalEffect.BUBBLE.direction)
        assertEquals(ParticleDirection.FALL, SeasonalEffect.SNOW.direction)
        assertEquals(ParticleDirection.FALL, SeasonalEffect.SAKURA.direction)
        assertEquals(ParticleDirection.FALL, SeasonalEffect.CLOVER.direction)
        assertEquals(ParticleDirection.FALL, SeasonalEffect.MAPLE.direction)
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
        assertEquals(
            particleCountFor(DEFAULT_SEASONAL_DENSITY),
            count
        )
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
        assertTrue(
            seasonalParticles(SeasonalEffect.SAKURA, 0.6f, seed = 42) !=
                seasonalParticles(SeasonalEffect.SAKURA, 0.6f, seed = 43)
        )
    }

    @Test
    fun `each shape defaults to its own seed`() {
        // Not a guarantee about any one particle - a guarantee that two shapes do
        // not start from the identical random stream and fall in step.
        assertTrue(
            seasonalParticles(SeasonalEffect.SNOW, 0.8f) !=
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
            }
        }
    }

    @Test
    fun `rising shapes travel slower than falling ones`() {
        val fall = seasonalParticles(SeasonalEffect.SNOW, 1f, seed = 5)
        val rise = seasonalParticles(SeasonalEffect.FIREFLY, 1f, seed = 5)
        assertTrue("falling speeds come from the slow-to-brisk band", fall.all { it.speed in 0.07f..0.17f })
        assertTrue("rising speeds come from the slower band", rise.all { it.speed in 0.025f..0.075f })
        assertTrue(
            "a firefly that crossed the screen at a snowflake's speed is a spark " +
                "thrown upwards, not a living light",
            rise.map { it.speed }.average() < fall.map { it.speed }.average()
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
            speedScale = Float.NaN
        ).sanitised()
        assertEquals(DEFAULT_SEASONAL_DENSITY, repaired.density)
        assertEquals(DEFAULT_SEASONAL_SIZE_SCALE, repaired.sizeScale)
        assertEquals(DEFAULT_SEASONAL_SPEED_SCALE, repaired.speedScale)

        val clamped = SeasonalSettings(density = 8f, sizeScale = 8f, speedScale = 8f)
            .sanitised()
        assertEquals(MAX_SEASONAL_DENSITY, clamped.density)
        assertEquals(MAX_SEASONAL_SIZE_SCALE, clamped.sizeScale)
        assertEquals(MAX_SEASONAL_SPEED_SCALE, clamped.speedScale)
    }
}
