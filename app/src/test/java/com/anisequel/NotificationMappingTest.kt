package com.anisequel

import com.anisequel.data.model.MessageNode
import com.anisequel.data.model.NotificationCommentNode
import com.anisequel.data.model.NotificationNode
import com.anisequel.data.model.NotificationThreadNode
import com.anisequel.data.model.NotificationsData
import com.anisequel.data.model.NotificationsPage
import com.anisequel.data.model.NotificationsViewer
import com.anisequel.data.model.PageInfo
import com.anisequel.data.repository.parseNotifications
import com.anisequel.domain.notifications.ActivityNotification
import com.anisequel.domain.notifications.AiringNotification
import com.anisequel.domain.notifications.MessageNotification
import com.anisequel.domain.notifications.NotificationKind
import com.anisequel.domain.notifications.NotificationTarget
import com.anisequel.domain.notifications.ThreadNotification
import com.anisequel.domain.notifications.UnknownNotification
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire-to-domain boundary.
 *
 * A notification union grows without this build, and one unreadable member must
 * not take the page down with it. These cases pin the three outcomes a row can
 * have: a known type mapped to its domain shape, an unknown type kept as a
 * visible fallback, and a duplicate id collapsed without disturbing the order.
 */
class NotificationMappingTest {

    @Test
    fun `known types map without asking the UI to read a typename`() {
        val data = NotificationsData(
            viewer = NotificationsViewer(unreadNotificationCount = 4),
            page = NotificationsPage(
                pageInfo = PageInfo(hasNextPage = false),
                notifications = listOf(
                    NotificationNode(
                        id = 5,
                        typename = "AiringNotification",
                        type = "AIRING",
                        createdAt = 100,
                        episode = 3,
                        contexts = listOf("Episode 3")
                    ),
                    NotificationNode(
                        id = 6,
                        typename = "ActivityMessageNotification",
                        type = "ACTIVITY_MESSAGE",
                        createdAt = 200,
                        activityId = 11,
                        message = MessageNode(
                            id = 11,
                            message = "hello",
                            messengerId = 7
                        )
                    ),
                    NotificationNode(
                        id = 10,
                        typename = "ActivityReplyNotification",
                        type = "ACTIVITY_REPLY",
                        createdAt = 50,
                        context = "a reply",
                        activityId = null
                    )
                )
            )
        )

        val page = parseNotifications(data)

        assertEquals(3, page.notifications.size)
        assertTrue(page.notifications[0] is MessageNotification)
        assertTrue(page.notifications[1] is AiringNotification)
        val reply = page.notifications[2] as ActivityNotification
        assertEquals(NotificationKind.ACTIVITY_REPLY, reply.kind)
        assertEquals("a reply", reply.context)
        assertEquals(4, page.serverUnreadCount)
        assertFalse(page.hasMore)
    }

    @Test
    fun `an unknown typename becomes a visible fallback not a dropped row`() {
        val data = NotificationsData(
            page = NotificationsPage(
                notifications = listOf(
                    NotificationNode(
                        id = 7,
                        typename = "FutureNotification",
                        type = "FUTURE",
                        createdAt = 300,
                        context = "something new"
                    )
                )
            )
        )

        val row = parseNotifications(data).notifications.single()
        assertTrue(row is UnknownNotification)
        assertEquals("FutureNotification", (row as UnknownNotification).typename)
        assertEquals("something new", row.context)
    }

    @Test
    fun `a union member without an id is skipped instead of failing the page`() {
        val page = parseNotifications(
            NotificationsData(
                page = NotificationsPage(
                    notifications = listOf(
                        NotificationNode(id = null, typename = "FutureNotification", type = "FUTURE"),
                        NotificationNode(id = 12, typename = "FollowingNotification", type = "FOLLOWING")
                    )
                )
            )
        )

        assertEquals(listOf(12), page.notifications.map { it.id })
    }

