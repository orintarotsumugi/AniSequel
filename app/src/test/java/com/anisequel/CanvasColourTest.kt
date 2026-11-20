package com.anisequel

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.anisequel.domain.color.Hct
import com.anisequel.ui.theme.applyCanvasColour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picked canvas has to be a whole scheme, not a background painted under
 * the old one. The roles that sit on top of it must keep their contrast on the
 * surface they are actually drawn over, and the elevation tint has to belong
 * to the canvas rather than to the accent that was active before it.
 */
class CanvasColourTest {

    private val canvases = listOf(
        Color(0xFF123024),
        Color(0xFF2B1C3D),
        Color(0xFFE9E2D5),
        Color(0xFF0A0F1E)
    )

    @Test
    fun `onSurfaceVariant keeps AA against surfaceVariant`() {
        canvases.forEach { canvas ->
            listOf(lightColorScheme(), darkColorScheme()).forEach { base ->
                val scheme = applyCanvasColour(base, canvas)
                val contrast = contrast(scheme.onSurfaceVariant, scheme.surfaceVariant)
                assertTrue(
                    "canvas ${hex(canvas)}: onSurfaceVariant only reached $contrast:1 " +
                        "against surfaceVariant, the surface it is printed on",
                    contrast >= 4.5f
                )
            }
        }
    }

    @Test
    fun `outline stays visible on the elevated surfaces`() {
        canvases.forEach { canvas ->
            listOf(lightColorScheme(), darkColorScheme()).forEach { base ->
                val scheme = applyCanvasColour(base, canvas)
                val contrast = contrast(scheme.outline, scheme.surfaceContainer)
                assertTrue(
                    "canvas ${hex(canvas)}: outline only reached $contrast:1 " +
                        "against the card surface",
                    contrast >= 3.0f
                )
            }
        }
    }

    @Test
    fun `the elevation tint follows the picked canvas`() {
        canvases.forEach { canvas ->
            val scheme = applyCanvasColour(lightColorScheme(), canvas)
            val canvasHue = Hct.fromInt(canvas.toArgb()).hue
            val tintHue = Hct.fromInt(scheme.surfaceTint.toArgb()).hue
            assertTrue(
                "surfaceTint hue $tintHue does not belong to canvas hue $canvasHue",
                angularDistance(canvasHue, tintHue) < 12.0
            )
        }
    }

    @Test
    fun `a null canvas leaves the scheme untouched`() {
        val base = lightColorScheme()
        assertEquals(base, applyCanvasColour(base, null))
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }

    private fun hex(color: Color) = String.format(java.util.Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)

    private fun angularDistance(a: Double, b: Double): Double {
        val raw = kotlin.math.abs(a - b) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }
}
