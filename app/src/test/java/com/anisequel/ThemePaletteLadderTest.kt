package com.anisequel

import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.anisequel.ui.theme.AniBlue
import com.anisequel.ui.theme.AniBlueLight
import com.anisequel.ui.theme.DarkColorScheme
import com.anisequel.ui.theme.LightColorScheme
import com.anisequel.ui.theme.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The side of the palette file that is easy to leave half-finished.
 *
 * The four hand-authored palettes spell out their accents and a few surfaces,
 * then lean on `darkColorScheme` for the rest. Those defaults are Material's
 * purple-grey baseline, not "unset", so a missing role is a visible purple cast
 * under a pink, green, blue or amber accent. `withBrandDarkNeutralLadder` fills
 * the neutral roles once; this pins that every static palette actually gets it.
 */
@RunWith(RobolectricTestRunner::class)
class ThemePaletteLadderTest {

    private val handAuthored = listOf(
        ThemePalette.SAKURA,
        ThemePalette.MATCHA,
        ThemePalette.JUBITER,
        ThemePalette.AMBER
    )

    @Test
    fun `every hand-authored dark palette completes the neutral ladder`() {
        handAuthored.forEach { palette ->
            val dark = palette.dark
            assertEquals(
                "${palette.id} surfaceVariant",
                DarkColorScheme.surfaceVariant,
                dark.surfaceVariant
            )
            assertEquals(
                "${palette.id} onSurfaceVariant",
                DarkColorScheme.onSurfaceVariant,
                dark.onSurfaceVariant
            )
            assertEquals(
                "${palette.id} surfaceDim",
                DarkColorScheme.surfaceDim,
                dark.surfaceDim
            )
            assertEquals(
                "${palette.id} surfaceBright",
                DarkColorScheme.surfaceBright,
                dark.surfaceBright
            )
            assertEquals(
                "${palette.id} surfaceContainerLowest",
                DarkColorScheme.surfaceContainerLowest,
                dark.surfaceContainerLowest
            )
            assertEquals(
                "${palette.id} inverseSurface",
                DarkColorScheme.inverseSurface,
                dark.inverseSurface
            )
            assertEquals(
                "${palette.id} inverseOnSurface",
                DarkColorScheme.inverseOnSurface,
                dark.inverseOnSurface
            )
        }
    }

    @Test
    fun `the brand blue holds AA on the surfaces it is drawn on`() {
        assertTrue(
            "the light primary must carry white text",
            contrast(AniBlue.toArgb(), LightColorScheme.onPrimary.toArgb()) >= 4.5
        )
        assertTrue(
            "the light primary must also be readable on the light surface",
            contrast(AniBlue.toArgb(), LightColorScheme.surface.toArgb()) >= 4.5
        )
        assertTrue(
            "the dark primary must carry onPrimary",
            contrast(AniBlueLight.toArgb(), DarkColorScheme.onPrimary.toArgb()) >= 4.5
        )
    }

    private fun contrast(foreground: Int, background: Int): Double =
        ColorUtils.calculateContrast(foreground, background)
}
