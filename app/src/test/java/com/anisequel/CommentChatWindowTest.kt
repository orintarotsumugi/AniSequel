package com.anisequel

import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.ActivityReplyWindow
import com.anisequel.data.model.FollowUser
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.usecase.CommentWindow
import com.anisequel.ui.viewmodel.CommentThreadStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chat view's window, driven through the store rather than through a screen.
 *
 * The failure this pins is the one the whole feature exists to avoid: a thread that
 * opens at its oldest comments, or that quietly misses the newest ones because the
 * count the feed carried is stale. Both are invisible in a screenshot - the rows
 * that arrive are real comments, in order - and only an assertion on *which page
 * was asked for* says whether the sheet opened where a reader expects it.
 */
class CommentChatWindowTest {

    /**
     * The thread as a server, answering by page.
     *
     * Only the two reply reads are real. The interface's other members keep their
     * default `unsupported` answers, which is the house rule for these doubles: a
     * fake that implements everything is a second implementation to keep in step.
     */
    private class WindowRepository(
        var replies: List<ActivityReply>,
        var serverCount: Int = replies.size
    ) : AniListRepository {
        val requestedPages = mutableListOf<Int>()
        var wholeThreadReads = 0

        override suspend fun getActivityReplyWindow(
            activityId: Int,
            page: Int,
            perPage: Int
        ): Result<ActivityReplyWindow> {
            requestedPages += page
            // AniList's paging, exactly: replies ascend by id, a page is a slice of
            // that order, and a page past the last one answers empty while still
            // reporting the real count.
            val from = (page - 1) * perPage
            val rows = if (from >= replies.size) emptyList() else replies.drop(from).take(perPage)
            return Result.success(
                ActivityReplyWindow(replies = rows, replyCount = serverCount, page = page)
            )
        }

        override suspend fun getActivityReplies(activityId: Int): Result<List<ActivityReply>> {
            wholeThreadReads++
            return Result.success(replies)
        }
    }

    private fun reply(id: Int, author: Int = 1, at: Int = 1_000 + id) = ActivityReply(
        id = id,
        text = "comment $id",
        likeCount = 0,
        isLiked = false,
        createdAt = at,
        user = FollowUser(id = author, name = "user$author", avatar = null)
    )

    private fun thread(count: Int) = (1..count).map { reply(it, author = it % 3) }

    private fun storeFor(repository: AniListRepository, scope: CoroutineScope) =
        CommentThreadStore(repository = repository, scope = scope, isDemo = false, viewerId = 1)

    @Test
    fun `a long thread opens at its newest page, not at page one`() = runTest {
        val repository = WindowRepository(thread(40))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 40, chatView = true)
        runCurrent()

        // 40 comments at 25 a page is two pages and the newest comments are on the
        // second. Aiming at page one would open the sheet at the conversation's
        // first two dozen messages and leave the last eight behind.
        assertEquals(listOf(2), repository.requestedPages)
        val state = store.state.value!!
        assertEquals(40, state.replyCount)
        assertEquals("the second page of 40 at 25 is the tail", 15, state.replies.size)
        assertEquals("the newest comment must be the last row drawn", 40, state.replies.last().id)
        assertEquals(26, state.replies.first().id)
        assertTrue(state.isChatView)
        assertEquals(25, CommentWindow.hiddenCount(state.replyCount, state.replies.size))
    }

    @Test
    fun `a stale feed count is re-aimed once at the real last page`() = runTest {
        // The card that opened the sheet counted 20; six comments have landed since,
        // so the newest replies sit on a page the guess never named.
        val repository = WindowRepository(thread(26))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 20, chatView = true)
        runCurrent()

        assertEquals(
            "page one was aimed with a stale count, then the real page asked for: ${repository.requestedPages}",
            listOf(1, 2),
            repository.requestedPages
        )
        assertEquals(26, store.state.value!!.replies.last().id)
    }

    @Test
    fun `a count that lands on the same page is not worth a second request`() = runTest {
        // The feed said 30 and the server says 28: both put the newest replies on
        // page 2, and the rows already fetched are the right rows.
        val repository = WindowRepository(thread(28), serverCount = 28)
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 30, chatView = true)
        runCurrent()
        assertEquals(listOf(2), repository.requestedPages)
    }

    @Test
    fun `older walks back a page and keeps what is already on screen`() = runTest {
        val repository = WindowRepository(thread(60))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 60, chatView = true)
        runCurrent()
        assertEquals(listOf(3), repository.requestedPages)
        val opened = store.state.value!!.replies.toList()

        store.loadOlder()
        runCurrent()
        assertEquals(listOf(3, 2), repository.requestedPages)
        val after = store.state.value!!
        assertEquals(2, after.oldestPage)
        assertTrue(
            "the newest rows are still the newest rows",
            after.replies.takeLast(opened.size) == opened
        )
        assertEquals(
            "page two runs 26..50 and sits above page three with no gap",
            26,
            after.replies.first().id
        )
        assertEquals(35, after.replies.size)
    }

    @Test
    fun `full-thread mode reads the whole thread in one request`() = runTest {
        val repository = WindowRepository(thread(40))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 0, chatView = false)
        runCurrent()

        assertEquals(1, repository.wholeThreadReads)
        assertTrue(
            "the window document is not used when chat view is off",
            repository.requestedPages.isEmpty()
        )
        val state = store.state.value!!
        assertEquals(40, state.replies.size)
        assertEquals(0, state.shownRows)
        assertNull(state.windowError)
    }

    @Test
    fun `a check that finds comments counts them instead of moving the reader`() = runTest {
        val repository = WindowRepository(thread(40))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 40, chatView = true)
        runCurrent()

        // Somebody posts while the sheet is open.
        repository.replies = thread(41)
        repository.serverCount = 41
        repository.requestedPages.clear()

        store.checkForNewComments(atBottom = false)
        runCurrent()

        val state = store.state.value!!
        assertEquals(41, state.replyCount)
        assertEquals(
            "a reader parked further up is told how many arrived, not moved",
            1,
            state.unseenNewComments
        )
        assertTrue("the new comment is fetched, so it is there to reveal", state.replies.any { it.id == 41 })

        store.revealNewComments()
        assertEquals(0, store.state.value!!.unseenNewComments)
    }

    @Test
    fun `a background check is not started for a demo thread or a full thread`() = runTest {
        val repository = WindowRepository(thread(40))
        val scope = this
        val store = CommentThreadStore(
            repository = repository,
            scope = scope,
            isDemo = false,
            viewerId = 1
        )
        store.open(activityId = 7, knownReplyCount = 40, chatView = false)
        runCurrent()
        repository.requestedPages.clear()

        store.checkForNewComments(atBottom = true)
        runCurrent()
        assertTrue(
            "full-thread mode has no window to refresh",
            repository.requestedPages.isEmpty()
        )
    }
}
