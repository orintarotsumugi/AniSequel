package com.anisequel.domain.color

/**
 * One complete Material 3 colour scheme, as ARGB values.
 *
 * Deliberately a plain data class of `Int`s: nothing here knows about Compose, so
 * the palette can be generated and contrast-tested on the JVM, and the UI layer
 * only has to turn it into a `ColorScheme`.
 */
data class MaterialScheme(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val inversePrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val surfaceTint: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val outline: Int,
    val outlineVariant: Int,
    val scrim: Int,
    val surfaceBright: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainerLowest: Int,
    val surfaceDim: Int
) {
    /** Role lookup for tests and diagnostics. */
    fun role(name: String): Int = when (name) {
        "primary" -> primary
        "onPrimary" -> onPrimary
        "primaryContainer" -> primaryContainer
        "onPrimaryContainer" -> onPrimaryContainer
        "inversePrimary" -> inversePrimary
        "secondary" -> secondary
        "onSecondary" -> onSecondary
        "secondaryContainer" -> secondaryContainer
        "onSecondaryContainer" -> onSecondaryContainer
        "tertiary" -> tertiary
        "onTertiary" -> onTertiary
        "tertiaryContainer" -> tertiaryContainer
        "onTertiaryContainer" -> onTertiaryContainer
        "error" -> error
        "onError" -> onError
        "errorContainer" -> errorContainer
        "onErrorContainer" -> onErrorContainer
        "background" -> background
        "onBackground" -> onBackground
        "surface" -> surface
        "onSurface" -> onSurface
        "surfaceVariant" -> surfaceVariant
        "onSurfaceVariant" -> onSurfaceVariant
        "surfaceTint" -> surfaceTint
        "inverseSurface" -> inverseSurface
        "inverseOnSurface" -> inverseOnSurface
        "outline" -> outline
        "outlineVariant" -> outlineVariant
        "scrim" -> scrim
        "surfaceBright" -> surfaceBright
        "surfaceContainer" -> surfaceContainer
        "surfaceContainerHigh" -> surfaceContainerHigh
        "surfaceContainerHighest" -> surfaceContainerHighest
        "surfaceContainerLow" -> surfaceContainerLow
        "surfaceContainerLowest" -> surfaceContainerLowest
        "surfaceDim" -> surfaceDim
        else -> throw IllegalArgumentException("unknown Material colour role '$name'")
    }

    companion object {
        /** Every role, in the order `ColorScheme`'s own documentation lists them. */
        val ROLE_NAMES: List<String> = listOf(
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
            "inversePrimary", "secondary", "onSecondary", "secondaryContainer",
            "onSecondaryContainer", "tertiary", "onTertiary", "tertiaryContainer",
            "onTertiaryContainer", "error", "onError", "errorContainer",
            "onErrorContainer", "background", "onBackground", "surface", "onSurface",
            "surfaceVariant", "onSurfaceVariant", "surfaceTint", "inverseSurface",
            "inverseOnSurface", "outline", "outlineVariant", "scrim", "surfaceBright",
            "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
            "surfaceContainerLow", "surfaceContainerLowest", "surfaceDim"
        )
    }
}

/**
 * Builds the app's schemes from a single seed with Material 3's tonal-palette
 * rules.
 *
 * ## Tonal spot, not "the seed at five lightnesses"
 *
 * Material You's default scheme ("tonal spot") uses five palettes derived from
 * one seed:
 *
 *  - **primary** - the seed's hue, at the seed's chroma (floored here, see below)
 *  - **secondary** - the same hue, much less chroma, so secondary actions sit
 *    beside primary without competing with it
 *  - **tertiary** - the hue rotated 60 degrees, for accents that are related but
 *    distinguishable
 *  - **neutral** - the seed's hue at a twentieth of the chroma: surfaces
 *  - **neutral variant** - the same, slightly more chromatic: inputs, outlines
 *
 * Each palette is then sampled at fixed tones. Those fixed tones are the whole
 * accessibility argument: `surface` is tone 98 in light and 6 in dark, and
 * `onSurface` is 10 and 90 respectively, so the text/background pair has the
 * same contrast for a red cover as for a blue one. HSL could not promise that,
 * because HSL lightness is not luminance.
 *
 * ## Two deliberate departures from "exactly the seed"
 *
 * 1. **A chroma floor.** A washed-out cover (grey sky, white dress) would
 *    otherwise produce a washed-out app that does not read as that title's
 *    colour at all. `MIN_PRIMARY_CHROMA` keeps the hue recognisable while the
 *    tone ladder keeps it readable. The floor is applied to the primary palette
 *    only; secondary and neutral follow it down proportionally, so the scheme
 *    stays coherent.
 * 2. **`tintSurfaces`.** The anime layer wants its backdrop to be the show's
 *    colour; a standing custom accent must not repaint surfaces the user did not
 *    ask to change. When false, the neutral palettes sit on the brand's own
 *    blue-grey hue instead of the seed's.
 */
