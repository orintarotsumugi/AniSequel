package com.anisequel.domain.color

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.abs

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

    private data class ColorCandidate(
        val hct: Hct,
        val population: Int
    )

    private const val TARGET_CHROMA = 48.0
    private const val WEIGHT_PROPORTION = 0.7
    private const val WEIGHT_CHROMA_ABOVE = 0.3
    private const val WEIGHT_CHROMA_BELOW = 0.1
    private const val CUTOFF_CHROMA = 5.0
    private const val CUTOFF_EXCITED_PROPORTION = 0.01

    /** A second hue is only worth drawing if it is genuinely coloured and not tiny. */
    private const val ACCENT_MIN_CHROMA = 24.0
    private const val ACCENT_MIN_SHARE = 0.004

    /** How far the accent's hue must sit from the seed's before it is a second colour. */
    private const val ACCENT_MIN_HUE_DISTANCE = 40.0

    /**
     * Builds the complete cover palette from one histogram pass.
     *
     * The old repository asked for the seed and accent separately; both calls
     * quantized the same pixel array into the same 5-bit histogram. A cover is
     * sampled only once, so keep that histogram and score both roles from it.
     */
    fun paletteFrom(pixels: IntArray): CoverPalette? {
        val histogram = quantize(pixels)
        val seed = score(histogram) ?: return null
        return CoverPalette(seed = seed, accent = accent(histogram, seed))
    }

    /** The most suitable theme seed in [pixels], or null if the image is grey. */
    fun seedFrom(pixels: IntArray): Int? = score(quantize(pixels))

    /**
     * A second, distinct hue in [pixels] - the colour a gradient pairs with the seed.
     *
     * The seed's own scoring is not reused here: it is built to pick the *most
     * representative* colour, so running it twice would confidently return the same
     * hue. This pass rewards chroma instead of population, requires a real region
     * rather than a one-pixel logo, and rejects anything within
     * [ACCENT_MIN_HUE_DISTANCE] of [seed] - a two-colour gradient needs two colours,
     * and a cover with only one hue has no second colour to give.
     */
    fun accentFrom(pixels: IntArray, seed: Int? = null): Int? = accent(quantize(pixels), seed)

    /** [accentFrom] with a hand-built histogram, so the scoring can be tested. */
    fun accent(histogram: Map<Int, Int>, seed: Int? = null): Int? {
        val total = histogram.values.sumOf { it.toLong() }
        if (total <= 0L) return null
        val seedHue = seed?.let { Hct.fromInt(it).hue }

        var best: Int? = null
        var bestScore = -1.0
        for ((argb, population) in histogram) {
            if (population <= 0) continue
            val share = population.toDouble() / total
            if (share < ACCENT_MIN_SHARE) continue

            val hct = Hct.fromInt(argb)
            if (hct.chroma < ACCENT_MIN_CHROMA) continue
            if (seedHue != null && hueDistance(hct.hue, seedHue) < ACCENT_MIN_HUE_DISTANCE) {
                continue
            }

            // Chroma, tempered by how much of the image wears it: the same trade a
            // designer makes between "the accent colour" and "a bright speck".
            val score = hct.chroma * kotlin.math.sqrt(share)
            // A named tie-break rather than the same Elvis expression inside the
            // conjunction: it parses exactly the same and reads without counting
            // parentheses.
            val winsTie = best?.let { argb < it } ?: true
            if (score > bestScore || (score == bestScore && winsTie)) {
                bestScore = score
                best = argb
            }
        }
        return best
    }

    /** The shortest arc between two hues, in degrees. */
    private fun hueDistance(a: Double, b: Double): Double {
        val difference = abs(a - b) % 360.0
        return if (difference > 180.0) 360.0 - difference else difference
    }

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
        val candidates = colorCandidates(histogram)
        val excitedProportion = excitedProportions(candidates) ?: return null
        return bestCandidate(candidates, excitedProportion)?.hct?.argb
    }

    private fun colorCandidates(histogram: Map<Int, Int>): List<ColorCandidate> =
        histogram.mapNotNull { (argb, population) ->
            if (population <= 0) return@mapNotNull null
            ColorCandidate(Hct.fromInt(argb), population)
        }

    /** Builds the M3 neighbourhood score without making candidate ranking stateful. */
    private fun excitedProportions(candidates: List<ColorCandidate>): DoubleArray? {
        if (candidates.isEmpty()) return null

        val huePopulation = IntArray(360)
        for (candidate in candidates) {
            val hue = floor(candidate.hct.hue).toInt().coerceIn(0, 359)
            huePopulation[hue] += candidate.population
        }
        val populationSum = huePopulation.sum().toDouble()
        if (populationSum <= 0.0) return null

        val excited = DoubleArray(360)
        for (hue in huePopulation.indices) {
            val proportion = huePopulation[hue] / populationSum
            if (proportion == 0.0) continue
            for (neighbour in hue - 15..hue + 15) {
                excited[sanitizeDegreesInt(neighbour)] += proportion
            }
        }
        return excited
    }

    private fun bestCandidate(
        candidates: List<ColorCandidate>,
        excitedProportion: DoubleArray
    ): ColorCandidate? {
        var best: ColorCandidate? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (candidate in candidates) {
            val hct = candidate.hct
            if (hct.chroma < CUTOFF_CHROMA) continue
            val hue = sanitizeDegreesInt(hct.hue.roundToInt())
            val proportion = excitedProportion[hue]
            if (proportion <= CUTOFF_EXCITED_PROPORTION) continue

            val score = scoreCandidate(hct, proportion)
            if (isBetter(score, candidate, bestScore, best)) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun scoreCandidate(hct: Hct, proportion: Double): Double {
        val proportionScore = proportion * 100.0 * WEIGHT_PROPORTION
        val chromaWeight = if (hct.chroma < TARGET_CHROMA) {
            WEIGHT_CHROMA_BELOW
        } else {
            WEIGHT_CHROMA_ABOVE
        }
        return proportionScore + (hct.chroma - TARGET_CHROMA) * chromaWeight
    }

    /** Equal scores use population and ARGB, never HashMap iteration order. */
    private fun isBetter(
        score: Double,
        candidate: ColorCandidate,
        bestScore: Double,
        best: ColorCandidate?
    ): Boolean {
        if (score > bestScore) return true
        if (abs(score - bestScore) >= 0.0000001 || best == null) return false
        return candidate.population > best.population ||
            (candidate.population == best.population && candidate.hct.argb < best.hct.argb)
    }

    private fun sanitizeDegreesInt(degrees: Int): Int {
        var value = degrees % 360
        if (value < 0) value += 360
        return value
    }
}
