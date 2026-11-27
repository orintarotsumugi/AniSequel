package com.anisequel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.notifications.BatteryOptimisation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The battery-exemption hand-off in Notification settings.
 *
 * The card can only ask Android for the exemption; it cannot set it. That makes the
 * intent the whole feature, and the intent has two ways to fail silently:
 *
 *  - `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` opens its dialog only for an app
 *    that declares the permission. Without it the action has no target, so the
 *    button looks dead and nothing tells the reader why.
 *  - Some Settings apps do not register the request action at all, and the reader
 *    needs a screen that still exists - the exemption list, then the app's details
 *    page, the same ladder the install permission uses.
 *
 * Robolectric cannot reproduce a Settings app that refuses an action, which is
 * exactly why the fallback is asserted (through [RecordingContext]) rather than
 * trusted to a device test this project has no emulator for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryOptimisationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageUri = "package:" + context.packageName

    @Test
    fun `the exemption is asked for this package and safe from an application context`() {
        val routes = BatteryOptimisation.requestRoutes(context)
        val request = routes.first()

        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, request.action)
        assertEquals(packageUri, request.data?.toString())
        assertTrue(
            "without FLAG_ACTIVITY_NEW_TASK the launch from the application context " +
                "throws, and the card reports a missing battery page on every device",
            request.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
        )
    }

    @Test
    fun `a build that does not answer the request dialog still reaches the exemption list`() {
        val recorder = RecordingContext(
            base = context,
            missing = setOf(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        )

        val opened = BatteryOptimisation.request(recorder)

        assertTrue("the fallback opened a screen", opened)
        assertEquals(
            "the direct dialog is tried first, then Android's exemption list",
            listOf(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            ),
            recorder.started.map { it.action }
        )
    }

    @Test
    fun `the permission the dialog needs is declared`() {
        val manifest = source("app/src/main/AndroidManifest.xml")

        assertTrue(
            "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is only offered to an app " +
                "that declares REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; without the line " +
                "the dialog never appears and the button does nothing",
            manifest.contains("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
        )
    }

    private class RecordingContext(
        base: Context,
        private val missing: Set<String>
    ) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            // Recorded before the throw: the ladder is judged on what it tried,
            // not only on the rung that answered.
            started += intent
            if (intent.action in missing) {
                throw ActivityNotFoundException("no Settings screen for ${intent.action}")
            }
        }
    }

    private fun source(path: String): String = File(findProjectRoot(), path).readText()
        .replace(Regex("<!--[\\s\\S]*?-->"), "")

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