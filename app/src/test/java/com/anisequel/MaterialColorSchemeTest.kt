package com.anisequel

import com.anisequel.domain.color.Hct
import com.anisequel.domain.color.MaterialColorSchemes
import com.anisequel.domain.color.MaterialScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Material 3 colour engine, tested without Compose or a device.
 *
 * The values pinned here are Google's own for a `#6750A4` seed, which is the
 * purple Material uses in its documentation. They are the check that this is HCT
 * and not a lookalike: an HSL approximation lands on a similar-looking purple
 * with a primary that is not tone 40, and a CAM16 port with the wrong
 * chromatic-adaptation factors lands seven degrees off in hue while still
 * producing a plausible screen colour.
 */
class MaterialColorSchemeTest {

    private val purpleSeed = 0xFF6750A4.toInt()

    @Test
    fun `HCT of Material's seed is the documented triple`() {
        val hct = Hct.fromInt(purpleSeed)
        assertEquals("hue", 298.98, hct.hue, 0.5)
        assertEquals("chroma", 47.86, hct.chroma, 1.0)
        assertEquals("tone", 40.08, hct.tone, 0.2)
    }

    @Test
    fun `tone 40 of the seed is the light primary`() {
        val light = MaterialColorSchemes.tonalSpot(purpleSeed, dark = false, tintSurfaces = true)
        assertEquals(
            "the light primary is the seed's own tone 40",
            0xFF6750A4.toInt(),
            light.primary
        )
        assertEquals(
            "the light container is tone 90",
            0xFFE9DDFF.toInt(),
            light.primaryContainer
        )
    }

    @Test
    fun `dark primary is tone 80 of the same palette`() {
        val dark = MaterialColorSchemes.tonalSpot(purpleSeed, dark = true, tintSurfaces = true)
        assertEquals(0xFFCFBCFF.toInt(), dark.primary)
        assertEquals(
            "dark containers are tone 30",
            30.0,
            Hct.fromInt(dark.primaryContainer).tone,
            0.5
        )
        assertEquals(0xFFE9DDFF.toInt(), dark.onPrimaryContainer)
    }

    /**
     * The accessibility argument for generating instead of copying.
     *
     * Material's fixed tones are chosen so these pairs hold for any hue. A yellow
     * primary at tone 40 cannot carry white text, so `readableOn` flips the
     * foreground; that is the case this would catch if it ever regressed to
     * "always tone 100".
     */
    @Test
    fun `both schemes keep AA contrast for every hue`() {
        val seeds = listOf(
            "red" to 0xFFD32F2F.toInt(),
            "green" to 0xFF2E7D32.toInt(),
            "yellow" to 0xFFFBC02D.toInt(),
            "blue" to 0xFF1565C0.toInt(),
            "violet" to 0xFF7B1FA2.toInt(),
            "grey" to 0xFF808080.toInt()
        )
        val pairs: List<Pair<String, (MaterialScheme) -> Pair<Int, Int>>> = listOf(
            "onPrimary/primary" to { it.onPrimary to it.primary },
            "onPrimaryContainer/primaryContainer" to { it.onPrimaryContainer to it.primaryContainer },
            "onSurface/surface" to { it.onSurface to it.surface },
            "onSurfaceVariant/surfaceVariant" to { it.onSurfaceVariant to it.surfaceVariant },
            "onSecondary/secondary" to { it.onSecondary to it.secondary },
            "onError/error" to { it.onError to it.error }
        )

        seeds.forEach { (name, seed) ->
            listOf(false, true).forEach { dark ->
                val scheme = MaterialColorSchemes.tonalSpot(seed, dark = dark, tintSurfaces = true)
                pairs.forEach { (pairName, pair) ->
                    val (foreground, background) = pair(scheme)
                    val ratio = contrastRatio(foreground, background)
                    assertTrue(
                        "$name ${if (dark) "dark" else "light"} $pairName is " +
                            "%.2f:1".format(ratio),
                        ratio >= 4.5
                    )
                }
            }
        }
    }

    @Test
    fun `tinted surfaces follow the seed and untinted surfaces do not`() {
        val red = 0xFFD32F2F.toInt()
        val blue = 0xFF1565C0.toInt()

        val redBrand = MaterialColorSchemes.tonalSpot(red, dark = false, tintSurfaces = false)
        val blueBrand = MaterialColorSchemes.tonalSpot(blue, dark = false, tintSurfaces = false)
        assertEquals(
            "a standing accent must not move surfaces",
            redBrand.background,
            blueBrand.background
        )
        assertEquals(redBrand.surfaceContainerHigh, blueBrand.surfaceContainerHigh)

        val redShow = MaterialColorSchemes.tonalSpot(red, dark = false, tintSurfaces = true)
        val blueShow = MaterialColorSchemes.tonalSpot(blue, dark = false, tintSurfaces = true)
        assertNotEquals(
            "an anime layer is opened for one show, so its backdrop is that show's",
            redShow.background,
            blueShow.background
        )
    }

    @Test
    fun `a grey seed uses the brand hue instead of an arbitrary one`() {
        val grey = MaterialColorSchemes.tonalSpot(0xFF808080.toInt(), dark = false, tintSurfaces = true)
        val hue = Hct.fromInt(grey.primary).hue
        val delta = angularDistance(hue, MaterialColorSchemes.BRAND_HUE)

        assertTrue(
            "a grey cover's hue is noise; the primary sits $delta degrees from the " +
                "brand instead of being a random red or green",
            delta < 10.0
        )
        assertTrue(
            "and the chroma floor keeps it from being a grey scheme",
            Hct.fromInt(grey.primary).chroma > 15.0
        )
    }

    @Test
    fun `light and dark are both generated and are not the same scheme`() {
        val light = MaterialColorSchemes.tonalSpot(purpleSeed, dark = false, tintSurfaces = true)
        val dark = MaterialColorSchemes.tonalSpot(purpleSeed, dark = true, tintSurfaces = true)

        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.onSurface, dark.onSurface)
        assertTrue(
            "the dark background is dark: tone 6",
            relativeLuminance(dark.background) < 0.02
        )
        assertTrue(
            "the light background is light: tone 98",
            relativeLuminance(light.background) > 0.8
        )
    }

    private fun contrastRatio(a: Int, b: Int): Double {
        val first = relativeLuminance(a)
        val second = relativeLuminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun relativeLuminance(argb: Int): Double {
        fun channel(component: Int): Double {
            val c = component / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel((argb shr 16) and 0xFF) +
            0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }

    private fun angularDistance(a: Double, b: Double): Double {
        val raw = kotlin.math.abs(a - b) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }
}
