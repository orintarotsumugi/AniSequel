package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * An accent family the user can pick instead of the brand blue.
 *
 * One line of the theme editor rather than a free colour wheel: every
 * palette below is a matched pair of light/dark role triples, so a
 * non-designer can never land on "primary that cannot hold text". The
 * dynamic-colour toggle still wins when it is on; this list is the brand path.
 */
data class ThemePalette(
    val id: String,
    val displayName: String,
    val light: ColorScheme,
    val dark: ColorScheme
) {
    /**
     * Foreground used on the palette's primary chip in the picker. Matches
     * whether a scheme here is its "dark" variant or not.
     */
    val preview: ThemePalettePreview
        get() = ThemePalettePreview(
            primary = dark.primary,
            primaryContainer = dark.primaryContainer,
            secondary = dark.secondary
        )

    companion object {
        val ANISDK: ThemePalette = ThemePalette(
            id = "anisequel",
            displayName = "AniList",
            light = LightColorScheme,
            dark = DarkColorScheme
        )

        val SAKURA: ThemePalette = ThemePalette(
            id = "sakura",
            displayName = "Sakura",
            light = lightColorScheme(
                primary = Color(0xFFC2185B),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFD9E2),
                onPrimaryContainer = Color(0xFF3E001D),
                inversePrimary = Color(0xFFFFB3C9),
                secondary = Color(0xFF8E3A75),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFFCEF0),
                onSecondaryContainer = Color(0xFF370033),
                tertiary = Color(0xFFC0605B),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFFFDAD8),
                onTertiaryContainer = Color(0xFF410003),
                surfaceTint = Color(0xFFC2185B)
            ),
            dark = darkColorScheme(
                primary = Color(0xFFFFB3C9),
                onPrimary = Color(0xFF5E122C),
                primaryContainer = Color(0xFF901C47),
                onPrimaryContainer = Color(0xFFFFD9E2),
                inversePrimary = Color(0xFFC2185B),
                secondary = Color(0xFFE8B2DA),
                onSecondary = Color(0xFF53194B),
                secondaryContainer = Color(0xFF72305F),
                onSecondaryContainer = Color(0xFFFFCEF0),
                tertiary = Color(0xFFFFADA8),
                onTertiary = Color(0xFF5E1918),
                tertiaryContainer = Color(0xFF7F2F2B),
                onTertiaryContainer = Color(0xFFFFDAD8),
                background = DarkBgMain,
                onBackground = DarkTextPrimary,
                surface = DarkSurface,
                onSurface = DarkTextPrimary,
                surfaceContainerLow = DarkSurfaceLow,
                surfaceContainer = DarkSurfaceContainer,
                surfaceContainerHigh = DarkSurfaceContainerHigh,
                surfaceContainerHighest = DarkSurfaceContainerHighest,
                surfaceTint = Color(0xFFFFB3C9),
                outline = DarkBorder,
                outlineVariant = Color(0xFF24333F),
                scrim = Color(0xFF000000)
            )
        )

        val MATCHA: ThemePalette = ThemePalette(
            id = "matcha",
            displayName = "Matcha",
            light = lightColorScheme(
                primary = Color(0xFF2E7D32),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFC8E6C9),
                onPrimaryContainer = Color(0xFF00220A),
                inversePrimary = Color(0xFFA5D6A7),
                secondary = Color(0xFF5D7A4E),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFD2ECC2),
                onSecondaryContainer = Color(0xFF13250B),
                tertiary = Color(0xFFB75E00),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFFFDBC4),
                onTertiaryContainer = Color(0xFF341200),
                surfaceTint = Color(0xFF2E7D32)
            ),
            dark = darkColorScheme(
                primary = Color(0xFFA5D6A7),
                onPrimary = Color(0xFF0C3D17),
                primaryContainer = Color(0xFF145B26),
                onPrimaryContainer = Color(0xFFC8E6C9),
                inversePrimary = Color(0xFF2E7D32),
                secondary = Color(0xFFB6CC9C),
                onSecondary = Color(0xFF26361A),
                secondaryContainer = Color(0xFF3D4F34),
                onSecondaryContainer = Color(0xFFD2ECC2),
                tertiary = Color(0xFFFFB377),
                onTertiary = Color(0xFF5A2900),
                tertiaryContainer = Color(0xFF7B3F00),
                onTertiaryContainer = Color(0xFFFFDBC4),
                background = DarkBgMain,
                onBackground = DarkTextPrimary,
                surface = DarkSurface,
                onSurface = DarkTextPrimary,
                surfaceContainerLow = DarkSurfaceLow,
                surfaceContainer = DarkSurfaceContainer,
                surfaceContainerHigh = DarkSurfaceContainerHigh,
                surfaceContainerHighest = DarkSurfaceContainerHighest,
                surfaceTint = Color(0xFFA5D6A7),
                outline = DarkBorder,
                outlineVariant = Color(0xFF24333F),
                scrim = Color(0xFF000000)
            )
        )

        val JUBITER: ThemePalette = ThemePalette(
            id = "jupiter",
            displayName = "Jupiter",
            light = lightColorScheme(
                primary = Color(0xFF3948AB),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFC5CBFF),
                onPrimaryContainer = Color(0xFF001159),
                inversePrimary = Color(0xFFB9C3FF),
                secondary = Color(0xFF5E5E81),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE1E0FF),
                onSecondaryContainer = Color(0xFF161733),
                tertiary = Color(0xFF8B4B8C),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFFFD7F5),
                onTertiaryContainer = Color(0xFF39003F),
                surfaceTint = Color(0xFF3948AB)
            ),
            dark = darkColorScheme(
                primary = Color(0xFFB9C3FF),
                onPrimary = Color(0xFF0A1936),
                primaryContainer = Color(0xFF263570),
                onPrimaryContainer = Color(0xFFC5CBFF),
                inversePrimary = Color(0xFF3948AB),
                secondary = Color(0xFFC5C4EA),
                onSecondary = Color(0xFF2A2B4C),
                secondaryContainer = Color(0xFF424363),
                onSecondaryContainer = Color(0xFFE1E0FF),
                tertiary = Color(0xFFF3B3F5),
                onTertiary = Color(0xFF4A0C4D),
                tertiaryContainer = Color(0xFF6D276F),
                onTertiaryContainer = Color(0xFFFFD7F5),
                background = DarkBgMain,
                onBackground = DarkTextPrimary,
                surface = DarkSurface,
                onSurface = DarkTextPrimary,
                surfaceContainerLow = DarkSurfaceLow,
                surfaceContainer = DarkSurfaceContainer,
                surfaceContainerHigh = DarkSurfaceContainerHigh,
                surfaceContainerHighest = DarkSurfaceContainerHighest,
                surfaceTint = Color(0xFFB9C3FF),
                outline = DarkBorder,
                outlineVariant = Color(0xFF24333F),
                scrim = Color(0xFF000000)
            )
        )

        val AMBER: ThemePalette = ThemePalette(
            id = "amber",
            displayName = "Sunset",
            light = lightColorScheme(
                primary = Color(0xFFB45309),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFDDB0),
                onPrimaryContainer = Color(0xFF321801),
                inversePrimary = Color(0xFFFFBA79),
                secondary = Color(0xFF7B5900),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFFE194),
                onSecondaryContainer = Color(0xFF231A00),
                tertiary = Color(0xFF9A452F),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFFFDAD1),
                onTertiaryContainer = Color(0xFF3E0500),
                surfaceTint = Color(0xFFB45309)
            ),
            dark = darkColorScheme(
                primary = Color(0xFFFFBA79),
                onPrimary = Color(0xFF4A2800),
                primaryContainer = Color(0xFF6F3A00),
                onPrimaryContainer = Color(0xFFFFDDB0),
                inversePrimary = Color(0xFFB45309),
                secondary = Color(0xFFE5C15F),
                onSecondary = Color(0xFF3D2E00),
                secondaryContainer = Color(0xFF574500),
                onSecondaryContainer = Color(0xFFFFE194),
                tertiary = Color(0xFFFFB5A3),
                onTertiary = Color(0xFF591E0D),
                tertiaryContainer = Color(0xFF7F3120),
                onTertiaryContainer = Color(0xFFFFDAD1),
                background = DarkBgMain,
                onBackground = DarkTextPrimary,
                surface = DarkSurface,
                onSurface = DarkTextPrimary,
                surfaceContainerLow = DarkSurfaceLow,
                surfaceContainer = DarkSurfaceContainer,
                surfaceContainerHigh = DarkSurfaceContainerHigh,
                surfaceContainerHighest = DarkSurfaceContainerHighest,
                surfaceTint = Color(0xFFFFBA79),
                outline = DarkBorder,
                outlineVariant = Color(0xFF24333F),
                scrim = Color(0xFF000000)
            )
        )

        val entries: List<ThemePalette> = listOf(ANISDK, SAKURA, MATCHA, JUBITER, AMBER)

        fun fromStorage(id: String?): ThemePalette =
            entries.firstOrNull { it.id == id } ?: ANISDK
    }
}

/**
 * A tiny preview dot trio used by the palette picker.
 */
data class ThemePalettePreview(
    val primary: Color,
    val primaryContainer: Color,
    val secondary: Color
)
