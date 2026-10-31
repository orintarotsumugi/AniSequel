package com.anisequel.ui.viewmodel

import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaNode
import com.anisequel.data.repository.AniListRepository
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The detail sheet's on-demand media detail.
 *
 * Every screen describes a show with the node its own query returned - a list
 * node, a thin arriving/activity summary, or a pinned favourite - and fetches the
 * heavy fields from AniList when the user opens it. One implementation so the
 * request, the loading flag and the "already have this" guard cannot drift between
 * callers; one *instance* for the Library, the Profile and the studio page, held by
 * `MediaDetailHost`, so the same title is the same page everywhere. The dashboard
 * holds its own because its store also folds each answer into the cards behind the
 * sheet.
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
     * Every node fetched this session, keyed by media id, under the same bound as
     * [cache].
     *
     * A map rather than a single slot: the user can open one entry, close it and
     * open another before the first response lands, and a single slot would let
     * the slow response paint over the entry they are actually looking at.
     *
     * Keyed by the **id alone**, unlike [cache]. This one is what the screens read
     * and a screen only ever holds one title, so namespacing it would buy nothing
     * and would cost every lookup a second argument. The namespacing that matters
     * is on the fetch, where a wrong hit would persist for the whole session.
     *
     * Bounded the same way [cache] is, and for a while it was not: publishing used
     * to be `_details.value + (id to node)`, so the 251st title evicted an entry
     * from the fetch cache and left the node itself reachable from here - the LRU
     * cap was nominal, and a long session held every synopsis, cast and studio
     * tree it had ever opened. Nothing visibly breaks when this map drops an
     * entry, which is exactly why the leak went unnoticed.
     */
    private val published = object : LinkedHashMap<Int, MediaNode>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, MediaNode>?): Boolean =
            size > MAX_ENTRIES
    }

    private val _details = MutableStateFlow<Map<Int, MediaNode>>(emptyMap())

    /**
     * The nodes the screens read: a snapshot of [published].
     *
     * A snapshot rather than the map itself because `MutableStateFlow` compares
     * values by equality, and an access-ordered `LinkedHashMap` mutating in place
     * would hand every collector the same instance without an emission to say it
     * changed.
     */
    val details: StateFlow<Map<Int, MediaNode>> = _details.asStateFlow()

    /**
     * Publishes [detail] for [mediaId], refreshing its recency in [published].
     *
     * A write counts as an access on an access-ordered map, so re-opening an
     * entry refreshes it even when the snapshot already carries the node - which
     * is what keeps the title on screen the last thing the bound would drop. The
     * snapshot is only rebuilt when the screens are actually missing the node.
     */
    private fun publish(mediaId: Int, detail: MediaNode) {
        published[mediaId] = detail
        if (_details.value[mediaId] !== detail) {
            _details.value = LinkedHashMap(published)
        }
    }

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
     * Followed users' entries per title, fetched separately from the detail.
     *
     * ## Why it is not part of [load]
     *
     * A detail that is already cached returns early, and friends are not part of
     * the detail: the detail query is public, `Page.mediaList(isFollowing:)` is
     * viewer-scoped and can fail on its own (signed out, or the request budget
     * spent). Folding the two together would mean a cached title never asks for
     * friends again, and a failed friends call throwing away a detail that
     * arrived.
     *
     * ## Why the fetch cache is namespaced and the published map is not
     *
     * Same split as [cache] and [published], for the same reason: the fetch cache
     * must never serve `manga/1` out of `anime/1`, but the sheet looks its title
     * up by the id its own node carries, and a screen only ever holds one title.
     * Re-opening a title re-publishes its list synchronously from the fetch cache,
     * so the only wrong state is the frame before that happens - which is exactly
     * what [details] already accepts.
     */
    private val friendsCache = object : LinkedHashMap<MediaCoverKey, List<MediaFriend>>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<MediaCoverKey, List<MediaFriend>>?
        ): Boolean = size > MAX_ENTRIES
    }

    private val friendsInFlight = mutableSetOf<MediaCoverKey>()

    private val _friends = MutableStateFlow<Map<Int, List<MediaFriend>>>(emptyMap())

    /** Friends' entries the screens read, keyed by media id. See [friendsCache]. */
    val friends: StateFlow<Map<Int, List<MediaFriend>>> = _friends.asStateFlow()

    private fun publishFriends(mediaId: Int, friends: List<MediaFriend>) {
        if (_friends.value[mediaId] !== friends) {
            _friends.value = _friends.value + (mediaId to friends)
        }
    }

    /**
     * Fetches the followed users who have this title on their list.
     *
     * Called alongside [load] rather than by it - see [friends]. A failure is
     * swallowed for the same reason a detail failure is: the page is already
     * complete without this card, and an error over "nobody you follow has seen
     * this" would be a lie about which of the two happened.
     */
    fun loadFriends(mediaId: Int, mediaType: String? = null) {
        val key = cacheKey(mediaId, mediaType)
        friendsCache[key]?.let { known ->
            publishFriends(mediaId, known)
            return
        }
        if (isDemo || key in friendsInFlight) return

        friendsInFlight += key
        val startedAtGeneration = generation
        scope.launch {
            try {
                repositoryResult {
                    repository.getMediaFriends(mediaId, mediaType)
                }.onSuccess { list ->
                    // Dropped when the store was cleared while this was on the wire,
                    // because the answer belongs to a session that has ended - see
                    // [generation] for what filing it anyway showed the next reader.
                    if (startedAtGeneration == this@MediaDetailStore.generation) {
                        friendsCache[key] = list
                        publishFriends(mediaId, list)
                    }
                }
                // A failed friends load draws no card: see above.
            } finally {
                friendsInFlight -= key
            }
        }
    }

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
            publish(mediaId, known)
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
        val startedAtGeneration = generation
        scope.launch {
            try {
                // A repository that throws rather than returning a classified
                // failure used to escape this launch: the `finally` below ran,
                // and then the exception reached the thread's handler and closed
                // the app *while the detail sheet was opening*. `repositoryResult`
                // turns it into the failure this code already swallows, which is
                // the behaviour the sheet was designed around - it keeps drawing
                // the summary it already had.
                repositoryResult {
                    repository.getMediaDetail(mediaId, mediaType)
                }.onSuccess { detail ->
                    // Written only if the store has not been cleared since the
                    // request went out - see [generation]. The `finally` below still
                    // cleans up either way.
                    if (startedAtGeneration == this@MediaDetailStore.generation) {
                        cache[key] = detail
                        publish(mediaId, detail)
                        // The host's fold is a callback, and `onSuccess` rethrows
                        // anything its lambda throws - so a listener that fails would
                        // still have taken the fetch, and the launch, with it. The
                        // detail is already published at this point; what the host
                        // does with it is its own problem.
                        try {
                            onLoaded?.invoke(mediaId, detail)
                        } catch (_: Exception) {
                            // Deliberately swallowed: see above.
                        }
                    }
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

    /**
     * Bumped by [clear] and checked by every launch before it writes back.
     *
     * Without it a fetch that started before the clear lands after it and re-files
     * its answer in the cache the clear emptied. For the detail half that is merely a
     * stale card; for `friends` it is the previous account's friend list filed under
     * one title, because the query is `isFollowing`-scoped and therefore
     * viewer-scoped - the sheet reopened after a sign-out then reads other people's
     * watching history back out of [friendsCache] for the rest of the session. The
     * repository settles the same question with `listEpoch`; this store needed its
     * own copy of that answer.
     */
    @Volatile
    private var generation = 0

    /** Drops the cache; called when the collection it was built against changes. */
    fun clear() {
        // Before anything is emptied, so a launch that observes the new value can
        // never write into the cache this call is clearing.
        generation++
        cache.clear()
        // Cleared rather than left to drain: a launch that is still in flight for a
        // collection that no longer exists has nothing to fold its answer into, and
        // leaving it would keep a spinner turning over a list the reader has left.
        // The launched coroutines still clear their own key on the way out.
        inFlight.clear()
        published.clear()
        _details.value = emptyMap()
        _loading.value = emptySet()
        friendsCache.clear()
        friendsInFlight.clear()
        _friends.value = emptyMap()
    }

    /**
     * Internal rather than private so `MediaDetailNamespacingTest` can name the
     * bound it asserts instead of repeating the literal - raising the cap should
     * change the test's arithmetic, not its meaning.
     */
    internal companion object {
        /**
         * The bound on both detail caches: the namespaced fetch cache and the
         * published map the screens read.
         */
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
