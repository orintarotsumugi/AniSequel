package com.anisequel.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.data.network.NetworkClient
import com.anisequel.data.repository.AniListRepositoryImpl
import com.anisequel.data.repository.ArrivingPreferences
import com.anisequel.data.repository.AuthRepository
import com.anisequel.data.repository.HiddenSequelsPreferences
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.data.repository.TierListPreferences
import com.anisequel.ui.screens.dashboard.DashboardScreen
import com.anisequel.ui.screens.login.LoginScreen
import com.anisequel.ui.screens.settings.SettingsScreen
import com.anisequel.ui.screens.studio.StudioScreen
import com.anisequel.ui.screens.tier.TierListScreen
import com.anisequel.ui.viewmodel.AuthUiState
import com.anisequel.ui.viewmodel.AuthViewModel
import com.anisequel.ui.viewmodel.DashboardUiState
import com.anisequel.ui.viewmodel.DashboardViewModel
import com.anisequel.ui.viewmodel.StudioViewModel
import com.anisequel.ui.viewmodel.TierListViewModel
import kotlinx.coroutines.flow.MutableStateFlow

object AppRoutes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val DASHBOARD_DEMO = "dashboard_demo"
    const val DASHBOARD_USER = "dashboard_user/{username}"
    const val SETTINGS = "settings"
    const val TIER_LIST = "tier_list"

    fun userDashboard(username: String) = "dashboard_user/$username"
}

/** A state that never emits a real dashboard, for when there is no session VM. */
private val EmptyDashboardState = MutableStateFlow<DashboardUiState?>(null)

/**
 * Expressive spatial spring for the cross-route slide transitions.
 *
 * A purposeless fixed-duration tween used to fly the screens with no
 * overshoot; the same bouncy spring physics the rest of the app uses are the
 * motion routes should use too.
 */
private val RouteSlideSpring: FiniteAnimationSpec<IntOffset> get() =
    when (com.anisequel.ui.components.expressive.ExpressiveMotion.speed) {
        com.anisequel.data.repository.MotionStyle.INSTANT -> snap()
        com.anisequel.data.repository.MotionStyle.SMOOTH -> spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
        com.anisequel.data.repository.MotionStyle.CHILL -> spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }

/**
 * Effects-style fade that accompanies [RouteSlideSpring] on route transitions.
 * A pure slide crosses one surface over the other; the slide plus a short fade
 * gives the incoming surface something to compose against - without it, the
 * underneath content reads as the background rather than as replaced content
 * while the slide is moving.
 */
private val RouteFadeEffect: FiniteAnimationSpec<Float> get() =
    when (com.anisequel.ui.components.expressive.ExpressiveMotion.speed) {
        com.anisequel.data.repository.MotionStyle.INSTANT -> snap()
        com.anisequel.data.repository.MotionStyle.SMOOTH -> spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
        com.anisequel.data.repository.MotionStyle.CHILL -> spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }

