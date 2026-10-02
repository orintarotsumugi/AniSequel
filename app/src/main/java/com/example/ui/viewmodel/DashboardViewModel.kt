package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.FilterCriteria
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaNode
import com.example.data.model.MissedSequel
import com.example.data.model.RelationKind
import com.example.data.model.SequelSortOption
import com.example.data.model.StatusFilter
import com.example.data.model.ViewerProfile
import com.example.data.network.AniListErrorKind
import com.example.data.network.AniListException
import com.example.data.repository.AniListRepository
import com.example.domain.usecase.FindMissedSequelsUseCase
import com.example.domain.usecase.GetViewerProfileUseCase
import com.example.domain.usecase.SaveToPlanningUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

sealed interface DashboardEvent {
    data class ShowSnackbar(val message: String) : DashboardEvent
}

class DashboardViewModel(
    private val aniListRepository: AniListRepository,
    private val targetUsername: String? = null,
    private val isDemo: Boolean = false,
    private val getViewerProfileUseCase: GetViewerProfileUseCase = GetViewerProfileUseCase(aniListRepository),
    private val saveToPlanningUseCase: SaveToPlanningUseCase = SaveToPlanningUseCase(aniListRepository),
    private val findMissedSequelsUseCase: FindMissedSequelsUseCase = FindMissedSequelsUseCase()
) : ViewModel() {

    companion object {
        // Re-analysing the whole list on every keystroke visibly stutters once a
        // user has a few thousand completed entries, so the query is held back
        // until the typing pauses.
        private const val SEARCH_DEBOUNCE_MS = 220L
    }

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _filterCriteria = MutableStateFlow(FilterCriteria())
    val filterCriteria: StateFlow<FilterCriteria> = _filterCriteria.asStateFlow()

    private val _eventFlow = MutableSharedFlow<DashboardEvent>()
    val eventFlow: SharedFlow<DashboardEvent> = _eventFlow.asSharedFlow()

    private var cachedViewer: ViewerProfile? = null
    private var cachedCollection: MediaListCollection? = null
    private val addedToPlanningIds = mutableSetOf<Int>()
    private var searchJob: Job? = null

    /**
     * Everything the list is missing, before filtering.
     *
     * Walking the relations of a few hundred entries is the expensive half of
     * this screen and it only depends on two things: whether already-planned
     * entries are hidden, and which relation kinds are included. Caching on that
     * pair means sorting, searching and changing status or format no longer
     * re-walk the whole list.
     */
    private var discoveredCandidates: List<MissedSequel>? = null
    private var discoveryKey: Pair<Boolean, Set<RelationKind>>? = null

    /** Description, banner and studio per media, fetched only when opened. */
    private val detailCache = mutableMapOf<Int, MediaNode>()

    /**
     * Ids whose one-off detail query is in flight.
     *
     * Kept out of [DashboardUiState.Success] deliberately: a detail arriving
     * patches a single card, and routing that through a full recompute would
     * throw away the "loading" state the sheet is rendering.
     */
    private val _loadingDetailIds = MutableStateFlow<Set<Int>>(emptySet())
    val loadingDetailIds: StateFlow<Set<Int>> = _loadingDetailIds.asStateFlow()

    /** True when the current session may write back to the user's AniList. */
    private val canWriteToAniList: Boolean
        get() = !isDemo && targetUsername.isNullOrBlank()

    init {
        loadData()
    }

    fun loadData() {
        if (isDemo) {
            viewModelScope.launch { loadDemoData() }
            return
        }

        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading(
                if (!targetUsername.isNullOrBlank()) {
                    "Searching for @$targetUsername on AniList..."
                } else {
                    "Connecting to AniList..."
                }
            )

            val viewerResult = if (!targetUsername.isNullOrBlank()) {
                aniListRepository.getUserByName(targetUsername)
            } else {
                getViewerProfileUseCase.execute()
            }

            viewerResult.fold(
                onSuccess = { viewer ->
                    cachedViewer = viewer
                    _uiState.value = DashboardUiState.Loading("Scanning ${viewer.name}'s anime history...")

                    val listResult = aniListRepository.getUserAnimeList(viewer.id)
                    listResult.fold(
                        onSuccess = { collection ->
                            cachedCollection = collection
                            // A new list invalidates everything derived from it.
                            discoveredCandidates = null
                            discoveryKey = null
                            detailCache.clear()
                            addedToPlanningIds.clear()
                            recompute()
                        },
                        onFailure = { err -> showError(err, fallback = "Failed to fetch anime list") }
                    )
                },
                onFailure = { err -> showError(err, fallback = "Failed to connect with AniList") }
            )
        }
    }

    /**
     * A dead token is not a network blip: retrying the same request can never
     * succeed, so the screen needs a way back to the login screen rather than a
     * Retry button that fails identically every time.
     */
    private fun showError(error: Throwable, fallback: String) {
        _uiState.value = DashboardUiState.Error(
            message = error.message ?: fallback,
            isAuthError = error.isAuthFailure()
        )
    }

    private fun Throwable.isAuthFailure(): Boolean =
        (this as? AniListException)?.kind == AniListErrorKind.INVALID_SESSION

    fun refresh() {
        // The refresh button used to blank the list back to a full-screen loading
        // state, which for a large account meant losing the results the user was
        // reading to watch a spinner. Keep the list up and show progress in place.
        (_uiState.value as? DashboardUiState.Success)?.let { current ->
            _uiState.value = current.copy(isRefreshing = true)
        }

        if (isDemo) {
            viewModelScope.launch { loadDemoData() }
            return
        }

        viewModelScope.launch {
            val viewer = cachedViewer ?: run {
                _uiState.value = DashboardUiState.Loading("Connecting to AniList...")
                return@launch
            }

            aniListRepository.getUserAnimeList(viewer.id).fold(
                onSuccess = { collection ->
                    cachedCollection = collection
                    // The server is the source of truth for what is already
                    // planned, so the optimistic local set is dropped here.
                    addedToPlanningIds.clear()
                    discoveredCandidates = null
                    discoveryKey = null
                    recompute()
                },
                onFailure = { err ->
                    val success = _uiState.value as? DashboardUiState.Success
                    if (success != null) {
                        _uiState.value = success.copy(isRefreshing = false)
                    } else {
                        showError(err, fallback = "Failed to refresh from AniList")
                    }
                }
            )
        }
    }

    private suspend fun loadDemoData() {
        cachedViewer = aniListRepository.getDemoProfile()
        cachedCollection = aniListRepository.getDemoAnimeList()
        discoveredCandidates = null
        discoveryKey = null
        recompute()
    }

    /**
     * Recomputes what is on screen.
     *
     * The list walk and the sort both run on [Dispatchers.Default]: for a large
     * account this is thousands of comparisons over hundreds of objects, which
     * used to run on the main thread and drop frames on every filter change.
     */
    private suspend fun recompute() {
        val viewer = cachedViewer ?: return
        val collection = cachedCollection ?: return
        val criteria = _filterCriteria.value

        val candidates = discoverOnce(collection, criteria)

        val (watchedCount, visible) = withContext(Dispatchers.Default) {
            countWatched(collection) to findMissedSequelsUseCase.applyFilters(candidates, criteria)
        }

        val updated = visible
            .map { withCachedDetail(it) }
            .map { if (addedToPlanningIds.contains(it.sequelId)) it.copy(isAddedToPlanning = true) else it }

        _uiState.value = DashboardUiState.Success(
            viewer = viewer,
            missedSequels = updated,
            totalWatchedCount = watchedCount,
            totalMissedCount = updated.size,
            isRefreshing = false,
            isDemoMode = isDemo,
            canWriteToAniList = canWriteToAniList
        )
    }

    private suspend fun discoverOnce(
        collection: MediaListCollection,
        criteria: FilterCriteria
    ): List<MissedSequel> {
        val key = criteria.hideAlreadyPlanned to criteria.includedRelations
        discoveredCandidates?.let { cached ->
            if (discoveryKey == key) return cached
        }

        val discovered = withContext(Dispatchers.Default) {
            findMissedSequelsUseCase.discover(collection, criteria)
        }
        discoveredCandidates = discovered
        discoveryKey = key
        return discovered
    }

    private fun countWatched(collection: MediaListCollection): Int {
        val lists = collection.lists ?: return 0
        val entries = lists.flatMap { group -> group.entries.orEmpty() }
        return entries.count { entry ->
            val status = entry.status ?: entry.media.mediaListEntry?.status
            status.equals("COMPLETED", ignoreCase = true) ||
                    (entry.progress != null && entry.media.episodes != null &&
                            entry.media.episodes > 0 && entry.progress >= entry.media.episodes)
        }
    }

    private fun withCachedDetail(sequel: MissedSequel): MissedSequel =
        detailCache[sequel.sequelId]?.let(sequel::withDetail) ?: sequel

    /**
     * Fetches the synopsis, banner and studio for one entry.
     *
     * These are the fields the list query leaves out on purpose: they are only
     * ever rendered for the entry a user opens, and asking AniList for them
     * across every relation node is what makes it return HTTP 500 on large
     * accounts. Demo data is already complete, so it skips the call.
     */
    fun loadDetail(sequel: MissedSequel) {
        if (isDemo || detailCache.containsKey(sequel.sequelId)) return

        _loadingDetailIds.value = _loadingDetailIds.value + sequel.sequelId
        viewModelScope.launch {
            try {
                aniListRepository.getMediaDetail(sequel.sequelId).onSuccess { detail ->
                    detailCache[sequel.sequelId] = detail
                    val success = _uiState.value as? DashboardUiState.Success ?: return@onSuccess
                    _uiState.value = success.copy(
                        missedSequels = success.missedSequels.map { item ->
                            if (item.sequelId == sequel.sequelId) item.withDetail(detail) else item
                        }
                    )
                }
                // A failed detail load is not worth interrupting the user over:
                // the sheet simply shows what the list query already had.
            } finally {
                _loadingDetailIds.value = _loadingDetailIds.value - sequel.sequelId
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _filterCriteria.value = _filterCriteria.value.copy(searchQuery = query)

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            recompute()
        }
    }

    fun updateSortOption(sortOption: SequelSortOption) {
        _filterCriteria.value = _filterCriteria.value.copy(sortOption = sortOption)
        viewModelScope.launch { recompute() }
    }

    fun updateStatusFilter(status: StatusFilter) {
        // Choosing "Upcoming" while "Include unreleased" was off produced an
        // empty screen with nothing on it to explain why: the switch said
        // upcoming entries were filtered out, and the status chip asked for
        // exactly those.
        val criteria = _filterCriteria.value
        _filterCriteria.value = if (status == StatusFilter.NOT_YET_RELEASED) {
            criteria.copy(statusFilter = status, includeUnreleased = true)
        } else {
            criteria.copy(statusFilter = status)
        }
        viewModelScope.launch { recompute() }
    }

    fun toggleIncludeUnreleased(include: Boolean = !_filterCriteria.value.includeUnreleased) {
        _filterCriteria.value = _filterCriteria.value.copy(includeUnreleased = include)
        viewModelScope.launch { recompute() }
    }

    fun toggleHideAlreadyPlanned(hide: Boolean = !_filterCriteria.value.hideAlreadyPlanned) {
        _filterCriteria.value = _filterCriteria.value.copy(hideAlreadyPlanned = hide)
        viewModelScope.launch { recompute() }
    }

    /** Widens the results to prequels, side stories and spin-offs. */
    fun toggleRelation(kind: RelationKind, enabled: Boolean? = null) {
        val current = _filterCriteria.value.includedRelations
        val next = when (enabled) {
            null -> if (kind in current) current - kind else current + kind
            true -> current + kind
            false -> current - kind
        }
        _filterCriteria.value = _filterCriteria.value.copy(
            includedRelations = next.ifEmpty { setOf(RelationKind.SEQUEL) }
        )
        viewModelScope.launch { recompute() }
    }

    fun selectFormat(format: String?) {
        _filterCriteria.value = _filterCriteria.value.copy(selectedFormat = format)
        viewModelScope.launch { recompute() }
    }

    fun resetFilters() {
        searchJob?.cancel()
        _filterCriteria.value = FilterCriteria()
        viewModelScope.launch { recompute() }
    }

    fun addToPlanning(sequel: MissedSequel) {
        val current = _uiState.value
        if (current !is DashboardUiState.Success) return

        if (!current.canWriteToAniList) {
            viewModelScope.launch {
                _eventFlow.emit(
                    DashboardEvent.ShowSnackbar(
                        if (current.isDemoMode) {
                            "Demo mode: nothing is saved to AniList. Connect an account to add entries for real."
                        } else {
                            "Connect your own AniList account to add entries to your Planning list."
                        }
                    )
                )
            }
            return
        }

        val updatedList = current.missedSequels.map {
            if (it.sequelId == sequel.sequelId) it.copy(isAddingToPlanning = true) else it
        }
        _uiState.value = current.copy(missedSequels = updatedList)

        viewModelScope.launch {
            saveToPlanningUseCase.execute(sequel.sequelId).fold(
                onSuccess = {
                    addedToPlanningIds.add(sequel.sequelId)
                    _eventFlow.emit(
                        DashboardEvent.ShowSnackbar(
                            "Added \"${sequel.sequelTitle}\" to your AniList Planning list"
                        )
                    )
                    recompute()
                },
                onFailure = { err ->
                    // Clearing isAddingToPlanning matters: the detail sheet holds
                    // its own copy, and without this it spins forever because
                    // nothing told it the request had ended.
                    val success = _uiState.value as? DashboardUiState.Success
                    if (success != null) {
                        _uiState.value = success.copy(
                            missedSequels = success.missedSequels.map { item ->
                                if (item.sequelId == sequel.sequelId) {
                                    item.copy(isAddingToPlanning = false)
                                } else {
                                    item
                                }
                            }
                        )
                    }

                    _eventFlow.emit(
                        DashboardEvent.ShowSnackbar(
                            err.message ?: "Could not add to your Planning list"
                        )
                    )
                }
            )
        }
    }

    /**
     * Factory for the manual construction in AppNavigation.
     *
     * The ViewModels used to be built with `remember { DashboardViewModel(...) }`,
     * which tied them to the composition rather than to a ViewModelStore: rotating
     * the device threw one away and re-fetched the user's whole list, and they
     * were never cleared when a destination left the back stack.
     */
    class Factory(
        private val aniListRepository: AniListRepository,
        private val targetUsername: String? = null,
        private val isDemo: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(
                aniListRepository = aniListRepository,
                targetUsername = targetUsername,
                isDemo = isDemo
            ) as T
        }
    }
}