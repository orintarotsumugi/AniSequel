package com.anisequel.domain.color

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Picks a Material theme seed out of a cover image.
 *
 * This is the same shape as Material You's image pipeline, without the heavy
 * quantizer: the pixels are binned into a 5-bit-per-channel histogram (M3's
 * `QuantizerMap`), and the bins are ranked with M3's `Score` rules. The scoring
 * is what makes the result look like a designer chose it:
 *
 *  - **Chroma is rewarded**, but with diminishing returns above 48. A tiny neon
 *    logo cannot outrank a large, genuinely coloured region on chroma alone.
 *  - **Hue is rewarded by neighbourhood, not by the exact degree.** Each bin's
 *    score counts the population within 15 degrees either side of its hue, so a
 *    gradient (which would otherwise split into ten low-population bins) is
 *    treated as one region.
 *  - **Greys and near-greys are dropped** below chroma 5, which is what stops a
 *    black-and-white cover from producing a grey app. When nothing survives,
 *    the caller falls back to AniList's own colour rather than inventing one.
 *
 * The output is deliberately a single seed rather than a set of four swatches:
 * the tonal-palette builder derives secondary, tertiary, surfaces and outlines
 * from one hue, so handing it a small secondary swatch would only make the
 * scheme internally inconsistent.
 */
object CoverColorQuantizer {

    private const val TARGET_CHROMA = 48.0
    private const val WEIGHT_PROPORTION = 0.7
    private const val WEIGHT_CHROMA_ABOVE = 0.3
    private const val WEIGHT_CHROMA_BELOW = 0.1
    private const val CUTOFF_CHROMA = 5.0
    private const val CUTOFF_EXCITED_PROPORTION = 0.01

    /** The most suitable theme seed in [pixels], or null if the image is grey. */
    fun seedFrom(pixels: IntArray): Int? = score(quantize(pixels))

    /**
     * Bins opaque pixels into 5 bits per channel.
     *
     * Transparency is skipped rather than composited onto white: a PNG cover with
     * a transparent border would otherwise contribute a large white region that
     * is not part of the artwork.
     */
    fun quantize(pixels: IntArray): Map<Int, Int> {
        val histogram = HashMap<Int, Int>(pixels.size.coerceAtMost(4096))
        for (pixel in pixels) {
            // `ushr` fills with zeros, so the top byte is already 0..255.
            val alpha = pixel ushr 24
            if (alpha < 0x80) continue
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF

            // 5 bits hold the colour; the low 3 are filled from the top so the
            // bin's representative value sits in the middle of its range rather
            // than at its darkest corner.
            val r = red shr 3
            val g = green shr 3
            val b = blue shr 3
            val quantized = (0xFF shl 24) or
                (((r shl 3) or (r shr 2)) shl 16) or
                (((g shl 3) or (g shr 2)) shl 8) or
                ((b shl 3) or (b shr 2))

            histogram[quantized] = (histogram[quantized] ?: 0) + 1
        }
        return histogram
    }

    /**
     * Ranks a histogram and returns the best seed.
     *
     * A port of `Score.score`, reduced to the one colour the app needs. Kept
     * separate from [quantize] so the scoring can be tested with a hand-built
     * histogram instead of a bitmap.
     */
    fun score(histogram: Map<Int, Int>): Int? {
        if (histogram.isEmpty()) return null

        val huePopulation = IntArray(360)
        var populationSum = 0.0
        val candidates = ArrayList<Hct>(histogram.size)
        for ((argb, population) in histogram) {
            if (population <= 0) continue
            val hct = Hct.fromInt(argb)
            candidates += hct
            val hue = floor(hct.hue).toInt().coerceIn(0, 359)
            huePopulation[hue] += population
            populationSum += population
        }
        if (populationSum <= 0.0) return null

        // How much of the image sits within 15 degrees of each hue. This is the
        // "neighbourhood" part of M3's score: without it, a smooth gradient is
        // split across many bins and loses to a flat patch of equal total area.
        val excitedProportion = DoubleArray(360)
        for (hue in 0 until 360) {
            val proportion = huePopulation[hue] / populationSum
            if (proportion == 0.0) continue
            for (neighbour in hue - 15..hue + 15) {
                excitedProportion[sanitizeDegreesInt(neighbour)] += proportion
            }
        }

        var best: Hct? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (candidate in candidates) {
            val hct = candidate
            if (hct.chroma < CUTOFF_CHROMA) continue
            val hue = sanitizeDegreesInt(hct.hue.roundToInt())
            val proportion = excitedProportion[hue]
            if (proportion <= CUTOFF_EXCITED_PROPORTION) continue

            val proportionScore = proportion * 100.0 * WEIGHT_PROPORTION
            val chromaWeight = if (hct.chroma < TARGET_CHROMA) {
                WEIGHT_CHROMA_BELOW
            } else {
                WEIGHT_CHROMA_ABOVE
            }
            val chromaScore = (hct.chroma - TARGET_CHROMA) * chromaWeight
            val score = proportionScore + chromaScore

            if (score > bestScore) {
                bestScore = score
                best = hct
            }
        }
        return best?.argb
    }

    private fun sanitizeDegreesInt(degrees: Int): Int {
        var value = degrees % 360
        if (value < 0) value += 360
        return value
    }
}
