package com.anisequel.data.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.anisequel.domain.notifications.NotificationAlertPreferences
import java.util.concurrent.TimeUnit

/**
 * The one owner of background-check scheduling.
 *
 * A unique periodic request, constrained to a connected network, updated in
 * place when the interval changes. WorkManager persists it across a reboot, so
 * nothing here listens for `BOOT_COMPLETED`: a second boot receiver would be a
 * second source of truth about whether the feature is on.
 *
 * Enabling alerts also enqueues an immediate one-shot check. That run posts
 * nothing - it establishes the silent baseline - which is what keeps the first
 * event after enabling from arriving alongside a year of history. Manual checks
 * use their own unique name so asking for one cannot cancel or postpone the
 * periodic schedule.
 */
object NotificationAlertsScheduler {
    private const val PERIODIC_WORK = "anisequel.notification-check.periodic"
    private const val MANUAL_WORK = "anisequel.notification-check.now"

    fun ensureScheduled(context: Context, intervalMinutes: Int) {
        val interval = NotificationAlertPreferences.clampInterval(intervalMinutes).toLong()
        val request = PeriodicWorkRequestBuilder<NotificationCheckWorker>(
            interval,
            TimeUnit.MINUTES
        )
            .setConstraints(connectedNetwork())
            // A rate-limited or offline check is retried on a rising delay
            // rather than at the default few seconds; AniList's limiter is a
            // shared bucket and hammering it makes the next check fail too.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            // UPDATE keeps one request and rewrites its interval. KEEP would
            // silently ignore a new interval, which makes the Settings choice a
            // control that does nothing until the work is cancelled by hand.
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Runs the real detection-and-delivery pipeline once, now.
     *
     * `KEEP` rather than `REPLACE`: a button pressed twice must not cancel the
     * run that is already in flight and start over. It is the same worker as the
     * periodic check, so a manual press cannot post a different vocabulary.
     */
    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<NotificationCheckWorker>()
            .setConstraints(connectedNetwork())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Stops checking and drops any queued manual run.
     *
     * Called when the reader turns background alerts off, signs out, or switches
     * account. Scheduled work is not data: cancelling it does not clear the
     * per-account cursor, so turning alerts back on resumes from the last
     * delivered id instead of replaying history.
     */
    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(MANUAL_WORK)
    }

    private fun connectedNetwork(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
