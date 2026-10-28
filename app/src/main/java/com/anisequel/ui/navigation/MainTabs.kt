package com.anisequel.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveNavigationBar
import com.anisequel.ui.components.expressive.NavigationDestination

/**
 * The pages the app's main bar switches between.
 *
 * An enum rather than an index because the bar, the route mapping and the tap
 * handler are three places that have to agree about what "the second tab" is, and
 * an index agreed by convention is the kind of thing that drifts silently when one
 * of the three gains a destination.
 *
 * The declaration order **is** the bar's order: [enabledMainTabs] filters this list
 * rather than building one, so the tier list sits between the Library and the
 * Profile by being declared there. The tier list is one of the bar's pages and not
 * a page pushed from them, so all three doors into it - the bar, the trophy in the
 * Library and the row in the profile - take the same route and leave Back in the
 * same place.
 */
enum class MainTab {
    HOME,
    LIBRARY,
    TIERS,
    PROFILE
}

/**
 * The tabs the bar actually offers, in declaration order.
 *
 * A filter over [MainTab.entries] rather than a second hand-written list: the one
 * thing that must never happen is the bar and `MainBar`'s index arithmetic
 * disagreeing about order, and deriving both from the same list is what makes that
 * impossible instead of merely unlikely.
 *
 * [tierListTab] is the reader's setting. It is passed in rather than read here so
 * this stays a pure function - it is the host that collects the preference, and a
 * preview or a test can ask for either bar without a DataStore.
 */
internal fun enabledMainTabs(tierListTab: Boolean): List<MainTab> =
    MainTab.entries.filter { tab -> tab != MainTab.TIERS || tierListTab }

/**
 * The destinations of [MainBar], derived from [tabs] so the bar's order and the
 * list `MainBar` indexes into are one fact rather than two.
 *
 * [profileAvatarUrl] replaces the Profile tab's glyph with the signed-in viewer's
 * own avatar - the tab is "you", and the picture is the recognisable half of that.
 * While the viewer is still loading (or in the demo, which has no session) it is
 * null and the fallback glyph carries the tab, so the bar never renders a hole.
 */
internal fun mainTabDestinations(
    tabs: List<MainTab>,
    profileAvatarUrl: String?
): List<NavigationDestination> = tabs.map { tab ->
    when (tab) {
        MainTab.HOME -> NavigationDestination(
            label = "Home",
            icon = AppVectorIcons.ProfileHome,
            testTag = "main_tab_home"
        )
        // A shelf of books, and it used to be the Stats tab's stopwatch.
        //
        // The old glyph was borrowed "because it is the same list", which was
        // true only when the Library *was* the profile's fourth tab. Once it
        // became a page of its own, the stopwatch read as time watched - and
        // it was the same glyph as the episode-count chart, so two different
        // destinations were drawn as one icon. This is the app's own vector,
        // per AGENTS.md §2, not a font glyph.
        MainTab.LIBRARY -> NavigationDestination(
            label = "Library",
            icon = AppVectorIcons.Library,
            testTag = "main_tab_library"
        )
        // The same trophy the Library's card and the profile's row use, because
        // it is the same page. A second glyph for the tab would make the two
        // look like two features.
        MainTab.TIERS -> NavigationDestination(
            label = "Tiers",
            icon = AppVectorIcons.Trophy,
            testTag = "main_tab_tiers"
        )
        MainTab.PROFILE -> NavigationDestination(
            label = "Profile",
            icon = AppVectorIcons.ProfilePerson,
            testTag = "main_tab_profile",
            iconUrl = profileAvatarUrl
        )
    }
}

/**
 * Which tab a route belongs to, or null when the route is not one of the bar's
 * pages - which is how the bar knows the reader is on a screen pushed *from* a
 * tab, and hides.
 *
 * The routes that answer here are the app's top level: the dashboard (signed in or
 * demo), the library, the tier list, and the viewer's own profile. Everything else
 * - Settings, a studio page, a scanned dashboard, somebody else's profile - is a
 * screen pushed from one of these, and a bar drawn over them would claim the
 * reader is on a page they are not, with a selected tab they did not choose.
 *
 * Home has two routes because the demo runs without a session: the signed-in
 * dashboard is the real one, and the demo dashboard is the same page for a reader
 * who has not signed in. Both answer HOME, because to the person looking at them
 * they are the same page; which one a tap navigates to is decided by the caller,
 * from the session state rather than from this mapping.
 *
 * The tier list answers TIERS even though the bar is not drawn there - see
 * [showsMainBar]. It is still one of the bar's pages, and the answer is what makes
 * a joke's "main pages" rule, a tab-change announcement and a Back out of the
 * board all agree with the tab the reader tapped to get there.
 *
 * The profile answers with the route *pattern* it is registered under
 * (`profile?tab={tab}`), not with a literal - `NavDestination.route` reports the
 * pattern, and matching literals here would silently stop recognising the tab.
 */
internal fun mainTabForRoute(route: String?): MainTab? = when (route) {
    AppRoutes.DASHBOARD, AppRoutes.DASHBOARD_DEMO -> MainTab.HOME
    AppRoutes.LIBRARY -> MainTab.LIBRARY
    AppRoutes.TIER_LIST -> MainTab.TIERS
    ProfileRoutes.OWN_ROUTE -> MainTab.PROFILE
    else -> null
}

