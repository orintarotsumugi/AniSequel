package com.anisequel.ui.viewmodel

import com.anisequel.data.repository.RefreshInterval
import com.anisequel.data.repository.isRefreshDue
import com.anisequel.data.repository.millisUntilRefreshDue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Decides when the list already on screen is due for another trip to AniList.
 *
 * ## Why this is not a method on `DashboardViewModel`
 *
 * It is a second, independent reason for that class to change - "how long is a
 * list allowed to be stale" versus "what does the recompute pipeline do" - and
 * keeping them together is what pushed `DashboardViewModel` past 700 lines.
 * More usefully, it can be tested without a ViewModel, a fake repository, a main
 * dispatcher rule and a fake clock all at once, which is the only reason the
 * behaviour below has a test at all.
 *
 * ## The failure this fixes
 *
 * Nothing observed the app coming back to the foreground. The list was cached
 * for a flat hour in [com.anisequel.data.repository.AniListRepositoryImpl] and
 * `DashboardViewModel` loaded once in its `init`, so reopening AniSequel - which
 * Android does not restart, it resumes - served the same collection. The list
 * went stale the moment it was fetched: finishing something on AniList in a
 * browser, or an episode airing, changed nothing the user could see. Only the
 * refresh gesture, or waiting out the hour, produced new data.
 *
 * So the rule is deliberately two-layered, and the two halves are not
 * interchangeable:
 *
 *  - [start] re-checks on every return to the foreground, because that is the
 *    moment someone is most likely to be looking for something new.
 *  - While the app stays open, it sleeps exactly as long as the chosen interval
 *    and then re-fetches, so a dashboard left up on a desk catches up on its own.
 *
 * WorkManager would be the wrong tool for either half. It cannot run periodic
 * work more often than every 15 minutes, it is deliberately inexact under doze,
 * and it would spend AniList's ~30 requests a minute refreshing a list nobody is
 * looking at.
 */
