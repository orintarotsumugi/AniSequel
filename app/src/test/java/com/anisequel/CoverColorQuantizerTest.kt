package com.anisequel

import com.anisequel.domain.color.CoverColorQuantizer
import com.anisequel.domain.color.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The image path that feeds the theme.
 *
 * The failure this replaces was not "the palette is slightly off": it was that
 * the app never looked at the cover at all. It read AniList's single
 * `coverImage.color` field, which is a server-side average and is frequently the
 * background wash rather than the artwork. These tests pin the three properties
 * that make a cover-derived seed feel chosen:
 *
 *  - a genuinely coloured cover produces a seed of that hue,
 *  - a grey cover produces *no* seed, so the caller can fall back instead of
 *    inventing a colour,
 *  - a small vivid region is not buried by a large white margin.
 */
class CoverColorQuantizerTest {

    @Test
    fun `a red cover yields a red seed`() {
        val red = 0xFFD32F2F.toInt()
        val pixels = solid(red, 1000) + solid(0xFF000000.toInt(), 300) + solid(0xFFFFFFFF.toInt(), 200)

        val seed = CoverColorQuantizer.seedFrom(pixels)
        assertNotNull("a red cover has to produce a seed", seed)

        val expectedHue = Hct.fromInt(red).hue
        val actualHue = Hct.fromInt(seed!!).hue
        assertTrue(
            "the seed's hue ($actualHue) is not the cover's ($expectedHue)",
            angularDistance(actualHue, expectedHue) < 12.0
        )
    }

    @Test
    fun `a grey cover produces no seed`() {
        val pixels = solid(0xFF808080.toInt(), 1000) +
            solid(0xFF000000.toInt(), 500) +
            solid(0xFFFFFFFF.toInt(), 500)

        assertNull(
            "a black-and-white cover has no hue to theme with; the caller falls " +
                "back to AniList's colour instead of the quantizer inventing one",
            CoverColorQuantizer.seedFrom(pixels)
        )
    }

    @Test
    fun `a small vivid accent is not buried by a white field`() {
        val blue = 0xFF1565C0.toInt()
        val pixels = solid(0xFFFFFFFF.toInt(), 2000) + solid(blue, 400)

        val seed = CoverColorQuantizer.seedFrom(pixels)
        assertNotNull("the blue region is 17% of the image, well above the cutoff", seed)

        val expectedHue = Hct.fromInt(blue).hue
        val actualHue = Hct.fromInt(seed!!).hue
        assertTrue(
            "the seed took the white margin instead of the only coloured region " +
                "(seed hue $actualHue, blue $expectedHue)",
            angularDistance(actualHue, expectedHue) < 12.0
        )
    }

    @Test
    fun `transparent pixels do not contribute a colour`() {
        val transparentRed = 0x00FF0000
        val pixels = solid(transparentRed, 1000)

        assertNull(
            "a transparent border is not part of the artwork",
            CoverColorQuantizer.seedFrom(pixels)
        )
        assertTrue(
            "and nothing should have reached the histogram",
            CoverColorQuantizer.quantize(pixels).isEmpty()
        )
    }

    @Test
    fun `quantization keeps neighbouring shades in one bin`() {
        val a = 0xFFD32F2F.toInt()
        val b = 0xFFD42F2F.toInt()
        val histogram = CoverColorQuantizer.quantize(intArrayOf(a, b))

        assertEquals(
            "a gradient has to stay one region, or its population is split across " +
                "bins and loses to a flat patch of the same total area",
            1,
            histogram.size
        )
        assertEquals(2, histogram.values.first())
    }

    private fun solid(argb: Int, count: Int): IntArray = IntArray(count) { argb }

    private fun angularDistance(a: Double, b: Double): Double {
        val raw = kotlin.math.abs(a - b) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }
}