    @Test
    fun `moshi accepts an unknown member that only carries a typename`() {
        val json = """
            {
              "Viewer": { "unreadNotificationCount": 1 },
              "Page": {
                "pageInfo": { "hasNextPage": false },
                "notifications": [ { "__typename": "FutureNotification" } ]
              }
            }
        """.trimIndent()
        val parsed = Moshi.Builder().build()
            .adapter(NotificationsData::class.java)
            .fromJson(json)

        val page = requireNotNull(parsed).page
        val node = requireNotNull(page).notifications?.single()
        assertEquals("FutureNotification", node?.typename)
        assertEquals(null, node?.id)
        assertTrue(parseNotifications(parsed).notifications.isEmpty())
    }

    @Test
    fun `a node with no typename is a fallback rather than a crash`() {
        val row = parseNotifications(
            NotificationsData(
                page = NotificationsPage(
                    notifications = listOf(NotificationNode(id = 8, context = "bare"))
                )
            )
        ).notifications.single()

        assertTrue(row is UnknownNotification)
        assertEquals(NotificationKind.UNKNOWN, row.kind)
    }

    @Test
    fun `duplicate ids are collapsed and the page is sorted newest first`() {
        val duplicate = NotificationNode(
            id = 5,
            typename = "AiringNotification",
            type = "AIRING",
            createdAt = 100
        )
        val page = parseNotifications(
            NotificationsData(
                page = NotificationsPage(
                    notifications = listOf(
                        duplicate,
                        NotificationNode(id = 5, typename = "AiringNotification", type = "AIRING", createdAt = 100),
                        NotificationNode(id = 0, typename = "FollowingNotification", type = "FOLLOWING", createdAt = 999),
                        NotificationNode(
                            id = 9,
                            typename = "ThreadCommentLikeNotification",
                            type = "THREAD_COMMENT_LIKE",
                            createdAt = null,
                            thread = NotificationThreadNode(id = 2, title = "thread"),
                            comment = NotificationCommentNode(id = 3, comment = "comment")
                        )
                    )
                )
            )
        )

        assertEquals(listOf(5, 9), page.notifications.map { it.id })
        assertTrue(page.notifications[1] is ThreadNotification)
        assertNull(page.notifications[1].createdAt)
    }

    @Test
    fun `a thread comment uses the canonical URL instead of a guessed anchor`() {
        val commentUrl = "https://anilist.co/forum/thread/2/comment/3"
        val row = parseNotifications(
            NotificationsData(
                page = NotificationsPage(
                    notifications = listOf(
                        NotificationNode(
                            id = 10,
                            typename = "ThreadCommentReplyNotification",
                            type = "THREAD_COMMENT_REPLY",
                            createdAt = 10,
                            thread = NotificationThreadNode(
                                id = 2,
                                title = "thread",
                                siteUrl = "https://anilist.co/forum/thread/2"
                            ),
                            comment = NotificationCommentNode(
                                id = 3,
                                comment = "reply",
                                siteUrl = commentUrl,
                                createdAt = 9
                            )
                        )
                    )
                )
            )
        ).notifications.single() as ThreadNotification

        val target = row.target as NotificationTarget.Thread
        assertEquals(commentUrl, target.siteUrl)
        assertEquals("reply", target.commentText)
        assertEquals(9, target.commentCreatedAt ?: 0)
    }

    @Test
    fun `a page with no notifications still reports its server count`() {
        val page = parseNotifications(
            NotificationsData(
                viewer = NotificationsViewer(unreadNotificationCount = 2),
                page = NotificationsPage(notifications = null)
            )
        )

        assertTrue(page.notifications.isEmpty())
        assertEquals(2, page.serverUnreadCount)
        // No pageInfo and no rows: the conservative answer is no further pages.
        assertFalse(page.hasMore)
    }

    @Test
    fun `a full page without pageInfo is treated as having more`() {
        val rows = (1..25).map { id ->
            NotificationNode(id = id, typename = "FollowingNotification", type = "FOLLOWING", createdAt = id)
        }
        val page = parseNotifications(NotificationsData(page = NotificationsPage(notifications = rows)))

        assertTrue(page.hasMore)
    }
}
