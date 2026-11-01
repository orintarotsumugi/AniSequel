package com.anisequel

import com.anisequel.ui.components.expressive.NavGesture
import com.anisequel.ui.components.expressive.navGestureDecision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the navigation bar decides a hold-and-swipe was.
 *
 * The gesture itself cannot be tested here - there is no `androidTest` source set
 * and no compose rule in the JVM suite - so the decision is extracted and pinned,
 * the same way `TierDragLogicTest` and `MainTabsTest` pin the arithmetic they were
 * pulled out of.
 *
 * The failure this records: `moved` used to mean "the previewed index changed", and
 * the index only changes once the drag has passed a whole destination width. A hold
 * with a short shove - which is what "hold it and move it left" actually is - left
 * the index where it started, so the gesture was classified as a still hold and the
 * hold shortcut fired. On the main bar the shortcut is the jump to the inbox, so
 * swiping the bar twice on the profile opened a message thread.
 */
class NavGestureDecisionTest {

    @Test
    fun `a hold the finger never left fires the shortcut`() {
        assertEquals(
            NavGesture.Hold(2),
            navGestureDecision(
                startIndex = 2,
                settledIndex = 2,
                travelledPx = 3f,
                touchSlopPx = 12f,
                hasHoldShortcut = true
            )
        )
    }

    /**
     * The regression. Shorter than a destination step, longer than slop: the index
     * never moved, so the old rule called this a hold and jumped to Messages.
     */
    @Test
    fun `a short drag that stayed on its tab asks for nothing`() {
        assertEquals(
            "a drag is not a hold, however little it moved",
            NavGesture.None,
            navGestureDecision(
                startIndex = 2,
                settledIndex = 2,
                travelledPx = 40f,
                touchSlopPx = 12f,
                hasHoldShortcut = true
            )
        )
    }

    @Test
    fun `an out-and-back drag is the no-op it always was`() {
        // Travelled three destination widths and came home: the finger changed its
        // mind, it did not ask for the tab it started on.
        assertEquals(
            NavGesture.None,
            navGestureDecision(3, 3, travelledPx = 380f, touchSlopPx = 12f, hasHoldShortcut = true)
        )
    }

    @Test
    fun `a drag that ends on another destination switches to it`() {
        assertEquals(
            NavGesture.Switch(0),
            navGestureDecision(3, 0, travelledPx = 300f, touchSlopPx = 12f, hasHoldShortcut = true)
        )
        assertEquals(
            "a bar with no shortcut still switches",
            NavGesture.Switch(4),
            navGestureDecision(1, 4, travelledPx = 200f, touchSlopPx = 12f, hasHoldShortcut = false)
        )
    }

    @Test
    fun `a still finger on a bar with no shortcut does nothing`() {
        assertEquals(
            NavGesture.None,
            navGestureDecision(1, 1, travelledPx = 0f, touchSlopPx = 12f, hasHoldShortcut = false)
        )
    }
}
