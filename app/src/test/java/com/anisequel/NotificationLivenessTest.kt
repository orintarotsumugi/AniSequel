package com.anisequel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two things that made background alerts go quiet, and the clock that made the
 * inbox lie about how long ago a row arrived.
 *
 * ## Why these are source assertions
 *
 * The worker is a `CoroutineWorker`: its decisions come from WorkManager's
 * `runAttemptCount`, the stored session and the DataStore preferences, and there is
 * no WorkManager test runtime in this module. The failures are not exceptions -
 * they are silence. A run that cannot read a session used to cancel the unique
 * periodic request, so every later check was gone until the app was opened again;
 * a run whose retries were spent used to return success *before* checking, so a
 * whole period passed with nothing posted and nothing recorded. Both look exactly
 * like an account with no new activity, which is why neither was noticed. Pinning
 * the decision in the file is the only check available here, in the same spirit as
 * `InfoFloatingBarKeyingTest`.
 */
class NotificationLivenessTest {

    private val worker =
        source("app/src/main/java/com/anisequel/data/notifications/NotificationCheckWorker.kt")
    private val centre =
        source("app/src/main/java/com/anisequel/ui/screens/notifications/NotificationCenterScreen.kt")

    @Test
    fun `a run that cannot read a session leaves the schedule alone`() {
        val skip = worker.indexOf("skip: no stored session")
        assertTrue("the no-session branch is gone; read the worker before changing this", skip >= 0)
        val firstCancel = worker.indexOf("NotificationAlertsScheduler.cancel")
        assertTrue("the worker no longer cancels anything, so this guard proves nothing", firstCancel >= 0)
        assertTrue(
            "a run that cannot read a session cancels the unique periodic request again: " +
                "the alerts stop arriving and nothing says why",
            skip < firstCancel
        )
        // The authoritative cancellation stays: alerts switched off in Settings.
        assertTrue(
            "turning background alerts off no longer stops the scheduled work",
            worker.contains("if (!preferences.enabled)")
        )
    }

    @Test
    fun `spent retries record a failure instead of skipping the check`() {
        assertTrue(
            "giving up no longer ends the run as a failure",
            worker.contains("runAttemptCount + 1 >= MAX_ATTEMPTS -> Result.failure()")
        )
        assertFalse(
            "the give-up guard is back in front of the check, so an exhausted period " +
                "posts nothing and reports nothing",
            worker.contains("if (runAttemptCount >= MAX_ATTEMPTS)")
        )
    }

    @Test
    fun `the inbox clock keeps ticking while the screen is open`() {
        assertTrue(
            "the inbox reads its relative labels against the shared minute clock",
            centre.contains("rememberNowEpochSecond()")
        )
        assertTrue(
            "the clock is no longer rebuilt from the epoch second it ticks",
            centre.contains("Instant.ofEpochSecond(nowEpochSecond)")
        )
        assertFalse(
            "the clock is frozen at the page arrival again: every \"Nm ago\" on a " +
                "screen left open then stays wrong",
            centre.contains("remember(state.items, state.refreshing) { Instant.now() }")
        )
    }

    private fun source(path: String): String =
        Regex("""/[*][\s\S]*?[*]/""")
            .replace(File(findProjectRoot(), path).readText()) { "" }
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }
}
