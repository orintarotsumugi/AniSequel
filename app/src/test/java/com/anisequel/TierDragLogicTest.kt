package com.anisequel

import com.anisequel.domain.tier.TierDefinition
import com.anisequel.ui.screens.tier.TIER_DRAG_EDGE_DP
import com.anisequel.ui.screens.tier.tierDragHint
import com.anisequel.ui.screens.tier.tierDragScrollStrength
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the two decisions a drag makes away from the layout: how fast holding
 * near an edge scrolls the board, and what the pill under the finger promises.
 *
 * Split from `TierScreenLogicTest` when that file crossed the 700-line ceiling;
 * these are the part of the board's logic about the *gesture* rather than about
 * where a tile sits.
 */
class TierDragLogicTest {

    private val tiers = listOf(
        TierDefinition("s", "S", 0xFFE5484D),
        TierDefinition("a", "A", 0xFFF76B15),
        TierDefinition("b", "B", 0xFFFFC53D)
    )

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

    @Test
    fun `the tray's strip neither scrolls nor hosts the bottom band`() {
        // The viewport ends at 900 but the tray covers everything from 756 down.
        // Holding just above the shelf scrolls; holding *on* the shelf does not,
        // because the rows arriving under the finger are rows nobody can see.
        assertEquals(
            1f,
            tierDragScrollStrength(
                pointerY = 756f,
                viewportTop = 100f,
                viewportBottom = 900f,
                edge = TIER_DRAG_EDGE_DP,
                bottomLimit = 756f
            ),
            0.001f
        )
        assertEquals(
            0f,
            tierDragScrollStrength(
                pointerY = 800f,
                viewportTop = 100f,
                viewportBottom = 900f,
                edge = TIER_DRAG_EDGE_DP,
                bottomLimit = 756f
            ),
            0f
        )
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

    @Test
    fun `a tray drag released off the board says it stays in the pool`() {
        // The poster is already unranked, so "take it out of the list" would
        // promise a change that the release does not make.
        assertEquals(
            "Release to leave it in the pool",
            tierDragHint(tiers, null, fromTray = true)
        )
    }
}