object MaterialColorSchemes {

    /**
     * The hue the app's own navy sits at. Used for surfaces when the seed is a
     * custom accent rather than an immersive anime layer - and for a seed with no
     * usable hue at all, where reusing the brand is better than guessing.
     */
    const val BRAND_HUE: Double = 211.0

    /** How much chroma a seed must have before its hue is trusted. */
    private const val MIN_USABLE_CHROMA: Double = 4.0

    /** Below this a title's colour stops looking like a theme and starts looking grey. */
    private const val MIN_PRIMARY_CHROMA: Double = 24.0

    /** The app's neutral blue-grey for untinted surfaces. */
    private const val BRAND_NEUTRAL_CHROMA: Double = 6.0

    /** WCAG AA for normal text; the tone ladder is designed around it. */
    private const val MIN_TEXT_CONTRAST: Double = 4.5

    /**
     * Builds the light and dark schemes for [seedArgb].
     *
     * Both are always generated even though the anime layer used to force dark
     * mode: respecting the user's Light/Dark/System choice is what makes this
     * behave like wallpaper-derived dynamic colour instead of a separate theme.
     */
    fun tonalSpot(
        seedArgb: Int,
        dark: Boolean,
        tintSurfaces: Boolean
    ): MaterialScheme {
        val seed = Hct.fromInt(seedArgb)
        val usableHue = seed.chroma >= MIN_USABLE_CHROMA
        val hue = if (usableHue) seed.hue else BRAND_HUE
        val primaryChroma = if (usableHue) {
            maxOf(seed.chroma, MIN_PRIMARY_CHROMA)
        } else {
            MIN_PRIMARY_CHROMA
        }

        val primary = TonalPalette(hue, primaryChroma)
        val secondary = TonalPalette(
            hue,
            maxOf(primaryChroma - 32.0, primaryChroma * 0.5)
        )
        val tertiary = TonalPalette(
            ColorMath.sanitizeDegrees(hue + 60.0),
            primaryChroma
        )
        // The error palette is fixed in Material 3 - red means error in every
        // scheme, and deriving it from a red cover would make an error state
        // indistinguishable from the accent.
        val error = TonalPalette(25.0, 84.0)

        val neutralChroma = if (tintSurfaces) {
            maxOf(primaryChroma / 8.0, 8.0)
        } else {
            BRAND_NEUTRAL_CHROMA
        }
        val neutralHue = if (tintSurfaces) hue else BRAND_HUE
        val neutral = TonalPalette(neutralHue, neutralChroma)
        val neutralVariant = TonalPalette(neutralHue, neutralChroma + 4.0)

        return if (dark) {
            darkScheme(primary, secondary, tertiary, error, neutral, neutralVariant)
        } else {
            lightScheme(primary, secondary, tertiary, error, neutral, neutralVariant)
        }
    }

