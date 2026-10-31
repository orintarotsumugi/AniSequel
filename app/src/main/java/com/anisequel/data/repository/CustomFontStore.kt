package com.anisequel.data.repository

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

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
 *
 * ## Why every import gets a new file name
 *
 * The old store always wrote `custom_font`. Android's `Typeface` keeps a
 * process-wide cache keyed by file path, so replacing the bytes at that path and
 * reloading it could hand back the *previous* face until the app restarted. A
 * fresh name per import makes the path a different key, which is the only thing
 * that reliably makes the new font visible immediately. The old files are deleted
 * after the new one is in place.
 */
object CustomFontStore {

    /**
     * A font is a few hundred kilobytes; a mis-picked video is gigabytes.
     *
     * Checked while copying rather than from `OpenableColumns.SIZE`, because
     * document providers are allowed to report -1 and a guard that reads as
     * load-bearing and cannot fail is worse than one that does its job.
     */
    private const val MAX_FONT_BYTES = 24L * 1024 * 1024

    private fun fontsDir(context: Context): File = File(context.filesDir, "fonts")

    /**
     * Copies [uri] into the app's font directory, after proving it is a font.
     *
     * Writes to a sibling temp file first and moves it into place, so a pick that
     * fails validation cannot destroy the font already in use.
     */
    suspend fun import(context: Context, uri: Uri): Result<ImportedFont> =
        withContext(Dispatchers.IO) {
            val dir = fontsDir(context)
            val temp = File(dir, "font.importing")
            try {
                dir.mkdirs()
                val opened = context.contentResolver.openInputStream(uri)
                    ?.use { input -> temp.outputStream().use { output -> copyLimited(input, output) } }
                if (opened == null) {
                    return@withContext Result.failure(
                        IllegalStateException("That file could not be opened.")
                    )
                }
                if (opened == 0L) {
                    return@withContext Result.failure(
                        IllegalStateException("That file is empty.")
                    )
                }
                // Throws for a file the platform's font decoder cannot read.
                Typeface.createFromFile(temp)

                val rawName = displayName(context, uri)
                val name = prettyFontName(rawName) ?: "Imported font"
                val target = File.createTempFile("custom_font_", extensionFor(rawName), dir)
                moveIntoPlace(temp, target)

                // Only after the new font is in place: a failed import must leave
                // the previous face working, not delete it first.
                dir.listFiles()
                    ?.filter { it != target && it != temp }
                    ?.forEach { it.delete() }

                Result.success(ImportedFont(path = target.absolutePath, name = name))
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                if (temp.exists()) temp.delete()
            }
        }

    /** Removes every imported file; the preference is cleared by the caller. */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        fontsDir(context).listFiles()?.forEach { it.delete() }
    }

    private fun copyLimited(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_FONT_BYTES) {
                error(
                    "That file is larger than ${MAX_FONT_BYTES / (1024 * 1024)} MB, " +
                        "which is too big to copy."
                )
            }
            output.write(buffer, 0, read)
        }
        return total
    }

    /**
     * An atomic rename where the filesystem supports it.
     *
     * Same directory, so `ATOMIC_MOVE` is available on every volume the app's
     * internal storage can be on; the fallback exists for the emulator's odd
     * overlay filesystems rather than as a licence to skip the atomic path.
     */
    private fun moveIntoPlace(temp: File, target: File) {
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
        }.getOrNull()

    private fun extensionFor(rawName: String?): String {
        val extension = rawName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return if (extension in FONT_EXTENSIONS) ".$extension" else ".font"
    }

    private val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
}

/**
 * "Fira_Sans-Bold.ttf" -> "Fira Sans Bold".
 *
 * Document providers hand back file names, not font names, and the raw string was
 * drawn verbatim in Settings - underscores and extension included. The extension
 * is dropped and separators become spaces; the face's internal name would be
 * nicer still, but reading it means parsing the font table, and a reader who
 * picked "MyFont-Bold.otf" recognises it fine without one.
 */
internal fun prettyFontName(raw: String?): String? {
    val withoutExtension = raw
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.substringBeforeLast('.')
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
    return withoutExtension?.takeIf { it.isNotEmpty() }
}
