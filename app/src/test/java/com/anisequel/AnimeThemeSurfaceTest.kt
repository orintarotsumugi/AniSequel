package com.anisequel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
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
     * The tint is a hue swap on a fixed ladder, not a repaint.
     *
     * Lightness is what the text contrast is made of - `onSurface` and
     * `onSurfaceVariant` are fixed greys, so moving *lightness* is what would actually
     * hurt legibility. Every role stays on the brand's own ladder, and the saturation
     * comes down, so a red seed tints rather than repaints.
     */
    @Test
    fun `tinting moves the hue and leaves the lightness alone`() {
        val brand = DynamicThemeBuilder.createDynamicSchemes(Color(0xFF7FA9D4)).second
        val tinted = DynamicThemeBuilder.createDynamicSchemes(Color(0xFFD64B4B), tintSurfaces = true).second

        // `zip` hands back a Pair of Pairs, so this destructures the *one* parameter
        // `forEach` takes into its two halves. Writing it as two parameters -
        // `{ (a, b), (c, d) -> }` - reads as a two-arg lambda that `forEach` has no
        // room for, and the compiler says so.
        surfaceRoles(brand).zip(surfaceRoles(tinted)).forEach { (brandRole, tintedRole) ->
            val role = brandRole.first
            val brandColor = brandRole.second
            val tintedColor = tintedRole.second
            val brandHsl = hslOf(brandColor)
            val tintedHsl = hslOf(tintedColor)

            assertEquals(
                "$role: the surface ramp is a hue swap on a fixed lightness ladder - " +
                    "that is the whole safety argument for tinting it at all",
                brandHsl[2],
                tintedHsl[2],
                0.01f
            )
            assertTrue(
                "$role: the tint has to stay under the brand's own saturation, or a " +
                    "red seed produces a brown card rather than a red-tinted one " +
                    "(brand ${brandHsl[1]}, tinted ${tintedHsl[1]})",
                tintedHsl[1] < brandHsl[1]
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

    private fun hslOf(color: Color): FloatArray {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        return hsl
    }
}