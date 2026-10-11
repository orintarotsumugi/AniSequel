package com.example

import com.example.data.repository.RefreshInterval
import com.example.ui.viewmodel.ListFreshnessWatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The foreground check and the timer that re-reads a stale anime list.
 *
 * ## The bug this is the regression test for
 *
 * Nothing observed the app coming back to the foreground. The list was cached
 * for a flat hour and `DashboardViewModel` loaded once in its `init`, so
 * reopening AniSequel - which Android does by *resuming* the process, not
 * restarting it - produced the identical dashboard every time for up to an hour.
 * The list went stale the moment it was fetched, and only the refresh button or
 * the expiry of that hour produced anything new.
 *
 * The fake `onRefresh` calls `markLoaded` exactly as `DashboardViewModel.refresh`
 * does on success. That is not tidiness: without it the list stays overdue
 * forever, and the tests below would be measuring the retry floor instead of
 * the interval. Which is why every watch below is a `lateinit var` - the fake
 * has to call back into the watch that is still being constructed, and a `val`
 * cannot be read inside its own initializer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListFreshnessWatchTest {

    private val base = 1_700_000_000_000L
    private fun minutes(count: Long) = count * 60 * 1000L

    @Test
    fun `a list inside the interval is left alone when the app is resumed`() = runTest {
        var now = base
        var refreshes = 0
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.THIRTY_MINUTES },
            onRefresh = { refreshes++; watch.markLoaded(now) },
            nowMillis = { now }
        )
        watch.markLoaded(now)

        // 29 minutes of a 30-minute cadence: reopening here must cost nothing.
        now = base + minutes(29)
        watch.start()
        runCurrent()

        assertEquals("reopening the app inside the interval must not re-fetch", 0, refreshes)
        watch.stop()
    }

    /**
     * The regression itself. One hour and a quarter since the last fetch, on the
     * shipped 30-minute cadence, and the app has just come back to the
     * foreground - which is exactly the moment this used to do nothing.
     */
    @Test
    fun `a list past the interval is re-fetched the moment the app is resumed`() = runTest {
        var now = base
        var refreshes = 0
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.THIRTY_MINUTES },
            onRefresh = { refreshes++; watch.markLoaded(now) },
            nowMillis = { now }
        )
        watch.markLoaded(now)

        now = base + minutes(75)
        watch.start()
        runCurrent()

        assertEquals(1, refreshes)
        watch.stop()
    }

    /**
     * Production `onRefresh` is fire-and-forget: it starts a network fetch and
     * returns, and `markLoaded` only lands when that fetch succeeds. The loop's
     * wait used to be computed before that fetch landed, so a resume-time refresh
     * that was still in flight made the loop fire a second, identical refresh
     * once the retry floor elapsed. The re-check at fire time must cancel it.
     */
    @Test
    fun `a fetch landing during the wait is not re-fetched five minutes later`() = runTest {
        var now = base
        var refreshes = 0
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.THIRTY_MINUTES },
            // Deliberately async: like a real network fetch, it does not call
            // `markLoaded` from inside `onRefresh`.
            onRefresh = { refreshes++ },
            nowMillis = { now }
        )
        watch.markLoaded(now)

        now = base + minutes(31)
        watch.start()
        runCurrent()
        assertEquals(1, refreshes)

        // The fetch resolves a moment later, as a real request would - well
        // before the five-minute retry floor has elapsed.
        watch.markLoaded(now)
        now = base + minutes(36)

        advanceTimeBy(minutes(5))
        runCurrent()
        assertEquals(
            "data that loaded during the wait must cancel the pending fetch",
            1,
            refreshes
        )
        watch.stop()
    }
    @Test
    fun `the list re-fetches on its own once the interval passes`() = runTest {
        var now = base
        var refreshes = 0
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.THIRTY_MINUTES },
            onRefresh = { refreshes++; watch.markLoaded(now) },
            nowMillis = { now }
        )
        watch.markLoaded(now)
        watch.start()
        runCurrent()

        assertEquals(0, refreshes)

        advanceTimeBy(minutes(29))
        runCurrent()
        assertEquals("29 minutes is inside a 30-minute interval", 0, refreshes)

        advanceTimeBy(minutes(1))
        runCurrent()
        assertEquals(1, refreshes)

        advanceTimeBy(minutes(30))
        runCurrent()
        assertEquals(2, refreshes)

        watch.stop()
    }

    /**
     * Reopening repeatedly must cost one fetch per interval, not one per resume.
     * Twenty resumes across twenty minutes is the ordinary case - the user keeps
     * switching back to check - and spending a multi-megabyte request on each is
     * what the cache exists to prevent.
     */
    @Test
    fun `reopening repeatedly inside the interval never re-fetches`() = runTest {
        var now = base
        var refreshes = 0
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.THIRTY_MINUTES },
            onRefresh = { refreshes++; watch.markLoaded(now) },
            nowMillis = { now }
        )
        watch.markLoaded(now)
        watch.start()

        repeat(20) { minutesIn ->
            now = base + minutes(minutesIn.toLong())
            // A resume is a stop followed by a start: the composable disposes on
            // the way out and re-enters on the way back in.
            watch.stop()
            watch.start()
            runCurrent()
        }

        assertEquals(
            "20 opens inside a 30-minute interval must not spend a single request",
            0,
            refreshes
        )
        watch.stop()
    }

    /**
     * A failed fetch leaves the list just as overdue as it was, so a naive loop
     * asks again immediately - turning one dropped request into a tight retry
     * loop against an API the app is already being rate-limited by.
     */
    @Test
    fun `a failed refresh backs off instead of retrying in a tight loop`() = runTest {
        var now = base
        var refreshes = 0
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.THIRTY_MINUTES },
            // Deliberately does not `markLoaded`, which is what a failure looks like.
            onRefresh = { refreshes++ },
            nowMillis = { now }
        )
        watch.markLoaded(now)

        now = base + minutes(31)
        watch.start()
        runCurrent()
        assertEquals(1, refreshes)

        advanceTimeBy(minutes(1))
        runCurrent()
        assertEquals("a minute later is still inside the retry floor", 1, refreshes)

        advanceTimeBy(minutes(4))
        runCurrent()
        assertEquals(2, refreshes)

        watch.stop()
    }

    /**
     * A timer here would be a bug in the dangerous direction: an interval of
     * zero is due the instant it is measured, so the loop would re-fetch
     * continuously for as long as the app stayed open.
     */
    @Test
    fun `always refreshes on resume but never schedules a timer`() = runTest {
        var now = base
        var refreshes = 0
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.ALWAYS },
            onRefresh = { refreshes++; watch.markLoaded(now) },
            nowMillis = { now }
        )
        watch.markLoaded(now)

        now = base + 1_000L
        watch.start()
        runCurrent()
        assertEquals(1, refreshes)

        advanceTimeBy(minutes(120))
        runCurrent()
        assertEquals("always is the resume check, not a loop", 1, refreshes)
        watch.stop()
    }

    @Test
    fun `manual only never refreshes on resume or on a timer`() = runTest {
        var now = base
        var refreshes = 0
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.MANUAL_ONLY },
            onRefresh = { refreshes++; watch.markLoaded(now) },
            nowMillis = { now }
        )
        watch.markLoaded(now)

        now = base + 365L * 24 * 60 * 60 * 1000L
        watch.start()
        runCurrent()

        advanceTimeBy(24 * 60 * 60 * 1000L)
        runCurrent()

        assertEquals(0, refreshes)
        watch.stop()
    }

    /**
     * Two live loops would double every fetch - spending twice the request
     * budget the cache exists to protect - and `onPauseOrDispose` can leave the
     * composable before the pause it is waiting for has been delivered.
     *
     * Proved by letting virtual time run through two whole intervals: a
     * surviving first loop would wake on exactly the same schedule as the second
     * and the counts would climb twice as fast.
     */
    @Test
    fun `starting again replaces the previous loop rather than adding one`() = runTest {
        var now = base
        var refreshes = 0
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.THIRTY_MINUTES },
            onRefresh = { refreshes++; watch.markLoaded(now) },
            nowMillis = { now }
        )
        watch.markLoaded(now)

        now = base + minutes(31)
        watch.start()
        runCurrent()
        assertEquals(1, refreshes)

        // A second resume while the first loop is still parked in its delay. The
        // list was just loaded, so this start refreshes nothing by itself.
        watch.start()
        runCurrent()
        assertEquals(1, refreshes)

        advanceTimeBy(minutes(30))
        runCurrent()
        assertEquals("one loop means one fetch per interval", 2, refreshes)

        advanceTimeBy(minutes(30))
        runCurrent()
        assertEquals("the replaced loop must not still be running", 3, refreshes)

        watch.stop()
    }

    /**
     * Nothing loaded yet means the age reads as ~56 years, so every interval
     * including "Always" looks overdue. Firing on that would put a second fetch
     * on top of the one the ViewModel's `init` already has in flight.
     */
    @Test
    fun `nothing loaded yet is not treated as overdue`() = runTest {
        var now = base
        var refreshes = 0
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.ALWAYS },
            onRefresh = { refreshes++ },
            nowMillis = { now }
        )

        watch.start()
        runCurrent()

        assertEquals(0, refreshes)
        watch.stop()
    }

    @Test
    fun `stopping is safe when nothing was ever started`() = runTest {
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.DEFAULT },
            onRefresh = {}
        )

        watch.stop()
        watch.stop()

        assertFalse(watch.isWatching)
    }

    /**
     * A timed interval, deliberately: `Always` and `Manual only` both return from
     * the loop on the first pass, so a finished coroutine is the correct answer
     * there and `isWatching` would be false immediately after `start`.
     */
    @Test
    fun `the watch reports whether a loop is running`() = runTest {
        lateinit var watch: ListFreshnessWatch
        watch = ListFreshnessWatch(
            scope = backgroundScope,
            interval = { RefreshInterval.THIRTY_MINUTES },
            onRefresh = {}
        )

        assertFalse(watch.isWatching)
        watch.start()
        runCurrent()
        assertTrue(watch.isWatching)
        watch.stop()
        assertFalse(watch.isWatching)
    }
}
