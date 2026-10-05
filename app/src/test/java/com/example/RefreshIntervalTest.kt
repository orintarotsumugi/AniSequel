package com.example

import com.example.data.repository.RefreshInterval
import com.example.data.repository.isRefreshDue
import com.example.data.repository.millisUntilRefreshDue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a cached anime list is due for another trip to AniList.
 *
 * ## The bug these pin
 *
 * Nothing observed the app returning to the foreground and the list cache had a
 * flat hour-long window, so reopening AniSequel answered from the same in-memory
 * collection every time - Android resumes a process rather than restarting it,
 * so "close and reopen" changed nothing the user could see. Finishing something
 * on AniList in a browser produced an identical dashboard until the hour
 * elapsed, and the only way out was the refresh button.
 *
 * The fix is a staleness decision with a boundary, so the boundary is what has
 * to be tested: `29 min` is not due at a 30-minute interval and `30 min` is, and
 * getting that edge wrong in either direction is either a stale dashboard or a
 * request spent for nothing.
 */
class RefreshIntervalTest {

    private val base = 1_700_000_000_000L
    private fun minutes(count: Long) = base + count * 60 * 1000L

    @Test
    fun `thirty minutes is what the app ships with`() {
        assertEquals(
            "the default is the interval that fixes the reopen bug; changing it " +
                    "silently reinstates a one-hour stale window nobody asked for",
            RefreshInterval.THIRTY_MINUTES,
            RefreshInterval.DEFAULT
        )
        assertEquals(30 * 60 * 1000L, RefreshInterval.THIRTY_MINUTES.staleAfterMillis)
    }

    @Test
    fun `a list inside the interval is not due`() {
        assertFalse(isRefreshDue(minutes(29), base, RefreshInterval.THIRTY_MINUTES))
        assertFalse(isRefreshDue(base, base, RefreshInterval.THIRTY_MINUTES))
    }

    /**
     * Inclusive edge. `30 min` has to be due at exactly 30 minutes, or a list
     * would sit at 30:00 waiting for a boundary it can only ever approach.
     */
    @Test
    fun `a list is due at exactly the interval and after it`() {
        assertTrue(isRefreshDue(minutes(30), base, RefreshInterval.THIRTY_MINUTES))
        assertTrue(isRefreshDue(minutes(45), base, RefreshInterval.THIRTY_MINUTES))
    }

    /** The bug itself, as an assertion: 45 minutes old, back on a 30-minute cadence. */
    @Test
    fun `a list past the interval is due, which is what reopening the app acts on`() {
        assertTrue(
            "returning to the app after 45 minutes must re-fetch under the default",
            isRefreshDue(minutes(45), base, RefreshInterval.DEFAULT)
        )
    }

    @Test
    fun `every timed interval is not due the instant it is loaded`() {
        RefreshInterval.entries
            .filter { it.staleAfterMillis > 0L }
            .forEach { interval ->
                assertFalse(
                    "${interval.displayName} must not be due the instant it is loaded",
                    isRefreshDue(base, base, interval)
                )
            }
    }

    /**
     * `ALWAYS` has a zero window, so it is due even with no elapsed time - that
     * is what "re-fetch on every return to the foreground" means, and it is why
     * the two extremes have to be excluded from the test above.
     */
    @Test
    fun `always is due at any age, including none`() {
        assertTrue(isRefreshDue(base, base, RefreshInterval.ALWAYS))
        assertTrue(isRefreshDue(base + 1, base, RefreshInterval.ALWAYS))
        assertTrue(isRefreshDue(minutes(1), base, RefreshInterval.ALWAYS))
    }

    @Test
    fun `manual only is never due`() {
        assertFalse(isRefreshDue(base, base, RefreshInterval.MANUAL_ONLY))
        assertFalse(isRefreshDue(base + 365L * 24 * 60 * 60 * 1000, base, RefreshInterval.MANUAL_ONLY))
    }

