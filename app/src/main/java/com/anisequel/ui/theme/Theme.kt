package com.anisequel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.ThemeMode
import com.anisequel.domain.color.Hct
import com.anisequel.domain.color.TonalPalette
import kotlin.math.roundToInt

internal val DarkColorScheme = darkColorScheme(
    primary = AniBlueLight,
    onPrimary = Color(0xFF00325F),
    primaryContainer = AniBlueContainerDark,
    onPrimaryContainer = Color(0xFFD6E7FF),
    inversePrimary = AniBlue,
    secondary = AniCyan,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004E52),
    onSecondaryContainer = Color(0xFF9CF0F4),
    tertiary = StatusWarningDark,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = StatusWarningContainerDark,
    onTertiaryContainer = Color(0xFFFFE0B8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBgMain,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceContainerHigh,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = Color(0xFF070C12),
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceTint = AniBlueLight,
    inverseSurface = LightSurfaceContainer,
    inverseOnSurface = LightTextPrimary,
    outline = DarkBorder,
    outlineVariant = Color(0xFF24333F),
    scrim = Color(0xFF000000),
)

internal val LightColorScheme = lightColorScheme(
    primary = AniBlue,
    onPrimary = Color.White,
    primaryContainer = AniBlueContainerLight,
    onPrimaryContainer = Color(0xFF001C38),
    inversePrimary = AniBlueLight,
    secondary = Color(0xFF00696B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF0F4),
    onSecondaryContainer = Color(0xFF002021),
    tertiary = StatusWarningLight,
    onTertiary = Color.White,
    tertiaryContainer = StatusWarningContainerLight,
    onTertiaryContainer = Color(0xFF2C1700),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightBgMain,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceTint = AniBlue,
    inverseSurface = DarkSurfaceContainer,
    inverseOnSurface = DarkTextPrimary,
    outline = LightBorder,
    outlineVariant = Color(0xFFD8E1EC),
    scrim = Color(0xFF000000),
)

/**
 * How much of a canvas colour's own chroma survives into its neutral ramp.
 *
 * A picked canvas is not wallpaper colour: it is one field the reader chose,
 * and the cards around it should look like shades of that field rather than
 * like a different palette laid on top. A fraction is enough to carry the hue
 * without turning every elevated container into a pastel copy of the canvas.
 */
private const val CANVAS_NEUTRAL_FRACTION = 0.35
private const val MIN_CANVAS_CHROMA = 2.0
private const val MIN_CANVAS_NEUTRAL_CHROMA = 4.0
private const val MAX_CANVAS_NEUTRAL_CHROMA = 12.0
private const val CANVAS_VARIANT_CHROMA = 4.0

/**
 * The picked canvas colour as a whole surface ramp, not just `background`.
 *
 * Overriding only `background`/`surface` left most of the app on the old ramp:
 * cards sit on `surfaceContainer*`, and sheets and app bars on `surfaceDim`/
 * `surfaceBright`, so a changed canvas looked half-applied - "the colour
 * doesn't stick". Every neutral role is re-derived from the canvas itself, and
 * the `on` roles are derived too, which is what lets a canvas apply in light
 * mode without putting dark-on-dark text on screen.
 *
 * ## Why HCT tones rather than RGB lifts
 *
 * This used to interpolate each channel toward white or black. RGB has no idea
 * which direction is perceptually lighter, so a saturated canvas desaturated
 * unevenly and a mid-tone could put two adjacent containers on nearly the same
 * luminance while a third jumped. HCT tone is CIELAB L*: sampling the canvas's
 * own hue at neighbouring tones gives an evenly spaced elevation ramp and lets
 * the foreground be picked by measured contrast instead of a 0.18 threshold.
 */