    private fun lightScheme(
        primary: TonalPalette,
        secondary: TonalPalette,
        tertiary: TonalPalette,
        error: TonalPalette,
        neutral: TonalPalette,
        neutralVariant: TonalPalette
    ): MaterialScheme {
        val primaryColor = primary.tone(40)
        val secondaryColor = secondary.tone(40)
        val tertiaryColor = tertiary.tone(40)
        val errorColor = error.tone(40)

        return MaterialScheme(
            primary = primaryColor,
            onPrimary = readableOn(primary, primaryColor, preferredTone = 100),
            primaryContainer = primary.tone(90),
            onPrimaryContainer = primary.tone(10),
            inversePrimary = primary.tone(80),
            secondary = secondaryColor,
            onSecondary = readableOn(secondary, secondaryColor, preferredTone = 100),
            secondaryContainer = secondary.tone(90),
            onSecondaryContainer = secondary.tone(10),
            tertiary = tertiaryColor,
            onTertiary = readableOn(tertiary, tertiaryColor, preferredTone = 100),
            tertiaryContainer = tertiary.tone(90),
            onTertiaryContainer = tertiary.tone(10),
            error = errorColor,
            onError = readableOn(error, errorColor, preferredTone = 100),
            errorContainer = error.tone(90),
            onErrorContainer = error.tone(10),
            background = neutral.tone(98),
            onBackground = neutral.tone(10),
            surface = neutral.tone(98),
            onSurface = neutral.tone(10),
            surfaceVariant = neutralVariant.tone(90),
            onSurfaceVariant = neutralVariant.tone(30),
            surfaceTint = primaryColor,
            inverseSurface = neutral.tone(20),
            inverseOnSurface = neutral.tone(95),
            outline = neutralVariant.tone(50),
            outlineVariant = neutralVariant.tone(80),
            scrim = ColorMath.BLACK,
            surfaceBright = neutral.tone(98),
            surfaceContainer = neutral.tone(94),
            surfaceContainerHigh = neutral.tone(92),
            surfaceContainerHighest = neutral.tone(90),
            surfaceContainerLow = neutral.tone(96),
            surfaceContainerLowest = neutral.tone(100),
            surfaceDim = neutral.tone(87)
        )
    }

    private fun darkScheme(
        primary: TonalPalette,
        secondary: TonalPalette,
        tertiary: TonalPalette,
        error: TonalPalette,
        neutral: TonalPalette,
        neutralVariant: TonalPalette
    ): MaterialScheme {
        val primaryColor = primary.tone(80)
        val secondaryColor = secondary.tone(80)
        val tertiaryColor = tertiary.tone(80)
        val errorColor = error.tone(80)

        return MaterialScheme(
            primary = primaryColor,
            onPrimary = readableOn(primary, primaryColor, preferredTone = 20),
            primaryContainer = primary.tone(30),
            onPrimaryContainer = primary.tone(90),
            inversePrimary = primary.tone(40),
            secondary = secondaryColor,
            onSecondary = readableOn(secondary, secondaryColor, preferredTone = 20),
            secondaryContainer = secondary.tone(30),
            onSecondaryContainer = secondary.tone(90),
            tertiary = tertiaryColor,
            onTertiary = readableOn(tertiary, tertiaryColor, preferredTone = 20),
            tertiaryContainer = tertiary.tone(30),
            onTertiaryContainer = tertiary.tone(90),
            error = errorColor,
            onError = readableOn(error, errorColor, preferredTone = 20),
            errorContainer = error.tone(30),
            onErrorContainer = error.tone(90),
            background = neutral.tone(6),
            onBackground = neutral.tone(90),
            surface = neutral.tone(6),
            onSurface = neutral.tone(90),
            surfaceVariant = neutralVariant.tone(30),
            onSurfaceVariant = neutralVariant.tone(80),
            surfaceTint = primaryColor,
            inverseSurface = neutral.tone(90),
            inverseOnSurface = neutral.tone(20),
            outline = neutralVariant.tone(60),
            outlineVariant = neutralVariant.tone(30),
            scrim = ColorMath.BLACK,
            surfaceBright = neutral.tone(24),
            surfaceContainer = neutral.tone(12),
            surfaceContainerHigh = neutral.tone(17),
            surfaceContainerHighest = neutral.tone(22),
            surfaceContainerLow = neutral.tone(10),
            surfaceContainerLowest = neutral.tone(4),
            surfaceDim = neutral.tone(6)
        )
    }

    /**
     * The foreground for a filled colour.
     *
     * Tone 100 on tone 40 is the Material default and is what this returns for
     * most hues. It is not safe for every hue: a yellow or lime tone 40 is bright
     * enough that white text on it fails 4.5:1, and Material's own answer is a
     * contrast-based choice rather than a fixed tone. Try the preferred end
     * first, then whichever of the palette's darkest and lightest tones is
     * actually readable, so a vivid yellow cover gets dark text and a deep blue
     * one gets light text.
     */
    private fun readableOn(
        palette: TonalPalette,
        background: Int,
        preferredTone: Int
    ): Int {
        val preferred = palette.tone(preferredTone)
        if (ColorMath.contrastRatio(preferred, background) >= MIN_TEXT_CONTRAST) {
            return preferred
        }
        val candidates = intArrayOf(palette.tone(0), palette.tone(10), palette.tone(100))
        return candidates.maxByOrNull { ColorMath.contrastRatio(it, background) } ?: preferred
    }
}
