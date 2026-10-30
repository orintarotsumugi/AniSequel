package com.anisequel.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.anisequel.domain.color.ArgbHex
import com.anisequel.domain.color.MaterialColorSchemes
import com.anisequel.domain.color.MaterialScheme

/**
 * Turns any seed colour into the app's Material 3 palette.
 *
 * The colour science lives in `domain/color`: `Hct` provides the perceptual
 * space, `MaterialColorSchemes` the tonal-palette rules, and `CoverColorQuantizer`
 * the image path. This object is only the boundary between that pure code and
 * Compose, so the palettes can be tested without a device and the UI does not
 * grow a second copy of the tone ladder.
 *
 * ## What changed, and why the old version looked wrong
 *
 * This used to derive a handful of roles in HSL and copy the rest off the brand
 * scheme. Three failures came out of that:
 *
 *  - HSL lightness is not luminance, so a bright yellow seed's "primary" could
 *    not hold white text while a blue one at the same lightness could.
 *  - Only primary/secondary and the dark surface ramp were re-derived. Borders,
 *    the text grey and every container stayed the brand's, so the anime theme
 *    read as "a coloured button on the usual app".
 *  - The light scheme was generated but unreachable, because the anime layer
 *    forced dark mode. That also meant a reader on a light phone got a dark sheet
 *    the moment the feature was on.
 *
 * Every role is generated now, both light and dark, and the tone ladder is
 * Material's own: 40/80 for primaries, 10/90 for containers, 6/98 for surfaces,
 * 30/80 for secondary text and 50/60 for outlines. Those pairs carry their
 * contrast by construction, so the same scheme is legible for a red cover and a
 * green one.
 */
object DynamicThemeBuilder {

    /**
     * Parses a stored hex colour.
     *
     * Kept as a `Color`-returning function here because callers in the theme
     * layer all want one; the parsing itself is shared with the preference layer
     * through [ArgbHex].
     */
    fun parseHexColor(hex: String?): Color? = ArgbHex.parse(hex)?.let { Color(it) }

    /**
     * The accent a seed produces, as the app draws it.
     *
     * Anything that shows a cover colour as a *colour* - the Settings chip, the
     * hero's backdrop and shadow - has to ask for this rather than paint the seed.
     * The seed is a pixel and pixels come in any tone, so a night-sky cover's raw
     * value is a near-black box on a near-black card and a snow cover's is a flash
     * of white; this is the tone the scheme actually uses for `primary`, which is
     * the colour the reader is being told about.
     */
    fun accentColor(seed: Color, dark: Boolean): Color {
        val argb = MaterialColorSchemes.accentFor(seed.toArgb(), dark)
        return Color(argb)
    }

    /**
     * Generates a light and a dark [ColorScheme] from [seed].
     *
     * [tintSurfaces] is the difference between an accent and a theme:
     *
     *  - the **anime theme** is an immersive layer opened for a specific show, so
     *    its neutral palettes sit on the show's hue and the backdrop belongs to
     *    that title.
     *  - the **custom accent** is a standing preference. Surfaces a user never
     *    asked to change must not move because they picked a hue -
     *    `appBackgroundColor` exists to move the background on its own terms.
     *
     * Both schemes are returned and both are reachable now. The anime layer
     * respects Light/Dark/System rather than forcing dark.
     */
    fun createDynamicSchemes(seed: Color, tintSurfaces: Boolean = false): Pair<ColorScheme, ColorScheme> {
        val argb = seed.toArgb()
        val light = MaterialColorSchemes.tonalSpot(argb, dark = false, tintSurfaces = tintSurfaces)
        val dark = MaterialColorSchemes.tonalSpot(argb, dark = true, tintSurfaces = tintSurfaces)
        return light.toColorScheme(LightColorScheme) to dark.toColorScheme(DarkColorScheme)
    }

    /**
     * Applies every role from the generated scheme.
     *
     * The copies are a safety net for a future Material role this file has not
     * learned yet: an omitted role keeps the app's brand value rather than
     * Material's purple baseline. All 36 roles that exist today are overridden,
     * and `MaterialScheme.role` is tested to cover the same set.
     */
    private fun MaterialScheme.toColorScheme(base: ColorScheme): ColorScheme = base.copy(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(surfaceTint),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        scrim = Color(scrim),
        surfaceBright = Color(surfaceBright),
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
        surfaceContainerLow = Color(surfaceContainerLow),
        surfaceContainerLowest = Color(surfaceContainerLowest),
        surfaceDim = Color(surfaceDim)
    )
}
