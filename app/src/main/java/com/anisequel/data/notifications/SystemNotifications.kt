package com.anisequel.data.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.anisequel.MainActivity
import com.anisequel.R

/**
 * The platform side of notifications: the runtime permission, the channel and
 * the one deliberate test post.
 *
 * The event notifications themselves are posted by [NotificationAlerts] from
 * [NotificationCheckWorker]. This object owns the vocabulary they share - the
 * channel id, the two intent extras a tap carries, and the permission state -
 * so the worker, the inbox and Settings cannot disagree about whether Android
 * would actually show anything.
 *
 * There is exactly **one** channel. An earlier build created
 * `anisequel_inbox` for the test post; adding a second channel for real events
 * would have left that one orphaned in the system settings page, silencing a
 * switch that no longer controlled anything. The existing channel is reused and
 * its name/description are updated on the next launch, which Android allows for
 * everything except importance.
 *
 * API 33 introduced [Manifest.permission.POST_NOTIFICATIONS]; API 31 and 32
 * only need the app-wide switch. Both states are reported separately from
 * [isEnabled], because the fix differs: one needs the runtime dialog, the other
 * needs the system notification settings page.
 */
internal object SystemNotifications {
    const val CHANNEL_ID = "anisequel_inbox"

    /** Legacy test-post tap: open the inbox. */
    const val EXTRA_OPEN_NOTIFICATIONS = "anisequel.notifications.open"

    /**
     * The in-app route a real event alert should open.
     *
     * A string rather than a typed payload because a `PendingIntent` can outlive
     * the process that created it: whatever is in the extras is all the next
     * process will have, and a route string is the one form that survives that
     * without a Parcelable class loading problem.
     */
    const val EXTRA_OPEN_ROUTE = "anisequel.notifications.route"

    /** Every notification id in the tapped group, so a tap can mark them read. */
    const val EXTRA_NOTIFICATION_IDS = "anisequel.notifications.ids"

    private const val CHANNEL_NAME = "AniSequel alerts"
    private const val CHANNEL_DESCRIPTION =
        "AniList activity and title alerts, plus the inbox test post. Background checks " +
            "are periodic; AniList has no instant push stream."
    private const val TEST_NOTIFICATION_ID = 2101
    private const val TEST_REQUEST_CODE = 2102

    /** Why Android would or would not show a post, in the four states a fix differs for. */
    enum class ChannelStatus {
        ALLOWED,

        /** Android 13+ and the runtime dialog has never been granted. */
        PERMISSION_MISSING,

        /** The app-wide switch is off, or the app is force-restricted by the system. */
        APP_BLOCKED,

        /** The app is allowed but this channel has been silenced. */
        CHANNEL_BLOCKED
    }

    fun hasRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * True when the runtime permission, the app switch and this channel are on.
     *
     * `areNotificationsEnabled` only answers for the app: a reader can leave that
     * on and silence this one channel, and the section used to call that
     * "Allowed" while the test post was silently dropped. The channel is created
     * here so there is an answer to read; creating it again after the reader
     * changed its importance does not overwrite that choice.
     */
    fun isEnabled(context: Context): Boolean = channelStatus(context) == ChannelStatus.ALLOWED

    fun channelStatus(context: Context): ChannelStatus {
        if (!hasRuntimePermission(context)) return ChannelStatus.PERMISSION_MISSING
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return ChannelStatus.APP_BLOCKED
        if (!manager.areNotificationsEnabled()) return ChannelStatus.APP_BLOCKED
        ensureChannel(context)
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return ChannelStatus.ALLOWED
        return if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
            ChannelStatus.CHANNEL_BLOCKED
        } else {
            ChannelStatus.ALLOWED
        }
    }

    /** False once Android has granted the runtime permission, or on API 31-32. */
    fun canRequestRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasRuntimePermission(context)

    /**
     * Creates the channel before the first post.
     *
     * Android creates it implicitly on the first notification too, but doing it
     * first means the system settings page can name and describe it even before
     * a notification has ever arrived.
     */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts one test notification, or returns false when Android would drop it.
     *
     * This stays a permission/channel diagnostic: it is the only post that is
     * not derived from an AniList event, and Settings labels it as such. A real
     * event alert goes through [NotificationAlerts] and the delivery cursor.
     */
    fun postTest(context: Context): Boolean {
        if (!isEnabled(context)) return false
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val openInbox = PendingIntent.getActivity(
            context,
            TEST_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("AniSequel notifications are on")
            .setContentText("Tap to open your inbox. This is a test, not a new activity.")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "Android allows AniSequel to post on this channel. Turn on background " +
                        "alerts in Notification settings to be woken when AniList activity " +
                        "arrives; that check is periodic, not instant push."
                )
            )
            .setContentIntent(openInbox)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        manager.notify(TEST_NOTIFICATION_ID, notification)
        return true
    }

    /**
     * Opens the screen that can actually change the answer.
     *
     * The per-app notification page is the direct route; a few OEM builds drop
     * it, so app details is the fallback. Both carry NEW_TASK because the caller
     * may hand in an application context.
     */
    fun openSystemSettings(context: Context): Boolean {
        val direct = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch(context, direct)) return true
        // Some builds register the channel-level page but not the app-level one.
        val channel = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch(context, channel)) return true
        val details = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(context, details)
    }

    private fun launch(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
}
