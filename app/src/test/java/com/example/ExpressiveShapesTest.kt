package com.example

import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.components.expressive.ExpressiveShapes
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shape tokens that outline every tab bar and segmented control.
 *
 * Both of these used to be `RoundedPolygon`s drawn through `toShape()`, and that
 * is what produced the eggs and blobs in the released UI: a `RoundedPolygon` is
 * authored on a *square* perimeter, and `toShape()` maps that square onto
 * whatever `Size` the layout hands it, scaling x and y independently. On a tab
 * measured 120x44, `MaterialShapes.Pill` came out as a 120x44 ellipse rather
 * than a capsule, and `pillSoft` - which pointed at `Cookie7Sided`, a
 * seven-sided cookie rather than a pill at all - stretched into the blob the
 * selected theme option was drawn as.
 *
 * `RoundedCornerShape(percent = 50)` recomputes its radius as
 * `min(width, height) / 2` on every draw, so it is a correct capsule at any
 * size and any aspect ratio - which matters because these elements are measured
 * from their labels and change width with the text and with font scale.
 *
 * This asserts the *type*, not a rendered pixel, because a plain JVM test cannot
 * rasterise a Compose outline. That is enough to fail loudly if someone swaps a
 * capsule token back to a polygon: the shapes differ in runtime type, and the
 * polygon-backed one is exactly what this test exists to keep out.
 */
class ExpressiveShapesTest {

    @Test
    fun `the tab pill is a capsule and not a stretched polygon`() {
        assertTrue(
            "ExpressiveShapes.pill must be a RoundedCornerShape. A RoundedPolygon " +
                "drawn via toShape() is stretched from a square perimeter to the " +
                "layout bounds, so a 'pill' on a wide, short tab renders as an " +
                "ellipse. See ExpressiveShapes.pill.",
            ExpressiveShapes.pill is RoundedCornerShape
        )
    }

    @Test
    fun `the segmented pill is a capsule and not a stretched polygon`() {
        assertTrue(
            "ExpressiveShapes.pillSoft must be a RoundedCornerShape. This token " +
                "used to point at Cookie7Sided, a seven-sided cookie that is not a " +
                "pill at all, and stretching it is what drew the blob on the " +
                "selected theme option.",
            ExpressiveShapes.pillSoft is RoundedCornerShape
        )
    }

    /**
     * The two tokens are separate names for one shape today. They are kept apart
     * because the call sites mean different things by them - the outer track of a
     * segmented bar versus its selected segment - so pinning them together here
     * is only true while both genuinely want a capsule, and this says so.
     */
    @Test
    fun `both pill tokens resolve to the same capsule geometry`() {
        assertTrue(
            "ExpressiveShapes.pill and pillSoft are both meant to be capsules; " +
                "if one has diverged, update this test deliberately rather than " +
                "letting it drift",
            ExpressiveShapes.pill::class == ExpressiveShapes.pillSoft::class
        )
    }
}