package com.anisequel.data.storage

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppStorageKind { SAVED, CACHE, TEMPORARY }

data class AppStorageEntry(
    val label: String,
    val detail: String,
    val bytes: Long,
    val kind: AppStorageKind
)

data class AppStorageSnapshot(
    val entries: List<AppStorageEntry>,
    val savedBytes: Long,
    val cacheBytes: Long,
    val temporaryBytes: Long
) {
    val totalBytes: Long get() = savedBytes + cacheBytes + temporaryBytes

    /** The share of on-disk bytes that is app-owned data rather than a cache. */
    val savedFraction: Float
        get() = if (totalBytes <= 0L) 0f else (savedBytes.toDouble() / totalBytes).toFloat()
}

/**
 * Measures the folders the app owns, without asking Android for a whole-device
 * storage figure.
 *
 * The distinction matters: a reader who taps "clear cache" in the system
 * Settings expects the disposable artwork to go, not the list snapshots they
 * rely on offline. Every entry here is measured from the same paths the features
 * write to, and nothing outside [Context.getFilesDir] / [Context.getNoBackupFilesDir]
 * / [Context.getCacheDir] is counted.
 */
object AppStorageManager {

    @OptIn(ExperimentalCoilApi::class)
    suspend fun read(context: Context): AppStorageSnapshot = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val datastore = File(app.filesDir, "datastore")
        val fonts = File(app.filesDir, "fonts")
        val sharedPrefs = File(app.filesDir.parentFile, "shared_prefs")
        val snapshots = File(app.noBackupFilesDir, "anilist_snapshots")
        val coverCache = runCatching { app.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L)
        val widgetCovers = treeBytes(File(app.noBackupFilesDir, "widget_cover_cache"))
        val preferences = treeBytes(datastore) + treeBytes(sharedPrefs)
        val fontBytes = treeBytes(fonts)
        val snapshotBytes = treeBytes(snapshots)
        val temporary = treeBytes(app.cacheDir)
        val filesRoot = treeBytes(app.filesDir)
        val otherSaved = (filesRoot - treeBytes(datastore) - fontBytes).coerceAtLeast(0L)
        val artwork = coverCache + widgetCovers
        val entries = listOf(
            AppStorageEntry(
                "Settings & local data",
                "Preferences, tier lists, hidden titles and notification reads",
                preferences + otherSaved,
                AppStorageKind.SAVED
            ),
            AppStorageEntry(
                "Profile & list snapshots",
                "Saved offline copies used by Library and Profile",
                snapshotBytes,
                AppStorageKind.SAVED
            ),
            AppStorageEntry(
                "Custom fonts",
                "Imported and downloaded font files",
                fontBytes,
                AppStorageKind.SAVED
            ),
            AppStorageEntry(
                "Cover artwork",
                "Poster and banner cache, kept across updates",
                artwork,
                AppStorageKind.CACHE
            ),
            AppStorageEntry(
                "Temporary files",
                "Exports and other scratch files Android may clear",
                temporary,
                AppStorageKind.TEMPORARY
            )
        ).filter { it.bytes > 0L }
        AppStorageSnapshot(
            entries = entries,
            savedBytes = preferences + otherSaved + snapshotBytes + fontBytes,
            cacheBytes = artwork,
            temporaryBytes = temporary
        )
    }

    suspend fun clearTemporary(context: Context): Unit = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { file -> runCatching { file.deleteRecursively() } }
        Unit
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = listOf("KB", "MB", "GB")
        var value = bytes.toDouble() / 1024.0
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit += 1
        }
        return if (value >= 100.0) {
            "${value.toInt()} ${units[unit]}"
        } else {
            String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
        }
    }

    private fun treeBytes(root: File): Long {
        if (!root.exists()) return 0L
        if (root.isFile) return root.length().coerceAtLeast(0L)
        return root.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length().coerceAtLeast(0L) }
    }
}
