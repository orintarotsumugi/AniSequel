package com.example

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
        var followingResult: List<FollowUser> = emptyList()
    ) : AniListRepository {
        var activityFetchCount = 0
        var followersFetchCount = 0
        var followingFetchCount = 0

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

    private fun createActivity(id: Int) = ListActivity(
        id = id,
        status = "completed",
        progress = 12,
        media = MediaNode(
            id = id,
            title = MediaTitle(english = "Anime $id"),
            coverImage = MediaCoverImage(large = "https://example.test/$id.png")
        )
    )
}
