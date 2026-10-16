package com.anisequel

import com.anisequel.domain.motion.WaveGeometry
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wavy progress card's geometry.
 *
 * A `Canvas` cannot be asserted, so the shape is pinned here instead: these are the
 * numbers the drawing reads, and every way this control can look wrong on screen is a
 * way one of them can be wrong in this file.
 */
class WaveGeometryTest {

    private val maxAmplitude = 12f

    /**
     * The track is drawn at this same amplitude, so a zero here would make an
     * untouched title's track a ruled line while every other title's is a wave.
     */
    @Test
    fun `an untouched title still has a wave to draw`() {
        val resting = WaveGeometry.amplitude(maxAmplitude, progress = 0f)

        assertEquals(maxAmplitude * 0.45f, resting, 1e-4f)
        assertTrue("the resting amplitude must not be flat", resting > 0f)
    }

    /** At 100% the wave is at its full height, so the last episode is visible in it. */
    @Test
    fun `a finished title is drawn at full amplitude`() {
        assertEquals(maxAmplitude, WaveGeometry.amplitude(maxAmplitude, progress = 1f), 1e-4f)
    }

    @Test
    fun `the wave grows with progress`() {
        val empty = WaveGeometry.amplitude(maxAmplitude, progress = 0f)
        val quarter = WaveGeometry.amplitude(maxAmplitude, progress = 0.25f)
        val half = WaveGeometry.amplitude(maxAmplitude, progress = 0.5f)
        val full = WaveGeometry.amplitude(maxAmplitude, progress = 1f)

        assertTrue("$empty !< $quarter", empty < quarter)
        assertTrue("$quarter !< $half", quarter < half)
        assertTrue("$half !< $full", half < full)
    }

    /**
     * A progress outside 0..1 cannot come from the model, and this is the guard that
     * says so rather than letting it through: 1.4 would draw a wave taller than the
     * bar that contains it.
     */
    @Test
    fun `progress outside the range is clamped`() {
        assertEquals(maxAmplitude, WaveGeometry.amplitude(maxAmplitude, progress = 1.4f), 1e-4f)
        assertEquals(
            maxAmplitude * 0.45f,
            WaveGeometry.amplitude(maxAmplitude, progress = -0.2f),
            1e-4f
        )
    }

    @Test
    fun `the wave starts on its own centre line`() {
        assertEquals(0f, WaveGeometry.offset(x = 0f, wavelength = 100f, amplitude = maxAmplitude, phase = 0f), 1e-4f)
    }

    /** Nothing may be drawn outside the band the amplitude promises, or the bar clips. */
    @Test
    fun `no sample stands taller than the amplitude`() {
        var x = 0f
        while (x <= 400f) {
            val sample = WaveGeometry.offset(x = x, wavelength = 120f, amplitude = 4f, phase = 0.7f)
            assertTrue("sample at $x was $sample", kotlin.math.abs(sample) <= 4f + 1e-4f)
            x += 1.7f
        }
    }

    /** One period along is the same height, or the wave drifts across the bar. */
    @Test
    fun `the wave repeats every wavelength`() {
        val wavelength = 120f
        var x = 0f
        while (x <= 300f) {
            assertEquals(
                WaveGeometry.offset(x, wavelength, maxAmplitude, phase = 0.3f),
                WaveGeometry.offset(x + wavelength, wavelength, maxAmplitude, phase = 0.3f),
                1e-3f
            )
            x += 11f
        }
    }

    /** The travelling phase: one full period per cycle, and a half turn at the halfway point. */
    @Test
    fun `a cycle is a full turn`() {
        assertEquals(0f, WaveGeometry.phase(0f), 1e-5f)
        assertEquals(PI.toFloat(), WaveGeometry.phase(0.5f), 1e-5f)
        assertEquals((2.0 * PI).toFloat(), WaveGeometry.phase(1f), 1e-5f)
    }

    /**
     * Phase is what makes the wave travel rather than stand still, and its effect is
     * asserted rather than its arithmetic: reversing the turn has to flip the offset.
     */
    @Test
    fun `half a turn inverts the wave`() {
        val upright = WaveGeometry.offset(x = 30f, wavelength = 120f, amplitude = 5f, phase = 0f)
        val inverted = WaveGeometry.offset(x = 30f, wavelength = 120f, amplitude = 5f, phase = PI.toFloat())

        assertEquals(upright, -inverted, 1e-4f)
        assertTrue("the sample has to be off the centre line for this to mean anything", upright != 0f)
    }
}
