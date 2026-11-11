package com.anisequel.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.components.detail.EntryEditor
import com.anisequel.ui.screens.nova.NovaOtherScreen
import com.anisequel.ui.screens.nova.NovaScheduleScreen
import com.anisequel.ui.screens.nova.NovaSearchScreen
import com.anisequel.ui.viewmodel.DashboardUiState
import com.anisequel.ui.viewmodel.DashboardViewModel
import com.anisequel.ui.viewmodel.MediaDetailHost

/**
 * The new look's feature hub.
 *
 * Its own route rather than a sheet because it is a page in the reference this
 * layout follows, and because every card on it is a real destination the reader
 * should be able to come back from by the system gesture. It is deliberately not
 * a main-bar tab: the bar has four contended slots, and this is reached from the
 * one card on Home that names it.
 *
 * No data is loaded here. The cards are doors to pages that already own their
 * queries, which is what keeps opening the hub free even on AniList's rate limit.
 */
fun NavGraphBuilder.otherDestinations(
    navController: NavHostController,
    signedIn: Boolean,
    aniListRepository: AniListRepository,
    dashboardViewModel: DashboardViewModel?,
    detailHost: MediaDetailHost,
    entryEditor: EntryEditor?
) {
    composable(AppRoutes.OTHER) {
        NovaOtherScreen(
            onBack = { navController.popBackStack() },
            onOpenSchedule = { navController.navigateSingle(AppRoutes.SCHEDULE) },
            onOpenProfile = {
                navController.navigateToMainTab(MainTab.PROFILE, signedIn = signedIn)
            },
            onOpenLibrary = {
                navController.navigateToMainTab(MainTab.LIBRARY, signedIn = signedIn)
            },
            onOpenTiers = {
                navController.navigateToMainTab(MainTab.TIERS, signedIn = signedIn)
            },
            onOpenSettings = { navController.navigateSingle(AppRoutes.SETTINGS) }
        )
    }

    // The catalogue search. One of the bar's pages when its tab is enabled, and
    // the only page in the app that is reached *only* through the bar - which is
    // why it is registered here beside the hub rather than in a graph of its own.
    composable(AppRoutes.SEARCH) {
        NovaSearchScreen(
            repository = aniListRepository,
            canWrite = signedIn,
            onBack = { navController.popBackStack() },
            detailHost = detailHost,
            entryEditor = entryEditor,
            onOpenStudio = { id, name ->
                navController.navigateSingle(StudioRoutes.studio(id, name))
            }
        )
    }

    composable(AppRoutes.SCHEDULE) {
        val openStudio: (Int, String?) -> Unit = { id, name ->
            navController.navigateSingle(StudioRoutes.studio(id, name))
        }
        if (dashboardViewModel == null) {
            NovaScheduleScreen(
                entries = emptyList(),
                isLoading = false,
                error = null,
                repository = aniListRepository,
                detailHost = detailHost,
                entryEditor = null,
                onBack = { navController.popBackStack() },
                onOpenStudio = openStudio
            )
        } else {
            DashboardSchedule(
                dashboardViewModel = dashboardViewModel,
                repository = aniListRepository,
                detailHost = detailHost,
                entryEditor = entryEditor,
                onBack = { navController.popBackStack() },
                onOpenStudio = openStudio
            )
        }
    }
}

/**
 * Reads the dashboard's already-downloaded list for the schedule.
 *
 * The schedule is not a second query: `arriving` is the same memoised slice Home
 * draws, and opening this page while the dashboard is refreshing shows the same
 * waiting state rather than an invented empty schedule.
 */
@Composable
private fun DashboardSchedule(
    dashboardViewModel: DashboardViewModel,
    repository: AniListRepository,
    detailHost: MediaDetailHost,
    entryEditor: EntryEditor?,
    onBack: () -> Unit,
    onOpenStudio: (Int, String?) -> Unit
) {
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()

    NovaScheduleScreen(
        entries = (state as? DashboardUiState.Success)?.arriving.orEmpty(),
        isLoading = state is DashboardUiState.Loading,
        error = (state as? DashboardUiState.Error)?.message,
        repository = repository,
        detailHost = detailHost,
        entryEditor = entryEditor,
        onBack = onBack,
        onOpenStudio = onOpenStudio
    )
}
