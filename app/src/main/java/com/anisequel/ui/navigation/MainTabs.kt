package com.anisequel.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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
import com.anisequel.ui.theme.LocalMainBarLift

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

    /**
     * The catalogue search.
     *
     * Declared between the Library and the tier list because that is where it is
     * useful: the two list-shaped pages either side of it are where a reader
     * notices a title is missing. It is off by default, and unlike the tier list
     * there is no second door into it - see `MainTabPreferences.searchTab`.
     */
    SEARCH,
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
internal fun enabledMainTabs(
    tierListTab: Boolean,
    searchTab: Boolean = false,
    /**
     * Whether either namespace is searchable.
     *
     * The tab switch and the two type switches are two separate answers, and the
     * bar needs both: a reader can leave the Search tab on while turning manga
     * off and still want the anime page, but when both halves are off there is
     * no question the page can answer at all. Hiding it then is what makes the
     * two switches mean something rather than leaving a tab that opens onto a
     * sentence saying it has nothing to show.
     */
    searchAnimeEnabled: Boolean = true,
    searchMangaEnabled: Boolean = true
): List<MainTab> = MainTab.entries.filter { tab ->
    when (tab) {
        MainTab.TIERS -> tierListTab
        MainTab.SEARCH -> searchTab && (searchAnimeEnabled || searchMangaEnabled)
        else -> true
    }
}

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
        // The app's own house, not the profile's borrowed one. See
        // [AppVectorIcons.HomeTab]: Home and the profile's Home tab are two
        // destinations, and one drawing for both read as a shortcut.
        MainTab.HOME -> NavigationDestination(
            label = "Home",
            icon = AppVectorIcons.HomeTab,
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
        // The magnifier, and nothing else in the bar uses one: the Library's
        // search is a field *inside* that page, so a bar icon that also meant
        // "search" had to mean the catalogue rather than the list. That
        // distinction is the whole reason this is a page of its own.
        MainTab.SEARCH -> NavigationDestination(
            label = "Search",
            icon = AppVectorIcons.Search,
            testTag = "main_tab_search"
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
 * The tier list answers TIERS even though it can hide the bar - see
 * [showsMainBar]. It is still one of the bar's pages, and the answer is what makes
 * a joke's "main pages" rule, a tab-change announcement and a Back out of the
 * board all agree with the tab the reader tapped to get there.
 *
 * The profile answers with the route *pattern* it is registered under
 * (`profile?tab={tab}&segment={segment}`), not with a literal - `NavDestination.route`
 * reports the pattern, and matching literals here would silently stop recognising
 * the tab. That is also why a held Profile tab, which navigates with the segment
 * named, still answers PROFILE.
 */
internal fun mainTabForRoute(route: String?): MainTab? = when (route) {
    AppRoutes.DASHBOARD, AppRoutes.DASHBOARD_DEMO -> MainTab.HOME
    AppRoutes.LIBRARY -> MainTab.LIBRARY
    AppRoutes.SEARCH -> MainTab.SEARCH
    AppRoutes.TIER_LIST -> MainTab.TIERS
    ProfileRoutes.OWN_ROUTE -> MainTab.PROFILE
    else -> null
}

/**
 * Whether [MainBar] is drawn over a page that belongs to [tab].
 *
 * Every one of the bar's pages draws it, with one exception that is a state rather
 * than a route: an **open** tier board.
 *
 * The tier list's library is one of the bar's pages and keeps the bar - a reader
 * choosing a list, or leaving for another tab, is doing exactly what the bar is for.
 * A board is different: it is dragged with the finger and its tray is pinned over
 * the bottom edge. That tray is the page's own control, and a floating capsule
 * underneath it would be a second set of controls in the same strip of screen. So
 * the bar steps aside only while [tierBoardOpen], and comes back when the reader
 * closes the board - which the host animates rather than snaps.
 *
 * This used to be `tab != MainTab.TIERS` with no second argument, which hid the bar
 * over the library as well. That left the tier list's list of lists as the only page
 * in the app you could not leave by the bar, which read as the app having lost its
 * navigation rather than as the board owning the screen.
 */
internal fun showsMainBar(tab: MainTab?, tierBoardOpen: Boolean): Boolean =
    tab != null && !(tab == MainTab.TIERS && tierBoardOpen)

/**
 * How much room a main page's scrolling content leaves for [MainBar], *below the
 * system navigation inset*.
 *
 * This is the bar's own height, and the arithmetic is the bar's own: 52dp of item,
 * 5dp of padding inside the pill, 12dp of outer padding - 52 + 2x5 + 2x12 = 86. It
 * used to say 96 with a comment that summed to 74, so every main list carried a
 * ten-dp strip of nothing under the bar and the last row could not be scrolled flush
 * with it. The 8dp shadow is deliberately not counted: it is drawn over content
 * rather than occupying height, which is what an elevated surface is.
 *
 * **Not** the whole clearance. The system inset that follows it is read from the
 * device at draw time by [mainBarClearance], because it is not a constant: a gesture
 * bar, a three-button bar and a cutout are three different heights, and hardcoding
 * the largest of them either wastes space or leaves the last row of a list sitting
 * under the bar. That mismatch is also what produces the "bounce back" snap when the
 * list reaches its end and the padding disagrees with where the bar really is.
 */
internal val MainBarClearance: Dp = 86.dp

/**
 * The bar, plus the device's own navigation inset beneath it, plus the reader's
 * lift.
 *
 * Composable because it reads a `WindowInsets`, which is only available during
 * composition. Read rather than assumed - see [MainBarClearance] for why a constant
 * is the wrong answer here.
 *
 * The lift is read from the same composition local the bar reads, not passed in:
 * a list that asked the preference itself would keep its old padding for a frame
 * after the slider moved, and the bar would arrive on top of its last row. A lift
 * of zero is the shipped clearance exactly.
 */
@Composable
internal fun mainBarClearance(): Dp {
    val systemInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return MainBarClearance + systemInset + LocalMainBarLift.current.dp
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
 * that forgot the inset would draw it under a gesture bar. The reader can also
 * lift it above that inset - [LocalMainBarLift] - and the padding here is the one
 * place that moves; the list clearance in [mainBarClearance] follows the same
 * local so content is never stranded under the raised bar.
 *
 * The selection morph - the growing label, the springing weight, the press scale -
 * is [ExpressiveNavigationBar]'s and is documented there.
 *
 * ## Holding the bar, and what a hold is for
 *
 * The bar's held gesture exists to *start a swipe*: lift, then shove left or right
 * and the preview drags across the bar with the finger. It is not a shortcut and it
 * goes nowhere on its own. Holding Profile and releasing used to open the Messages
 * segment, which is the bar navigating on a press nobody completed - a hold belongs
 * to the gesture that follows it, and a destination arriving on a plain
 * press-and-release reads as a misfire. The segment is one tap on Profile and one
 * tap on the chip.
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
    // `indexOf` without `coerceAtLeast(0)`: a page can be on screen without being
    // one of the bar's tabs. The top-bar shortcut opens Search or Tiers even when
    // the matching tab is switched off, and coercing the missing index to zero
    // drew Home as the selected tab on a page that is not Home. -1 means exactly
    // "this page is not on the bar", and the bar draws no selection for it. The
    // bar still navigates and still supports the hold-and-swipe: from -1 a drag
    // towards the later tabs steps into the first one, which is the nearest
    // destination in that direction.
    val selectedIndex = tabs.indexOf(selected)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            // After the inset, not before: the lift is daylight *above* the
            // device's gesture area. Adding it inside the inset instead would
            // shorten the bar's distance from the screen edge only on devices
            // with no gesture bar.
            .padding(bottom = LocalMainBarLift.current.dp),
        contentAlignment = Alignment.Center
    ) {
        // One bar for both looks. The new look used to redraw this as its own
        // simpler capsule, which quietly cost the hold-and-swipe gesture and made
        // "the same tab" behave differently depending on a setting. The layout
        // switch changes pages, not navigation.
        ExpressiveNavigationBar(
            destinations = destinations,
            selectedIndex = selectedIndex,
            onSelect = { index -> tabs.getOrNull(index)?.let(onSelect) }
        )
    }
}

/**
 * The route a tab opens, and the page it keeps underneath itself.
 *
 * Split out of [navigateToMainTab] so the pair is a pure fact a JVM test can
 * pin: the bar's whole Back behaviour rests on the second value, and the demo's
 * used to be missing.
 */
internal data class MainTabTarget(val route: String, val homeRoute: String)

/**
 * Where [tab] goes, given whether there is a session.
 *
 * Home is two routes because the demo has no session: which one is "Home" is
 * decided from [signedIn] rather than from the route the reader is on.
 */
internal fun mainTabTarget(tab: MainTab, signedIn: Boolean): MainTabTarget {
    val homeRoute = if (signedIn) AppRoutes.DASHBOARD else AppRoutes.DASHBOARD_DEMO
    val route = when (tab) {
        MainTab.HOME -> homeRoute
        MainTab.LIBRARY -> AppRoutes.LIBRARY
        MainTab.SEARCH -> AppRoutes.SEARCH
        MainTab.TIERS -> AppRoutes.TIER_LIST
        MainTab.PROFILE -> ProfileRoutes.OWN_DEFAULT
    }
    return MainTabTarget(route = route, homeRoute = homeRoute)
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
 * ## The demo is not a second navigation model
 *
 * Home is the demo dashboard and every other page is reached from it exactly as
 * it is when signed in. The profile used to be special-cased to *replace* the
 * demo dashboard, on the theory that a demo profile is not on AniList and should
 * not stack on it. What that actually did was pop Home out of the back stack:
 * the profile's own back arrow then had nothing to return to, and a Home/Profile
 * round trip pushed a duplicate profile each time, so Back walked through copies
 * of the same page. A tab is a tab in both sessions.
 */
internal fun NavHostController.navigateToMainTab(tab: MainTab, signedIn: Boolean) {
    if (mainTabForRoute(currentDestination?.route) == tab) return

    val target = mainTabTarget(tab, signedIn)
    navigate(target.route) {
        popUpTo(target.homeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
