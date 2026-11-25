package com.anisequel.domain.notifications

/**
 * What this device has already shown the reader, per account.
 *
 * AniList API exposes a single unreadNotificationCount and no per-item read
 * flag, so the only honest way to stop an inbox resurrecting every historical
 * notification on each launch is to keep the read mark here. It is explicitly
 * local: the server count seeds the first baseline and is never displayed as an
 * authority afterwards, and this app never sends resetNotificationCount.
 *
 * The mark is an id watermark rather than a growing set of ids. AniList
 * notification ids increase monotonically, so "read through 12345" means every
 * older notification is read - including rows on pages this device has not
 * fetched and categories it has not opened. [readIds] is only for individual
 * reads above the watermark, and is cleared whenever the watermark moves past
 * them.
 */
data class NotificationReadState(
    /** False until a first page has established a baseline. */
    val anchored: Boolean = false,
    /** Every notification with an id at or below this is read. */
    val readThroughId: Int? = null,
    /** Individually read notifications above [readThroughId]. */
    val readIds: Set<Int> = emptySet()
) {
    fun isUnread(id: Int): Boolean {
        if (!anchored) return false
        if (id in readIds) return false
        val watermark = readThroughId ?: return true
        return id > watermark
    }

    fun markRead(id: Int): NotificationReadState {
        if (!anchored) return this
        val watermark = readThroughId
        if (watermark != null && id <= watermark) return this
        return copy(readIds = readIds + id)
    }

    fun markAllRead(throughId: Int): NotificationReadState {
        val watermark = maxOf(readThroughId ?: Int.MIN_VALUE, throughId)
        return copy(
            anchored = true,
            readThroughId = watermark,
            readIds = emptySet()
        )
    }

    companion object {
        /**
         * Establishes the first baseline from the first page and the server one-shot
         * unread count.
         *
         * The count tells us how many of the newest rows should start unread;
         * everything older is read. Without a count (an older response, or a
         * failed Viewer field) every row on the first page is read, which is the
         * conservative baseline: nothing historical is presented as new.
         *
         * The unread count is not stored and never used again. It is a seed, not
         * a source of truth, because the reader can mark rows read here without
         * changing AniList counter.
         */
        fun anchorFor(ids: List<Int>, serverUnreadCount: Int?): NotificationReadState {
            val sorted = ids.distinct().sortedDescending()
            if (sorted.isEmpty()) {
                return NotificationReadState(anchored = true, readThroughId = 0)
            }
            val unread = (serverUnreadCount ?: 0).coerceAtLeast(0)
            val boundary = when {
                unread <= 0 -> sorted.first()
                unread >= sorted.size -> sorted.last() - 1
                else -> sorted[unread]
            }
            return NotificationReadState(
                anchored = true,
                readThroughId = boundary.coerceAtLeast(0)
            )
        }
    }
}