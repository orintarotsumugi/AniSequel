package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.ViewerProfile
import com.example.data.network.NetworkClient
import com.example.data.repository.AniListRepositoryImpl
import com.example.data.repository.AuthRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
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
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val authState by authViewModel.uiState.collectAsState()
    var currentViewerProfile by remember { mutableStateOf<ViewerProfile?>(null) }

    // React to auth state changes to navigate automatically
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
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
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
            val apiService = remember(authRepository) {
                NetworkClient.createApiService(authRepository)
            }
            val aniListRepository = remember(apiService) {
                AniListRepositoryImpl(apiService)
            }
            val dashboardViewModel = remember(aniListRepository) {
                DashboardViewModel(aniListRepository = aniListRepository)
            }

            DashboardScreen(
                dashboardViewModel = dashboardViewModel,
                onOpenSettings = { profile ->
                    currentViewerProfile = profile
                    navController.navigate(AppRoutes.SETTINGS)
                }
            )
        }

        composable(AppRoutes.DASHBOARD_DEMO) {
            val apiService = remember(authRepository) {
                NetworkClient.createApiService(authRepository)
            }
            val aniListRepository = remember(apiService) {
                AniListRepositoryImpl(apiService)
            }
            val demoViewModel = remember(aniListRepository) {
                DashboardViewModel(aniListRepository = aniListRepository, isDemo = true)
            }

            DashboardScreen(
                dashboardViewModel = demoViewModel,
                onOpenSettings = { profile ->
                    currentViewerProfile = profile
                    navController.navigate(AppRoutes.SETTINGS)
                }
            )
        }

        composable(
            route = AppRoutes.DASHBOARD_USER,
            arguments = listOf(navArgument("username") { type = NavType.StringType })
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val apiService = remember(authRepository) {
                NetworkClient.createApiService(authRepository)
            }
            val aniListRepository = remember(apiService) {
                AniListRepositoryImpl(apiService)
            }
            val userViewModel = remember(aniListRepository, username) {
                DashboardViewModel(aniListRepository = aniListRepository, targetUsername = username)
            }

            DashboardScreen(
                dashboardViewModel = userViewModel,
                onOpenSettings = { profile ->
                    currentViewerProfile = profile
                    navController.navigate(AppRoutes.SETTINGS)
                }
            )
        }

        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                authViewModel = authViewModel,
                viewer = currentViewerProfile,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
