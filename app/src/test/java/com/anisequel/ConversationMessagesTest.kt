package com.anisequel

import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MessageNode
import com.anisequel.data.model.isWith
import com.anisequel.ui.screens.profile.conversationLines
import com.anisequel.ui.viewmodel.UserMessagesViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversation view's pairing and ordering.
 *
 * A message's own thread is AniList's replies to that message activity, and that
 * is what the sheet reads first. The inbox is still a second source - it can hold
 * a separate message activity to the same person - so the two are merged in time
 * order. Both halves are worth pinning: a wrong match puts somebody's other
 * conversation in the sheet they opened, and a wrong order reads a chat backwards.
 *
 * `Page.activities` has no recipient filter, which is why the fallback has to
 * match by the id pair at all rather than asking for "messages with this user".
 */
class ConversationMessagesTest {

    private fun message(
        id: Int,
        from: Int,
        to: Int,
        at: Int,
        body: String = "b$id"
    ) = MessageNode(
        id = id,
        message = body,
        createdAt = at,
        recipientId = to,
        messengerId = from,
        recipient = FollowUser(id = to, name = "user$to", avatar = null),
        messenger = FollowUser(id = from, name = "user$from", avatar = null)
    )

    private fun state(received: List<MessageNode>, sent: List<MessageNode>) =
        UserMessagesViewModel.UiState(
            viewerId = VIEWER,
            received = UserMessagesViewModel.SourceState(messages = received, loaded = true),
            sent = UserMessagesViewModel.SourceState(messages = sent, loaded = true)
        )

    @Test
    fun `both directions of one exchange pair, and a third party does not`() {
        val mine = message(1, from = VIEWER, to = OTHER, at = 100)
        val theirs = message(2, from = OTHER, to = VIEWER, at = 200)
        val someoneElse = message(3, from = OTHER, to = VIEWER + 1, at = 300)

        assertTrue(mine.isWith(OTHER, VIEWER))
        assertTrue(theirs.isWith(OTHER, VIEWER))
        assertFalse("not my message at all", someoneElse.isWith(OTHER, VIEWER))
        assertFalse("no counterpart to compare against", theirs.isWith(null, VIEWER))
        assertFalse("a message that lost its ids is not evidence", theirs.copy(messengerId = null).isWith(OTHER, VIEWER))
    }

    @Test
    fun `a conversation is ordered oldest first`() {
        val state = state(
            received = listOf(message(2, from = OTHER, to = VIEWER, at = 200)),
            sent = listOf(message(1, from = VIEWER, to = OTHER, at = 100))
        )
        val thread = com.anisequel.ui.viewmodel.conversationMessages(state, OTHER, VIEWER)
        assertEquals("a chat reads downward to the newest message", listOf(1, 2), thread.map { it.id })
    }

    @Test
    fun `a message that arrived on both halves is drawn once`() {
        val twice = message(1, from = VIEWER, to = OTHER, at = 100)
        val state = state(received = listOf(twice), sent = listOf(twice))
        val thread = com.anisequel.ui.viewmodel.conversationMessages(state, OTHER, VIEWER)
        assertEquals(
            "a repeated id is a crash in a keyed list, not a duplicate row",
            1,
            thread.size
        )
    }

    @Test
    fun `a message's own replies join the inbox's timeline, oldest first`() {
        // The sheet draws two sources: the inbox rows it could match, and the
        // replies AniList keeps under the message that was opened. They used to
        // be one list because there was no second source; a reply written on the
        // site is invisible to the inbox and was simply missing from the thread.
        val inbox = listOf(
            message(2, from = OTHER, to = VIEWER, at = 200, body = "reply-ish"),
            message(1, from = VIEWER, to = OTHER, at = 100, body = "first")
        )
        val replies = listOf(
            ActivityReply(
                id = 2,
                text = "their answer",
                createdAt = 300,
                user = FollowUser(id = OTHER, name = "user$OTHER")
            ),
            ActivityReply(
                id = 3,
                text = "my answer",
                createdAt = 400,
                user = FollowUser(id = VIEWER, name = "me")
            )
        )

        val lines = conversationLines(
            messages = inbox,
            replies = replies,
            viewerId = VIEWER,
            highlightMessageId = 1
        )

        assertEquals(
            "oldest at the top, and each source on the same clock",
            listOf("first", "reply-ish", "their answer", "my answer"),
            lines.map { it.body }
        )
        assertEquals(
            "a reply id and a message id are different rows even when the number matches",
            listOf("m:1", "m:2", "r:2", "r:3"),
            lines.map { it.key }
        )
        assertTrue(lines.last().sentByViewer)
        assertTrue("a reply from the other account is theirs", !lines[2].sentByViewer)
        assertEquals(
            "only the tapped message is marked, not the reply that shares its number",
            listOf("m:1"),
            lines.filter { it.highlight }.map { it.key }
        )
    }

    @Test
    fun `a conversation nobody is named for is empty rather than the whole inbox`() {
        val state = state(
            received = listOf(message(2, from = OTHER, to = VIEWER, at = 200)),
            sent = emptyList()
        )
        assertTrue(conversationWithNothingToMatch(state).isEmpty())
    }

    private fun conversationWithNothingToMatch(
        state: UserMessagesViewModel.UiState
    ) = com.anisequel.ui.viewmodel.conversationMessages(state, null, VIEWER)

    private companion object {
        const val VIEWER = 42
        const val OTHER = 7
    }
}
