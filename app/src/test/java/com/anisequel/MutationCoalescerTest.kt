package com.anisequel

import com.anisequel.domain.usecase.MutationCoalescer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The write coalescer behind progress steppers and score sliders.
 *
 * A reader eight episodes behind taps `+` five times in a second. Each tap used
 * to be a `SaveMediaListEntry` - five mutations against an API that allows about
 * thirty requests a minute, to arrive at one number. The tests below pin the two
 * halves of the fix: the burst is one request with the settled value, and a
 * value the server already holds is no request at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MutationCoalescerTest {

    @Test
    fun `a burst of taps becomes one write of the final value`() = runTest {
        val writes = mutableListOf<Pair<Int, Int>>()
        val coalescer = MutationCoalescer<Int, Int>(
            scope = backgroundScope,
            windowMillis = 600,
            commit = { key, value -> writes += key to value }
        )
        coalescer.seed(1, 3)

        // Five taps inside the window: 4, 5, 6, 7, 8.
        (4..8).forEach { progress ->
            assertTrue(coalescer.submit(1, progress))
            advanceTimeBy(100)
            runCurrent()
        }

        assertEquals("nothing is sent while the taps are still arriving", 0, writes.size)

        advanceTimeBy(600)
        runCurrent()

        assertEquals("one request for the settled value", listOf(1 to 8), writes)
    }

    @Test
    fun `a value the server already holds sends nothing`() = runTest {
        val writes = mutableListOf<Int>()
        val coalescer = MutationCoalescer<Int, Int>(
            scope = backgroundScope,
            windowMillis = 600,
            commit = { _, value -> writes += value }
        )
        coalescer.seed(1, 8)

        // like -> unlike -> like, where "like" is 8 and "unlike" is 7: the third
        // tap restores what the server already has, so no request is justified.
        assertTrue(coalescer.submit(1, 7))
        assertFalse("restoring the committed value is a no-op", coalescer.submit(1, 8))

        advanceTimeBy(600)
        runCurrent()

        assertEquals("the pending write was cancelled, not committed", 0, writes.size)
    }

    @Test
    fun `two titles are two writes`() = runTest {
        val writes = mutableListOf<Pair<Int, Int>>()
        val coalescer = MutationCoalescer<Int, Int>(
            scope = backgroundScope,
            windowMillis = 600,
            commit = { key, value -> writes += key to value }
        )

        coalescer.submit(1, 5)
        coalescer.submit(2, 7)
        advanceTimeBy(600)
        runCurrent()

        assertEquals(
            "per-key, not global: merging these would drop one title's progress",
            setOf(1 to 5, 2 to 7),
            writes.toSet()
        )
    }

    @Test
    fun `flush waits for the pending write`() = runTest {
        var written = 0
        val coalescer = MutationCoalescer<Int, Int>(
            scope = backgroundScope,
            windowMillis = 600,
            commit = { _, _ -> written++ }
        )

        coalescer.submit(1, 4)
        val flushed = async { coalescer.flush(1) }
        advanceTimeBy(600)
        runCurrent()
        flushed.await()

        assertEquals("flush is the closing screen's last tap landing", 1, written)
    }
}
