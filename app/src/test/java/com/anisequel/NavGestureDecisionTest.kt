package com.anisequel

import com.anisequel.ui.components.expressive.navGestureDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the navigation bar decides a hold-and-swipe was.
 *
 * The gesture itself cannot be tested here - there is no `androidTest` source set
 * and no compose rule in the JVM suite - so the decision is extracted and pinned,
 * the same way `TierDragLogicTest` and `MainTabsTest` pin the arithmetic they were
 * pulled out of.
 *
 * Two failures are recorded here, in order.
 *
 * The first: `moved` used to mean "the previewed index changed", and the index only
 * changes once the drag has passed a whole destination width. A hold with a short
 * shove - which is what "hold it and move it left" actually is - left the index where
 * it started, so the gesture was classified as a still hold and the hold *shortcut*
 * fired. Swiping the bar twice on the profile opened a message thread.
 *
 * The second: the shortcut itself. Holding Profile and releasing opened the Messages
 * segment even though the finger never moved, because a bar with a shortcut treats a
 * still hold as the shortcut. A hold is how a swipe begins - the bar lifts, the
 * haptic ticks, and that is the whole acknowledgement - so a still hold now asks for
 * nothing, and the only thing that can navigate out of this gesture is a drag that
 * ends somewhere else.
 */
class NavGestureDecisionTest {

    @Test
    fun `a hold the finger never left asks for nothing`() {
        assertNull(
            "the bar lifts and ticks, and goes nowhere",
            navGestureDecision(
                startIndex = 2,
                settledIndex = 2,
                travelledPx = 3f,
                touchSlopPx = 12f
            )
        )
    }

    /**
     * The first regression. Shorter than a destination step, longer than slop: the
     * index never moved, so the old rule called this a hold and jumped to Messages.
     */
    @Test
    fun `a short drag that stayed on its tab asks for nothing`() {
        assertNull(
            "a drag is not a hold, however little it moved",
            navGestureDecision(
                startIndex = 2,
                settledIndex = 2,
                travelledPx = 40f,
                touchSlopPx = 12f
            )
        )
    }

    @Test
    fun `an out-and-back drag is the no-op it always was`() {
        // Travelled three destination widths and came home: the finger changed its
        // mind, it did not ask for the tab it started on.
        assertNull(navGestureDecision(3, 3, travelledPx = 380f, touchSlopPx = 12f))
    }

    @Test
    fun `a drag that ends on another destination switches to it`() {
        assertEquals(0, navGestureDecision(3, 0, travelledPx = 300f, touchSlopPx = 12f))
        assertEquals(4, navGestureDecision(1, 4, travelledPx = 200f, touchSlopPx = 12f))
    }

    @Test
    fun `travel measured at exactly the slop has not left the finger yet`() {
        // `>` and not `>=`: a finger that has moved precisely the slop distance is
        // still where the tap began, and the bar must not navigate on it.
        assertNull(navGestureDecision(1, 2, travelledPx = 12f, touchSlopPx = 12f))
    }
}
