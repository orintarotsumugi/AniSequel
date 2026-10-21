package com.anisequel.domain.color

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * HCT (Hue, Chroma, Tone), the colour space Material 3 builds tonal palettes in.
 *
 * Pure Kotlin and no Android imports, so the whole colour path can be tested on
 * the JVM. This is a port of the parts of Google's `material-color-utilities`
 * that the app actually needs: `Cam16`, `ViewingConditions`, `Hct`, and the
 * gamut solver that maps a requested tone back into sRGB.
 *
 * ## Why this exists at all
 *
 * `DynamicThemeBuilder` used to derive its palette in HSL. That is fine for
 * producing *a* colour from a seed, and wrong for producing a *Material 3*
 * palette: HSL's lightness is not perceptual luminance, so two seeds at the same
 * HSL lightness can land on completely different contrast. A yellow seed could
 * produce a primary at tone 40 that white text could not sit on, while a blue seed
 * at the same lightness was comfortable. Tone in HCT is CIELAB L*, which is
 * luminance by construction, so the palettes below keep the same relationship to
 * their text whatever hue the cover art is.
 *
 * HCT's tone is exactly L*: `Hct.fromInt` takes it from the colour's Y, and
 * `Hct.from` solves for a colour with that L* while holding the requested hue and
 * chroma as far as the sRGB gamut allows. That is what makes tone 10/90 container
 * pairs safe for every seed rather than only for the blue the app was designed
 * around.
 */
class Hct private constructor(
    val hue: Double,
    val chroma: Double,
    val tone: Double,
    /** The sRGB colour this HCT triple actually resolves to. */
    val argb: Int
) {
    companion object {
        /** Converts an ARGB colour to HCT under the default viewing conditions. */
        fun fromInt(argb: Int): Hct {
            val cam = Cam16.fromInt(argb)
            return Hct(cam.hue, cam.chroma, ColorMath.lstarFromArgb(argb), argb)
        }

        /**
         * Resolves an HCT triple to ARGB.
         *
         * Chroma is reduced, not clipped, when the requested triple is outside
         * sRGB: clipping each channel independently is what turns a saturated
         * orange at tone 90 into pink. Reducing chroma keeps the hue and the
         * requested tone, which are the two properties the palette depends on.
         */
        fun from(hue: Double, chroma: Double, tone: Double): Hct {
            val safeHue = ColorMath.sanitizeDegrees(hue)
            val safeTone = tone.coerceIn(0.0, 100.0)
            // At the ends of the tone scale, and for a genuinely neutral seed,
            // there is no hue or chroma left to preserve. Resolving these
            // directly also keeps the solver away from division by a chroma of
            // zero.
            if (chroma < 1.0 || safeTone <= 0.0 || safeTone >= 100.0) {
                val gray = when {
                    safeTone <= 0.0 -> ColorMath.BLACK
                    safeTone >= 100.0 -> ColorMath.WHITE
                    else -> ColorMath.argbFromTone(safeTone)
                }
                return Hct(safeHue, 0.0, safeTone, gray)
            }
            val argb = HctSolver.solve(safeHue, chroma, safeTone)
            val cam = Cam16.fromInt(argb)
            return Hct(cam.hue, cam.chroma, ColorMath.lstarFromArgb(argb), argb)
        }
    }
}

/**
 * A Material 3 tonal palette: one hue and chroma, sampled at any tone.
 *
 * Tones are memoised because a scheme asks for the same few tones from each
 * palette several times - `primary` and `surfaceTint` are the same colour, for
 * example - and because the gamut solve is the expensive part of building a
 * theme. A palette is immutable, so this cache can never go stale.
 */
class TonalPalette(val hue: Double, val chroma: Double) {
    private val tones = HashMap<Int, Int>(16)

    fun tone(tone: Int): Int {
        val safeTone = tone.coerceIn(0, 100)
        return tones.getOrPut(safeTone) {
            Hct.from(hue, chroma, safeTone.toDouble()).argb
        }
    }

    companion object {
        fun fromInt(argb: Int): TonalPalette {
            val hct = Hct.fromInt(argb)
            return TonalPalette(hct.hue, hct.chroma)
        }
    }
}

/**
 * The colour arithmetic HCT needs, kept in one place so the public colour types
 * do not have to expose it.
 */
internal object ColorMath {
    val BLACK: Int = 0xFF000000.toInt()
    val WHITE: Int = 0xFFFFFFFF.toInt()

    private const val LAB_EPSILON = 216.0 / 24389.0
    private const val LAB_KAPPA = 24389.0 / 27.0

