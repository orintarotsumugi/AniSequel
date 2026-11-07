package com.anisequel

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.anisequel.domain.color.Hct
import com.anisequel.ui.theme.applyCanvasColour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Settings "canvas colour" is the control that is *supposed* to repaint the
 * whole app, and for the reporting user it did nothing.
 *
 * ## What was broken
 *
 * `AniSequelTheme` gated the override on `darkTheme`, so a reader on light mode
 * (or System on a light phone) picked a background, the chip lit as if selected,
 * the value round-tripped through DataStore correctly - and was silently
 * discarded at the last step. The gate existed because the override reused the
 * scheme's `on` roles: a dark canvas in light mode meant the light theme's
 * near-black text on a black canvas. That was the real bug, and the fix is to
 * derive the text and the whole surface ramp from the picked colour instead of
 * dropping the feature for one mode.
 *
 * ## Why the ramp too, not just `background`
 *
 * Overriding only `background`/`surface` left `surfaceContainer*` on the brand
 * ramp - cards, sheets and app bars kept the old colour, which is the other half
 * of "changing the full background colour doesn't work".
 */
@RunWith(RobolectricTestRunner::class)
class CanvasColourTest {

    private val light = lightColorScheme()
    private val dark = darkColorScheme()

    /** The Settings presets plus tones no preset reaches but a picker can. */
    private fun angularDistance(a: Double, b: Double): Double {
        val raw = kotlin.math.abs(a - b) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }

    private val canvases = listOf(
        "pure black" to Color(0xFF000000),
        "midnight navy" to Color(0xFF080C14),
        "deep charcoal" to Color(0xFF121212),
        "slate navy" to Color(0xFF101820),
        "mist white" to Color(0xFFF2F0F7),
        "warm paper" to Color(0xFFF5EFE3),
        "a mid red" to Color(0xFF8C1D18),
        "the nastiest mid tone" to Color(0xFF7C7C7C)
    )

    @Test
    fun `no canvas means no change`() {
        assertSame(dark, applyCanvasColour(dark, null))
    }

    /**
     * The reported bug, pinned in the mode that dropped the colour.
     *
     * Light mode is where the old `&& darkTheme` guard threw the choice away;
     * asserting dark only would have passed on the broken build.
     */
    @Test
    fun `the canvas paints both schemes`() {
        canvases.forEach { (name, canvas) ->
            listOf(
                "light" to light,
                "dark" to dark
            ).forEach { (mode, base) ->
                val painted = applyCanvasColour(base, canvas)
                assertEquals("$name on $mode: the canvas never reached background", canvas, painted.background)
                assertEquals("$name on $mode: surface is the canvas too", canvas, painted.surface)
            }
        }
    }

    /**
     * "Full background colour" means the cards move with it.
     *
     * The containers must leave the base ramp (otherwise the change reads as
     * half-applied) but must not collapse onto the canvas either, or elevation
     * stops being visible.
     */
    @Test
    fun `the whole surface ramp is re-derived from the canvas`() {
        val (_, canvas) = canvases.first()
        val painted = applyCanvasColour(dark, canvas)

        val roles = listOf(
            painted.surfaceContainerLowest,
            painted.surfaceContainerLow,
            painted.surfaceContainer,
            painted.surfaceContainerHigh,
            painted.surfaceContainerHighest
        )

        assertTrue(
            "the container ramp has to sit on the canvas, not the brand navy: $roles",
            roles.all { it != dark.surfaceContainer }
        )
        assertEquals(
            "and each step has to be its own tone, or cards lose their elevation cue",
            roles.size,
            roles.distinct().size
        )
        assertNotEquals(painted.surfaceContainer, painted.surfaceContainerHighest)
    }

    /**
     * Whatever canvas is picked, its text stays readable.
     *
     * This is the property the dark-mode gate was a stand-in for. The gate
     * "solved" it by not painting light mode at all; the derived `on` roles have
     * to actually hold the contrast instead.
     */
    @Test
    fun `text on the canvas stays above AA`() {
        canvases.forEach { (name, canvas) ->
            val painted = applyCanvasColour(dark, canvas)

            listOf(
                "onBackground" to painted.onBackground,
                "onSurface" to painted.onSurface
            ).forEach { (role, color) ->
                val contrast = ColorUtils.calculateContrast(color.toArgb(), canvas.toArgb())
                assertTrue(
                    "$name: $role reads at $contrast:1 against the canvas, below 4.5",
                    contrast >= 4.5
                )
            }

            val secondary = ColorUtils.calculateContrast(
                painted.onSurfaceVariant.toArgb(),
                canvas.toArgb()
            )
            assertTrue(
                "$name: onSurfaceVariant is secondary body text, $secondary:1 is not enough",
                secondary >= 3.0
            )
        }
    }

    /**
     * The ramp is the canvas's colour, not a grey wash toward white or black.
     *
     * RGB interpolation was the older implementation: it could keep a red canvas
     * red while desaturating a cyan one unevenly, and its borders stayed on the
     * brand's navy. The ramp is sampled in HCT now, and the borders move with it.
     */
    @Test
    fun `the canvas ramp keeps its hue and re-derives the borders`() {
        val canvas = Color(0xFF8C1D18)
        val painted = applyCanvasColour(dark, canvas)
        val canvasHue = Hct.fromInt(canvas.toArgb()).hue
        val containerHue = Hct.fromInt(painted.surfaceContainer.toArgb()).hue

        assertTrue(
            "the cards sit ${angularDistance(canvasHue, containerHue)} degrees from " +
                "the picked canvas, so the ramp is not following it",
            angularDistance(canvasHue, containerHue) < 25.0
        )
        assertNotEquals(
            "a border from the brand navy is invisible on a red canvas",
            dark.outline,
            painted.outline
        )
        assertNotEquals(
            "and the subtle border has to move too",
            dark.outlineVariant,
            painted.outlineVariant
        )
    }

    /**
     * The non-surface roles survive.
     *
     * The canvas is a neutrals-only repaint; a picked background must not move
     * the accent, error or the containers buttons sit on.
     */
    @Test
    fun `the canvas leaves the accent and status roles alone`() {
        canvases.forEach { (name, canvas) ->
            val painted: ColorScheme = applyCanvasColour(dark, canvas)
            assertEquals("$name moved primary", dark.primary, painted.primary)
            assertEquals("$name moved error", dark.error, painted.error)
            assertEquals("$name moved primaryContainer", dark.primaryContainer, painted.primaryContainer)
        }
    }
}
