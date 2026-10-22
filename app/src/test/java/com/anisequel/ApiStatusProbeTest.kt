package com.anisequel

import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.ApiStatusSnapshot
import com.anisequel.ui.viewmodel.ApiStatusProbeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The reachability probe's own contract, independent of the screens that draw it.
 *
 * Every one of these pins a way the probe went wrong in a shipped build:
 *
 *  - a Check tapped while the automatic probe was still running was dropped
 *    silently, so the reader's tap did nothing and the strip showed the answer to
 *    a request they did not make;
 *  - a request that threw escaped into the composition's coroutine scope, which
 *    both crashed and left the strip spinning "checking" forever - the one state
 *    `ApiStatusUi.isChecking` can never recover from on its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiStatusProbeTest {

    @Test
    fun `a check tapped while one is running is queued, not dropped`() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val probe = ApiStatusProbeState(
            requestStatus = {
                calls++
                gate.await()
                ApiStatusSnapshot(ApiReachability.ONLINE, latencyMillis = calls * 10L)
            },
            scope = backgroundScope
        )

        probe.probe()
        runCurrent()
        assertEquals("the first check did not start", 1, calls)

        // The reader taps Check while the automatic probe is still in flight.
        probe.probe()
        runCurrent()
        assertEquals("the tap started a second concurrent request", 1, calls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("the tap was thrown away instead of queued", 2, calls)
        assertEquals(ApiReachability.ONLINE, probe.ui.value.reachability)
        // The verdict on screen has to be the second request's, not the first's.
        assertEquals(20L, probe.ui.value.latencyMillis)
        assertFalse(probe.ui.value.isChecking)
    }

    @Test
    fun `a new failure cancels the running check and the tap queued behind it`() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val probe = ApiStatusProbeState(
            requestStatus = {
                calls++
                gate.await()
                ApiStatusSnapshot(ApiReachability.ONLINE)
            },
            scope = backgroundScope
        )

        probe.probe()
        runCurrent()
        probe.probe()
        runCurrent()
        assertEquals(1, calls)

        // An answer that already proves AniList replied, so no new probe is
        // started and the count below is about the two requests above only.
        probe.onFailure(AniListErrorKind.INVALID_SESSION)
        runCurrent()

        assertEquals("the old question's queued tap was still asked", 1, calls)
        assertEquals(ApiReachability.ONLINE, probe.ui.value.reachability)
        assertFalse(probe.ui.value.isChecking)
    }

    @Test
    fun `a probe that throws reports no verdict instead of spinning forever`() = runTest {
        val probe = ApiStatusProbeState(
            requestStatus = { error("the socket closed under us") },
            scope = backgroundScope
        )

        probe.probe()
        advanceUntilIdle()

        assertEquals(ApiReachability.UNKNOWN, probe.ui.value.reachability)
        assertFalse("the strip was left checking forever", probe.ui.value.isChecking)
    }
}
