package com.example.ui.components.update

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.data.update.UpdateCheckResult
import com.example.data.update.UpdateDownloadResult
import com.example.data.update.UpdateManager
import com.example.data.update.UpdateManifest
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

    var state: UpdateUiState by mutableStateOf(UpdateUiState.Idle)
        private set

    /** Asks GitHub whether anything newer exists. */
    fun check() {
        if (state is UpdateUiState.Checking) return
        state = UpdateUiState.Checking

        scope.launch {
            state = when (val result = updateManager.checkForUpdate()) {
                is UpdateCheckResult.UpToDate ->
                    UpdateUiState.UpToDate(result.installedVersion)

                is UpdateCheckResult.Available ->
                    UpdateUiState.Available(result.manifest)

                is UpdateCheckResult.Failed ->
                    UpdateUiState.Failed(result.reason)
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
     * fetched again. That is what makes the first-run Settings round trip cost
     * one download instead of two - see [UpdateManager.downloadedApkFor].
     */
    fun download() {
        val manifest = (state as? UpdateUiState.Available)?.manifest ?: return

        val alreadyOnDisk = updateManager.downloadedApkFor(manifest)
        if (alreadyOnDisk != null) {
            settleDownloaded(alreadyOnDisk, manifest, sentToSettings = false)
            return
        }

        state = UpdateUiState.Downloading(
            manifest,
            bytesRead = 0,
            totalBytes = manifest.sizeBytes ?: 0
        )

        scope.launch {
            // Any APK from an earlier attempt goes first. Two copies of the
            // same APK in the cache directory is not harmful, but it is wasted
            // storage on a phone that may not have much, and the old one is
            // never valid again once the user has tapped update.
            updateManager.clearDownloadedApk()

            when (val result = updateManager.download(manifest) { read, total ->
                state = UpdateUiState.Downloading(manifest, read, total)
            }) {
                is UpdateDownloadResult.Success ->
                    settleDownloaded(result.file, result.manifest, sentToSettings = false)

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
     */
    fun install() {
        val file = when (val current = state) {
            is UpdateUiState.ReadyToInstall -> current.file
            is UpdateUiState.NeedsInstallPermission -> current.file
            else -> return
        }

        // startActivity can still throw even with the permission granted: some
        // OEM builds have no package installer registered at all, and a
        // headless device has none. Losing the downloaded APK to a crash here
        // would be the worst possible outcome for a feature the user asked for.
        if (!installation.openInstaller(file)) {
            state = UpdateUiState.Failed("This device has no app installer.")
        }
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