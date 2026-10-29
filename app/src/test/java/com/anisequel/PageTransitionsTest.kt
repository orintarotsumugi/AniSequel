package com.anisequel

import androidx.compose.animation.AnimatedContentTransitionScope
import com.anisequel.ui.navigation.AppRoutes
import com.anisequel.ui.navigation.ProfileRoutes
import com.anisequel.ui.navigation.pageEnterDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which edge a page move comes from.
 *
 * This is the half of the transition that has no visible constant to compare
 * against - the springs are shared tokens, but the direction is a decision - so a
 * wrong branch is only ever caught by watching a pop and thinking it looks like a
 * push. That is exactly what happened: `popEnterPage` called `enterPage`, which
 * defaults to the push direction, so Back revealed the previous page from the same
 * edge the pushed page had just entered from.
 *
 * `SlideDirection.Left` means the content travels left, which is a page arriving
 * from the right - the direction of a forward push. `Right` is the reverse: the
 * content travels right, so it arrives from the left, which is a pop.
 */
class PageTransitionsTest {

    private val left = AnimatedContentTransitionScope.SlideDirection.Left
    private val right = AnimatedContentTransitionScope.SlideDirection.Right

    @Test
    fun `a push from a tab enters from the right`() {
        assertEquals(
            left,
            pageEnterDirection(
                from = AppRoutes.DASHBOARD,
                to = AppRoutes.SETTINGS,
                popped = false
            )
        )
    }

    @Test
    fun `a pop reveals the previous page from the left`() {
        assertEquals(
            "Back must unwind the push: Settings entered from the right, so the " +
                "dashboard it reveals enters from the left as Settings leaves to the right",
            right,
            pageEnterDirection(
                from = AppRoutes.SETTINGS,
                to = AppRoutes.DASHBOARD,
                popped = true
            )
        )
    }

    @Test
    fun `a push to a tab further along the bar travels left`() {
        assertEquals(
            left,
            pageEnterDirection(
                from = AppRoutes.DASHBOARD,
                to = AppRoutes.LIBRARY,
                popped = false
            )
        )
    }

    @Test
    fun `a tab pop travels back the way it came`() {
        assertEquals(
            "Library is to the right of Home on the bar, so going back is a " +
                "rightward move even though it is also a pop",
            right,
            pageEnterDirection(
                from = AppRoutes.LIBRARY,
                to = AppRoutes.DASHBOARD,
                popped = true
            )
        )
    }

    @Test
    fun `the tier list keeps its own vertical move`() {
        // The direction helper is not consulted for the tier list - `enterPage`
        // and `popEnterPage` branch on it first - but the tab pair is still
        // ordered, so a future caller cannot get a nonsensical answer.
        assertEquals(
            right,
            pageEnterDirection(
                from = AppRoutes.TIER_LIST,
                to = AppRoutes.DASHBOARD,
                popped = true
            )
        )
    }

    @Test
    fun `unknown routes still have a direction`() {
        assertEquals(left, pageEnterDirection(from = null, to = null, popped = false))
        assertEquals(
            "a pop of a route the bar does not know must still unwind rather than " +
                "enter from the push edge",
            right,
            pageEnterDirection(from = "somewhere_new", to = null, popped = true)
        )
    }

    @Test
    fun `the viewer's own profile is a tab, not a pushed page`() {
        assertEquals(
            left,
            pageEnterDirection(
                from = AppRoutes.DASHBOARD,
                to = ProfileRoutes.OWN_ROUTE,
                popped = false
            )
        )
        assertEquals(
            right,
            pageEnterDirection(
                from = ProfileRoutes.OWN_ROUTE,
                to = AppRoutes.DASHBOARD,
                popped = true
            )
        )
    }
}
