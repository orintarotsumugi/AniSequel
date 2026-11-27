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
    val surfaceDim: Int,
    // The twelve "fixed" roles: the same tones in light and dark, so a component
    // that opts out of the mode switch (a date picker's day cell, a snackbar
    // action) keeps the seed's colour instead of falling back to Material's
    // purple. They were missing entirely, which meant every one of them stayed at
    // the baseline scheme's value - the one place a cover-derived theme still
    // showed Material's default.
    val primaryFixed: Int,
    val primaryFixedDim: Int,
    val onPrimaryFixed: Int,
    val onPrimaryFixedVariant: Int,
    val secondaryFixed: Int,
    val secondaryFixedDim: Int,
    val onSecondaryFixed: Int,
    val onSecondaryFixedVariant: Int,
    val tertiaryFixed: Int,
    val tertiaryFixedDim: Int,
    val onTertiaryFixed: Int,
    val onTertiaryFixedVariant: Int
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
        "primaryFixed" -> primaryFixed
        "primaryFixedDim" -> primaryFixedDim
        "onPrimaryFixed" -> onPrimaryFixed
        "onPrimaryFixedVariant" -> onPrimaryFixedVariant
        "secondaryFixed" -> secondaryFixed
        "secondaryFixedDim" -> secondaryFixedDim
        "onSecondaryFixed" -> onSecondaryFixed
        "onSecondaryFixedVariant" -> onSecondaryFixedVariant
        "tertiaryFixed" -> tertiaryFixed
        "tertiaryFixedDim" -> tertiaryFixedDim
        "onTertiaryFixed" -> onTertiaryFixed
        "onTertiaryFixedVariant" -> onTertiaryFixedVariant
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
            "surfaceContainerLow", "surfaceContainerLowest", "surfaceDim",
            "primaryFixed", "primaryFixedDim", "onPrimaryFixed", "onPrimaryFixedVariant",
            "secondaryFixed", "secondaryFixedDim", "onSecondaryFixed",
            "onSecondaryFixedVariant", "tertiaryFixed", "tertiaryFixedDim",
            "onTertiaryFixed", "onTertiaryFixedVariant"
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
 *  - **tertiary** - the cover's own second hue when the quantizer found one, and
 *    the hue rotated 60 degrees otherwise, for accents that are related but
 *    distinguishable
 *  - **neutral** - the seed's hue at an eighth of its chroma (floored, capped,
 *    see [tonalSpot]): surfaces
 *  - **neutral variant** - the same, four chroma more: inputs, outlines
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
 *    blue-grey hue instead of the seed's. When true, `tintStrength` moves that
 *    neutral ramp between the brand and the seed before the tone ladder samples
 *    it - the retained theme's Subtle/Balanced/Full choice.
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

    /**
     * How coloured a cover's second hue must be before it becomes tertiary.
     *
     * Below this it is a tinted grey, and the 60-degree rotation produces a
     * clearer companion than the artwork would have.
     */
    private const val MIN_ACCENT_CHROMA: Double = 24.0

    /**
     * How far the second hue must sit from the seed before it counts as one.
     *
     * Tonal spot's tertiary is 60 degrees away; anything under 25 is close enough
     * to read as the same colour at tone 40, so the rotation is better.
     */
    private const val MIN_ACCENT_HUE_DISTANCE: Double = 25.0

    /**
     * The most chroma a cover's second hue may bring to tertiary.
     *
     * Primary is capped at its own chroma; a neon accent at chroma 100 would make
     * the tertiary container the loudest surface on the page and break the
     * primary/secondary/tertiary hierarchy the ladder is meant to express.
     */
    private const val MAX_TERTIARY_CHROMA: Double = 60.0

    /**
     * The tinted surface ramp's chroma bounds: low enough that a grey cover still
     * gets a recognisably tinted page, high enough that a vivid one does not
     * repaint the containers as coloured cards.
     *
     * Raised from 8/12 in 2.1.1. At the old ceiling a vivid cover's page was a
     * near-grey with a coloured button on it: `primaryChroma / 8` for a chroma-100
     * cover was capped at 12, which is about a fifth of the accent sitting right
     * above it, so the "the whole app takes on this show's colour" promise held for
     * the controls and not for the page. The tone ladder is what carries contrast - 
     * tone 6/98 surfaces against tone 90/10 text, for any chroma - so moving the
     * ceiling up makes the page more coloured without touching a single contrast
     * pair. `MaterialColorSchemeTest` still checks every AA pair at every seed.
     */
    private const val MIN_TINTED_NEUTRAL_CHROMA: Double = 10.0
    private const val MAX_TINTED_NEUTRAL_CHROMA: Double = 16.0

    /** WCAG AA for normal text; the tone ladder is designed around it. */
    private const val MIN_TEXT_CONTRAST: Double = 4.5

    /** The tone the primary sits at: Material's 40 in light, 80 in dark. */
    private const val PRIMARY_TONE_LIGHT = 40
    private const val PRIMARY_TONE_DARK = 80

    /**
     * The colour a cover seed produces as the app's accent.
     *
     * A seed is a pixel, and pixels come in any tone: a night-sky cover scores a
     * near-black bin and a snow cover a near-white one. The *scheme* never shows
     * that raw value - it samples the seed's palette at tone 40 or 80 - so
     * anything that draws the seed as a colour (the Settings chip, the hero's
     * backdrop and shadow) has to take the same tone or it disagrees with the
     * buttons sitting next to it. It used to draw the raw pixel, which is why a
     * dark poster's "Now matching this anime" chip was a black box nobody could
     * tell was a colour, and a pale one flashed white behind the poster before
     * the image loaded.
     */
    fun accentFor(seedArgb: Int, dark: Boolean): Int =
        primaryPalette(Hct.fromInt(seedArgb)).tone(if (dark) PRIMARY_TONE_DARK else PRIMARY_TONE_LIGHT)

    /**
     * The accent as three tones of the seed's own palette, for a gradient.
     *
     * [accentFor] answers "what colour is this title?" with one tone. A gradient
     * needs three, and they have to come from the same palette or the chip stops
     * being a reading of the artwork at all. The middle stop is exactly
     * [accentFor]'s tone, so this can be used where a flat accent was without the
     * swatch disagreeing with the scheme's `primary` - the property
     * `AnimeThemeSurfaceTest` pins.
     *
     * The ends are chosen in tone, not hue or chroma: a dark ramp runs from a
     * brighter tone down through the primary, a light one from a deeper tone up
     * through it. Contrast at the ends is not load-bearing - the foreground beside
     * the swatch is drawn on the card, not on the gradient.
     */
    fun accentRamp(seedArgb: Int, dark: Boolean): IntArray {
        val palette = primaryPalette(Hct.fromInt(seedArgb))
        return if (dark) {
            intArrayOf(palette.tone(90), palette.tone(PRIMARY_TONE_DARK), palette.tone(60))
        } else {
            intArrayOf(palette.tone(20), palette.tone(PRIMARY_TONE_LIGHT), palette.tone(60))
        }
    }

    /**
     * The primary palette for [seed] - the hue and chroma rules in one place, so
     * [accentFor] and [tonalSpot] cannot drift into describing two different
     * "the anime's colour".
     */
    private fun primaryPalette(seed: Hct): TonalPalette {
        if (seed.chroma < MIN_USABLE_CHROMA) {
            return TonalPalette(BRAND_HUE, MIN_PRIMARY_CHROMA)
        }
        return TonalPalette(seed.hue, maxOf(seed.chroma, MIN_PRIMARY_CHROMA))
    }

    /**
     * The tertiary palette from a cover's second hue, or null when there is none
     * worth using.
     *
     * The accent is already a scored region of the artwork - the quantizer only
     * reports one at chroma 24 or above and 40 degrees from the seed - so the
     * checks here are about the *scheme*, not the picture: an accent that is
     * nearly grey would make tertiary muddier than the rotation it replaces, one
     * within [MIN_ACCENT_HUE_DISTANCE] of the seed is not a second colour, and an
     * extremely vivid one is capped so tertiary stays a companion to primary
     * rather than outshouting it.
     */
    private fun accentPalette(
        accentArgb: Int?,
        hue: Double,
        primaryChroma: Double
    ): TonalPalette? {
        val accent = accentArgb?.let { Hct.fromInt(it) } ?: return null
        if (accent.chroma < MIN_ACCENT_CHROMA) return null
        if (angularDistance(accent.hue, hue) < MIN_ACCENT_HUE_DISTANCE) return null
        return TonalPalette(
            accent.hue,
            accent.chroma.coerceIn(MIN_PRIMARY_CHROMA, MAX_TERTIARY_CHROMA)
        )
    }

    /** Shortest distance between two hues, in degrees. */
    private fun angularDistance(a: Double, b: Double): Double {
        val raw = ((a - b) % 360.0 + 360.0) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }

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
        tintSurfaces: Boolean,
        /**
         * How far the neutral palettes lean toward the seed's hue, 0..1.
         *
         * 1 is the immersive sheet's backdrop and the existing behaviour; 0 is
         * the brand's neutral ramp. The retained theme used to generate at 1 and
         * then lerp two finished schemes in RGB, which produced a surface ramp
         * that was neither the cover's nor the brand's and could step unevenly
         * because RGB has no idea that the tones were chosen on a ladder. This
         * interpolates the *inputs* to the ladder instead, so Subtle and Balanced
         * get a coherent ramp that is still recognisably from the cover.
         */
        tintStrength: Float = 1f,
        /**
         * The artwork's second hue, when the quantizer found one.
         *
         * A blind +60 rotation is Material's default because a wallpaper has no
         * better information; a cover does. When the accent is a genuinely
         * different hue, tertiary - and the three fixed roles built from it - are
         * the poster's own second colour instead of a synthetic neighbour, which
         * is what makes the chips in the detail page look sampled from the art.
         * Null, a grey accent, or an accent within [MIN_ACCENT_HUE_DISTANCE] of
         * the seed falls back to the rotation, so a one-hue cover is unchanged.
         */
        accentArgb: Int? = null
    ): MaterialScheme {
        val strength = tintStrength.coerceIn(0f, 1f).toDouble()
        val seed = Hct.fromInt(seedArgb)
        val primary = primaryPalette(seed)
        val primaryChroma = primary.chroma
        val hue = primary.hue

        val secondary = TonalPalette(
            hue,
            maxOf(primaryChroma - 32.0, primaryChroma * 0.5)
        )

        val tertiary = accentPalette(accentArgb, hue, primaryChroma) ?: TonalPalette(
            ColorMath.sanitizeDegrees(hue + 60.0),
            primaryChroma
        )
        // The error palette is fixed in Material 3 - red means error in every
        // scheme, and deriving it from a red cover would make an error state
        // indistinguishable from the accent.
        val error = TonalPalette(25.0, 84.0)

        val neutralChroma = if (tintSurfaces) {
            // Material caps its neutral palettes (4 and 8 chroma) because a
            // wallpaper is a standing choice the user did not make for one app.
            // This is the immersive path, so the cast is allowed to be stronger -
            // but a vivid cover at chroma 100 would put the surfaces at 12.5 and
            // turn `surfaceContainerHighest` into a pastel card instead of a grey
            // one, so the tint is capped where it stops reading as a backdrop.
            lerpDouble(
                BRAND_NEUTRAL_CHROMA,
                (primaryChroma / 8.0).coerceIn(
                    MIN_TINTED_NEUTRAL_CHROMA,
                    MAX_TINTED_NEUTRAL_CHROMA
                ),
                strength
            )
        } else {
            BRAND_NEUTRAL_CHROMA
        }
        val neutralHue = if (tintSurfaces) {
            // Shortest arc, not a raw difference: the seed at 350 degrees and the
            // brand at 211 should meet around 280, not travel through green
            // because 211 - 350 is negative.
            val delta = ((BRAND_HUE - hue + 540.0) % 360.0) - 180.0
            ColorMath.sanitizeDegrees(hue + delta * (1.0 - strength))
        } else {
            BRAND_HUE
        }
        val neutral = TonalPalette(neutralHue, neutralChroma)
        val neutralVariant = TonalPalette(neutralHue, neutralChroma + 4.0)

        return if (dark) {
            darkScheme(primary, secondary, tertiary, error, neutral, neutralVariant)
        } else {
            lightScheme(primary, secondary, tertiary, error, neutral, neutralVariant)
        }
    }

    private fun lerpDouble(from: Double, to: Double, fraction: Double): Double =
        from + (to - from) * fraction

    private fun lightScheme(
        primary: TonalPalette,
        secondary: TonalPalette,
        tertiary: TonalPalette,
        error: TonalPalette,
        neutral: TonalPalette,
        neutralVariant: TonalPalette
    ): MaterialScheme {
        val primaryColor = primary.tone(PRIMARY_TONE_LIGHT)
        val secondaryColor = secondary.tone(PRIMARY_TONE_LIGHT)
        val tertiaryColor = tertiary.tone(PRIMARY_TONE_LIGHT)
        val errorColor = error.tone(PRIMARY_TONE_LIGHT)

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
            surfaceDim = neutral.tone(87),
            // Fixed tones are mode-independent by definition: 90/80 for the fill
            // and its dimmed pair, 10/30 for the label. Material's own schemes use
            // these exact tones in both modes.
            primaryFixed = primary.tone(90),
            primaryFixedDim = primary.tone(80),
            onPrimaryFixed = primary.tone(10),
            onPrimaryFixedVariant = primary.tone(30),
            secondaryFixed = secondary.tone(90),
            secondaryFixedDim = secondary.tone(80),
            onSecondaryFixed = secondary.tone(10),
            onSecondaryFixedVariant = secondary.tone(30),
            tertiaryFixed = tertiary.tone(90),
            tertiaryFixedDim = tertiary.tone(80),
            onTertiaryFixed = tertiary.tone(10),
            onTertiaryFixedVariant = tertiary.tone(30)
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
        val primaryColor = primary.tone(PRIMARY_TONE_DARK)
        val secondaryColor = secondary.tone(PRIMARY_TONE_DARK)
        val tertiaryColor = tertiary.tone(PRIMARY_TONE_DARK)
        val errorColor = error.tone(PRIMARY_TONE_DARK)

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
            surfaceDim = neutral.tone(6),
            // Same tones as the light scheme on purpose - see the note there.
            primaryFixed = primary.tone(90),
            primaryFixedDim = primary.tone(80),
            onPrimaryFixed = primary.tone(10),
            onPrimaryFixedVariant = primary.tone(30),
            secondaryFixed = secondary.tone(90),
            secondaryFixedDim = secondary.tone(80),
            onSecondaryFixed = secondary.tone(10),
            onSecondaryFixedVariant = secondary.tone(30),
            tertiaryFixed = tertiary.tone(90),
            tertiaryFixedDim = tertiary.tone(80),
            onTertiaryFixed = tertiary.tone(10),
            onTertiaryFixedVariant = tertiary.tone(30)
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
