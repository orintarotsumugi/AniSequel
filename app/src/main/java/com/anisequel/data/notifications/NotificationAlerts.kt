package com.anisequel.data.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.anisequel.MainActivity
import com.anisequel.R
import com.anisequel.domain.notifications.NotificationAlertDraft
import com.anisequel.domain.notifications.NotificationAlertTone
import com.anisequel.domain.notifications.NotificationDestinations

/**
 * The only place a real event notification is posted.
 *
 * Everything here is chosen for replacement rather than accumulation:
 *
 *  - the tray id is derived from the group key, so a newer like on the same
 *    activity updates one notification instead of stacking a second;
 *  - `setOnlyAlertOnce(true)` means a retried worker run (process death, a
 *    WorkManager retry) cannot sound the same alert twice;
 *  - the tap payload is the in-app route plus every id in the group, so the
 *    destination survives the process being killed between the post and the tap.
 *
 * `VISIBILITY_PRIVATE` is deliberate: message and comment previews are shown
 * when the phone is unlocked, but a secure lock screen shows the app name and
 * title rather than the body of somebody's private reply.
 */
internal object NotificationAlerts {

    fun sink(context: Context): NotificationDeliverySink = object : NotificationDeliverySink {
        override fun canDeliver(): Boolean = canPost(context)
        override fun deliver(drafts: List<NotificationAlertDraft>): Int = post(context, drafts)
    }

    fun canPost(context: Context): Boolean = SystemNotifications.isEnabled(context)

    /**
     * Posts each draft, newest last, and returns how many landed.
     *
     * The caller only stores the new delivery cursor when this returns; if it
     * throws, nothing is marked delivered and the next run retries under the
     * same ids.
     */
    fun post(context: Context, drafts: List<NotificationAlertDraft>): Int {
        if (drafts.isEmpty()) return 0
        if (!canPost(context)) return 0
        SystemNotifications.ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return 0
        var posted = 0
        drafts.asReversed().forEach { draft ->
            val id = notificationIdFor(draft.key)
            manager.notify(id, build(context, draft, id))
            posted += 1
        }
        return posted
    }

    /**
     * Removes every AniSequel post from the tray.
     *
     * Called on sign-out, on disabling background alerts and when the signed-in
     * account changes. `cancelAll` is correct here because the app has exactly
     * two sources of notifications - this one and the diagnostic test post - and
     * both belong to the session that just ended. It would be wrong the day the
     * app posts an update-download notification that must outlive a sign-out.
     */
    fun cancelAll(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
    }

    private fun build(context: Context, draft: NotificationAlertDraft, id: Int): Notification =
        Notification.Builder(context, SystemNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(draft.title)
            .setContentText(draft.text)
            .setStyle(Notification.BigTextStyle().bigText(draft.expandedText ?: draft.text))
            .setWhen(draft.timestampSeconds?.times(1000L) ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(draft.tone.notificationCategory())
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            // A retry after process death re-posts the same id. Without this it
            // would buzz again for an event the reader was already told about.
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent(context, draft, id))
            .build()

    private fun openIntent(
        context: Context,
        draft: NotificationAlertDraft,
        requestCode: Int
    ): PendingIntent {
        // Re-checked at post time as well as at build time: the route is a
        // string, and a malformed one would push a destination that matches
        // nothing. Falling back to the inbox is always a working destination.
        val route = draft.route.takeIf(NotificationDestinations::isSafe)
            ?: NotificationDestinations.INBOX
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(SystemNotifications.EXTRA_OPEN_ROUTE, route)
            putExtra(
                SystemNotifications.EXTRA_NOTIFICATION_IDS,
                draft.notificationIds.toIntArray()
            )
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun NotificationAlertTone.notificationCategory(): String = when (this) {
        NotificationAlertTone.SOCIAL -> Notification.CATEGORY_SOCIAL
        NotificationAlertTone.MEDIA -> Notification.CATEGORY_RECOMMENDATION
        NotificationAlertTone.STATUS -> Notification.CATEGORY_STATUS
    }

    /**
     * A stable, positive tray id for a group key.
     *
     * Kept above the test post's id so a diagnostic can never replace a real
     * alert (or the other way round) no matter what a key hashes to.
     */
    internal fun notificationIdFor(key: String): Int {
        val hash = key.hashCode().toLong() and 0x7FFFFFFFL
        return (hash % 900_000L + 100_000L).toInt()
    }
}