    /** sRGB channel (0..255) to linear light on the 0..100 scale CAM16 uses. */
    fun linearized(channel: Int): Double {
        val normalized = channel / 255.0
        return if (normalized <= 0.040449936) {
            normalized / 12.92 * 100.0
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4) * 100.0
        }
    }

    /** Linear light on the 0..100 scale back to an sRGB channel (0..255). */
    fun delinearized(component: Double): Int {
        val normalized = (component / 100.0).coerceIn(0.0, 1.0)
        val encoded = if (normalized <= 0.0031308) {
            normalized * 12.92
        } else {
            1.055 * normalized.pow(1.0 / 2.4) - 0.055
        }
        return (encoded * 255.0).roundToInt().coerceIn(0, 255)
    }

    fun xyzFromArgb(argb: Int): DoubleArray {
        val r = linearized((argb shr 16) and 0xFF)
        val g = linearized((argb shr 8) and 0xFF)
        val b = linearized(argb and 0xFF)
        return doubleArrayOf(
            0.41233895 * r + 0.35762064 * g + 0.18051042 * b,
            0.2126 * r + 0.7152 * g + 0.0722 * b,
            0.01932141 * r + 0.11916382 * g + 0.95034478 * b
        )
    }

    fun argbFromXyz(x: Double, y: Double, z: Double): Int {
        val linear = linearRgbFromXyz(x, y, z)
        return argbFromRgb(
            delinearized(linear[0]),
            delinearized(linear[1]),
            delinearized(linear[2])
        )
    }

    /** Linear sRGB on the same 0..100 scale, used for the gamut check. */
    fun linearRgbFromXyz(x: Double, y: Double, z: Double): DoubleArray = doubleArrayOf(
        3.2406 * x - 1.5372 * y - 0.4986 * z,
        -0.9689 * x + 1.8758 * y + 0.0415 * z,
        0.0557 * x - 0.2040 * y + 1.0570 * z
    )

    fun argbFromRgb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or
            ((red and 0xFF) shl 16) or
            ((green and 0xFF) shl 8) or
            (blue and 0xFF)

    fun lstarFromY(y: Double): Double = 116.0 * labF(y / 100.0) - 16.0

    fun yFromLstar(lstar: Double): Double = 100.0 * labInvf((lstar + 16.0) / 116.0)

    fun lstarFromArgb(argb: Int): Double = lstarFromY(xyzFromArgb(argb)[1])

    /** A neutral grey at [tone] - the only colour HCT allows at zero chroma. */
    fun argbFromTone(tone: Double): Int {
        val channel = delinearized(yFromLstar(tone))
        return argbFromRgb(channel, channel, channel)
    }

    fun relativeLuminance(argb: Int): Double {
        fun channel(component: Int): Double {
            val c = component / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel((argb shr 16) and 0xFF) +
            0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }

    /** WCAG 2.1 contrast ratio, from 1.0 (identical) to 21.0 (black on white). */
    fun contrastRatio(foreground: Int, background: Int): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun sanitizeDegrees(degrees: Double): Double {
        var value = degrees % 360.0
        if (value < 0.0) value += 360.0
        return value
    }

    private fun labF(t: Double): Double =
        if (t > LAB_EPSILON) cbrt(t) else (LAB_KAPPA * t + 16.0) / 116.0

    private fun labInvf(ft: Double): Double {
        val ft3 = ft * ft * ft
        return if (ft3 > LAB_EPSILON) ft3 else (116.0 * ft - 16.0) / LAB_KAPPA
    }
}

/**
 * The CAM16 viewing conditions the palette is defined under.
 *
 * These are the same defaults Google's library uses: D65 white, a 200-lux
 * adapting field, a 50% grey background and an average surround. They describe a
 * phone screen rather than a print shop, which is what makes the resulting tones
 * match Material You's own output.
 */
