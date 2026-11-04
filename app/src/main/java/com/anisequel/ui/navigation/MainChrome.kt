package com.anisequel.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.anisequel.data.repository.JokePreferences
import com.anisequel.domain.jokes.JokeMoment
import com.anisequel.domain.jokes.JokePosition
import com.anisequel.domain.jokes.JokeScope
import com.anisequel.domain.jokes.JokeSettings
import com.anisequel.ui.components.JokeMomentBus
import com.anisequel.ui.components.JokeToastHost
import com.anisequel.ui.components.JokeToastLift
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.seasonal.SeasonalBarTrail
import com.anisequel.ui.components.seasonal.SeasonalChrome
import com.anisequel.ui.components.seasonal.SeasonalWind
import kotlinx.coroutines.launch

/**
 * The two pieces of chrome the app draws *over* the navigation graph: the main
 * bar and the joke toast.
 *
 * ## Why they are composables of their own
 *
 * Both used to be inlined in [AppNavigation]'s `Box`, in the same scope that
 * calls `NavHost`. Reading `currentBackStackEntryAsState()` there meant every
 * push, pop and tab switch invalidated the call site of the graph itself - the
 * `NavHost`'s arguments were re-evaluated and its destination lambdas rebuilt, to
 * move two overlays that are the only things on screen which actually changed.
 * Moving the read one scope down confines the invalidation to the host that uses
 * it: a route change now recomposes a bar and a toast, not the graph.
 *
 * The split is also a file boundary because these change for a different reason
 * than the destination table does. [AppNavigation] answers which routes exist and
 * what each one is handed; this file answers what floats above them.
 */

/**
 * The bar, plus the one rule that hides it.
 *
 * `showsMainBar` is the whole visibility rule - null means this is a screen
 * pushed from a tab (Settings, a studio, somebody else's profile), and an open
 * tier board owns the bottom edge with its own tray.
 *
 * Animated rather than conditional, because the tier list is a *tab*: a bar that
 * vanished on the tap and reappeared on Back would read as the app losing its
 * navigation and then finding it again. It leaves downwards, which is the
 * direction the tier list arrives from, so the two read as one move.
 *
 * The fast slide token, not the route one: the bar only has to clear its own
 * height, and it has to be gone before the tier board's tray finishes rising into
 * the same edge. On the calm spring the two overlap for most of the move and the
 * edge looks busy at the exact moment it should look empty.
 */
@Composable
internal fun BoxScope.MainBarHost(
    navController: NavHostController,
    tabs: List<MainTab>,
    profileAvatarUrl: String?,
    signedIn: Boolean,
    tierBoardOpen: Boolean,
    onTierBoardOpenChange: (Boolean) -> Unit
) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val currentTab = mainTabForRoute(currentRoute)

    // A board that is open when the route is left - a pop from somewhere the
    // screen's own dispose did not run first - must not hide the bar on whatever
    // page comes next. The screen also reports false when it goes, but the host
    // is the only place that knows the route changed.
    LaunchedEffect(currentRoute) {
        if (currentRoute != AppRoutes.TIER_LIST) onTierBoardOpenChange(false)
        // A page change is the UI moving too, not just a finger. A small
        // alternating gust keeps the sky from feeling like a screenshot with a
        // scroll hook.
        SeasonalWind.stir()
    }

    // The last tab the route actually named, kept for the frames the bar spends
    // leaving.
    //
    // `currentTab` goes null the moment the destination is not one of the bar's pages
    // - pushing Settings, or opening the board - and it is null for the whole exit
    // animation, so the highlight snapped to Home (a tab the reader was not on) and
    // slid away claiming it. Written after composition rather than during it, so the
    // exit frames read a settled value while a tab switch still draws from
    // `currentTab` itself on its first frame.
    val lastTab = remember { mutableStateOf(MainTab.HOME) }
    SideEffect { if (currentTab != null) lastTab.value = currentTab }

    // The seasonal sky gathers its drift on the screen's bottom edge and draws a
    // snow cap on the pill itself. The bar leaves the composition when it hides, so
    // its bounds have to be cleared explicitly; a stale capsule would leave the cap
    // hanging in the air over Settings. See SeasonalChrome for why the values are
    // draw-phase state rather than something that recomposes this host.
    val barVisible = showsMainBar(currentTab, tierBoardOpen)
    LaunchedEffect(barVisible) {
        if (!barVisible) SeasonalChrome.clearBar()
    }

    AnimatedVisibility(
        visible = barVisible,
        // The bar arrives as a surface, not a strip: a short slide plus a small
        // expansion. The expansion is what makes the pill feel like it grew out of the
        // bottom edge instead of being uncovered by it. Bouncy on the way in and plain
        // fast on the way out - a control that wobbles while it is leaving reads as a
        // dropped frame, not as a flourish.
        enter = slideInVertically(
            animationSpec = RouteTabSlideSpring,
            initialOffsetY = { height -> height }
        ) + scaleIn(
            initialScale = 0.90f,
            animationSpec = ExpressiveMotion.BouncySpatial
        ) + fadeIn(ExpressiveMotion.FastEffects),
        exit = slideOutVertically(
            animationSpec = RouteTabSlideSpring,
            targetOffsetY = { height -> height }
        ) + scaleOut(
            targetScale = 0.96f,
            animationSpec = ExpressiveMotion.FastSpatial
        ) + fadeOut(ExpressiveMotion.FastEffects),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        MainBar(
            // The last real tab, not Home, while the bar animates out - see [lastTab].
            // An open board still hides the bar; `MainBar` coerces an unknown selection
            // to the first tab for that one frame rather than crashing on an index of
            // -1.
            selected = currentTab ?: lastTab.value,
            tabs = tabs,
            // The largest the viewer's avatar has; the bar falls back to a glyph
            // while it is still unknown.
            profileAvatarUrl = profileAvatarUrl,
            onSelect = { tab -> navController.navigateToMainTab(tab, signedIn = signedIn) }
            // No hold shortcut. Holding Profile and lifting used to jump the reader to
            // the inbox, which is not what a hold on a tab means: the bar's held gesture
            // exists to *start a swipe*, and a destination nobody asked for arriving on a
            // plain press-and-release reads as the bar misfiring. The Messages segment
            // is one tap on Profile and one tap on the chip, and that is the whole route.
        )
    }

    // Over the bar, not under it: the cap is snow that has landed on the pill, so
    // it has to paint after the surface it sits on. It takes no pointer input - it
    // is a Canvas with only a draw block - so every tab under it still receives the
    // first tap. Gated on the same visibility rule as the bar itself.
    SeasonalBarTrail(
        visible = barVisible,
        modifier = Modifier.matchParentSize()
    )
}

