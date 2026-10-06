package com.example

import com.example.data.network.RequestCoalescer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Identical concurrent requests must cost one network call, not N.
 *
 * The dashboard refetches the whole list whenever the ViewModel is recreated,
 * and a detail query fires each time a sheet opens. Rotating the device or
 * opening two sheets quickly used to produce two identical multi-megabyte
 * POSTs - and on AniList's degraded 30 req/min budget that is not a rounding
 * error, it is visible latency and 429s.
 */
class RequestCoalescerTest {

    @Test
    fun `concurrent identical requests share one response`() = runTest {
        val coalescer = RequestCoalescer<String>()
        var calls = 0
        val release = CompletableDeferred<Unit>()

        val results = listOf("a", "b", "c").map {
            async {
                coalescer.coalesce("GetViewer") {
                    calls++
                    release.await()
                    "response"
                }
            }
        }

        // Let all three reach the coalescer before the first call completes.
        testScheduler.advanceUntilIdle()
        release.complete(Unit)

        assertEquals(3, results.size)
        results.forEach { assertEquals("response", it.await()) }
        assertEquals(
            "three racing identical requests must produce one network call",
            1,
            calls
        )
    }

    @Test
    fun `different keys are not collapsed`() = runTest {
        val coalescer = RequestCoalescer<String>()
        var calls = 0

        val a = async { coalescer.coalesce("GetViewer") { calls++; "viewer" } }
        val b = async { coalescer.coalesce("GetMediaDetail") { calls++; "detail" } }

        assertEquals("viewer", a.await())
        assertEquals("detail", b.await())
        assertEquals(
            "two different queries are not the same request",
            2,
            calls
        )
    }

    @Test
    fun `a failure is delivered to every waiter rather than retried`() = runTest {
        val coalescer = RequestCoalescer<String>()
        var calls = 0
        val release = CompletableDeferred<Unit>()

        val waiters = listOf("a", "b").map {
            async {
                runCatching {
                    coalescer.coalesce("GetViewer") {
                        calls++
                        release.await()
                        error("Invalid token")
                    }
                }.exceptionOrNull()
            }
        }

        testScheduler.advanceUntilIdle()
        release.complete(Unit)

        waiters.forEach { assertTrue(it.await() is IllegalStateException) }
        assertEquals(
            "a failure must not be retried once per waiter",
            1,
            calls
        )
    }

    @Test
    fun `a request after a failure is allowed to retry`() = runTest {
        // If the entry were left behind, the next caller would await a
        // permanently failed deferred and this second attempt would never run.
        val coalescer = RequestCoalescer<String>()

        runCatching {
            coalescer.coalesce("GetViewer") { error("boom") }
        }
        assertEquals("recovered", coalescer.coalesce("GetViewer") { "recovered" })
    }

    @Test
    fun `sequential requests are never collapsed`() = runTest {
        // Only *concurrent* duplicates are safe to share. Collapsing across
        // time would serve a stale response to an explicit refresh.
        val coalescer = RequestCoalescer<String>()
        var calls = 0

        repeat(3) {
            assertEquals("fresh", coalescer.coalesce("GetViewer") { calls++; "fresh" })
        }

        assertEquals(3, calls)
    }

    /**
     * Claiming the in-flight slot has to be atomic.
     *
     * It used to read the map under the lock, then write the new deferred under
     * a *second* acquisition. Between the two, a second coroutine also read
     * null, also considered itself first, and also ran the block - so two
     * callers produced two requests, which is the one thing this class exists
     * to prevent.
     *
     * Many coroutines are launched at once so the window is hit even on a
     * single-threaded test dispatcher.
     */
    @Test
    fun `many simultaneous callers still produce exactly one request`() = runTest {
        val coalescer = RequestCoalescer<String>()
        var calls = 0
        val release = CompletableDeferred<Unit>()

        val waiters = List(50) {
            async {
                coalescer.coalesce("GetUserAnimeList") {
                    calls++
                    release.await()
                    "response"
                }
            }
        }

        testScheduler.advanceUntilIdle()
        release.complete(Unit)

        waiters.forEach { assertEquals("response", it.await()) }
        assertEquals(
            "the in-flight slot must be claimed in one critical section",
            1,
            calls
        )
    }

    @Test
    fun `the in-flight entry is released once the request settles`() = runTest {
        val coalescer = RequestCoalescer<String>()

        coalescer.coalesce("GetViewer") { "viewer" }

        assertEquals(
            "a settled request must not stay registered",
            0,
            coalescer.inFlightCount()
        )
    }

    @Test
    fun `a failed request also releases its slot`() = runTest {
        val coalescer = RequestCoalescer<String>()

        runCatching { coalescer.coalesce("GetViewer") { error("boom") } }

        assertEquals(0, coalescer.inFlightCount())
    }

    /**
     * Cancelling the request in flight must not cancel the callers waiting on
     * it.
     *
     * This is the load path exactly: a second `loadData` cancels the first
     * while it is still on the network, and the replacement arrives in time to
     * be handed the dying request. The exception it was sharing described the
     * *leader's* job, so the replacement died as cancelled too - no result, no
     * error, no retry, and a dashboard that sat on its spinner forever. The
     * follower now claims the slot and issues the request itself.
     */
    @Test
    fun `a cancelled leader does not cancel the callers waiting on it`() = runTest {
        val coalescer = RequestCoalescer<String>()
        var calls = 0

        val leader = async {
            coalescer.coalesce("GetUserAnimeList") {
                calls++
                awaitCancellation()
            }
        }
        testScheduler.advanceUntilIdle()

        val replacement = async {
            coalescer.coalesce("GetUserAnimeList") {
                calls++
                "response"
            }
        }
        testScheduler.advanceUntilIdle()

        leader.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals("response", replacement.await())
        assertEquals(
            "the replacement must issue the request the cancelled leader never finished",
            2,
            calls
        )
        assertEquals(0, coalescer.inFlightCount())
    }

    /**
     * The same key after a cancelled leader must work for a caller that starts
     * from scratch, not only for one that was already waiting.
     *
     * Guards the other half of the fix: a leader cancelled between completing
     * its deferred and taking the lock in its `finally` leaves a finished entry
     * in the map, and every later caller would be handed that dead deferred
     * instead of a fresh request.
     */
    @Test
    fun `a cancelled leader does not wedge the key for later callers`() = runTest {
        val coalescer = RequestCoalescer<String>()

        val leader = async {
            coalescer.coalesce("GetUserAnimeList") { awaitCancellation() }
        }
        testScheduler.advanceUntilIdle()
        leader.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals("fresh", coalescer.coalesce("GetUserAnimeList") { "fresh" })
        assertEquals(0, coalescer.inFlightCount())
    }
}