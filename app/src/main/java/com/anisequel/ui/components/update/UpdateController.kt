package com.anisequel.ui.components.update

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.anisequel.data.update.UpdateCheckResult
import com.anisequel.data.update.UpdateDownloadResult
import com.anisequel.data.update.UpdateManager
import com.anisequel.data.update.UpdateManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * The updater's state, as something Compose can hold.
 *
 * A controller object rather than a ViewModel because the update flow has no
 * state worth surviving process death: if the app is killed mid-download the
 * next launch checks again from scratch, and the partial file was never renamed
 * into place anyway. A ViewModel here would only add a factory and a scope.
 */
class UpdateController(
    private val updateManager: UpdateManager,
    /**
     * Public because the Settings row needs somewhere to send the release-notes
     * link. Opening a browser is the same class of device-intent work as opening
     * the installer, so it belongs to the same object rather than to a third
     * thing the UI holds a `Context` for.
     */
    val installation: UpdateInstallation
) {

    /**
     * The scope the updater runs its work in.
     *
     * Deliberately not a `rememberCoroutineScope()` handed in by the caller.
     * Those scopes are cancelled when the composable that created them leaves
     * the composition, so a download started from the Settings row was killed
     * the moment the user navigated away - leaving [state] stuck on
     * `Downloading`, with no download running and no way out of it but a
     * process restart. The work belongs to the updater, not to whichever screen
     * happened to start it.
     *
     * `SupervisorJob` so that one failed check cannot cancel the updater's
     * scope and take every later attempt with it.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The speed readout for the progress line.
     *
     * Kept here rather than in [UpdateManager] because it is a presentation
     * concern: the transfer reports bytes, and what those bytes mean per second
     * on *this* screen is the UI's business. One instance per download, reset
     * when a new one starts so a previous transfer's window cannot leak into
     * the next reading.
     */
    private val rate = TransferRate()

    var state: UpdateUiState by mutableStateOf(UpdateUiState.Idle)
        private set

    /** Asks GitHub whether anything newer exists. */
    fun check() {
        // Never over a running transfer. `MainActivity` checks once per Activity
        // creation, and a rotation is a new creation: between two progress callbacks
        // the state passed through `Checking`/`Available`, the dialog offered
        // Download again, and a second `FileOutputStream` appended to the same
        // `.part` - two interleaved transfers in one file, which then failed
        // verification and was reported as a tampered release. The download owns the
        // state until it settles.
        if (state is UpdateUiState.Checking || state is UpdateUiState.Downloading) return
        state = UpdateUiState.Checking

        scope.launch {
            when (val result = updateManager.checkForUpdate()) {
                is UpdateCheckResult.UpToDate ->
                    state = UpdateUiState.UpToDate(result.installedVersion)

                // Rehydrate before offering the download. This process may have
                // no memory of a download this device already made - the app was
                // killed, or the user force-stopped it, or the APK was fetched on
                // a launch that never got as far as installing. The file is still
                // on disk and still valid, so showing "Download & install" would
                // be a lie: the user presses it, and the button quietly returns
                // without transferring anything, which reads as the app having
                // downloaded the file all over again.
                //
                // Not an `elvis` on the result: `settleDownloaded` assigns the
                // state itself and returns Unit, so folding it into an
                // expression would lose the branch and then overwrite whatever
                // it had set.
                is UpdateCheckResult.Available -> {
                    val onDisk = updateManager.downloadedApkFor(result.manifest)
                    if (onDisk != null) {
                        settleDownloaded(onDisk, result.manifest, sentToSettings = false)
                    } else {
                        state = UpdateUiState.Available(result.manifest)
                    }
                }

                is UpdateCheckResult.Failed ->
                    state = UpdateUiState.Failed(result.reason)
            }
        }
    }

    /**
     * Downloads the offered release, then either opens the installer or asks
     * for permission.
     *
     * The permission check is done *after* the download, not before: the user
     * has already chosen to update, and a download that succeeds and then says
     * "now go grant a permission" is a better outcome than refusing to start
     * over a setting they may have already granted since last launch.
     *
     * An APK already on disk for this exact version is reused rather than
     * fetched again, and so is a *partial* one: the transfer asks for the rest
     * of the bytes it already has. That is what makes the first-run Settings
     * round trip cost one download instead of two - see
     * [UpdateManager.downloadedApkFor] - and it is why an interrupted transfer
     * is not the whole file again.
     */
    fun download() {
        // The cast is the re-entry guard: `state` becomes `Downloading`
        // synchronously below, before anything is launched, so a second tap
        // arriving in the same frame finds a state that is not `Available` and
        // returns. Without that, two taps would put two transfers on one
        // `.part` file and the result would be an APK made of two interleaved
        // downloads.
        val manifest = (state as? UpdateUiState.Available)?.manifest ?: return

        val alreadyOnDisk = updateManager.downloadedApkFor(manifest)
        if (alreadyOnDisk != null) {
            settleDownloaded(alreadyOnDisk, manifest, sentToSettings = false)
            return
        }

        rate.reset()
        state = UpdateUiState.Downloading(
            manifest,
            bytesRead = 0,
            totalBytes = manifest.sizeBytes ?: 0
        )

        scope.launch {
            when (val result = updateManager.download(manifest) { read, total ->
                // The callback runs on OkHttp's IO thread, so the clock is read
                // here rather than captured once: elapsed time has to be measured
                // where it happens for the rate to mean anything.
                state = UpdateUiState.Downloading(
                    manifest,
                    read,
                    total,
                    bytesPerSecond = rate.sample(read, SystemClock.elapsedRealtime())
                )
            }) {
                is UpdateDownloadResult.Success -> {
                    // Only now, with the new APK safely renamed into place, are
                    // the stale ones removed. This used to run *before* the
                    // download started, which meant a failed retry left the user
                    // with nothing at all - having thrown away a previous
                    // release's APK that was perfectly installable.
                    updateManager.clearDownloadedApk(keep = result.file)
                    settleDownloaded(result.file, result.manifest, sentToSettings = false)
                }

                is UpdateDownloadResult.Failed ->
                    state = UpdateUiState.Failed(result.reason)
            }
        }
    }

    /** Decides between "ready" and "ask for permission" for a file on disk. */
    private fun settleDownloaded(
        file: File,
        manifest: UpdateManifest,
        sentToSettings: Boolean
    ) {
        state = if (installation.canInstallPackages()) {
            UpdateUiState.ReadyToInstall(file, manifest)
        } else {
            UpdateUiState.NeedsInstallPermission(file, manifest, sentToSettings)
        }
    }

    /**
     * Fires the system installer on the downloaded APK.
     *
     * Called by the user pressing Install, and - the point of the whole
     * permission dance - called for them by [onAppResumed] once the permission
     * has actually been granted.
     *
     * ## What a failed launch does to the state
     *
     * Nothing, except write down what went wrong. This used to replace the state
     * with [UpdateUiState.Failed], which took the downloaded file *out of the
     * state entirely*: the pop-up disappeared, the Settings row showed the error
     * as if nothing had been downloaded, and the only way back to the APK was a
     * re-check that happened to rehydrate it again. The file is still on disk and
     * still verified, so the state stays what it was and the message rides along
     * as [UpdateUiState.ReadyToInstall.notice] - one more press away from
     * working.
     */
    fun install() {
        val file = when (val current = state) {
            is UpdateUiState.ReadyToInstall -> current.file
            is UpdateUiState.NeedsInstallPermission -> current.file
            else -> return
        }

        // startActivity can still fail even with the permission granted: some OEM
        // builds have no package installer registered at all, and a headless
        // device has none. Losing the downloaded APK to a crash here would be the
        // worst possible outcome for a feature the user asked for - which is why
        // `openInstaller` contains that rather than letting it throw.
        val notice = when (installation.openInstaller(file)) {
            InstallerLaunch.Opened -> null
            InstallerLaunch.CouldNotOpen ->
                "Android would not open the installer for this update. It is " +
                    "still downloaded - press Install again, or take the APK " +
                    "from the release page."
        }

        // Both states carry the file, so both keep it and only gain the message.
        // An elvis rather than a second `when`, because there is no third case:
        // firing the installer cannot have moved the state out from under this.
        state = (state as? UpdateUiState.ReadyToInstall)?.copy(notice = notice)
            ?: (state as? UpdateUiState.NeedsInstallPermission)?.copy(notice = notice)
            ?: state
    }

    /**
     * Sends the user to the per-app "install unknown apps" setting.
     *
     * Records that the hand-off has been offered, so the row stops re-offering
     * the same button to somebody who has already come back without granting
     * it.
     */
    fun requestInstallPermission() {
        val current = state as? UpdateUiState.NeedsInstallPermission ?: return

        state = current.copy(sentToSettings = true)

        if (!installation.openInstallPermissionSettings()) {
            state = UpdateUiState.Failed("This device has no Settings app.")
        }
    }

    /**
     * Called when the app comes back to the foreground.
     *
     * This is what makes the install permission a one-time thing rather than a
     * recurring detour. From Android 8 the app has to be granted
     * `REQUEST_INSTALL_PACKAGES` before it may open the installer at all, so the
     * first update *has* to leave the app. The user grants it, comes back, and
     * without this the flow would still be sitting on "needs permission" with a
     * button that would send them to the screen they just left. So the grant is
     * re-checked here and the installer is opened on the APK that is already
     * downloaded.
     *
     * Opening the installer unprompted is safe in the sense that matters: the
     * system install dialog still requires the user's own confirmation, and no
     * version of this app can install itself. The alternative - a third tap to
     * confirm a request the user already made - is the friction this removes.
     *
     * A no-op in every other state, and it does not re-check for updates: that
     * stays one check per launch, driven from `MainActivity`.
     */
    fun onAppResumed() {
        val waiting = state as? UpdateUiState.NeedsInstallPermission ?: return
        if (!installation.canInstallPackages()) return

        settleDownloaded(waiting.file, waiting.manifest, sentToSettings = waiting.sentToSettings)
        install()
    }

    /** Back to the untouched state, for the "Check again" affordance. */
    fun reset() {
        state = UpdateUiState.Idle
    }
}

