package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
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

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _filterCriteria = MutableStateFlow(FilterCriteria())
    val filterCriteria: StateFlow<FilterCriteria> = _filterCriteria.asStateFlow()

    private val _eventFlow = MutableSharedFlow<DashboardEvent>()
    val eventFlow: SharedFlow<DashboardEvent> = _eventFlow.asSharedFlow()

    private var cachedViewer: ViewerProfile? = null
    private var cachedCollection: MediaListCollection? = null
    private val addedToPlanningIds = mutableSetOf<Int>()

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
                if (targetUsername != null) "Searching for @$targetUsername on AniList..." else "Connecting to AniList..."
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
                    val isAuth = err.message?.contains("Session expired", ignoreCase = true) == true ||
                            err.message?.contains("invalid", ignoreCase = true) == true
                    _uiState.value = DashboardUiState.Error(
                        message = err.message ?: "Failed to connect with AniList. Please check your credentials.",
                        isAuthError = isAuth
                    )
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

    fun refresh() {
        val current = _uiState.value
        if (current is DashboardUiState.Success) {
            _uiState.value = current.copy(isRefreshing = true)
        }
        loadData()
    }

    private fun recalculateMissedSequels(isDemo: Boolean = this.isDemo) {
        val viewer = cachedViewer ?: return
        val collection = cachedCollection ?: return

        val lists = collection.lists ?: emptyList()
        val allEntries = lists.flatMap { it.entries ?: emptyList() }
        val watchedCount = allEntries.count { entry ->
            val status = entry.status ?: entry.media.mediaListEntry?.status
            status.equals("COMPLETED", ignoreCase = true) ||
                    (entry.progress != null && entry.media.episodes != null && entry.progress >= entry.media.episodes && entry.media.episodes > 0)
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
        recalculateMissedSequels()
    }

    fun updateSortOption(sort: SequelSortOption) {
        _filterCriteria.value = _filterCriteria.value.copy(sortOption = sort)
        recalculateMissedSequels()
    }

    fun updateStatusFilter(status: StatusFilter) {
        _filterCriteria.value = _filterCriteria.value.copy(statusFilter = status)
        recalculateMissedSequels()
    }

    fun toggleIncludeUnreleased() {
        val current = _filterCriteria.value.includeUnreleased
        _filterCriteria.value = _filterCriteria.value.copy(includeUnreleased = !current)
        recalculateMissedSequels()
    }

    fun toggleHideAlreadyPlanned() {
        val current = _filterCriteria.value.hideAlreadyPlanned
        _filterCriteria.value = _filterCriteria.value.copy(hideAlreadyPlanned = !current)
        recalculateMissedSequels()
    }

    fun selectFormat(format: String?) {
        _filterCriteria.value = _filterCriteria.value.copy(selectedFormat = format)
        recalculateMissedSequels()
    }

    fun addToPlanning(sequel: MissedSequel) {
        val current = _uiState.value
        if (current !is DashboardUiState.Success) return

        if (current.isDemoMode) {
            addedToPlanningIds.add(sequel.sequelId)
            viewModelScope.launch {
                _eventFlow.emit(DashboardEvent.ShowSnackbar("[Demo Mode] Added \"${sequel.sequelTitle}\" to Planning list!"))
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
                        DashboardEvent.ShowSnackbar("Added \"${sequel.sequelTitle}\" to your AniList Planning list!")
                    )
                    recalculateMissedSequels()
                },
                onFailure = { err ->
                    recalculateMissedSequels()
                    _eventFlow.emit(
                        DashboardEvent.ShowSnackbar("Failed to add to planning: ${err.message}")
                    )
                }
            )
        }
    }
}
