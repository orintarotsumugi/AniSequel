package com.anisequel

import com.anisequel.domain.notifications.FollowNotification
import com.anisequel.domain.notifications.NotificationActor
import com.anisequel.domain.notifications.NotificationDateBucket
import com.anisequel.domain.notifications.NotificationGroup
import com.anisequel.domain.notifications.NotificationSection
import com.anisequel.domain.notifications.buildNotificationSections
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The NEW/EARLIER tree and its date headings.
 *
 * Boundaries are local calendar dates, so these tests inject both the clock and
 * the zone. A Monday-morning inbox must put Sunday in the previous week, not in
 * "Earlier this week", and a read row must not borrow the unread section's
 * "Yesterday" label.
 */
class NotificationSectionsTest {

    private val zone = ZoneId.of("UTC")

    // Thursday 26 November 2026, 12:00 UTC. ISO week starts Monday 23 November.
    private val now = Instant.parse("2026-11-26T12:00:00Z")

    private fun epoch(day: Int, hour: Int = 12): Int =
        Instant.parse("2026-11-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:00:00Z")
            .epochSecond
            .toInt()

    private fun group(id: Int, createdAt: Int?, unread: Boolean): NotificationGroup {
        val notification = FollowNotification(
            id = id,
            createdAt = createdAt,
            actor = NotificationActor(id, "user$id", null),
            context = null
        )
        return NotificationGroup(
            key = "single:$id",
            members = listOf(notification),
            representative = notification,
            isUnread = unread,
            unreadMemberCount = if (unread) 1 else 0
        )
    }

    @Test
    fun `an empty inbox has no sections`() {
        assertTrue(buildNotificationSections(emptyList(), now, zone).isEmpty())
    }

    @Test
    fun `unread rows bucket into today yesterday earlier this week and older`() {
        val slices = buildNotificationSections(
            listOf(
                group(4, epoch(26), unread = true),
                group(3, epoch(25), unread = true),
                group(2, epoch(24), unread = true),
                group(1, epoch(10), unread = true)
            ),
            now,
            zone
        )

        assertEquals(4, slices.size)
        assertTrue(slices.all { it.section == NotificationSection.NEW })
        assertEquals(
            listOf(
                NotificationDateBucket.TODAY,
                NotificationDateBucket.YESTERDAY,
                NotificationDateBucket.EARLIER_THIS_WEEK,
                NotificationDateBucket.OLDER
            ),
            slices.map { it.bucket }
        )
    }

    @Test
    fun `read rows use this week rather than yesterday`() {
        val slices = buildNotificationSections(
            listOf(
                group(3, epoch(25), unread = false),
                group(2, epoch(24), unread = false),
                group(1, epoch(10), unread = false)
            ),
            now,
            zone
        )

        assertTrue(slices.all { it.section == NotificationSection.EARLIER })
        assertEquals(
            listOf(
                NotificationDateBucket.THIS_WEEK,
                NotificationDateBucket.OLDER
            ),
            slices.map { it.bucket }
        )
    }

    @Test
    fun `a read row from today keeps its own Today heading`() {
        val slices = buildNotificationSections(listOf(group(1, epoch(26), unread = false)), now, zone)

        assertEquals(NotificationSection.EARLIER, slices.single().section)
        assertEquals(NotificationDateBucket.TODAY, slices.single().bucket)
    }

    @Test
    fun `new rows always come before earlier rows`() {
        val slices = buildNotificationSections(
            listOf(
                group(2, epoch(26), unread = false),
                group(1, epoch(26), unread = true)
            ),
            now,
            zone
        )

        assertEquals(NotificationSection.NEW, slices.first().section)
        assertEquals(NotificationSection.EARLIER, slices.last().section)
    }

    @Test
    fun `a missing timestamp is older rather than invented as today`() {
        val slices = buildNotificationSections(listOf(group(1, null, unread = true)), now, zone)

        assertEquals(NotificationDateBucket.OLDER, slices.single().bucket)
    }

    @Test
    fun `a Sunday before a Monday belongs to the previous week`() {
        // Sunday 22 November, the day before the current ISO week.
        val slices = buildNotificationSections(
            listOf(group(1, epoch(22), unread = true)),
            now,
            zone
        )

        assertEquals(NotificationDateBucket.OLDER, slices.single().bucket)
    }

    @Test
    fun `only buckets with rows are emitted and counts are member counts`() {
        val slices = buildNotificationSections(
            listOf(group(2, epoch(26), unread = true), group(1, epoch(26), unread = true)),
            now,
            zone
        )

        assertEquals(1, slices.size)
        assertEquals(2, slices.single().unreadCount)
        assertEquals(2, slices.single().memberCount)
        assertEquals(2, slices.single().groups.size)
    }

    @Test
    fun `a folded like row counts its unread members not its rows`() {
        val first = group(3, epoch(26), unread = true)
        val second = group(2, epoch(26), unread = false)
        val folded = NotificationGroup(
            key = "activity_like:7",
            members = first.members + second.members,
            representative = first.representative,
            isUnread = true,
            unreadMemberCount = 1
        )

        val slices = buildNotificationSections(listOf(folded), now, zone)

        assertEquals(1, slices.single().unreadCount)
        assertEquals(2, slices.single().memberCount)
    }
}
