package com.anisequel.domain.notifications

/**
 * Which kinds of notification the reader wants to see.
 *
 * These are local view preferences, not Android notification permission and not
 * AniList settings. Turning one off hides those rows from this app inbox; it
 * does not dismiss anything on the server, because AniList notification
 * document has no per-type dismissal for a viewer.
 */
data class NotificationPreferences(
    /** Local unread tracking. Off means no dot, no pill count, no new section. */
    val trackingEnabled: Boolean = true,
    /** Opening a row marks it read. */
    val markReadOnOpen: Boolean = true,
    val airing: Boolean = true,
    val likes: Boolean = true,
    val replies: Boolean = true,
    val mentions: Boolean = true,
    val messages: Boolean = true,
    val follows: Boolean = true,
    val mediaUpdates: Boolean = true,
    val submissions: Boolean = true
) {
    fun isEnabled(kind: NotificationKind): Boolean = when (kind) {
        NotificationKind.AIRING -> airing
        NotificationKind.ACTIVITY_LIKE,
        NotificationKind.ACTIVITY_REPLY_LIKE,
        NotificationKind.THREAD_LIKE,
        NotificationKind.THREAD_COMMENT_LIKE -> likes
        NotificationKind.ACTIVITY_REPLY,
        NotificationKind.ACTIVITY_REPLY_SUBSCRIBED,
        NotificationKind.THREAD_COMMENT_REPLY,
        NotificationKind.THREAD_COMMENT_SUBSCRIBED -> replies
        NotificationKind.ACTIVITY_MENTION,
        NotificationKind.THREAD_COMMENT_MENTION -> mentions
        NotificationKind.ACTIVITY_MESSAGE -> messages
        NotificationKind.FOLLOWING -> follows
        NotificationKind.RELATED_MEDIA_ADDITION,
        NotificationKind.MEDIA_DATA_CHANGE,
        NotificationKind.MEDIA_MERGE,
        NotificationKind.MEDIA_DELETION -> mediaUpdates
        NotificationKind.MEDIA_SUBMISSION_UPDATE -> submissions
        NotificationKind.UNKNOWN -> true
    }

    fun isFilterEnabled(filter: NotificationFilter): Boolean = when (filter) {
        NotificationFilter.ALL -> true
        NotificationFilter.AIRING -> airing
        NotificationFilter.ACTIVITY -> likes || replies || mentions || follows
        NotificationFilter.MESSAGES -> messages
        NotificationFilter.REPLIES -> replies
        NotificationFilter.MENTIONS -> mentions
        NotificationFilter.LIKES -> likes
        NotificationFilter.FOLLOWS -> follows
        NotificationFilter.MEDIA_UPDATES -> mediaUpdates
        NotificationFilter.SUBMISSIONS -> submissions
    }

    /** The chips worth drawing, in the order they are drawn. */
    fun visibleFilters(): List<NotificationFilter> =
        NotificationFilter.entries.filter(::isFilterEnabled)

    /** True when no category at all is on, so the inbox should say so. */
    val allCategoriesOff: Boolean
        get() = !airing && !likes && !replies && !mentions && !messages &&
            !follows && !mediaUpdates && !submissions
}