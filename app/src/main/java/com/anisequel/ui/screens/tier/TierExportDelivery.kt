package com.anisequel.ui.screens.tier

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Where an exported board goes: the share chooser, or `Pictures/AniSequel`.
 *
 * Split from [renderTierListImage] because it is a different question with a
 * different failure mode. Rendering can fail because a poster 404s or the bitmap
 * does not fit the heap; delivery fails because the device has no app that
 * accepts `image/png`, or because MediaStore refused the insert. Keeping both in
 * one file made the renderer's memory work read as if it were about file paths.
 */

/**
 * Writes a PNG into the cache and hands its URI to whatever can share images.
 *
 * The cache, not the files directory: this file exists for one chooser round
 * trip and the system may evict it afterwards. Only the `exports/` directory is
 * published by the app's `FileProvider`, so a file written anywhere else here
 * would fail `getUriForFile` at the last step - the same two-strings-must-agree
 * trap the updater's paths file documents.
 */
internal suspend fun shareTierListImage(
    context: Context,
    bitmap: Bitmap,
    listName: String
): Boolean {
    val file = withContext(Dispatchers.IO) { writeExportFile(context, bitmap, listName) }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    withContext(Dispatchers.Main) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, listName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share tier list"))
    }
    return true
}

/** Writes a PNG to `Pictures/AniSequel` through MediaStore. */
internal suspend fun saveTierListImage(
    context: Context,
    bitmap: Bitmap,
    listName: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${safeName(listName)}-$now.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/AniSequel"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri: Uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return@withContext false

        val written = resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: false
        if (!written) {
            resolver.delete(uri, null, null)
            return@withContext false
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }
}

private fun writeExportFile(context: Context, bitmap: Bitmap, listName: String): File {
    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(
        directory,
        "${safeName(listName)}-${System.currentTimeMillis()}.png"
    )
    file.outputStream().use { output ->
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            throw IOException("PNG encoding failed")
        }
    }
    pruneOldExports(directory, keep = file)
    return file
}

/**
 * Deletes all but the newest few exports, keeping [keep].
 *
 * The cache is not a promise to clean up after the app: Android evicts it under
 * storage pressure, but a phone with room keeps everything written to it, and a
 * 4K board is tens of megabytes. Sharing the same list five times used to leave
 * five full-size PNGs in the cache for no reason, and they are duplicates of a
 * file the person already sent.
 *
 * Two older files are kept rather than none because the share chooser may still
 * be reading the file it was handed when the next export is written - deleting
 * the one being read turns a second share into a broken first one.
 */
private fun pruneOldExports(directory: File, keep: File) {
    val exports = directory.listFiles()
        ?.filter { it.isFile && it.extension == "png" }
        ?: return
    exports.asSequence()
        .filter { it != keep }
        .sortedByDescending { it.lastModified() }
        .drop(2)
        .forEach { it.delete() }
}

/**
 * A file name that survives every filesystem without becoming a different name.
 *
 * The list name is user text: it can contain `/`, a colon, or a leading dot, and
 * a `File` built from it would write outside the cache or vanish as a hidden
 * file. Everything outside the conservative set becomes a dash, and the result
 * is bounded so the MediaStore display name stays usable.
 */
private fun safeName(listName: String): String =
    listName.trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-', '.')
        .take(60)
        .ifBlank { "tier-list" }
