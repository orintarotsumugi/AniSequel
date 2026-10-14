package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.update.UpdateCheckResult
import com.anisequel.data.update.UpdateDownloadResult
import com.anisequel.data.update.UpdateManager
import com.anisequel.data.update.UpdateManifest
import com.anisequel.ui.components.update.UpdateController
import com.anisequel.ui.components.update.UpdateInstallation
import com.anisequel.ui.components.update.UpdateUiState
import com.anisequel.ui.components.update.InstallerLaunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The updater's state across a relaunch.
 *
 * ## Why this exists
 *
 * The updater kept its state in a process-wide object, so it survived navigating
 * away from Settings but not the process being killed. On the next launch every
 * state was rebuilt from nothing, and - this is the bug - the app never asked
 * whether the APK it had already downloaded was still on disk. So a user who
 * downloaded a release, then force-stopped the app or had it killed, came back
 * to a button reading "Download & install". Pressing it transferred nothing and
 * quietly jumped to the installer. From the outside that is indistinguishable
 * from the app having downloaded the whole file again.
 *
 * [UpdateController.check] now rehydrates from [UpdateManager.downloadedApkFor]
 * before offering a download. These tests pin that, because the failure mode it
 * prevents is invisible: nothing errors, nothing is logged, the file is simply
 * fetched twice for no reason.
 *
 * @see UpdateManager.downloadDirectory for why the file has to be somewhere the
 *   system will not empty out from under it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateDownloadStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = StandardTestDispatcher()

    private val manifest = UpdateManifest(
        version = "9.9.9",
        versionCode = 9999L,
        downloadUrl = "https://example.invalid/anisequel.apk",
        sizeBytes = 2_600_000L
    )

    @Before
    fun installTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun removeTestDispatcher() {
        Dispatchers.resetMain()
    }

    /** Answers a check with [manifest] and reports [onDisk] as already downloaded. */
    private fun managerReporting(onDisk: File?): UpdateManager =
        object : UpdateManager(context) {
            override suspend fun checkForUpdate(): UpdateCheckResult =
                UpdateCheckResult.Available(manifest)

            override fun downloadedApkFor(manifest: UpdateManifest): File? = onDisk
        }

    /** Grants the install permission, so the flow reaches a terminal state. */
    private fun grantingInstallation(): UpdateInstallation =
        object : UpdateInstallation(context) {
            override fun canInstallPackages(): Boolean = true
            override fun openInstaller(apk: File): InstallerLaunch = InstallerLaunch.Opened
            override fun openInstallPermissionSettings(): Boolean = true
            override fun openReleasePage(url: String?): Boolean = true
        }

    private fun controller(manager: UpdateManager): UpdateController =
        UpdateController(manager, grantingInstallation())

    @Test
    fun `a relaunch with the apk already downloaded offers the installer, not another download`() {
        // This is the regression. Before the fix this was `Available`, and the
        // button said "Download & install".
        val alreadyThere = File("anisequel-9999.apk")

        runTest(dispatcher) {
            val controller = controller(managerReporting(alreadyThere))

            controller.check()
            advanceUntilIdle()

            val state = controller.state
            assertTrue(
                "expected to rehydrate to ReadyToInstall but was $state",
                state is UpdateUiState.ReadyToInstall
            )
            assertEquals(alreadyThere, (state as UpdateUiState.ReadyToInstall).file)
        }
    }

    @Test
    fun `a relaunch with nothing downloaded still offers the download`() {
        // The other half: rehydrating must not manufacture a state. With no file
        // on disk there is genuinely nothing to install.
        runTest(dispatcher) {
            val controller = controller(managerReporting(onDisk = null))

            controller.check()
            advanceUntilIdle()

            assertTrue(
                "expected Available but was ${controller.state}",
                controller.state is UpdateUiState.Available
            )
        }
    }

    @Test
    fun `rehydration respects a missing install permission`() {
        // Downloading something and then being unable to install it is still the
        // state it always was. The file is kept either way; only the copy and the
        // button change, which is the whole point of the round trip.
        val alreadyThere = File("anisequel-9999.apk")

        runTest(dispatcher) {
            val controller = UpdateController(
                managerReporting(alreadyThere),
                object : UpdateInstallation(context) {
                    override fun canInstallPackages(): Boolean = false
                    override fun openInstaller(apk: File): InstallerLaunch = InstallerLaunch.Opened
                    override fun openInstallPermissionSettings(): Boolean = true
                    override fun openReleasePage(url: String?): Boolean = true
                }
            )

            controller.check()
            advanceUntilIdle()

            val state = controller.state
            assertTrue(
                "expected NeedsInstallPermission but was $state",
                state is UpdateUiState.NeedsInstallPermission
            )
            assertEquals(
                alreadyThere,
                (state as UpdateUiState.NeedsInstallPermission).file
            )
        }
    }

    @Test
    fun `a failed download leaves the previous apk on disk`() {
        // The cleanup used to run *before* the download started, so a retry that
        // failed left the user with nothing: the previous release's APK had
        // already been deleted, and the new one never arrived.
        var kept: File? = null
        var cleared = false

        val manager = object : UpdateManager(context) {
            override suspend fun checkForUpdate(): UpdateCheckResult =
                UpdateCheckResult.Available(manifest)

            override fun downloadedApkFor(manifest: UpdateManifest): File? = null

            override suspend fun download(
                manifest: UpdateManifest,
                onProgress: (Long, Long) -> Unit
            ): UpdateDownloadResult = UpdateDownloadResult.Failed("no network")

            override fun clearDownloadedApk(keep: File?) {
                cleared = true
                kept = keep
            }
        }

        runTest(dispatcher) {
            val controller = UpdateController(manager, grantingInstallation())

            controller.check()
            advanceUntilIdle()
            controller.download()
            advanceUntilIdle()

            assertTrue("expected the download to fail", controller.state is UpdateUiState.Failed)
            assertFalse(
                "nothing new arrived, so nothing may be deleted",
                cleared
            )
            assertEquals(null, kept)
        }
    }

    /**
     * An installer that will not open, and how the updater reports it.
     *
     * This is the flow behind the message every user of #61 saw - "This device
     * has no app installer" - on phones that install apps perfectly well. Two
     * things were wrong with it, and both are pinned here:
     *
     *  - the message blamed the device for a failure inside this app, and
     *  - reporting it *replaced* the state, so the downloaded APK was dropped out
     *    of the UI entirely and the only way back to it was a re-check.
     */
    @Test
    fun `a failed launch keeps the downloaded apk and says what failed`() {
        val alreadyThere = File("anisequel-9999.apk")

        val refusing = object : UpdateInstallation(context) {
            override fun canInstallPackages(): Boolean = true
            override fun openInstaller(apk: File): InstallerLaunch = InstallerLaunch.CouldNotOpen
            override fun openInstallPermissionSettings(): Boolean = true
            override fun openReleasePage(url: String?): Boolean = true
        }

        runTest(dispatcher) {
            val controller = UpdateController(managerReporting(alreadyThere), refusing)

            controller.check()
            advanceUntilIdle()
            controller.install()

            val state = controller.state
            val ready = state as? UpdateUiState.ReadyToInstall

            assertTrue(
                "the apk is downloaded and still on disk, so the state must stay " +
                    "ReadyToInstall rather than becoming Failed. Was $state",
                ready != null
            )
            assertEquals(
                "the file must still be in the state, or the UI has nothing to " +
                    "retry with",
                alreadyThere,
                ready?.file
            )
            assertTrue(
                "a launch that did not open has to say so. Expected a notice, " +
                    "got ${ready?.notice}",
                ready?.notice != null
            )
        }
    }

    @Test
    fun `a successful launch reports no problem`() {
        val alreadyThere = File("anisequel-9999.apk")

        runTest(dispatcher) {
            val controller = UpdateController(
                managerReporting(alreadyThere),
                grantingInstallation()
            )

            controller.check()
            advanceUntilIdle()
            controller.install()

            val ready = controller.state as? UpdateUiState.ReadyToInstall

            assertTrue(
                "expected ReadyToInstall but was ${controller.state}",
                ready != null
            )
            assertEquals(
                "a launcher that opened has nothing to apologise for",
                null,
                ready?.notice
            )
        }
    }
}