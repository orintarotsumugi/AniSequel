package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.DiagnosticsPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one setting that decides whether a crash leaves anything behind.
 *
 * ## Why "off by default" is a test and not a comment
 *
 * The app's About page promises there is no telemetry, and the crash report is the
 * one thing in the build that writes a trace of its own failure to disk. Shipping
 * it on by default would be a quiet reversal of that promise - and the kind that
 * nobody notices, because the file it writes is one nobody looks at until
 * something goes wrong. The default is the whole consent model here, so it is
 * pinned rather than left to whoever next edits the `getBoolean` fallback.
 *
 * ## Why Robolectric
 *
 * `SharedPreferences` is the point: this setting is read from an uncaught-exception
 * handler, where a suspend call is not an option (see the class doc). That makes
 * the real implementation the thing worth testing, rather than a fake in front of
 * it - and the round trip through a second instance is what proves the value
 * survives the process, which is the only lifetime a crash report cares about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticsPreferencesTest {

    private fun preferences() =
        DiagnosticsPreferences(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `crash reports are off until the reader turns them on`() {
        assertFalse(
            "A fresh install must not keep a trace of its own failures. The About " +
                "page says there is no telemetry; this is where that is true.",
            preferences().crashReportsEnabled
        )
    }

    @Test
    fun `turning reports on survives a new instance`() {
        preferences().crashReportsEnabled = true

        // A second instance is what the next launch looks like: the value has to
        // come back from disk, not from the object that wrote it.
        assertTrue(preferences().crashReportsEnabled)
    }

    @Test
    fun `turning reports back off is remembered`() {
        preferences().crashReportsEnabled = true
        preferences().crashReportsEnabled = false

        assertFalse(preferences().crashReportsEnabled)
    }
}
