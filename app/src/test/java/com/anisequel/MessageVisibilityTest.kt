package com.anisequel

import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MessageNode
import com.anisequel.data.model.MessageSource
import com.anisequel.data.repository.MessageVisibility
import com.anisequel.ui.viewmodel.MessageFilter
import com.anisequel.ui.viewmodel.UserMessagesViewModel
import com.anisequel.ui.viewmodel.hasHiddenMessages
import com.anisequel.ui.viewmodel.isNewMessage
import com.anisequel.ui.viewmodel.visibleMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader's own view of their inbox: what a dismissal removes, and what earns the
 * new mark.
 *
 * Both are pure rules over a row and a stamp, and both were written against a list
 * that arrives from two independent queries. The failure each one guards is the
 * pairing going wrong: a dismissal that also hides the other side of the same
 * exchange, and a badge that appears on the messages the reader wrote themselves.
 */
class MessageVisibilityTest {

    private val viewer = 999
    private val other = 300

    private fun message(
        id: Int,
        sent: Boolean,
        createdAt: Int? = 1_788_000_000
    ) = MessageNode(
        id = id,
        message = "body $id",
        createdAt = createdAt,
        messengerId = if (sent) viewer else other,
        recipientId = if (sent) other else viewer,
        messenger = FollowUser(id = if (sent) viewer else other, name = "m"),
        recipient = FollowUser(id = if (sent) other else viewer, name = "r")
    )

    private fun state(
        filter: MessageFilter = MessageFilter.ALL,
        received: List<MessageNode> = emptyList(),
        sent: List<MessageNode> = emptyList(),
        visibility: MessageVisibility = MessageVisibility()
    ) = UserMessagesViewModel.UiState(
        filter = filter,
        received = UserMessagesViewModel.SourceState(messages = received, loaded = true),
        sent = UserMessagesViewModel.SourceState(messages = sent, loaded = true),
        viewerId = viewer,
        visibility = visibility
    )

    @Test
    fun `a row's side is the same test its direction line uses`() {
        assertEquals(MessageSource.SENT, message(1, sent = true).sideOf(viewer))
        assertEquals(MessageSource.RECEIVED, message(2, sent = false).sideOf(viewer))
        // A message from AniList that lost the viewer's id reads as received, which
        // is the same safe-wrong answer `isSentBy` gives.
        assertEquals(MessageSource.RECEIVED, message(3, sent = true).sideOf(null))
    }

    @Test
    fun `hiding a sent row leaves the received one alone`() {
        val visibility = MessageVisibility(hiddenSent = setOf(7))
        assertTrue(visibility.isHidden(message(7, sent = true), viewer))
        assertFalse(visibility.isHidden(message(7, sent = false), viewer))
    }

    @Test
    fun `the dismissed row goes and the merge keeps its order`() {
        val received = listOf(message(1, sent = false, createdAt = 100), message(2, sent = false, createdAt = 300))
        val sent = listOf(message(3, sent = true, createdAt = 200))
        val all = state(received = received, sent = sent)
        assertEquals(listOf(2, 3, 1), visibleMessages(all).map { it.id })

        val hidden = state(
            received = received,
            sent = sent,
            visibility = MessageVisibility(hiddenReceived = setOf(1), hiddenSent = emptySet())
        )
        assertEquals("a hidden row must not survive the merge", listOf(2, 3), visibleMessages(hidden).map { it.id })
        assertFalse(hasHiddenMessages(all))
        assertTrue(hasHiddenMessages(hidden))
    }

    /**
     * The dismissal belongs to the row, not to the chip that was selected.
     *
     * Under `All` the two sources are one list, so a row hidden from Received is
     * absent from the merge as well - and a reader who dismisses it while looking at
     * `All` does not meet it again on the next scroll of the same list.
     */
    @Test
    fun `a dismissal follows the row across both filters`() {
        val received = listOf(message(1, sent = false))
        val visibility = MessageVisibility(hiddenReceived = setOf(1))
        assertTrue(
            visibleMessages(
                state(filter = MessageFilter.RECEIVED, received = received, visibility = visibility)
            ).isEmpty()
        )
        assertTrue(
            visibleMessages(
                state(filter = MessageFilter.ALL, received = received, visibility = visibility)
            ).isEmpty()
        )
        // The other side is untouched: hiding a message they received does not make
        // the reply they sent vanish.
        assertEquals(
            listOf(2),
            visibleMessages(
                state(
                    filter = MessageFilter.ALL,
                    received = received,
                    sent = listOf(message(2, sent = true)),
                    visibility = visibility
                )
            ).map { it.id }
        )
    }

    @Test
    fun `only a received row newer than the stamp is marked new`() {
        val visibility = MessageVisibility(lastReceivedSeenAt = 500L, flagNew = true)
        assertTrue(visibility.isNew(message(1, sent = false, createdAt = 501), viewer))
        assertFalse(visibility.isNew(message(2, sent = false, createdAt = 500), viewer))
        assertFalse(visibility.isNew(message(3, sent = true, createdAt = 900), viewer))
        // A row with no time cannot be newer than anything, so it is not new.
        assertFalse(visibility.isNew(message(4, sent = false, createdAt = null), viewer))
    }

    @Test
    fun `switching the option off hides the marks without moving the stamp`() {
        val visibility = MessageVisibility(lastReceivedSeenAt = 500L, flagNew = false)
        assertFalse(visibility.isNew(message(1, sent = false, createdAt = 900), viewer))
        assertEquals(
            "turning the badge off must not clear what has been read",
            500L,
            visibility.lastReceivedSeenAt
        )
        // The same rule the card asks, through the derivation that reads the state.
        assertFalse(isNewMessage(state(visibility = visibility), message(1, sent = false, createdAt = 900)))
    }

    @Test
    fun `the empty state still answers for a fully dismissed inbox`() {
        val visibility = MessageVisibility(hiddenReceived = setOf(1), hiddenSent = setOf(2))
        val loaded = state(
            received = listOf(message(1, sent = false)),
            sent = listOf(message(2, sent = true)),
            visibility = visibility
        )
        assertTrue(visibleMessages(loaded).isEmpty())
        assertTrue(hasHiddenMessages(loaded))
    }
}
