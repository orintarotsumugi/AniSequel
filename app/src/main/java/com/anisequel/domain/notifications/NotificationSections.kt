package com.anisequel.domain.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/** The two halves of the inbox: unread first, then everything already read. */
enum class NotificationSection { NEW, EARLIER }

/**
 * A date heading inside a section.
 *
 * New rows use Today / Yesterday / Earlier this week; read rows use Today /
 * This week / Older. They are different labels on purpose: "Earlier this week"
 * inside EARLIER would suggest the read rows are older than the unread ones,
 * which is not what the sections mean.
 */
enum class NotificationDateBucket(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    EARLIER_THIS_WEEK("Earlier this week"),
    THIS_WEEK("This week"),
    OLDER("Older")
}

/** One rendered heading and the groups under it. Never empty. */
data class NotificationSectionSlice(
    val section: NotificationSection,
    val bucket: NotificationDateBucket,
    val groups: List<NotificationGroup>
) {
    /** Unread members, so a folded like row counts the people still unseen. */
    val unreadCount: Int get() = groups.sumOf { it.unreadMemberCount }

    /** Every row in the slice, including read members of a folded group. */
    val memberCount: Int get() = groups.sumOf { it.members.size }
}

/**
 * Folds the already-grouped inbox into NEW and EARLIER, then buckets each by
 * the group's newest timestamp.
 *
 * Pure and clock-injected so the boundaries are testable: "yesterday" is a local
 * calendar date, not 24 hours, and a Monday-morning inbox must not put Sunday in
 * "Earlier this week". The week starts on Monday ([WeekFields.ISO]) because
 * AniList's own community is ISO-week shaped and the alternative - Locale's
 * first day - would make the same rows move between headings on a device whose
 * region changed.
 *
 * A group with no timestamp at all goes to OLDER rather than inventing "now":
 * AniList occasionally omits `createdAt`, and putting an unknown row in Today
 * is a claim the payload did not make.
 */
fun buildNotificationSections(
    groups: List<NotificationGroup>,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault()
): List<NotificationSectionSlice> {
    if (groups.isEmpty()) return emptyList()
    val today = now.atZone(zone).toLocalDate()
    val weekStart = today.with(WeekFields.ISO.dayOfWeek(), 1L)
    val buckets = LinkedHashMap<Pair<NotificationSection, NotificationDateBucket>, MutableList<NotificationGroup>>()

    for (group in groups) {
        val section = if (group.isUnread) NotificationSection.NEW else NotificationSection.EARLIER
        val bucket = bucketFor(group, section, today, weekStart, zone)
        buckets.getOrPut(section to bucket) { mutableListOf() } += group
    }

    return buckets.map { (key, members) ->
        NotificationSectionSlice(section = key.first, bucket = key.second, groups = members)
    }.sortedWith(
        compareBy<NotificationSectionSlice> { if (it.section == NotificationSection.NEW) 0 else 1 }
            .thenBy { bucketOrder(it.bucket) }
    )
}

private fun bucketFor(
    group: NotificationGroup,
    section: NotificationSection,
    today: LocalDate,
    weekStart: LocalDate,
    zone: ZoneId
): NotificationDateBucket {
    val timestamp = group.newestAt ?: return NotificationDateBucket.OLDER
    val date = Instant.ofEpochSecond(timestamp.toLong()).atZone(zone).toLocalDate()
    val new = section == NotificationSection.NEW
    return when {
        date == today -> NotificationDateBucket.TODAY
        date == today.minusDays(1) && new -> NotificationDateBucket.YESTERDAY
        !date.isBefore(weekStart) && new -> NotificationDateBucket.EARLIER_THIS_WEEK
        !date.isBefore(weekStart) -> NotificationDateBucket.THIS_WEEK
        else -> NotificationDateBucket.OLDER
    }
}

private fun bucketOrder(bucket: NotificationDateBucket): Int = when (bucket) {
    NotificationDateBucket.TODAY -> 0
    NotificationDateBucket.YESTERDAY -> 1
    NotificationDateBucket.EARLIER_THIS_WEEK -> 2
    NotificationDateBucket.THIS_WEEK -> 2
    NotificationDateBucket.OLDER -> 3
}
