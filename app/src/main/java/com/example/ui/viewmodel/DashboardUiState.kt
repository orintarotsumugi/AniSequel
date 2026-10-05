package com.example.ui.viewmodel

import com.example.data.model.MissedSequel
import com.example.data.model.ViewerProfile

/**
 * The dashboard's public state contract.
 *
 * Split out of `DashboardViewModel` because these two types are what the *UI*
 * depends on and the ViewModel is not: `DashboardScreen` switches over every
 * branch of [DashboardUiState] and collects every [DashboardEvent], and neither
 * of them has a behaviour, a dependency on a repository, or a coroutine in it.
 *
 * Keeping them in their own file makes the screen's requirements readable without
 * scrolling through 700 lines of state-mutation to find out what shape the state
 * is. Nothing else moves with this - it is the same two declarations, in the same
 * package, with the same names, so no call site changes.
 */
sealed interface DashboardUiState {
    data class Loading(val message: String = "Connecting to AniList...") : DashboardUiState

    data class Success(
        val viewer: ViewerProfile,
        val missedSequels: List<MissedSequel>,
        val totalWatchedCount: Int,
        val totalMissedCount: Int,
        val isRefreshing: Boolean = false,
        val isDemoMode: Boolean = false,
        /**
         * Whether "Add to Planning" can actually reach AniList. False in demo
         * mode and when scanning someone else's public profile: both look
         * identical to a signed-in user until the mutation is rejected.
         */
        val canWriteToAniList: Boolean = true
    ) : DashboardUiState

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val isAuthError: Boolean = false
    ) : DashboardUiState
}

/**
 * One-shot things the dashboard does in response to a user action.
 *
 * Separate from [DashboardUiState] because these are *events*, not state: a
 * snackbar that has been shown cannot be un-shown by the screen that missed it,
 * which is exactly what happens across a configuration change or while a detail
 * sheet is open.
 */
sealed interface DashboardEvent {
    /**
     * [actionLabel] adds a button to the snackbar and the screen runs it when
     * tapped. Hiding an entry is the one gesture in the app that is easy to do
     * by accident and tedious to undo by hand, so it carries an undo rather
     * than only telling the user where the list lives.
     */
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null
    ) : DashboardEvent
}