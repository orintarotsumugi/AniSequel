package com.anisequel.ui.components.update

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.update.formatBytes
import com.anisequel.data.update.formatRate

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
    val state = controller.state

    // Nothing to say in any of these, so nothing is drawn - critically including
    // UpToDate and Failed, which are results the user asked for on the Settings
    // tab and does not need to also be interrupted about at launch.
    val manifest = when (state) {
        is UpdateUiState.Available -> state.manifest
        is UpdateUiState.Downloading -> state.manifest
        is UpdateUiState.ReadyToInstall -> state.manifest
        is UpdateUiState.NeedsInstallPermission -> state.manifest
        else -> null
    } ?: return

    val downloading = state is UpdateUiState.Downloading

    AlertDialog(
        // A download in flight cannot be dismissed by tapping outside.
        //
        // It used to be, and the result was a dialog that came back on its own:
        // dismissing reset the state to Idle while the transfer kept running in
        // the controller's scope, and when it finished it set the state to
        // ReadyToInstall - which is one of the states this composable draws. So
        // the pop-up reappeared minutes later, over whatever the user was doing,
        // for an update they had explicitly dismissed. There is no cancel button
        // on the transfer itself, so "dismiss" did not mean what it looked like
        // it meant: the only honest way to end a download is to let it finish.
        onDismissRequest = { if (!downloading) controller.reset() },
        modifier = Modifier.testTag("update_prompt"),
        icon = {
            Icon(
                imageVector = Icons.Outlined.NewReleases,
                contentDescription = null
            )
        },
        title = {
            Text(text = "AniSequel ${manifest.version} is out")
        },
        text = {
            Column {
                Text(
                    text = "A newer build of AniSequel has been published.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (manifest.sizeBytes != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatBytes(manifest.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state is UpdateUiState.Downloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DownloadProgress(state = state)
                }

                // What went wrong the last time the installer was asked for, if
                // it did. The download is still on disk and this button is still
                // the way to install it, so the message sits above both rather
                // than replacing the dialog with an error.
                val notice = state.installNotice()
                if (notice != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Not the manifest's `notes`: that field is a build number and
                // a commit hash, which tells somebody deciding whether to update
                // nothing. The release page the workflow writes proper notes to
                // is one tap away, so offer the link instead of the log.
                manifest.releaseUrl?.let { releaseUrl ->
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { controller.installation.openReleasePage(releaseUrl) },
                        modifier = Modifier.testTag("update_prompt_notes")
                    ) { Text("What's new") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (state) {
                        is UpdateUiState.Available -> controller.download()
                        is UpdateUiState.ReadyToInstall -> controller.install()
                        is UpdateUiState.NeedsInstallPermission -> controller.requestInstallPermission()
                        else -> Unit
                    }
                },
                enabled = !downloading,
                modifier = Modifier.testTag("update_prompt_confirm")
            ) {
                Text(
                    when (state) {
                        is UpdateUiState.ReadyToInstall -> "Install"
                        // Once the hand-off has been offered, this button means
                        // "try again" rather than "explain it again".
                        is UpdateUiState.NeedsInstallPermission ->
                            if (state.sentToSettings) "Try again" else "Open settings"

                        // Asking for a download that is already running reads as
                        // though the tap did nothing, which it did.
                        is UpdateUiState.Downloading -> "Downloading…"

                        else -> "Download & install"
                    }
                )
            }
        },
        dismissButton = {
            // Disabled for the same reason `onDismissRequest` is: there is no way
            // to cancel the transfer, so offering this would be offering
            // something other than what it says.
            TextButton(
                onClick = { controller.reset() },
                enabled = !downloading,
                modifier = Modifier.testTag("update_prompt_dismiss")
            ) { Text("Later") }
        }
    )
}

/**
 * The failure message this state is carrying, if any.
 *
 * Only the two states holding a downloaded APK can have one - see
 * [UpdateUiState.ReadyToInstall.notice].
 */
private fun UpdateUiState.installNotice(): String? = when (this) {
    is UpdateUiState.ReadyToInstall -> notice
    is UpdateUiState.NeedsInstallPermission -> notice
    else -> null
}

/**
 * Download progress, as a determinate bar where possible.
 *
 * Falls back to indeterminate when the server sends no `Content-Length` - which
 * GitHub's asset CDN sometimes does through a redirect - rather than showing a
 * bar stuck at 0% that looks broken.
 *
 * The speed is here because "downloading…" alone is the one state a user cannot
 * tell apart from a stalled transfer: a 2.6 MB file at 30 KB/s looks identical
 * to one that is not moving, and the only way to know which is to put a rate on
 * the screen. It is a reading over the last couple of seconds rather than the
 * average, because on a phone connection the average is a history of the signal
 * rather than an answer about now - see [TransferRate].
 */
@Composable
fun DownloadProgress(
    state: UpdateUiState.Downloading,
    modifier: Modifier = Modifier
) {
    val progress = state.progress
    val speed = formatRate(state.bytesPerSecond)

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
            text = when {
                progress != null && speed.isNotEmpty() ->
                    "${(progress * 100).toInt()}% · ${formatBytes(state.bytesRead)} " +
                        "of ${formatBytes(state.totalBytes)} · $speed"

                progress != null ->
                    "${(progress * 100).toInt()}% · ${formatBytes(state.bytesRead)} " +
                        "of ${formatBytes(state.totalBytes)}"

                speed.isNotEmpty() -> "Downloading… · $speed"

                else -> "Downloading…"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}