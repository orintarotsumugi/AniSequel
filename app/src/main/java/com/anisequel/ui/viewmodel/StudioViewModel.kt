package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.StudioNode
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.STUDIO_PAGE_SIZE
import com.anisequel.domain.studio.StudioYearShelf
import com.anisequel.domain.studio.studioYearShelves
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder for one studio's page: who they are, and what they made by year.
 *
 * ## One request is one page, and a page is 25 titles
 *
 * AniList's `Studio.media` connection caps `perPage` at 25 - not at the root `Page`
 * query's 50 - and answers a larger value with a 400 rather than trimming it. A
 * studio with three hundred credits is therefore twelve round trips if the reader
 * scrolls that far, which is why the pages are *appended* lazily from
 * [loadNextPage] instead of being fetched up front: the average studio page is read
 * for its first two or three shelves.
 *
 * ## Why the shelves are recomputed rather than accumulated
 *
 * Every page is merged into one list and the list is re-grouped. Grouping per page
 * and concatenating would put a year shelf on screen twice as soon as two adjacent
 * pages shared a year - which, at 25 titles per page over a studio's filmography,
 * they almost always do. The grouping itself is [studioYearShelves], pure and
 * tested, so this class never decides what a shelf is.
 *
 * ## The detail sheet
 *
 * Tapping a poster opens the same sheet the dashboard and profile use, so the heavy
 * fields come from the app's shared `MediaDetailHost` - not from a store of this
 * ViewModel's own, which is what made a title walked to from a filmography a second
 * cache beside the Library's. The host always fetches, which matters here: a studio
 * has no fixture data and needs no session to read, so its page is live even when
 * the app is showing the demo dashboard.
 */
class StudioViewModel(
    private val aniListRepository: AniListRepository,
    private val studioId: Int
) : ViewModel() {

    sealed interface StudioUiState {
        /** Before the first answer. The header still draws the name it was called with. */
        data object Loading : StudioUiState
        data class Success(
            val studio: StudioNode,
            val shelves: List<StudioYearShelf>,
            /** How many titles are actually on screen, across every shelf. */
            val titleCount: Int,
            val isLoadingMore: Boolean = false,
            val hasMore: Boolean = false,
            /**
             * Why the *next* page failed, or null.
             *
             * The same failure the activity feed had: an append that failed left no
             * trace, and the scroll that triggered it stays at the bottom, so the
             * page looked like it simply ended mid-filmography. The screen turns
             * this into a retry row.
             */
            val loadMoreError: String? = null
        ) : StudioUiState
        data class Error(val message: String) : StudioUiState
    }

    private val _state = MutableStateFlow<StudioUiState>(StudioUiState.Loading)
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    // The detail sheet's store is not held here. It used to be, which made a title
    // opened from a filmography a second cache beside the Library's and the
    // profile's - two answers to "what is this title". The navigation owns the one
    // store and passes it to the screen; see `MediaDetailHost`.

    private var loadJob: Job? = null
    private var nextPage = 1
    private var started = false

    /** Every title received so far, in arrival order, before grouping. */
    private val received = mutableListOf<MediaNode>()

    /**
     * Fetch the first page, once.
     *
     * Guarded because the screen is re-entered from every title that opened a studio
     * - and from the sheet's own back gesture - and a re-entry that refetched would
     * spend a request to show the identical shelves. [retry] is the way past the
     * guard when the first attempt failed.
     */
    fun loadFirstPage(forceRefresh: Boolean = false) {
        if (started && !forceRefresh) return
        started = true
        nextPage = 1
        received.clear()
        fetch(forceRefresh = forceRefresh, appending = false)
    }

    fun loadNextPage() {
        val current = _state.value as? StudioUiState.Success ?: return
        if (!current.hasMore || current.isLoadingMore || current.loadMoreError != null) return
        fetch(forceRefresh = false, appending = true)
    }

    /**
     * Retry after a failure, whether that was the first page or the next one.
     *
     * An appended-page failure keeps the shelves it has - clearing them to show an
     * error would throw away the part of the filmography the reader has already
     * read - so this clears only the error and asks again from the same page.
     */
    fun retry() {
        val current = _state.value
        if (current is StudioUiState.Success && received.isNotEmpty()) {
            _state.value = current.copy(loadMoreError = null)
            fetch(forceRefresh = false, appending = true)
            return
        }
        started = false
        loadFirstPage(forceRefresh = true)
    }

    private fun fetch(forceRefresh: Boolean, appending: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (appending) {
                (_state.value as? StudioUiState.Success)?.let {
                    _state.value = it.copy(isLoadingMore = true, loadMoreError = null)
                }
            } else if (_state.value !is StudioUiState.Success || forceRefresh) {
                _state.value = StudioUiState.Loading
            }

            val page = nextPage
            aniListRepository.getStudio(studioId, page)
                .onSuccess { node ->
                    received += node.media?.nodes.orEmpty()
                    // A short page is the end of the filmography. `>=` rather than
                    // `==` because AniList has been known to answer a few over the
                    // cap, and one extra title is not a reason to claim another page
                    // exists and then render an empty one.
                    val fullPage = node.media?.nodes.orEmpty().size >= STUDIO_PAGE_SIZE
                    _state.value = StudioUiState.Success(
                        studio = node,
                        shelves = studioYearShelves(received),
                        titleCount = received.size,
                        hasMore = fullPage,
                        isLoadingMore = false
                    )
                    nextPage = page + 1
                }
                .onFailure { failure ->
                    val message = failure.message ?: "Couldn't load this studio."
                    val current = _state.value
                    _state.value = if (appending && current is StudioUiState.Success) {
                        current.copy(isLoadingMore = false, loadMoreError = message)
                    } else {
                        StudioUiState.Error(message)
                    }
                }
        }
    }

    class Factory(
        private val aniListRepository: AniListRepository,
        private val studioId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StudioViewModel(
            aniListRepository = aniListRepository,
            studioId = studioId
        ) as T
    }
}
