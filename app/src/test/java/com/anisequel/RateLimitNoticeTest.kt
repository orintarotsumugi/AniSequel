package com.anisequel

import com.anisequel.data.network.RateLimitNotice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The dedupe on the app-wide rate-limit notice.
 *
 * ## The failure this prevents
 *
 * A screen does not make one request. The dashboard fires its list, viewer and
 * metadata queries together, and a 429 is the answer to all of them in the same
 * second. Without a window, each failure posted its own Toast, so the reader
 * got a stack of identical messages and could not read the one thing they
 * needed from any of them - that the wait is a minute, not a tap.
 *
 * ## Why the clock is passed in
 *
 * The window is 20 seconds of wall time, and a test that slept through it
 * would spend 20 seconds per case. `now` is a [RateLimitNotice.report]
 * parameter instead, and these tests drive the timestamps themselves.
 *
 * ## Why [RateLimitNotice.reset] is called first
 *
 * The object is process-wide, so its "last reported" timestamp survives from
 * one test method to the next; without the reset, the next test's first report
 * would sit inside the previous test's window and be silently dropped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RateLimitNoticeTest {

    @Before
    fun forgetTheLastReport() {
        RateLimitNotice.reset()
    }

    /**
     * Subscribes to the notices for the rest of this test, before any report.
     *
     * The subscription has to happen first: the flow has no replay -
     * deliberately, because replaying the last notice after a rotation would
     * toast it a second time - so a value emitted with nobody listening is
     * gone. `UnconfinedTestDispatcher` makes the collector active the moment
     * it is launched rather than one dispatch later.
     */
    private fun TestScope.collectNotices(): MutableList<Int?> {
        val seen = mutableListOf<Int?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            RateLimitNotice.events.collect { seen += it }
        }
        return seen
    }

    @Test
    fun `reports inside the window raise one notice`() = runTest {
        val seen = collectNotices()

        // One screen's requests, all refused in the same second.
        RateLimitNotice.report(retryAfterSeconds = 30, now = 1_000L)
        RateLimitNotice.report(retryAfterSeconds = 30, now = 1_100L)
        RateLimitNotice.report(retryAfterSeconds = 30, now = 6_000L)
        testScheduler.advanceUntilIdle()

        assertEquals(
            "three parallel 429s are one episode, not three toasts",
            listOf(30),
            seen
        )
    }

    @Test
    fun `a report outside the window raises a second notice`() = runTest {
        val seen = collectNotices()

        RateLimitNotice.report(retryAfterSeconds = 30, now = 1_000L)
        // Comfortably past the window rather than exactly on its edge: where
        // the edge sits is a chosen detail, the requirement is only that a
        // later episode is not swallowed by an earlier one.
        RateLimitNotice.report(
            retryAfterSeconds = 45,
            now = 1_000L + RateLimitNotice.DEDUPE_WINDOW_MILLIS * 2
        )
        testScheduler.advanceUntilIdle()

        assertEquals("a new episode must be heard", listOf(30, 45), seen)
    }

    @Test
    fun `a notice with no server wait still emits and carries null`() = runTest {
        val seen = collectNotices()

        // The GraphQL-status road has no `Retry-After` header to read, so the
        // wait is unknown - but the reader still has to be told they are rate
        // limited, and the null has to survive to the code that decides
        // whether to mention a wait.
        RateLimitNotice.report(retryAfterSeconds = null, now = 1_000L)
        testScheduler.advanceUntilIdle()

        assertEquals("an unknown wait is still a notice", listOf<Int?>(null), seen)
    }
}
