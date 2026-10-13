package com.example

import com.example.data.model.ActivityLikeState
import com.example.data.model.ActivityReply
import com.example.data.model.FollowUser
import com.example.data.model.ListActivity
import com.example.data.model.MediaCoverImage
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaNode
import com.example.data.model.MediaTitle
import com.example.data.model.SimpleMediaListEntry
import com.example.data.model.UserOverview
import com.example.data.model.ViewerProfile
import com.example.data.repository.AniListRepository
import com.example.ui.viewmodel.SocialList
import com.example.ui.viewmodel.UserActivityViewModel
import com.example.ui.viewmodel.UserSocialViewModel
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
        var activityPages: Map<Int, List<ListActivity>> = emptyMap(),
        var followersResult: List<FollowUser> = emptyList(),
        var followingResult: List<FollowUser> = emptyList(),
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

        override suspend fun getViewer(): Result<ViewerProfile> = Result.failure(UnsupportedOperationException())
        override suspend fun getUserByName(userName: String): Result<ViewerProfile> = Result.failure(UnsupportedOperationException())
        override suspend fun getUserAnimeList(userId: Int, forceRefresh: Boolean): Result<MediaListCollection> = Result.success(MediaListCollection(emptyList()))
        override suspend fun getUserAnimeListByUsername(userName: String, forceRefresh: Boolean): Result<MediaListCollection> = Result.success(MediaListCollection(emptyList()))
        override suspend fun getMediaDetail(mediaId: Int): Result<MediaNode> = Result.failure(UnsupportedOperationException())
        override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> = Result.failure(UnsupportedOperationException())
        override fun getDemoProfile() = ViewerProfile(1, "Demo", null, null)
        override fun getDemoAnimeList() = MediaListCollection(emptyList())

        override suspend fun getUserActivity(
            userId: Int,
            page: Int,
            forceRefresh: Boolean
        ): Result<List<ListActivity>> {
            activityFetchCount++
            val list = activityPages[page] ?: emptyList()
            return Result.success(list)
        }

        override suspend fun getUserFollowers(
            userId: Int,
            forceRefresh: Boolean
        ): Result<List<FollowUser>> {
            followersFetchCount++
            return Result.success(followersResult)
        }

        override suspend fun getUserFollowing(
            userId: Int,
            forceRefresh: Boolean
        ): Result<List<FollowUser>> {
            followingFetchCount++
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

        val viewModel = UserActivityViewModel(repository, userId = 1, pageSize = 25)
        viewModel.loadFirstPage()
        advanceUntilIdle()

        val state1 = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(25, state1.activities.size)
        assertTrue("Page 1 had 25 items so hasMore must be true", state1.hasMore)
        assertEquals(1, repository.activityFetchCount)

        // Calling loadNextPage should now fetch page 2
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals("Page 2 fetch should have been invoked", 2, repository.activityFetchCount)
        val state2 = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(35, state2.activities.size)
        assertFalse("Page 2 had 10 items (< 25) so hasMore must be false", state2.hasMore)

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

    @Test
    fun `UserActivityViewModel updateUserId triggers load when starting with null userId`() = runTest(testDispatcher) {
        val page1 = List(5) { createActivity(it + 1) }
        val repository = FakeProfileRepository(
            activityPages = mapOf(1 to page1)
        )

        val viewModel = UserActivityViewModel(repository, userId = null, pageSize = 25)
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

    @Test
    fun `UserSocialViewModel updateUserId triggers load when starting with null userId`() = runTest(testDispatcher) {
        val followers = listOf(FollowUser(1, "Follower1"))
        val repository = FakeProfileRepository(followersResult = followers)

        val viewModel = UserSocialViewModel(repository, userId = null)
        viewModel.load(SocialList.FOLLOWERS)
        advanceUntilIdle()

        // Stays in loading gracefully
        assertTrue(viewModel.state.value is UserSocialViewModel.SocialState.Loading)
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
        val viewModel = UserActivityViewModel(repository, userId = null, pageSize = 25)

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
        val viewModel = UserActivityViewModel(repository, userId = 1, pageSize = 25)
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
        val viewModel = UserActivityViewModel(repository, userId = 1, pageSize = 25)
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
        val viewModel = UserActivityViewModel(repository, userId = 1, pageSize = 25)
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
        val viewModel = UserActivityViewModel(repository, userId = 1, pageSize = 25)
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
        val viewModel = UserActivityViewModel(repository, userId = 1, pageSize = 25)
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
        // The feed's reply count is kept honest without a refetch.
        val feed = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(3, feed.activities.single().replyCount)
    }

    @Test
    fun `a failed comment keeps the draft available`() = runTest(testDispatcher) {
        val repository = FakeProfileRepository(activityPages = mapOf(1 to listOf(createActivity(1))))
        repository.postReplyResult = Result.failure(RuntimeException("offline"))
        val viewModel = UserActivityViewModel(repository, userId = 1, pageSize = 25)
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
    ) = ListActivity(
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
