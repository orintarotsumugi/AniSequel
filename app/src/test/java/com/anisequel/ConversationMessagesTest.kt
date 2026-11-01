package com.anisequel

import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MessageNode
import com.anisequel.data.model.isWith
import com.anisequel.ui.viewmodel.UserMessagesViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversation view's pairing and ordering.
 *
 * AniList cannot answer "the thread with this person" - `Page.activities` has no
 * recipient filter - so the conversation is assembled from the two inbox halves and
 * matched by the id pair. Both halves of that are worth pinning: a wrong match puts
 * somebody's other conversation in the sheet they opened, and a wrong order reads a
 * chat backwards.
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
