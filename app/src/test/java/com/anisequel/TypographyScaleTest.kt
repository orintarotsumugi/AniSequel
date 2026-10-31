package com.anisequel

import androidx.compose.ui.text.font.FontFamily
import com.anisequel.data.repository.MAX_FONT_SCALE
import com.anisequel.data.repository.MIN_FONT_SCALE
import com.anisequel.ui.theme.anisequelTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reading scale Settings > Type & reading writes.
 *
 * Two properties matter and neither is visible in a screenshot: every style moves
 * together (a bigger title over unchanged body text is half a setting), and the
 * slider's range is the range the theme honours - a value smuggled in from an old
 * build or a bad write must clamp, not scale the app into unreadability.
 */
class TypographyScaleTest {

    @Test
    fun `the whole scale grows together`() {
        val base = anisequelTypography(1f)
        val larger = anisequelTypography(1.2f)

        assertTrue(
            "body text must grow with the scale",
            larger.bodyLarge.fontSize.value > base.bodyLarge.fontSize.value
        )
        assertTrue(
            "titles must grow with the scale",
            larger.titleLarge.fontSize.value > base.titleLarge.fontSize.value
        )
        assertTrue(
            "labels must grow with the scale",
            larger.labelSmall.fontSize.value > base.labelSmall.fontSize.value
        )
    }

    @Test
    fun `the scale clamps to the range the slider offers`() {
        val clampedHigh = anisequelTypography(9f).bodyLarge.fontSize.value
        val atMax = anisequelTypography(MAX_FONT_SCALE).bodyLarge.fontSize.value
        val clampedLow = anisequelTypography(0.1f).bodyLarge.fontSize.value
        val atMin = anisequelTypography(MIN_FONT_SCALE).bodyLarge.fontSize.value

        assertEquals(atMax.toDouble(), clampedHigh.toDouble(), 0.001)
        assertEquals(atMin.toDouble(), clampedLow.toDouble(), 0.001)
    }

    @Test
    fun `an imported family reaches every style`() {
        val imported = anisequelTypography(1f, FontFamily.Serif)

        assertEquals(FontFamily.Serif, imported.displayLarge.fontFamily)
        assertEquals(FontFamily.Serif, imported.bodyMedium.fontFamily)
        assertEquals(FontFamily.Serif, imported.labelSmall.fontFamily)
    }
}
