package com.anisequel

import com.anisequel.ui.widget.WidgetImageBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget cover memory budget.
 *
 * The old loader decoded to a fixed pixel backstop. That is one number for every
 * device and every number of placed widgets, and the case it fails is the one
 * widgets actually ship in: several tiles, each decoding at once. These tests
 * pin the properties the calculation must keep rather than a specific phone's
 * arithmetic.
 */
class WidgetImageBudgetTest {

    @Test
    fun `a phone-sized display gets the edge-bounded ceiling`() {
        val budget = WidgetImageBudget.maxPixels(1080, 2400, imageCount = 1)

        assertEquals(
            "one tile on a 1080p phone is capped by MAX_EDGE_PX, not by the display",
            WidgetImageBudget.MAX_EDGE_PX * WidgetImageBudget.MAX_EDGE_PX,
            budget
        )
    }

    @Test
    fun `more images mean less memory each`() {
        val one = WidgetImageBudget.maxPixels(480, 800, imageCount = 1)
        val six = WidgetImageBudget.maxPixels(480, 800, imageCount = 6)

        assertTrue("one image may use more than six", one > six)
        assertTrue(
            "the budget still has to be a usable bitmap",
            six >= WidgetImageBudget.MIN_EDGE_PX * WidgetImageBudget.MIN_EDGE_PX
        )
    }

    @Test
    fun `a degenerate display never produces a zero-pixel budget`() {
        val budget = WidgetImageBudget.maxPixels(0, 0, imageCount = 0)

        assertEquals(
            "a zero would ask the decoder for an infinitely sampled bitmap",
            WidgetImageBudget.MIN_EDGE_PX * WidgetImageBudget.MIN_EDGE_PX,
            budget
        )
    }

    @Test
    fun `target size keeps the cover aspect and the edge clamps`() {
        val (width, height) = WidgetImageBudget.targetSize(
            WidgetImageBudget.MAX_EDGE_PX * WidgetImageBudget.MAX_EDGE_PX
        )

        assertTrue("width is clamped to the ceiling", width <= WidgetImageBudget.MAX_EDGE_PX)
        assertTrue(
            "height follows the cover aspect rather than an independent clamp",
            height <= (WidgetImageBudget.MAX_EDGE_PX * WidgetImageBudget.COVER_ASPECT).toInt()
        )
        assertTrue("width is never a degenerate tile", width >= WidgetImageBudget.MIN_EDGE_PX)
        assertTrue("height is never a degenerate tile", height >= WidgetImageBudget.MIN_EDGE_PX)
        assertTrue(
            "the shape is portrait cover art, not a square",
            height > width
        )
    }
}
