package com.anisequel

import com.anisequel.ui.screens.tier.TIER_TRAY_SNAP_SLOP
import com.anisequel.ui.screens.tier.TierRowBand
import com.anisequel.ui.screens.tier.tierRowAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a poster dragged *out of the pool* lands.
 *
 * ## Why this has its own hit test
 *
 * The board answers "which tier is this drop in" with `tierDropTarget`, in the
 * board's own layout coordinates. A tray drag cannot reuse it: the tray is a
 * sibling pinned to the bottom of the screen and the board scrolls inside a column
 * above it, so the tray's pointer and the board's rows do not share an origin.
 *
 * The alternative was to convert one space into the other, and that is a third
 * opinion about the same geometry held by a file that does not own the geometry.
 * The board instead publishes where its rows actually are - in window pixels - and
 * this tests a pointer against those.
 *
 * ## What a null answer means here
 *
 * Not an error: *un-file the title*. Dropping a poster back into the pool is how a
 * mis-drop is undone without opening a sheet, so "nowhere near a row" has to be an
 * instruction rather than a shrug. That is the same contract `tierDropTarget` has,
 * and the two tests together are what stops one of them drifting into "guess the
 * closest" while the other stays honest.
 */
class TierTrayDropTest {

    /** Three rows, 100px tall, 8px apart, in window space. */
    private val bands = listOf(
        TierRowBand("s", top = 200f, bottom = 300f),
        TierRowBand("a", top = 308f, bottom = 408f),
        TierRowBand("b", top = 416f, bottom = 516f)
    )

    private fun dropAt(x: Float, y: Float, slop: Float = TIER_TRAY_SNAP_SLOP) =
        tierRowAt(bands, windowX = x, windowY = y, leftEdge = 0f, rightEdge = 400f, slop = slop)

    @Test
    fun `a release inside a row files into that row`() {
        assertEquals("s", dropAt(x = 200f, y = 250f))
        assertEquals("a", dropAt(x = 200f, y = 350f))
        assertEquals("b", dropAt(x = 200f, y = 450f))
    }

    @Test
    fun `a release in the gap snaps to the nearest row`() {
        // 300..308 is the gap. Eight dp of it is a near miss with one obvious
        // answer, and refusing it is what makes a drag feel broken.
        assertEquals("s", dropAt(x = 200f, y = 304f))
        assertEquals("a", dropAt(x = 200f, y = 412f))
    }

    @Test
    fun `a release far from every row is a deliberate miss`() {
        // Over the pool itself, and over the heading above the first row.
        assertNull(dropAt(x = 200f, y = 800f))
        assertNull(dropAt(x = 200f, y = 100f))
    }

    @Test
    fun `a release beyond the snap slop does not guess`() {
        // Ten px past the bottom of B: inside the slop, so it snaps.
        assertEquals("b", dropAt(x = 200f, y = 516f + 10f))
        // A hundred px past it is not, so it refuses rather than picking B anyway.
        assertNull(dropAt(x = 200f, y = 516f + 100f))
    }

    @Test
    fun `a release beside the board misses every row`() {
        // Past the right edge: the rows are on the board, and this is not. Picking
        // the nearest row here would file a title under a judgement the person
        // never made by aiming at nothing.
        assertNull(tierRowAt(bands, windowX = 500f, windowY = 250f, leftEdge = 0f, rightEdge = 400f))
    }

    @Test
    fun `the snap slop is measured from the edge, not the centre`() {
        // Rows are ~100px tall, so a centre-referenced slop would either be
        // enormous enough to swallow the heading above the first row, or too small
        // to bridge the 8px gap between two rows at all. Probed at the *top* edge of
        // the first row, which is the boundary that matters: just inside the slop
        // snaps, just outside it refuses, and nothing in between is guessed at.
        val firstRowTop = bands.first().top

        assertEquals(
            "one pixel inside the slop is a near miss, and near misses snap",
            "s",
            dropAt(x = 200f, y = firstRowTop - (TIER_TRAY_SNAP_SLOP - 1f))
        )
        assertNull(
            "one pixel outside the slop is a deliberate miss",
            dropAt(x = 200f, y = firstRowTop - (TIER_TRAY_SNAP_SLOP + 1f))
        )
    }

    @Test
    fun `no rows means no drop`() {
        assertNull(
            tierRowAt(emptyList(), windowX = 200f, windowY = 250f, leftEdge = 0f, rightEdge = 400f)
        )
    }
}