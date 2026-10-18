package com.anisequel

import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.ListActivity
import com.anisequel.data.model.MediaCoverImage
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.viewmodel.CommentThreadStore
import com.anisequel.ui.viewmodel.UserActivityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Editing and deleting your own comment: the dialog behind the thread's overflow.
 *
 * ## What these pin
 *
 * The two writes go to the right comment with the right text, a success closes the
 * dialog and moves the thread (and, for a delete, the activity's comment count),
 * and a failure does not: the dialog stays up with the draft still in the box and
 * the reason beside it, because the retry has to be one tap rather than a rewrite.
 *
 * The ownership rules are pinned here too, in both directions. A comment by
 * somebody else cannot open the dialog, and neither can any comment when the
 * viewer is unknown - signed out. The server enforces ownership on the mutation
 * regardless, which is exactly why the UI must not invite a tap it knows will be
 * refused.
 *
 * The composer's two-character floor lives here as well: it is the API's own rule
 * (`SaveActivityReply.text` is Min 2), and it used to be discovered by sending a
 * one-character comment and reading the server's refusal as a post failure.
 *
 * ## Why the thread helpers are not a second copy of the sheet
 *
 * `CommentThreadStore` is the state, and these tests drive it through the
 * ViewModel's delegating calls - the same surface the sheet calls - so what is
 * asserted is the behaviour the screen gets, not a re-implementation of it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentManagementTest {

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
    fun `a one-character comment is refused before it reaches AniList`() = runTest(testDispatcher) {
        val repository = FakeCommentRepository()
        val viewModel = buildViewModel(repository)
        openThread(viewModel)

        viewModel.postComment("a")
        advanceUntilIdle()

        assertEquals(0, repository.postReplyFetchCount)
        assertNull(viewModel.comments.value!!.postError)
    }

    @Test
    fun `editing your own comment replaces its text and closes the dialog`() = runTest(testDispatcher) {
        val repository = FakeCommentRepository(
            replies = listOf(mineReply(id = 5, text = "old")),
            editResult = Result.success(ActivityReply(id = 5, text = "new"))
        )
        val viewModel = buildViewModel(repository)
        openThread(viewModel)

        viewModel.openReplyActions(5)
        assertEquals(5, viewModel.comments.value!!.action?.replyId)

        viewModel.setReplyActionMode(CommentThreadStore.ReplyAction.Mode.EDIT)
        // The editor starts from the comment on screen, not from an empty box:
        // the stored text is what the reader came to rewrite.
        assertEquals("old", viewModel.comments.value!!.action?.draft)

        viewModel.updateReplyActionDraft("new")
        viewModel.submitReplyEdit()
        advanceUntilIdle()

        val comments = viewModel.comments.value!!
        assertNull(comments.action)
        assertEquals("new", comments.replies.single().text)
        assertEquals(5, repository.lastEditedReplyId)
        assertEquals("new", repository.lastEditedText)
    }

    @Test
    fun `a failed edit keeps the dialog open with the draft and the reason`() = runTest(testDispatcher) {
        val repository = FakeCommentRepository(
            replies = listOf(mineReply(id = 5, text = "old")),
            editResult = Result.failure(RuntimeException("offline"))
        )
        val viewModel = buildViewModel(repository)
        openThread(viewModel)

        viewModel.openReplyActions(5)
        viewModel.setReplyActionMode(CommentThreadStore.ReplyAction.Mode.EDIT)
        viewModel.updateReplyActionDraft("new")
        viewModel.submitReplyEdit()
        advanceUntilIdle()

        val action = viewModel.comments.value!!.action
        assertTrue("The dialog stays up on failure", action != null)
        assertFalse(action!!.isBusy)
        assertEquals("offline", action.error)
        // The attempted text survives, so the retry is one tap and not a rewrite.
        assertEquals("new", action.draft)
    }

    @Test
    fun `deleting your own comment removes it and drops the feed count`() = runTest(testDispatcher) {
        val repository = FakeCommentRepository(
            activity = createActivity(id = 1, replyCount = 2),
            replies = listOf(mineReply(id = 5, text = "mine"))
        )
        val viewModel = buildViewModel(repository)
        openThread(viewModel)

        viewModel.openReplyActions(5)
        viewModel.setReplyActionMode(CommentThreadStore.ReplyAction.Mode.CONFIRM_DELETE)
        viewModel.submitReplyDelete()
        advanceUntilIdle()

        val comments = viewModel.comments.value!!
        assertNull(comments.action)
        assertTrue(comments.replies.isEmpty())
        assertEquals(5, repository.lastDeletedReplyId)
        assertEquals(1, repository.deleteReplyFetchCount)
        // The mirror of the post's bump: the card no longer claims a comment the
        // thread does not have.
        val feed = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(1, feed.activities.single().replyCount)
    }

    @Test
    fun `a failed delete keeps the row, the count and the dialog`() = runTest(testDispatcher) {
        val repository = FakeCommentRepository(
            activity = createActivity(id = 1, replyCount = 2),
            replies = listOf(mineReply(id = 5, text = "mine")),
            deleteResult = Result.failure(RuntimeException("offline"))
        )
        val viewModel = buildViewModel(repository)
        openThread(viewModel)

        viewModel.openReplyActions(5)
        viewModel.setReplyActionMode(CommentThreadStore.ReplyAction.Mode.CONFIRM_DELETE)
        viewModel.submitReplyDelete()
        advanceUntilIdle()

        val comments = viewModel.comments.value!!
        assertEquals(1, comments.replies.size)
        val action = comments.action
        assertTrue("The dialog stays up on failure", action != null)
        assertEquals("offline", action!!.error)
        // Nothing was decremented for a delete that never happened.
        val feed = viewModel.state.value as UserActivityViewModel.ActivityState.Success
        assertEquals(2, feed.activities.single().replyCount)
    }

    @Test
    fun `somebody else's comment cannot open the manage dialog`() = runTest(testDispatcher) {
        // The sheet does not draw the control for these, but the store is the half
        // a test can reach - and the half that has to refuse even if it is.
        val repository = FakeCommentRepository(
            replies = listOf(
                ActivityReply(id = 5, text = "theirs", user = FollowUser(id = 42, name = "Them"))
            )
        )
        val viewModel = buildViewModel(repository)
        openThread(viewModel)

        viewModel.openReplyActions(5)

        assertNull(viewModel.comments.value!!.action)
    }

    @Test
    fun `a signed-out viewer cannot open the manage dialog`() = runTest(testDispatcher) {
        // No viewer id is the signed-out case: ownership cannot be decided, so the
        // safe half is no menu rather than a Delete AniList would refuse.
        val repository = FakeCommentRepository(replies = listOf(mineReply(id = 5, text = "mine")))
        val viewModel = UserActivityViewModel(repository, userId = 1, pageSize = 25)
        openThread(viewModel)

        viewModel.openReplyActions(5)

        assertNull(viewModel.comments.value!!.action)
    }

    /** The id the manage tests sign in as; [mineReply] authors their comments. */
    private val viewerId = 7

    private fun buildViewModel(repository: AniListRepository) = UserActivityViewModel(
        repository,
        userId = 1,
        viewerId = viewerId,
        pageSize = 25
    )

    /** Loads the feed and opens the thread, which is what every case starts from. */
    private fun TestScope.openThread(viewModel: UserActivityViewModel, activityId: Int = 1) {
        viewModel.loadFirstPage()
        advanceUntilIdle()
        viewModel.openComments(activityId)
        advanceUntilIdle()
    }

    /** A reply authored by the viewer these tests sign in as. */
    private fun mineReply(id: Int, text: String) = ActivityReply(
        id = id,
        text = text,
        user = FollowUser(id = viewerId, name = "Me")
    )

    /**
     * Only the calls the thread makes are real; the rest of the profile surface is
     * stubbed, because a test double that implements everything is a second
     * implementation to keep in step.
     */
    private class FakeCommentRepository(
        private val activity: ListActivity = createActivity(id = 1),
        var replies: List<ActivityReply> = emptyList(),
        var postResult: Result<ActivityReply> =
            Result.success(ActivityReply(id = 1, text = "posted")),
        var editResult: Result<ActivityReply> =
            Result.success(ActivityReply(id = 1, text = "edited")),
        var deleteResult: Result<Unit> = Result.success(Unit)
    ) : AniListRepository {
        var repliesFetchCount = 0
        var postReplyFetchCount = 0
        var editReplyFetchCount = 0
        var deleteReplyFetchCount = 0

        /** What the last edit and delete were actually called with. */
        var lastEditedReplyId: Int? = null
        var lastEditedText: String? = null
        var lastDeletedReplyId: Int? = null

        override suspend fun getViewer(): Result<ViewerProfile> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getUserByName(userName: String): Result<ViewerProfile> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getUserAnimeList(
            userId: Int,
            forceRefresh: Boolean
        ): Result<MediaListCollection> = Result.success(MediaListCollection(emptyList()))

        override suspend fun getUserAnimeListByUsername(
            userName: String,
            forceRefresh: Boolean
        ): Result<MediaListCollection> = Result.success(MediaListCollection(emptyList()))

        override suspend fun getMediaDetail(mediaId: Int, mediaType: String?): Result<MediaNode> =
            Result.failure(UnsupportedOperationException())

        override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> =
            Result.failure(UnsupportedOperationException())

        override fun getDemoProfile() = ViewerProfile(1, "Demo", null, null)
        override fun getDemoAnimeList() = MediaListCollection(emptyList())

        override suspend fun getUserActivity(
            userId: Int,
            page: Int,
            forceRefresh: Boolean
        ): Result<List<ListActivity>> = Result.success(if (page == 1) listOf(activity) else emptyList())

        override suspend fun getUserFollowers(
            userId: Int,
            forceRefresh: Boolean
        ): Result<List<FollowUser>> = Result.success(emptyList())

        override suspend fun getUserFollowing(
            userId: Int,
            forceRefresh: Boolean
        ): Result<List<FollowUser>> = Result.success(emptyList())

        override suspend fun getActivityReplies(activityId: Int): Result<List<ActivityReply>> {
            repliesFetchCount++
            return Result.success(replies)
        }

        override suspend fun postActivityReply(activityId: Int, text: String): Result<ActivityReply> {
            postReplyFetchCount++
            return postResult
        }

        override suspend fun editActivityReply(
            replyId: Int,
            activityId: Int,
            text: String
        ): Result<ActivityReply> {
            editReplyFetchCount++
            lastEditedReplyId = replyId
            lastEditedText = text
            return editResult
        }

        override suspend fun deleteActivityReply(replyId: Int): Result<Unit> {
            deleteReplyFetchCount++
            lastDeletedReplyId = replyId
            return deleteResult
        }
    }

}

/**
 * The one activity every case's feed carries.
 *
 * `createActivity` is top-level rather than a companion member because the fake
 * repository - a nested class - uses it as a default argument, and a nested class
 * cannot reach its outer class's companion as unqualified scope.
 */
private fun createActivity(id: Int, replyCount: Int = 0) = ListActivity(
    id = id,
    status = "completed",
    progress = "12",
    likeCount = 0,
    replyCount = replyCount,
    media = MediaNode(
        id = id,
        title = MediaTitle(english = "Anime $id"),
        coverImage = MediaCoverImage(large = "https://example.test/$id.png")
    )
)
