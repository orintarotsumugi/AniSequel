package com.anisequel.ui.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.components.detail.RepositoryEntryEditor
import com.anisequel.ui.screens.studio.StudioScreen
import com.anisequel.ui.viewmodel.MediaDetailHost
import com.anisequel.ui.viewmodel.StudioViewModel

/**
 * Destinations for animation studios:
 *
 *  - [StudioRoutes.ROUTE]: the studio page showing filmography categorized by year.
 */
fun NavGraphBuilder.studioDestinations(
    navController: NavHostController,
    aniListRepository: AniListRepository,
    detailHost: MediaDetailHost,
    sharedEntryEditor: RepositoryEntryEditor?
) {
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
            detailHost = detailHost,
            initialName = studioName,
            onNavigateBack = { navController.popBackStack() },
            // Single-top: a studio page credits studios, so a reader can go
            // down a chain of them, and the page they are already on must not
            // be pushed again behind their own tap.
            onOpenStudio = { id, name ->
                navController.navigateSingle(StudioRoutes.studio(id, name))
            },
            entryEditor = sharedEntryEditor
        )
    }
}