internal fun applyCanvasColour(base: ColorScheme, canvas: Color?): ColorScheme {
    if (canvas == null) return base

    val hct = Hct.fromInt(canvas.toArgb())
    // Which side of the canvas the text belongs on is a contrast question, not
    // a tone threshold: a mid-tone canvas can be closer to white than to black
    // in luminance while still having more room on the dark side.
    val canvasIsDark = canvasContrast(Color.White, canvas) >=
        canvasContrast(Color.Black, canvas)
    val onCanvas = if (canvasIsDark) Color.White else Color.Black
    val ramp = CanvasRamp(canvas = canvas, hct = hct, isDark = canvasIsDark)

    return base.copy(
        background = canvas,
        onBackground = onCanvas,
        surface = canvas,
        onSurface = onCanvas,
        surfaceContainerLowest = ramp.lowest(),
        surfaceContainerLow = ramp.surface(4.0),
        surfaceContainer = ramp.surface(8.0),
        surfaceContainerHigh = ramp.surface(13.0),
        surfaceContainerHighest = ramp.surface(18.0),
        surfaceDim = ramp.dim(),
        surfaceBright = ramp.bright(),
        surfaceVariant = ramp.variantSurface(),
        onSurfaceVariant = ramp.onSurfaceVariant(),
        outline = ramp.outline(),
        outlineVariant = ramp.outlineVariant(),
        inverseSurface = ramp.inverseSurface(),
        inverseOnSurface = ramp.inverseOnSurface()
    )
}

/**
 * The surface ladder for one picked canvas.
 *
 * A class rather than four local helpers because every role is sampled from the
 * same two palettes at the same tone origin. Keeping that origin in one object
 * is what stops the container ramp, the borders and the inverse pair drifting
 * onto different hues; it also keeps [applyCanvasColour] itself small enough to
 * read as a role map rather than a colour-science routine.
 */
private class CanvasRamp(
    private val canvas: Color,
    hct: Hct,
    private val isDark: Boolean
) {
    private val neutralChroma = canvasNeutralChroma(hct)
    private val neutral = TonalPalette(hct.hue, neutralChroma)
    private val variant = TonalPalette(hct.hue, neutralChroma + CANVAS_VARIANT_CHROMA)
    private val tone = hct.tone

    // Positive offsets travel toward the lighter end on a dark canvas and the
    // darker end on a light one, which is Material's elevation direction in
    // each mode. The offsets are in HCT tone, so they stay perceptually even
    // whatever the canvas's hue or saturation.
    private val direction = if (isDark) 1.0 else -1.0

    fun surface(offset: Double): Color = Color(
        neutral.tone((tone + offset * direction).roundToInt().coerceIn(0, 100))
    )

    private fun variantAt(offset: Double): Color = Color(
        variant.tone((tone + offset * direction).roundToInt().coerceIn(0, 100))
    )

    fun lowest(): Color = surface(if (isDark) -2.0 else -3.0)

    fun dim(): Color = surface(if (isDark) -2.0 else 4.0)

    fun bright(): Color = surface(if (isDark) 8.0 else -3.0)

    fun variantSurface(): Color = variantAt(8.0)

    fun onSurfaceVariant(): Color = canvasTextColor(
        variant,
        canvas,
        preferredTone = if (isDark) 80 else 30,
        minimum = 4.5f
    )

    fun outline(): Color = canvasTextColor(
        variant,
        canvas,
        preferredTone = if (isDark) 60 else 50,
        minimum = 3.0f
    )

    fun outlineVariant(): Color = variantAt(if (isDark) 30.0 else 20.0)

    fun inverseSurface(): Color = Color(neutral.tone(if (isDark) 90 else 20))

    fun inverseOnSurface(): Color = Color(neutral.tone(if (isDark) 10 else 90))
}

/**
 * A genuinely neutral canvas stays neutral; a coloured one keeps a small,
 * bounded amount of its chroma rather than becoming a saturated card wall.
 */
private fun canvasNeutralChroma(hct: Hct): Double = if (hct.chroma < MIN_CANVAS_CHROMA) {
    0.0
} else {
    (hct.chroma * CANVAS_NEUTRAL_FRACTION).coerceIn(
        MIN_CANVAS_NEUTRAL_CHROMA,
        MAX_CANVAS_NEUTRAL_CHROMA
    )
}

