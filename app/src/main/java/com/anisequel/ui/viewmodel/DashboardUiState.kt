package com.anisequel.ui.viewmodel

import com.anisequel.data.model.ArrivingEntry
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.domain.usecase.RecentActivitySummary

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
        /**
         * The viewer's own entries that are airing or announced.
         *
         * A separate list rather than a partition of [missedSequels] on
         * purpose: this section is never filtered, searched or sorted by the
         * filter sheet, because it answers "what is arriving?" rather than
         * "what am I missing?". Empty means nothing of theirs is arriving, so
         * the section hides itself.
         */
        val arriving: List<ArrivingEntry> = emptyList(),
        val totalWatchedCount: Int,
        val totalMissedCount: Int,
        /**
         * The viewer's in-progress anime, most recently updated first.
         *
         * Drawn as the new Home's Continue rail. Carried in the state for the
         * same reason as [arriving]: the collection has already been downloaded
         * and the walk is memoised in the ViewModel, so the rail costs a slice of
         * a list the page below it is drawing anyway - not a second query.
         *
         * Empty on the demo dashboard (there is no collection) and for a viewer
         * who has not started anything, which is exactly when the rail hides.
         */
        val continueWatching: List<MediaListEntryItem> = emptyList(),
        /**
         * What the activity widget's tile counts and names.
         *
         * The widget used to render "0 recent updates" for every signed-in
         * account because the app had no count to write. This is derived from
         * the list the dashboard already downloads - no extra request - and
         * carried in the state so the widget sync keyed on it repaints when the
         * answer changes.
         */
        val recentActivity: RecentActivitySummary = RecentActivitySummary.Empty,
        val isRefreshing: Boolean = false,
        /**
         * Whether what is on screen is the device's saved copy rather than a live
         * answer.
         *
         * It exists so the screen can say so. A cold start paints the snapshot
         * first and then replaces it when the network answers; when the network
         * does not answer, the snapshot stays and this flag stays true. Silently
         * presenting a day-old list as today's is the failure this avoids - the
         * list is still worth drawing, it just is not current.
         */
        val isCached: Boolean = false,
        val isDemoMode: Boolean = false,
        /**
         * Whether "Add to Planning" can actually reach AniList. False in demo
         * mode and when scanning someone else's public profile: both look
         * identical to a signed-in user until the mutation is rejected.
         */
        val canWriteToAniList: Boolean = true,
        /**
         * Up to three of the viewer's own entries, for Settings' card-style
         * preview.
         *
         * Carried in the state rather than fetched by Settings for a performance
         * reason: the dashboard has already downloaded this collection, and the
         * preview is three thumbnails. A second read from the repository would
         * either walk the cached list again or, on a cold Settings visit, download
         * the whole list to draw them. Empty is an answer - the demo dashboard and
         * the public-profile scan have no viewer list - and the preview falls back
         * to sample titles in that case.
         */
        val cardPreview: List<MediaListEntryItem> = emptyList()
    ) : DashboardUiState

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val isAuthError: Boolean = false,
        /**
         * The classified failure, when there was one.
         *
         * The screen uses it to choose the friendly headline, the joke and the
         * reachability it can infer without a request. Null means the failure
         * arrived as a plain [Throwable], and the screen falls back to its
         * generic title; it is optional so every existing construction path
         * keeps working.
         */
        val kind: AniListErrorKind? = null
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
        val actionLabel: String? = null,
        /**
         * The entry the action puts back, when the action is "Undo" on a hide.
         *
         * Carried by the event rather than read from a slot on the screen: the slot
         * held only the most recently hidden entry, and `collectLatest` cancels the
         * previous message - so hiding two entries quickly made the first message's
         * Undo restore the second one, or restore nothing.
         */
        val undoSequel: MissedSequel? = null
    ) : DashboardEvent
}