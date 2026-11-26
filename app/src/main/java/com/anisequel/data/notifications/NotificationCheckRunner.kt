package com.anisequel.data.notifications

import com.anisequel.data.model.NotificationPage
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.domain.notifications.InboxNotification
import com.anisequel.domain.notifications.NotificationAlertDraft
import com.anisequel.domain.notifications.NotificationAlertPreferences
import com.anisequel.domain.notifications.NotificationDeliveryState
import com.anisequel.domain.notifications.planNotificationAlerts
import java.io.IOException

/** One page of the inbox, already mapped and newest first. */
interface NotificationPageSource {
    suspend fun page(page: Int, forceRefresh: Boolean): Result<NotificationPage>
}

/** The per-account delivery cursor, kept apart from the inbox read mark. */
interface NotificationCursorStore {
    suspend fun load(accountId: Int): NotificationDeliveryState
    suspend fun save(accountId: Int, state: NotificationDeliveryState)
}

/** The Android side: whether posting is allowed, and the one post call. */
interface NotificationDeliverySink {
    fun canDeliver(): Boolean
    fun deliver(drafts: List<NotificationAlertDraft>): Int
}

/** What a check did, in terms the worker and the logs can tell apart. */
sealed interface NotificationCheckOutcome {
    data object Disabled : NotificationCheckOutcome
    data object BaselineEstablished : NotificationCheckOutcome
    data object NoNewEvents : NotificationCheckOutcome
    data class Posted(val alerts: Int) : NotificationCheckOutcome
    data object PermissionBlocked : NotificationCheckOutcome
    data class Failed(val retryable: Boolean, val reason: String) : NotificationCheckOutcome
}

/**
 * The delivery pipeline, decoupled from Android so it can be tested with fakes.
 *
 * One run:
 *  1. reads the account's cursor;
 *  2. fetches page one, and more pages only while they are needed to reach the
 *     previous cursor (bounded by [maxPages], so a dormant inbox is not walked
 *     in full every half hour);
 *  3. plans the alerts (first run establishes a silent baseline);
 *  4. posts them, then saves the new cursor.
 *
 * The save is after the post because a state saved first and lost to process
 * death would drop an alert forever. The opposite order can repeat a post, and
 * that is why the tray identity is the group key: a retry replaces the same
 * notification instead of adding a second one.
 *
 * A failed fetch returns before anything is written, so the cursor is preserved
 * and the next run sees the same events. Permission being off is treated the
 * same way once past the baseline: those events are still pending, and waking
 * the phone to throw them away is not delivery.
 */
class NotificationCheckRunner(
    private val source: NotificationPageSource,
    private val cursors: NotificationCursorStore,
    private val sink: NotificationDeliverySink,
    private val maxPages: Int = MAX_PAGES
) {

    suspend fun run(
        accountId: Int,
        preferences: NotificationAlertPreferences,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): NotificationCheckOutcome {
        if (!preferences.enabled) return NotificationCheckOutcome.Disabled

        val previous = cursors.load(accountId)
        val collected = mutableListOf<InboxNotification>()
        var page = 1
        var hasMore = false
        var fetches = 0

        // Page one is always fetched, so new arrivals are posted promptly even
        // while a backlog is being drained. When the previous run stopped above
        // the cursor, the second fetch jumps to the page it stopped on; the
        // rows in between were covered by earlier runs, and new arrivals have
        // only shifted older rows further back, never skipped them.
        while (fetches < maxPages) {
            val result = source.page(page, forceRefresh = page == 1)
            val data = result.getOrElse { return it.toOutcome() }
            fetches += 1
            collected += data.notifications
            hasMore = data.hasMore
            if (!data.hasMore) break
            if (!previous.baselineEstablished) break
            val oldest = data.notifications.minOfOrNull { it.id }
            if (oldest == null || oldest <= previous.cursor) break
            page = if (page == 1 && previous.resumePage > 1) {
                previous.resumePage
            } else {
                page + 1
            }
        }

        val plan = planNotificationAlerts(
            notifications = collected,
            hasMore = hasMore,
            previous = previous,
            preferences = preferences,
            nowEpochMillis = nowEpochMillis,
            nextResumePage = page + 1
        )

        if (plan.baselineEstablished) {
            cursors.save(accountId, plan.state)
            return NotificationCheckOutcome.BaselineEstablished
        }

        if (!sink.canDeliver()) return NotificationCheckOutcome.PermissionBlocked

        val posted = if (plan.drafts.isEmpty()) {
            0
        } else {
            try {
                sink.deliver(plan.drafts)
            } catch (failure: Exception) {
                // Nothing was saved, so the next attempt sees the same ids and
                // retries them under the same tray keys.
                return NotificationCheckOutcome.Failed(
                    retryable = true,
                    reason = failure.javaClass.simpleName
                )
            }
        }
        if (plan.drafts.isNotEmpty() && posted == 0) {
            // `canDeliver` was true a moment ago, but the platform can change
            // its mind between the check and the post. Saving here would mark
            // events delivered that never reached the tray.
            return NotificationCheckOutcome.PermissionBlocked
        }
        cursors.save(accountId, plan.state)
        return if (posted == 0) {
            NotificationCheckOutcome.NoNewEvents
        } else {
            NotificationCheckOutcome.Posted(posted)
        }
    }

    private fun Throwable.toOutcome(): NotificationCheckOutcome {
        val kind = (this as? AniListException)?.kind
        return when (kind) {
            AniListErrorKind.INVALID_SESSION ->
                NotificationCheckOutcome.Failed(retryable = false, reason = "session expired")
            AniListErrorKind.OFFLINE,
            AniListErrorKind.RATE_LIMITED,
            AniListErrorKind.SERVER ->
                NotificationCheckOutcome.Failed(retryable = true, reason = kind.name)
            null -> NotificationCheckOutcome.Failed(
                retryable = this is IOException,
                reason = javaClass.simpleName
            )
            else -> NotificationCheckOutcome.Failed(retryable = true, reason = kind.name)
        }
    }

    companion object {
        /**
         * Three pages of 25.
         *
         * Enough to cover a busy day between checks without turning a periodic
         * wake-up into a full history walk. If the window is not enough the
         * cursor does not move and the next check continues from where this one
         * stopped.
         */
        const val MAX_PAGES = 3
    }
}
