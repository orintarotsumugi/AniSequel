package com.example.ui.viewmodel

import com.example.data.model.MediaNode
import com.example.data.repository.AniListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The detail sheet's on-demand media detail, shared by the dashboard and the
 * profile.
 *
 * Both screens describe a show with the node their own query returned - a list
 * node, or a thin arriving/activity summary - and fetch the heavy fields from
 * AniList when the user opens it. One implementation so the request, the
 * loading flag and the "already have this" guard cannot drift between the two
 * ViewModels.
 *
 * ## Why a failure is swallowed
 *
 * A detail load only fills in a sheet that is already on screen with the
 * summary's own data. Interrupting the user with an error over a synopsis that
 * simply did not arrive is worse than the sheet quietly keeping what it had -
 * which is what the dashboard did before this was shared.
 */
class MediaDetailStore(
    private val repository: AniListRepository,
    private val scope: CoroutineScope,
    private val isDemo: Boolean,
    /**
     * Called with a node the first time it is fetched, so a list that shows the
     * same entry can fold the detail into it without re-fetching.
     */
    private val onLoaded: ((Int, MediaNode) -> Unit)? = null
) {
    /** Description, banner and studio per media, fetched only when opened. LRU-bounded. */
    private val cache = object : LinkedHashMap<Int, MediaNode>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, MediaNode>?): Boolean =
            size > MAX_ENTRIES
    }

    /**
     * Every node fetched this session, keyed by media id.
     *
     * A map rather than a single slot: the user can open one entry, close it and
     * open another before the first response lands, and a single slot would let
     * the slow response paint over the entry they are actually looking at.
     */
    private val _details = MutableStateFlow<Map<Int, MediaNode>>(emptyMap())
    val details: StateFlow<Map<Int, MediaNode>> = _details.asStateFlow()

    private val _loading = MutableStateFlow<Set<Int>>(emptySet())
    val loading: StateFlow<Set<Int>> = _loading.asStateFlow()

    /** The node already fetched for [mediaId], if any, without starting a request. */
    fun cached(mediaId: Int): MediaNode? = cache[mediaId]

    fun load(mediaId: Int) {
        cache[mediaId]?.let { known ->
            // Re-published on open so a re-opened entry has its detail again
            // without another request.
            if (_details.value[mediaId] !== known) {
                _details.value = _details.value + (mediaId to known)
            }
            return
        }
        // Demo data is already complete, and there is no session to fetch with.
        if (isDemo || mediaId in _loading.value) return

        _loading.value = _loading.value + mediaId
        scope.launch {
            try {
                repository.getMediaDetail(mediaId).onSuccess { detail ->
                    cache[mediaId] = detail
                    _details.value = _details.value + (mediaId to detail)
                    onLoaded?.invoke(mediaId, detail)
                }
                // A failed detail load is not worth interrupting the user over:
                // the sheet shows what the summary query already had.
            } finally {
                _loading.value = _loading.value - mediaId
            }
        }
    }

    /** Drops the cache; called when the collection it was built against changes. */
    fun clear() {
        cache.clear()
        _details.value = emptyMap()
        _loading.value = emptySet()
    }

    private companion object {
        const val MAX_ENTRIES = 250
    }
}
