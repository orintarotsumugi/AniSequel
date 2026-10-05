package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.data.network.NetworkClient
import com.example.data.repository.AniListRepositoryImpl
import com.example.data.repository.AuthRepository
import com.example.data.repository.HiddenSequelsPreferences
import com.example.data.repository.RefreshIntervalPreferences
import com.example.data.repository.ThemePreferences
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.flow.MutableStateFlow

object AppRoutes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val DASHBOARD_DEMO = "dashboard_demo"
    const val DASHBOARD_USER = "dashboard_user/{username}"
    const val SETTINGS = "settings"

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
private val RouteSlideSpring: FiniteAnimationSpec<IntOffset> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@Composable
fun AppNavigation(
    authRepository: AuthRepository,
    authViewModel: AuthViewModel,
    themePreferences: ThemePreferences,
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
                refreshIntervalPreferences = refreshIntervalPreferences
            )
        )
    } else {
        null
    }

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
                if (currentRoute == AppRoutes.DASHBOARD || currentRoute == AppRoutes.SETTINGS) {
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

    NavHost(
        navController = navController,
        startDestination = if (authState is AuthUiState.Authenticated) AppRoutes.DASHBOARD else AppRoutes.LOGIN,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = RouteSlideSpring
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = RouteSlideSpring
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = RouteSlideSpring
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = RouteSlideSpring
            )
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
                    navController.navigate(AppRoutes.userDashboard(username))
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
                    onSignInAgain = authViewModel::logout,
                    onOpenSettings = {
                        navController.navigate(AppRoutes.SETTINGS)
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
                onSignInAgain = {
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) }
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
                    refreshIntervalPreferences = refreshIntervalPreferences
                )
            )

            DashboardScreen(
                dashboardViewModel = userViewModel,
                onSignInAgain = {
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) }
            )
        }

        composable(AppRoutes.SETTINGS) {
            // Collected here rather than at the top of AppNavigation: the viewer
            // profile only exists once a dashboard has actually loaded, and
            // `collectAsState` needs a flow that is known to be non-null.
            // `collectAsState` must see a non-null receiver on every pass:
            // a null-propagating `?.` hides the call when
            // `mainDashboardViewModel` is absent, so a session arriving or
            // the theme rotating after Settings is open would re-enter this
            // lambda with a different call sequence and crash at runtime.
            // A stable empty flow keeps the call unconditional.
            val dashboardFlow = mainDashboardViewModel?.uiState ?: EmptyDashboardState
            val dashboardState by dashboardFlow.collectAsState()
            val successState = dashboardState as? DashboardUiState.Success
            val signedInViewer = successState?.viewer

            SettingsScreen(
                authViewModel = authViewModel,
                themePreferences = themePreferences,
                refreshIntervalPreferences = refreshIntervalPreferences,
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
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}