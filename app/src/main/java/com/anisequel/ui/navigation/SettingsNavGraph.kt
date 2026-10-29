package com.anisequel.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.AppLockPreferences
import com.anisequel.data.repository.ArrivingPreferences
import com.anisequel.data.repository.JokePreferences
import com.anisequel.data.repository.MainTabPreferences
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.screens.settings.SettingsScreen
import com.anisequel.ui.viewmodel.AuthViewModel
import com.anisequel.ui.viewmodel.DashboardUiState

/**
 * The settings destination: account, app preferences, diagnostics, and release info.
 */
fun NavGraphBuilder.settingsDestinations(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    themePreferences: ThemePreferences,
    appLockPreferences: AppLockPreferences,
    refreshIntervalPreferences: RefreshIntervalPreferences,
    arrivingPreferences: ArrivingPreferences,
    quickAddPreferences: QuickAddPreferences,
    mainTabPreferences: MainTabPreferences,
    jokePreferences: JokePreferences,
    changelogRepository: ChangelogRepository,
    aniListRepository: AniListRepository,
    dashboardState: DashboardUiState?,
    signedInViewer: ViewerProfile?
) {
    composable(AppRoutes.SETTINGS) {
        val successState = dashboardState as? DashboardUiState.Success

        SettingsScreen(
            authViewModel = authViewModel,
            themePreferences = themePreferences,
            appLockPreferences = appLockPreferences,
            refreshIntervalPreferences = refreshIntervalPreferences,
            arrivingPreferences = arrivingPreferences,
            quickAddPreferences = quickAddPreferences,
            mainTabPreferences = mainTabPreferences,
            jokePreferences = jokePreferences,
            changelogRepository = changelogRepository,
            checkApiReachability = aniListRepository::checkApiReachability,
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
