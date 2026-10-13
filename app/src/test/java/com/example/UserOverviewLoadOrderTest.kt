package com.example

import com.example.data.model.MediaCover
import com.example.data.model.MediaCoverKey
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaListEntryItem
import com.example.data.model.MediaListGroup
import com.example.data.model.MediaNode
import com.example.data.model.MediaTitle
import com.example.data.model.SimpleMediaListEntry
import com.example.data.model.UserOverview
import com.example.data.model.ViewerProfile
import com.example.data.repository.AniListRepository
import com.example.ui.viewmodel.UserOverviewUiState
import com.example.ui.viewmodel.UserOverviewViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The order the profile screen's two fetches are *published* in.
 *
 * ## The regression this pins
 *
 * The overview and the media list used to be awaited together before anything was
 * shown, on the argument that a page with a name and a "7 Watching" is worse than
 * a spinner. The list is the expensive half by an order of magnitude - one request
 * per 500 entries, chunk-merged - and only the Stats and Library tabs read it, so
 * the cost of that ordering was the whole download in front of a bio that had
 * already arrived.
 *
 * The fix publishes the overview immediately and merges the list in afterwards,
 * which is only *correct* because of the flag: without `isLibraryLoading` the
 * Stats and Library tabs would read an empty list as "this account tracks
 * nothing", and the summary card would print zeroes for somebody with 900
 * entries. Both halves are asserted here, because the second one is the reason the
 * first is safe - a future change that awaits both again fails the first test, and
 * one that publishes early without the flag fails the second.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserOverviewLoadOrderTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the profile is published before the list arrives, flagged as still counting`() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<Unit>()
            val viewModel = UserOverviewViewModel(
                GatedListRepository(listGate = gate),
                userId = 1
            )

            advanceUntilIdle()

            val loading = viewModel.uiState.value as UserOverviewUiState.Success
            assertEquals("Bocchi", loading.username)
            assertTrue(
                "the list must not be awaited before the profile is shown",
                loading.isLibraryLoading
            )
            assertTrue(
                "nothing may claim to be a tally before the list has landed",
                loading.libraryAnime.isEmpty()
            )

            gate.complete(Unit)
            advanceUntilIdle()

            val settled = viewModel.uiState.value as UserOverviewUiState.Success
            assertFalse("the list arrived, so nothing is pending", settled.isLibraryLoading)
            assertEquals(1, settled.libraryAnime.size)
            assertEquals(11, settled.libraryAnime.single().media.id)
        }

    /**
     * The bio's cover batch must not be queued behind the chunked list download.
     *
     * `getUserAnimeList` is the app's single most expensive query - chunked, one
     * request per 500 entries, sequentially. `loadMediaCovers` used to be called
     * *after* `listDeferred.await()`, so every AniList card in a "hall of fame"
     * bio sat on its placeholder icon for the whole length of that download and
     * then popped a poster in, and the comment above that call said the opposite
     * of what the code did.
     *
     * The assertion is made while the list is still suspended, which is the only
     * moment the ordering is observable: once the gate opens both have landed and
     * the map looks identical either way.
     */
    @Test
    fun `a bio's cover batch is requested while the list download is still in flight`() =
        runTest(testDispatcher) {
            val listGate = CompletableDeferred<Unit>()
            // Named so the assertion can read what the *repository* was asked for.
            // The ViewModel exposes the answer (`mediaCovers`) but not the question,
            // and this test is about the question - the batch had to be issued while
            // the list was still suspended, not merely have landed by the end.
            val repository = GatedListRepository(
                listGate = listGate,
                bio = "**Favourites**\n\nhttps://anilist.co/anime/128893"
            )
            val viewModel = UserOverviewViewModel(repository, userId = 1)

            // Idles with the list still suspended: `gate.await()` never completes,
            // so the scheduler reaches idle with the download in flight, which is
            // the only moment the ordering is observable.
            advanceUntilIdle()

            val settled = viewModel.uiState.value as UserOverviewUiState.Success
            assertTrue("the list must still be in flight", settled.isLibraryLoading)
            assertTrue("the list has not landed yet", settled.libraryAnime.isEmpty())
            assertEquals(
                "covers must be requested without waiting for the list download",
                listOf(MediaCoverKey("ANIME", 128893)),
                repository.requestedCoverKeys
            )

            listGate.complete(Unit)
            advanceUntilIdle()
        }

    /**
     * Covers fetched before the list lands must survive it.
     *
     * The state is published twice - once with the list still loading, once with
     * it - so the cover map has to be owned by something other than the final
     * publish. Publishing the list-less state over the top of the first would
     * empty it and put every card back on its placeholder.
     */
    @Test
    fun `covers fetched during the list download are still there once it lands`() =
        runTest(testDispatcher) {
            val listGate = CompletableDeferred<Unit>()
            val viewModel = UserOverviewViewModel(
                GatedListRepository(
                    listGate = listGate,
                    bio = "https://anilist.co/anime/128893"
                ),
                userId = 1
            )
            advanceUntilIdle()
            assertTrue(viewModel.mediaCovers.value.isNotEmpty())

            listGate.complete(Unit)
            advanceUntilIdle()

            assertTrue(
                "the cover must survive the publish that carries the list",
                viewModel.mediaCovers.value.containsKey(MediaCoverKey("ANIME", 128893))
            )
        }

    @Test
    fun `a failed list fetch still shows the profile and stops claiming to be loading`() =
        runTest(testDispatcher) {
            val viewModel = UserOverviewViewModel(
                GatedListRepository(listGate = null),
                userId = 1
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value as UserOverviewUiState.Success
            assertEquals("Bocchi", state.username)
            assertFalse(
                "a failed list is an answer: the screen must not spin forever",
                state.isLibraryLoading
            )
            assertTrue(state.libraryAnime.isEmpty())
        }

    /**
     * A repository whose list fetch waits on [listGate].
     *
     * A gate rather than a delay: the point of the test is that the profile is
     * published while the list is *unresolved*, and only a suspended call can hold
     * that state still for the assertions. A null gate fails the fetch instead,
     * which is the other half of the contract.
     */
    private class GatedListRepository(
        private val listGate: CompletableDeferred<Unit>?,
        /** The bio the fake profile carries. Null for the tests that have none. */
        private val bio: String? = null
    ) : AniListRepository {

        /**
         * Every key the profile asked a cover for, in order.
         *
         * Recorded rather than inferred from the published state, because the
         * question these tests ask is *when* the batch was requested - and by the
         * time the list lands, the map looks the same either way.
         */
        val requestedCoverKeys = mutableListOf<MediaCoverKey>()

        override suspend fun getViewer(): Result<ViewerProfile> = failure()
        override suspend fun getUserByName(userName: String): Result<ViewerProfile> = failure()

        override suspend fun getUserAnimeList(
            userId: Int,
            forceRefresh: Boolean
        ): Result<MediaListCollection> {
            val gate = listGate ?: return Result.failure(RuntimeException("no list for you"))
            gate.await()
            return Result.success(
                MediaListCollection(
                    lists = listOf(
                        MediaListGroup(
                            name = "Completed",
                            status = "COMPLETED",
                            entries = listOf(
                                MediaListEntryItem(
                                    status = "COMPLETED",
                                    progress = 12,
                                    media = MediaNode(
                                        id = 11,
                                        title = MediaTitle(english = "Bocchi the Rock!")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }

        override suspend fun getUserAnimeListByUsername(
            userName: String,
            forceRefresh: Boolean
        ): Result<MediaListCollection> = getUserAnimeList(0, forceRefresh)

        override suspend fun getMediaDetail(mediaId: Int): Result<MediaNode> = failure()
        override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> = failure()

        override suspend fun getUserOverview(
            userId: Int?,
            userName: String?,
            forceRefresh: Boolean
        ): Result<UserOverview> = Result.success(
            // `bio` is null for the tests that have none, which skips the cover
            // batch: those are about the two fetches that build the page. The two
            // ordering tests pass one so the batch exists to be observed.
            UserOverview(id = 1, name = "Bocchi", about = bio)
        )

        override suspend fun getMediaCovers(
            keys: List<MediaCoverKey>
        ): Result<Map<MediaCoverKey, MediaCover>> {
            requestedCoverKeys += keys
            return Result.success(
                keys.associateWith { MediaCover(url = "https://img.anili.st/${it.type}/${it.id}.jpg") }
            )
        }

        override fun getDemoProfile(): ViewerProfile = ViewerProfile(1, "Demo", null, null)
        override fun getDemoAnimeList(): MediaListCollection = MediaListCollection(emptyList())

        private fun <T> failure(): Result<T> =
            Result.failure(UnsupportedOperationException("not used by this test"))
    }
}