class ListFreshnessWatch(
    private val scope: CoroutineScope,
    /** Read fresh each time, so a change in Settings applies without a restart. */
    private val interval: () -> RefreshInterval,
    /**
     * Runs a fetch. The Boolean is `true` for a gesture the reader made and
     * `false` for an automatic one; see [requestManualRefresh].
     */
    private val onRefresh: (gesture: Boolean) -> Unit,
    /** Injectable so the interval boundary is testable rather than wall-clock bound. */
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    private companion object {
        /**
         * The shortest gap between two automatic fetches.
         *
         * A floor, not a retry policy: after a *successful* fetch the wait is the
         * user's interval, which is always longer, so this only ever binds after
         * one that failed. Without it, a failed fetch leaves the list just as
         * overdue as it was, and the loop asks again immediately - turning one
         * dropped request into a tight retry loop against an API the app is
         * already being rate-limited by.
         */
        const val MIN_RETRY_GAP_MILLIS = 5 * 60 * 1000L

        /** How long to wait before re-checking when no list has arrived yet. */
        const val NOT_LOADED_POLL_MILLIS = MIN_RETRY_GAP_MILLIS

        /**
         * The shortest gap between two gesture-driven fetches.
         *
         * A pull-to-refresh is a deliberate act: the reader has decided the list
         * they are looking at is old, and making them wait the full automatic
         * cooldown for saying so is the wrong way round. Five seconds still
         * absorbs the double-tap and the accidental second pull.
         */
        const val GESTURE_COOLDOWN_MILLIS = 5_000L

        /**
         * The shortest gap between an automatic fetch and the next attempt.
         *
         * Longer than the gesture floor because nobody asked for it. A resume
         * moments after a refresh that failed, or after the reader already
         * pulled, must not spend another request to learn the same thing. This
         * is deliberately far below [MIN_RETRY_GAP_MILLIS] - it is a debounce on
         * the *trigger*, not the retry backoff, and the loop still waits minutes
         * between its own attempts.
         */
        const val AUTO_COOLDOWN_MILLIS = 15_000L
    }

    /** Zero until the first successful load. The epoch would otherwise read as 56 years old. */
    private var lastLoadedAtMillis = 0L

    /**
     * The last time a fetch was *started*, whether or not it succeeded.
     *
     * Separate from [lastLoadedAtMillis]: a failed fetch leaves the list just as
     * stale as before, so the cooldown has to be measured from the attempt or a
     * resumed app would immediately try again. It is advanced before [onRefresh]
     * runs for the same reason.
     */
    private var lastAttemptAtMillis = 0L

    private var job: Job? = null

    /** True while the timer loop is running, for the resume path's own assertions. */
    val isWatching: Boolean
        get() = job?.isActive == true

    /**
     * Records that the list now held was fetched at [atMillis].
     *
     * Called from every path that replaces the collection - the initial load, a
     * manual refresh, an automatic one. That is the whole contract: the watch
     * has no way of observing a fetch succeed, so forgetting a call site is the
     * one mistake that shows up as data that never goes stale again.
     */
    fun markLoaded(atMillis: Long = nowMillis()) {
        lastLoadedAtMillis = atMillis
    }

    /**
     * Starts the foreground check and, if the interval calls for it, the timer.
     *
     * Restarts cleanly, so resuming twice cannot leave two loops running - two
     * would double every fetch and quietly spend the request budget the cache
     * exists to protect.
     */
    fun start() {
        stop()
        job = scope.launch {
            val loadedAt = lastLoadedAtMillis

            // Guarded on `loadedAt` rather than asked [isRefreshDue] first: with
            // nothing loaded, the age reads as ~56 years and every interval
            // including "Always" is overdue, which would fire a second fetch on
            // top of the one `init` already has in flight.
            if (loadedAt != 0L && isRefreshDue(nowMillis(), loadedAt, interval())) {
                fire(gesture = false)
            }

            while (isActive) {
                val loaded = lastLoadedAtMillis
                val wait = if (loaded == 0L) {
                    NOT_LOADED_POLL_MILLIS
                } else {
                    // null means this interval is decided entirely by the resume
                    // check above - "Always" would re-fetch continuously on a
                    // loop, and "Manual" must not re-fetch at all.
                    millisUntilRefreshDue(nowMillis(), loaded, interval()) ?: break
                }

                delay(maxOf(wait, MIN_RETRY_GAP_MILLIS))

                // Fire unless a newer load replaced the one this wait was
                // computed against. `onRefresh` is fire-and-forget, so when the
                // resume check above woke an exact on-time refresh the wait was
                // read against the *pre-*refresh timestamp while that fetch was
                // still in flight - firing again after sleeping it out used to
                // put a second, identical fetch on top of the one that had just
                // landed. The comparison is the whole check, by construction:
                // the delay was derived from "how long until this load is due",
                // so once it expires the timestamp can only have changed because
                // another load succeeded. Re-reading the clock here would add a
                // second, decoupled clock next to the sleep (the tests drive the
                // delay with `advanceTimeBy` while `nowMillis` stays pinned, and
                // the wait is the only value that carries the schedule).
                if (lastLoadedAtMillis == loaded) {
                    // The loop's own delay is already minutes long, so the short
                    // trigger cooldown would only re-check a schedule that has
                    // just proved itself. It applies to resumes and gestures.
                    fire(gesture = false, enforceCooldown = false)
                }
            }
        }
    }

    /**
     * Runs a fetch the reader explicitly asked for, unless one just ran.
     *
     * Returns whether it fired, so a caller that also owns its own loading state
     * can decide whether to set it. The automatic path is [start] and the loop;
     * this is the only entry point for a gesture, which is what makes the two
     * cooldowns distinguishable.
     */
    fun requestManualRefresh(): Boolean = fire(gesture = true)

    /**
     * Fires [onRefresh] if the cooldown for this kind of attempt has elapsed.
     *
     * The timestamp moves before the callback, not after: a fetch that throws
     * still counts as an attempt, and the next trigger must not be admitted
     * because the failure forgot to stamp the clock.
     */
    private fun fire(gesture: Boolean, enforceCooldown: Boolean = true): Boolean {
        val cooldown = if (gesture) GESTURE_COOLDOWN_MILLIS else AUTO_COOLDOWN_MILLIS
        val now = nowMillis()
        if (enforceCooldown && now - lastAttemptAtMillis < cooldown) return false
        lastAttemptAtMillis = now
        onRefresh(gesture)
        return true
    }

    /** Stops the timer. Safe to call when not started. */
    fun stop() {
        job?.cancel()
        job = null
    }
}
