package com.anisequel

import com.anisequel.domain.notifications.ActivityNotification
import com.anisequel.domain.notifications.AiringNotification
import com.anisequel.domain.notifications.FollowNotification
import com.anisequel.domain.notifications.InboxNotification
import com.anisequel.domain.notifications.MessageNotification
import com.anisequel.domain.notifications.NotificationActor
import com.anisequel.domain.notifications.NotificationAlertPreferences
import com.anisequel.domain.notifications.NotificationDeliveryState
import com.anisequel.domain.notifications.NotificationDestinations
import com.anisequel.domain.notifications.NotificationKind
import com.anisequel.domain.notifications.NotificationMedia
import com.anisequel.domain.notifications.UnknownNotification
import com.anisequel.domain.notifications.planNotificationAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The delivery planner: the arithmetic that decides whether a phone buzzes.
 *
 * These are the failures worth pinning rather than the happy path alone. A first
 * run must not post history, a retried run must not post the same alert twice, a
 * bounded page walk must not silently drop the rows it could not reach, and a
 * disabled category must not unload a backlog when it is turned back on.
 */
class NotificationAlertPlanTest {

    private val now = 1_000_000L

    private fun follow(id: Int, actorId: Int = id, createdAt: Int = id): FollowNotification =
        FollowNotification(
            id = id,
            createdAt = createdAt,
            actor = NotificationActor(actorId, "user$actorId", null),
            context = null
        )

    private fun like(id: Int, activityId: Int = 7, actorId: Int = id): ActivityNotification =
        ActivityNotification(
            id = id,
            kind = NotificationKind.ACTIVITY_LIKE,
            createdAt = id,
            actor = NotificationActor(actorId, "user$actorId", null),
            activityId = activityId,
            context = "the activity body",
            activityText = null,
            activityUrl = null,
            media = NotificationMedia(9, "A Title", null, "ANIME")
        )

    private fun anchored(cursor: Int, ids: Set<Int> = emptySet()): NotificationDeliveryState =
        NotificationDeliveryState(
            baselineEstablished = true,
            deliveredThroughId = cursor,
            deliveredIds = ids
        )

    @Test
    fun `the first run posts nothing and anchors at the newest id`() {
        val plan = planNotificationAlerts(
            notifications = listOf(follow(100), follow(99), follow(98)),
            hasMore = true,
            previous = NotificationDeliveryState(),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        )

        assertTrue(plan.drafts.isEmpty())
        assertTrue(plan.baselineEstablished)
        assertTrue(plan.state.baselineEstablished)
        assertEquals(100, plan.state.deliveredThroughId)
        assertEquals(now, plan.state.lastCheckedAtMillis)
    }

    @Test
    fun `an empty first page anchors at zero so the next event is genuinely new`() {
        val baseline = planNotificationAlerts(
            notifications = emptyList(),
            hasMore = false,
            previous = NotificationDeliveryState(),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        )

        assertEquals(0, baseline.state.deliveredThroughId)

        val next = planNotificationAlerts(
            notifications = listOf(follow(1)),
            hasMore = false,
            previous = baseline.state,
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now + 1
        )

        assertEquals(1, next.drafts.size)
        assertEquals(1, next.state.deliveredThroughId)
    }

    @Test
    fun `only ids above the cursor are new`() {
        val plan = planNotificationAlerts(
            notifications = listOf(follow(103), follow(102), follow(100)),
            hasMore = false,
            previous = anchored(100),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        )

        assertEquals(listOf(103, 102), plan.drafts.map { it.notificationIds.single() })
        assertEquals(103, plan.state.deliveredThroughId)
    }

    @Test
    fun `an id already remembered above the cursor is not posted again`() {
        val plan = planNotificationAlerts(
            notifications = listOf(follow(103), follow(102)),
            hasMore = false,
            previous = anchored(100, setOf(103)),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        )

        assertEquals(listOf(102), plan.drafts.map { it.notificationIds.single() })
    }

    @Test
    fun `a bounded walk that cannot reach the cursor keeps the cursor and remembers what it posted`() {
        val page = (200 downTo 126).map { follow(it) }
        val first = planNotificationAlerts(
            notifications = page,
            hasMore = true,
            previous = anchored(50),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        )

        assertFalse(first.reachedCursor)
        assertEquals(50, first.state.deliveredThroughId)
        assertTrue(first.state.deliveredIds.containsAll(page.map { it.id }))
        assertEquals(page.size, first.drafts.sumOf { it.notificationIds.size })

        // The next run sees the same window plus one newer event. Only the new
        // one may post; the rest are remembered from the previous pass.
        val second = planNotificationAlerts(
            notifications = listOf(follow(201)) + page,
            hasMore = true,
            previous = first.state,
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now + 1
        )

        assertEquals(listOf(201), second.drafts.map { it.notificationIds.single() })
        assertEquals(50, second.state.deliveredThroughId)
    }

    @Test
    fun `once the walk reaches the cursor the watermark advances and the set clears`() {
        val plan = planNotificationAlerts(
            notifications = (200 downTo 50).map { follow(it) },
            hasMore = true,
            previous = anchored(50, setOf(999)),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        )

        assertTrue(plan.reachedCursor)
        assertEquals(200, plan.state.deliveredThroughId)
        assertTrue(plan.state.deliveredIds.isEmpty())
    }