/**
 * The one [UpdateController] for this process.
 *
 * There is exactly one update to make and one APK to download, so the state
 * behind that is process-wide. It used to be `remember`ed per call site, which
 * quietly produced *two* of them: one in `MainActivity` driving the launch
 * prompt, and another inside `UpdateSectionCard` driving the Settings row.
 *
 * That was not just a duplicate. Because the two never saw each other's state,
 * starting a download from the Settings row left the launch prompt still sitting
 * on `Available`, so it went on offering an update the user was already
 * downloading - and reopening Settings showed `Idle` and checked GitHub again.
 * It also meant two [UpdateManager]s, and therefore two OkHttp clients, each
 * with its own connection pool and thread pool.
 *
 * Held in a field rather than in composition state so it survives the Settings
 * screen being torn down and rebuilt, and so the launch prompt and the Settings
 * row are reading the same object. Doubly-checked locking, because the launch
 * check and the Settings row can both reach this on first composition.
 */
@Volatile
private var sharedUpdateController: UpdateController? = null

private val updateControllerLock = Any()

private fun obtainUpdateController(context: Context): UpdateController {
    sharedUpdateController?.let { return it }
    return synchronized(updateControllerLock) {
        sharedUpdateController
            ?: run {
                // The application context, deliberately: this object outlives
                // every screen, and an Activity context here would leak the
                // last rotated-away Activity for as long as the process lives.
                // Every intent it fires carries FLAG_ACTIVITY_NEW_TASK, which is
                // what makes launching from a non-Activity context legal.
                val appContext = context.applicationContext
                UpdateController(
                    updateManager = UpdateManager(appContext),
                    installation = UpdateInstallation(appContext)
                )
            }.also { sharedUpdateController = it }
    }
}

/**
 * The shared update controller.
 *
 * Prefer this over constructing an [UpdateController] directly: a second one
 * will hold a second, contradictory idea of what the updater is doing.
 */
@Composable
fun rememberUpdateController(
    context: Context = LocalContext.current
): UpdateController = remember(context) {
    obtainUpdateController(context)
}