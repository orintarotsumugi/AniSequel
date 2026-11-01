package com.anisequel

import com.anisequel.domain.usecase.CommentWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic that decides which page of a comment thread holds the newest
 * comments.
 *
 * The bug this prevents is the quiet one: a thread that opens at its *oldest*
 * message because the last page was computed with an off-by-one. Nothing errors,
 * nothing looks broken, and the reader scrolls for a minute to find the comment
 * somebody posted two hours ago. Every case here is a count AniList can answer
 * with, including the boundary counts where integer division goes wrong.
 */
class CommentWindowTest {

    @Test
    fun `a thread shorter than a page aims at page one`() {
        assertEquals(1, CommentWindow.lastPage(replyCount = 0))
        assertEquals(1, CommentWindow.lastPage(replyCount = 1))
        assertEquals(1, CommentWindow.lastPage(replyCount = 25))
    }

    @Test
    fun `the page after the boundary is page two, and the boundary itself is page one`() {
        // 25 replies at 25 per page is one page. 26 is the first case where the
        // newest comment is not on page one, and it is the case a `/ perPage`
        // rounding error gets wrong by one page - opening at the second-newest
        // message rather than the newest.
        assertEquals(1, CommentWindow.lastPage(replyCount = 25))
        assertEquals(2, CommentWindow.lastPage(replyCount = 26))
        assertEquals(2, CommentWindow.lastPage(replyCount = 50))
        assertEquals(3, CommentWindow.lastPage(replyCount = 51))
    }

    @Test
    fun `a negative or absent count aims at the only page an empty thread has`() {
        // A stale feed row can carry a count that no longer matches a thread whose
        // newest comments were deleted. That is page one, not page zero and not a
        // throw: page zero is an invalid GraphQL argument and AniList answers it
        // with an error rather than an empty page.
        assertEquals(1, CommentWindow.lastPage(replyCount = -4))
        assertEquals(1, CommentWindow.lastPage(replyCount = 0, perPage = 10))
    }

    @Test
    fun `a guess on the same page as the truth is not worth a second request`() {
        // The feed said 30, the server says 28: both put the newest replies on
        // page 2, so the rows already fetched are the right rows. Re-fetching here
        // would spend a rate-limited request to redraw the same screen.
        assertFalse(CommentWindow.needsRetarget(guessedReplyCount = 30, realReplyCount = 28))
        assertFalse(CommentWindow.needsRetarget(guessedReplyCount = 0, realReplyCount = 0))
    }

    @Test
    fun `a guess that hides the newest page is retargeted`() {
        assertTrue(CommentWindow.needsRetarget(guessedReplyCount = 25, realReplyCount = 26))
        assertTrue(CommentWindow.needsRetarget(guessedReplyCount = 0, realReplyCount = 40))
        // And the other direction: a feed that has aged out comments the server no
        // longer has.
        assertTrue(CommentWindow.needsRetarget(guessedReplyCount = 51, realReplyCount = 49))
    }

    @Test
    fun `older walks back one page and stops at the first`() {
        assertEquals(4, CommentWindow.olderPage(5))
        assertNull(CommentWindow.olderPage(1))
        // Page zero cannot happen from a fetched window, and asking for an older
        // page from it would be a request for a page that does not exist.
        assertNull(CommentWindow.olderPage(0))
    }

    @Test
    fun `older is offered only while the thread is bigger than the rows on hand`() {
        assertTrue(CommentWindow.hasOlder(page = 3, replyCount = 90, loadedCount = 50))
        // The whole thread is fetched: there is nothing older to reach.
        assertFalse(CommentWindow.hasOlder(page = 1, replyCount = 12, loadedCount = 12))
        // Page one with more comments above is the case the server's own count
        // answers - the window is at the oldest end already.
        assertFalse(CommentWindow.hasOlder(page = 1, replyCount = 90, loadedCount = 25))
    }

    /**
     * [CommentWindow.hasOlder] is asked about the page that is *on screen*.
     *
     * The sheet used to step the page back first and then hand that page in, which
     * made the same question twice and answered "nothing older" while sitting on
     * page 2 - so page 1, the first 25 comments of any thread longer than 50, could
     * never be reached. `CommentChatWindowTest` catches the double step at the call
     * site; this pins the contract here so the two cannot drift apart again.
     */
    @Test
    fun `older is still offered at page two because page one is above it`() {
        assertTrue(CommentWindow.hasOlder(page = 2, replyCount = 60, loadedCount = 35))
    }

    @Test
    fun `the rows drawn are the window rather than everything fetched`() {
        assertEquals(25, CommentWindow.visibleRowCount(repliesLoaded = 60, shownRows = 25))
        assertEquals(60, CommentWindow.visibleRowCount(repliesLoaded = 60, shownRows = 60))
        // A window bigger than the list means the reader has revealed it all.
        assertEquals(60, CommentWindow.visibleRowCount(repliesLoaded = 60, shownRows = 75))
        // Full-thread mode keeps no window, and 0 there means the whole list.
        assertEquals(60, CommentWindow.visibleRowCount(repliesLoaded = 60, shownRows = 0))
        assertEquals(0, CommentWindow.visibleRowCount(repliesLoaded = 0, shownRows = 25))
    }

    @Test
    fun `the hidden count is what the Older button prints`() {
        assertEquals(65, CommentWindow.hiddenCount(replyCount = 90, loadedCount = 25))
        // A thread whose newest comments were deleted between two reads answers a
        // smaller count than the rows on screen. The button must not read
        // "Older -5".
        assertEquals(0, CommentWindow.hiddenCount(replyCount = 10, loadedCount = 25))
    }

    @Test
    fun `a refresh grows the window by what arrived and never shrinks it`() {
        assertEquals(30, CommentWindow.grownRowCount(shownRows = 25, addedRows = 5))
        assertEquals(25, CommentWindow.grownRowCount(shownRows = 25, addedRows = 0))
        assertEquals(25, CommentWindow.grownRowCount(shownRows = 25, addedRows = -7))
    }

    @Test
    fun `the first screen is one page, so no fetched rows are invisible`() {
        // A window smaller than the page that produced it would hide comments the
        // app already paid to fetch - and a window larger than it would mean the
        // sheet is claiming rows it never asked for.
        assertEquals(CommentWindow.PAGE_SIZE, CommentWindow.INITIAL_ROWS)
    }
}
