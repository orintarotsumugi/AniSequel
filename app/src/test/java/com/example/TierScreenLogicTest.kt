package com.example

import com.example.domain.tier.TierAssignment
import com.example.domain.tier.TierCandidate
import com.example.domain.tier.TierDefinition
import com.example.domain.tier.TierLayoutOptions
import com.example.domain.tier.TierListLayout
import com.example.domain.tier.layoutTierList
import com.example.ui.screens.tier.TIER_DROP_SNAP_SLOP
import com.example.ui.screens.tier.TIER_MAX_ZOOM
import com.example.ui.screens.tier.TIER_MIN_ZOOM
import com.example.ui.screens.tier.clampTierZoom
import com.example.ui.screens.tier.tierDropTarget
import com.example.ui.screens.tier.tierItemAt
import com.example.ui.screens.tier.tierListSubtitle
import com.example.ui.screens.tier.tierRowColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Tier List screen's decisions - the ones the screen makes but does
 * not draw.
 *
 * The board is arranged by `layoutTierList` and this file is the only other
 * thing that knows where anything is, so each test builds a *real* arrangement
 * with the same options `TierListScreen` uses rather than hand-written boxes. A
 * hand-written box that disagreed with the layout by a few dp would make a test
 * pass for the wrong reason, which is the one outcome worse than no test.
 *
 * What is being pinned:
 *
 *  - [tierDropTarget] decides what a drop means. Two ways of missing a row are
 *    different mistakes and must not get the same answer: the 8dp gap *between*
 *    two rows is a near miss with one obvious answer, while a release over the
 *    heading or past the end of the board is a deliberate "take this out of my
 *    list". Filing a title into the nearest tier when the person pointed nowhere
 *    is the exact assertion this feature refuses to make anywhere else - it is
 *    why unrated titles are not auto-filed under F either.
 *  - [clampTierZoom] keeps a pinch inside the range the board can render at, and
 *    swallows the NaN that a coincident two-pointer pinch produces. `coerceIn`
 *    passes NaN straight through, and every offset downstream of a NaN scale is a
 *    layout that never draws.
 *  - [tierRowColor] is the conversion the whole board depends on and the easiest
 *    line in the screen to get subtly wrong: handed a packed `0xAARRGGBB` Long,
 *    Compose's `Color(Int)` overload produces a *transparent* colour rather than
 *    an error.
 */
class TierScreenLogicTest {

    /** The options `TierListScreen` lays the board out with, spelled out again. */
    private val options = TierLayoutOptions(
        width = 360f,
        labelGutter = 52f,
        padding = 10f,
        itemWidth = 64f,
        itemGap = 6f,
        rowGap = 8f,
        captionHeight = 32f,
        headerHeight = 0f,
        minRowHeight = 48f,
        minCanvasHeight = 200f
    )

    private val tiers = listOf(
        TierDefinition("s", "S", 0xFFE5484D),
        TierDefinition("a", "A", 0xFFF76B15),
        TierDefinition("b", "B", 0xFFFFC53D)
    )

    /** Three rows, each with entries, so every row is taller than its minimum. */
    private fun board(): TierListLayout = layoutTierList(
        tiers = tiers,
        assignment = TierAssignment(
            mapOf(1 to "s", 2 to "s", 3 to "a", 4 to "a", 5 to "b")
        ),
        candidates = (1..5).map { TierCandidate(it, "Title $it", null, 80) },
        title = "Tier List",
        subtitle = null,
        options = options
    )

    /** A point in the middle of a row, horizontally and vertically. */
    private fun centreOf(layout: TierListLayout, rowIndex: Int) =
        (layout.rows[rowIndex].x + layout.rows[rowIndex].right) / 2f to
            layout.rows[rowIndex].y + layout.rows[rowIndex].height / 2f

    // ---- Drop target -------------------------------------------------------

    @Test
    fun `a release inside a row files it under that row`() {
        val layout = board()
        val (x, y) = centreOf(layout, 1)

        assertEquals("a", tierDropTarget(layout.rows, x, y))
    }

    @Test
    fun `a release in the gap between two rows snaps to the nearer edge`() {
        val layout = board()
        val (x, _) = centreOf(layout, 0)
        val gapMiddle = (layout.rows[0].bottom + layout.rows[1].y) / 2f

        // The gap is `rowGap` wide, so these two points are one dp either side of
        // dead centre and unambiguously nearer one row than the other.
        assertEquals("s", tierDropTarget(layout.rows, x, gapMiddle - 1f))
        assertEquals("a", tierDropTarget(layout.rows, x, gapMiddle + 1f))
    }

    @Test
    fun `dead centre of a gap still resolves rather than refusing`() {
        val layout = board()
        val (x, _) = centreOf(layout, 0)
        val gapMiddle = (layout.rows[0].bottom + layout.rows[1].y) / 2f

        // Not asserting *which* row: the two distances are equal, and the rule
        // that matters is that the gap never resolves to nothing. A drop that
        // silently refused would read as a broken drag.
        assertNotNull(tierDropTarget(layout.rows, x, gapMiddle))
    }

    @Test
    fun `a release just outside a row still snaps to it`() {
        val layout = board()
        val (x, _) = centreOf(layout, 0)

        assertTrue(
            "a drop inside the snap slop of the first row must not clear the title",
            tierDropTarget(layout.rows, x, layout.rows[0].y - (TIER_DROP_SNAP_SLOP - 4f)) == "s"
        )
        assertEquals(
            "b",
            tierDropTarget(
                layout.rows,
                x,
                layout.rows[2].bottom + (TIER_DROP_SNAP_SLOP - 4f)
            )
        )
    }

