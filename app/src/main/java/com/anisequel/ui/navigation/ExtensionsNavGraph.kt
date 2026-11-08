package com.anisequel.ui.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.data.extensions.ExtensionManager
import com.anisequel.ui.screens.extensions.ExtensionManagerScreen
import com.anisequel.ui.screens.extensions.ExtensionsScreen
import com.anisequel.ui.screens.extensions.PlayerSettingsScreen
import com.anisequel.ui.viewmodel.ExtensionsViewModel

/**
 * The Extensions tab and the two pages pushed from its wrench.
 *
 * The catalogue ViewModel is keyed to the route, not to each entry, so leaving
 * the tab for the manager and coming back does not re-read every repository. The
 * manager and settings pages take [manager] directly: they are status pages over
 * one process-wide owner, and a copied snapshot would let the page describe an
 * install the catalogue no longer has.
 */
fun NavGraphBuilder.extensionsDestinations(
    navController: NavHostController,
    manager: ExtensionManager
) {
    composable(AppRoutes.EXTENSIONS) {
        val extensionsViewModel: ExtensionsViewModel = viewModel(
            key = "extensions_catalog",
            factory = ExtensionsViewModel.Factory(manager)
        )
        ExtensionsScreen(
            viewModel = extensionsViewModel,
            onOpenManager = { navController.navigateSingle(AppRoutes.EXTENSION_MANAGER) }
        )
    }

    composable(AppRoutes.EXTENSION_MANAGER) {
        ExtensionManagerScreen(
            manager = manager,
            onBack = { navController.popBackStack() },
            onOpenPlayerSettings = { navController.navigateSingle(AppRoutes.PLAYER_SETTINGS) },
            onOpenExtensions = {
                navController.navigateToMainTab(MainTab.EXTENSIONS, signedIn = true)
            }
        )
    }

    composable(AppRoutes.PLAYER_SETTINGS) {
        PlayerSettingsScreen(
            playerPreferences = manager.playerPreferences,
            onBack = { navController.popBackStack() }
        )
    }
}
