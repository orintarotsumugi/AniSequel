package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.FilterCriteria
import com.example.data.model.MediaListCollection
import com.example.data.model.MissedSequel
import com.example.data.model.SequelSortOption
import com.example.data.model.StatusFilter
import com.example.data.model.ViewerProfile
import com.example.data.repository.AniListRepository
import com.example.domain.usecase.FindMissedSequelsUseCase
import com.example.domain.usecase.GetViewerProfileUseCase
import com.example.domain.usecase.SaveToPlanningUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data class Loading(val message: String = "Connecting to AniList...") : DashboardUiState

    data class Success(
        val viewer: ViewerProfile,
        val missedSequels: List<MissedSequel>,
        val totalWatchedCount: Int,
        val totalMissedCount: Int,
        val isRefreshing: Boolean = false,
        val isDemoMode: Boolean = false
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

    init {
        loadData()
    }

    fun loadData() {
        if (isDemo) {
            loadDemoData()
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
                            recalculateMissedSequels()
                        },
                        onFailure = { err ->
                            _uiState.value = DashboardUiState.Error(
                                message = err.message ?: "Failed to fetch anime list from AniList"
                            )
                        }
                    )
                },
                onFailure = { err ->
                    _uiState.value = DashboardUiState.Error(
                        message = err.message ?: "Failed to connect with AniList. Please check your credentials.",
                        // A dead token is not a network blip: retrying the same
                        // request can never succeed, so the screen needs to offer
                        // a way back to the login screen rather than a Retry
                        // button that fails identically every time.
                        isAuthError = isAuthFailure(err)
                    )
                }
            )
        }
    }

    private fun isAuthFailure(error: Throwable): Boolean {
        val message = error.message ?: return false
        return listOf("session expired", "invalid", "token", "unauthorized", "viewer not found")
            .any { message.contains(it, ignoreCase = true) }
    }

    fun refresh() {
        // The refresh button used to blank the list back to a full-screen loading
        // state, which for a large account meant losing the results the user was
        // reading to watch a spinner. Keep the list up and show progress in place.
        (_uiState.value as? DashboardUiState.Success)?.let { current ->
            _uiState.value = current.copy(isRefreshing = true)
        }

        if (isDemo) {
            loadDemoData()
            return
        }

        viewModelScope.launch {
            val viewer = cachedViewer
                ?: run {
                    _uiState.value = DashboardUiState.Loading("Connecting to AniList...")
                    return@launch
                }

            val listResult = aniListRepository.getUserAnimeList(viewer.id)
            listResult.fold(
                onSuccess = { collection ->
                    cachedCollection = collection
                    // The server is the source of truth for what is already
                    // planned, so the optimistic local set is dropped here.
                    addedToPlanningIds.clear()
                    recalculateMissedSequels()
                },
                onFailure = { err ->
                    (_uiState.value as? DashboardUiState.Success)?.let { current ->
                        _uiState.value = current.copy(isRefreshing = false)
                    } ?: run {
                        _uiState.value = DashboardUiState.Error(
                            message = err.message ?: "Failed to refresh from AniList",
                            isAuthError = isAuthFailure(err)
                        )
                    }
                }
            )
        }
    }

    private fun loadDemoData() {
        val demoViewer = aniListRepository.getDemoProfile()
        val demoList = aniListRepository.getDemoAnimeList()
        cachedViewer = demoViewer
        cachedCollection = demoList
        recalculateMissedSequels(isDemo = true)
    }

    private fun recalculateMissedSequels(isDemo: Boolean = this.isDemo) {
        val viewer = cachedViewer ?: return
        val collection = cachedCollection ?: return

        val lists = collection.lists ?: emptyList()
        val allEntries = lists.flatMap { it.entries ?: emptyList() }
        val watchedCount = allEntries.count { entry ->
            val status = entry.status ?: entry.media.mediaListEntry?.status
            status.equals("COMPLETED", ignoreCase = true) ||
                    (entry.progress != null && entry.media.episodes != null &&
                            entry.progress >= entry.media.episodes && entry.media.episodes > 0)
        }

        val allMissed = findMissedSequelsUseCase.execute(collection, _filterCriteria.value)

        val updatedList = allMissed.map { item ->
            if (addedToPlanningIds.contains(item.sequelId)) {
                item.copy(isAddedToPlanning = true, isAddingToPlanning = false)
            } else {
                item
            }
        }

        _uiState.value = DashboardUiState.Success(
            viewer = viewer,
            missedSequels = updatedList,
            totalWatchedCount = watchedCount,
            totalMissedCount = updatedList.size,
            isRefreshing = false,
            isDemoMode = isDemo
        )
    }

    fun updateSearchQuery(query: String) {
        _filterCriteria.value = _filterCriteria.value.copy(searchQuery = query)

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            recalculateMissedSequels()
        }
    }

    fun updateSortOption(sortOption: SequelSortOption) {
        _filterCriteria.value = _filterCriteria.value.copy(sortOption = sortOption)
        recalculateMissedSequels()
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
        recalculateMissedSequels()
    }

    fun toggleIncludeUnreleased(include: Boolean = !_filterCriteria.value.includeUnreleased) {
        _filterCriteria.value = _filterCriteria.value.copy(includeUnreleased = include)
        recalculateMissedSequels()
    }

    fun toggleHideAlreadyPlanned(hide: Boolean = !_filterCriteria.value.hideAlreadyPlanned) {
        _filterCriteria.value = _filterCriteria.value.copy(hideAlreadyPlanned = hide)
        recalculateMissedSequels()
    }

    fun selectFormat(format: String?) {
        _filterCriteria.value = _filterCriteria.value.copy(selectedFormat = format)
        recalculateMissedSequels()
    }

    fun resetFilters() {
        searchJob?.cancel()
        _filterCriteria.value = FilterCriteria()
        recalculateMissedSequels()
    }

    fun addToPlanning(sequel: MissedSequel) {
        val current = _uiState.value
        if (current !is DashboardUiState.Success) return

        if (current.isDemoMode) {
            addedToPlanningIds.add(sequel.sequelId)
            viewModelScope.launch {
                _eventFlow.emit(
                    DashboardEvent.ShowSnackbar("Demo mode: \"${sequel.sequelTitle}\" was not saved to AniList")
                )
            }
            recalculateMissedSequels()
            return
        }

        val updatedList = current.missedSequels.map {
            if (it.sequelId == sequel.sequelId) it.copy(isAddingToPlanning = true) else it
        }
        _uiState.value = current.copy(missedSequels = updatedList)

        viewModelScope.launch {
            val result = saveToPlanningUseCase.execute(sequel.sequelId)
            result.fold(
                onSuccess = {
                    addedToPlanningIds.add(sequel.sequelId)
                    _eventFlow.emit(
                        DashboardEvent.ShowSnackbar("Added \"${sequel.sequelTitle}\" to your AniList Planning list")
                    )
                    recalculateMissedSequels()
                },
                onFailure = { err ->
                    // Clearing isAddingToPlanning matters: the card it came from
                    // keeps its own copy, and the detail sheet was left spinning
                    // on that copy forever because nothing told it the request
                    // had ended.
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