@Composable
fun AppNavigation(
    authRepository: AuthRepository,
    authViewModel: AuthViewModel,
    themePreferences: ThemePreferences,
    /**
     * A tap on the home-screen widget asked for the profile's Activity tab.
     *
     * Passed in rather than read from the Activity's intent here so this stays a
     * pure function of its arguments - the widget's request arrives as an Intent
     * extra, which only `MainActivity` can see, and routing it through here is
     * what keeps the navigation graph from having to know about `Intent`s.
     */
    openProfileActivityOnLaunch: Boolean = false,
    /** Called once the request has been navigated to, so it is not replayed. */
    onProfileActivityOpened: () -> Unit = {},
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val authState by authViewModel.uiState.collectAsState()

    // Application-scoped: the hidden list is a property of the person, not of a
    // screen, and it has to survive the Settings screen and the detail sheet
    // being torn down. Without this the dashboards are built with a null store
    // and "Not interested" silently does nothing.
    val context = LocalContext.current
    val hiddenSequelsPreferences = remember(context) {
        HiddenSequelsPreferences(context.applicationContext)
    }

    // Application-scoped for the same reason: how often the list is re-fetched is
    // a property of the person, and the repository that caches the list outlives
    // any one screen. Two instances would mean two DataStore readers disagreeing
    // about the same preference.
    val refreshIntervalPreferences = remember(context) {
        RefreshIntervalPreferences(context.applicationContext)
    }

    // Application-scoped so the dashboard and Settings agree in one session:
    // the dashboard mirrors both flags and Settings writes them, and two
    // instances would read two copies of the same preference.
    val arrivingPreferences = remember(context) {
        ArrivingPreferences(context.applicationContext)
    }

    val quickAddPreferences = remember(context) {
        com.anisequel.data.repository.QuickAddPreferences(context.applicationContext)
    }

    // The Library tab's remembered media type. Application-scoped and built here
    // for the same reason as the rest: one DataStore reader per process, so the
    // tab cannot disagree with itself about what was last chosen.
    val libraryPreferences = remember(context) {
        com.anisequel.data.repository.LibraryPreferences(context.applicationContext)
    }

    // Application-scoped for the same reason as the rest: the tier list is the
    // reader's own saved work, and a tier list built one visit and thrown away on
    // the next is worse than no feature at all. `remember` keyed on `context` is
    // what keeps one DataStore reader per process, so a second destination cannot
    // disagree with this one about what is filed where.
    val tierListPreferences = remember(context) {
        TierListPreferences(context.applicationContext)
    }

    // The cache window is a supplier rather than a value because the user can
    // change it from Settings while the app is open: reading it per lookup is
    // what makes "15 min" mean fifteen minutes from the moment it is picked,
    // rather than from the moment this repository was built.
    val aniListRepository = remember(authRepository) {
        AniListRepositoryImpl(
            apiService = NetworkClient.createApiService(authRepository),
            listCacheTtlMillis = refreshIntervalPreferences::currentStalenessMillis
        )
    }

    // Application-scoped for the same reason: the notes are a property of the
    // build, and the release workflow reads the same file to write the release
    // body. Two readers of two copies is how an app claims a feature the
    // download page does not mention.
    val changelogRepository = remember(context) {
        ChangelogRepository(context.applicationContext)
    }

    // Mirrors the stored interval into the plain value the repository reads,
    // since a list-cache lookup cannot suspend. Started here rather than in the
    // preferences object because that would mean the class owning a CoroutineScope
    // it has no business having.
    LaunchedEffect(refreshIntervalPreferences) {
        refreshIntervalPreferences.observeInterval()
    }

    // Nothing is loaded until the stored session has actually been read.
    //
    // Picking a start destination from an unresolved state is what produced a
    // login screen on every cold start, and - because the OAuth round trip hands
    // the app off to the browser long enough for the process to be killed - a
    // login screen every time the user came *back* from signing in. It read as
    // the app forgetting they had asked to sign in, then signing them in anyway
    // a second later.
    //
    // A splash for the few tens of milliseconds this takes is the honest thing
    // to draw while the answer is genuinely unknown. Guessing is not.
    if (authState is AuthUiState.Restoring) {
        RestoringPlaceholder(modifier)
        return
    }

    // The signed-in dashboard is scoped to the activity, not to its destination,
    // for two reasons: rotating the device used to throw it away and re-fetch the
    // user's entire list, and Settings needs the same viewer profile that the
    // dashboard already holds - it used to be passed through a plain `remember`,
    // which lost the profile on every configuration change.
    //
    // Built only when there is a session, and keyed on the token. Both halves are
    // load-bearing:
    //
    // `DashboardViewModel` loads in its `init`, so constructing it while
    // unauthenticated issued the viewer query with no `Authorization` header at
    // all. AniList answers that 401 "Unauthorized.", the repository classified it
    // as a dead session, and because the ViewModel is activity-scoped the screen
    // kept that error for good - so signing in landed the user on "Session
    // expired. Please re-authenticate." having just been told their sign-in
    // succeeded. Nothing re-ran the `init`, so no retry could clear it. It read
    // as the app refusing the login it had just accepted.
    //
    // Keying on the token means signing out and back in produces a fresh
    // ViewModel whose `init` runs against the token actually in force, instead of
    // re-showing whatever the previous session left behind.
    val authenticated = authState as? AuthUiState.Authenticated

    val mainDashboardViewModel: DashboardViewModel? = if (authenticated != null) {
        viewModel(
            key = "dashboard_${authenticated.token}",
            factory = DashboardViewModel.Factory(
                aniListRepository = aniListRepository,
                hiddenSequelsPreferences = hiddenSequelsPreferences,
                refreshIntervalPreferences = refreshIntervalPreferences,
                arrivingPreferences = arrivingPreferences
            )
        )
    } else {
        null
    }

    // Collected once here, beside the session, so the profile routes have the
    // viewer's name and id without each of them re-reading the dashboard's
    // state. The id is the load-bearing half: without it the profile screen
    // looks its list up by name, misses the cache the dashboard just filled, and
    // spends AniList's most expensive request re-downloading a list it already
    // holds. See `ProfileDestination`.
    //
    // The empty flow is the same one Settings uses, for the same reason: a
    // null-propagating `?.` would hide the `collectAsState` call on the frames
    // where there is no session, and re-entering this lambda with a different
    // call sequence crashes at runtime.
    val dashboardFlow = mainDashboardViewModel?.uiState ?: EmptyDashboardState
    val dashboardState by dashboardFlow.collectAsState()
    val signedInViewer = (dashboardState as? DashboardUiState.Success)?.viewer

    // React to auth state changes to navigate automatically.
    LaunchedEffect(authState) {
        when (authState) {
            is AuthUiState.Authenticated -> {
                val currentRoute = navController.currentDestination?.route
                if (currentRoute != AppRoutes.DASHBOARD && currentRoute != AppRoutes.SETTINGS) {
                    navController.navigate(AppRoutes.DASHBOARD) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                    }
                }
            }
            is AuthUiState.Unauthenticated -> {
                val currentRoute = navController.currentDestination?.route
                // The tier list is named here for the same reason the other two
                // are. It reads one person's saved work and needs no session of
                // its own, so nothing would ever move the user off it - which is
                // precisely the failure: an AniList 401 that lands while the list
                // is open would drop the session and leave the ranking on screen
                // with no dashboard, no sign-in and no way forward but Back.
                if (currentRoute == AppRoutes.DASHBOARD ||
                    currentRoute == AppRoutes.SETTINGS ||
                    currentRoute == AppRoutes.TIER_LIST
                ) {
                    // `popUpTo(0)` is not a destination - it popped nothing and
                    // left the dashboard underneath the login screen, so signing
                    // out and back in replayed the old dashboard state.
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    // The widget's tap, honoured once the app knows who is signed in.
    //
    // Deferred rather than fired on arrival because a profile needs an identity:
    // the widget's `PendingIntent` can only start the Activity, and at that moment
    // the stored session may still be being restored - or may not exist, in which
    // case the request has to stay pending until a sign-in resolves it instead of
    // being spent on a login screen.
    LaunchedEffect(openProfileActivityOnLaunch, authState) {
        if (openProfileActivityOnLaunch && authState is AuthUiState.Authenticated) {
            navController.navigateSingle(ProfileRoutes.OWN_ACTIVITY)
            onProfileActivityOpened()
        }
    }

    // Keep home screen widgets in sync with the live dashboard counts.
    //
    // Keyed on the values the widgets actually render, not on the whole state
    // object. The state instance is replaced on every filter keystroke (the
    // search box triggers a recompute), so keying on it re-rendered three
    // widgets per character typed to write numbers that had not changed. The
    // viewer, the two counts and the top entries are the only inputs.
    //
    // The top arriving entry is a key of its own because the tiles now render
    // its cover and title, and its size can be unchanged while the entry at the
    // front of the list changes - which would otherwise leave the previous
    // poster on the tile.
    val widgetSuccess = dashboardState as? DashboardUiState.Success
    val widgetTopMissedId = widgetSuccess?.missedSequels?.firstOrNull()?.sequelId
    val widgetTopArrivingId = widgetSuccess?.arriving?.firstOrNull()?.mediaId
    // The avatar is the activity tile's artwork, so the tile repaints when it
    // changes even if nothing else about the viewer did.
    val widgetAvatarUrl = widgetSuccess?.viewer?.avatar?.large
        ?: widgetSuccess?.viewer?.avatar?.medium
    LaunchedEffect(
        widgetSuccess?.viewer?.id,
        widgetSuccess?.viewer?.name,
        widgetSuccess?.totalMissedCount,
        widgetSuccess?.arriving?.size,
        widgetTopMissedId,
        widgetTopArrivingId,
        widgetAvatarUrl
    ) {
        val success = widgetSuccess ?: return@LaunchedEffect
        val top = success.missedSequels.firstOrNull()
        val arriving = success.arriving.firstOrNull()
        com.anisequel.ui.widget.AniSequelWidgetData.updateData(
            context = context,
            missedCount = success.totalMissedCount,
            arrivingCount = success.arriving.size,
            username = success.viewer.name,
            topMissedTitle = top?.sequelTitle,
            topMissedParent = top?.parentTitle,
            topMissedCover = top?.sequelCoverUrl,
            topMissedColor = top?.coverColor,
            arrivingTitle = arriving?.title,
            arrivingCover = arriving?.coverUrl,
            arrivingColor = arriving?.coverColor,
            activityArt = widgetAvatarUrl
        )
        com.anisequel.ui.widget.AniSequelWidgetProvider.updateAll(context)
        com.anisequel.ui.widget.MissedSequelsWidgetProvider.updateAll(context)
        com.anisequel.ui.widget.ArrivingSequelsWidgetProvider.updateAll(context)
        com.anisequel.ui.widget.FeaturedSequelWidgetProvider.updateAll(context)
    }

    NavHost(
        navController = navController,
        startDestination = if (authState is AuthUiState.Authenticated) AppRoutes.DASHBOARD else AppRoutes.LOGIN,
        modifier = modifier,
        enterTransition = {
            (slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = RouteSlideSpring
            ) + fadeIn(RouteFadeEffect))
        },
        exitTransition = {
            (slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = RouteSlideSpring
            ) + fadeOut(RouteFadeEffect))
        },
        popEnterTransition = {
            (slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = RouteSlideSpring
            ) + fadeIn(RouteFadeEffect))
        },
        popExitTransition = {
            (slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = RouteSlideSpring
            ) + fadeOut(RouteFadeEffect))
        }
    ) {
        composable(AppRoutes.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                authState = authState,
                onStartDemo = {
                    navController.navigate(AppRoutes.DASHBOARD_DEMO)
                },
                onScanUsername = { username ->
                    navController.navigateSingle(AppRoutes.userDashboard(username))
                }
            )
        }

        composable(AppRoutes.DASHBOARD) {
            // Null until a session exists; the route is only reachable once one
            // does, and a brief hold beats rendering a dashboard that cannot
            // possibly have a token behind it.
            mainDashboardViewModel?.let { dashboardViewModel ->
                DashboardScreen(
                    dashboardViewModel = dashboardViewModel,
                    quickAddPreferences = quickAddPreferences,
                    // The cast and crew rows open a profile sheet in-app. Null on the
                    // demo route below, which is why this is passed rather than read
                    // off a ViewModel: the demo deliberately has none.
                    repository = aniListRepository,
                    onSignInAgain = authViewModel::logout,
                    onOpenSettings = {
                        navController.navigateSingle(AppRoutes.SETTINGS)
                    },
                    // Guarded on the viewer existing rather than on the route:
                    // the button only appears on a loaded dashboard, and an
                    // avatar that can be tapped before there is a name to open
                    // is a route that would have to look one up - which is the
                    // request this wiring exists to avoid.
                    onOpenProfile = { viewer ->
                        if (viewer != null) {
                            // Single-top: this is the button a thumb finds twice.
                            // Each repeated tap used to push another identical
                            // profile, so Back had to be pressed as many times as
                            // the button had been pressed before the dashboard
                            // came back.
                            navController.navigateSingle(ProfileRoutes.OWN_DEFAULT)
                        }
                    },
                    onOpenStudio = { studioId, studioName ->
                        navController.navigateSingle(StudioRoutes.studio(studioId, studioName))
                    }
                )
            }
        }

        composable(AppRoutes.DASHBOARD_DEMO) {
            val demoViewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModel.Factory(aniListRepository, isDemo = true)
            )

            DashboardScreen(
                dashboardViewModel = demoViewModel,
                quickAddPreferences = quickAddPreferences,
                onSignInAgain = {
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigateSingle(AppRoutes.SETTINGS) },
                onOpenStudio = { studioId, studioName ->
                    navController.navigateSingle(StudioRoutes.studio(studioId, studioName))
                },
                onOpenProfile = {
                    navController.navigate(ProfileRoutes.OWN_DEFAULT) {
                        // A demo profile is not on AniList, so the viewer can
                        // change what it says from there and come back to
                        // something that no longer matches. Replacing it rather
                        // than stacking means Back lands on the demo dashboard
                        // every time instead of on a stack of stale demos.
                        popUpTo(AppRoutes.DASHBOARD_DEMO) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = AppRoutes.DASHBOARD_USER,
            arguments = listOf(navArgument("username") { type = NavType.StringType })
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username").orEmpty()

            // Keyed on the username so switching profiles cannot reuse the
            // ViewModel that is still holding the previous user's list.
            val userViewModel: DashboardViewModel = viewModel(
                key = "dashboard_user_$username",
                factory = DashboardViewModel.Factory(
                    aniListRepository = aniListRepository,
                    targetUsername = username,
                    hiddenSequelsPreferences = hiddenSequelsPreferences,
                    refreshIntervalPreferences = refreshIntervalPreferences,
                    arrivingPreferences = arrivingPreferences
                )
            )

            DashboardScreen(
                dashboardViewModel = userViewModel,
                quickAddPreferences = quickAddPreferences,
                repository = aniListRepository,
                onSignInAgain = {
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigateSingle(AppRoutes.SETTINGS) },
                // The scanned person, not the viewer. Passing the viewer's name
                // here would have opened the user's own profile from a dashboard
                // that is showing somebody else's list - which looks exactly
                // right on the top bar and is completely wrong.
                onOpenProfile = {
                    if (username.isNotBlank()) {
                        navController.navigateSingle(ProfileRoutes.byUsername(username))
                    }
                },
                onOpenStudio = { studioId, studioName ->
                    navController.navigateSingle(StudioRoutes.studio(studioId, studioName))
                }
            )
        }

        // The base route and the widget's `profile?tab=activity` are one
        // destination: the query argument is optional, so every way in lands here
        // and only the widget names a tab.
        //
        // `OWN_DEFAULT` (`profile?tab=`) rather than `OWN` is what the callers use,
        // because a `composable()` route is matched *exactly* - `navigate("profile")`
        // matches no route at all and silently does nothing. See `ProfileRoutes.OWN_DEFAULT`.
        composable(
            route = ProfileRoutes.OWN_ROUTE,
            arguments = listOf(
                navArgument(ProfileRoutes.OWN_TAB_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            // Read off the route rather than from a shared flag, so the request
            // travels with the destination. A flag would outlive the visit and
            // re-open the Activity tab on the *next* profile the user opened,
            // from wherever they opened it.
            val requestedTab = backStackEntry.arguments?.getString(ProfileRoutes.OWN_TAB_ARG)

            // `signedInViewer` is null until the dashboard has loaded, and a demo
            // dashboard has no session ViewModel at all - hence the two sources
            // rather than one. The button that reaches this route is on a loaded
            // dashboard, so in practice this is never an empty string.
            val username = signedInViewer?.name ?: DEMO_USERNAME
            ProfileDestination(
                repository = aniListRepository,
                username = username,
                userId = signedInViewer?.id,
                isDemo = signedInViewer == null,
                navController = navController,
                onSignInAgain = authViewModel::logout,
                // This is the viewer's own profile, so the tier list - a ranking
                // of their own saved work - belongs on its bar. The scanned and
                // followed routes leave this defaulted off.
                isOwnProfile = true,
                libraryPreferences = libraryPreferences,
                initialTab = if (requestedTab == ProfileRoutes.TAB_ACTIVITY) {
                    ProfileRoutes.ACTIVITY_TAB_INDEX
                } else {
                    0
                }
            )
        }

        composable(
            route = ProfileRoutes.BY_USERNAME,
            arguments = ProfileUsernameArguments
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username").orEmpty()
            ProfileDestination(
                repository = aniListRepository,
                username = username,
                // No id for a scanned profile: `getUserByName` has already been
                // asked for it by the dashboard that led here, but its id is not
                // carried in the route. `null` puts the list fetch on the by-name
                // cache key, which is the same key that dashboard used - so it is
                // still a cache hit rather than a second download.
                userId = null,
                isDemo = false,
                navController = navController,
                onSignInAgain = authViewModel::logout,
                libraryPreferences = libraryPreferences
            )
        }

        composable(
            route = ProfileRoutes.BY_ID,
            arguments = ProfileIdArguments
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            ProfileDestination(
                repository = aniListRepository,
                // The name is only a label at this point; the query is by id. The
                // follower row carries both, so an empty name would be a bug in
                // the caller rather than a real case, and it still fetches.
                username = "",
                userId = userId,
                isDemo = false,
                navController = navController,
                onSignInAgain = authViewModel::logout,
                libraryPreferences = libraryPreferences
            )
        }

        composable(AppRoutes.TIER_LIST) {
            // Built at the destination rather than hoisted beside the dashboard's,
            // because unlike the dashboard it is not shared between two routes and
            // the whole cost of holding it is one person's list: scoping it here
            // means leaving the screen discards nothing, because everything worth
            // keeping was already written to DataStore by the ViewModel.
            val tierListViewModel: TierListViewModel = viewModel(
                factory = TierListViewModel.Factory(
                    preferences = tierListPreferences,
                    // The tier list is a ranking of the *signed-in viewer's* own
                    // library, so it needs their id and their session. Opening
                    // somebody else's profile must never rank their titles into
                    // the viewer's list.
                    aniListRepository = aniListRepository,
                    userId = signedInViewer?.id,
                    isDemo = signedInViewer == null
                )
            )

            // No Scaffold or TopAppBar here. Every other destination hands its
            // screen a back lambda and lets the screen own its bar - Settings does
            // it this way too - and a bar added around the screen instead would be
            // a second one stacked on top of the tier list's own header.
            TierListScreen(
                tierListViewModel = tierListViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // The studio page. Registered last because it is the youngest destination
        // here, not because order means anything: `NavHost` matches on the route
        // pattern.
        composable(
            route = StudioRoutes.ROUTE,
            arguments = StudioArguments
        ) { backStackEntry ->
            val studioId = backStackEntry.arguments?.getInt(StudioRoutes.STUDIO_ID_ARG) ?: 0
            val studioName = backStackEntry.arguments?.getString(StudioRoutes.NAME_ARG)

            // Keyed on the studio so walking from one studio page to another cannot
            // reuse the ViewModel still holding the first one's filmography - the
            // same reason the scanned dashboards are keyed on their username.
            val studioViewModel: StudioViewModel = viewModel(
                key = "studio_$studioId",
                factory = StudioViewModel.Factory(
                    aniListRepository = aniListRepository,
                    studioId = studioId
                )
            )

            StudioScreen(
                viewModel = studioViewModel,
                studioId = studioId,
                repository = aniListRepository,
                initialName = studioName,
                onNavigateBack = { navController.popBackStack() },
                // Single-top: a studio page credits studios, so a reader can go
                // down a chain of them, and the page they are already on must not
                // be pushed again behind their own tap.
                onOpenStudio = { id, name ->
                    navController.navigateSingle(StudioRoutes.studio(id, name))
                }
            )
        }

        composable(AppRoutes.SETTINGS) {
            val successState = dashboardState as? DashboardUiState.Success

            SettingsScreen(
                authViewModel = authViewModel,
                themePreferences = themePreferences,
                refreshIntervalPreferences = refreshIntervalPreferences,
                arrivingPreferences = arrivingPreferences,
                quickAddPreferences = quickAddPreferences,
                changelogRepository = changelogRepository,
                viewer = signedInViewer,
                totalWatchedCount = successState?.totalWatchedCount,
                totalMissedCount = successState?.totalMissedCount,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
    }
}

/**
 * The demo profile's name.
 *
 * Deliberately the same name as [com.anisequel.data.repository.DemoDataProvider]
 * uses for its viewer, so the profile screen's own route and its fixture agree
 * about who "this" is.
 */
private const val DEMO_USERNAME = "OtakuExplorer"

/**
 * The brief hold while the stored session is read.
 *
 * Uses the app's own background colour rather than nothing, so the window does
 * not flash a lighter default before the themed content arrives - which on a
 * dark theme is its own brief flicker, and the very thing this was added to
 * stop.
 */
@Composable
private fun RestoringPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveLoadingIndicator()
    }
}