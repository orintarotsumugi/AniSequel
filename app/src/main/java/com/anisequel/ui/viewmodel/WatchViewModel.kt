package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.extensions.ExtensionManager
import com.anisequel.data.extensions.ExtensionContent
import com.anisequel.data.extensions.PlayableSource
import com.anisequel.data.extensions.SourceAnime
import com.anisequel.data.extensions.SourceDetails
import com.anisequel.data.extensions.SourceMapping
import com.anisequel.data.extensions.WatchPreferences
import com.anisequel.ui.navigation.WatchAnime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Watch page's state.
 *
 * [details] is the source's answer; [mapping] is the reader's saved choice of
 * *which* source result belongs to this AniList title. They are separate because
 * a search can return a list without a choice (the search sheet) and a chosen
 * mapping can exist before its detail request finishes.
 */
data class WatchUiState(
    val sources: List<PlayableSource> = emptyList(),
    val selectedSourceId: String? = null,
    val mapping: SourceMapping? = null,
    val results: List<SourceAnime> = emptyList(),
    val details: SourceDetails? = null,
    val isSearching: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null
) {
    val selectedSource: PlayableSource?
        get() = sources.firstOrNull { it.id == selectedSourceId }

    val matchedAnime: SourceAnime?
        get() = details?.anime
            ?: mapping?.let { SourceAnime(it.animeId, it.animeTitle, it.coverUrl) }

    val episodes get() = details?.episodes.orEmpty()

    val isBusy: Boolean get() = isSearching || isLoadingDetails
}

/**
 * One title's Watch page: the source match, the search, and the episode list.
 *
 * The page opens on the saved mapping when there is one and otherwise searches
 * the source by the AniList title and takes the first answer, which is what makes
 * the hero's "Found: ..." card appear without the reader typing. Taking the first
 * answer automatically is a deliberate beta trade: it is the only way one tap
 * gets from a library title to a playable episode, and "Wrong Title?" is the
 * one-tap correction. Nothing is written to AniList; a wrong match never changes
 * the reader's list.
 *
 * The source search itself is `ExtensionManager`'s - this class knows the
 * difference between a search and a detail request, not how either is performed.
 */
