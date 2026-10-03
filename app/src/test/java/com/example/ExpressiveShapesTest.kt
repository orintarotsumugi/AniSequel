package com.example

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.components.expressive.ExpressiveShapes
import org.junit.Assert.assertEquals
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

    /**
     * The tests above assert the token *type*. This one asserts the outline the
     * shape actually produces, at the size of a real tab, because that is the
     * property that was actually broken and the type alone does not spell it out.
     *
     * `ExpressiveTabBar` measures its tabs at 44dp tall and lets the label set
     * the width, so roughly 120x44 is what a two-letter tab gets. On those bounds
     * a capsule's corner radius must be exactly half the height - the ends are
     * semicircles - which is what "percent = 50" means in practice.
     *
     * Two things are checked:
     *
     *  - The outline is `Outline.Rounded`. A `RoundedPolygon` drawn through
     *    `toShape()` returns `Outline.Generic`, because it is an arbitrary path.
     *    So this fails outright on the old implementation, which is the bug.
     *  - The radius is 22 = 44 / 2. A `RoundedCornerShape(50.dp)` would clamp to
     *    a corner pair and read as a lozenge instead of a capsule at this height.
     */
    @Test
    fun `the pill renders as a capsule at the size of a real tab`() {
        val outline = ExpressiveShapes.pill.createOutline(
            size = Size(width = 120f, height = 44f),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f)
        )

        assertTrue(
            "the pill must produce a rounded outline, not a generic path; a " +
                "Generic outline means a RoundedPolygon is being stretched to fit " +
                "the tab bounds, which is the original bug",
            outline is Outline.Rounded
        )

        val rect = (outline as Outline.Rounded).roundRect

        assertEquals(
            "top-left corner x must be half the height for the ends to be " +
                "semicircles",
            22f, rect.topLeftCornerRadius.x, TOLERANCE
        )
        assertEquals(
            "top-left corner y must equal its x so the corner is a true circle",
            22f, rect.topLeftCornerRadius.y, TOLERANCE
        )
        assertEquals(
            "bottom-right corner x must be half the height too",
            22f, rect.bottomRightCornerRadius.x, TOLERANCE
        )
        assertEquals(
            "bottom-right corner y must equal its x",
            22f, rect.bottomRightCornerRadius.y, TOLERANCE
        )
    }

    /**
     * The same assertion for the segmented bar's inner token, at the height that
     * component actually uses. `ExpressivePolygonSegmentedBar` measures its
     * segments at 40dp, so the capsule radius is 20 rather than 22 - which is
     * also the check that the token is genuinely being re-derived per draw
     * instead of carrying one baked-in radius.
     */
    @Test
    fun `the segmented pill renders as a capsule at its own height`() {
        val outline = ExpressiveShapes.pillSoft.createOutline(
            size = Size(width = 140f, height = 40f),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f)
        )

        assertTrue(
            "pillSoft must produce a rounded outline, not a stretched path",
            outline is Outline.Rounded
        )

        val rect = (outline as Outline.Rounded).roundRect

        assertEquals("capsule radius at 40dp tall", 20f, rect.topLeftCornerRadius.x, TOLERANCE)
        assertEquals("capsule radius must be circular", 20f, rect.topLeftCornerRadius.y, TOLERANCE)
    }

    private companion object {
        /**
         * Radii come back as exact halves in Compose, but the outline is built
         * through float maths, so compare with a tolerance rather than requiring
         * bit equality.
         */
        const val TOLERANCE = 0.01f
    }
}