package com.anisequel.domain.notifications

/** What one planning pass produced, and the state the caller must persist. */
data class NotificationDeliveryPlan(
    val drafts: List<NotificationAlertDraft>,
    val state: NotificationDeliveryState,
    /** True when this pass was the silent first-run baseline. */
    val baselineEstablished: Boolean = false,
    /**
     * False when the fetched window stopped before reaching the previous cursor.
     * The cursor then stays put and the seen ids are remembered as delivered, so
     * the next pass continues the walk instead of repeating this one.
     */
    val reachedCursor: Boolean = true
)

/**
 * Decides which newly seen notifications become tray alerts.
 *
 * Pure on purpose: the failure modes worth testing are arithmetic, not Android.
 * The caller supplies the pages it managed to fetch and whether AniList said
 * there were more; everything that decides an alert - the baseline, the cursor,
 * the dedupe set, the category switches - is here.
 *
 * ## Why a watermark and a set, rather than one or the other
 *
 * AniList notification ids increase with creation time, so "delivered through
 * 12345" is a compact cursor. It is not sufficient on its own: one check is
 * bounded to a few pages, and if more events arrived than that window can hold,
 * the walk stops above the old cursor. Advancing to the highest seen id would
 * silently drop everything between the two. Leaving the cursor alone and
 * re-posting the newest page next time would duplicate alerts. The set remembers
 * what was already posted above the watermark, and `nextResumePage` says where
 * the next check continues, so a gap larger than one window still drains without
 * repeating anything.
 *
 * A first run posts nothing and anchors the cursor at the newest visible id.
 * That is the only defence against enabling alerts and receiving years of
 * history at once; it is also why a failed first fetch must not be saved as a
 * baseline - the caller only persists what this returns after a success.
 */
fun planNotificationAlerts(
    notifications: List<InboxNotification>,
    hasMore: Boolean,
    previous: NotificationDeliveryState,
    preferences: NotificationAlertPreferences,
    nowEpochMillis: Long,
    /** Where a walk that stops above the cursor should resume next time. */
    nextResumePage: Int = 1
): NotificationDeliveryPlan {
    val unique = notifications.distinctBy { it.id }
    val newestId = unique.maxOfOrNull { it.id }

    if (!previous.baselineEstablished) {
        return NotificationDeliveryPlan(
            drafts = emptyList(),
            state = previous.copy(
                baselineEstablished = true,
                // An empty inbox anchors at zero: everything that arrives later
                // is genuinely new, and everything before it never existed.
                deliveredThroughId = newestId ?: 0,
                deliveredIds = emptySet(),
                lastCheckedAtMillis = nowEpochMillis,
                lastAlertCount = 0,
                resumePage = 1
            ),
            baselineEstablished = true,
            reachedCursor = true
        )
    }

    val cursor = previous.cursor
    val candidates = unique
        .filter { it.id > cursor && it.id !in previous.deliveredIds }
        .sortedByDescending { it.id }

    // Coverage is a fact about the oldest row actually seen, not about how many
    // pages were requested. A page that happens to hold the cursor covers the
    // gap even when `hasNextPage` is still true.
    val oldestSeen = unique.minOfOrNull { it.id }
    val reachedCursor = !hasMore || unique.isEmpty() || oldestSeen == null || oldestSeen <= cursor

    val groups = groupNotifications(candidates) { true }
    val drafts = groups.mapNotNull { group ->
        if (!preferences.allows(group.representative.kind)) return@mapNotNull null
        buildNotificationAlertDraft(group)
    }

    val state = if (reachedCursor) {
        previous.copy(
            baselineEstablished = true,
            deliveredThroughId = maxOf(cursor, newestId ?: cursor),
            deliveredIds = emptySet(),
            lastCheckedAtMillis = nowEpochMillis,
            lastAlertCount = drafts.size,
            // The gap is closed, so the next run starts from the top again.
            resumePage = 1
        )
    } else {
        // Everything considered this pass is remembered, including rows whose
        // category is off: "processed" and "posted" are not the same thing, and
        // a category turned on later must not unload the events it missed.
        val tracked = (previous.deliveredIds + candidates.map { it.id })
            .sortedDescending()
            .take(MAX_TRACKED_IDS)
            .toSet()
        previous.copy(
            baselineEstablished = true,
            deliveredThroughId = cursor,
            deliveredIds = tracked,
            lastCheckedAtMillis = nowEpochMillis,
            lastAlertCount = drafts.size,
            resumePage = nextResumePage.coerceAtLeast(1)
        )
    }

    return NotificationDeliveryPlan(
        drafts = drafts,
        state = state,
        baselineEstablished = false,
        reachedCursor = reachedCursor
    )
}

/**
 * How many posted ids are remembered while a bounded walk is incomplete.
 *
 * One run fetches at most three pages (75 rows), so this is four runs' worth:
 * enough for the page overlap a resumed walk re-covers, while keeping a
 * long-running gap from growing a DataStore key without bound.
 */
private const val MAX_TRACKED_IDS = 300
