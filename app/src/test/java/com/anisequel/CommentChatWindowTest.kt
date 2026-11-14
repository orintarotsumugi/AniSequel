package com.anisequel

import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.ActivityReplyWindow
import com.anisequel.data.model.FollowUser
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.usecase.CommentWindow
import com.anisequel.ui.viewmodel.CommentThreadStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
@OptIn(ExperimentalCoroutinesApi::class)
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
    ) : AniListRepository by UnsupportedRepository {
        val requestedPages = mutableListOf<Int>()
        var wholeThreadReads = 0

        /** Makes the paged read fail, for a background check that has to say so. */
        var failWindowReads = false

        override suspend fun getActivityReplyWindow(
            activityId: Int,
            page: Int,
            perPage: Int
        ): Result<ActivityReplyWindow> {
            requestedPages += page
            if (failWindowReads) return Result.failure(IllegalStateException("the socket closed"))
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

    /**
     * The walk back has to finish.
     *
     * Sixty replies are three pages, and Older used to stop on the second one. The
     * caller stepped back a page and then asked the domain whether a step back was
     * possible, so the question was answered twice and its answer at page 2 was
     * "no, there is nothing older than page 1" about a page the sheet was not on.
     * The oldest 25 comments of any thread longer than 50 were unreachable, while
     * the row above the list went on reading "Older 25" and did nothing when pressed.
     */
    @Test
    fun `older walks all the way back to the first page`() = runTest {
        val repository = WindowRepository(thread(60))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 60, chatView = true)
        runCurrent()

        store.loadOlder()
        runCurrent()
        store.loadOlder()
        runCurrent()

        assertEquals(listOf(3, 2, 1), repository.requestedPages)
        val state = store.state.value!!
        assertEquals(1, state.oldestPage)
        assertEquals("every page of the thread is in hand", 60, state.replies.size)
    }

    /**
     * A failed check is not a failed Older page.
     *
     * Both used to land in `windowError`, and the banner under the list read that
     * field as the Older failure: the Retry it offered asked for an older page after
     * a refresh that had not arrived, which moved the reader backwards and still
     * did not bring the comments they were waiting for.
     */
    @Test
    fun `a failed background check names its own retry`() = runTest {
        val repository = WindowRepository(thread(40))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 40, chatView = true)
        runCurrent()
        repository.failWindowReads = true

        store.checkForNewComments(atBottom = true)
        runCurrent()

        val state = store.state.value!!
        assertEquals("the socket closed", state.refreshError)
        assertNull("there is no older page being retried", state.windowError)
        assertFalse("the header is not still spinning", state.isRefreshing)
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

    /**
     * Reopening refreshes the newest page in hand, not the oldest one loaded.
     *
     * The cache exists so a dismiss by accident costs nothing, and it told `open`
     * which page to aim at. That page used to be `oldestPage` - the page a reader
     * reached by pressing Older - and the fetch after it merged as a first read,
     * which replaces the window rather than adding to it. So the sheet reopened on
     * the reader's oldest page with the newer rows gone and the conversation
     * apparently jumping backwards. A message thread goes through this same window,
     * so opening a conversation from the inbox did it too.
     */
    @Test
    fun `a reopen refreshes the newest page and keeps the older pages in hand`() = runTest {
        val repository = WindowRepository(thread(60))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 60, chatView = true)
        runCurrent()
        store.loadOlder()
        runCurrent()
        assertEquals(listOf(3, 2), repository.requestedPages)
        store.close()

        // Somebody posted while the sheet was gone.
        repository.replies = thread(61)
        repository.serverCount = 61
        repository.requestedPages.clear()

        store.open(activityId = 7, knownReplyCount = 60, chatView = true)
        runCurrent()

        assertEquals(
            "a reopen asks for the newest page in hand, not the oldest one loaded: " +
                repository.requestedPages,
            listOf(3),
            repository.requestedPages
        )
        val state = store.state.value!!
        assertEquals(
            "the page Older brought in must not be thrown away by the reopen",
            2,
            state.oldestPage
        )
        assertEquals("no gap above the oldest page", 26, state.replies.first().id)
        assertEquals("and the newest comment is the last row", 61, state.replies.last().id)
        assertEquals(36, state.replies.size)
        assertFalse(state.isLoading)
        assertFalse("the reopen is a refresh, not a first read", state.isRefreshing)
    }

    /**
     * A reopen that lands exactly one page behind fills that page forward.
     *
     * The refreshed page is still the page the cache ends on, and the page after
     * it holds comments the reader has not seen. Jumping straight at the newest
     * page would skip the rows in between; walking one page forward keeps the list
     * whole. This is the case a live thread produces in minutes, so it is the one a
     * reopen has to get right.
     */
    @Test
    fun `a reopen one page behind fetches that page too, without a hole`() = runTest {
        val repository = WindowRepository(thread(60))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 60, chatView = true)
        runCurrent()
        store.close()

        repository.replies = thread(80)
        repository.serverCount = 80
        repository.requestedPages.clear()

        store.open(activityId = 7, knownReplyCount = 60, chatView = true)
        runCurrent()

        assertEquals(
            "the page in hand is refreshed, then the page after it: " +
                repository.requestedPages,
            listOf(3, 4),
            repository.requestedPages
        )
        val state = store.state.value!!
        assertEquals("the newest page's last row", 80, state.replies.last().id)
        assertEquals(
            "25 rows per page and no skipped page: ${state.replies.map { it.id }}",
            (51..80).toList(),
            state.replies.map { it.id }
        )
    }

    /**
     * A long absence opens the newest window rather than a thread with a hole in it.
     *
     * Several pages of comments can land while the sheet is closed. The cache has
     * no page that bridges them, so refreshing only the tail would draw the cached
     * rows directly above the newest page with a whole conversation missing between
     * them, and Older - which works backwards from `oldestPage` - could never fill
     * it. The newest page as a fresh window is what a first open would have done.
     */
    @Test
    fun `a reopen several pages behind opens the newest page instead of leaving a hole`() = runTest {
        val repository = WindowRepository(thread(60))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 60, chatView = true)
        runCurrent()
        store.close()

        repository.replies = thread(111)
        repository.serverCount = 111
        repository.requestedPages.clear()

        store.open(activityId = 7, knownReplyCount = 60, chatView = true)
        runCurrent()

        assertEquals(listOf(3, 5), repository.requestedPages)
        val state = store.state.value!!
        assertEquals("the newest page, not a window stitched across a gap", 5, state.oldestPage)
        assertEquals((101..111).toList(), state.replies.map { it.id })
        assertEquals(111, state.replyCount)
    }

    /**
     * The header's Refresh button.
     *
     * A reader who taps it has decided the thread is stale; the poll's timer has not
     * agreed yet. Two things have to stay true while serving that: one request at a
     * time, because AniList holds the session at about 30 a minute and every other
     * screen spends from the same purse; and the rows already on screen stay drawn,
     * because a Refresh that blanked the thread would be worse than no Refresh.
     */
    @Test
    fun `a refresh re-reads the newest page over the rows on screen`() = runTest {
        val repository = WindowRepository(thread(30))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 30, chatView = true)
        runCurrent()
        repository.requestedPages.clear()

        repository.replies = thread(33)
        repository.serverCount = 33
        store.refreshNow(atBottom = true)
        runCurrent()

        assertEquals("one request, at the newest page", listOf(2), repository.requestedPages)
        val state = store.state.value!!
        assertFalse("the rows stayed", state.isRefreshing)
        assertEquals(33, state.replyCount)
        // The newest page, not the whole thread: a Refresh re-reads the page the
        // reader is looking at, and the rows above it stay unfetched - `Older` is
        // what pays for those, on purpose, because pulling 33 comments to refresh
        // 8 of them is a second full read of a thread nobody asked to re-open.
        assertEquals((26..33).toList(), state.replies.map { it.id })
        assertEquals("a reader at the bottom is not owed a pill for rows they can see", 0, state.unseenNewComments)
    }

    @Test
    fun `a refresh during a request in flight is ignored rather than queued`() = runTest {
        val repository = WindowRepository(thread(60))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 60, chatView = true)
        runCurrent()
        repository.requestedPages.clear()

        // Two taps in the same frame: the second must not start a second read, or the
        // two answers count the same new comment twice and the pill lies.
        store.refreshNow(atBottom = false)
        store.refreshNow(atBottom = false)
        runCurrent()

        assertEquals(listOf(3), repository.requestedPages)
    }

    @Test
    fun `a refresh in full-thread mode re-reads the thread instead of doing nothing`() = runTest {
        // Chat view off is a real setting, and a header control that only worked with
        // it on would be a button that can only fail for those readers.
        val repository = WindowRepository(thread(8))
        val store = storeFor(repository, this)
        store.open(activityId = 7, knownReplyCount = 8, chatView = false)
        runCurrent()
        repository.wholeThreadReads = 0

        repository.replies = thread(9)
        store.refreshNow(atBottom = true)
        runCurrent()

        assertEquals(1, repository.wholeThreadReads)
        assertEquals("the re-read replaced the rows, whole thread", 9, store.state.value!!.replies.size)
    }

    @Test
    fun `a refresh does nothing for a demo thread`() = runTest {
        val repository = WindowRepository(thread(10))
        val store = CommentThreadStore(
            repository = repository,
            scope = this,
            isDemo = true,
            viewerId = 1
        )
        store.open(activityId = 7, knownReplyCount = 10, chatView = true)
        runCurrent()
        repository.requestedPages.clear()

        store.refreshNow(atBottom = true)
        runCurrent()

        assertTrue("the demo has no server to ask", repository.requestedPages.isEmpty())
    }
}

