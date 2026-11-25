package com.anisequel.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.anisequel.BuildConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupResult(
    val manifest: BackupManifest,
    val fileCount: Int,
    val bytes: Long
)

data class RestoreResult(
    val manifest: BackupManifest,
    val fileCount: Int
)

/**
 * Writes and reads the one backup format this app owns.
 *
 * ## What is deliberately not in a backup
 *
 * The AniList session and the first-run answer are protected files. The token is
 * sealed by an Android keystore key that does not leave the install, so restoring
 * it on a new phone cannot work; restoring it on the same phone after a reinstall
 * would replace a working session with ciphertext whose key was deleted with the
 * app. Onboarding is current-device state: a restore from a completed install must
 * not skip a first run that is still in progress, and a restore during setup must
 * not trap the reader in setup again. Both files are left exactly as they are.
 *
 * ## Why restore stages first
 *
 * A zip is untrusted input. Every entry is written under a private staging
 * directory after its path is proven to stay inside that directory, and nothing
 * touches the real store until the whole archive has been read. The final copy
 * replaces one file at a time with a sibling temp file and an atomic rename, so an
 * interrupted restore leaves either the old file or the complete new one. The
 * process is restarted afterwards because DataStore caches parsed files in memory:
 * writing the bytes under a live instance would be undone by its next write.
 */
object AppBackupManager {

    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_RESTORE_BYTES = 8L * 1024 * 1024 * 1024
    private const val RESTORE_DIRECTORY = "anisequel_backup_restore"

    private val protectedFileNames = setOf(
        "anisequel_auth_prefs.preferences_pb",
        "anisequel_onboarding_prefs.preferences_pb"
    )

    private data class Candidate(
        val content: BackupContent,
        val entryName: String,
        val file: File
    )

    fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "anisequel-backup-$stamp.zip"
    }

    suspend fun backup(
        context: Context,
        uri: Uri,
        selection: BackupSelection
    ): Result<BackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(!selection.isEmpty()) { "Choose at least one thing to back up." }
            val candidates = candidates(context, selection)
            require(candidates.isNotEmpty()) { "There is nothing to back up yet." }
            val manifest = BackupManifest(
                schema = BackupManifest.CURRENT_SCHEMA,
                packageName = context.packageName,
                appVersion = BuildConfig.VERSION_NAME,
                createdAtMillis = System.currentTimeMillis(),
                contents = selection.contents()
            )
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("That file could not be opened for writing.")
            var bytes = 0L
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                zip.putNextEntry(ZipEntry(BackupManifest.ENTRY_NAME))
                zip.write(manifest.encode().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                candidates.forEach { candidate ->
                    zip.putNextEntry(ZipEntry(candidate.entryName))
                    candidate.file.inputStream().buffered().use { input ->
                        bytes += input.copyTo(zip)
                    }
                    zip.closeEntry()
                }
            }
            BackupResult(manifest, candidates.size, bytes)
        }
    }

    suspend fun inspect(context: Context, uri: Uri): Result<BackupManifest> =
        withContext(Dispatchers.IO) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("That file could not be opened.")
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name == BackupManifest.ENTRY_NAME) {
                            val bytes = zip.readLimited(MAX_MANIFEST_BYTES)
                            val manifest = BackupManifest.parse(bytes.toString(Charsets.UTF_8))
                                ?: error("That file is not an AniSequel backup.")
                            require(manifest.packageName == context.packageName) {
                                "That backup belongs to another app."
                            }
                            return@runCatching manifest
                        }
                        zip.closeEntry()
                    }
                }
                error("That file is not an AniSequel backup.")
            }
        }

    suspend fun restore(
        context: Context,
        uri: Uri,
        manifest: BackupManifest,
        selection: BackupSelection
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(manifest.packageName == context.packageName) {
                "That backup belongs to another app."
            }
            val staged = File(context.cacheDir, RESTORE_DIRECTORY)
            staged.deleteRecursively()
            staged.mkdirs()
            val stagedRoot = staged.canonicalFile.toPath()
            val extracted = mutableListOf<Pair<String, File>>()
            var totalBytes = 0L
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("That file could not be opened.")
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = safeEntryName(entry.name)
                        if (entry.isDirectory || name == null || name == BackupManifest.ENTRY_NAME) {
                            zip.closeEntry()
                            continue
                        }
                        val content = contentFor(name)
                        val wanted = content != null &&
                            selection.includes(content) &&
                            manifest.contents.contains(content) &&
                            !isProtected(name)
                        if (!wanted) {
                            zip.closeEntry()
                            continue
                        }
                        val target = File(staged, name)
                        val canonical = target.canonicalFile
                        require(canonical.toPath().startsWith(stagedRoot)) {
                            "That backup contains an unsafe path."
                        }
                        canonical.parentFile?.mkdirs()
                        val copied = FileOutputStream(canonical).use { output ->
                            zip.copyTo(output)
                        }
                        totalBytes += copied
                        require(totalBytes <= MAX_RESTORE_BYTES) {
                            "That backup is too large to restore."
                        }
                        extracted += name to canonical
                        zip.closeEntry()
                    }
                }
                require(extracted.isNotEmpty()) {
                    "That backup has nothing this app can restore."
                }
                extracted.forEach { (name, source) -> copyIntoPlace(context, name, source) }
                RestoreResult(manifest, extracted.size)
            } finally {
                staged.deleteRecursively()
            }
        }
    }

    /** Starts a fresh process so DataStore reads the files just written. */
    fun restart(context: Context) {
        val app = context.applicationContext
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            runCatching { app.startActivity(intent) }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            Process.killProcess(Process.myPid())
        }, 700L)
    }

    private fun candidates(context: Context, selection: BackupSelection): List<Candidate> {
        val result = mutableListOf<Candidate>()
        if (selection.settings) {
            context.filesDir.walkTopDown()
                .filter { it.isFile && it.isBackupable() && it.name !in protectedFileNames }
                .forEach { file ->
                    val relative = file.relativeTo(context.filesDir).invariantSeparatorsPath
                    result += Candidate(BackupContent.SETTINGS, "files/$relative", file)
                }
            sharedPreferencesDirectory(context).listFiles()
                ?.filter { it.isFile && it.extension == "xml" }
                ?.forEach { file ->
                    result += Candidate(
                        BackupContent.SETTINGS,
                        "shared_prefs/${file.name}",
                        file
                    )
                }
        }
        if (selection.offline) {
            addTree(
                root = File(context.noBackupFilesDir, "anilist_snapshots"),
                prefix = "no_backup/anilist_snapshots",
                content = BackupContent.OFFLINE,
                into = result
            )
        }
        if (selection.artwork) {
            addTree(
                root = File(context.noBackupFilesDir, "widget_cover_cache"),
                prefix = "no_backup/widget_cover_cache",
                content = BackupContent.ARTWORK,
                into = result
            )
            addTree(
                root = File(context.noBackupFilesDir, "anilist_cover_cache"),
                prefix = "no_backup/anilist_cover_cache",
                content = BackupContent.ARTWORK,
                into = result
            )
        }
        return result.distinctBy { it.entryName }
    }

    private fun addTree(
        root: File,
        prefix: String,
        content: BackupContent,
        into: MutableList<Candidate>
    ) {
        if (!root.isDirectory) return
        root.walkTopDown()
            .filter { it.isFile && it.isBackupable() }
            .forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                into += Candidate(content, "$prefix/$relative", file)
            }
    }

    private fun File.isBackupable(): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return !lower.endsWith(".tmp") &&
            !lower.endsWith(".part") &&
            !lower.endsWith(".lock") &&
            !lower.endsWith(".corrupt")
    }

    private fun sharedPreferencesDirectory(context: Context): File {
        val direct = File(context.filesDir.parentFile, "shared_prefs")
        return if (direct.isDirectory) direct
        else File(context.applicationInfo.dataDir, "shared_prefs")
    }

    private fun safeEntryName(raw: String): String? {
        val name = raw.replace('\\', '/')
        if (name.isBlank() || name.startsWith('/')) return null
        if (name.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        return name
    }

    private fun contentFor(entryName: String): BackupContent? = when {
        entryName.startsWith("files/") -> BackupContent.SETTINGS
        entryName.startsWith("shared_prefs/") -> BackupContent.SETTINGS
        entryName.startsWith("no_backup/anilist_snapshots/") -> BackupContent.OFFLINE
        entryName.startsWith("no_backup/widget_cover_cache/") -> BackupContent.ARTWORK
        entryName.startsWith("no_backup/anilist_cover_cache/") -> BackupContent.ARTWORK
        else -> null
    }

    private fun isProtected(entryName: String): Boolean {
        if (!entryName.startsWith("files/datastore/")) return false
        return protectedFileNames.any { entryName.endsWith("/$it") }
    }

    private fun copyIntoPlace(context: Context, entryName: String, source: File) {
        val destination = when {
            entryName.startsWith("files/") ->
                File(context.filesDir, entryName.removePrefix("files/"))
            entryName.startsWith("shared_prefs/") ->
                File(sharedPreferencesDirectory(context), entryName.removePrefix("shared_prefs/"))
            entryName.startsWith("no_backup/") ->
                File(context.noBackupFilesDir, entryName.removePrefix("no_backup/"))
            else -> return
        }
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, ".${destination.name}.restoring")
        Files.copy(source.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun ZipInputStream.readLimited(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) throw IOException("That backup manifest is too large.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
