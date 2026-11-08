package com.anisequel.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.data.extensions.ExtensionManager
import com.anisequel.ui.screens.watch.PlayerScreen
import com.anisequel.ui.screens.watch.WatchScreen
import com.anisequel.ui.viewmodel.WatchViewModel

/**
 * The two Watch destinations.
 *
 * Neither is one of the bar's pages, so [mainTabForRoute] answers null and the
 * bar hides itself. The Watch page keeps a back arrow to the title sheet it came
 * from; the player pops back to the episode list, which is what the reader expects
 * after a file finishes.
 *
 * The player route is argument-free because the stream URL and the episode queue
 * live in `PlaybackQueue`; see that class for why percent-encoding them into a
 * route loses the queue the next-episode control needs.
 */
fun NavGraphBuilder.watchDestinations(
    navController: NavHostController,
    manager: ExtensionManager
) {
    composable(
        route = WatchRoutes.WATCH,
        arguments = WatchRoutes.watchArguments
    ) { backStackEntry ->
        val media = WatchRoutes.from(backStackEntry)
        val watchViewModel: WatchViewModel = viewModel(
            key = "watch_${media.id}",
            factory = WatchViewModel.Factory(manager, media)
        )
        val context = LocalContext.current
        WatchScreen(
            viewModel = watchViewModel,
            onBack = { navController.popBackStack() },
            onPlay = { navController.navigateSingle(WatchRoutes.PLAYER) },
            onOpenAniList = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        )
    }

    composable(WatchRoutes.PLAYER) {
        PlayerScreen(
            watchPreferences = manager.watchPreferences,
            playerPreferences = manager.playerPreferences,
            onBack = { navController.popBackStack() },
            onOpenSettings = {
                navController.navigateSingle(AppRoutes.PLAYER_SETTINGS)
            }
        )
    }
}
