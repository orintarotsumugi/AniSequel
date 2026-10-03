package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.update.formatBytes
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.DownloadProgress
import com.example.ui.components.UpdateUiState
import com.example.ui.components.openReleasePage
import com.example.ui.components.rememberUpdateController

@Composable
fun UpdateSectionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = rememberUpdateController()
    val state = controller.state

    LaunchedEffect(Unit) {
        if (state == UpdateUiState.Idle) controller.check(scope)
    }

    SectionCard(title = "Updates", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (state) {
                        UpdateUiState.Idle, UpdateUiState.Checking ->
                            "Checking GitHub for a newer build…"

                        is UpdateUiState.UpToDate ->
                            "You're on v${state.installedVersion}, the latest release."

                        is UpdateUiState.Available ->
                            "AniSequel v${state.manifest.version} is available!"

                        is UpdateUiState.ReadyToInstall ->
                            "Downloaded. Ready to install."

                        is UpdateUiState.NeedsInstallPermission ->
                            "Downloaded. AniSequel needs permission to install apps."

                        is UpdateUiState.Failed ->
                            state.message

                        is UpdateUiState.Downloading -> "Downloading update…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (state is UpdateUiState.Available) {
                    Text(
                        text = "Size: ${formatBytes(state.manifest.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when (state) {
            is UpdateUiState.Downloading -> {
                Spacer(modifier = Modifier.height(12.dp))
                DownloadProgress(state = state)
            }

            is UpdateUiState.Available -> {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { controller.reset() },
                        modifier = Modifier.testTag("dismiss_update_button")
                    ) { Text("Not now") }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { controller.download(scope) },
                        modifier = Modifier.testTag("download_update_button")
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                    }
                }

                state.manifest.releaseUrl?.takeIf { it.isNotBlank() }?.let { releaseUrl ->
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { openReleasePage(context, releaseUrl) },
                        modifier = Modifier.testTag("release_notes_button")
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("What's changed in v${state.manifest.version}")
                    }
                }
            }

            is UpdateUiState.ReadyToInstall -> {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { controller.install(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("install_update_button")
                ) { Text("Install now") }
            }

            is UpdateUiState.NeedsInstallPermission -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Android will not let AniSequel open the installer until you allow it to install apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { controller.requestInstallPermission(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_install_permission_button")
                ) { Text("Open Settings") }
            }

            else -> Unit
        }

        when (state) {
            is UpdateUiState.UpToDate, is UpdateUiState.Failed -> {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { controller.check(scope) },
                    modifier = Modifier.testTag("check_updates_button")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check again")
                }
            }

            else -> Unit
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AniSequel checks github.com/LOST-4EVER/AniSequel for new releases without sending personal data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
