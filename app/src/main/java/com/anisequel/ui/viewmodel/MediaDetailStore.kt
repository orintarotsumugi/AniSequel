package com.anisequel.ui.viewmodel

import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaNode
import com.anisequel.data.repository.AniListRepository
import java.util.Locale
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
    /**
     * Description, banner and studio per media, fetched only when opened. LRU-bounded.
     *
     * Keyed on [MediaCoverKey], not a bare id: AniList ids are unique per media
     * namespace rather than globally, so a manga's detail was served out of the
     * anime's entry whenever the two happened to share an id - a title that opened
     * to a synopsis and cast belonging to something else. What is *published* is
     * still keyed by the id alone, because that is what the screens look up by and
     * one screen only ever holds one title.
     */
    private val cache = object : LinkedHashMap<MediaCoverKey, MediaNode>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MediaCoverKey, MediaNode>?): Boolean =
            size > MAX_ENTRIES
    }

    /**
     * Every node fetched this session, keyed by media id.
     *
     * A map rather than a single slot: the user can open one entry, close it and
     * open another before the first response lands, and a single slot would let
     * the slow response paint over the entry they are actually looking at.
     *
     * Keyed by the **id alone**, unlike [cache]. This one is what the screens read
     * and a screen only ever holds one title, so namespacing it would buy nothing
     * and would cost every lookup a second argument. The namespacing that matters
     * is on the fetch, where a wrong hit would persist for the whole session.
     */
    private val _details = MutableStateFlow<Map<Int, MediaNode>>(emptyMap())
    val details: StateFlow<Map<Int, MediaNode>> = _details.asStateFlow()

    /**
     * Namespaces currently being fetched, so the guard above is per-title rather
     * than per-number.
     *
     * A plain set rather than a `StateFlow`: it is only read synchronously inside
     * [load] and written inside the launched coroutine, and making it observable
     * would mean the screens' spinner is driven by a flow that has to be mapped
     * and scoped to answer a question - "is this one title loading" - that a set of
     * ids answers directly. [loading] stays the observable, and stays keyed by id,
     * because that is what its consumers look up by.
     */
    private val inFlight = mutableSetOf<MediaCoverKey>()

    private val _loading = MutableStateFlow<Set<Int>>(emptySet())

    /**
     * Which media ids are being fetched, for the screens' spinner.
     *
     * Keyed by id rather than by [inFlight]'s key, because that is what the
     * consumers ask - "is the title I have open still loading" - and a screen only
     * ever holds one title. Two namespaces sharing an id both appear here, which is
     * harmless for a spinner and is exactly the collision the fetch guard no longer
     * has.
     */
    val loading: StateFlow<Set<Int>> = _loading.asStateFlow()

    /**
     * The node already fetched for this title, if any, without starting a request.
     *
     * Namespaced for the same reason [load] is - see [cache]. A card reading this
     * for a manga must not be answered out of the anime's entry when the two share
     * an id, because the fold it performs is invisible: the card just quietly keeps
     * rendering the other title's synopsis.
     */
    fun cached(mediaId: Int, mediaType: String? = null): MediaNode? =
        cache[cacheKey(mediaId, mediaType)]

    fun load(mediaId: Int, mediaType: String? = null) {
        val key = cacheKey(mediaId, mediaType)
        cache[key]?.let { known ->
            // Re-published on open so a re-opened entry has its detail again
            // without another request.
            if (_details.value[mediaId] !== known) {
                _details.value = _details.value + (mediaId to known)
            }
            return
        }
        // Demo data is already complete, and there is no session to fetch with.
        //
        // The in-flight guard is keyed the same way [cache] is. It used to be a
        // `Set<Int>`, which meant a second open for a *different* namespace sharing
        // an id was dropped as "already loading" while the first was in flight - so
        // opening `manga/1` and then, before it landed, `anime/1`, silently kept
        // the manga's detail for both. The same collision as everywhere else here,
        // in the one place that decides whether a fetch happens at all.
        if (isDemo || key in inFlight) return

        inFlight += key
        _loading.value = _loading.value + mediaId
        scope.launch {
            try {
                repository.getMediaDetail(mediaId, mediaType).onSuccess { detail ->
                    cache[key] = detail
                    _details.value = _details.value + (mediaId to detail)
                    onLoaded?.invoke(mediaId, detail)
                }
                // A failed detail load is not worth interrupting the user over:
                // the sheet shows what the summary query already had.
            } finally {
                inFlight -= key
                // Only drop the spinner when *no* namespace for this id is still
                // fetching. Dropping it on the first to finish would hide the
                // spinner while the second was still on the wire, and the screen
                // would show a half-filled page as though it were complete.
                if (inFlight.none { it.id == mediaId }) {
                    _loading.value = _loading.value - mediaId
                }
            }
        }
    }

    /**
     * One place that turns a caller's id and type into a cache key.
     *
     * Two call sites ([load] and [cached]) and a shared [cache] between them: written
     * out twice, the two could normalise the type differently - one uppercasing and
     * one not - and the cache would then miss on a title it already held, spending a
     * second request to re-fetch what it had. `MediaCoverKey` uppercases in
     * `graphqlType` rather than in the constructor precisely because it cannot be
     * relied on to, so the normalisation has to be explicit here.
     */
    private fun cacheKey(mediaId: Int, mediaType: String?): MediaCoverKey =
        MediaCoverKey(mediaType?.uppercase(Locale.ROOT) ?: UNSPECIFIED, mediaId)

    /** Drops the cache; called when the collection it was built against changes. */
    fun clear() {
        cache.clear()
        // Cleared rather than left to drain: a launch that is still in flight for a
        // collection that no longer exists has nothing to fold its answer into, and
        // leaving it would keep a spinner turning over a list the reader has left.
        // The launched coroutines still clear their own key on the way out.
        inFlight.clear()
        _details.value = emptyMap()
        _loading.value = emptySet()
    }

    private companion object {
        const val MAX_ENTRIES = 250

        /**
         * The cache namespace for a title fetched without a known type.
         *
         * AniList's `MediaType` enum has an `UNSPECIFIED` member and reusing its
         * spelling means an untyped fetch can never be served to a caller that asked
         * for a specific one.
         */
        const val UNSPECIFIED = "UNSPECIFIED"
    }
}