/**
 * Whether [MainBar] is drawn over a page that belongs to [tab].
 *
 * The tier list is the one page of the bar that draws no bar. A board is dragged
 * with the finger and its tray is pinned over the bottom edge - that tray is the
 * page's own control, and a floating capsule underneath it would be a second set
 * of controls in the same strip of screen. So the bar steps aside while the list is
 * open and comes back when the reader leaves, which the host animates rather than
 * snaps.
 */
internal fun showsMainBar(tab: MainTab?): Boolean = tab != null && tab != MainTab.TIERS

/**
 * How much room a main page's scrolling content leaves for [MainBar], *below the
 * system navigation inset*.
 *
 * This is the bar's own height - 52dp of item, 5dp of padding either side, 12dp of
 * margin - and deliberately **not** the whole clearance. The system inset that
 * follows it is read from the device at draw time by [mainBarClearance], because it
 * is not a constant: a gesture bar, a three-button bar and a cutout are three
 * different heights, and hardcoding the largest of them either wastes space or
 * leaves the last row of a list sitting under the bar. That mismatch is also what
 * produces the "bounce back" snap when the list reaches its end and the padding
 * disagrees with where the bar really is.
 */
internal val MainBarClearance: Dp = 96.dp

/**
 * The bar, plus the device's own navigation inset beneath it.
 *
 * Composable because it reads a `WindowInsets`, which is only available during
 * composition. Read rather than assumed - see [MainBarClearance] for why a constant
 * is the wrong answer here.
 */
@Composable
internal fun mainBarClearance(): Dp {
    val systemInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return MainBarClearance + systemInset
}

/**
 * The app's bottom bar: Home, Library, the tier list and the viewer's own profile.
 *
 * Drawn by the host over the `NavHost` rather than by any one screen, so the pages
 * it switches between do not each have to remember to draw it - and so a screen
 * added to the bar later cannot forget to. The tap is a route change; the bar itself
 * never navigates, it only says which tab was chosen.
 *
 * [tabs] is the filtered list the bar was built from, and the index the bar reports
 * is an index into it - not into [MainTab.entries]. Those two only agree while the
 * tier tab is on, which is exactly the kind of off-by-one a hidden tab would
 * produce if this were computed from the enum's ordinal.
 *
 * The bar owns its own bottom inset instead of leaving it to the caller: it is the
 * one element in this app that is always pinned to the bottom edge, and a caller
 * that forgot the inset would draw it under a gesture bar.
 *
 * The selection morph - the growing label, the springing weight, the press scale -
 * is [ExpressiveNavigationBar]'s and is documented there.
 */
@Composable
internal fun MainBar(
    selected: MainTab,
    tabs: List<MainTab>,
    profileAvatarUrl: String?,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val destinations = remember(tabs, profileAvatarUrl) {
        mainTabDestinations(tabs, profileAvatarUrl)
    }
    // `coerceAtLeast(0)` rather than an error: the bar is not drawn on the tier
    // list, but it is still composed for a frame while it animates away from a
    // screen whose tab may no longer be in the list (the reader can turn the tier
    // tab off in Settings while a tier board is open). A bar with no selection for
    // one frame beats a crash.
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveNavigationBar(
            destinations = destinations,
            selectedIndex = selectedIndex,
            onSelect = { index -> tabs.getOrNull(index)?.let(onSelect) }
        )
    }
}

/**
 * A tap on the main bar: switch to [tab] instead of pushing it.
 *
 * The bar's pages are alternatives, not a stack: the previous page is dropped
 * (`popUpTo` Home, with its state saved) before the new one is pushed, so Back
 * from any tab lands on Home rather than walking back through every tab the
 * reader visited - and returning to a tab restores what it was showing
 * (`restoreState`) instead of rebuilding it. `launchSingleTop` is the same guard
 * every other hop in the app uses: a second tap on the tab already on screen
 * must not push a duplicate.
 *
 * The tier list is one of those alternatives now, and it takes this path from all
 * three of its doors - the bar, the Library's trophy and the profile's row. Before,
 * those two pushed it, so Back from a list opened in the Library went to the
 * Library while Back from one opened in the bar would have gone to Home: the same
 * page with two different Back behaviours depending on how the reader arrived.
 *
 * Home is two routes because the demo has no session: which one is "Home" is
 * decided here from [signedIn] rather than from the route the reader is on.
 *
 * The demo profile keeps the rule its avatar already had: a demo profile is not
 * on AniList, so it replaces the demo dashboard instead of stacking on it.
 */
internal fun NavHostController.navigateToMainTab(tab: MainTab, signedIn: Boolean) {
    if (mainTabForRoute(currentDestination?.route) == tab) return

    val homeRoute = if (signedIn) AppRoutes.DASHBOARD else AppRoutes.DASHBOARD_DEMO
    val route = when (tab) {
        MainTab.HOME -> homeRoute
        MainTab.LIBRARY -> AppRoutes.LIBRARY
        MainTab.TIERS -> AppRoutes.TIER_LIST
        MainTab.PROFILE -> ProfileRoutes.OWN_DEFAULT
    }

    if (tab == MainTab.PROFILE && !signedIn) {
        navigate(route) { popUpTo(AppRoutes.DASHBOARD_DEMO) { inclusive = true } }
        return
    }

    navigate(route) {
        popUpTo(homeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
