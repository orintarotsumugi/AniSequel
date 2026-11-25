package com.anisequel.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.backup.AppBackupManager
import com.anisequel.data.backup.BackupManifest
import com.anisequel.data.backup.BackupSelection
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressivePrimaryButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class BackupBusy { NONE, BACKUP, READING, RESTORE }

private data class PendingRestore(val uri: Uri, val manifest: BackupManifest)

/**
 * Backup and restore, with the reader choosing how much of the app travels.
 *
 * The same card is the last page of first-run setup. The switch state is the
 * answer to "how much": settings and lists, offline snapshots, and the large
 * re-downloadable cover cache are separable, so a backup is not all-or-nothing.
 * Restoring stages and validates the archive before touching app storage, then
 * restarts the process because DataStore caches parsed files.
 */
@Composable
fun BackupRestoreCard(
    modifier: Modifier = Modifier,
    onRestoreApplied: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includeSettings by rememberSaveable { mutableStateOf(true) }
    var includeOffline by rememberSaveable { mutableStateOf(true) }
    var includeArtwork by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(BackupBusy.NONE) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }

    val selection = BackupSelection(
        settings = includeSettings,
        offline = includeOffline,
        artwork = includeArtwork
    )
    val idle = busy == BackupBusy.NONE

    // The operations run from the picker callback's own scope rather than from a
    // LaunchedEffect keyed on the chosen Uri. Clearing that key inside the effect
    // restarts it with null and cancels the backup it just started - a bug that
    // would leave a zero-byte file behind.
    val startBackup: (Uri) -> Unit = { uri ->
        if (busy == BackupBusy.NONE) {
            scope.launch {
                busy = BackupBusy.BACKUP
                AppBackupManager.backup(context, uri, selection).fold(
                    onSuccess = { result ->
                        status = "Backed up ${result.fileCount} files (" +
                            "${formatBytes(result.bytes)})."
                    },
                    onFailure = { error ->
                        status = error.message ?: "The backup could not be written."
                    }
                )
                busy = BackupBusy.NONE
            }
        }
    }
    val startInspect: (Uri) -> Unit = { uri ->
        if (busy == BackupBusy.NONE) {
            scope.launch {
                busy = BackupBusy.READING
                AppBackupManager.inspect(context, uri).fold(
                    onSuccess = { manifest -> pendingRestore = PendingRestore(uri, manifest) },
                    onFailure = { error ->
                        status = error.message ?: "That backup could not be read."
                    }
                )
                busy = BackupBusy.NONE
            }
        }
    }
    val startRestore: (PendingRestore) -> Unit = { pending ->
        if (busy == BackupBusy.NONE) {
            scope.launch {
                busy = BackupBusy.RESTORE
                AppBackupManager.restore(context, pending.uri, pending.manifest, selection).fold(
                    onSuccess = { result ->
                        status = "Restored ${result.fileCount} files. Restarting AniSequel…"
                        if (onRestoreApplied != null) onRestoreApplied()
                        else AppBackupManager.restart(context)
                    },
                    onFailure = { error ->
                        status = error.message ?: "The backup could not be restored."
                        busy = BackupBusy.NONE
                    }
                )
            }
        }
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) startBackup(uri) }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) startInspect(uri) }

    SectionCard(
        title = "Backup & restore",
        icon = AppVectorIcons.Restore,
        subtitle = "Choose how much of the app travels in one .zip file",
        modifier = modifier.testTag("backup_restore_card")
    ) {
        BackupScopeRow(
            title = "Settings & local data",
            subtitle = "Theme, layout, lists, hidden titles, notification reads and fonts",
            checked = includeSettings,
            enabled = idle,
            onCheckedChange = { includeSettings = it },
            tag = "backup_include_settings"
        )
        Spacer(modifier = Modifier.height(8.dp))
        BackupScopeRow(
            title = "Offline snapshots",
            subtitle = "Saved profile and list copies used when the network is unavailable",
            checked = includeOffline,
            enabled = idle,
            onCheckedChange = { includeOffline = it },
            tag = "backup_include_offline"
        )
        Spacer(modifier = Modifier.height(8.dp))
        BackupScopeRow(
            title = "Cover artwork",
            subtitle = "Large, and can be downloaded again; useful when moving to a new phone",
            checked = includeArtwork,
            enabled = idle,
            onCheckedChange = { includeArtwork = it },
            tag = "backup_include_artwork"
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "The AniList session is never backed up: its key stays on this " +
                "phone, so restoring it could only replace a working sign-in with an " +
                "unreadable one. Sign in again after a restore.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = idle && !selection.isEmpty(),
                modifier = Modifier.weight(1f).testTag("backup_restore_pick")
            ) {
                Text("Restore")
            }
            ExpressivePrimaryButton(
                text = if (busy == BackupBusy.BACKUP) "Backing up…" else "Back up",
                onClick = { createLauncher.launch(AppBackupManager.defaultFileName()) },
                modifier = Modifier.weight(1.3f),
                icon = AppVectorIcons.Export,
                loading = busy == BackupBusy.BACKUP,
                enabled = idle && !selection.isEmpty(),
                testTag = "backup_create"
            )
        }
        if (busy == BackupBusy.READING || busy == BackupBusy.RESTORE) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (busy == BackupBusy.READING) "Checking the backup…" else "Restoring…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        status?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("backup_status")
            )
        }
    }

    pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = { if (idle) pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Column {
                    Text(
                        text = "From ${formatDate(pending.manifest.createdAtMillis)} · " +
                            "AniSequel ${pending.manifest.appVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pending.manifest.contents.joinToString { it.displayName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Only the selected parts are replaced. The app will " +
                            "restart afterwards so every screen reads the restored files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (idle) {
                            pendingRestore = null
                            startRestore(pending)
                        }
                    },
                    enabled = idle
                ) { Text("Restore and restart") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRestore = null },
                    enabled = idle
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BackupScopeRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    SwitchRow(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.testTag(tag)
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(millis))