/**
 * Every repository call the chat-window tests do not exercise.
 *
 * The interface has abstract members (a repository that cannot answer them is a
 * bug, not a default), so the double delegates here rather than restating thirty
 * signatures - and a test that accidentally reaches one of them fails loudly with
 * this name in the stack, which is the useful kind of noise.
 */
internal object UnsupportedRepository : AniListRepository {
    override suspend fun getViewer(): Result<com.anisequel.data.model.ViewerProfile> = throw UnsupportedOperationException()
    override suspend fun getUserByName(userName: String): Result<com.anisequel.data.model.ViewerProfile> = throw UnsupportedOperationException()
    override suspend fun getUserAnimeList(userId: Int, forceRefresh: Boolean): Result<com.anisequel.data.model.MediaListCollection> = throw UnsupportedOperationException()
    override suspend fun getUserAnimeListByUsername(userName: String, forceRefresh: Boolean): Result<com.anisequel.data.model.MediaListCollection> = throw UnsupportedOperationException()
    override suspend fun getMediaDetail(mediaId: Int, mediaType: String?): Result<com.anisequel.data.model.MediaNode> = throw UnsupportedOperationException()
    override suspend fun addToPlanning(mediaId: Int): Result<com.anisequel.data.model.SimpleMediaListEntry> = throw UnsupportedOperationException()
    override fun getDemoProfile(): com.anisequel.data.model.ViewerProfile = throw UnsupportedOperationException()
    override fun getDemoAnimeList(): com.anisequel.data.model.MediaListCollection = throw UnsupportedOperationException()
}
