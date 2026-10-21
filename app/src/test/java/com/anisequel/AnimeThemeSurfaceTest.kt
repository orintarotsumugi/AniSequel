package com.anisequel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.anisequel.domain.color.Hct
import com.anisequel.ui.theme.DynamicThemeBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The anime theme is supposed to theme the app around the title that is open.
 *
 * ## What was broken
 *
 * `DynamicThemeBuilder.createDynamicSchemes` derived the whole accent family from the
 * seed and then handed back the **brand's fixed navy surfaces**, whatever the seed was:
 *
 * ```kotlin
 * background = DarkBgMain            // #0A1017, always
 * surface = DarkSurface              // #141F2B, always
 * surfaceContainerHigh = DarkSurfaceContainerHigh   // #21303F, always
 * ```
 *
 * So opening Chainsaw Man produced a red accent on a `#0A1017` backdrop, and opening
 * Frieren produced a pale blue accent on the identical `#0A1017` backdrop. The setting
 * is called the anime theme and its entire effect was a red button on a grey app:
 * everything a reader actually looks at while reading - the background, and the
 * surfaces the cards sit on - is the one thing it never reached.
 *
 * ## Why the surfaces are tinted *only* for this caller
 *
 * Two callers share this builder and they want opposite things. The anime theme is an
 * immersive layer the reader opened a specific show for. The custom accent in Settings
 * is a standing preference, and surfaces a user did not ask to change must not change
 * because they picked a hue - `appBackgroundColor` exists precisely to move the
 * background on purpose. Hence the opt-in flag rather than doing it for both.
 *
 * ## What the tone ladder changed about this file
 *
 * The old tests asserted HSL saturation and lightness. HSL lightness is not luminance,
 * so "same lightness" did not mean "same contrast" - two hues at the same HSL lightness
 * can be wildly different to read. The builder now uses HCT, where tone *is* CIELAB
 * L*, and these tests assert the property that actually matters: the tinted ramp sits
 * on the same luminance ladder as the brand's, and its hue is the show's.
 */
@RunWith(RobolectricTestRunner::class)
class AnimeThemeSurfaceTest {

    /** Four seeds spanning the hue circle, chosen for how differently they behave. */
    private val seeds = listOf(
        "a blue show" to Color(0xFF7FA9D4),
        "a red show" to Color(0xFFD64B4B),
        "a violet show" to Color(0xFF9B5BD6),
        "a green show" to Color(0xFF4BA86B)
    )

    /** The roles the fix is about. `background` is what a reader sees most of. */
    private fun surfaceRoles(scheme: androidx.compose.material3.ColorScheme) = listOf(
        "background" to scheme.background,
        "surface" to scheme.surface,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh
    )

    @Test
    fun `the anime theme's surfaces take the show's hue`() {
        seeds.forEach { (name, seed) ->
            val untinted = surfaceRoles(DynamicThemeBuilder.createDynamicSchemes(seed).second)
            val tinted = surfaceRoles(DynamicThemeBuilder.createDynamicSchemes(seed, tintSurfaces = true).second)

            tinted.forEach { (role, color) ->
                assertNotEquals(
                    "$name: the anime theme's $role came back identical to the brand " +
                        "navy whatever the show, so opening any title produced the " +
                        "same backdrop - which is the one thing the setting exists to " +
                        "change",
                    untinted.first { it.first == role }.second,
                    color
                )
            }
        }
    }

    /**
     * Two different shows must not produce the same app.
     *
     * This is the assertion that catches the bug on its own, and it is stated
     * separately because it is the honest form of the feature: the previous behaviour
     * satisfied "the accent changes" and failed this.
     */
    @Test
    fun `two different shows do not produce the same background`() {
        val backgrounds = seeds.map { (name, seed) ->
            name to DynamicThemeBuilder.createDynamicSchemes(seed, tintSurfaces = true)
                .second.background.value
        }.toMap()

        assertEquals(
            "every show's tint has to be its own - a red page and a green page " +
                "differed only in their accent before, which left the whole surface " +
                "ladder reading as one app in two colours:\n$backgrounds",
            backgrounds.size,
            backgrounds.values.toSet().size
        )
    }

