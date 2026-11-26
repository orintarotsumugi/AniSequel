package com.anisequel.domain.notifications

/**
 * Which AniList events may reach the Android notification tray.
 *
 * Deliberately separate from [NotificationPreferences], which shapes the in-app
 * inbox. The inbox switches hide rows from a screen the reader has already
 * opened; these decide whether a periodic background check wakes the phone. An
 * alert category that is off therefore never posts - it is not a card filter
 * with extra steps - and the delivery cursor still advances past those events so
 * turning the category back on cannot unload a backlog.
 *
 * The feature is opt-in. [enabled] is false by default and only the reader can
 * change that, because enabling it schedules work that runs without the app
 * being open.
 */
data class NotificationAlertPreferences(
    val enabled: Boolean = false,
    val airing: Boolean = true,
    val likes: Boolean = true,
    val replies: Boolean = true,
    val mentions: Boolean = true,
    val messages: Boolean = true,
    val follows: Boolean = true,
    val mediaUpdates: Boolean = true,
    val submissions: Boolean = true,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES
) {
    /**
     * True when this event kind is allowed to post.
     *
     * [NotificationKind.UNKNOWN] is always false. A member this build cannot
     * name has no honest title or destination, and "Something happened on
     * AniList" is exactly the generic alert the feature exists to avoid.
     */
    fun allows(kind: NotificationKind): Boolean = when (kind) {
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
        NotificationKind.UNKNOWN -> false
    }

    /** True when at least one category could post, so the master switch is worth offering. */
    val anyCategoryEnabled: Boolean
        get() = airing || likes || replies || mentions || messages ||
            follows || mediaUpdates || submissions

    companion object {
        /**
         * WorkManager refuses a periodic interval below 15 minutes. The UI only
         * offers values at or above this, and [clampInterval] is the second
         * guard because a stored value can outlive a build that offered it.
         */
        const val MIN_INTERVAL_MINUTES = 15

        /** Thirty minutes: AniList has no push stream, and this is a polling cost. */
        const val DEFAULT_INTERVAL_MINUTES = 30

        /** One day. Longer than that is indistinguishable from alerts being off. */
        const val MAX_INTERVAL_MINUTES = 24 * 60

        val INTERVAL_CHOICES = listOf(15, 30, 60, 180, 360, 720)

        fun clampInterval(minutes: Int): Int =
            minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
    }
}

/**
 * What this device has already delivered for one AniList account.
 *
 * Reading a card in the inbox is not delivery, so this is kept apart from
 * [NotificationReadState] on purpose. A reader can open AniSequel and read the
 * inbox without ever having been alerted, and a background check can deliver an
 * alert the reader has not opened yet.
 *
 * The cursor is a high id watermark plus a bounded set of ids already posted
 * *above* it. The set exists because one check is allowed to stop before it has
 * paged all the way down to the previous watermark: without it, the next check
 * would see the newest page again and post every row twice.
 */
data class NotificationDeliveryState(
    /** False until a first successful page has been seen, so history never floods. */
    val baselineEstablished: Boolean = false,
    /** Every notification at or below this id has been considered for delivery. */
    val deliveredThroughId: Int? = null,
    /** Ids above [deliveredThroughId] already posted or deliberately skipped. */
    val deliveredIds: Set<Int> = emptySet(),
    /** Epoch millis of the last successful check, for the Settings status line. */
    val lastCheckedAtMillis: Long? = null,
    /** Alerts posted by the last successful check. */
    val lastAlertCount: Int = 0,
    /**
     * The page a bounded backlog walk should ask for next.
     *
     * AniList pages notifications by offset, so a gap larger than one run's
     * three-page window cannot be closed by re-fetching page one: the same
     * first three pages come back every time. The next run always fetches page
     * one for new arrivals, then jumps here and walks forward. New rows shift
     * older rows toward higher page numbers, so this can overlap work already
     * done but cannot skip it; [deliveredIds] removes the overlap. It is 1 when
     * there is no backlog.
     */
    val resumePage: Int = 1
) {
    /** Zero means "nothing below this", not "no cursor": ids are positive. */
    val cursor: Int get() = deliveredThroughId ?: 0

    fun wasDelivered(id: Int): Boolean = id <= cursor || id in deliveredIds
}
