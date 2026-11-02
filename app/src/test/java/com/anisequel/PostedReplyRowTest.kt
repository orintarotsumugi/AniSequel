package com.anisequel

import com.anisequel.data.model.ActivityReply
import com.anisequel.ui.screens.profile.postedReplyRow
import com.anisequel.ui.screens.profile.threadRowsAboveReplies
import com.anisequel.ui.viewmodel.CommentThreadStore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the sheet scrolls after a reply is posted.
 *
 * ## The failure this pins
 *
 * The scroll was `replies.indexOfFirst { it.id == posted }`, which is the reply's
 * index in the thread - not its index in the `LazyColumn`. Those differ by one
 * whenever the refresh above the list has failed, because `CommentsThread` draws
 * the retry banner as a list item *before* the replies. A failed refresh and a
 * post in the same session is not exotic: the banner is exactly what a reader
 * sees when the network is flaky, which is also when they retry by writing.
 *
 * The consequence was not a crash and not an error message - the scroll simply
 * stopped one row early, so the reply the reader had just written sat below the
 * fold. That is the kind of bug that survives a review because the code looks
 * right, and survives a test run because nothing throws; the arithmetic is
 * pinned here instead.
 */
class PostedReplyRowTest {

    private fun reply(id: Int) = ActivityReply(id = id, text = "reply $id")

    @Test
    fun `a reply is its own index when the list has no banner`() {
        val replies = listOf(reply(1), reply(2), reply(3))

        assertEquals(2, postedReplyRow(replies, postedReplyId = 3, hasBannerRow = false))
    }

    @Test
    fun `a refresh banner above the list shifts the row by one`() {
        val replies = listOf(reply(1), reply(2), reply(3))

        assertEquals(
            "The banner is a list item, so the third reply is the fourth row.",
            3,
            postedReplyRow(replies, postedReplyId = 3, hasBannerRow = true)
        )
    }

    @Test
    fun `the banner shifts the first reply to the second row`() {
        assertEquals(1, postedReplyRow(listOf(reply(7)), postedReplyId = 7, hasBannerRow = true))
    }

    @Test
    fun `a reply that is not in the thread has no row`() {
        assertEquals(
            "Returning an index for a missing reply would scroll somewhere unrelated, " +
                "which is worse than not scrolling at all.",
            -1,
            postedReplyRow(listOf(reply(1)), postedReplyId = 99, hasBannerRow = true)
        )
    }

    @Test
    fun `an older-comments row above the thread counts like the banner does`() {
        val replies = listOf(reply(1), reply(2), reply(3))
        // Chat view opens one page of a long thread and puts an Older control above
        // it. Both that row and a failure banner sit over the replies, and either
        // one alone shifts the posted comment down by one - so the arithmetic adds
        // them rather than choosing one.
        assertEquals(
            3,
            postedReplyRow(replies, postedReplyId = 3, hasBannerRow = false, hasOlderRow = true)
        )
        assertEquals(
            4,
            postedReplyRow(replies, postedReplyId = 3, hasBannerRow = true, hasOlderRow = true)
        )
        assertEquals(
            -1,
            postedReplyRow(replies, postedReplyId = 99, hasBannerRow = true, hasOlderRow = true)
        )
    }

    /**
     * The rows the window has not drawn are not in the list either.
     *
     * `replies` is the fetched thread, and chat view draws the newest `shownRows` of
     * it. A reader who had scrolled up, walked back with Older, and *then* posted was
     * scrolled to their own comment's index in the fetched list - which counted every
     * unrevealed row above it as well. The index pointed past the end of the list,
     * `animateScrollToItem` clamped it, and the reader landed on the newest comment
     * instead of the one they had just written. Nothing threw; the scroll was simply
     * wrong, which is the family of bug this file exists for.
     */
    @Test
    fun `fetched rows the window has not drawn are not counted`() {
        val fetched = (1..60).map { reply(it) }
        // 60 fetched, the newest 25 drawn: the reader's reply (id 60) is the last of
        // the drawn ones, so its row is the drawn index 24 plus the rows above.
        assertEquals(
            24,
            postedReplyRow(fetched, postedReplyId = 60, hasBannerRow = false, hiddenRows = 35)
        )
        assertEquals(
            26,
            postedReplyRow(
                fetched,
                postedReplyId = 60,
                hasBannerRow = true,
                hasOlderRow = true,
                hiddenRows = 35
            )
        )
        // A reply that is in the fetched list but not drawn has no row to land on, and
        // saying -1 is what stops the caller from scrolling to a clamped guess.
        assertEquals(
            -1,
            postedReplyRow(fetched, postedReplyId = 5, hasBannerRow = false, hiddenRows = 35)
        )
    }

    /**
     * The rows the list draws above its first reply, counted the way the list counts them.
     *
     * The scroll that holds a reader's place when an Older page lands adds this to the
     * reply's position among the drawn rows. Two of the three conditions are read off
     * the state rather than off what the reader can see, so a drift between this and
     * `ThreadList`'s own branches is a scroll one comment off - and a reader who has no
     * idea why the thread moved is exactly the report that cannot be reproduced.
     */
    @Test
    fun `the rows above the replies are the head, the banner and the Older control`() {
        val replies = (1..25).map { reply(it) }
        val open = CommentThreadStore.CommentsState(
            activityId = 7,
            replies = replies,
            isChatView = true,
            replyCount = 25,
            shownRows = 25
        )
        assertEquals(0, threadRowsAboveReplies(open, drawnCount = 25))

        // A message thread draws the message itself first.
        val withHead = open.copy(head = reply(999))
        assertEquals(1, threadRowsAboveReplies(withHead, drawnCount = 25))

        // Any of the three failure fields puts exactly one banner row above the list,
        // not one per field that happens to be set.
        assertEquals(1, threadRowsAboveReplies(open.copy(refreshError = "nope"), drawnCount = 25))
        assertEquals(1, threadRowsAboveReplies(open.copy(windowError = "nope"), drawnCount = 25))
        assertEquals(2, threadRowsAboveReplies(withHead.copy(loadError = "nope"), drawnCount = 25))

        // The Older row appears only in chat view, and only while the server's count is
        // ahead of the rows drawn - the same test the row itself is drawn under.
        assertEquals(1, threadRowsAboveReplies(open.copy(replyCount = 90), drawnCount = 25))
        assertEquals(
            0,
            threadRowsAboveReplies(open.copy(isChatView = false, replyCount = 90), drawnCount = 25)
        )
        assertEquals(
            "a count the drawn rows already cover has nothing left to fetch",
            0,
            threadRowsAboveReplies(open.copy(replyCount = 25), drawnCount = 25)
        )
    }
}
