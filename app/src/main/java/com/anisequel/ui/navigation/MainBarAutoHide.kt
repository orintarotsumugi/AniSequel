package com.anisequel.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Hides the main bar while a page is being read downwards, and brings it back the
 * moment the reader turns around.
 *
 * ## Why a scroll listener at the root and not a flag on every page
 *
 * The bar floats *over* the pages rather than being part of them, so no single
 * screen owns it. Making each list call `onScrollDirectionChanged` would mean
 * five call sites that have to agree, and a page added later that simply
 * forgets - which is the failure mode [showsMainBar] exists to prevent for
 * visibility. A nested scroll connection mounted once, around the graph, sees
 * every scrollable in the app without any of them knowing it is there.
 *
 * ## Why it never consumes
 *
 * `onPostScroll` is a notification: the child has already applied the delta by
 * the time this runs, and this returns [Offset.Zero] so nothing downstream loses
 * a pixel of travel. It is also what keeps the observation from fighting the
 * pull-to-refresh gesture or a `LazyColumn`'s overscroll.
 *
 * ## The threshold, and why the two directions are not symmetric
 *
 * Hiding takes a deliberate scroll - [THRESHOLD_PX] of travel, roughly a thumb's
 * width on a 3x display - so a small nudge at the top of the list does not blink
 * the bar away. Showing takes one pixel in the other direction, because by then
 * the reader has told us what they want and the bar is the only way to change
 * pages: a bar that needed a second threshold to come back would read as stuck.
 *
 * The values are pixels because a nested scroll reports pixels and this object
 * is not composable, so it has no density to convert with. The threshold is a
 * distance, not a size - a slightly longer swipe on a low-density screen is not
 * a bug.
 */
internal object MainBarAutoHide {

    /** Upwards travel needed to hide the bar. A finger's worth, not a nudge. */
    private const val THRESHOLD_PX = 110f

    /** Whether the bar is currently scrolled away. Read by the host. */
    var hidden by mutableStateOf(false)
        private set

    /** Travel accumulated since the last direction change. */
    private var travelled = 0f

    /**
     * The reader started a new page, or a route change moved the bar.
     *
     * Called by the host on every route change: arriving on a page with the bar
     * already gone would hide the navigation from somebody who has not scrolled
     * that page at all.
     */
    fun reset() {
        hidden = false
        travelled = 0f
    }

    /**
     * The connection mounted once around the navigation graph.
     *
     * `consumed.y` is the distance the content actually moved: negative while
     * the reader is travelling further down a list, positive when they scroll
     * back up.
     */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val dy = consumed.y
            when {
                // Downwards. Accumulate until the gesture is unmistakably a
                // scroll, then stay hidden until it reverses.
                dy < 0f -> {
                    if (hidden) return Offset.Zero
                    travelled += -dy
                    if (travelled >= THRESHOLD_PX) {
                        hidden = true
                        travelled = 0f
                    }
                }
                // Upwards. One pixel is enough: the reader is going back, and
                // the bar is how they leave the page.
                dy > 0f -> {
                    travelled = 0f
                    if (hidden) hidden = false
                }
            }
            return Offset.Zero
        }
    }
}