/** Picks a foreground from [palette] that actually holds [minimum] against [canvas]. */
private fun canvasTextColor(
    palette: TonalPalette,
    canvas: Color,
    preferredTone: Int,
    minimum: Float
): Color {
    val preferred = Color(palette.tone(preferredTone))
    if (canvasContrast(preferred, canvas) >= minimum) return preferred
    return CANVAS_TEXT_TONES
        .map { Color(palette.tone(it)) }
        .maxByOrNull { canvasContrast(it, canvas) } ?: preferred
}

private fun canvasContrast(a: Color, b: Color): Float {
    val first = a.luminance()
    val second = b.luminance()
    return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
}

/** Foreground tones tried when the preferred one does not clear the contrast floor. */
private val CANVAS_TEXT_TONES = listOf(0, 10, 20, 30, 40, 60, 70, 80, 90, 100)

/**
 * Status colours Material 3 has no role for.
 *
 * "Airing", "Upcoming" and "Missed" are meanings, not theme roles, but they
 * have to be readable in both themes - which the previous hardcoded
 * `Color(0xFF10B981)` on a translucent chip was not, at roughly 2.6:1 in light
 * mode.
 */
@Immutable
data class StatusColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarningContainer: Color,
    val warningContainer: Color,
    val info: Color,
    val onInfoContainer: Color,
    val infoContainer: Color,
)

/**
 * The light-theme status colours, and the fallback for anything rendering
 * outside [AniSequelTheme].
 *
 * Named rather than inlined: the light set was previously spelled out twice -
 * once here as the CompositionLocal's default and once implicitly by
 * `else LocalStatusColors.current`, which read the local it was providing.
 * That second read happened to return the right thing and was pure accident.
 */
