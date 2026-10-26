package com.anisequel

import com.anisequel.data.model.ActivityReply
import com.anisequel.ui.screens.profile.postedReplyRow
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

        assertEquals(2, postedReplyRow(replies, postedReplyId = 3, hasRefreshBanner = false))
    }

    @Test
    fun `a refresh banner above the list shifts the row by one`() {
        val replies = listOf(reply(1), reply(2), reply(3))

        assertEquals(
            "The banner is a list item, so the third reply is the fourth row.",
            3,
            postedReplyRow(replies, postedReplyId = 3, hasRefreshBanner = true)
        )
    }

    @Test
    fun `the banner shifts the first reply to the second row`() {
        assertEquals(1, postedReplyRow(listOf(reply(7)), postedReplyId = 7, hasRefreshBanner = true))
    }

    @Test
    fun `a reply that is not in the thread has no row`() {
        assertEquals(
            "Returning an index for a missing reply would scroll somewhere unrelated, " +
                "which is worse than not scrolling at all.",
            -1,
            postedReplyRow(listOf(reply(1)), postedReplyId = 99, hasRefreshBanner = true)
        )
    }
}
