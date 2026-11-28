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
private const val TWO_PI = (2.0 * PI).toFloat()

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

    // region the wavy ring

    @Test
    fun `the ring's radius stays within its wobble`() {
        val base = 40f
        val wobble = 0.08f
        var angle = 0f
        while (angle <= 6.3f) {
            val radius = WaveGeometry.ringRadius(base, wobble, lobes = 7, angle = angle)
            assertTrue(
                "a lobe that reaches past the slot clips its own stroke at $angle",
                radius <= base * (1f + wobble) + 1e-4f
            )
            assertTrue(radius >= base * (1f - wobble) - 1e-4f)
            angle += 0.05f
        }
    }

    @Test
    fun `no wobble is a circle`() {
        var angle = 0f
        while (angle <= 6.3f) {
            assertEquals(40f, WaveGeometry.ringRadius(40f, 0f, lobes = 7, angle = angle), 1e-5f)
            angle += 0.2f
        }
    }

    /** Seven lobes means seven crests around one turn, which is what makes it a wave. */
    @Test
    fun `the ring crests once per lobe`() {
        val lobes = 5
        var crests = 0
        var previous = WaveGeometry.ringRadius(10f, 0.1f, lobes, angle = 0f)
        var rising = false
        var angle = 0.01f
        while (angle <= TWO_PI + 0.01f) {
            val radius = WaveGeometry.ringRadius(10f, 0.1f, lobes, angle)
            if (radius > previous && !rising) rising = true
            if (radius < previous && rising) {
                rising = false
                crests++
            }
            previous = radius
            angle += 0.005f
        }
        assertEquals(lobes, crests)
    }

    /**
     * A phase has to slide the outline, not reshape it.
     *
     * That is the whole difference between an animated ring and a ring that
     * breathes: the score cell became the one wavy mark on the page that stood
     * still - a picture of a wave rather than water - because the path was built
     * without a phase. Sliding it is safe precisely because the phase cannot change
     * the crest height, and a phase that also altered it would read as the ring
     * pulsing at whatever rate the reader happened to look at it.
     */
    @Test
    fun `a phase slides the ring and leaves its shape alone`() {
        val base = 20f
        val wobble = 0.07f
        val lobes = 7
        val phase = WaveGeometry.phase(0.25f)
        var angle = 0f
        while (angle < TWO_PI) {
            assertEquals(
                "the shifted outline has to be the unshifted one, moved",
                WaveGeometry.ringRadius(base, wobble, lobes, angle),
                WaveGeometry.ringRadius(base, wobble, lobes, angle - phase / lobes, phase),
                1e-4f
            )
            angle += 0.1f
        }
    }

    @Test
    fun `a full share closes the ring, and an empty one draws none of it`() {
        assertEquals(0f, WaveGeometry.ringSweepDegrees(0f), 1e-4f)
        assertEquals(180f, WaveGeometry.ringSweepDegrees(0.5f), 1e-4f)
        assertEquals(360f, WaveGeometry.ringSweepDegrees(1f), 1e-4f)
        // A score above 100 or a progress below zero is a bad payload, not a reason
        // to draw a ring that overlaps itself or runs backwards.
        assertEquals(360f, WaveGeometry.ringSweepDegrees(1.4f), 1e-4f)
        assertEquals(0f, WaveGeometry.ringSweepDegrees(-0.2f), 1e-4f)
    }

    // endregion
}
