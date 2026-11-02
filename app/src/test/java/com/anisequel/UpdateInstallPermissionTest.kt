package com.anisequel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.anisequel.ui.components.update.UpdateInstallation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Settings round trip the install permission needs.
 *
 * `REQUEST_INSTALL_PACKAGES` is a per-app grant, so the first update necessarily
 * sends the user to a Settings screen. The intent that got them there was built
 * without `FLAG_ACTIVITY_NEW_TASK`, while the update controller starts it from the
 * *application* context - where `startActivity` throws without that flag. The throw
 * was caught, turned into `false`, and reported to the user as "This device has no
 * Settings app", on every device, for the one screen they needed. Nothing in the
 * dialog changed, so the only place the failure showed was the phone.
 *
 * These tests pin the flag and the fallback. They cannot reproduce the platform's
 * throw under Robolectric - the shadow does not enforce the context rule - which is
 * exactly why the flag has to be asserted on the intent rather than trusted to a
 * test that would have passed before the fix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateInstallPermissionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageUri = "package:${context.packageName}"

    @Test
    fun `the install permission screen is scoped to this app and safe from an application context`() {
        val intent = UpdateInstallation(context).installPermissionSettings()

        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals(packageUri, intent.data?.toString())
        assertTrue(
            "without FLAG_ACTIVITY_NEW_TASK the launch from the application " +
                "context throws, and the updater reports a missing Settings app",
            intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
        )
    }

    @Test
    fun `a Settings app without the per-app screen falls back to the app's details page`() {
        val recorder = RecordingContext(
            base = context,
            missing = setOf(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        )

        val opened = UpdateInstallation(recorder).openInstallPermissionSettings()

        assertTrue("the fallback opened a screen", opened)
        assertEquals(
            "the per-app screen is tried first, then the details page",
            listOf(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ),
            recorder.started.map { it.action }
        )
        val fallback = recorder.started.last()
        assertEquals(packageUri, fallback.data?.toString())
        assertTrue(
            "the fallback has to be startable from the same application context",
            fallback.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
        )
    }

    /**
     * A phone that answers none of them, so the caller can say so.
     *
     * Every route has to be refused here, not just the first two: the walk ends at the
     * first screen that starts, and the point of the extra routes is that a re-skinned
     * build drops some of them and keeps others.
     */
    @Test
    fun `no route opening is reported, so the guide can say so`() {
        val recorder = RecordingContext(
            base = context,
            missing = setOf(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Settings.ACTION_SECURITY_SETTINGS,
                Settings.ACTION_SETTINGS
            )
        )

        assertFalse(UpdateInstallation(recorder).openInstallPermissionSettings())
        assertEquals(
            "the walk goes all the way down the list before it gives up",
            // Against the ladder's own size, not a number: a rung added to
            // `permissionSettingsRoutes` has to be tried by this walk too, and a
            // literal here would keep a test that no longer describes the list.
            UpdateInstallation(context).permissionSettingsRoutes().size,
            recorder.started.size
        )
    }

    /**
     * The OEM-difference case the old two-route version could not survive.
     *
     * A build that registers neither the package-scoped permission screen nor the app
     * details page is not rare - it is what a re-skinned Settings app that moved both
     * rows looks like from the outside. Before there were only those two routes, so
     * this device got "This device has no Settings app" while its own security page
     * held the switch the whole time.
     */
    @Test
    fun `a build with only the deeper screens still reaches one`() {
        val recorder = RecordingContext(
            base = context,
            missing = setOf(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            )
        )

        assertTrue(UpdateInstallation(recorder).openInstallPermissionSettings())
        assertEquals(
            "package-scoped, then the app's details, then the same action with no " +
                "package - the rung a re-skinned build keeps - then the security page",
            listOf(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Settings.ACTION_SECURITY_SETTINGS
            ),
            recorder.started.map { it.action }
        )
        assertNull(
            "the third attempt is the list of apps, so it must not carry this package",
            recorder.started[2].data
        )
        assertEquals(packageUri, recorder.started[0].data?.toString())
    }

    @Test
    fun `every route is startable from the application context`() {
        // The flag is the whole bug this file exists for, and it has to hold for the
        // fallbacks too: a route that throws for want of `NEW_TASK` is indistinguishable
        // from a route the device does not have.
        UpdateInstallation(context).permissionSettingsRoutes().forEach { intent ->
            assertTrue(
                "no new-task flag on ${intent.action}",
                intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
            )
        }
    }

    @Test
    fun `the package-scoped routes name this app and the others name nothing`() {
        val routes = UpdateInstallation(context).permissionSettingsRoutes()

        assertEquals(packageUri, routes[0].data?.toString())
        assertEquals(packageUri, routes[1].data?.toString())
        // A URI on the unscoped list route would ask the build for a package it is not
        // registered to open, which is the failure this list exists to walk past.
        assertNull("the list form carries no package", routes[2].data)
        assertNull(routes[3].data)
        assertNull(routes[4].data)
    }

    /**
     * A context that records what was launched and can refuse an action, standing
     * in for a Settings app that does not register it.
     */
    private class RecordingContext(
        base: Context,
        private val missing: Set<String>
    ) : ContextWrapper(base) {

        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += intent
            val action = intent.action
            if (action != null && action in missing) {
                throw ActivityNotFoundException("no handler for $action")
            }
        }
    }
}
