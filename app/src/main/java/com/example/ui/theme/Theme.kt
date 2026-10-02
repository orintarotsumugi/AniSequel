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

private val DarkColorScheme = darkColorScheme(
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

private val LightColorScheme = lightColorScheme(
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

private val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(
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
}

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You stays opt-in: the app's blue is its identity, and letting a
    // wallpaper repaint every status chip made the two greens and the coral
    // unreadable on some OEM palettes.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LocalStatusColors.current
    ) {
        // MaterialExpressiveTheme rather than MaterialTheme: the difference that
        // matters is the motion scheme it installs, which is what every
        // expressive component in the app animates against. Colour, typography
        // and shapes are passed explicitly so this stays the app's identity.
        ExpressiveThemeHost(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveThemeHost(
    colorScheme: androidx.compose.material3.ColorScheme,
    content: @Composable () -> Unit
) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = AniSequelTypography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = AniSequelTheme(darkTheme, dynamicColor, content)