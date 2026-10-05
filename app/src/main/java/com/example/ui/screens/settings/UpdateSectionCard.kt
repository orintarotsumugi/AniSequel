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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.update.formatBytes
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.update.DownloadProgress
import com.example.ui.components.update.UpdateUiState
import com.example.ui.components.update.rememberUpdateController

@Composable
fun UpdateSectionCard(modifier: Modifier = Modifier) {
    val controller = rememberUpdateController()
    val state = controller.state

    // `rememberUpdateController()` hands back the process-wide controller, which
    // the launch prompt already checked. So this only fires on a genuinely cold
    // start that never got that far, and reopening Settings does not re-check.
    LaunchedEffect(Unit) {
        if (controller.state == UpdateUiState.Idle) controller.check()
    }

    SectionCard(
        title = "Updates",
        icon = AppVectorIcons.SectionUpdates,
        subtitle = "Check for a newer build",
        modifier = modifier
    ) {
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
                    SettingsButton(
                        text = "Not now",
                        onClick = { controller.reset() },
                        // Equal halves. These two were laid out at their labels'
                        // natural widths, so "Not now" came out visibly narrower
                        // than "Download" and the pair looked like a ragged edge
                        // rather than a decision.
                        modifier = Modifier.weight(1f),
                        variant = SettingsButtonVariant.Outlined,
                        testTag = "dismiss_update_button"
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    SettingsButton(
                        text = "Download",
                        onClick = { controller.download() },
                        icon = AppVectorIcons.Download,
                        modifier = Modifier.weight(1f),
                        variant = SettingsButtonVariant.Filled,
                        testTag = "download_update_button"
                    )
                }

                state.manifest.releaseUrl?.takeIf { it.isNotBlank() }?.let { releaseUrl ->
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsButton(
                        text = "What's changed in v${state.manifest.version}",
                        onClick = { controller.installation.openReleasePage(releaseUrl) },
                        icon = AppVectorIcons.OpenInNew,
                        variant = SettingsButtonVariant.Text,
                        fillWidth = true,
                        testTag = "release_notes_button"
                    )
                }
            }

            is UpdateUiState.ReadyToInstall -> {
                Spacer(modifier = Modifier.height(12.dp))
                SettingsButton(
                    text = "Install now",
                    onClick = { controller.install() },
                    icon = AppVectorIcons.Download,
                    fillWidth = true,
                    testTag = "install_update_button"
                )
            }

            is UpdateUiState.NeedsInstallPermission -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.sentToSettings) {
                        "Still not allowed to install apps. Grant it in Settings, then " +
                            "come back - AniSequel will pick up where it left off."
                    } else {
                        "Android will not let AniSequel open the installer until you allow it " +
                            "to install apps. This only has to be done once."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsButton(
                    text = if (state.sentToSettings) "Open settings again" else "Open Settings",
                    onClick = { controller.requestInstallPermission() },
                    icon = AppVectorIcons.Settings,
                    fillWidth = true,
                    testTag = "grant_install_permission_button"
                )
            }

            else -> Unit
        }

        when (state) {
            is UpdateUiState.UpToDate, is UpdateUiState.Failed -> {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsButton(
                    text = "Check again",
                    onClick = { controller.check() },
                    icon = AppVectorIcons.Refresh,
                    variant = SettingsButtonVariant.Tonal,
                    fillWidth = true,
                    testTag = "check_updates_button"
                )
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
