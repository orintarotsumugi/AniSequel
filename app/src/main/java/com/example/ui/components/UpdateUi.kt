package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.update.UpdateCheckResult
import com.example.data.update.UpdateDownloadResult
import com.example.data.update.UpdateManager
import com.example.data.update.UpdateManifest
import com.example.data.update.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** What the updater is doing, for the UI to render. */
sealed interface UpdateUiState {
    /** Nothing has happened yet - the state a freshly opened tab sits in. */
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data class UpToDate(val installedVersion: String) : UpdateUiState

    data class Available(val manifest: UpdateManifest) : UpdateUiState

    data class Downloading(
        val manifest: UpdateManifest,
        val bytesRead: Long,
        val totalBytes: Long
    ) : UpdateUiState {
        /** 0f..1f, or null while the total is still unknown. */
        val progress: Float?
            get() = if (totalBytes > 0) {
                (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    data class ReadyToInstall(val file: File, val manifest: UpdateManifest) : UpdateUiState

    data class Failed(val message: String) : UpdateUiState

    /**
     * The APK is downloaded but the app is not allowed to open the installer.
     *
     * A distinct state rather than an error, because the user has not done
     * anything wrong and the fix is one tap into Settings.
     */
    data class NeedsInstallPermission(
        val file: File,
        val manifest: UpdateManifest
    ) : UpdateUiState
}

/**
 * The updater's state, as something Compose can hold.
 *
 * A controller object rather than a ViewModel because the update flow has no
 * state worth surviving process death: if the app is killed mid-download the
 * next launch checks again from scratch, and the partial file was never renamed
 * into place anyway. A ViewModel here would only add a factory and a scope.
 */
class UpdateController(private val updateManager: UpdateManager) {

    var state: UpdateUiState by mutableStateOf(UpdateUiState.Idle)
        private set

    /** Asks GitHub whether anything newer exists. */
    fun check(scope: CoroutineScope) {
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
     */
    fun download(scope: CoroutineScope) {
        val manifest = (state as? UpdateUiState.Available)?.manifest ?: return

        state = UpdateUiState.Downloading(manifest, bytesRead = 0, totalBytes = manifest.sizeBytes ?: 0)

        scope.launch {
            // Any APK from an earlier attempt goes first. Two copies of the
            // same APK in the cache directory is not harmful, but it is wasted
            // storage on a phone that may not have much, and the old one is
            // never valid again once the user has tapped update.
            updateManager.clearDownloadedApk()

            when (val result = updateManager.download(manifest) { read, total ->
                state = UpdateUiState.Downloading(manifest, read, total)
            }) {
                is UpdateDownloadResult.Success -> {
                    state = if (updateManager.canInstallPackages()) {
                        UpdateUiState.ReadyToInstall(result.file, result.manifest)
                    } else {
                        UpdateUiState.NeedsInstallPermission(result.file, result.manifest)
                    }
                }

                is UpdateDownloadResult.Failed ->
                    state = UpdateUiState.Failed(result.reason)
            }
        }
    }

    /** Fires the system installer on a downloaded APK. */
    fun install(context: Context) {
        val file = when (val current = state) {
            is UpdateUiState.ReadyToInstall -> current.file
            is UpdateUiState.NeedsInstallPermission -> current.file
            else -> return
        }

        // startActivity can still throw even with the permission granted: some
        // OEM builds have no package installer registered at all, and a
        // headless device has none. Losing the downloaded APK to a crash here
        // would be the worst possible outcome for a feature the user asked for.
        runCatching { context.startActivity(updateManager.installIntent(file)) }
            .onFailure { state = UpdateUiState.Failed("This device has no installer.") }
    }

    /** Sends the user to the per-app "install unknown apps" setting. */
    fun requestInstallPermission(context: Context) {
        val intent = updateManager.unknownSourcesSettingsIntent()
        if (intent != null) {
            runCatching { context.startActivity(intent) }
        }
    }

    /** Back to the untouched state, for the "Check again" affordance. */
    fun reset() {
        state = UpdateUiState.Idle
    }
}

/**
 * Opens a release's page on GitHub.
 *
 * The manifest has carried a `release_url` since the first version, and nothing
 * read it. It is the only way to reach the release notes written by the
 * workflow, which are the ones that explain what a build actually changed - the
 * `notes` field is a build number and a commit hash, which is not something to
 * show a user deciding whether to update.
 *
 * Failures are swallowed deliberately: a device with no browser at all should
 * lose the link, not crash the dialog.
 */
fun openReleasePage(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** A controller for the current composition. */
@Composable
fun rememberUpdateController(
    context: Context = LocalContext.current
): UpdateController = remember(context) {
    UpdateController(UpdateManager(context.applicationContext))
}

/**
 * The startup prompt.
 *
 * Shown once per launch, and only when GitHub actually has something newer -
 * never as a "you're up to date" dialog, which would be an interruption with
 * no action in it.
 *
 * It deliberately offers *Later* as the equal-weight negative. An app that can
 * nag about updates on every launch is an app people stop opening, and this one
 * is a sideloaded APK the user updates when they choose.
 */
@Composable
fun UpdatePromptHost(controller: UpdateController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = controller.state

    // Nothing to say in any of these, so nothing is drawn - critically including
    // UpToDate and Failed, which are results the user asked for on the Settings
    // tab and does not need to also be interrupted about at launch.
    if (state !is UpdateUiState.Available &&
        state !is UpdateUiState.Downloading &&
        state !is UpdateUiState.ReadyToInstall &&
        state !is UpdateUiState.NeedsInstallPermission
    ) {
        return
    }

    AlertDialog(
        onDismissRequest = { controller.reset() },
        modifier = Modifier.testTag("update_prompt"),
        icon = {
            Icon(
                imageVector = Icons.Outlined.NewReleases,
                contentDescription = null
            )
        },
        title = {
            val manifest = (state as? UpdateUiState.Available)?.manifest
            Text(
                text = if (manifest != null) "AniSequel ${manifest.version} is out" else "Update available"
            )
        },
        text = {
            Column {
                val manifest = (state as? UpdateUiState.Available)?.manifest
                Text(
                    text = manifest?.notes?.takeIf { it.isNotBlank() }
                        ?: "A newer build of AniSequel has been published.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state is UpdateUiState.Downloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DownloadProgress(state = state)
                } else if (manifest?.sizeBytes != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatBytes(manifest.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (state) {
                        is UpdateUiState.Available -> controller.download(scope)
                        is UpdateUiState.ReadyToInstall -> controller.install(context)
                        is UpdateUiState.NeedsInstallPermission -> controller.requestInstallPermission(context)
                        else -> Unit
                    }
                },
                enabled = state !is UpdateUiState.Downloading,
                modifier = Modifier.testTag("update_prompt_confirm")
            ) {
                Text(
                    when (state) {
                        is UpdateUiState.ReadyToInstall -> "Install"
                        is UpdateUiState.NeedsInstallPermission -> "Open settings"
                        else -> "Download & install"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { controller.reset() },
                modifier = Modifier.testTag("update_prompt_dismiss")
            ) { Text("Later") }
        }
    )
}

/**
 * Download progress, as a determinate bar where possible.
 *
 * Falls back to indeterminate when the server sends no `Content-Length` - which
 * GitHub's asset CDN sometimes does through a redirect - rather than showing a
 * bar stuck at 0% that looks broken.
 */
@Composable
fun DownloadProgress(
    state: UpdateUiState.Downloading,
    modifier: Modifier = Modifier
) {
    val progress = state.progress

    Column(modifier = modifier.fillMaxWidth().testTag("update_download_progress")) {
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (progress == null) {
                "Downloading…"
            } else {
                "${(progress * 100).toInt()}% · ${formatBytes(state.bytesRead)} " +
                        "of ${formatBytes(state.totalBytes)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}