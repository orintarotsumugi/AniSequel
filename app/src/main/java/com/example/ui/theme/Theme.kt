package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.repository.ThemeMode

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
    surfaceTint = AniBlue,
    inverseSurface = DarkSurfaceContainer,
    inverseOnSurface = DarkTextPrimary,
    outline = LightBorder,
    outlineVariant = Color(0xFFD8E1EC),
    scrim = Color(0xFF000000),
)

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

/**
 * Whether this device can theme itself from the user's wallpaper.
 *
 * Material You arrived in Android 12. Exposed as a function of the platform
 * rather than as a stored preference so the Settings switch can disable itself
 * on an older device instead of storing a choice that can never take effect.
 */
val supportsDynamicColor: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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
    motionStyle: com.example.data.repository.MotionStyle = com.example.data.repository.MotionStyle.DEFAULT,
    /**
     * Swap the dark theme surfaces to true black. Useful on OLED panels
     * where [Color.Black] draws zero light instead of a dark navy.
     */
    trueBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && supportsDynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> palette.dark
        else -> palette.light
    }.let { scheme ->
        if (trueBlack && darkTheme) {
            scheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF070707),
                surfaceContainer = Color(0xFF101010),
                surfaceContainerHigh = Color(0xFF1A1A1A),
                surfaceContainerHighest = Color(0xFF262626)
            )
        } else {
            scheme
        }
    }

    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors
    ) {
        // MaterialExpressiveTheme rather than MaterialTheme: the difference that
        // matters is the motion scheme it installs, which is what every
        // expressive component in the app animates against. Colour, typography
        // and shapes are passed explicitly so this stays the app's identity.
        ExpressiveThemeHost(
            colorScheme = colorScheme,
            motionStyle = motionStyle,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveThemeHost(
    colorScheme: androidx.compose.material3.ColorScheme,
    motionStyle: com.example.data.repository.MotionStyle,
    content: @Composable () -> Unit
) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = when (motionStyle) {
            com.example.data.repository.MotionStyle.EXPRESSIVE -> MotionScheme.expressive()
            com.example.data.repository.MotionStyle.STANDARD -> MotionScheme.standard()
        },
        shapes = ExpressiveShapes,
        typography = AniSequelTypography,
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