private val LightStatusColors = StatusColors(
    success = StatusSuccessLight,
    onSuccessContainer = StatusSuccessLight,
    successContainer = StatusSuccessContainerLight,
    warning = StatusWarningLight,
    onWarningContainer = StatusWarningLight,
    warningContainer = StatusWarningContainerLight,
    info = StatusInfoLight,
    onInfoContainer = StatusInfoLight,
    infoContainer = StatusInfoContainerLight,
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

object AniSequelTheme {
    val statusColors: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}

private val DarkStatusColors = StatusColors(
    success = StatusSuccessDark,
    onSuccessContainer = StatusSuccessDark,
    successContainer = StatusSuccessContainerDark,
    warning = StatusWarningDark,
    onWarningContainer = StatusWarningDark,
    warningContainer = StatusWarningContainerDark,
    info = StatusInfoDark,
    onInfoContainer = StatusInfoDark,
    infoContainer = StatusInfoContainerDark,
)

@Composable
fun AniSequelTheme(
    /**
     * Light, dark, or follow the device.
     *
     * Takes a [ThemeMode] rather than a `darkTheme: Boolean` because the
     * settings screen has to offer "follow system" as a third choice, and
     * folding that into a boolean would mean the theme re-deriving which
     * system value to use from a flag it was never given.
     */
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    /**
     * Material You - derive the palette from the user's wallpaper.
     *
     * Opt-in rather than automatic. The brand blue is the app's identity, and
     * a wallpaper-derived palette can leave the two status greens and the
     * coral unreadable against an arbitrary background. The user decides, and
     * the decision is remembered.
     */
    dynamicColor: Boolean = false,
    /**
     * The non-dynamic-color palette. Switches which static identity is
     * shown when `dynamicColor` is off. Defaults to the brand blue, named by
     * storage id for round-tripping through the preferences file.
     */
    palette: ThemePalette = ThemePalette.ANISDK,
    /** Which compiled motion scheme the app animates with. */
    motionStyle: com.anisequel.data.repository.MotionStyle = com.anisequel.data.repository.MotionStyle.DEFAULT,
    /**
     * Swap the dark theme surfaces to true black. Useful on OLED panels
     * where [Color.Black] draws zero light instead of a dark navy.
     */
    trueBlack: Boolean = false,
    /** Custom hex accent colour chosen by user. */
    customColorHex: String? = null,
    animeThemeActive: Boolean = false,
    animeThemeColorHex: String? = null,
    /**
     * The cover colour retention kept, used as the seed when no sheet is open.
     *
     * Retention used to stop at the accent: the sheet closed, the immersive layer
     * ended, and the background snapped back to the palette's neutral navy while
     * only the buttons kept the show's hue. That is a visible flick back to normal
     * on every exit, which reads as the colour being lost rather than kept. The
     * stored marker is what the retained tint is built from now, so a kept colour
     * keeps the *whole* scheme - surfaces included - until the reader picks a
     * palette, a hue or Reset appearance in Settings (each of which clears the
     * marker in `ThemePreferences`).
     *
     * Resolved by the caller rather than by a second flag here: only
     * `ThemePreferences` knows whether retention is on and whether the feature is
     * still enabled, and a theme that re-derived that would be a second answer to
     * a question the preferences already answer.
     */
    animeThemeRetainedColorHex: String? = null,
    /**
     * How much of the retained colour's tinted surfaces survive the sheet.
     *
     * 1 is the full scheme the sheet was drawn with; lower values ease the neutral
     * roles back toward the palette while leaving the accent on the cover. It is
     * ignored while a sheet is open, because a preview that does not match the
     * sheet is not a preview. See `RetainedTint` in the preferences for the three
     * offered values and why this is a setting rather than a constant.
     */
    animeTintStrength: Float = 1f,
    appBackgroundColor: String? = null,
    /**
     * The reader's reading scale, from Settings > Type & reading.
     *
     * Applied to the whole type scale in one place - see [anisequelTypography] -
     * because the alternative, a per-screen `fontSize`, is what this app already
     * paid for once: a dozen sizes that no setting could reach.
     */
    fontScale: Float = 1f,
    /** An imported font's file path, or null for the system face. */
    customFontPath: String? = null,
    /** Scroll speed for titles that outgrow their row, in dp per second. */
    titleScrollDpPerSecond: Float =
        com.anisequel.data.repository.DEFAULT_TITLE_SCROLL_DP_PER_SECOND,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // The live layer while a sheet is open, and the retained marker after it
    // closes. Both produce the tinted scheme; which one is present is the only
    // difference, and keeping them in one seed is what makes the exit stop
    // repainting every surface back to the brand.
    val animeSeedHex = animeThemeColorHex?.takeIf { animeThemeActive }
        ?: animeThemeRetainedColorHex
    val animeColor = androidx.compose.runtime.remember(animeSeedHex) {
        DynamicThemeBuilder.parseHexColor(animeSeedHex)
    }

    // The anime layer follows Light/Dark/System. It used to force dark mode,
    // which meant a reader on a light phone got a dark sheet the moment they
    // opened a title - and made the light anime scheme below unreachable. Both
    // schemes are generated now and the same mode switch that governs the rest of
    // the app selects between them.
    val darkTheme = effectiveDark

    val customColor = androidx.compose.runtime.remember(customColorHex) {
        DynamicThemeBuilder.parseHexColor(customColorHex)
    }

    val bgCustomColor = androidx.compose.runtime.remember(appBackgroundColor) {
        DynamicThemeBuilder.parseHexColor(appBackgroundColor)
    }

    // Keep the tokens the app uses directly in step with the M3 motion scheme.
    // They are the same property by different key: theme tokens animate M3
    // components, the ExpressiveMotion tokens animate everything we write, and
    // both have to agree on what happens after the curve (and on what that is).
    //
    // Written from a `SideEffect` because the tokens are snapshot state: this write
    // is what invalidates every composable that read one, which is what makes the
    // Motion style switch reach the app that is already on screen. A commit-phase
    // write rather than an assignment in the body, and not out of tidiness - a
    // composition that is discarded would leave the assignment behind, describing a
    // theme that never reached the screen.
    SideEffect {
        com.anisequel.ui.components.expressive.ExpressiveMotion.speed = motionStyle
    }

    // Dynamic colour used to be `dynamicColor && supportsDynamicColor`. The
    // second half was a runtime SDK_INT check against `S`, and the floor is now
    // 31, so it can only ever be true. Dropped rather than left in: a guard
    // that reads as load-bearing and cannot fail is worse than no guard,
    // because the next reader assumes there is a device class it excludes.
    //
    // The scheme builders used to run inline in the `when` below, so every
    // recomposition of the theme - the whole app - re-derived the transforms
    // even when nothing about the colour had changed. The heavy half now runs
    // once per colour, keyed on the parsed value.
    //
    // The anime layer generates both light and dark. While a sheet is open it uses
    // the full tint: the cover's hue reaches the neutral palettes, so the backdrop
    // behind a Chainsaw Man page is not the brand navy it was behind a Frieren one.
    //
    // A retained theme keeps that backdrop at the reader's chosen strength. It is
    // generated at that strength rather than generated fully tinted and blended
    // toward the palette afterwards: the strength moves the neutral palette's hue
    // and chroma *before* the tonal ladder samples it, so Subtle and Balanced keep
    // one coherent, contrast-checked surface ramp instead of an RGB average of two
    // ramps. The live sheet still uses the full tint, because it is the preview of
    // what retention will keep.
    val retainedOnly = animeThemeColorHex?.takeIf { animeThemeActive } == null &&
        animeThemeRetainedColorHex != null
    val animeSchemes = androidx.compose.runtime.remember(
        animeColor,
        retainedOnly,
        animeTintStrength
    ) {
        animeColor?.let {
            if (retainedOnly) {
                DynamicThemeBuilder.createRetainedSchemes(it, animeTintStrength)
            } else {
                DynamicThemeBuilder.createDynamicSchemes(it, tintSurfaces = true)
            }
        }
    }
    val customDynamicSchemes = androidx.compose.runtime.remember(customColor) {
        // A custom accent is a standing preference, not a title. Its surfaces
        // stay on the brand's neutral hue: picking a colour in Settings must not
        // repaint every card, which is what `appBackgroundColor` is for.
        customColor?.let { DynamicThemeBuilder.createDynamicSchemes(it) }
    }
    val context = LocalContext.current
    val wallpaperScheme = androidx.compose.runtime.remember(dynamicColor, darkTheme, context) {
        if (dynamicColor) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else null
    }

    val colorScheme = when {
        // Both asserts are gated by the same condition in the branch above, and
        // `remember` guarantees the same value a read would give - a kotlin
        // nullable here would only re-check what the branch already proved.
        animeColor != null -> {
            val schemes = requireNotNull(animeSchemes)
            if (darkTheme) schemes.second else schemes.first
        }
        dynamicColor -> requireNotNull(wallpaperScheme)
        customColor != null -> {
            val (lightDynamic, darkDynamic) = requireNotNull(customDynamicSchemes)
            if (darkTheme) darkDynamic else lightDynamic
        }
        darkTheme -> palette.dark
        else -> palette.light
    }.let { scheme ->
        val baseScheme = if (trueBlack && darkTheme) {
            scheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF070707),
                surfaceContainer = Color(0xFF101010),
                surfaceContainerHigh = Color(0xFF1A1A1A),
                surfaceContainerHighest = Color(0xFF262626),
                // The two roles the ramp is not complete without. Sheets, menus
                // and the tonal app-bar states read these directly, so leaving
                // them alone kept the show's hue in the bottom and top of a
                // ramp that was otherwise pure black - a coloured band at the
                // foot of an OLED screen, which is the one place true black is
                // supposed to be obvious.
                surfaceDim = Color.Black,
                surfaceBright = Color(0xFF2E2E2E)
            )
        } else {
            scheme
        }

        // The canvas colour applies in both modes now. It used to be gated on
        // `darkTheme` because every preset was a dark colour and applying one in
        // light mode left the light theme's near-black `onBackground` text on a
        // black canvas - so a picked background silently did nothing for anyone
        // on light mode or System-on-a-light-phone. `applyCanvasColour` fixes the
        // actual problem instead: it derives the on-roles and the surface ramp
        // from the picked colour, whatever mode it lands in.
        applyCanvasColour(baseScheme, bgCustomColor)
    }

    // Every role eases to its new value instead of cutting. This is read after
    // the true-black and custom-background overrides so those are animated too,
    // and it is the scheme the M3 components actually receive.
    val animatedColorScheme = rememberAnimatedColorScheme(colorScheme)

    // The reader's type, resolved once per change: the family from the imported
    // file (or the system face) and the whole scale at their reading size. Keyed
    // and remembered because this composable wraps the entire app and rebuilt
    // sixteen TextStyles on every animation frame of a scheme change otherwise.
    val customFamily = androidx.compose.runtime.remember(customFontPath) {
        customFontFamily(customFontPath)
    }
    val appTypography = androidx.compose.runtime.remember(fontScale, customFamily) {
        anisequelTypography(fontScale, customFamily ?: FontFamily.Default)
    }

    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors
    ) {
        // MaterialExpressiveTheme rather than MaterialTheme: the difference that
        // matters is the motion scheme it installs, which is what every
        // expressive component in the app animates against. Colour, typography
        // and shapes are passed explicitly so this stays the app's identity.
        ExpressiveThemeHost(
            colorScheme = animatedColorScheme,
            motionStyle = motionStyle,
            typography = appTypography,
            content = {
                // The title-scroll preference, handed down once. `animates` is false
                // under INSTANT: a reader who asked for no motion did not ask for
                // "except the text", and a marquee is the one loop that runs while
                // they are reading.
                CompositionLocalProvider(
                    com.anisequel.ui.components.LocalTitleMotion provides
                        com.anisequel.ui.components.TitleMotion(
                            velocity = titleScrollDpPerSecond
                                .coerceIn(
                                    com.anisequel.data.repository.MIN_TITLE_SCROLL_DP_PER_SECOND,
                                    com.anisequel.data.repository.MAX_TITLE_SCROLL_DP_PER_SECOND
                                )
                                .dp,
                            animates = motionStyle !=
                                com.anisequel.data.repository.MotionStyle.INSTANT
                        )
                ) {
                    content()
                }
            },
        )
    }
}

