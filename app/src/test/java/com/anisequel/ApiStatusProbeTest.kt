package com.anisequel

import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.ApiStatusSnapshot
import com.anisequel.ui.viewmodel.ApiStatusProbeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        // `runCurrent`, not `advanceUntilIdle`: the probe is launched in
        // `backgroundScope`, and `advanceUntilIdle` intentionally stops once only
        // background coroutines are left unprocessed. It would return with the
        // gate still unread and the strip still checking, which is the very state
        // this test exists to rule out.
        runCurrent()
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
        // Same background-scope caveat as above: `advanceUntilIdle` would return
        // without running the request, so the initial UNKNOWN state would pass
        // this test without the catch ever executing.
        runCurrent()

        assertEquals(ApiReachability.UNKNOWN, probe.ui.value.reachability)
        assertFalse("the strip was left checking forever", probe.ui.value.isChecking)
    }

    /**
     * A thrown check has to be *visible*, not merely survivable.
     *
     * The catch above stopped the crash, but a probe that threw was then
     * indistinguishable from one that had not finished - the dialog said "the
     * question has not been answered yet" and the reader tapped Check again with
     * no idea the check itself was what failed. The count is what the dialog's
     * toast keys on, so a retry that fails the same way must still count as a new
     * failure rather than being swallowed as "same message as last time".
     */
    @Test
    fun `every thrown check is counted so the dialog can say so again`() = runTest {
        val probe = ApiStatusProbeState(
            requestStatus = { error("the socket closed under us") },
            scope = backgroundScope
        )

        probe.probe()
        runCurrent()
        assertEquals(1, probe.ui.value.failureCount)
        assertEquals("the socket closed under us", probe.ui.value.failureMessage)

        probe.probe()
        runCurrent()
        assertEquals("a retry that failed the same way was not counted", 2, probe.ui.value.failureCount)
        assertEquals(ApiReachability.UNKNOWN, probe.ui.value.reachability)
        assertFalse(probe.ui.value.isChecking)
    }

    @Test
    fun `a check that answers clears the failure and keeps the count`() = runTest {
        var answer: Result<ApiStatusSnapshot> = Result.failure(IllegalStateException("boom"))
        val probe = ApiStatusProbeState(
            requestStatus = { answer.getOrThrow() },
            scope = backgroundScope
        )

        probe.probe()
        runCurrent()
        assertEquals(1, probe.ui.value.failureCount)

        answer = Result.success(ApiStatusSnapshot(ApiReachability.ONLINE, latencyMillis = 42L))
        probe.probe()
        runCurrent()

        assertEquals(ApiReachability.ONLINE, probe.ui.value.reachability)
        assertNull("a successful check still carried the old failure", probe.ui.value.failureMessage)
        // Monotonic: winding the count back would make the next failure look like
        // the first one and the dialog would stay silent.
        assertEquals(1, probe.ui.value.failureCount)
    }
}
