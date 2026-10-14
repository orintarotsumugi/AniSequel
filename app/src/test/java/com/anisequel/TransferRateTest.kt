package com.anisequel

import com.anisequel.ui.components.update.TransferRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The speed shown under the download bar.
 *
 * ## Why this is measured over a window
 *
 * The obvious implementation is "bytes so far divided by seconds so far", and it
 * reports the wrong thing on a phone. A transfer that stalls for a minute in a
 * tunnel and then runs at full speed averages out to something the connection
 * is not currently doing - and the user, watching a bar that is visibly moving,
 * concludes it has stopped.
 *
 * So the reading is over the last couple of seconds. What that costs is visible
 * here: a spike is forgotten within the window, and a transfer that restarts
 * never averages backwards.
 *
 * The readings below are hand-written rather than captured from a download,
 * which is the point - the awkward cases (a resume that opens at a byte count
 * no connection reached, two samples in the same millisecond, a restart) are
 * precisely the ones a real download produces rarely enough to go unnoticed.
 */
class TransferRateTest {

    @Test
    fun `a steady transfer reports its own speed`() {
        val rate = TransferRate()

        // 64 KB each second: the rate is 65536 bytes per second.
        var reading: Long? = null
        repeat(5) { second ->
            reading = rate.sample(65_536L * (second + 1), nowMillis = second * 1_000L)
        }

        assertEquals(65_536L, reading)
    }

    @Test
    fun `the first reading has nothing to compare against`() {
        val rate = TransferRate()

        // One sample is not a rate. Reporting the bytes themselves divided by
        // zero, or by one millisecond, would put a nonsense number on screen in
        // the first second of every transfer.
        assertNull(rate.sample(32_768L, nowMillis = 0L))
    }

    @Test
    fun `a resumed transfer does not open at an impossible speed`() {
        // The regression. A resumed download's first callback already carries
        // every byte from the previous attempt, so a whole-transfer average
        // opens at, say, 1 MB in 20 ms - fifty times faster than the network.
        val rate = TransferRate()

        var reading: Long? = null
        reading = rate.sample(1_048_576L, nowMillis = 100L)
        reading = rate.sample(1_081_344L, nowMillis = 200L)

        // 32 KB in 100 ms, not 1 MB in 100 ms.
        assertEquals(32_768L * 10L, reading)
    }

    @Test
    fun `a spike is forgotten once it is out of the window`() {
        val rate = TransferRate()

        rate.sample(32_768L, nowMillis = 0L)
        // One very fast second.
        rate.sample(1_048_576L, nowMillis = 1_000L)
        // Then a long, ordinary one - the spike must no longer be in the reading.
        val reading = rate.sample(1_114_112L, nowMillis = 6_000L)

        // The window is two seconds and keeps at least two samples, so what
        // survives is the slow stretch rather than the fast one.
        assertTrue(
            "expected the recent, slow rate rather than the spike, got $reading",
            reading != null && reading < 200_000L
        )
    }

    @Test
    fun `a transfer that restarts does not average backwards`() {
        val rate = TransferRate()

        rate.sample(1_048_576L, nowMillis = 0L)
        rate.sample(1_081_344L, nowMillis = 1_000L)

        // Fewer bytes than last time: a new transfer, not a slow one.
        val reading = rate.sample(32_768L, nowMillis = 2_000L)

        assertNull(
            "a backwards step is a restart, not a negative rate",
            reading
        )
    }

    @Test
    fun `reset forgets the previous transfer`() {
        val rate = TransferRate()

        rate.sample(65_536L, nowMillis = 0L)
        rate.sample(131_072L, nowMillis = 1_000L)
        rate.reset()

        assertNull(
            "after a reset there is nothing to divide",
            rate.sample(65_536L, nowMillis = 2_000L)
        )
    }

    @Test
    fun `two readings in the same millisecond report nothing rather than infinity`() {
        val rate = TransferRate()

        rate.sample(32_768L, nowMillis = 500L)

        assertNull(
            "a zero-length interval has no rate, and dividing by it would put " +
                "an unbounded number on screen",
            rate.sample(98_304L, nowMillis = 500L)
        )
    }
}