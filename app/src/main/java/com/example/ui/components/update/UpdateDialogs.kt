package com.example.ui.components.update

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
import com.example.data.update.formatBytes

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
                enabled = state !is UpdateUiState.Downloading,
                modifier = Modifier.testTag("update_prompt_confirm")
            ) {
                Text(
                    when (state) {
                        is UpdateUiState.ReadyToInstall -> "Install"
                        // Once the hand-off has been offered, this button means
                        // "try again" rather than "explain it again".
                        is UpdateUiState.NeedsInstallPermission ->
                            if (state.sentToSettings) "Try again" else "Open settings"

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