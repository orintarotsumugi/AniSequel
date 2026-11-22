package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launch screen's one decision.
 *
 * The failure this pins is not subtle: `MainActivity` draws the branded
 * placeholder until its preference stores have answered, and the app has no
 * exit from that screen. A read that never arrives is therefore not a slow
 * launch, it is a launch that never finishes. The watchdog is the only arm that
 * may proceed with an unanswered read, and it must never be what a *normal*
 * launch takes - otherwise every cold start would skip the stored theme.
 *
 * The interface read is part of the gate because its one value that is applied
 * above the theme is the display density: composing at the default and then
 * taking the stored value re-lays-out every screen one frame in, which is a
 * visible resize of the app on launch rather than a colour settling.
 */
class LaunchGateTest {

    @Test
    fun `a normal launch waits for every read`() {
        assertFalse(
            launchGateReady(
                themeLoaded = false,
                interfaceLoaded = false,
                lockLoaded = false,
                watchdogElapsed = false
            )
        )
        assertFalse(
            launchGateReady(
                themeLoaded = true,
                interfaceLoaded = false,
                lockLoaded = true,
                watchdogElapsed = false
            )
        )
        assertFalse(
            launchGateReady(
                themeLoaded = true,
                interfaceLoaded = true,
                lockLoaded = false,
                watchdogElapsed = false
            )
        )
        assertFalse(
            launchGateReady(
                themeLoaded = false,
                interfaceLoaded = true,
                lockLoaded = true,
                watchdogElapsed = false
            )
        )
    }

    @Test
    fun `every answer opens the gate`() {
        assertTrue(
            launchGateReady(
                themeLoaded = true,
                interfaceLoaded = true,
                lockLoaded = true,
                watchdogElapsed = false
            )
        )
    }

    @Test
    fun `the watchdog opens the gate with an unanswered read`() {
        assertTrue(
            launchGateReady(
                themeLoaded = false,
                interfaceLoaded = false,
                lockLoaded = false,
                watchdogElapsed = true
            )
        )
        assertTrue(
            launchGateReady(
                themeLoaded = true,
                interfaceLoaded = false,
                lockLoaded = true,
                watchdogElapsed = true
            )
        )
    }
}