    /**
     * A timezone correction or a changed date moves the wall clock backwards.
     * Reading that as "not yet due" would pin the cached list for as long as the
     * clock stayed behind, which is the one case where keeping stale data is
     * strictly worse than spending a request.
     */
    @Test
    fun `a clock that has moved backwards counts as due`() {
        assertTrue(isRefreshDue(base, base + 60 * 1000L, RefreshInterval.THIRTY_MINUTES))
        assertTrue(isRefreshDue(base, base + 365L * 24 * 60 * 60 * 1000, RefreshInterval.ONE_HOUR))
    }

    /**
     * The wait counts down towards the interval.
     *
     * The `!!` is the assertion: a null here means the interval was mistaken for
     * one of the two that never schedule anything, and the caller would sit and
     * wait for a resume that never refreshed.
     */
    @Test
    fun `the wait counts down towards the interval`() {
        assertEquals(
            30 * 60 * 1000L,
            millisUntilRefreshDue(base, base, RefreshInterval.THIRTY_MINUTES)!!
        )
        assertEquals(
            1 * 60 * 1000L,
            millisUntilRefreshDue(minutes(29), base, RefreshInterval.THIRTY_MINUTES)!!
        )
    }

    /** Zero means "due right now", and the caller must not delay by it. */
    @Test
    fun `the wait is never negative`() {
        assertEquals(
            0L,
            millisUntilRefreshDue(minutes(45), base, RefreshInterval.THIRTY_MINUTES)!!
        )
    }

    @Test
    fun `a clock that has moved backwards waits the whole interval again`() {
        assertEquals(
            30 * 60 * 1000L,
            millisUntilRefreshDue(base, base + 60 * 1000L, RefreshInterval.THIRTY_MINUTES)!!
        )
    }

    /**
     * The half of the contract that is easy to get wrong in the dangerous
     * direction.
     *
     * A null means "nothing to schedule; the resume check decides". Scheduling
     * `ALWAYS` on a timer would re-fetch continuously, because an interval of
     * zero is due the instant it is measured - so this null is what stops the
     * app from hammering AniList in a loop whenever someone picks that option.
     */
    @Test
    fun `no timer runs for the intervals that are decided on resume alone`() {
        assertNull(millisUntilRefreshDue(base, base, RefreshInterval.ALWAYS))
        assertNull(millisUntilRefreshDue(base, base, RefreshInterval.MANUAL_ONLY))
    }

    @Test
    fun `an unrecognised stored value falls back to the shipped default`() {
        assertEquals(RefreshInterval.DEFAULT, RefreshInterval.fromStorage("sepia"))
        assertEquals(RefreshInterval.DEFAULT, RefreshInterval.fromStorage(null))
        assertEquals(RefreshInterval.DEFAULT, RefreshInterval.fromStorage(""))
        assertEquals(RefreshInterval.ONE_HOUR, RefreshInterval.fromStorage("one_hour"))
    }

    /**
     * The stored form is a contract with a preferences file that outlives the
     * build that wrote it. Renaming a value silently resets everyone's choice.
     */
    @Test
    fun `storage values are stable`() {
        assertEquals("always", RefreshInterval.ALWAYS.storageValue)
        assertEquals("fifteen_minutes", RefreshInterval.FIFTEEN_MINUTES.storageValue)
        assertEquals("thirty_minutes", RefreshInterval.THIRTY_MINUTES.storageValue)
        assertEquals("one_hour", RefreshInterval.ONE_HOUR.storageValue)
        assertEquals("manual_only", RefreshInterval.MANUAL_ONLY.storageValue)
    }

    @Test
    fun `every interval round-trips through storage`() {
        RefreshInterval.entries.forEach { interval ->
            assertEquals(
                "$interval must survive a round trip",
                interval,
                RefreshInterval.fromStorage(interval.storageValue)
            )
        }
    }
}
