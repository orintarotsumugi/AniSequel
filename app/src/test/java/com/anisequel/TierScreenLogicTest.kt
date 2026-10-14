package com.anisequel

import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierListDocument
import com.anisequel.domain.tier.TierListLayout
import com.anisequel.domain.tier.layoutTierList
import com.anisequel.ui.screens.tier.ScreenTierOptions
import com.anisequel.ui.screens.tier.TIER_DRAG_EDGE_DP
import com.anisequel.ui.screens.tier.TIER_DROP_SNAP_SLOP
import com.anisequel.ui.screens.tier.TIER_MAX_ZOOM
import com.anisequel.ui.screens.tier.TIER_MIN_ZOOM
import com.anisequel.ui.screens.tier.TierDrop
import com.anisequel.ui.screens.tier.clampTierZoom
import com.anisequel.ui.screens.tier.relativeTimeLabel
import com.anisequel.ui.screens.tier.tierDragHint
import com.anisequel.ui.screens.tier.tierDragScrollStrength
import com.anisequel.ui.screens.tier.tierDropCaret
import com.anisequel.ui.screens.tier.tierDropPlacement
import com.anisequel.ui.screens.tier.tierDropTarget
import com.anisequel.ui.screens.tier.tierItemAt
import com.anisequel.ui.screens.tier.tierLibrarySubtitle
import com.anisequel.ui.screens.tier.tierListCardStatus
import com.anisequel.ui.screens.tier.tierListSubtitle
import com.anisequel.ui.screens.tier.tierRowColor
import com.anisequel.ui.screens.tier.visibleTierItems
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
 * with `ScreenTierOptions` - the very options the board uses - rather than a
 * hand-written copy of them. A hand-written box that disagreed with the layout by
 * a few dp would make a test pass for the wrong reason, which is the one outcome
 * worse than no test; a hand-written *options* block that disagreed with the
 * screen is the same mistake one level up, and it is how a test stays green while
 * the board moves a tile somewhere else.
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
 *  - [tierDropPlacement] decides where *inside* the row it goes, which is the
 *    difference between "put it in S" and "put it here".
 *  - [tierDragScrollStrength] is what lets a drag reach a row that is off the
 *    screen, and is the reason a list of fifty titles can be arranged at all.
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

    /** The options the board lays out with - the screen's own, not a copy. */
    private val options = ScreenTierOptions

    private val tiers = listOf(
        TierDefinition("s", "S", 0xFFE5484D),
        TierDefinition("a", "A", 0xFFF76B15),
        TierDefinition("b", "B", 0xFFFFC53D)
    )

    /** Three rows, each with entries, so every row is taller than its minimum. */
    private fun board(): TierListLayout = layoutTierList(
        tiers = tiers,
        assignment = TierAssignment(
            linkedMapOf(1 to "s", 2 to "s", 3 to "a", 4 to "a", 5 to "b")
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

    /** The box of one tile, so the expectations below are read off the layout. */
    private fun tileOf(layout: TierListLayout, mediaId: Int) =
        layout.items.first { it.mediaId == mediaId }

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

    // ---- Where inside the row ----------------------------------------------

    @Test
    fun `a drop on the left half of a tile goes above it`() {
        val layout = board()
        val third = tileOf(layout, 3)

        val drop = tierDropPlacement(
            rows = layout.rows,
            items = layout.items,
            x = third.x + 8f,
            y = third.y + 20f
        )

        assertEquals("a", drop.tierId)
        assertEquals(3, drop.beforeMediaId)
    }

    @Test
    fun `a drop on the right half of a tile goes below it`() {
        val layout = board()
        val third = tileOf(layout, 3)

        val drop = tierDropPlacement(
            rows = layout.rows,
            items = layout.items,
            x = third.right - 8f,
            y = third.y + 20f
        )

        // Past the third tile's own centre, so the tile it lands on top of is the
        // fourth - which is what "between the two" means when the only thing a
        // position can say is which tile to go above.
        assertEquals("a", drop.tierId)
        assertEquals(4, drop.beforeMediaId)
    }

    @Test
    fun `a drop past the last tile of a row lands at the end of that row`() {
        val layout = board()
        val (_, y) = centreOf(layout, 2)

        val drop = tierDropPlacement(rows = layout.rows, items = layout.items, x = 330f, y = y)

        assertEquals("b", drop.tierId)
        assertNull("the end of a row is said with no anchor", drop.beforeMediaId)
    }

    @Test
    fun `the tile being dragged is never its own anchor`() {
        // Read straight off the layout this is a real case: the finger starts on
        // this tile, so for the first few frames it is the tile whose centre is
        // nearest the pointer. Anchoring to it would reorder the row under a
        // finger that has not moved.
        val layout = board()
        val dragged = tileOf(layout, 3)

        val drop = tierDropPlacement(
            rows = layout.rows,
            items = layout.items,
            x = dragged.x + 8f,
            y = dragged.y + 20f,
            skipMediaId = dragged.mediaId
        )

        assertEquals(4, drop.beforeMediaId)
    }

    @Test
    fun `a drop on the second line of a wrapped row goes into that line`() {
        // The branch that only exists because a row wraps: six tiles in a row four
        // wide are two lines, and a drop on the lower one has to mean a position on
        // the lower one - not "somewhere in this row", which is what the old
        // tier-only drop said.
        val wrapped = layoutTierList(
            tiers = listOf(TierDefinition("s", "S", 0xFFE5484D)),
            assignment = TierAssignment((1..6).associateWith { "s" }),
            candidates = (1..6).map { TierCandidate(it, "Title $it", null, 80) },
            title = "Tier List",
            subtitle = null,
            options = options
        )
        val secondLine = wrapped.items.sortedBy { it.y }.drop(4)

        val onSecondLine = tierDropPlacement(
            rows = wrapped.rows,
            items = wrapped.items,
            x = secondLine.first().x - 10f,
            y = secondLine.first().y + 6f
        )
        val inTheGapBetweenLines = tierDropPlacement(
            rows = wrapped.rows,
            items = wrapped.items,
            x = secondLine.first().x - 10f,
            y = wrapped.items.first().bottom + 2f
        )

        assertEquals("s", onSecondLine.tierId)
        assertEquals(
            "a drop on the lower line lands at the start of that line",
            secondLine.first().mediaId,
            onSecondLine.beforeMediaId
        )
        assertEquals(
            "the gap between two lines belongs to the lower one",
            secondLine.first().mediaId,
            inTheGapBetweenLines.beforeMediaId
        )
    }

    @Test
    fun `a drop off every row clears instead of taking a position`() {
        val layout = board()

        val drop = tierDropPlacement(
            rows = layout.rows,
            items = layout.items,
            x = 180f,
            y = layout.rows[0].y - 40f
        )

        assertTrue(drop.clears)
        assertNull(drop.beforeMediaId)
    }

    // ---- The caret that shows where it lands -------------------------------

    @Test
    fun `the caret sits on the seam the tile will drop into`() {
        val layout = board()
        val fourth = tileOf(layout, 4)

        val caret = tierDropCaret(TierDrop("a", 4), layout.rows, layout.items, options)

        assertNotNull(caret)
        // Half a gap to the left of the tile it goes above, so the bar is centred
        // on the space between the two posters rather than drawn over one.
        assertEquals(fourth.x - options.itemGap / 2f, caret!!.x, 0.001f)
        assertEquals(fourth.y, caret.y, 0.001f)
        assertEquals(fourth.height, caret.height, 0.001f)
    }

    @Test
    fun `at the end of a row the caret sits past the last tile`() {
        val layout = board()
        val last = tileOf(layout, 5)

        val caret = tierDropCaret(TierDrop("b", null), layout.rows, layout.items, options)

        assertEquals(last.right + options.itemGap / 2f, caret!!.x, 0.001f)
    }

    @Test
    fun `an empty row gets a caret where its first tile will appear`() {
        // "The first poster in this row" has no tile to be drawn beside, so the
        // caret has to be placed by the row's own geometry - just inside the
        // label gutter, which is where the layout will put tile number one.
        val onlyOneRowFilled = layoutTierList(
            tiers = tiers,
            assignment = TierAssignment(linkedMapOf(1 to "s")),
            candidates = (1..5).map { TierCandidate(it, "Title $it", null, 80) },
            title = "Tier List",
            subtitle = null,
            options = options
        )

        val caret = tierDropCaret(
            TierDrop("a", null),
            onlyOneRowFilled.rows,
            onlyOneRowFilled.items,
            options
        )

        val row = onlyOneRowFilled.rows.first { it.tierId == "a" }
        assertNotNull(caret)
        assertEquals(row.x + options.labelGutter, caret!!.x, 0.001f)
        assertEquals(row.y, caret.y, 0.001f)
        assertEquals(row.height, caret.height, 0.001f)
    }

    @Test
    fun `no caret is drawn for a drop that takes the title out of the list`() {
        // The hint says what is about to happen instead. A caret would promise a
        // slot the release is not going to use.
        val layout = board()

        assertNull(tierDropCaret(TierDrop(null, null), layout.rows, layout.items, options))
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

    // ---- Dragging near an edge --------------------------------------------

    @Test
    fun `a drag in the middle of the screen does not scroll`() {
        assertEquals(
            0f,
            tierDragScrollStrength(
                pointerY = 500f,
                viewportTop = 100f,
                viewportBottom = 900f,
                edge = TIER_DRAG_EDGE_DP
            ),
            0f
        )
    }

    @Test
    fun `a drag at an edge scrolls, faster the closer it gets`() {
        val nearTop = tierDragScrollStrength(136f, 100f, 900f, TIER_DRAG_EDGE_DP)
        val atTop = tierDragScrollStrength(100f, 100f, 900f, TIER_DRAG_EDGE_DP)
        val atBottom = tierDragScrollStrength(900f, 100f, 900f, TIER_DRAG_EDGE_DP)

        // Signed, so negative is up: the caller applies it to the scroll directly.
        assertEquals(-0.5f, nearTop, 0.001f)
        assertEquals(-1f, atTop, 0.001f)
        assertEquals(1f, atBottom, 0.001f)
    }

    @Test
    fun `a finger dragged off the screen keeps scrolling at full speed`() {
        // Which happens constantly: a finger near the edge is usually over the app
        // bar or the navigation bar, and a strength that went *down* there - or
        // flipped sign - would stall the drag at the moment it was working.
        assertEquals(-1f, tierDragScrollStrength(40f, 100f, 900f, TIER_DRAG_EDGE_DP), 0f)
        assertEquals(1f, tierDragScrollStrength(1010f, 100f, 900f, TIER_DRAG_EDGE_DP), 0f)
    }

    @Test
    fun `a viewport with no height cannot divide by zero`() {
        // The first frame, before the column has been measured.
        assertEquals(0f, tierDragScrollStrength(0f, 0f, 0f, TIER_DRAG_EDGE_DP), 0f)
        assertEquals(0f, tierDragScrollStrength(10f, 100f, 900f, edge = 0f), 0f)
    }

    // ---- The hint ----------------------------------------------------------

    @Test
    fun `the hint names the tier the release would file into`() {
        assertEquals("Release to file under A", tierDragHint(tiers, "a"))
        assertEquals(
            "Release here to take it out of the list",
            tierDragHint(tiers, null)
        )
    }

    @Test
    fun `a tier id with no definition still names something`() {
        // Defensive: the tiers and the drop are read from two different values,
        // and an id with no label would otherwise print as an empty pill.
        assertEquals("Release to file under gone", tierDragHint(tiers, "gone"))
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

    // ---- Drag emission -----------------------------------------------------

    /**
     * The dragged tile is dropped from the drawn list, and *only* that tile.
     *
     * This pins the failure that crashed the screen: the skip was applied to the
     * emitted children but not to the slot list the measure policy positions them
     * against, so `check(measurables.size == slots.size)` failed by one the instant
     * a long-press began a drag. The fix is that both halves read one filtered
     * list, which this asserts is the same list.
     */
    @Test
    fun `the tile under the finger is dropped from the drawn list`() {
        val layout = board()
        val dragged = layout.items[2]

        val drawn = visibleTierItems(layout.items, dragged.mediaId)

        assertEquals(
            "the dragged tile must not be emitted as a board child, or the geometry " +
                "emits one poster fewer than it has slots and the board throws",
            layout.items.size - 1,
            drawn.size
        )
        assertTrue(
            "the tile being dragged is still in the drawn list",
            drawn.none { it.mediaId == dragged.mediaId }
        )
        // Order is preserved, because the emitted children and the slots are
        // matched by index rather than by id.
        assertEquals(layout.items.filterNot { it.mediaId == dragged.mediaId }, drawn)
    }

    @Test
    fun `with nothing dragged every tile is drawn`() {
        val layout = board()

        assertEquals(layout.items, visibleTierItems(layout.items, null))
    }

    @Test
    fun `a skip id that matches no tile drops nothing`() {
        val layout = board()

        assertEquals(layout.items, visibleTierItems(layout.items, 9999))
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

    // ---- Wording -----------------------------------------------------------

    @Test
    fun `an unloaded library is not reported as an empty one`() {
        assertEquals("Your library has not loaded yet", tierListSubtitle(ranked = 0, librarySize = 0))
        assertEquals("Nothing ranked yet", tierListSubtitle(ranked = 0, librarySize = 271))
        assertEquals("12 of 271 titles ranked", tierListSubtitle(ranked = 12, librarySize = 271))
    }

    @Test
    fun `the library counts its lists in the singular when there is one`() {
        assertEquals("1 saved list", tierLibrarySubtitle(1))
        assertEquals("0 saved lists", tierLibrarySubtitle(0))
        assertEquals("3 saved lists", tierLibrarySubtitle(3))
    }

    @Test
    fun `a list card says what is in it and when it last moved`() {
        val now = 1_700_000_000_000L
        val ranked = TierListDocument(
            id = "list1",
            name = "One",
            assignment = TierAssignment(linkedMapOf(1 to "s", 2 to "s")),
            updatedAt = now - 3 * 60 * 60 * 1000L
        )

        assertEquals("2 titles ranked - updated 3 hours ago", tierListCardStatus(ranked, now))
        assertEquals(
            "1 title ranked",
            tierListCardStatus(
                TierListDocument(
                    id = "list2",
                    name = "Two",
                    assignment = TierAssignment(mapOf(1 to "s"))
                ),
                now
            )
        )
        assertEquals(
            "Nothing ranked yet",
            tierListCardStatus(TierListDocument(id = "list3", name = "Three"), now)
        )
    }

    @Test
    fun `a relative time reads as prose at every scale`() {
        val now = 1_700_000_000_000L
        fun ago(millis: Long) = relativeTimeLabel(now, now - millis)

        assertEquals("just now", ago(0))
        assertEquals("just now", ago(30_000))
        assertEquals("1 minute ago", ago(60_000))
        assertEquals("5 minutes ago", ago(5 * 60_000))
        assertEquals("1 hour ago", ago(60 * 60_000))
        assertEquals("3 hours ago", ago(3 * 3_600_000))
        assertEquals("yesterday", ago(25 * 3_600_000))
        assertEquals("3 days ago", ago(3 * 86_400_000))
        assertEquals("last week", ago(10 * 86_400_000L))
        assertEquals("3 weeks ago", ago(21 * 86_400_000L))
        assertEquals("last month", ago(40 * 86_400_000L))
        assertEquals("3 months ago", ago(95 * 86_400_000L))
    }

    @Test
    fun `a clock that moved backwards does not count up from nothing`() {
        // A device whose time was changed, or a list written by a device whose
        // clock was ahead. "In -3 hours" would be worse than vague.
        assertEquals("just now", relativeTimeLabel(now = 1_000L, then = 5_000L))
    }
}
