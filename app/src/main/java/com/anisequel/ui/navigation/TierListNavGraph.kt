package com.anisequel.ui.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.TierListPreferences
import com.anisequel.ui.screens.library.LibraryLoadingView
import com.anisequel.ui.screens.profile.ProfileErrorView
import com.anisequel.ui.screens.tier.TierListScreen
import com.anisequel.ui.viewmodel.AuthUiState
import com.anisequel.ui.viewmodel.DashboardUiState
import com.anisequel.ui.viewmodel.DashboardViewModel
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
    /**
     * The session, which is known long before [signedInViewer] is.
     *
     * The two are different facts and only the second one says who the board is
     * for: the viewer id arrives with the dashboard's viewer query, while the bar
     * is tappable from the first frame of a cold start.
     */
    authenticated: AuthUiState.Authenticated?,
    /** The viewer query's failure, when the account query is what failed. */
    dashboardError: DashboardUiState.Error?,
    mainDashboardViewModel: DashboardViewModel?,
    onBoardOpenChange: (Boolean) -> Unit,
    onSignInAgain: () -> Unit
) {
    composable(AppRoutes.TIER_LIST) {
        // The same window `libraryDestinations` documents for itself, and the same
        // trap: while a session exists but the viewer query has not answered,
        // `signedInViewer` is null, which below reads as *demo mode*. Building the
        // ViewModel in that window ranks the fixture's titles, and the board writes
        // them straight to DataStore - so the reader's real list is populated with
        // fake shows by a screen that never told them it was the demo one.
        //
        // The bar is drawn over the dashboard's own loading state, so this route is
        // reachable in that window from the first frame of a cold start.
        if (signedInViewer == null && authenticated != null) {
            if (dashboardError != null) {
                ProfileErrorView(
                    message = dashboardError.message,
                    kind = dashboardError.kind,
                    canRetry = dashboardError.canRetry,
                    isAuthError = dashboardError.isAuthError,
                    onRetry = { mainDashboardViewModel?.loadData() },
                    onSignInAgain = onSignInAgain
                )
            } else {
                LibraryLoadingView(message = "Connecting to AniList to find your list\u2026")
            }
            return@composable
        }

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
                // The session, not the loaded viewer: the window where the two
                // disagree is the branch above, so by here this is the demo app
                // or a viewer who has arrived.
                isDemo = authenticated == null
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
