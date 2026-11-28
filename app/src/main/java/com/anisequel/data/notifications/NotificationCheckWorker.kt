package com.anisequel.data.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.anisequel.AniSequelApplication
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.network.NetworkClient
import com.anisequel.data.repository.AniListRepositoryImpl
import com.anisequel.data.repository.AniListSnapshots
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Periodic AniList check, and the only thing that posts real alerts.
 *
 * This is *polling*, not push. AniList gives this app no event stream, so a
 * "notification" is the newest rows seen on a periodic request; Android decides
 * when the work actually runs, and battery optimisation can delay it past the
 * interval. The Settings copy says that rather than promising instant delivery.
 *
 * The work is idempotent: the delivery cursor is saved only after a successful
 * post, tray ids are derived from group keys, and [runLock] keeps a manual
 * check from racing the periodic one inside this process. A retry therefore
 * continues from the same cursor instead of replaying history.
 *
 * The schedule belongs to the app, not to a run of this worker. Only the app's own
 * state changes - signing out, switching account, alerts switched off - cancel the
 * unique request. A run that cannot verify the session skips its turn, and one
 * whose retries are spent fails instead of succeeding quietly; both used to cancel
 * or short-circuit the check in ways that stopped the alerts for good with nothing
 * on screen to say why.
 */
class NotificationCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runLock.withLock { check() }

    private suspend fun check(): Result {
        // A plain Application means this is not a real app run (a test harness or
        // a WorkManager stub); there is no repository to reach, so succeed and
        // let the periodic schedule survive.
        val application = applicationContext as? AniSequelApplication
            ?: return Result.success()

        val authRepository = application.authRepository
        if (authRepository.getAccessToken().isNullOrBlank()) {
            // Deliberately no cancel here. This run cannot tell a reader who
            // signed out from one whose token the device could not read at this
            // moment - the app woken for a scheduled job, a keystore still
            // opening, a DataStore read that failed. Cancelling the unique
            // request on that guess removed every future check, so the alerts
            // stopped arriving with the schedule gone and nothing on screen to
            // say why. Signing out is the app's own signal and
            // NotificationAlertSchedulingEffect cancels there; this worker only
            // skips its turn, on the same reasoning as the permission branch
            // below.
            Log.d(TAG, "skip: no stored session")
            return Result.success()
        }

        val alertStore = NotificationAlertStore(applicationContext)
        val preferences = alertStore.loadPreferences()
        if (!preferences.enabled) {
            Log.d(TAG, "skip: background alerts are off")
            NotificationAlertsScheduler.cancel(applicationContext)
            return Result.success()
        }

        if (!SystemNotifications.isEnabled(applicationContext)) {
            // Android is blocking the post. The cursor is deliberately not
            // advanced: those events have not been delivered, and granting the
            // permission later should not throw them away. Re-enabling is also
            // what triggers the immediate catch-up check in Settings.
            Log.d(TAG, "skip: Android permission or channel is off")
            return Result.success()
        }

        val repository = AniListRepositoryImpl(
            apiService = NetworkClient.createApiService(authRepository),
            snapshots = AniListSnapshots.of(applicationContext)
        )

        val accountId = alertStore.activeAccountId() ?: run {
            val viewer = repository.getViewer().getOrElse { failure ->
                Log.d(TAG, "viewer lookup failed: ${failure.javaClass.simpleName}")
                return failure.toWorkerResult()
            }
            alertStore.recordActiveAccount(viewer.id)
            viewer.id
        }

        // An explicit object rather than a lambda SAM: the source is a suspend
        // method, and spelling the implementation out keeps this call site
        // readable about which repository call a periodic check is allowed to make.
        val source = object : NotificationPageSource {
            override suspend fun page(page: Int, forceRefresh: Boolean) =
                repository.getNotifications(page, forceRefresh)
        }
        val runner = NotificationCheckRunner(
            source = source,
            cursors = alertStore,
            sink = NotificationAlerts.sink(applicationContext)
        )
        val outcome = runner.run(accountId, preferences)
        // Counts and outcome names only. Notification titles, message bodies and
        // the access token are never logged.
        Log.d(TAG, "check account=$accountId outcome=$outcome")

        return when (outcome) {
            is NotificationCheckOutcome.Failed -> retryOrGiveUp(outcome.retryable)

            else -> Result.success()
        }
    }

    private fun Throwable.toWorkerResult(): Result {
        val kind = (this as? AniListException)?.kind
        return when (kind) {
            AniListErrorKind.INVALID_SESSION -> {
                NotificationAlertsScheduler.cancel(applicationContext)
                Result.success()
            }

            else -> retryOrGiveUp(retryable = true)
        }
    }

    /**
     * Retries a temporary failure, and stops retrying once [MAX_ATTEMPTS] are spent.
     *
     * This used to be a guard at the top of [check] that returned success once the
     * count was reached, so a period whose retries ran out did not fail - it skipped
     * the check itself, posted nothing and recorded nothing. A background check that
     * silently stops checking is indistinguishable from an AniList account with no
     * new activity. For a periodic worker [Result.failure] ends this run and the next
     * period is scheduled anyway (periodic work is only ever cancelled, never
     * finished), and for the manual one-shot it ends the retry chain the reader
     * asked for.
     */
    private fun retryOrGiveUp(retryable: Boolean): Result = when {
        !retryable -> {
            // A dead session cannot be fixed by retrying; the app will show its
            // own sign-in notice when it is next opened.
            NotificationAlertsScheduler.cancel(applicationContext)
            Result.success()
        }

        runAttemptCount + 1 >= MAX_ATTEMPTS -> Result.failure()

        else -> Result.retry()
    }

    private companion object {
        const val TAG = "NotificationCheck"
        const val MAX_ATTEMPTS = 3

        /**
         * Serialises the manual and periodic runs in this process.
         *
         * WorkManager's unique-work rules stop two requests with the same name
         * from overlapping, but "Check now" and the periodic request are two
         * names on purpose - one must not cancel the other. This lock is what
         * keeps them from reading the same cursor and posting the same events.
         */
        val runLock = Mutex()
    }
}