    /**
     * The custom accent must not move the background.
     *
     * A standing preference, and `appBackgroundColor` is the control that moves the
     * background on purpose. Tinting here would take that away without being asked.
     */
    @Test
    fun `the custom accent leaves the surfaces alone`() {
        val fromBlue = DynamicThemeBuilder.createDynamicSchemes(Color(0xFF7FA9D4)).second
        val fromRed = DynamicThemeBuilder.createDynamicSchemes(Color(0xFFD64B4B)).second

        assertEquals(
            "the custom accent changes the accent, not the surfaces",
            fromBlue.background,
            fromRed.background
        )
        assertEquals(
            "nor the containers the cards are drawn on",
            fromBlue.surfaceContainerHigh,
            fromRed.surfaceContainerHigh
        )
    }

    /**
     * The tint is a hue swap on a fixed tone ladder, not a repaint.
     *
     * Both schemes sample the neutral palette at the same tones. Tone in HCT is
     * CIELAB L*, and L* determines Y, so the tinted and brand surfaces have the same
     * Y by construction. That is the safety argument for tinting at all: `onSurface`
     * keeps the same contrast it had against the brand, whatever the show's hue.
     *
     * The tolerance is for 8-bit rounding, not for a different ramp: a 0.005 change in
     * luminance is half a channel step on a dark surface.
     */
    @Test
    fun `tinting keeps the tone ladder and therefore text contrast`() {
        val brand = DynamicThemeBuilder.createDynamicSchemes(Color(0xFF7FA9D4)).second
        val tinted = DynamicThemeBuilder.createDynamicSchemes(Color(0xFFD64B4B), tintSurfaces = true).second

        surfaceRoles(brand).zip(surfaceRoles(tinted)).forEach { (brandRole, tintedRole) ->
            val role = brandRole.first
            val brandLuminance = ColorUtils.calculateLuminance(brandRole.second.toArgb()).toDouble()
            val tintedLuminance = ColorUtils.calculateLuminance(tintedRole.second.toArgb()).toDouble()

            assertEquals(
                "$role: the tint moved the surface off the brand's tone ladder, which " +
                    "is what keeps onSurface readable against it",
                brandLuminance,
                tintedLuminance,
                0.005
            )
        }

        assertTrue(
            "onSurface has to stay readable on a tinted surface",
            ColorUtils.calculateContrast(
                tinted.onSurface.toArgb(),
                tinted.background.toArgb()
            ) >= 4.5
        )
    }

    /**
     * The backdrop is the show's hue, not just a different shade of navy.
     *
     * HCT hue is measured on the surface that is actually drawn, so this catches a
     * tint that changed chroma but forgot the hue - which would leave every show on
     * the same blue backdrop again.
     */
    @Test
    fun `the tinted background follows the show's hue`() {
        seeds.forEach { (name, seed) ->
            val tinted = DynamicThemeBuilder.createDynamicSchemes(seed, tintSurfaces = true).second
            val seedHue = Hct.fromInt(seed.toArgb()).hue
            val surfaceHue = Hct.fromInt(tinted.background.toArgb()).hue
            val delta = angularDistance(seedHue, surfaceHue)

            assertTrue(
                "$name: the backdrop sits ${delta} degrees from the cover's hue, so it " +
                    "is not tinted with the show's colour at all",
                delta < 20.0
            )
        }
    }

    /**
     * The accent itself must not regress.
     *
     * The surface work sits on top of a working accent, and a change that left the
     * primary unreadable on the new surfaces would be a worse bug than the one being
     * fixed.
     */
    @Test
    fun `the accent is still a light accent on the tinted path`() {
        seeds.forEach { (name, seed) ->
            val primary = DynamicThemeBuilder.createDynamicSchemes(seed, tintSurfaces = true).second.primary
            assertTrue(
                "$name: the primary is text on this ramp, so it has to stay light " +
                    "(luminance ${ColorUtils.calculateLuminance(primary.toArgb())})",
                ColorUtils.calculateLuminance(primary.toArgb()) > 0.2f
            )
        }
    }

    private fun angularDistance(a: Double, b: Double): Double {
        val raw = kotlin.math.abs(a - b) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }
}
