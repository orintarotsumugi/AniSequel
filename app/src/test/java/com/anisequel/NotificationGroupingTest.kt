package com.anisequel

import com.anisequel.domain.notifications.ActivityNotification
import com.anisequel.domain.notifications.InboxNotification
import com.anisequel.domain.notifications.NotificationActor
import com.anisequel.domain.notifications.NotificationComment
import com.anisequel.domain.notifications.NotificationKind
import com.anisequel.domain.notifications.NotificationThread
import com.anisequel.domain.notifications.ThreadNotification
import com.anisequel.domain.notifications.groupNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Folding rules, pinned at the domain boundary.
 *
 * The failure being guarded is a row that claims more than it knows: folding two
 * different activities, two replies, or two comments under one thread into a
 * single "people liked this" line. Every test here names a target and asserts
 * that a different target stays a different row.
 */
class NotificationGroupingTest {

    private fun like(
        id: Int,
        activityId: Int?,
        actorId: Int = id,
        createdAt: Int = id
    ): ActivityNotification = ActivityNotification(
        id = id,
        kind = NotificationKind.ACTIVITY_LIKE,
        createdAt = createdAt,
        actor = NotificationActor(actorId, "user$actorId", null),
        activityId = activityId,
        context = null,
        activityText = null,
        activityUrl = null,
        media = null
    )

    private fun reply(id: Int, activityId: Int?): ActivityNotification = ActivityNotification(
        id = id,
        kind = NotificationKind.ACTIVITY_REPLY,
        createdAt = id,
        actor = NotificationActor(id, "user$id", null),
        activityId = activityId,
        context = "reply body",
        activityText = null,
        activityUrl = null,
        media = null
    )

    private fun threadLike(id: Int, threadId: Int, actorId: Int = id): ThreadNotification =
        ThreadNotification(
            id = id,
            kind = NotificationKind.THREAD_LIKE,
            createdAt = id,
            actor = NotificationActor(actorId, "user$actorId", null),
            thread = NotificationThread(threadId, "thread"),
            comment = null,
            context = null
        )

    private fun commentLike(id: Int, threadId: Int, commentId: Int?): ThreadNotification =
        ThreadNotification(
            id = id,
            kind = NotificationKind.THREAD_COMMENT_LIKE,
            createdAt = id,
            actor = NotificationActor(id, "user$id", null),
            thread = NotificationThread(threadId, "thread"),
            comment = commentId?.let { NotificationComment(it, "comment") },
            context = null
        )

    @Test
    fun `likes on the same activity fold into one row`() {
        val notifications = listOf(like(30, 7), like(29, 7), like(28, 7))
        val groups = groupNotifications(notifications) { false }

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().members.size)
        assertEquals(3, groups.single().actorCount)
        assertEquals(30, groups.single().representative.id)
        assertEquals(listOf(30, 29, 28), groups.single().ids)
    }

    @Test
    fun `likes on different activities never fold together`() {
        val groups = groupNotifications(listOf(like(30, 7), like(29, 8))) { false }

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.members.size == 1 })
    }

    @Test
    fun `a like without an activity id stays individual`() {
        val groups = groupNotifications(listOf(like(30, null), like(29, null))) { false }

        assertEquals(2, groups.size)
    }

    @Test
    fun `likes on the same thread fold into one row`() {
        val groups = groupNotifications(listOf(threadLike(30, 2), threadLike(29, 2))) { false }

        assertEquals(1, groups.size)
        assertEquals("thread_like:2", groups.single().key)
        assertEquals(listOf(30, 29), groups.single().ids)
    }

    @Test
    fun `likes on different threads never fold together`() {
        val groups = groupNotifications(listOf(threadLike(30, 2), threadLike(29, 3))) { false }

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.members.size == 1 })
    }

    @Test
    fun `likes on different replies under one activity stay individual`() {
        val replyLike = ActivityNotification(
            id = 31,
            kind = NotificationKind.ACTIVITY_REPLY_LIKE,
            createdAt = 31,
            actor = NotificationActor(31, "user31", null),
            activityId = 7,
            context = "a reply",
            activityText = null,
            activityUrl = null,
            media = null
        )
        val other = replyLike.copy(id = 32, actor = NotificationActor(32, "user32", null))

        assertEquals(2, groupNotifications(listOf(replyLike, other)) { false }.size)
    }

    @Test
    fun `replies to the same activity stay individual`() {
        val groups = groupNotifications(listOf(reply(30, 7), reply(29, 7))) { false }

        assertEquals(2, groups.size)
    }

    @Test
    fun `comment likes fold only when the comment is known`() {
        val sameComment = listOf(commentLike(30, 2, 9), commentLike(29, 2, 9))
        assertEquals(1, groupNotifications(sameComment) { false }.size)

        val differentComments = listOf(commentLike(30, 2, 9), commentLike(29, 2, 10))
        assertEquals(2, groupNotifications(differentComments) { false }.size)

        // A missing comment id cannot distinguish two likes under one thread.
        val missingComment = listOf(commentLike(30, 2, null), commentLike(29, 2, null))
        assertEquals(2, groupNotifications(missingComment) { false }.size)
    }

    @Test
    fun `a folded row is unread when any member is unread`() {
        val notifications = listOf(like(30, 7), like(29, 7), like(28, 7))
        val groups = groupNotifications(notifications) { it.id == 29 }

        assertTrue(groups.single().isUnread)
        assertEquals(1, groups.single().unreadMemberCount)
    }

    @Test
    fun `a folded row is read only when every member is read`() {
        val notifications = listOf(like(30, 7), like(29, 7), like(28, 7))
        val groups = groupNotifications(notifications) { false }

        assertFalse(groups.single().isUnread)
    }

    @Test
    fun `groups are ordered by their newest member`() {
        val oldFold = listOf(like(10, 1), like(9, 1))
        val newSingle = listOf(reply(11, 2))
        val groups = groupNotifications(oldFold + newSingle) { false }

        assertEquals(11, groups.first().representative.id)
        assertEquals(10, groups.last().representative.id)
    }

    @Test
    fun `every member keeps its own id after folding`() {
        val notifications = listOf(like(30, 7), like(29, 7))
        val ids = groupNotifications(notifications) { false }.single().ids

        assertEquals(setOf(30, 29), ids.toSet())
    }

    @Test
    fun `empty input produces no rows`() {
        assertTrue(groupNotifications(emptyList<InboxNotification>()) { false }.isEmpty())
    }
}
