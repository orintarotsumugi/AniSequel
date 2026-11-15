package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launch screen's one decision.
 *
 * The failure this pins is not subtle: `MainActivity` draws the branded
 * placeholder until both preference stores have answered, and the app has no
 * exit from that screen. A read that never arrives is therefore not a slow
 * launch, it is a launch that never finishes. The watchdog is the only arm that
 * may proceed with an unanswered read, and it must never be what a *normal*
 * launch takes - otherwise every cold start would skip the stored theme.
 */
class LaunchGateTest {

    @Test
    fun `a normal launch waits for both reads`() {
        assertFalse(launchGateReady(themeLoaded = false, lockLoaded = false, watchdogElapsed = false))
        assertFalse(launchGateReady(themeLoaded = true, lockLoaded = false, watchdogElapsed = false))
        assertFalse(launchGateReady(themeLoaded = false, lockLoaded = true, watchdogElapsed = false))
    }

    @Test
    fun `both answers open the gate`() {
        assertTrue(launchGateReady(themeLoaded = true, lockLoaded = true, watchdogElapsed = false))
    }

    @Test
    fun `the watchdog opens the gate with an unanswered read`() {
        assertTrue(launchGateReady(themeLoaded = false, lockLoaded = false, watchdogElapsed = true))
        assertTrue(launchGateReady(themeLoaded = true, lockLoaded = false, watchdogElapsed = true))
    }
}
