package com.anisequel

import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.viewmodel.MediaDetailStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The friends row has to age like the detail it sits beside.
 *
 * ## The bug
 *
 * The Info page is kept fresh by one loop: it re-asks for the detail every ten
 * minutes while the page is resumed, and its own doc says the page stays fresh.
 * Friends were loaded once when the sheet opened and then never again - the store
 * published its cache entry and returned, with no freshness window at all, so the
 * first successful answer was the answer for the whole session.
 *
 * What that looked like: a page left open showed a live synopsis, a live score and
 * a friends row frozen at whatever it said when the sheet opened. A friend who
 * started the show while the reader was looking at it never appeared, and reopening
 * the same title later in the session still showed the old row - the repository's
 * own cache could not correct it either, because its time-to-live is the reader's
 * list interval and can be longer than this page's ten minutes.
 *
 * These tests pin the three halves of the fix: the window is a window (an answer a
 * moment old is reused), it expires (an older answer is replaced), and the
 * replacement is a background refresh (the old row keeps drawing, no spinner, and
 * the repository is asked to bypass its own cache so the request is not answered
 * with the very list it is replacing).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaFriendsRefreshTest {

    private val key = MediaCoverKey("ANIME", 1)

    @Test
    fun `friends are not re-asked inside the detail window`() = runTest {
        val clock = TestClock()
        val repository = FriendsRepository()
        val store = storeFor(repository, testScheduler, clock)

        store.loadFriends(1, "ANIME")
        advanceUntilIdle()
        assertEquals(1, repository.friendsRequests)

        clock.advanceBy(MediaDetailStore.DETAIL_REFRESH_INTERVAL_MILLIS - 1)
        store.loadFriends(1, "ANIME")
        advanceUntilIdle()

        assertEquals("a list fetched a moment ago still answers the card", 1, repository.friendsRequests)
    }

    @Test
    fun `an older answer is replaced once the window passes`() = runTest {
        val clock = TestClock()
        val repository = FriendsRepository()
        val store = storeFor(repository, testScheduler, clock)

        store.loadFriends(1, "ANIME")
        advanceUntilIdle()
        assertEquals(listOf("First"), store.friends.value[key]?.map { it.name })

        clock.advanceBy(MediaDetailStore.DETAIL_REFRESH_INTERVAL_MILLIS + 1)
        repository.answer = listOf(friend("Second"))
        store.loadFriends(1, "ANIME")
        advanceUntilIdle()

        assertEquals(2, repository.friendsRequests)
        assertEquals(
            "the refresh is answered from the repository's own cache unless it is " +
                "told to bypass it - and that cache can outlive this window",
            listOf(false, true),
            repository.forceRefreshes
        )
        assertEquals(
            "the new answer replaces the frozen one",
            listOf("Second"),
            store.friends.value[key]?.map { it.name }
        )
    }

    @Test
    fun `the old list stays on screen while the refresh is in the air`() = runTest {
        val clock = TestClock()
        val pending = CompletableDeferred<Result<List<MediaFriend>>>()
        val repository = FriendsRepository()
        val store = storeFor(repository, testScheduler, clock)

        store.loadFriends(1, "ANIME")
        advanceUntilIdle()

        clock.advanceBy(MediaDetailStore.DETAIL_REFRESH_INTERVAL_MILLIS + 1)
        repository.pending = pending
        store.loadFriends(1, "ANIME")
        runCurrent()

        assertEquals(
            "a refresh must not blank the card it is refreshing",
            listOf("First"),
            store.friends.value[key]?.map { it.name }
        )
        assertFalse(
            "the card already has content; a spinner over it is the flash the " +
                "detail half avoids for the same reason",
            key in store.friendsLoading.value
        )

        pending.complete(Result.success(listOf(friend("Second"))))
        advanceUntilIdle()
        assertEquals(listOf("Second"), store.friends.value[key]?.map { it.name })
    }

    // ---- helpers -----------------------------------------------------------------

    /**
     * A store whose scope shares the test's scheduler, for the reason
     * `MediaDetailNamespacingTest.storeFor` documents: a second scheduler is a store
     * whose coroutines `advanceUntilIdle` cannot move, and every assertion about a
     * request that never happened then passes by comparing nothing with nothing.
     */
    private fun storeFor(
        repository: AniListRepository,
        scheduler: TestCoroutineScheduler,
        clock: TestClock
    ): MediaDetailStore = MediaDetailStore(
        repository = repository,
        scope = TestScope(StandardTestDispatcher(scheduler)),
        isDemo = false,
        now = clock::millis
    )

    private fun friend(name: String) = MediaFriend(
        userId = name.hashCode(),
        name = name,
        avatarUrl = null,
        score = null,
        progress = 0,
        progressVolumes = null
    )

    /** A clock the test moves by hand; see [MediaDetailStore]'s `now` parameter. */
    private class TestClock {
        private var at = 1_000L

        fun millis(): Long = at

        fun advanceBy(millis: Long) {
            at += millis
        }
    }

    /**
     * Counts the friends requests and returns whatever the test has scripted.
     *
     * The eight members with no default are listed because [AniListRepository]
     * declares them abstract even though this test never calls one - a fake that
     * omitted one would not compile, and they fail loudly rather than answering if
     * a future test does reach one.
     */
    private class FriendsRepository(
        var answer: List<MediaFriend> = listOf(MediaFriend(1, "First", null, null, 0, null)),
        var pending: CompletableDeferred<Result<List<MediaFriend>>>? = null
    ) : AniListRepository {
        var friendsRequests = 0
            private set

        val forceRefreshes = mutableListOf<Boolean>()

        override suspend fun getMediaFriends(
            mediaId: Int,
            mediaType: String?,
            forceRefresh: Boolean
        ): Result<List<MediaFriend>> {
            friendsRequests++
            forceRefreshes += forceRefresh
            pending?.let { return it.await() }
            return Result.success(answer)
        }

        override suspend fun getMediaDetail(mediaId: Int, mediaType: String?): Result<MediaNode> =
            failing()

        override suspend fun getViewer(): Result<ViewerProfile> = failing()

        override suspend fun getUserByName(userName: String): Result<ViewerProfile> = failing()

        override suspend fun getUserAnimeList(
            userId: Int,
            forceRefresh: Boolean
        ): Result<MediaListCollection> = failing()

        override suspend fun getUserAnimeListByUsername(
            userName: String,
            forceRefresh: Boolean
        ): Result<MediaListCollection> = failing()

        override suspend fun getUserMangaList(
            userId: Int,
            forceRefresh: Boolean
        ): Result<MediaListCollection> = failing()

        override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> = failing()

        override fun getDemoProfile(): ViewerProfile = ViewerProfile(1, "Demo", null, null)

        override fun getDemoAnimeList(): MediaListCollection = MediaListCollection(emptyList())

        private fun <T> failing(): Result<T> =
            Result.failure(UnsupportedOperationException("not used by this test"))
    }
}
