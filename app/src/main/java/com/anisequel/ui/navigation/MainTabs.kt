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
 * The three pages the app's main bar switches between.
 *
 * An enum rather than an index because the bar, the route mapping and the tap
 * handler are three places that have to agree about what "the second tab" is, and
 * an index agreed by convention is the kind of thing that drifts silently when one
 * of the three gains a destination.
 */
enum class MainTab {
    HOME,
    LIBRARY,
    PROFILE
}

/**
 * The destinations of [MainBar], derived from [MainTab.entries] so the bar's order
 * and the enum's order are one fact rather than two.
 *
 * [profileAvatarUrl] replaces the Profile tab's glyph with the signed-in viewer's
 * own avatar - the tab is "you", and the picture is the recognisable half of that.
 * While the viewer is still loading (or in the demo, which has no session) it is
 * null and the fallback glyph carries the tab, so the bar never renders a hole.
 */
internal fun mainTabDestinations(profileAvatarUrl: String?): List<NavigationDestination> =
    MainTab.entries.map { tab ->
        when (tab) {
            MainTab.HOME -> NavigationDestination(
                label = "Home",
                icon = AppVectorIcons.ProfileHome,
                testTag = "main_tab_home"
            )
            // The profile's own Library chip glyph, deliberately: it is the same
            // list, and a reader who learned the stopwatch in the profile should
            // find it again on the bar rather than learn a second icon for one
            // destination.
            MainTab.LIBRARY -> NavigationDestination(
                label = "Library",
                icon = AppVectorIcons.StatEpisodeCount,
                testTag = "main_tab_library"
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
 * pages - which is how the bar knows to hide.
 *
 * The routes that answer here are the app's top level: the dashboard (signed in or
 * demo), the library, and the viewer's own profile. Everything else - Settings, the
 * tier list, a studio page, a scanned dashboard, somebody else's profile - is a
 * screen pushed *from* one of these, and a bar drawn over them would claim the
 * reader is on a page they are not, with a selected tab they did not choose.
 *
 * Home has two routes because the demo runs without a session: the signed-in
 * dashboard is the real one, and the demo dashboard is the same page for a reader
 * who has not signed in. Both answer HOME, because to the person looking at them
 * they are the same page; which one a tap navigates to is decided by the caller,
 * from the session state rather than from this mapping.
 *
 * The profile answers with the route *pattern* it is registered under
 * (`profile?tab={tab}`), not with a literal - `NavDestination.route` reports the
 * pattern, and matching literals here would silently stop recognising the tab.
 */
internal fun mainTabForRoute(route: String?): MainTab? = when (route) {
    AppRoutes.DASHBOARD, AppRoutes.DASHBOARD_DEMO -> MainTab.HOME
    AppRoutes.LIBRARY -> MainTab.LIBRARY
    ProfileRoutes.OWN_ROUTE -> MainTab.PROFILE
    else -> null
}

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
 * The app's bottom bar: Home, Library, and the viewer's own profile.
 *
 * Drawn by the host over the `NavHost` rather than by any one screen, so the pages
 * it switches between do not each have to remember to draw it - and so a screen
 * added to the bar later cannot forget to. The tap is a route change; the bar itself
 * never navigates, it only says which tab was chosen.
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
    profileAvatarUrl: String?,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val destinations = remember(profileAvatarUrl) { mainTabDestinations(profileAvatarUrl) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveNavigationBar(
            destinations = destinations,
            selectedIndex = selected.ordinal,
            onSelect = { index -> onSelect(MainTab.entries[index]) }
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