    @Test
    fun `a release well above the first row clears rather than guessing`() {
        val layout = board()
        val (x, _) = centreOf(layout, 0)

        // Over the heading. Nothing is within snapping distance of any row, so
        // `moveTo` gets a null tier id and the title goes back to the library.
        assertNull(tierDropTarget(layout.rows, x, layout.rows[0].y - 40f))
    }

    @Test
    fun `a release well below the last row clears rather than guessing`() {
        val layout = board()
        val (x, _) = centreOf(layout, 2)

        assertNull(tierDropTarget(layout.rows, x, layout.rows[2].bottom + 40f))
    }

    @Test
    fun `a release past the side of a row clears rather than guessing`() {
        val layout = board()
        val (_, y) = centreOf(layout, 1)

        // Horizontally outside the board but vertically inside the middle row.
        // The nearest row is at zero distance, so only the horizontal test can
        // save this - and if that test were missing the drop would land in "a".
        assertNull(tierDropTarget(layout.rows, layout.rows[1].x - 5f, y))
        assertNull(tierDropTarget(layout.rows, layout.rows[1].right + 5f, y))
    }

    @Test
    fun `an empty board has no drop target`() {
        // `removeTier` refuses to leave zero rows, but the screen must not throw
        // on the way down from one row to none if that guard ever changes.
        assertNull(tierDropTarget(emptyList(), 180f, 100f))
    }

    // ---- Zoom --------------------------------------------------------------

    @Test
    fun `zoom is held at both ends`() {
        assertEquals(TIER_MIN_ZOOM, clampTierZoom(0.01f), 0f)
        assertEquals(TIER_MIN_ZOOM, clampTierZoom(-4f), 0f)
        assertEquals(TIER_MAX_ZOOM, clampTierZoom(40f), 0f)
        assertEquals(TIER_MAX_ZOOM, clampTierZoom(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun `zoom inside the range is left alone`() {
        assertEquals(1f, clampTierZoom(1f), 0f)
        assertEquals(1.5f, clampTierZoom(1.5f), 0f)
        assertEquals(TIER_MIN_ZOOM, clampTierZoom(TIER_MIN_ZOOM), 0f)
        assertEquals(TIER_MAX_ZOOM, clampTierZoom(TIER_MAX_ZOOM), 0f)
    }

    @Test
    fun `a pinch that produces NaN cannot take the layout with it`() {
        // Two pointers momentarily coinciding make the zoom factor 0/0. NaN fails
        // both comparisons in `coerceIn`, so without the guard it would pass
        // through and every offset downstream would be NaN - a board that lays
        // out to nothing and never draws.
        val clamped = clampTierZoom(Float.NaN)

        assertTrue("clampTierZoom handed a NaN straight back", !clamped.isNaN())
        assertEquals(TIER_MIN_ZOOM, clamped, 0f)
    }

    // ---- Hit testing -------------------------------------------------------

    @Test
    fun `a press on a poster picks that poster, and a press on a row does not`() {
        val layout = board()
        val first = layout.items.first()

        assertEquals(
            "a press in the middle of a poster must pick that poster",
            first.mediaId,
            tierItemAt(
                layout.items,
                first.x + first.width / 2f,
                first.y + first.height / 2f
            )
        )
        // The label gutter belongs to the row, not to the tile beside it, so a
        // long press there must not start a drag.
        assertNull(tierItemAt(layout.items, 1f, layout.rows[0].y + 10f))
    }

    // ---- Colour ------------------------------------------------------------

    @Test
    fun `a packed ARGB long becomes an opaque colour of the same hue`() {
        // `Color(Int)` would read this as `0x00FFE5484D` and produce a fully
        // transparent red. Six invisible rows is the failure this pins.
        assertEquals(0xFFE5484D00000000uL, tierRowColor(0xFFE5484D).value)
    }

    @Test
    fun `a long that came back negative from storage still converts`() {
        // Any colour whose top byte has the sign bit set round-trips through a
        // signed store as a negative Long, which is what this is.
        assertEquals(0xFF00000000000000uL, tierRowColor(-0x1000000L).value)
    }

    @Test
    fun `a fully saturated long is masked rather than shifted off the end`() {
        // The mask is what lets a signed store round-trip. `-1L` is the shape
        // a white written as an Int constant takes on the way back out of one,
        // and it has to come out opaque white rather than transparent black.
        //
        // `0xFFFFFFFF00000000`, not `0xFFFFFFFFFFFFFFFF`. `Color` packs
        // `0xAARRGGBB` into the *top* 32 bits and ignores the bottom half, so
        // the two would draw identically - but only the masked form is the
        // packed colour every other `Color` in the app holds, and asserting the
        // unmasked one would pin a value Compose never builds by itself.
        assertEquals(0xFFFFFFFF00000000uL, tierRowColor(-1L).value)
        assertEquals(0xFFFFFFFF00000000uL, tierRowColor(0xFFFFFFFF.toLong()).value)
    }

    // ---- Empty-state wording ----------------------------------------------

    @Test
    fun `an unloaded library is not reported as an empty one`() {
        assertEquals("Your library has not loaded yet", tierListSubtitle(ranked = 0, librarySize = 0))
        assertEquals("Nothing ranked yet", tierListSubtitle(ranked = 0, librarySize = 271))
        assertEquals("12 of 271 titles ranked", tierListSubtitle(ranked = 12, librarySize = 271))
    }
}
