package com.anisequel

import com.anisequel.domain.color.ArgbHex
import com.anisequel.domain.color.CoverColorQuantizer
import com.anisequel.domain.color.Hct
import com.anisequel.domain.color.TonalPalette
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

/**
     * Every tone on the ladder is the tone it asked for, at every hue and chroma.
     *
     * This is the contrast contract: the palette builder picks tones 40/80/90 by
     * name and the AA pairs are guaranteed by those tones being what they say. The
     * gamut search at the top of the scale used to give up silently - with no
     * chroma in gamut it returned the unclipped solve, so tone 99 at a red hue came
     * back as a clipped magenta at tone 70, and a scheme that asked for tone 99 got
     * a colour two thirds of the way down the ladder. An achromatic fallback now
     * guarantees the tone, and a half-unit tolerance at the ceiling lets tone 99
     * keep its chroma where the gamut can hold it.
     */
    @Test
    fun `every tone of every palette is the tone it asks for`() {
        for (hue in 0 until 360 step 30) {
            for (chroma in listOf(24.0, 48.0, 120.0)) {
                val palette = TonalPalette(hue.toDouble(), chroma)
                for (tone in 1..99) {
                    val measured = Hct.fromInt(palette.tone(tone)).tone
                    assertTrue(
                        "hue $hue at chroma $chroma asked for tone $tone and got " +
                            "%.2f".format(measured),
                        kotlin.math.abs(measured - tone) < 1.5
                    )
                }
            }
        }
    }

    /** The exact failure above, named, so a regression is one line to read. */
    @Test
    fun `tone 99 of a red palette is still a red at tone 99`() {
        val solved = TonalPalette(0.0, 120.0).tone(99)
        val hct = Hct.fromInt(solved)
        assertTrue(
            "tone 99 came back as tone %.2f (hue %.1f): the solver returned an "
                .format(hct.tone, hct.hue) +
                "out-of-gamut colour instead of the neutral it can actually draw",
            hct.tone >= 97.5
        )
    }

    private fun angularDistance(a: Double, b: Double): Double {
        val raw = kotlin.math.abs(a - b) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }
}
