package com.example

import com.example.data.network.RequestCoalescer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
}