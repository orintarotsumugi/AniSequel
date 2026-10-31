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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.ThemeMode

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
 * A retained scheme with its neutral roles eased back toward [base].
 *
 * [strength] 1 returns [retained] untouched. At lower values the *surfaces* move
 * toward the base scheme while every accent, container and `on` role stays the
 * cover's - which is the difference between "this show tints my app" and "this
 * show's colour is in my app but my background is still mine". The blend is a
 * plain per-channel lerp, which is enough because both schemes are generated for
 * the same dark/light mode and their brightness is already in the same band.
 *
 * The `on` roles are deliberately not blended with the surfaces: they were chosen
 * against the *retained* surfaces by the tonal ladder, and mixing them toward a
 * second set of `on` colours could only lower contrast.
 */
private fun blendRetainedTint(
    retained: ColorScheme,
    base: ColorScheme,
    strength: Float
): ColorScheme {
    val fraction = strength.coerceIn(0f, 1f)
    fun blend(from: Color, to: Color): Color = lerp(from, to, fraction)
    return retained.copy(
        background = blend(base.background, retained.background),
        surface = blend(base.surface, retained.surface),
        surfaceVariant = blend(base.surfaceVariant, retained.surfaceVariant),
        surfaceTint = retained.surfaceTint,
        surfaceBright = blend(base.surfaceBright, retained.surfaceBright),
        surfaceDim = blend(base.surfaceDim, retained.surfaceDim),
        surfaceContainer = blend(base.surfaceContainer, retained.surfaceContainer),
        surfaceContainerHigh = blend(base.surfaceContainerHigh, retained.surfaceContainerHigh),
        surfaceContainerHighest = blend(
            base.surfaceContainerHighest,
            retained.surfaceContainerHighest
        ),
        surfaceContainerLow = blend(base.surfaceContainerLow, retained.surfaceContainerLow),
        surfaceContainerLowest = blend(
            base.surfaceContainerLowest,
            retained.surfaceContainerLowest
        ),
        outline = blend(base.outline, retained.outline),
        outlineVariant = blend(base.outlineVariant, retained.outlineVariant)
    )
}

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
 * The ramp is built by blending toward the contrast colour (lifts) or toward
 * the further-out extreme (drops), so elevation steps stay visible on any
 * canvas and text keeps the contrast the blend was chosen for.
 */
internal fun applyCanvasColour(base: ColorScheme, canvas: Color?): ColorScheme {
    if (canvas == null) return base

    // The 0.18 crossover is where pure white and pure black text have equal
    // contrast on the canvas, and it is chosen so neither candidate dips below
    // WCAG AA anywhere: a mid-tone canvas would otherwise read 4.1:1 with a
    // fixed light/dark boolean at 0.5 and Material's toned text colours.
    val canvasIsDark = canvas.luminance() < 0.18f
    val onCanvas = if (canvasIsDark) Color.White else Color.Black
    val drop = if (canvasIsDark) Color.Black else Color.White

    fun mix(from: Color, to: Color, fraction: Float): Color {
        fun leg(a: Float, b: Float) = a + (b - a) * fraction
        return Color(
            red = leg(from.red, to.red),
            green = leg(from.green, to.green),
            blue = leg(from.blue, to.blue),
            alpha = leg(from.alpha, to.alpha)
        )
    }
    fun lift(fraction: Float) = mix(canvas, onCanvas, fraction)

    return base.copy(
        background = canvas,
        onBackground = onCanvas,
        surface = canvas,
        onSurface = onCanvas,
        surfaceContainerLowest = mix(canvas, drop, 0.20f),
        surfaceContainerLow = lift(0.05f),
        surfaceContainer = lift(0.10f),
        surfaceContainerHigh = lift(0.16f),
        surfaceContainerHighest = lift(0.22f),
        surfaceDim = mix(canvas, drop, 0.10f),
        surfaceBright = lift(0.12f),
        surfaceVariant = lift(0.10f),
        onSurfaceVariant = mix(onCanvas, canvas, 0.35f)
    )
}

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
    // The anime layer generates both light and dark. `tintSurfaces = true`
    // because it is the immersive half: a sheet is open for one show, so the
    // cover's hue reaches the neutral palettes and the backdrop behind a Chainsaw
    // Man page is not the brand navy it was behind a Frieren one.
    //
    // What *outlives* the sheet does not come through here. Retention writes the
    // cover colour as the custom accent below, which is the same colour on the
    // app's usual surfaces - the distinction `DynamicThemeBuilder` documents, and
    // the one this file used to get wrong by keeping the tinted layer switched on
    // after the reader had closed the title.
    val animeSchemes = androidx.compose.runtime.remember(animeColor) {
        animeColor?.let { DynamicThemeBuilder.createDynamicSchemes(it, tintSurfaces = true) }
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

    // The neutral scheme the retained tint eases back toward. It is the custom
    // accent's own surfaces (which are brand-neutral by construction) when the
    // retained colour wrote that accent, and the chosen palette otherwise. The
    // accent roles are never blended: "Subtle" is about how far the background
    // moves, not about desaturating the show's colour.
    val neutralBaseScheme = when {
        customColor != null -> {
            val (lightDynamic, darkDynamic) = requireNotNull(customDynamicSchemes)
            if (darkTheme) darkDynamic else lightDynamic
        }
        darkTheme -> palette.dark
        else -> palette.light
    }
    // True only when the scheme is built from the stored marker rather than the
    // live layer. While a sheet is open the immersive scheme is the whole point.
    val retainedOnly = animeThemeColorHex?.takeIf { animeThemeActive } == null &&
        animeThemeRetainedColorHex != null

    val colorScheme = when {
        // Both asserts are gated by the same condition in the branch above, and
        // `remember` guarantees the same value a read would give - a kotlin
        // nullable here would only re-check what the branch already proved.
        animeColor != null -> {
            val schemes = requireNotNull(animeSchemes)
            val selected = if (darkTheme) schemes.second else schemes.first
            if (retainedOnly && animeTintStrength < 1f) {
                blendRetainedTint(selected, neutralBaseScheme, animeTintStrength)
            } else {
                selected
            }
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

/**
 * The reader's imported font, or null when there is none or it can no longer load.
 *
 * The file lives in the app's own storage - `CustomFontStore` copies it there on
 * import - so this is an existence check and a lazy `Font` wrapper. A file that
 * disappeared (cleared data, a failed restore) falls back to the system face rather
 * than failing the theme: a font is a preference, not a dependency. The import path
 * validates the file with a real `Typeface` before it is recorded, which is where
 * the corrupt-file case is actually caught.
 */
internal fun customFontFamily(path: String?): FontFamily? {
    if (path.isNullOrBlank()) return null
    val file = java.io.File(path)
    if (!file.isFile) return null
    return runCatching { FontFamily(Font(file)) }.getOrNull()
}
