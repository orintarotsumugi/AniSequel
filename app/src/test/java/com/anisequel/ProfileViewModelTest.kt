package com.anisequel

import com.anisequel.data.model.ActivityLikeState
import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.ProfileActivity
import com.anisequel.data.model.MediaCoverImage
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.UserActivityPage
import com.anisequel.data.model.UserOverview
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.viewmodel.SocialList
import com.anisequel.ui.viewmodel.UserActivityViewModel
import com.anisequel.ui.viewmodel.UserSocialViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeProfileRepository(
        var activityPages: Map<Int, List<ProfileActivity>> = emptyMap(),
        /**
         * The `pageInfo.hasNextPage` each page answers with.
         *
         * A page with no entry derives it from its size, which is what the API
         * does in the common case - but the whole reason the ViewModel reads this
         * field is that a page can arrive short and still have a successor, so the
         * test that matters sets it explicitly.
         */
        var pageHasNext: Map<Int, Boolean> = emptyMap(),
        /** Pages this repository refuses, so a failure path can be exercised. */
        var failPages: Set<Int> = emptySet(),
        var followersResult: List<FollowUser> = emptyList(),
        var followingResult: List<FollowUser> = emptyList(),
        /**
         * Make one half of the pair fail.
         *
         * Both exist because the tab fetches the two lists in one job and shows one
         * of them, so the case that matters is *one* answer arriving and the other not
         * - which is the state that used to replace a readable grid with an error for
         * a list nobody had asked to see.
         */
        var followersFail: Boolean = false,
        var followingFail: Boolean = false,
        var likeResult: Result<ActivityLikeState> =
            Result.success(ActivityLikeState(isLiked = true, likeCount = 1)),
        var repliesResult: Result<List<ActivityReply>> = Result.success(emptyList()),
        var postReplyResult: Result<ActivityReply> =
            Result.success(ActivityReply(id = 1, text = "posted"))
    ) : AniListRepository {
        var activityFetchCount = 0
        var followersFetchCount = 0
        var followingFetchCount = 0
        var likeFetchCount = 0
        var repliesFetchCount = 0
        var postReplyFetchCount = 0

        /**
         * The write signal `AniListRepositoryImpl` publishes on every mutation.
         *
         * Exposed as a plain property so a test can bump it the way a write does,
         * and read back through the interface property below. Starts at the same
         * zero the real repository does: the ViewModel treats the first emission
         * as a baseline, not as a write.
         */
        val mutations = MutableStateFlow(0L)

        override val listMutationVersion: StateFlow<Long> get() = mutations

        override suspend fun getViewer(): Result<ViewerProfile> = Result.failure(UnsupportedOperationException())
        override suspend fun getUserByName(userName: String): Result<ViewerProfile> = Result.failure(UnsupportedOperationException())
        override suspend fun getUserAnimeList(userId: Int, forceRefresh: Boolean): Result<MediaListCollection> = Result.success(MediaListCollection(emptyList()))
        override suspend fun getUserAnimeListByUsername(userName: String, forceRefresh: Boolean): Result<MediaListCollection> = Result.success(MediaListCollection(emptyList()))
        override suspend fun getMediaDetail(mediaId: Int, mediaType: String?): Result<MediaNode> = Result.failure(UnsupportedOperationException())
        override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> = Result.failure(UnsupportedOperationException())
        override fun getDemoProfile() = ViewerProfile(1, "Demo", null, null)
        override fun getDemoAnimeList() = MediaListCollection(emptyList())

        override suspend fun getUserActivity(
            userId: Int,
            page: Int,
            forceRefresh: Boolean
        ): Result<UserActivityPage> {
            activityFetchCount++
            // `failPages` is mutable so a test can fail a page and then clear it to
            // model a retry succeeding - the two halves of "the feed has to be
            // resumable", which is the thing being pinned.
            if (page in failPages) {
                return Result.failure(RuntimeException("AniList is rate limiting right now."))
            }
            val list = activityPages[page] ?: emptyList()
            return Result.success(
                UserActivityPage(
                    activities = list,
                    hasMore = pageHasNext[page] ?: (list.size >= 25)
                )
            )
        }

        override suspend fun getUserFollowers(
            userId: Int,
            forceRefresh: Boolean
        ): Result<List<FollowUser>> {
            followersFetchCount++
            if (followersFail) return Result.failure(AniListException(AniListErrorKind.SERVER, "followers unavailable"))
            return Result.success(followersResult)
        }

        override suspend fun getUserFollowing(
            userId: Int,
            forceRefresh: Boolean
        ): Result<List<FollowUser>> {
            followingFetchCount++
            if (followingFail) return Result.failure(AniListException(AniListErrorKind.SERVER, "following unavailable"))
            return Result.success(followingResult)
        }

        override suspend fun toggleActivityLike(activityId: Int): Result<ActivityLikeState> {
            likeFetchCount++
            return likeResult
        }

        override suspend fun getActivityReplies(activityId: Int): Result<List<ActivityReply>> {
            repliesFetchCount++
            return repliesResult
        }

        override suspend fun postActivityReply(activityId: Int, text: String): Result<ActivityReply> {
            postReplyFetchCount++
            return postReplyResult
        }
    }

    @Test
    fun `UserActivityViewModel pagination loads next page when hasMore is true`() = runTest(testDispatcher) {
        val page1 = List(25) { createActivity(it + 1) }
        val page2 = List(10) { createActivity(it + 26) }
        val repository = FakeProfileRepository(
            activityPages = mapOf(1 to page1, 2 to page2)
        )

        val viewModel = UserActivityViewModel(repository, userId = 1)
        viewModel.loadFirstPage()
        advanceUntilIdle()

        val state1 = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(25, state1.activities.size)
        assertTrue("AniList reported a next page, so hasMore must be true", state1.hasMore)
        assertEquals(1, repository.activityFetchCount)

        // Calling loadNextPage should now fetch page 2
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals("Page 2 fetch should have been invoked", 2, repository.activityFetchCount)
        val state2 = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(35, state2.activities.size)
        assertFalse("AniList reported no next page, so hasMore must be false", state2.hasMore)

        // Calling loadNextPage again when hasMore is false should not fetch
        viewModel.loadNextPage()
        advanceUntilIdle()
        assertEquals("Should not fetch when hasMore is false", 2, repository.activityFetchCount)
    }

    @Test
    fun `UserSocialViewModel switches between followers and following instantly without re-fetching`() = runTest(testDispatcher) {
        val followers = listOf(FollowUser(1, "Follower1"), FollowUser(2, "Follower2"))
        val following = listOf(FollowUser(3, "Following1"))
        val repository = FakeProfileRepository(
            followersResult = followers,
            followingResult = following
        )

        val viewModel = UserSocialViewModel(repository, userId = 1)
        viewModel.load(SocialList.FOLLOWERS)
        advanceUntilIdle()

        val initialSuccess = viewModel.state.value as UserSocialViewModel.SocialState.Success
        assertEquals(SocialList.FOLLOWERS, initialSuccess.selected)
        assertEquals(2, initialSuccess.followers.size)
        assertEquals(1, initialSuccess.following.size)
        assertEquals(1, repository.followersFetchCount)
        assertEquals(1, repository.followingFetchCount)

        // Switching to FOLLOWING should update selected immediately without re-fetching
        viewModel.select(SocialList.FOLLOWING)

        val switchedSuccess = viewModel.state.value as UserSocialViewModel.SocialState.Success
        assertEquals(SocialList.FOLLOWING, switchedSuccess.selected)
        assertEquals(1, repository.followersFetchCount)
        assertEquals(1, repository.followingFetchCount)

        // Selecting the same list does nothing
        viewModel.select(SocialList.FOLLOWING)
        assertEquals(1, repository.followersFetchCount)

        // Force refresh triggers re-fetch
        viewModel.load(SocialList.FOLLOWING, forceRefresh = true)
        advanceUntilIdle()
        assertEquals(2, repository.followersFetchCount)
        assertEquals(2, repository.followingFetchCount)
    }

    /**
     * The prefetch must not outrank the list being read.
     *
     * `load` fetches followers *and* following in one job and the tab only ever
     * shows one of them. The job used to report *either* failure by replacing the
     * whole tab with an error screen, so a reader looking at a fifty-person grid
     * lost it because the list they had not asked for was refused. The failure now
     * belongs to the half it happened to, recorded beside the rows that survived.
     */
    @Test
    fun `a prefetched list that fails leaves the list on screen alone`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(
            followersResult = listOf(FollowUser(1, "Follower1"), FollowUser(2, "Follower2")),
            followingFail = true
        )
        val viewModel = UserSocialViewModel(repository, userId = 1)

        viewModel.load(SocialList.FOLLOWERS)
        advanceUntilIdle()

        val state = viewModel.state.value as UserSocialViewModel.SocialState.Success
        assertEquals(
            "the list being read is still the one shown",
            SocialList.FOLLOWERS,
            state.selected
        )
        assertEquals(2, state.followers.size)
        assertTrue("a failed half is not an answer about this account", state.following.isEmpty())
        assertEquals(SocialList.FOLLOWING, state.failedList)
        assertNotNull("and it keeps the reason, for the tab to show on that chip", state.failureMessage)
        assertFalse("the fetch is over, so the marker must not stay up", state.isRefreshing)
    }

    /**
     * A chip tap during a refresh of a list with no rows yet must still switch.
     *
     * `Retarget` means "a job on the wire will answer this; move what the reader is
     * looking at rather than restart it". It only knew how to move the spinner, and
     * a refresh of rows that already exist is published as `Success` carrying
     * [UserSocialViewModel.SocialState.Success.isRefreshing] instead - so on an
     * account that follows nobody, tapping Following while a refresh was running
     * answered the tap with nothing at all.
     */
    @Test
    fun `a chip tap during a refresh moves the selection without restarting the job`() =
        runTest(testDispatcher) {
            val repository = FakeProfileRepository(
                followersResult = listOf(FollowUser(1, "Follower1"))
            )
            val viewModel = UserSocialViewModel(repository, userId = 1)
            viewModel.load(SocialList.FOLLOWERS)
            advanceUntilIdle()

            // A refresh in flight over rows that are on screen: `Success`, not
            // `Loading`, and no following rows to switch to.
            viewModel.load(SocialList.FOLLOWERS, forceRefresh = true)
            val refreshing = viewModel.state.value as UserSocialViewModel.SocialState.Success
            assertTrue("the rows stayed", refreshing.isRefreshing)
            assertTrue(refreshing.following.isEmpty())

            val fetchesBefore = repository.followersFetchCount + repository.followingFetchCount
            viewModel.select(SocialList.FOLLOWING)

            val switched = viewModel.state.value as UserSocialViewModel.SocialState.Success
            assertEquals(
                "the tap has to move what is shown, or it does nothing at all",
                SocialList.FOLLOWING,
                switched.selected
            )
            assertEquals(
                "and it must not cancel the job already answering both lists",
                fetchesBefore,
                repository.followersFetchCount + repository.followingFetchCount
            )

            advanceUntilIdle()
        }

    @Test
    fun `UserActivityViewModel updateUserId triggers load when starting with null userId`() = runTest(testDispatcher) {
        val page1 = List(5) { createActivity(it + 1) }
        val repository = FakeProfileRepository(
            activityPages = mapOf(1 to page1)
        )

        val viewModel = UserActivityViewModel(repository, userId = null)
        viewModel.loadFirstPage()
        advanceUntilIdle()

        // Should be waiting in Loading, not permanently failing
        assertTrue(viewModel.state.value is UserActivityViewModel.ActivityState.Loading)
        assertEquals(0, repository.activityFetchCount)

        // Once the profile resolves the user id, it should fetch automatically
        viewModel.updateUserId(1)
        advanceUntilIdle()

        val success = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(5, success.activities.size)
        assertEquals(1, repository.activityFetchCount)
    }

    /**
     * A failed *extra* page has to leave a way back.
     *
     * Pagination is driven by `LaunchedEffect(shouldPage)`, where `shouldPage` is a
     * `derivedStateOf` over scroll geometry. A reader parked at the bottom of the
     * feed leaves it `true`, so if a failed page changed nothing observable the
     * effect never fired again and the feed stopped growing for the rest of the
     * session - with `hasMore` still true and a spinner under a list that was
     * never going to get longer. Recovering meant scrolling seven rows away and
     * back, which nobody would think to try.
     *
     * The error is recorded rather than swallowed so the tab can offer a tap.
     */
    @Test
    fun `a failed extra page is recorded so the feed can be resumed`() = runTest(testDispatcher) {
        val page1 = List(25) { createActivity(it + 1) }
        val repository = FakeProfileRepository(
            activityPages = mapOf(
                1 to page1,
                2 to List(25) { createActivity(it + 26) }
            ),
            failPages = setOf(2)
        )
        val viewModel = UserActivityViewModel(repository, userId = 1)

        viewModel.loadFirstPage()
        advanceUntilIdle()

        val loaded = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(25, loaded.activities.size)
        assertNull("no page has failed yet", loaded.loadMoreError)

        viewModel.loadNextPage()
        advanceUntilIdle()

        val afterFailure = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(
            "the rows already read must survive a failed extra page",
            25,
            afterFailure.activities.size
        )
        assertFalse("the footer must not stay spinning", afterFailure.isLoadingMore)
        assertTrue(
            "the failure must be recorded, or there is no way back",
            afterFailure.loadMoreError != null
        )
        assertTrue("there is still more to read", afterFailure.hasMore)

        // Retrying, which is what the tab's retry row does.
        repository.failPages = emptySet()
        viewModel.loadNextPage()
        advanceUntilIdle()

        val recovered = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertNull("a successful retry clears the failure", recovered.loadMoreError)
        assertEquals(50, recovered.activities.size)
    }

    /**
     * A page shorter than the request is not the end of the feed.
     *
     * The feed drops union members and updates for media that no longer exists
     * before the ViewModel sees a page, so a twenty-five-row server page can
     * arrive as twenty-three rows. Deriving "no more pages" from that count ended
     * the feed there, with hundreds of entries still behind it. `hasMore` must be
     * AniList's answer, not the row count's.
     */
    @Test
    fun `a short page that reports a next page keeps paging`() = runTest(testDispatcher) {
        val page1 = List(23) { createActivity(it + 1) }
        val page2 = List(4) { createActivity(it + 100) }
        val repository = FakeProfileRepository(
            activityPages = mapOf(1 to page1, 2 to page2),
            pageHasNext = mapOf(1 to true)
        )

        val viewModel = UserActivityViewModel(repository, userId = 1)
        viewModel.loadFirstPage()
        advanceUntilIdle()

        val first = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(23, first.activities.size)
        assertTrue(
            "a filtered page is shorter than the one AniList sent, so its size cannot end the feed",
            first.hasMore
        )

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(2, repository.activityFetchCount)
        val second = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(27, second.activities.size)
        assertFalse("page two had no successor", second.hasMore)
    }

    /**
     * A refresh that fails must not cost the reader the rows it was refreshing.
     *
     * It used to be indistinguishable from a first load, so the refresh replaced
     * the feed with the loading skeleton and a failure then replaced it with the
     * full-screen error - twenty-five readable cards lost to one dropped request.
     * The rows stay and the failure becomes a retryable line above them.
     */
    @Test
    fun `a failed refresh keeps the rows it was refreshing`() = runTest(testDispatcher) {
        val page = List(3) { createActivity(it + 1) }
        val repository = FakeProfileRepository(activityPages = mapOf(1 to page))
        val viewModel = UserActivityViewModel(repository, userId = 1)

        viewModel.loadFirstPage()
        advanceUntilIdle()

        repository.failPages = setOf(1)
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals("the rows already read must survive a failed refresh", 3, state.activities.size)
        assertFalse("the refresh indicator must not stay up", state.isRefreshing)
        assertNotNull("the failure must be retryable rather than silent", state.refreshError)
    }

    @Test
    fun `UserSocialViewModel updateUserId triggers load when starting with null userId`() = runTest(testDispatcher) {
        val followers = listOf(FollowUser(1, "Follower1"))
        val repository = FakeProfileRepository(followersResult = followers)

        val viewModel = UserSocialViewModel(repository, userId = null)
        viewModel.load(SocialList.FOLLOWERS)
        advanceUntilIdle()

        // The old assertion here was `is Loading`, which is the contract this
        // replaced: `Loading` is a spinner whose only exit is the answer to the
        // request this branch never makes. An `Error` is the same sentence with
        // two exits - the tab's retry button, and the id arriving below.
        val pending = viewModel.state.value as UserSocialViewModel.SocialState.Error
        assertEquals(SocialList.FOLLOWERS, pending.list)
        assertEquals(0, repository.followersFetchCount)

        // Resolving the id triggers the fetch
        viewModel.updateUserId(1)
        advanceUntilIdle()

        val success = viewModel.state.value as UserSocialViewModel.SocialState.Success
        assertEquals(1, success.followers.size)
        assertEquals(1, repository.followersFetchCount)
    }

    /**
     * A profile the reader only glanced at must not spend three requests.
     *
     * `updateUserId` used to treat `Idle` as "go and get it", and `Idle` is the
     * *initial* state of both flows - so the id arriving from the profile overview
     * started a fetch the moment the header was on screen, before the Activity or
     * Social tab had been touched. On the by-username route, which is the only one
     * where the id is scanned rather than known up front, that was one activity
     * request plus two social ones (followers *and* following, because `load`
     * prefetches the other list) for every profile opened and immediately backed out
     * of.
     *
     * Both tabs fetch themselves on selection, so waiting costs the reader nothing.
     */
    @Test
    fun `an activity feed is not fetched until the tab asks for it`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(activityPages = mapOf(1 to List(5) { createActivity(it + 1) }))
        val viewModel = UserActivityViewModel(repository, userId = null)

        // Exactly what the profile screen does once the overview resolves.
        viewModel.updateUserId(1)
        advanceUntilIdle()

        assertEquals(
            "resolving the id must not spend a request on a tab nobody opened",
            0,
            repository.activityFetchCount
        )
        assertTrue(
            "and the feed must be left asking, not failed",
            viewModel.state.value is UserActivityViewModel.ActivityState.Idle
        )

        // Selecting the tab is what fetches it.
        viewModel.loadFirstPage()
        advanceUntilIdle()

        assertEquals(1, repository.activityFetchCount)
        assertTrue(viewModel.state.value is UserActivityViewModel.ActivityState.Success)
    }

    /**
     * The regression this signal exists for: a title planned from the dashboard
     * never appeared on the Activity tab.
     *
     * The feed is loaded once and the tab's re-entry is deliberately a no-op, so
     * the write changed the account and left the feed showing the list as it was
     * before - with no state that would ever fetch it again.
     */
    @Test
    fun `a list write refetches the viewer's own activity feed`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(
            activityPages = mapOf(1 to listOf(createActivity(1)))
        )
        val viewModel = UserActivityViewModel(repository, userId = 7, viewerId = 7)
        viewModel.loadFirstPage()
        advanceUntilIdle()
        assertEquals(1, repository.activityFetchCount)

        // What `AniListRepositoryImpl.invalidateLists()` does on a write.
        repository.mutations.value = 1
        advanceUntilIdle()

        assertEquals(
            "the feed holds the viewer's own entries, so a write must fetch it again",
            2,
            repository.activityFetchCount
        )
        assertTrue(viewModel.state.value is UserActivityViewModel.ActivityState.Success)
    }

    @Test
    fun `a list write does not refetch somebody else's activity feed`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(
            activityPages = mapOf(1 to listOf(createActivity(1)))
        )
        // Reading a stranger's profile: the feed is theirs, the session is the viewer's.
        val viewModel = UserActivityViewModel(repository, userId = 7, viewerId = 99)
        viewModel.loadFirstPage()
        advanceUntilIdle()
        assertEquals(1, repository.activityFetchCount)

        repository.mutations.value = 1
        advanceUntilIdle()

        assertEquals(
            "a stranger's feed cannot have changed, so refetching it is a wasted request",
            1,
            repository.activityFetchCount
        )
    }

    @Test
    fun `a list write before the tab is opened does not start a fetch`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(
            activityPages = mapOf(1 to listOf(createActivity(1)))
        )
        val viewModel = UserActivityViewModel(repository, userId = 7, viewerId = 7)

        repository.mutations.value = 1
        advanceUntilIdle()

        assertEquals(
            "the feed is fetched when the tab is selected, not by a write",
            0,
            repository.activityFetchCount
        )
        assertTrue(viewModel.state.value is UserActivityViewModel.ActivityState.Idle)
    }

    @Test
    fun `a social list is not fetched until the tab asks for it`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(followersResult = listOf(FollowUser(1, "Follower1")))
        val viewModel = UserSocialViewModel(repository, userId = null)

        viewModel.updateUserId(1)
        advanceUntilIdle()

        assertEquals(
            "resolving the id must not spend two requests on a tab nobody opened",
            0,
            repository.followersFetchCount
        )

        viewModel.load(SocialList.FOLLOWERS)
        advanceUntilIdle()

        assertEquals(1, repository.followersFetchCount)
        assertTrue(viewModel.state.value is UserSocialViewModel.SocialState.Success)
    }

    @Test
    fun `toggling a like is optimistic and settles on the server's answer`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(
            activityPages = mapOf(1 to listOf(createActivity(id = 1, isLiked = false, likeCount = 0)))
        )
        repository.likeResult = Result.success(ActivityLikeState(isLiked = true, likeCount = 1))
        val viewModel = UserActivityViewModel(repository, userId = 1)
        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.toggleLike(1)

        // Optimistic: the state has already moved, before the suspended request is
        // resumed - and the activity is marked in flight so a second tap cannot
        // toggle it back.
        val optimistic = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(true, optimistic.activities.single().isLiked)
        assertEquals(1, optimistic.activities.single().likeCount)
        assertTrue(1 in optimistic.pendingLikes)

        advanceUntilIdle()

        val settled = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(true, settled.activities.single().isLiked)
        assertEquals(1, settled.activities.single().likeCount)
        assertFalse(1 in settled.pendingLikes)
        assertEquals(1, repository.likeFetchCount)
    }

    @Test
    fun `a failed like puts the original row back`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(
            activityPages = mapOf(1 to listOf(createActivity(id = 1, isLiked = false, likeCount = 3)))
        )
        repository.likeResult = Result.failure(RuntimeException("nope"))
        val viewModel = UserActivityViewModel(repository, userId = 1)
        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.toggleLike(1)
        advanceUntilIdle()

        // The optimistic count was a guess and the server contradicted it, so the
        // pre-tap row is restored rather than left showing a like that did not stick.
        val state = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(false, state.activities.single().isLiked)
        assertEquals(3, state.activities.single().likeCount)
        assertFalse(1 in state.pendingLikes)
    }

    @Test
    fun `a like is refused when AniList did not answer whether it is liked`() = runTest(testDispatcher) {
        // `isLiked == null` is what a signed-out feed looks like. Offering the
        // action would send a mutation with no session to record it.
        val repository = FakeProfileRepository(
            activityPages = mapOf(1 to listOf(createActivity(id = 1, isLiked = null, likeCount = 0)))
        )
        val viewModel = UserActivityViewModel(repository, userId = 1)
        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.toggleLike(1)
        advanceUntilIdle()

        assertEquals(0, repository.likeFetchCount)
    }

    @Test
    fun `opening comments loads the thread`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(activityPages = mapOf(1 to listOf(createActivity(1))))
        repository.repliesResult = Result.success(listOf(ActivityReply(id = 5, text = "hi")))
        val viewModel = UserActivityViewModel(repository, userId = 1)
        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.openComments(1)
        assertTrue(viewModel.comments.value!!.isLoading)

        advanceUntilIdle()

        val comments = viewModel.comments.value!!
        assertFalse(comments.isLoading)
        assertEquals(1, comments.replies.size)
        assertEquals("hi", comments.replies.single().text)
        assertEquals(1, repository.repliesFetchCount)
    }

    @Test
    fun `posting a comment appends the created reply and bumps the feed count`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(
            activityPages = mapOf(1 to listOf(createActivity(id = 1, replyCount = 2)))
        )
        repository.postReplyResult = Result.success(ActivityReply(id = 9, text = "mine"))
        val viewModel = UserActivityViewModel(repository, userId = 1)
        viewModel.loadFirstPage()
        advanceUntilIdle()
        viewModel.openComments(1)
        advanceUntilIdle()

        viewModel.postComment("mine")
        advanceUntilIdle()

        val comments = viewModel.comments.value!!
        assertFalse(comments.isPosting)
        assertEquals(1, comments.replies.size)
        assertEquals(9, comments.replies.single().id)
        // Recorded so the sheet can bring the new comment into view - and only
        // this, rather than "the list grew", so a loaded thread does not scroll.
        assertEquals(9, comments.postedReplyId)
        // The feed's reply count is kept honest without a refetch.
        val feed = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(3, feed.activities.single().replyCount)
    }

    @Test
    fun `a failed comment keeps the draft available`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(activityPages = mapOf(1 to listOf(createActivity(1))))
        repository.postReplyResult = Result.failure(RuntimeException("offline"))
        val viewModel = UserActivityViewModel(repository, userId = 1)
        viewModel.loadFirstPage()
        advanceUntilIdle()
        viewModel.openComments(1)
        advanceUntilIdle()

        viewModel.postComment("mine")
        advanceUntilIdle()

        val comments = viewModel.comments.value!!
        assertFalse(comments.isPosting)
        assertEquals(0, comments.replies.size)
        assertTrue(comments.postError != null)
    }

    private fun createActivity(
        id: Int,
        isLiked: Boolean? = null,
        likeCount: Int = 0,
        replyCount: Int = 0
    ) = ProfileActivity(
        id = id,
        status = "completed",
        progress = "12",
        likeCount = likeCount,
        replyCount = replyCount,
        isLiked = isLiked,
        media = MediaNode(
            id = id,
            title = MediaTitle(english = "Anime $id"),
            coverImage = MediaCoverImage(large = "https://example.test/$id.png")
        )
    )
}
