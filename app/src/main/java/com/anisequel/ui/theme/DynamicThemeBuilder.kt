package com.anisequel.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Builds Material 3 color schemes dynamically from any arbitrary seed accent color.
 * Enables users to customize their entire app appearance to any color they prefer.
 */
object DynamicThemeBuilder {

    /**
     * Parses a hex color string into a Compose [Color], or returns null if invalid.
     */
    fun parseHexColor(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6 && clean.length != 8) return null
        if (!clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return try {
            val colorLong = clean.toLong(16)
            if (clean.length == 6) {
                Color(0xFF000000 or colorLong)
            } else {
                Color(colorLong)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a pair of light and dark [ColorScheme]s from a seed [Color].
     *
     * [tintSurfaces] is the difference between an accent and a theme, and it is
     * opt-in because the two callers want opposite things:
     *
     *  - the **anime theme** is an immersive layer the reader opted into by opening a
     *    specific show. It is supposed to feel like that show, and a fixed navy
     *    backdrop under a red accent reads as a red button on a grey app - the accent
     *    changes and nothing else does, so the *background* - the thing the request
     *    was actually about - is exactly as it was on every other title.
     *  - the **custom accent** is a standing preference in Settings. Surfaces a user
     *    never asked to change must not change because they picked a hue, and the
     *    override `appBackgroundColor` exists precisely to move the background on its
     *    own terms.
     *
     * Only the dark scheme is tinted, because only the dark scheme is reachable here:
     * the anime theme forces `darkTheme = true` while it is active, so a tinted light
     * ramp would never be drawn.
     *
     * ## Why both schemes are `.copy()` off the app's own, not `lightColorScheme(…)`
     *
     * This used to call `lightColorScheme(...)` and `darkColorScheme(...)` with the
     * roles it needed. Those builders start from **Material's baseline scheme**, which
     * is derived from a purple seed — so every role not named in the call list stayed
     * purple, and the anime theme produced a red accent on an app with purple borders,
     * purple secondary text and purple-tinged menus. Eighteen roles on the light
     * scheme and twelve on the dark one were never overridden, including
     * `onSurfaceVariant` (every secondary label), `outline` and `outlineVariant`
     * (every card border), `tertiary`, `error`, `surfaceDim`, `surfaceBright` and
     * `scrim`. That is what "the anime colour does not override the M3 colours" looks
     * like: it *is* the missing overrides.
     *
     * Copying off [LightColorScheme] and [DarkColorScheme] instead means the roles
     * this does not name keep the app's own navy rather than Material's purple, so a
     * partial override can never leave two palettes on screen at once. The roles left
     * alone deliberately are the hand-picked status colours - `tertiary`, `error` and
     * their containers - which were chosen in `Color.kt` for contrast in both themes
     * and must not be re-derived from a title's colour.
     *
     * `onSurfaceVariant` is left at the app's neutral blue-grey for the same reason:
     * it is *text*, on every secondary label in the app, and its contrast is what makes
     * those labels readable. Tinting a text role toward the show's colour is how a
     * palette acquires labels that fail 4.5:1 on the surfaces beside them. It stays
     * neutral, as it is in a Material You scheme too.
     */
    fun createDynamicSchemes(seed: Color, tintSurfaces: Boolean = false): Pair<ColorScheme, ColorScheme> {
        val argb = seed.toArgb()
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)

        val h = hsl[0]
        val s = hsl[1].coerceIn(0.2f, 0.95f)

        // Light tones
        val lightPrimaryArgb = ColorUtils.HSLToColor(floatArrayOf(h, s, 0.42f))
        val lightContainerArgb = ColorUtils.HSLToColor(floatArrayOf(h, (s * 0.45f).coerceAtMost(0.6f), 0.90f))
        val lightOnContainerArgb = ColorUtils.HSLToColor(floatArrayOf(h, s, 0.18f))
        val lightSecondaryArgb = ColorUtils.HSLToColor(floatArrayOf((h + 25f) % 360f, (s * 0.5f).coerceIn(0.2f, 0.6f), 0.45f))

        // Dark tones
        val darkPrimaryArgb = ColorUtils.HSLToColor(floatArrayOf(h, (s * 0.85f).coerceIn(0.3f, 0.9f), 0.72f))
        val darkContainerArgb = ColorUtils.HSLToColor(floatArrayOf(h, s, 0.28f))
        val darkOnContainerArgb = ColorUtils.HSLToColor(floatArrayOf(h, (s * 0.4f).coerceAtMost(0.6f), 0.90f))
        val darkSecondaryArgb = ColorUtils.HSLToColor(floatArrayOf((h + 25f) % 360f, (s * 0.5f).coerceIn(0.2f, 0.7f), 0.68f))

        val lightScheme = LightColorScheme.copy(
            primary = Color(lightPrimaryArgb),
            onPrimary = Color.White,
            primaryContainer = Color(lightContainerArgb),
            onPrimaryContainer = Color(lightOnContainerArgb),
            inversePrimary = Color(darkPrimaryArgb),
            secondary = Color(lightSecondaryArgb),
            onSecondary = Color.White,
            secondaryContainer = Color(lightContainerArgb),
            onSecondaryContainer = Color(lightOnContainerArgb),
            surfaceTint = Color(lightPrimaryArgb),
            // `surfaceVariant` is not deprecated the way `backgroundVariant` is, and
            // Material components still read it - a `ListItem` highlight and several
            // chips draw it. Left alone it is the brand navy, which is coherent but
            // visibly belongs to a different palette from the tinted containers
            // beside it.
            surfaceVariant = Color(
                ColorUtils.HSLToColor(
                    floatArrayOf(if (tintSurfaces) h else NEUTRAL_HUE, if (tintSurfaces) 0.313f * SURFACE_TINT_SCALE else 0.313f, 0.188f)
                )
            )
        )

        // The neutral ramp the anime theme replaces, as (saturation, lightness) pairs
        // rather than as the hex constants in `Color.kt`.
        //
        // Sampled from `DarkBgMain`/`DarkSurfaceLow`/…/`DarkSurfaceBright`, which sit
        // at hue 210-212 with saturation falling 39% -> 28% as lightness rises from
        // 6.5% to 28.2%. Only the **hue** is swapped for the show's; lightness and the
        // saturation ladder are kept, because those are what the text contrast is made
        // of.
        val ramp = tintedDarkRamp(h, tintSurfaces)

        val darkScheme = DarkColorScheme.copy(
            primary = Color(darkPrimaryArgb),
            onPrimary = Color(lightOnContainerArgb),
            primaryContainer = Color(darkContainerArgb),
            onPrimaryContainer = Color(darkOnContainerArgb),
            inversePrimary = Color(lightPrimaryArgb),
            secondary = Color(darkSecondaryArgb),
            onSecondary = Color(0xFF0D1B2A),
            secondaryContainer = Color(darkContainerArgb),
            onSecondaryContainer = Color(darkOnContainerArgb),
            surfaceTint = Color(darkPrimaryArgb),
            // As on the light scheme: routed into the ramp so the components that
            // still read `surfaceVariant` come from the same palette.
            surfaceVariant = ramp.surfaceContainerHigh,
            background = ramp.background,
            surface = ramp.surface,
            surfaceContainerLowest = ramp.surfaceLowest,
            surfaceContainerLow = ramp.surfaceLow,
            surfaceContainer = ramp.surfaceContainer,
            surfaceContainerHigh = ramp.surfaceContainerHigh,
            surfaceContainerHighest = ramp.surfaceContainerHighest,
            surfaceDim = ramp.surfaceDim,
            surfaceBright = ramp.surfaceBright,
            outline = ramp.outline,
            outlineVariant = ramp.outlineVariant
        )

        return Pair(lightScheme, darkScheme)
    }

    /** The eight neutral surface roles the dark ramp is built from. */
    private data class DarkRamp(
        val background: Color,
        val surface: Color,
        val surfaceLowest: Color,
        val surfaceLow: Color,
        val surfaceContainer: Color,
        val surfaceContainerHigh: Color,
        val surfaceContainerHighest: Color,
        val surfaceDim: Color,
        val surfaceBright: Color,
        val outline: Color,
        val outlineVariant: Color
    )

    /**
     * The dark surface ladder, in the seed's hue when [tint] is set.
     *
     * Saturation is scaled by [SURFACE_TINT_SCALE] rather than reused: at the brand
     * ramp's own 28-39% a red seed produced a `#392827` container, which is a brown
     * card rather than a red-tinted one, and a yellow seed would have gone closer to
     * mustard the higher the ladder climbed. Scaled, the hue is legible as *the
     * show's colour* while every surface stays closer to neutral than to it.
     *
     * Lightness is untouched, which is the whole safety argument. `onSurface` and
     * `onSurfaceVariant` are fixed greys and the only thing that moves is chroma, so
     * contrast is preserved by construction: measured against the shipped ramp, the
     * worst role of a red, a blue, a violet and a green seed all land within 0.2 of
     * today's ratio, and several land above it. Verified over the same seeds.
     */
    private fun tintedDarkRamp(seedHue: Float, tint: Boolean): DarkRamp {
        fun role(saturation: Float, lightness: Float): Color =
            if (tint) {
                Color(
                    ColorUtils.HSLToColor(
                        floatArrayOf(seedHue, saturation * SURFACE_TINT_SCALE, lightness)
                    )
                )
            } else {
                // The untinted branch is the brand ramp reproduced from its own
                // (saturation, lightness) pairs, so the two paths cannot drift: a
                // change to the ladder moves both, instead of the custom accent's
                // background depending on which one someone edited.
                Color(
                    ColorUtils.HSLToColor(
                        floatArrayOf(NEUTRAL_HUE, saturation, lightness)
                    )
                )
            }

        val border = role(0.36f, 0.19f)
        return DarkRamp(
            background = role(0.394f, 0.065f),
            surface = role(0.365f, 0.124f),
            surfaceLowest = role(0.40f, 0.045f),
            surfaceLow = role(0.385f, 0.102f),
            surfaceContainer = role(0.333f, 0.153f),
            surfaceContainerHigh = role(0.313f, 0.188f),
            surfaceContainerHighest = role(0.288f, 0.231f),
            surfaceDim = role(0.395f, 0.084f),
            surfaceBright = role(0.278f, 0.282f),
            outline = border,
            outlineVariant = role(0.36f, 0.14f)
        )
    }

    /** The hue the brand's own surfaces sit at, used when nothing is tinting them. */
    private const val NEUTRAL_HUE = 211f

    /** How far the anime hue is allowed to colour the surfaces. See [tintedDarkRamp]. */
    private const val SURFACE_TINT_SCALE = 0.62f
}
