package com.anisequel.ui.navigation

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.components.detail.RepositoryEntryEditor
import com.anisequel.ui.screens.library.LibraryLoadingView
import com.anisequel.ui.screens.library.LibraryScreen
import com.anisequel.ui.screens.profile.ProfileErrorView
import com.anisequel.ui.screens.profile.RememberedLibraryView
import com.anisequel.ui.viewmodel.AuthUiState
import com.anisequel.ui.viewmodel.DashboardUiState
import com.anisequel.ui.viewmodel.DashboardViewModel
import com.anisequel.ui.viewmodel.MediaDetailHost
import com.anisequel.ui.viewmodel.UserOverviewViewModel

/**
 * The Library destination: the reader's full anime collection with filter, sort,
 * and search controls.
 */
fun NavGraphBuilder.libraryDestinations(
    navController: NavHostController,
    aniListRepository: AniListRepository,
    detailHost: MediaDetailHost,
    sharedEntryEditor: RepositoryEntryEditor?,
    mainDashboardViewModel: DashboardViewModel?,
    dashboardState: DashboardUiState?,
    signedInViewer: ViewerProfile?,
    authenticated: AuthUiState.Authenticated?,
    librarySelection: RememberedLibraryView,
    libraryGridState: LazyGridState,
    onSignInAgain: () -> Unit
) {
    composable(AppRoutes.LIBRARY) {
        // Whose library this is, which the page cannot work out for itself: a list
        // belongs to one account, and which account is the reader's is only known
        // once the dashboard's viewer query has answered - the same query the bar
        // is drawn over from the first frame of a cold start.
        //
        // So while a session exists and that answer has not arrived, the route
        // draws the dashboard's own waiting and failure states and builds no
        // ViewModel. The alternative is the demo fallback the profile route uses,
        // and it is wrong here in a way it is not there: the profile route is only
        // reachable once the dashboard has loaded enough to draw the avatar that
        // opens it, while the bar is drawn over the dashboard's own loading state.
        // A tap in that window would build the ViewModel below around the demo
        // account and then - because its key is the page rather than the person,
        // which is what lets it outlive a tab switch - keep serving that stranger's
        // library on this page for the rest of the session.
        val viewerState = dashboardState
        if (signedInViewer == null && authenticated != null) {
            if (viewerState is DashboardUiState.Error) {
                // The account query is what failed, so its answer is this page's
                // answer: retrying it here is a real retry, and a dead session
                // offers the same way back rather than a button that cannot work.
                //
                // The calm error view, not the dashboard's failure theatre. This
                // is one of the bar's pages - the reader tapped Library to see
                // their list, not to watch a heart break - and the probe, status
                // strip and "go touch grass" off-ramp that used to be built here
                // pushed the only useful control below a full screen of scene.
                // The bits of it that carry information are still in the copy:
                // the headline and detail come from the same friendly sentences.
                ProfileErrorView(
                    message = viewerState.message,
                    kind = viewerState.kind,
                    canRetry = viewerState.canRetry,
                    isAuthError = viewerState.isAuthError,
                    onRetry = { mainDashboardViewModel?.loadData() },
                    onSignInAgain = onSignInAgain
                )
            } else {
                LibraryLoadingView(message = "Connecting to AniList to find your list\u2026")
            }
            return@composable
        }

        // Built at the destination: the first visit loads it, and switching away
        // saves the state with the entry (`navigateToMainTab`) rather than throwing
        // the list away.
        val libraryViewModel: UserOverviewViewModel = viewModel(
            key = "library_overview",
            factory = UserOverviewViewModel.Factory(
                aniListRepository = aniListRepository,
                // The viewer's own list - the same id the dashboard already fetched
                // under, which is what makes this the cheapest query in the app
                // rather than a second download of its most expensive one.
                userId = signedInViewer?.id,
                username = signedInViewer?.name ?: DEMO_USERNAME,
                // The session, not the loaded viewer: the window where the two
                // disagree is the branch above, so by here this is the demo app or
                // a viewer who has arrived.
                isDemo = authenticated == null
            )
        )

        LibraryScreen(
            repository = aniListRepository,
            overviewViewModel = libraryViewModel,
            detailHost = detailHost,
            remembered = librarySelection,
            gridState = libraryGridState,
            onOpenProfile = { navController.navigateToMainTab(MainTab.PROFILE, signedIn = authenticated != null) },
            onOpenTierList = {
                navController.navigateToMainTab(
                    MainTab.TIERS,
                    signedIn = authenticated != null
                )
            },
            onOpenStudio = { studioId, studioName ->
                navController.navigateSingle(StudioRoutes.studio(studioId, studioName))
            },
            onSignInAgain = onSignInAgain,
            entryEditor = sharedEntryEditor
        )
    }
}
