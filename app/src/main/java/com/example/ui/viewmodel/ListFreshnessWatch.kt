package com.example.ui.viewmodel

import com.example.data.repository.RefreshInterval
import com.example.data.repository.isRefreshDue
import com.example.data.repository.millisUntilRefreshDue
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
 * for a flat hour in [com.example.data.repository.AniListRepositoryImpl] and
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
    private val onRefresh: () -> Unit,
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
    }

    /** Zero until the first successful load. The epoch would otherwise read as 56 years old. */
    private var lastLoadedAtMillis = 0L

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
                onRefresh()
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
                onRefresh()
            }
        }
    }

    /** Stops the timer. Safe to call when not started. */
    fun stop() {
        job?.cancel()
        job = null
    }
}
