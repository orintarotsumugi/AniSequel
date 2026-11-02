package com.anisequel

import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.ActivityReplyWindow
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MessageNode
import com.anisequel.data.model.asThreadHead
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.viewmodel.CommentThreadStore
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A message is the first row of its own thread, not a thread of its own.
 *
 * This is the seam the two message sheets met at. The inbox used to open a
 * bubbles-and-reply-box sheet for the message body and the comment sheet for the
 * replies, so the exchange was in two places and neither could quote a line of it.
 * The comment sheet now draws the tapped message as `CommentsState.head`, and this
 * pins the two things a screenshot cannot show: that the body is mapped from the
 * message's own fields, and that the head survives the window fetch rather than
 * being left behind by it.
 */
class MessageThreadHeadTest {

    private val counterpart = FollowUser(id = 2, name = "holo", avatar = null)

    private fun message(id: Int = 77, body: String? = "**Hello** there") = MessageNode(
        id = id,
        message = body,
        isPrivate = true,
        createdAt = 1_700_000_000,
        likeCount = 4,
        replyCount = 9,
        isLiked = true,
        messengerId = 1,
        recipientId = 2,
        messenger = FollowUser(id = 1, name = "lawrence", avatar = null),
        recipient = counterpart
    )

    /** Answers the window with one reply, the way the server answers a short thread. */
    private class HeadRepository : AniListRepository by UnsupportedRepository {
        var windowReads = 0

        override suspend fun getActivityReplyWindow(
            activityId: Int,
            page: Int,
            perPage: Int
        ): Result<ActivityReplyWindow> {
            windowReads++
            return Result.success(
                ActivityReplyWindow(
                    replies = listOf(
                        ActivityReply(
                            id = 501,
                            text = "@lawrence hi",
                            user = FollowUser(id = 2, name = "holo", avatar = null)
                        )
                    ),
                    replyCount = 1,
                    page = page
                )
            )
        }
    }

    @Test
    fun `the message's own body becomes the thread's first row`() {
        val head = message().asThreadHead()

        assertEquals(77, head.id)
        assertEquals("**Hello** there", head.text)
        assertEquals(1_700_000_000, head.createdAt)
        assertEquals(4, head.likeCount)
        assertEquals("lawrence", head.user?.name)
        // The head is not likeable through the reply mutation: a message's like is
        // ToggleLikeV2(activityId), and this store only speaks reply ids. A value
        // here would enable a heart whose request lands on the wrong namespace.
        assertNull(head.isLiked)
    }

    @Test
    fun `opening a message thread keeps the message as its head`() = runTest {
        val repository = HeadRepository()
        val store = CommentThreadStore(
            repository = repository,
            scope = this,
            isDemo = false,
            viewerId = 1
        )

        store.open(activityId = 77, knownReplyCount = 9, chatView = true, head = message().asThreadHead())
        runCurrent()

        val state = store.state.value!!
        assertEquals(77, state.head?.id)
        assertEquals("the message must not also be drawn as a reply", 1, state.replies.size)
        assertFalse(
            "a reply id must never collide with the head's key",
            state.replies.any { it.id == state.head?.id }
        )
        assertEquals(1, repository.windowReads)
    }

    @Test
    fun `a comment thread has no head, so the card above the sheet is not drawn twice`() = runTest {
        val store = CommentThreadStore(
            repository = HeadRepository(),
            scope = this,
            isDemo = false,
            viewerId = 1
        )

        store.open(activityId = 12, knownReplyCount = 0, chatView = false)
        runCurrent()

        assertNull(store.state.value!!.head)
    }
}