internal class ViewingConditions private constructor(
    val n: Double,
    val aw: Double,
    val nbb: Double,
    val ncb: Double,
    val c: Double,
    val nc: Double,
    val rgbD: DoubleArray,
    val fl: Double,
    val flRoot: Double,
    val z: Double
) {
    companion object {
        val DEFAULT: ViewingConditions = make()

        private fun make(
            whitePoint: DoubleArray = doubleArrayOf(95.047, 100.0, 108.883),
            adaptingLuminance: Double = (200.0 / PI) * ColorMath.yFromLstar(50.0) / 100.0,
            backgroundLstar: Double = 50.0,
            surround: Double = 2.0,
            discountingIlluminant: Boolean = false
        ): ViewingConditions {
            val rW = 0.401288 * whitePoint[0] + 0.650173 * whitePoint[1] - 0.051461 * whitePoint[2]
            val gW = -0.250268 * whitePoint[0] + 1.204414 * whitePoint[1] + 0.045854 * whitePoint[2]
            val bW = -0.002079 * whitePoint[0] + 0.048952 * whitePoint[1] + 0.953127 * whitePoint[2]

            val f = 0.8 + surround / 10.0
            val c = if (f >= 0.9) {
                lerp(0.59, 0.69, (f - 0.9) * 10.0)
            } else {
                lerp(0.525, 0.59, (f - 0.8) * 10.0)
            }
            val d = if (discountingIlluminant) {
                1.0
            } else {
                (f * (1.0 - (1.0 / 3.6) * exp((-adaptingLuminance - 42.0) / 92.0)))
                    .coerceIn(0.0, 1.0)
            }

            // These are the chromatic-adaptation factors themselves, not the
            // factors scaled by each cone response. Mixing the two produces a
            // correct-looking luminance and a hue that is several degrees off -
            // the bug that made a material palette seed resolve 7 degrees away
            // from where Material's own library puts it.
            val rgbD = doubleArrayOf(
                d * (100.0 / rW) + 1.0 - d,
                d * (100.0 / gW) + 1.0 - d,
                d * (100.0 / bW) + 1.0 - d
            )

            val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
            val k4 = k * k * k * k
            val k4F = 1.0 - k4
            val fl = k4 * adaptingLuminance + 0.1 * k4F * k4F * cbrt(5.0 * adaptingLuminance)

            val n = ColorMath.yFromLstar(backgroundLstar) / whitePoint[1]
            val z = 1.48 + sqrt(n)
            val nbb = 0.725 / n.pow(0.2)

            val rgbAFactors = doubleArrayOf(
                (fl * rgbD[0] * rW / 100.0).pow(0.42),
                (fl * rgbD[1] * gW / 100.0).pow(0.42),
                (fl * rgbD[2] * bW / 100.0).pow(0.42)
            )
            val rgbA = doubleArrayOf(
                400.0 * rgbAFactors[0] / (rgbAFactors[0] + 27.13),
                400.0 * rgbAFactors[1] / (rgbAFactors[1] + 27.13),
                400.0 * rgbAFactors[2] / (rgbAFactors[2] + 27.13)
            )
            val aw = (2.0 * rgbA[0] + rgbA[1] + 0.05 * rgbA[2]) * nbb

            return ViewingConditions(
                n = n,
                aw = aw,
                nbb = nbb,
                ncb = nbb,
                c = c,
                nc = f,
                rgbD = rgbD,
                fl = fl,
                flRoot = fl.pow(0.25),
                z = z
            )
        }

        private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
    }
}

/**
 * The two CAM16 dimensions HCT keeps: hue and chroma.
 *
 * Lightness is deliberately absent. HCT takes its tone from CIELAB L* instead,
 * because CAM16's J is not perceptually uniform with it; the solver below
 * converts a requested tone into the J that produces that L*, which is why this
 * class only needs to expose the forward conversion and the `J/chroma/hue`
 * inverse.
 */
