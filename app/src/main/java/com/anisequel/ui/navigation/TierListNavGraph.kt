package com.anisequel.ui.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.TierListPreferences
import com.anisequel.ui.screens.tier.TierListScreen
import com.anisequel.ui.viewmodel.TierListViewModel

/**
 * The tier list destination: an interactive tier ranking board for the signed-in
 * viewer's anime library.
 */
fun NavGraphBuilder.tierListDestinations(
    navController: NavHostController,
    aniListRepository: AniListRepository,
    tierListPreferences: TierListPreferences,
    signedInViewer: ViewerProfile?,
    onBoardOpenChange: (Boolean) -> Unit
) {
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
            onBoardOpenChange = onBoardOpenChange,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
