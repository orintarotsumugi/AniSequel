package com.anisequel.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.AppLockPreferences
import com.anisequel.data.repository.ArrivingPreferences
import com.anisequel.data.repository.JokePreferences
import com.anisequel.data.repository.LibraryPreferences
import com.anisequel.data.repository.MainTabPreferences
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.data.repository.DevicePreferences
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.SeasonalPreferences
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.screens.settings.SettingsScreen
import com.anisequel.ui.viewmodel.AuthViewModel

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
    /**
     * The Library page's remembered view, so Settings can offer to forget it.
     * The same instance the Library writes through - one DataStore reader.
     */
    libraryPreferences: LibraryPreferences,
    mainTabPreferences: MainTabPreferences,
    jokePreferences: JokePreferences,
    changelogRepository: ChangelogRepository,
    seasonalPreferences: SeasonalPreferences,
    devicePreferences: DevicePreferences,
    /**
     * The card style the Interface page writes and the rest of the app reads.
     * The same instance the app root collects, so the picker cannot describe a style
     * the cards are not drawing.
     */
    interfacePreferences: InterfacePreferences,
    aniListRepository: AniListRepository,
    /**
     * The two dashboard counts the Settings header shows.
     *
     * Nullable because the dashboard may still be loading or may have failed.
     * Passed as values rather than as the whole dashboard state so this graph
     * does not subscribe the app root to every recompute.
     */
    totalWatchedCount: Int?,
    totalMissedCount: Int?,
    /**
     * Three of the viewer's own entries, for the Interface page's card preview.
     *
     * Values rather than the dashboard state, for the same reason as the two
     * counts above: this graph should not be subscribed to every recompute just
     * to find out that the same three covers are still there.
     */
    cardPreview: List<MediaListEntryItem>,
    signedInViewer: ViewerProfile?
) {
    composable(AppRoutes.SETTINGS) {
        SettingsScreen(
            authViewModel = authViewModel,
            themePreferences = themePreferences,
            appLockPreferences = appLockPreferences,
            refreshIntervalPreferences = refreshIntervalPreferences,
            arrivingPreferences = arrivingPreferences,
            quickAddPreferences = quickAddPreferences,
            libraryPreferences = libraryPreferences,
            mainTabPreferences = mainTabPreferences,
            jokePreferences = jokePreferences,
            changelogRepository = changelogRepository,
            seasonalPreferences = seasonalPreferences,
            devicePreferences = devicePreferences,
            interfacePreferences = interfacePreferences,
            checkApiReachability = aniListRepository::checkApiReachability,
            viewer = signedInViewer,
            totalWatchedCount = totalWatchedCount,
            totalMissedCount = totalMissedCount,
            cardPreviewEntries = cardPreview,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToLogin = {
                navController.navigate(AppRoutes.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        )
    }
}
