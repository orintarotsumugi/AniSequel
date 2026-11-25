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

/**
 * The platform side of notifications: the runtime permission, the channel and
 * the one deliberate test post.
 *
 * The inbox itself does not post anything. It has no background worker and no
 * push service, and this object does not add one: Android notifications exist
 * here so the reader can allow or refuse the permission knowingly, and so a
 * channel exists for a future notifier to use without inventing a second
 * notification vocabulary. The test post is the honest way to prove the
 * permission and channel work on this phone.
 *
 * API 33 introduced [Manifest.permission.POST_NOTIFICATIONS]; API 31 and 32
 * only need the app-wide switch. Both states are reported separately from
 * [isEnabled], because the fix differs: one needs the runtime dialog, the other
 * needs the system notification settings page.
 */
internal object SystemNotifications {
    const val CHANNEL_ID = "anisequel_inbox"
    const val EXTRA_OPEN_NOTIFICATIONS = "anisequel.notifications.open"

    private const val CHANNEL_NAME = "AniSequel inbox"
    private const val CHANNEL_DESCRIPTION =
        "Optional alerts from AniSequel. The inbox still refreshes only when you open it or pull down."
    private const val TEST_NOTIFICATION_ID = 2101
    private const val TEST_REQUEST_CODE = 2102

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
    fun isEnabled(context: Context): Boolean {
        if (!hasRuntimePermission(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context)
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
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

    /** Posts one test notification, or returns false when Android would drop it. */
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AniSequel notifications are on")
            .setContentText("Tap to open your inbox. This is a test, not a new activity.")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "Android alerts can reach you now. AniSequel still has no background " +
                        "or push worker, so the inbox itself refreshes only when you open it " +
                        "or pull down."
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
