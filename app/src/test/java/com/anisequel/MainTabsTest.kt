package com.anisequel

import com.anisequel.ui.navigation.AppRoutes
import com.anisequel.ui.navigation.MainTab
import com.anisequel.ui.navigation.ProfileRoutes
import com.anisequel.ui.navigation.StudioRoutes
import com.anisequel.ui.navigation.mainTabDestinations
import com.anisequel.ui.navigation.mainTabForRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract the main bottom bar rests on.
 *
 * Two things are pure facts here, and both fail silently at runtime if they drift:
 *
 *  - **Which routes are the bar's pages.** `mainTabForRoute` is the whole
 *    visibility rule - a route that answers `null` gets no bar - so a new
 *    top-level page that forgets to answer hides the bar on itself, and a pushed
 *    screen that answers wrongly draws the bar (with a selected tab the reader
 *    never chose) over somebody else's dashboard or a Settings page.
 *  - **The order of the destinations matches the enum.** `MainBar` passes
 *    `selectedIndex = selected.ordinal` and turns the bar's index back into a
 *    `MainTab` with `MainTab.entries[index]`; the two only agree while the
 *    destination list is derived from the enum in order. A hand-written list that
 *    reorders two entries would not compile wrong anywhere - it would just select
 *    the wrong tab.
 *
 * The profile case is named rather than left to the general rule because it is the
 * one that looks like a literal and is not: `NavDestination.route` reports the
 * registered *pattern* (`profile?tab={tab}`), so a mapping written against
 * `ProfileRoutes.OWN_DEFAULT` (`profile?tab=`) would silently never match - the bar
 * would hide exactly on the tab that is the viewer's own profile.
 */
class MainTabsTest {

    @Test
    fun `every page of the bar answers with its tab`() {
        assertEquals(MainTab.HOME, mainTabForRoute(AppRoutes.DASHBOARD))
        // The demo runs without a session; to the reader it is the same page.
        assertEquals(MainTab.HOME, mainTabForRoute(AppRoutes.DASHBOARD_DEMO))
        assertEquals(MainTab.LIBRARY, mainTabForRoute(AppRoutes.LIBRARY))
        assertEquals(MainTab.PROFILE, mainTabForRoute(ProfileRoutes.OWN_ROUTE))
    }

    @Test
    fun `a screen pushed from a tab gets no bar`() {
        val pushed = listOf(
            AppRoutes.LOGIN,
            AppRoutes.SETTINGS,
            AppRoutes.TIER_LIST,
            // Somebody else's dashboard, reached by scanning a name - not the
            // viewer's Home.
            AppRoutes.DASHBOARD_USER,
            // The pattern, not a built route: this is what `currentDestination.route`
            // reports, and building one here would call `android.net.Uri` - a stub,
            // not an implementation, in a plain JVM test.
            StudioRoutes.ROUTE,
            ProfileRoutes.BY_USERNAME,
            ProfileRoutes.BY_ID,
            // The route the profile is *navigated* with, as opposed to the pattern
            // `currentDestination.route` reports. Reaching this string from the
            // mapper would mean matching on a literal that never appears.
            ProfileRoutes.OWN_DEFAULT
        )

        pushed.forEach { route ->
            assertNull(
                "$route is not one of the bar's pages, so the bar must not be " +
                    "drawn over it",
                mainTabForRoute(route)
            )
        }
    }

    @Test
    fun `a route the bar does not know about gets no bar`() {
        assertNull(mainTabForRoute(null))
        assertNull(mainTabForRoute("somewhere_new"))
    }

    @Test
    fun `the bar reads Home, Library, Profile, once each`() {
        val destinations = mainTabDestinations(profileAvatarUrl = null)

        // The order is the requirement the bar was built to - Home, then the
        // viewer's own list, then the viewer - and it is also the enum's order,
        // which is what `MainBar` indexes by. A reorder fails here rather than
        // selecting a tab nobody chose.
        assertEquals(
            listOf("Home", "Library", "Profile"),
            destinations.map { it.label }
        )
        assertEquals(
            listOf("main_tab_home", "main_tab_library", "main_tab_profile"),
            destinations.map { it.testTag }
        )
        assertEquals(MainTab.entries.size, destinations.size)
    }

    @Test
    fun `only the profile tab carries the avatar, and it keeps a glyph without one`() {
        val avatar = "https://s4.anilist.co/file/avatar.png"

        mainTabDestinations(profileAvatarUrl = avatar).forEach { destination ->
            val isProfile = destination.testTag == "main_tab_profile"
            assertEquals(
                "the avatar is the Profile tab's identity; drawing it on any other " +
                    "tab would make two destinations look like the same place",
                if (isProfile) avatar else null,
                destination.iconUrl
            )
        }

        val withoutAvatar = mainTabDestinations(profileAvatarUrl = null)
            .first { it.testTag == "main_tab_profile" }
        assertTrue(
            "the bar is drawn while the viewer's avatar is still loading, and a tab " +
                "with no icon and no image is a hole in the bar",
            withoutAvatar.iconUrl == null
        )
    }
}
