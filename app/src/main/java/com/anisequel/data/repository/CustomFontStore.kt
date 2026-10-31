package com.anisequel.data.repository

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A font the reader imported, as the app now stores it. */
data class ImportedFont(val path: String, val name: String)

/**
 * The one imported font, kept inside the app's own storage.
 *
 * ## Why a copy, not the document URI
 *
 * `ACTION_OPEN_DOCUMENT` can grant a persistable URI, and the obvious design is to
 * store that URI and load the font through it. The grant belongs to the app that
 * supplied the file: uninstall that app, or move the file, and the font stops
 * resolving - months later, with no message, on a preference the reader set once.
 * Copying the bytes into `filesDir/fonts` makes the choice durable and costs a
 * single 100-500 KB file.
 *
 * ## Why validation happens here
 *
 * Compose's `Font(file)` is lazy: it hands the path to the platform at first draw.
 * A file that is not actually a font would then fail inside the theme, on every
 * screen, instead of on the import button that produced it. `Typeface.createFromFile`
 * is the same decoder the platform will use, and it throws for anything unusable -
 * so a bad pick is refused while the reader is still looking at the picker.
 */
object CustomFontStore {

    /** The single slot: one imported font at a time, replaced by the next import. */
    fun fontFile(context: Context): File =
        File(File(context.filesDir, "fonts"), "custom_font")

    /**
     * Copies [uri] over the current font, after proving it is one.
     *
     * Writes to a sibling temp file first and renames over the target, so a pick
     * that fails validation cannot destroy the font already in use.
     */
    suspend fun import(context: Context, uri: Uri): Result<ImportedFont> =
        withContext(Dispatchers.IO) {
            val target = fontFile(context)
            val temp = File(target.parentFile, "custom_font.importing")
            try {
                target.parentFile?.mkdirs()
                val opened = context.contentResolver.openInputStream(uri)
                    ?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                if (opened == null) {
                    return@withContext Result.failure(
                        IllegalStateException("That file could not be opened.")
                    )
                }
                // Throws for a file the platform's font decoder cannot read.
                Typeface.createFromFile(temp)
                if (target.exists() && !target.delete()) {
                    error("The current font could not be replaced.")
                }
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                }
                Result.success(
                    ImportedFont(
                        path = target.absolutePath,
                        name = displayName(context, uri) ?: "Imported font"
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                if (temp.exists()) temp.delete()
            }
        }

    /** Removes the imported file; the preference is cleared by the caller. */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        fontFile(context).delete()
    }

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
        }.getOrNull()
}