    @Test
    fun `likes on one activity become a single alert that keeps every id`() {
        val plan = planNotificationAlerts(
            notifications = listOf(like(103), like(102), like(101)),
            hasMore = false,
            previous = anchored(100),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        )

        val draft = plan.drafts.single()
        assertEquals("activity_like:7", draft.key)
        assertEquals(setOf(103, 102, 101), draft.notificationIds.toSet())
        assertTrue(draft.title.contains("liked your activity"))
        assertTrue(draft.title.contains("others"))
        assertEquals("the activity body", draft.text)
    }

    @Test
    fun `likes on different activities stay separate alerts`() {
        val plan = planNotificationAlerts(
            notifications = listOf(like(103, activityId = 7), like(102, activityId = 8)),
            hasMore = false,
            previous = anchored(100),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        )

        assertEquals(2, plan.drafts.size)
        assertEquals(setOf("activity_like:7", "activity_like:8"), plan.drafts.map { it.key }.toSet())
    }

    @Test
    fun `a disabled category posts nothing but still advances the cursor`() {
        val off = NotificationAlertPreferences(enabled = true, likes = false)
        val plan = planNotificationAlerts(
            notifications = listOf(like(103)),
            hasMore = false,
            previous = anchored(100),
            preferences = off,
            nowEpochMillis = now
        )

        assertTrue(plan.drafts.isEmpty())
        assertEquals(103, plan.state.deliveredThroughId)

        val backOn = planNotificationAlerts(
            notifications = listOf(like(103)),
            hasMore = false,
            previous = plan.state,
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now + 1
        )

        assertTrue("turning a category back on must not unload a backlog", backOn.drafts.isEmpty())
    }

    @Test
    fun `airing copy names the title and the episode`() {
        val airing = AiringNotification(
            id = 300,
            createdAt = 300,
            episode = 12,
            media = NotificationMedia(5, "Frieren", "cover", "ANIME"),
            contexts = listOf("Episode 12"),
            animeId = null
        )

        val draft = planNotificationAlerts(
            notifications = listOf(airing),
            hasMore = false,
            previous = anchored(299),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        ).drafts.single()

        assertEquals("Frieren", draft.title)
        assertEquals("Episode 12 is out now.", draft.text)
        assertNull("the episode line must not be repeated as context", draft.expandedText)
        assertEquals(NotificationDestinations.media(5, "ANIME"), draft.route)
    }

    @Test
    fun `message copy uses the sender and collapses the body`() {
        val message = MessageNotification(
            id = 400,
            createdAt = 400,
            actor = NotificationActor(8, "Marco", null),
            activityId = 55,
            peerId = 8,
            peerName = "Marco",
            body = "hello\nthere   friend",
            context = null
        )

        val draft = planNotificationAlerts(
            notifications = listOf(message),
            hasMore = false,
            previous = anchored(399),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        ).drafts.single()

        assertEquals("Marco sent you a message", draft.title)
        assertEquals("hello there friend", draft.text)
        assertEquals(NotificationDestinations.MESSAGES, draft.route)
    }

    @Test
    fun `an unknown union member never becomes a generic alert`() {
        val unknown = UnknownNotification(
            id = 500,
            createdAt = 500,
            typename = "FutureNotification",
            type = "FUTURE",
            context = "something"
        )

        val plan = planNotificationAlerts(
            notifications = listOf(unknown),
            hasMore = false,
            previous = anchored(499),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        )

        assertTrue(plan.drafts.isEmpty())
        assertEquals("it is still processed, not replayed forever", 500, plan.state.deliveredThroughId)
    }

    @Test
    fun `a group key is stable across checks so a newer like replaces its alert`() {
        val first = planNotificationAlerts(
            notifications = listOf(like(103), like(102)),
            hasMore = false,
            previous = anchored(100),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        ).drafts.single()

        val second = planNotificationAlerts(
            notifications = listOf(like(104)),
            hasMore = false,
            previous = anchored(103),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now + 1
        ).drafts.single()

        assertEquals(first.key, second.key)
    }

    @Test
    fun `missing optional fields still produce a drawable alert`() {
        val bare = FollowNotification(
            id = 600,
            createdAt = null,
            actor = null,
            context = null
        )

        val draft = planNotificationAlerts(
            notifications = listOf(bare),
            hasMore = false,
            previous = anchored(599),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        ).drafts.single()

        assertNotNull(draft)
        assertEquals("Someone followed you", draft.title)
        assertEquals(NotificationDestinations.INBOX, draft.route)
        assertNull(draft.timestampSeconds)
    }

    @Test
    fun `duplicate ids in one window post once`() {
        val plan = planNotificationAlerts(
            notifications = listOf(follow(101), follow(101), follow(100), follow(100)),
            hasMore = false,
            previous = anchored(99),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        )

        assertEquals(listOf(101, 100), plan.drafts.map { it.notificationIds.single() })
    }

    @Test
    fun `rows with mixed kinds keep their own alerts`() {
        val items: List<InboxNotification> = listOf(follow(103), like(102), follow(101))
        val plan = planNotificationAlerts(
            notifications = items,
            hasMore = false,
            previous = anchored(100),
            preferences = NotificationAlertPreferences(enabled = true),
            nowEpochMillis = now
        )

        assertEquals(3, plan.drafts.size)
        assertEquals(setOf(103, 102, 101), plan.drafts.flatMap { it.notificationIds }.toSet())
    }
}
