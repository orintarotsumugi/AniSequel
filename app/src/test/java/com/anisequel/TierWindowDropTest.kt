package com.anisequel

import androidx.compose.ui.geometry.Offset
import com.anisequel.domain.tier.TierItemBox
import com.anisequel.domain.tier.TierListLayout
import com.anisequel.domain.tier.TierRowBox
import com.anisequel.ui.screens.tier.TIER_DROP_SNAP_SLOP
import com.anisequel.ui.screens.tier.TierDragState
import com.anisequel.ui.screens.tier.tierDropAtWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a poster dragged *out of the pool* lands, and where the board's own drag
 * lands after the board has scrolled under the finger.
 *
 * ## One resolver, not two
 *
 * The tray's pointer is in window pixels, below a scrolling column, with the board
 * zoomed; the board's rows are in layout dp. This used to be answered by a
 * tray-local hit test over published row bands, which knew a row's vertical extent
 * but not its tiles - so a tray drop could name a tier but never a seam, and every
 * pool drop appended to the row while a board drag could place between two
 * posters. The board now publishes [tierDropAtWindow] and the tray, the highlight,
 * the caret and the release all read it.
 *
 * ## The scroll shift
 *
 * A board that auto-scrolls under a stationary finger moves its rows past the
 * pointer without producing a pointer event. [TierDragState.drop] therefore takes
 * how far the board scrolled since the lift: the ghost already carried that shift
 * in its draw phase, and the drop did not, so after scrolling to reach an
 * off-screen row the marker and the release stayed on the row the tile was picked
 * up from. Pin both halves here.
 */
class TierWindowDropTest {

    /** Three 100-unit rows, 8 apart, in layout units. */
    private val rows = listOf(
        row("s", y = 0f),
        row("a", y = 108f),
        row("b", y = 216f)
    )

    /** Two posters in the first row, so a seam exists to aim at. */
    private val items = listOf(
        item(mediaId = 1, rowY = 0f, x = 56f),
        item(mediaId = 2, rowY = 0f, x = 136f)
    )

    private fun row(tierId: String, y: Float) = TierRowBox(
        tierId = tierId,
        label = tierId.uppercase(),
        colorArgb = 0xFFE5484DL,
        x = 0f,
        y = y,
        width = 400f,
        height = 100f
    )

    private fun item(mediaId: Int, rowY: Float, x: Float) = TierItemBox(
        mediaId = mediaId,
        x = x,
        y = rowY,
        width = 72f,
        height = 100f,
        posterHeight = 66f
    )

    private fun dropAt(
        windowX: Float,
        windowY: Float,
        boardLeft: Float = 0f,
        boardTop: Float = 0f,
        density: Float = 1f,
        zoom: Float = 1f
    ) = tierDropAtWindow(
        rows = rows,
        items = items,
        windowX = windowX,
        windowY = windowY,
        boardLeft = boardLeft,
        boardTop = boardTop,
        density = density,
        zoom = zoom
    )

    // ---- The window-to-layout conversion ---------------------------------------

    @Test
    fun `the board's origin is subtracted before the density and zoom divide`() {
        // The finger is at y 260 on screen; the board starts at 200 and is drawn at
        // 2x. The tile it is over is at layout y 30 - the first row.
        assertEquals("s", dropAt(windowX = 120f, windowY = 260f, boardTop = 200f, density = 2f).tierId)
    }

    @Test
    fun `zoom divides the pointer position with the layout`() {
        // Zoom doubles every layout unit on screen, so 250px below the board's top
        // is layout y 125 - the second row, not the first.
        assertEquals("a", dropAt(windowX = 120f, windowY = 250f, zoom = 2f).tierId)
    }

    @Test
    fun `a board that scrolled changes where the same finger lands`() {
        // 260px below a board that started at 200 is layout 60, in S. The same
        // finger, after the board auto-scrolled up by 60px, is over layout 120, in
        // A. Nothing about the finger changed - only the board did.
        assertEquals("s", dropAt(windowX = 120f, windowY = 260f, boardTop = 200f).tierId)
        assertEquals("a", dropAt(windowX = 120f, windowY = 260f, boardTop = 140f).tierId)
    }