/**
 * Implementing `MotionScheme` is the documented route
 * (m3.material.io/styles/motion) for making every built-in M3 component
 * switch to a caller-supplied curve. This one is a no-op curve: every
 * animation it hands out is `snap()`, so Material components resolve the
 * transition instantly. It has the same cardinality as the standard scheme,
 * just with the duration set to zero.
 *
 * The app's own springs and fades do not go through MaterialExpressiveTheme;
 * they read [com.anisequel.ui.components.expressive.ExpressiveMotion.speed],
 * which the same preference drives.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private object InstantMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveThemeHost(
    colorScheme: androidx.compose.material3.ColorScheme,
    motionStyle: com.anisequel.data.repository.MotionStyle,
    typography: androidx.compose.material3.Typography,
    content: @Composable () -> Unit
) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = when (motionStyle) {
            com.anisequel.data.repository.MotionStyle.INSTANT -> InstantMotionScheme
            com.anisequel.data.repository.MotionStyle.SMOOTH -> MotionScheme.standard()
            com.anisequel.data.repository.MotionStyle.SNAPPY -> MotionScheme.standard()
            com.anisequel.data.repository.MotionStyle.CHILL -> MotionScheme.expressive()
            com.anisequel.data.repository.MotionStyle.FLOATY -> MotionScheme.expressive()
        },
        shapes = ExpressiveShapes,
        typography = typography,
        content = content
    )
}

/*
 * The old `MyApplicationTheme(darkTheme: Boolean, ...)` alias is gone rather
 * than adapted. Nothing in the app called it - `MainActivity` has used
 * `AniSequelTheme` since the theme was introduced - and adapting it would have
 * meant keeping a second public entry point whose `darkTheme: Boolean` cannot
 * express "follow the system", which is now the default. A compatibility alias
 * that silently cannot represent the default is a trap for the next person who
 * reaches for it.
 */