internal class Cam16(val hue: Double, val chroma: Double) {
    companion object {
        fun fromInt(argb: Int): Cam16 {
            val xyz = ColorMath.xyzFromArgb(argb)
            return fromXyz(xyz[0], xyz[1], xyz[2])
        }

        fun fromXyz(x: Double, y: Double, z: Double): Cam16 {
            val vc = ViewingConditions.DEFAULT
            val rC = 0.401288 * x + 0.650173 * y - 0.051461 * z
            val gC = -0.250268 * x + 1.204414 * y + 0.045854 * z
            val bC = -0.002079 * x + 0.048952 * y + 0.953127 * z

            val rA = adapt(vc, vc.rgbD[0] * rC)
            val gA = adapt(vc, vc.rgbD[1] * gC)
            val bA = adapt(vc, vc.rgbD[2] * bC)

            val a = (11.0 * rA - 12.0 * gA + bA) / 11.0
            val b = (rA + gA - 2.0 * bA) / 9.0
            val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
            val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0

            val atanDegrees = atan2(b, a) * 180.0 / PI
            val hue = ColorMath.sanitizeDegrees(atanDegrees)

            val ac = p2 * vc.nbb
            val j = 100.0 * (ac / vc.aw).pow(vc.c * vc.z)

            val huePrime = if (hue < 20.14) hue + 360.0 else hue
            val eHue = 0.25 * (cos(huePrime * PI / 180.0 + 2.0) + 3.8)
            val p1 = 50000.0 / 13.0 * eHue * vc.nc * vc.ncb
            val t = p1 * hypot(a, b) / (u + 0.305)
            val alpha = (1.64 - 0.29.pow(vc.n)).pow(0.73) * t.pow(0.9)
            val chroma = alpha * sqrt(j / 100.0)

            return Cam16(hue, chroma)
        }

        /**
         * XYZ for a CAM16 `J`, chroma and hue.
         *
         * This is the inverse of [fromXyz] restricted to what the solver needs.
         * The full CAM16 object also carries brightness, colourfulness,
         * saturation and the UCS coordinates; none of those feed a tonal
         * palette, and computing them per candidate would multiply the cost of
         * solving one tone for no visible difference.
         */
        fun xyzForJch(j: Double, chroma: Double, hue: Double): DoubleArray {
            val vc = ViewingConditions.DEFAULT
            val alpha = if (chroma == 0.0 || j == 0.0) {
                0.0
            } else {
                chroma / sqrt(j / 100.0)
            }
            val t = (alpha / (1.64 - 0.29.pow(vc.n)).pow(0.73)).pow(1.0 / 0.9)
            val hRad = hue * PI / 180.0
            val eHue = 0.25 * (cos(hRad + 2.0) + 3.8)
            val ac = vc.aw * (j / 100.0).pow(1.0 / vc.c / vc.z)
            val p1 = eHue * (50000.0 / 13.0) * vc.nc * vc.ncb
            val p2 = ac / vc.nbb

            val hSin = sin(hRad)
            val hCos = cos(hRad)
            val gamma = 23.0 * (p2 + 0.305) * t /
                (23.0 * p1 + 11.0 * t * hCos + 108.0 * t * hSin)

            val a = gamma * hCos
            val b = gamma * hSin
            val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
            val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
            val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0

            val rF = inverseAdaptation(vc, rA) / vc.rgbD[0]
            val gF = inverseAdaptation(vc, gA) / vc.rgbD[1]
            val bF = inverseAdaptation(vc, bA) / vc.rgbD[2]

            return doubleArrayOf(
                1.86206786 * rF - 1.01125463 * gF + 0.14918677 * bF,
                0.38752654 * rF + 0.62144744 * gF - 0.00897398 * bF,
                -0.01584150 * rF - 0.03412294 * gF + 1.04996444 * bF
            )
        }

        private fun adapt(vc: ViewingConditions, response: Double): Double {
            val factor = (vc.fl * abs(response) / 100.0).pow(0.42)
            return sign(response) * 400.0 * factor / (factor + 27.13)
        }

        private fun inverseAdaptation(vc: ViewingConditions, response: Double): Double {
            val base = maxOf(0.0, 27.13 * abs(response) / (400.0 - abs(response)))
            return sign(response) * (100.0 / vc.fl) * base.pow(1.0 / 0.42)
        }
    }
}

/**
 * Maps an HCT triple back into sRGB by solving for the right tone.
 *
 * The solver has two nested searches, both cheap and both bounded:
 *
 *  1. For a candidate chroma, find the CAM16 `J` whose resulting colour has the
 *     requested CIELAB tone. `J` and L* are both monotonic in luminance, so a
 *     bisection converges in a handful of steps.
 *  2. If that colour is outside sRGB, reduce chroma until it fits. The official
 *     library solves this analytically in CAM16-UCS; the result is the same to
 *     within a rounding step, and the search is easier to verify.
 *
 * The alternative - clipping each channel - preserves tone but not hue, which is
 * exactly how a tonal palette stops being a tonal palette.
 */
internal object HctSolver {
    private const val TONE_STEPS = 18
    private const val CHROMA_STEPS = 10

    fun solve(hue: Double, chroma: Double, tone: Double): Int {
        var xyz = xyzForTone(hue, chroma, tone)
        if (!isInGamut(xyz)) {
            var low = 0.0
            var high = chroma
            repeat(CHROMA_STEPS) {
                val mid = (low + high) / 2.0
                val candidate = xyzForTone(hue, mid, tone)
                if (isInGamut(candidate)) {
                    low = mid
                    xyz = candidate
                } else {
                    high = mid
                }
            }
        }
        return ColorMath.argbFromXyz(xyz[0], xyz[1], xyz[2])
    }

    private fun xyzForTone(hue: Double, chroma: Double, tone: Double): DoubleArray {
        var low = 0.0
        var high = 100.0
        var xyz = Cam16.xyzForJch(50.0, chroma, hue)
        repeat(TONE_STEPS) {
            val j = (low + high) / 2.0
            xyz = Cam16.xyzForJch(j, chroma, hue)
            if (ColorMath.lstarFromY(xyz[1]) < tone) low = j else high = j
        }
        return xyz
    }

    private fun isInGamut(xyz: DoubleArray): Boolean {
        val linear = ColorMath.linearRgbFromXyz(xyz[0], xyz[1], xyz[2])
        return linear.all { it >= -0.01 && it <= 100.01 }
    }
}
