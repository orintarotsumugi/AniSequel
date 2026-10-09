package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
     */
    fun createDynamicSchemes(seed: Color): Pair<ColorScheme, ColorScheme> {
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

        val lightScheme = lightColorScheme(
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
            background = LightBgMain,
            onBackground = LightTextPrimary,
            surface = LightSurface,
            onSurface = LightTextPrimary,
            surfaceContainerLow = LightSurfaceLow,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceContainerHigh,
            surfaceContainerHighest = LightSurfaceContainerHighest
        )

        val darkScheme = darkColorScheme(
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
            background = DarkBgMain,
            onBackground = DarkTextPrimary,
            surface = DarkSurface,
            onSurface = DarkTextPrimary,
            surfaceContainerLow = DarkSurfaceLow,
            surfaceContainer = DarkSurfaceContainer,
            surfaceContainerHigh = DarkSurfaceContainerHigh,
            surfaceContainerHighest = DarkSurfaceContainerHighest,
            outline = DarkBorder,
            outlineVariant = Color(0xFF24333F)
        )

        return Pair(lightScheme, darkScheme)
    }
}