/**
 * A joke, now and then, at the edge the reader chose.
 *
 * Mounted by the host for the same reason the bar is: only the host knows which
 * page is on screen, and a screen cannot draw a toast that outlives it.
 *
 * `active` is the page rule, not the switch: the host owns the switch, frequency
 * and timing. Main-pages means the pages the bar owns - the tier list included,
 * since it is one of them even while an open board hides the bar; Everywhere adds
 * the pages pushed from them. Login is excluded from both arms - a joke over the
 * sign-in screen is an interruption at the one moment the reader is deciding
 * something.
 */
@Composable
internal fun BoxScope.JokeHost(
    navController: NavHostController,
    preferences: JokePreferences,
    settings: JokeSettings,
    favourites: Set<String>
) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val currentTab = mainTabForRoute(currentRoute)

    val active = if (settings.scope == JokeScope.EVERYWHERE) {
        currentRoute != null && currentRoute != AppRoutes.LOGIN
    } else {
        currentTab != null
    }

    // The navigation graph is the only place that knows a tab changed, but the
    // host is the only place that knows whether the reader asked for a line at
    // that moment. So this announces and the host decides. Keyed on the tab and
    // skipping the first value: launching the app is not a tab change.
    var previousTab by remember { mutableStateOf<MainTab?>(null) }
    LaunchedEffect(currentTab) {
        val tab = currentTab
        if (tab != null && previousTab != null && previousTab != tab) {
            JokeMomentBus.announce(JokeMoment.TAB_CHANGE)
        }
        previousTab = tab
    }

    // The heart writes a DataStore preference, which is a suspend call. One scope
    // for the whole graph: the host outlives every screen, so a saved line must
    // not be cancelled when the page that showed it is popped.
    val scope = rememberCoroutineScope()
    val jokeAtTop = settings.position == JokePosition.TOP

    JokeToastHost(
        settings = settings,
        active = active,
        favouriteLines = favourites,
        onToggleFavourite = { line ->
            scope.launch { preferences.toggleFavourite(line) }
        },
        modifier = Modifier
            .align(if (jokeAtTop) Alignment.TopCenter else Alignment.BottomCenter)
            .then(
                if (jokeAtTop) {
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp)
                } else {
                    // Above the bar, and above the dashboard's snackbar rather than
                    // on top of it: the dashboard anchors that host at
                    // `MainBarClearance`, so a joke parked just clear of the bar
                    // would occasionally be drawn through an "Added to Planning"
                    // confirmation. The clearance already includes the device's
                    // navigation inset, and the extra lift is the toast's own
                    // constant so the two cannot drift.
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = mainBarClearance() + JokeToastLift)
                }
            )
    )
}