    @Test
    fun `a drop beside the board misses every row`() {
        // Past the right edge, in window pixels: the rows are on the board, and
        // this is not. Picking the nearest row here would file a title under a
        // judgement the person never made by aiming at nothing.
        assertNull(dropAt(windowX = 500f, windowY = 50f).tierId)
    }

    // ---- The row semantics, shared with the board's own drag -------------------

    @Test
    fun `a release inside a row files into that row`() {
        assertEquals("s", dropAt(windowX = 120f, windowY = 50f).tierId)
        assertEquals("a", dropAt(windowX = 120f, windowY = 158f).tierId)
        assertEquals("b", dropAt(windowX = 120f, windowY = 266f).tierId)
    }

    @Test
    fun `a release in the gap snaps to the nearest row`() {
        assertEquals("s", dropAt(windowX = 120f, windowY = 104f).tierId)
        assertEquals("a", dropAt(windowX = 120f, windowY = 212f).tierId)
    }

    @Test
    fun `a release beyond the snap slop does not guess`() {
        val belowLastRow = 316f
        assertEquals("b", dropAt(windowX = 120f, windowY = belowLastRow + 10f).tierId)
        assertNull(dropAt(windowX = 120f, windowY = belowLastRow + 100f).tierId)
        assertNull(dropAt(windowX = 120f, windowY = -100f).tierId)
    }

    @Test
    fun `a release under the tray names no row`() {
        // Geometry alone files this into B: y 266 is inside its band. The tray
        // covers everything from 250 down, and a row nobody can see is not a
        // target somebody aimed at.
        assertEquals("b", dropAt(windowX = 120f, windowY = 266f).tierId)
        val underTray = tierDropAtWindow(
            rows = rows,
            items = items,
            windowX = 120f,
            windowY = 266f,
            boardLeft = 0f,
            boardTop = 0f,
            density = 1f,
            zoom = 1f,
            bottomLimitY = 250f
        )
        assertNull(underTray.tierId)
    }

    @Test
    fun `no rows means no drop`() {
        val drop = tierDropAtWindow(
            rows = emptyList(),
            items = emptyList(),
            windowX = 200f,
            windowY = 50f,
            boardLeft = 0f,
            boardTop = 0f,
            density = 1f,
            zoom = 1f
        )
        assertNull(drop.tierId)
    }

    // ---- Where inside the row ---------------------------------------------------

    @Test
    fun `a release over the left half of a poster lands before it`() {
        // The first poster spans x 56..128; its midpoint is 92.
        assertEquals(1, dropAt(windowX = 70f, windowY = 50f).beforeMediaId)
    }

    @Test
    fun `a release past the last poster lands at the end of the row`() {
        // A null anchor is "end of row", not "no position" - it is what lets the
        // pool drop a title after everything already placed.
        val drop = dropAt(windowX = 300f, windowY = 50f)
        assertEquals("s", drop.tierId)
        assertNull(drop.beforeMediaId)
    }

    // ---- The board's drag after auto-scroll ------------------------------------

    @Test
    fun `the board's drop counts the scroll that happened under the finger`() {
        val layout = TierListLayout(
            width = 400f,
            height = 500f,
            title = "",
            subtitle = null,
            rows = rows,
            items = items
        )
        val drag = TierDragState()
        drag.lift(mediaId = 1, origin = Offset(200f, 50f), boardTop = 0f)

        assertEquals(
            "without scrolling, the lift point is still over S",
            "s",
            drag.drop(layout, zoom = 1f, density = 1f, scrollShift = 0f).tierId
        )
        assertEquals(
            "after the board scrolled up by 88 layout units, the same finger is over A",
            "a",
            drag.drop(layout, zoom = 1f, density = 1f, scrollShift = 88f).tierId
        )
    }

    @Test
    fun `the snapshot slop is the board's own`() {
        // The tray used to snap at 24 window pixels; it now goes through the
        // board's placement, so both drags snap at the same distance in layout
        // units. Probed at the top edge of the first row.
        assertEquals(
            "inside the slop is a near miss, and near misses snap",
            "s",
            dropAt(windowX = 120f, windowY = -(TIER_DROP_SNAP_SLOP - 1f)).tierId
        )
        assertNull(
            "outside the slop is a deliberate miss",
            dropAt(windowX = 120f, windowY = -(TIER_DROP_SNAP_SLOP + 1f)).tierId
        )
    }
}
