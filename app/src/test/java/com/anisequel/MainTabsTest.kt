package com.anisequel

import com.anisequel.ui.navigation.AppRoutes
import com.anisequel.ui.navigation.MainTab
import com.anisequel.ui.navigation.ProfileRoutes
import com.anisequel.ui.navigation.StudioRoutes
import com.anisequel.ui.navigation.enabledMainTabs
import com.anisequel.ui.navigation.mainTabDestinations
import com.anisequel.ui.navigation.mainTabForRoute
import com.anisequel.ui.navigation.showsMainBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract the main bottom bar rests on.
 *
 * Three things are pure facts here, and all of them fail silently at runtime if
 * they drift:
 *
 *  - **Which routes are the bar's pages.** `mainTabForRoute` is the whole
 *    visibility rule for everything *pushed* from a tab - a route that answers
 *    `null` gets no bar - so a new top-level page that forgets to answer hides the
 *    bar on itself, and a pushed screen that answers wrongly draws the bar (with a
 *    selected tab the reader never chose) over somebody else's dashboard or a
 *    Settings page.
 *  - **Which of those pages draw the bar.** `showsMainBar` is the exception the
 *    tier list needs, and it is a *state* rather than a route: the library of
 *    lists keeps the bar, and only an open board hides it because its own tray is
 *    pinned over the same edge.
 *  - **The order of the destinations matches the list `MainBar` indexes.** `MainBar`
 *    passes `selectedIndex = tabs.indexOf(selected)` and turns the bar's index back
 *    into a `MainTab` with `tabs[index]`; the two only agree while the destination
 *    list is derived from the same list. A hand-written list that reordered two
 *    entries would not compile wrong anywhere - it would just select the wrong tab.
 *    This is also why the index cannot come from `MainTab.ordinal`: the tier tab can
 *    be switched off, and an ordinal would then point one slot too far right.
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
        assertEquals(MainTab.TIERS, mainTabForRoute(AppRoutes.TIER_LIST))
        assertEquals(MainTab.PROFILE, mainTabForRoute(ProfileRoutes.OWN_ROUTE))
    }

    @Test
    fun `a screen pushed from a tab gets no bar`() {
        val pushed = listOf(
            AppRoutes.LOGIN,
            AppRoutes.SETTINGS,
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
    fun `the tier library draws the bar and an open board does not`() {
        assertEquals(MainTab.TIERS, mainTabForRoute(AppRoutes.TIER_LIST))
        assertTrue(
            "the library of lists is one of the bar's pages; without the bar it " +
                "was the only page in the app you could not leave by the bar",
            showsMainBar(mainTabForRoute(AppRoutes.TIER_LIST), tierBoardOpen = false)
        )
        assertFalse(
            "the board's tray is pinned over the bottom edge; a floating bar " +
                "under it would be a second set of controls in the same strip",
            showsMainBar(mainTabForRoute(AppRoutes.TIER_LIST), tierBoardOpen = true)
        )
    }

    @Test
    fun `every other page of the bar draws it`() {
        listOf(AppRoutes.DASHBOARD, AppRoutes.LIBRARY, ProfileRoutes.OWN_ROUTE)
            .forEach { route ->
                assertTrue(
                    "$route must draw the bar",
                    showsMainBar(mainTabForRoute(route), tierBoardOpen = false)
                )
            }
        assertFalse(
            "a pushed screen draws no bar",
            showsMainBar(mainTabForRoute(AppRoutes.SETTINGS), tierBoardOpen = false)
        )
        assertFalse(showsMainBar(null, tierBoardOpen = false))
        assertTrue(
            "a board left open while the route changed must not hide the bar on " +
                "whatever page comes next - the flag only means anything on TIERS",
            showsMainBar(MainTab.LIBRARY, tierBoardOpen = true)
        )
    }

    @Test
    fun `the bar reads Home, Library, Tiers, Profile, once each`() {
        val destinations = mainTabDestinations(
            tabs = enabledMainTabs(tierListTab = true),
            profileAvatarUrl = null
        )

        // The order is the requirement the bar was built to - Home, the viewer's
        // own list, the ranking, then the viewer - and it is also the enum's order,
        // which is what `MainBar` indexes by. A reorder fails here rather than
        // selecting a tab nobody chose.
        assertEquals(
            listOf("Home", "Library", "Tiers", "Profile"),
            destinations.map { it.label }
        )
        assertEquals(
            listOf(
                "main_tab_home",
                "main_tab_library",
                "main_tab_tiers",
                "main_tab_profile"
            ),
            destinations.map { it.testTag }
        )
        assertEquals(MainTab.entries.size, destinations.size)
    }

    @Test
    fun `turning the tier tab off removes one slot and nothing else`() {
        val tabs = enabledMainTabs(tierListTab = false)

        assertEquals(listOf(MainTab.HOME, MainTab.LIBRARY, MainTab.PROFILE), tabs)
        assertEquals(
            "the bar's index is an index into this list, so the Profile tab must " +
                "move from 3 to 2 rather than pointing at nothing",
            2,
            tabs.indexOf(MainTab.PROFILE)
        )
        assertEquals(
            "the setting only takes the tab off the bar; the page is still one of " +
                "the bar's pages for the joke scope and for Back",
            MainTab.TIERS,
            mainTabForRoute(AppRoutes.TIER_LIST)
        )
    }

    @Test
    fun `the tier tab is between the library and the profile`() {
        val tabs = enabledMainTabs(tierListTab = true)

        assertEquals(
            "the order is the enum's order, and the tier list was asked for " +
                "between Library and Profile",
            listOf(MainTab.HOME, MainTab.LIBRARY, MainTab.TIERS, MainTab.PROFILE),
            tabs
        )
    }

    @Test
    fun `only the profile tab carries the avatar, and it keeps a glyph without one`() {
        val avatar = "https://s4.anilist.co/file/avatar.png"

        mainTabDestinations(
            tabs = enabledMainTabs(tierListTab = true),
            profileAvatarUrl = avatar
        ).forEach { destination ->
            val isProfile = destination.testTag == "main_tab_profile"
            assertEquals(
                "the avatar is the Profile tab's identity; drawing it on any other " +
                    "tab would make two destinations look like the same place",
                if (isProfile) avatar else null,
                destination.iconUrl
            )
        }

        val withoutAvatar = mainTabDestinations(
            tabs = enabledMainTabs(tierListTab = true),
            profileAvatarUrl = null
        ).first { it.testTag == "main_tab_profile" }
        assertTrue(
            "the bar is drawn while the viewer's avatar is still loading, and a tab " +
                "with no icon and no image is a hole in the bar",
            withoutAvatar.iconUrl == null
        )
    }
}
