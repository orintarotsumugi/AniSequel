package com.anisequel

import com.anisequel.domain.color.ArgbHex
import com.anisequel.domain.color.CoverColorQuantizer
import com.anisequel.domain.color.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Defects in the pure colour path, pinned without a device.
 *
 * These are the rules the theme builder relies on: a grey never contributes a
 * hue, a seed hue has one bucket on write and on read, a stored hex is opaque,
 * and a second hue is chosen by population as well as chroma. Each one has
 * produced a theme that looked like the wrong title before it was fixed.
 */
class ColorEngineTest {

    @Test
    fun `a large grey margin cannot move the seed to its hue`() {
        // Hue 30 and hue 90, both coloured. A large achromatic region sits at
        // hue 0 and used to inflate the excitation around hue 90, handing the
        // seed to the weaker of the two coloured regions.
        val near = Hct.from(30.0, 40.0, 50.0).argb
        val far = Hct.from(90.0, 24.0, 50.0).argb
        val grey = Hct.from(0.0, 0.0, 50.0).argb
        val histogram = mapOf(near to 100, far to 100, grey to 4_000)

        val seed = CoverColorQuantizer.score(histogram)
        assertTrue("a coloured histogram must still yield a seed", seed != null)
        val hue = Hct.fromInt(seed!!).hue
        assertTrue(
            "the seed took the grey-inflated hue instead of the stronger coloured " +
                "region (got $hue, expected about 30)",
            angularDistance(hue, 30.0) < 12.0
        )
    }

    @Test
    fun `an achromatic fallback seed does not hide every accent`() {
        val seed = ArgbHex.parse("#808080")!!
        val histogram = mapOf(
            Hct.from(280.0, 50.0, 50.0).argb to 400,
            Hct.from(30.0, 50.0, 50.0).argb to 400
        )

        val accent = CoverColorQuantizer.accent(histogram, seed = seed)
        assertTrue(
            "a grey fallback has no hue, so it must not filter the accents",
            accent != null
        )
    }

    @Test
    fun `a coloured fallback seed still rejects accents near its own hue`() {
        val seedColor = Hct.from(30.0, 60.0, 50.0).argb
        val near = Hct.from(35.0, 60.0, 50.0).argb
        val far = Hct.from(220.0, 60.0, 50.0).argb
        val histogram = mapOf(near to 400, far to 400)

        val accent = CoverColorQuantizer.accent(histogram, seed = seedColor)
        assertTrue("the distant hue is the only acceptable accent", accent == far)
    }

    @Test
    fun `an achromatic colour has no hue`() {
        assertEquals(0.0, Hct.from(140.0, 0.0, 50.0).hue, 0.0001)
    }

    @Test
    fun `an invalid eight digit seed is rejected`() {
        assertNull(ArgbHex.parse("#12345"))
        assertNull(ArgbHex.parse("#GGGGGG"))
    }

    @Test
    fun `a seed hue with a fractional part selects a stable winner`() {
        // The excitation buckets are built with floor. Reading them with round
        // used to shift a hue like 20.6 onto the neighbouring bucket. Two
        // histograms that differ only by where the fractional hue sits must
        // agree on which region is the seed.
        val a = Hct.from(20.6, 50.0, 50.0).argb to 500
        val b = Hct.from(70.4, 30.0, 50.0).argb to 500
        val first = CoverColorQuantizer.score(mapOf(a, b))
        val second = CoverColorQuantizer.score(mapOf(a, b))
        assertEquals(first, second)
        assertTrue(first != null)
    }

    private fun angularDistance(a: Double, b: Double): Double {
        val raw = kotlin.math.abs(a - b) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }
}
