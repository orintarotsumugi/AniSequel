package com.anisequel

import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.viewmodel.MediaDetailStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A detail fetch must be scoped to one AniList media namespace, and the cache that
 * holds the answer must be scoped with it.
 *
 * ## The bug
 *
 * AniList media ids are **unique per namespace, not globally**: `anime/1` and
 * `manga/1` are different entities that happen to share a number. The detail query
 * asked `Media(id: $id)` with no type, so AniList resolved the id against both and
 * answered with whichever it preferred, and the result was cached under the bare id.
 *
 * Two distinct failures fell out of that one mistake:
 *
 *  - **Wrong content.** Opening a manga rendered the anime sharing that id - its
 *    title, synopsis, cast and relations.
 *  - **Missing content.** Where the id matched nothing in the manga namespace,
 *    AniList answered 404, `require("That entry is no longer available on
 *    AniList.")` threw, and `MediaDetailStore` swallowed the failure *by design*.
 *    The page was left with the thin summary node: no cast, no staff, no
 *    relations. That is the "the relations and the character list don't work"
 *    report, and it came from a sheet that was flawless for every anime - which is
 *    exactly why it read as a missing feature rather than a wrong id.
 *
 * `MediaCoverKey` documents the same collision for cover art, where
 * `getMediaCoversQuery` already solves it with `Page.media(id:, type:)`. The detail
 * path is the one that was missed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaDetailNamespacingTest {

    /**
     * The headline regression: the same id in two namespaces must produce two
     * entries, not one entry served twice.
     *
     * Asserted through [MediaDetailStore] rather than the repository, because the
     * store is where a collision would survive a session - the repository's cache
     * is cleared wholesale at a soft cap, while the store keeps answering from
     * what it holds.
     */
    @Test
    fun `an anime and a manga sharing an id are two different details`() = runTest {
        val repository = RecordingRepository()
        val store = storeFor(repository, testScheduler)

        store.load(mediaId = 1, mediaType = "ANIME")
        store.load(mediaId = 1, mediaType = "MANGA")
        advanceUntilIdle()

        assertEquals(
            "the two namespaces must be fetched separately; a shared id resolved " +
                "once is how a manga opened to an anime's synopsis",
            listOf("ANIME" to 1, "MANGA" to 1),
            repository.requests.toList()
        )
        assertTrue(
            "both namespaces have to be requested, not deduplicated into one",
            repository.requests.size == 2
        )
    }

    /**
     * The cached read has to be namespaced too, not just the write.
     *
     * A store that fetches correctly but reads back by bare id still serves the
     * anime's node to the manga's card - and the fold that performs is invisible,
     * because the card simply keeps rendering a synopsis that is not its own.
     */
    @Test
    fun `a cached read for the other namespace misses`() = runTest {
        val repository = RecordingRepository()
        val store = storeFor(repository, testScheduler)

        store.load(mediaId = 1, mediaType = "ANIME")
        advanceUntilIdle()

        assertNotNull("the anime's own cache entry must be there", store.cached(1, "ANIME"))
        assertNull(
            "a manga must not be answered out of the anime's cache entry",
            store.cached(1, "MANGA")
        )
    }

    /** Re-opening a title must not spend a second request. */
    @Test
    fun `re-opening the same title reuses the cached detail`() = runTest {
        val repository = RecordingRepository()
        val store = storeFor(repository, testScheduler)

        store.load(mediaId = 7, mediaType = "MANGA")
        advanceUntilIdle()
        store.load(mediaId = 7, mediaType = "MANGA")
        advanceUntilIdle()

        assertEquals("a re-open must not refetch", 1, repository.requests.size)
    }

    /**
     * Whatever the store keeps has to be bounded, and the bound must not drop the
     * title that was just opened.
     *
     * ## The bug
     *
     * The fetch cache was LRU-bounded at [MediaDetailStore.MAX_ENTRIES], but the
     * published map the screens read was `_details.value + (id to node)` for the
     * life of the store. So the 251st title evicted an entry from one map and
     * left the node itself reachable from the other: the cap was nominal, and a
     * long session held every synopsis, cast and studio tree it had ever opened.
     * Nothing visibly breaks when this map drops an entry, which is why the leak
     * went unnoticed.
     *
     * The second assertion is the one that matters at the call site. A sheet reads
     * this map for the node it is showing, and it was published by opening - so
     * the entry just opened must survive its own publication even when that
     * publication is what pushes the map past the bound.
     */
    @Test
    fun `the published details map is bounded without dropping the newest entry`() = runTest {
        val repository = RecordingRepository()
        val store = storeFor(repository, testScheduler)
        val total = MediaDetailStore.MAX_ENTRIES + 1

        repeat(total) { index -> store.load(mediaId = index + 1, mediaType = "ANIME") }
        advanceUntilIdle()

        assertTrue(
            "the published map held ${store.details.value.size} nodes after $total " +
                "fetches; the fetch cache's bound has to bound what the screens keep " +
                "reachable too",
            store.details.value.size <= MediaDetailStore.MAX_ENTRIES
        )
        assertNotNull(
            "the entry just opened has to survive its own publication - the sheet " +
                "on screen reads it back by id",
            store.details.value[total]
        )
    }

    /**
     * The type has to be normalised on the way in, or the cache misses on a title it
     * already holds. AniList's enum is uppercase and so is what the parser produces,
     * but a hand-built node can carry either, and `MediaCoverKey` deliberately
     * normalises only in `graphqlType`.
     */
    @Test
    fun `the type is normalised so casing cannot cause a miss`() = runTest {
        val repository = RecordingRepository()
        val store = storeFor(repository, testScheduler)

        store.load(mediaId = 9, mediaType = "manga")
        advanceUntilIdle()
        store.load(mediaId = 9, mediaType = "MANGA")
        advanceUntilIdle()

        assertEquals(
            "lower and upper case are the same AniList type and must be one entry",
            1,
            repository.requests.size
        )
        assertNotNull(store.cached(9, "ManGa"))
    }

    /**
     * An untyped fetch still works, and lands in a namespace of its own so it can
     * never be served to a caller that asked for a specific one.
     *
     * Some summary nodes cannot supply a type; that is a reason for the untyped form
     * to exist, not a reason for the typed one to become unreliable.
     */
    @Test
    fun `an untyped fetch is kept apart from a typed one`() = runTest {
        val repository = RecordingRepository()
        val store = storeFor(repository, testScheduler)

        store.load(mediaId = 3, mediaType = null)
        advanceUntilIdle()

        assertNull("an untyped entry must not answer a typed lookup", store.cached(3, "ANIME"))
        assertNotNull("but it must answer its own", store.cached(3, null))
    }

    /**
     * `MediaCoverKey` is the type the caches are keyed on, and it is the type whose
     * own doc records why. Its equality has to include the type - a key that
     * compared only on the id would silently reintroduce the whole bug in the data
     * type that is supposed to prevent it.
     */
    @Test
    fun `the cache key distinguishes the namespaces`() {
        assertTrue(
            "MediaCoverKey compares on id alone, which is the bug this work set out " +
                "to fix - everything keyed on it inherits the collision",
            MediaCoverKey("ANIME", 1) != MediaCoverKey("MANGA", 1)
        )
        assertEquals(MediaCoverKey("ANIME", 1), MediaCoverKey("ANIME", 1))
    }

    // ---- helpers -----------------------------------------------------------------

    /**
     * A store whose scope shares the *test's* scheduler.
     *
     * That sharing is the whole reason this helper exists. `runTest` owns a
     * `TestScope` on a `StandardTestDispatcher`, and `MediaDetailStore` launches its
     * fetch into whatever scope it is handed - so a store built on its own
     * `TestScope(StandardTestDispatcher(testScheduler))` works, while one built on a
     * bare `CoroutineScope(StandardTestDispatcher())` is a *second* scheduler, and
     * `advanceUntilIdle()` will not move it. The coroutine stays parked, the request
     * list stays empty, and an assertion like "two namespaces produce two requests"
     * passes by comparing an empty list with an empty list.
     *
     * Takes the scheduler rather than the scope so the scope cannot outlive the test
     * it belongs to.
     */
    private fun storeFor(
        repository: AniListRepository,
        scheduler: TestCoroutineScheduler
    ): MediaDetailStore = MediaDetailStore(
        repository = repository,
        scope = TestScope(StandardTestDispatcher(scheduler)),
        isDemo = false
    )

    // ---- fake --------------------------------------------------------------------

    /**
     * Answers each request with a node named for the namespace it was asked for, so
     * a cross-namespace hit is visible in the returned data rather than inferred.
     *
     * [AniListRepository] has a handful of members with no default - `getViewer`,
     * `getUserByName`, `getUserAnimeList`, `getUserAnimeListByUsername`,
     * `getUserMangaList`, `addToPlanning`, `getDemoProfile`, `getDemoAnimeList` - and
     * they are listed here even though this test never calls them, because an
     * interface with a default for everything except those eight means a fake that
     * omits one does not compile. They all fail, rather than returning something, so
     * a test that *did* reach one by accident would fail loudly instead of quietly
     * answering.
     */
    private class RecordingRepository : AniListRepository {
        val requests = mutableListOf<Pair<String?, Int>>()

        override suspend fun getMediaDetail(mediaId: Int, mediaType: String?): Result<MediaNode> {
            requests += mediaType to mediaId
            val label = mediaType ?: "UNTYPED"
            return Result.success(
                MediaNode(
                    id = mediaId,
                    type = mediaType,
                    title = MediaTitle(english = "$label-$mediaId")
                )
            )
        }

        override suspend fun getViewer(): Result<ViewerProfile> = failure()
        override suspend fun getUserByName(userName: String): Result<ViewerProfile> = failure()

        override suspend fun getUserAnimeList(
            userId: Int,
            forceRefresh: Boolean
        ): Result<MediaListCollection> = failure()

        override suspend fun getUserAnimeListByUsername(
            userName: String,
            forceRefresh: Boolean
        ): Result<MediaListCollection> = failure()

        override suspend fun getUserMangaList(
            userId: Int,
            forceRefresh: Boolean
        ): Result<MediaListCollection> = failure()

        override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> =
            failure()

        override fun getDemoProfile(): ViewerProfile = ViewerProfile(1, "Demo", null, null)
        override fun getDemoAnimeList(): MediaListCollection = MediaListCollection(emptyList())

        private fun <T> failure(): Result<T> =
            Result.failure(UnsupportedOperationException("not used by this test"))
    }
}
