package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
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
import com.example.data.repository.ThemePreferences
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.DashboardViewModel

object AppRoutes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val DASHBOARD_DEMO = "dashboard_demo"
    const val DASHBOARD_USER = "dashboard_user/{username}"
    const val SETTINGS = "settings"

    fun userDashboard(username: String) = "dashboard_user/$username"
}

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

    val aniListRepository = remember(authRepository) {
        AniListRepositoryImpl(NetworkClient.createApiService(authRepository))
    }

    // The signed-in dashboard is scoped to the activity, not to its destination,
    // for two reasons: rotating the device used to throw it away and re-fetch the
    // user's entire list, and Settings needs the same viewer profile that the
    // dashboard already holds - it used to be passed through a plain `remember`,
    // which lost the profile on every configuration change.
    val mainDashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(
            aniListRepository = aniListRepository,
            hiddenSequelsPreferences = hiddenSequelsPreferences
        )
    )
    val mainDashboardState by mainDashboardViewModel.uiState.collectAsState()
    val signedInViewer = (mainDashboardState as? DashboardUiState.Success)?.viewer

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

    // Nothing is navigated until the stored session has actually been read.
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

    NavHost(
        navController = navController,
        startDestination = if (authState is AuthUiState.Authenticated) AppRoutes.DASHBOARD else AppRoutes.LOGIN,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
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
            DashboardScreen(
                dashboardViewModel = mainDashboardViewModel,
                onSignInAgain = authViewModel::logout,
                onOpenSettings = {
                    navController.navigate(AppRoutes.SETTINGS)
                }
            )
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
                    hiddenSequelsPreferences = hiddenSequelsPreferences
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
            SettingsScreen(
                authViewModel = authViewModel,
                themePreferences = themePreferences,
                viewer = signedInViewer,
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