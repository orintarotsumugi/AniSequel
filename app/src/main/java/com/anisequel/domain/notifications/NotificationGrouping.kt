package com.anisequel.domain.notifications

/**
 * One row: either a single notification or a folded set of likes on the same
 * target.
 *
 * Folding is deliberately narrow. Two likes on the same activity are one event
 * ("three people liked your post"); two replies on the same activity are not,
 * because each reply is something a person wrote and reading one is not reading
 * the other. The same goes for mentions, follows, messages, airing rows, media
 * changes and submissions: they stay individual even when they share a target.
 */
data class NotificationGroup(
    val key: String,
    val members: List<InboxNotification>,
    val representative: InboxNotification,
    val isUnread: Boolean,
    /**
     * Unread members, which is not [members].size: a folded like row can still
     * be "new" because one person is unread while the other two were opened.
     * The New heading counts this, so it agrees with the pill.
     */
    val unreadMemberCount: Int
) {
    val ids: List<Int> get() = members.map { it.id }
    val actorCount: Int get() = actors.size
    val actors: List<NotificationActor>
        get() = members.mapNotNull { it.actor }.distinctBy { it.id }
    val newestAt: Int? get() = members.mapNotNull { it.createdAt }.maxOrNull()
}

/**
 * Folds groupable likes and leaves everything else alone.
 *
 * [notifications] must already be newest-first. The first member encountered for
 * a key becomes the representative, so the row shows the newest event and the
 * newest timestamp without a second sort. [isUnread] is supplied by the caller
 * because unread state is a property of the local read store, not of the wire
 * payload.
 */
fun groupNotifications(
    notifications: List<InboxNotification>,
    isUnread: (InboxNotification) -> Boolean
): List<NotificationGroup> {
    val groups = LinkedHashMap<String, MutableList<InboxNotification>>()
    val singles = mutableListOf<NotificationGroup>()

    for (notification in notifications) {
        val key = notification.groupKey
        if (key == null) {
            val unread = isUnread(notification)
            singles += NotificationGroup(
                key = "single:${notification.id}",
                members = listOf(notification),
                representative = notification,
                isUnread = unread,
                unreadMemberCount = if (unread) 1 else 0
            )
        } else {
            groups.getOrPut(key) { mutableListOf() } += notification
        }
    }

    val folded = groups.map { (key, members) ->
        val unread = members.count(isUnread)
        NotificationGroup(
            key = key,
            members = members,
            representative = members.first(),
            isUnread = unread > 0,
            unreadMemberCount = unread
        )
    }
    return (singles + folded).sortedWith(
        compareByDescending<NotificationGroup> { it.newestAt ?: 0 }
            .thenByDescending { it.representative.id }
    )
}