class WatchViewModel(
    private val manager: ExtensionManager,
    val media: WatchAnime
) : ViewModel() {

    val watchPreferences: WatchPreferences get() = manager.watchPreferences

    private val _state = MutableStateFlow(
        WatchUiState(searchQuery = media.title)
    )
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val sources = manager.sources().filter { it.content == ExtensionContent.ANIME }
            val saved = manager.watchPreferences.mappings.value[media.id]
            val playableIds = sources.filter { it.playable }.map { it.id }.toSet()
            val initial = saved?.sourceId?.takeIf { it in playableIds }
                ?: sources.firstOrNull { it.playable }?.id
            _state.update {
                it.copy(
                    sources = sources,
                    selectedSourceId = initial,
                    mapping = saved.takeIf { saved?.sourceId == initial },
                    searchQuery = media.title
                )
            }
            if (initial == null) {
                _state.update {
                    it.copy(error = "No playable source is installed. Add a repository in Extensions.")
                }
                return@launch
            }
            val usableMapping = saved?.takeIf { it.sourceId == initial }
            if (usableMapping != null) {
                loadDetails(initial, usableMapping.animeId, mapping = usableMapping)
            } else {
                autoMatch(initial)
            }
        }
    }

    /** Sets the search field only; submitting is [search]. */
    fun setSearchQuery(value: String) {
        _state.update { it.copy(searchQuery = value) }
    }

    /**
     * Searches the current source without choosing a result.
     *
     * The "Wrong Title?" sheet uses this. It does not clear the current mapping:
     * the reader is looking for a correction, and backing out of the sheet should
     * leave the page exactly as it was.
     */
    fun search(query: String = _state.value.searchQuery) {
        val sourceId = _state.value.selectedSourceId ?: return
        val cleaned = query.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null, searchQuery = cleaned) }
            runCatching { manager.search(sourceId, cleaned) }
                .onSuccess { results ->
                    _state.update {
                        it.copy(
                            isSearching = false,
                            results = results,
                            error = if (results.isEmpty()) {
                                "No result for \"$cleaned\" in ${it.selectedSource?.name}."
                            } else {
                                null
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSearching = false,
                            error = error.message ?: "The source search failed."
                        )
                    }
                }
        }
    }

    /** Picks one search result, saves it and loads its episodes. */
    fun chooseResult(anime: SourceAnime) {
        val sourceId = _state.value.selectedSourceId ?: return
        val mapping = SourceMapping(
            mediaId = media.id,
            sourceId = sourceId,
            animeId = anime.id,
            animeTitle = anime.title,
            coverUrl = anime.coverUrl
        )
        manager.watchPreferences.saveMapping(mapping)
        _state.update {
            it.copy(
                mapping = mapping,
                results = emptyList(),
                error = null
            )
        }
        viewModelScope.launch { loadDetails(sourceId, anime.id, mapping) }
    }

    /**
     * Switches source and searches the new one.
     *
     * The previous mapping is dropped before the search: its anime id belongs to
     * the old source's namespace, and keeping it would make the next "Found"
     * card name an id the new source has never heard of. Progress is cleared by
     * `WatchPreferences.saveMapping` when the source changes.
     */
    fun selectSource(source: PlayableSource) {
        if (!source.playable) {
            _state.update {
                it.copy(
                    error = "This ${source.kind.label} extension is installed, but its " +
                        "APK runtime is Phase 2. The built-in Internet Archive source plays now."
                )
            }
            return
        }
        if (source.id == _state.value.selectedSourceId && _state.value.details != null) return
        manager.watchPreferences.clearMapping(media.id)
        _state.update {
            it.copy(
                selectedSourceId = source.id,
                mapping = null,
                details = null,
                results = emptyList(),
                error = null
            )
        }
        viewModelScope.launch { autoMatch(source.id) }
    }

    /** Retry after a network failure: detail first if a mapping exists. */
    fun retry() {
        val state = _state.value
        val sourceId = state.selectedSourceId ?: return
        viewModelScope.launch {
            val mapping = state.mapping
            if (mapping != null) loadDetails(sourceId, mapping.animeId, mapping) else autoMatch(sourceId)
        }
    }

    /** Re-runs the title search even when the page already has an answer. */
    fun refresh() {
        val sourceId = _state.value.selectedSourceId ?: return
        viewModelScope.launch { autoMatch(sourceId) }
    }

    private suspend fun autoMatch(sourceId: String) {
        _state.update {
            it.copy(isSearching = true, isLoadingDetails = false, error = null, results = emptyList())
        }
        runCatching { manager.search(sourceId, media.title) }
            .onSuccess { results ->
                val first = results.firstOrNull()
                if (first == null) {
                    _state.update {
                        it.copy(
                            isSearching = false,
                            error = "No match for \"${media.title}\". Tap Wrong Title? and search."
                        )
                    }
                    return@onSuccess
                }
                val mapping = SourceMapping(
                    mediaId = media.id,
                    sourceId = sourceId,
                    animeId = first.id,
                    animeTitle = first.title,
                    coverUrl = first.coverUrl
                )
                manager.watchPreferences.saveMapping(mapping)
                _state.update {
                    it.copy(
                        isSearching = false,
                        mapping = mapping,
                        isLoadingDetails = true,
                        error = null
                    )
                }
                loadDetails(sourceId, first.id, mapping)
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isSearching = false,
                        error = error.message ?: "The source search failed."
                    )
                }
            }
    }

    private suspend fun loadDetails(
        sourceId: String,
        animeId: String,
        mapping: SourceMapping
    ) {
        _state.update { it.copy(isLoadingDetails = true, error = null) }
        runCatching { manager.details(sourceId, animeId) }
            .onSuccess { details ->
                _state.update {
                    it.copy(
                        isLoadingDetails = false,
                        details = details,
                        mapping = mapping,
                        error = null
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isLoadingDetails = false,
                        error = error.message ?: "The source did not return episodes."
                    )
                }
            }
    }

    class Factory(
        private val manager: ExtensionManager,
        private val media: WatchAnime
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WatchViewModel(manager, media) as T
    }
}
