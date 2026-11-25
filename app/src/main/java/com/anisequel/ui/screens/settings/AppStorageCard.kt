package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.storage.AppStorageEntry
import com.anisequel.data.storage.AppStorageKind
import com.anisequel.data.storage.AppStorageManager
import com.anisequel.data.storage.AppStorageSnapshot
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.rememberIsResumed
import kotlinx.coroutines.launch

/**
 * The app's own file manager: what each private folder costs, and which parts are
 * disposable.
 *
 * It reads the same paths the features write, so the bar is not an estimate of
 * "storage" from a device API. The saved/cache split is the point of the card:
 * covers are deliberately in `noBackupFilesDir` and survive an update, while
 * temporary exports are the only thing a cache clear is allowed to delete.
 */
@Composable
internal fun AppStorageCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resumed = rememberIsResumed()
    var snapshot by remember { mutableStateOf<AppStorageSnapshot?>(null) }
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(resumed) {
        snapshot = AppStorageManager.read(context)
    }

    SectionCard(
        title = "App files & cache",
        icon = AppVectorIcons.Library,
        subtitle = "Every private folder AniSequel writes, and what a clear removes",
        modifier = modifier.testTag("app_storage_card")
    ) {
        val current = snapshot
        if (current == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Measuring app files…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StorageSummary(current)
            Spacer(modifier = Modifier.height(12.dp))
            current.entries.forEach { entry ->
                StorageRow(entry = entry, totalBytes = current.totalBytes)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = "Covers and list snapshots live in the app's private files, " +
                    "not in Android's disposable cache, so updating AniSequel does not " +
                    "make the app download them again.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (current.temporaryBytes > 0L) {
                TextButton(
                    onClick = {
                        if (!clearing) {
                            clearing = true
                            scope.launch {
                                AppStorageManager.clearTemporary(context)
                                snapshot = AppStorageManager.read(context)
                                clearing = false
                            }
                        }
                    },
                    enabled = !clearing,
                    modifier = Modifier.testTag("app_storage_clear_temporary")
                ) {
                    Text(if (clearing) "Clearing temporary files…" else "Clear temporary files")
                }
            }
        }
    }
}

@Composable
private fun StorageSummary(snapshot: AppStorageSnapshot) {
    Text(
        text = AppStorageManager.formatBytes(snapshot.totalBytes) + " on this device",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { snapshot.savedFraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
    )
    Spacer(modifier = Modifier.height(5.dp))
    Text(
        text = AppStorageManager.formatBytes(snapshot.savedBytes) + " kept · " +
            AppStorageManager.formatBytes(snapshot.cacheBytes) + " cache · " +
            AppStorageManager.formatBytes(snapshot.temporaryBytes) + " temporary",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StorageRow(entry: AppStorageEntry, totalBytes: Long) {
    val fraction = if (totalBytes <= 0L) 0f else (entry.bytes.toDouble() / totalBytes).toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = AppStorageManager.formatBytes(entry.bytes),
                style = MaterialTheme.typography.labelMedium,
                color = when (entry.kind) {
                    AppStorageKind.SAVED -> MaterialTheme.colorScheme.primary
                    AppStorageKind.CACHE -> MaterialTheme.colorScheme.tertiary
                    AppStorageKind.TEMPORARY -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        )
    }
}
