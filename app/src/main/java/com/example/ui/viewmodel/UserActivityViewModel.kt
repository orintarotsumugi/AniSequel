package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ListActivity
import com.example.data.network.AniListErrorKind
import com.example.data.network.AniListException
import com.example.data.repository.AniListRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder for the Activity tab: somebody's list history, newest first.
 *
 * ## Why it does not load until it is asked
 *
 * The feed is a second and third request that a person who opened their profile
 * to look at their favourites does not need, and it is the only thing on this
 * screen that can be *long* - twenty-five entries per page, page after page. It
 * is fetched when the Activity tab is selected rather than in `init`, which is
 * the same rule the repository's caches exist to support: a profile the user
 * glanced at must not have spent AniList's request budget on a scrollback.
 *
 * ## Pagination
 *
 * [loadNextPage] exists and the screen calls it as the list nears its end. It is
 * one page per call rather than a loop, because the alternative is a screen that
 * silently pulls twenty pages while nobody is watching.
 */
class UserActivityViewModel(
    private val aniListRepository: AniListRepository,
    /**
     * Whose activity this is.
     *
     * Required, and nullable-safe: a feed is only addressable by id - AniList has
     * no "activity by username" - so a profile opened without one can show the
     * favourites but cannot show a feed, and says so rather than loading the
     * wrong person's.
     */
    private val userId: Int?,
    private val isDemo: Boolean = false,
    private val pageSize: Int = 25
) : ViewModel() {

    sealed interface ActivityState {
        /** Not fetched yet - the tab has not been opened, or there is no id. */
        data object Idle : ActivityState
        data class Loading(val message: String = "Loading recent activity...") : ActivityState
        data class Success(
            val activities: List<ListActivity>,
            val isLoadingMore: Boolean = false,
            val hasMore: Boolean = false
        ) : ActivityState
        data class Error(val message: String, val isAuthError: Boolean = false) : ActivityState
    }

    private val _state = MutableStateFlow<ActivityState>(ActivityState.Idle)
    val state: StateFlow<ActivityState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var nextPage = 1
    private var started = false

    /**
     * Fetch the first page, once.
     *
     * Guarded rather than idempotent because the Activity tab is re-entered every
     * time the user taps it, and a re-entry that refetched would spend a request
     * to show the identical list - which is also what the repository's cache would
     * prevent, one layer too late to matter for the UI.
     */
    fun loadFirstPage(forceRefresh: Boolean = false) {
        if (started && !forceRefresh) return
        started = true
        nextPage = 1
        fetch(forceRefresh)
    }

    fun loadNextPage() {
        val current = _state.value as? ActivityState.Success ?: return
        if (!current.hasMore || current.isLoadingMore) return
        fetch(forceRefresh = false, appending = true)
    }

    fun retry() {
        started = true
        nextPage = 1
        fetch(forceRefresh = false)
    }

    private fun fetch(forceRefresh: Boolean, appending: Boolean = false) {
        if (userId == null && !isDemo) {
            _state.value = ActivityState.Error(
                "AniList does not serve activity by name, and this profile was opened without an id."
            )
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (appending) {
                (_state.value as? ActivityState.Success)?.let {
                    _state.value = it.copy(isLoadingMore = true)
                }
            } else {
                _state.value = ActivityState.Loading()
            }

            val page = nextPage
            val result = if (isDemo) {
                Result.success(aniListRepository.getDemoUserActivity())
            } else {
                aniListRepository.getUserActivity(userId!!, page, forceRefresh)
            }

            result.fold(
                onSuccess = { activities ->
                    nextPage = page + 1
                    val existing = (_state.value as? ActivityState.Success)?.activities.orEmpty()
                    // De-duplicated on the id because pages overlap whenever a new
                    // update lands between two requests - the list is ordered by
                    // update time, so a row fetched on both pages appears twice and
                    // a `LazyColumn` keyed on it would throw.
                    val combined = if (appending) existing + activities else activities
                    _state.value = ActivityState.Success(
                        activities = combined.distinctBy { it.id },
                        isLoadingMore = false,
                        // A short page means the end. One short page is the only
                        // signal AniList gives here - `Page.pageInfo` is not asked
                        // for, because for a union-typed field the parse cost is
                        // not worth a boolean this derives more reliably.
                        hasMore = activities.size >= pageSize
                    )
                },
                onFailure = { error ->
                    _state.value = ActivityState.Error(
                        message = error.message ?: "Couldn't load recent activity.",
                        isAuthError = (error as? AniListException)?.kind == AniListErrorKind.INVALID_SESSION
                    )
                }
            )
        }
    }

    class Factory(
        private val aniListRepository: AniListRepository,
        private val userId: Int? = null,
        private val isDemo: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UserActivityViewModel(
            aniListRepository = aniListRepository,
            userId = userId,
            isDemo = isDemo
        ) as T
    }
}