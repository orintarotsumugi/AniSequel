package com.anisequel.data.repository

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * One of the families offered by the Font tab's download list.
 *
 * [familyName] is the name Google Fonts' CSS endpoint expects, not necessarily
 * the title the app draws: "JetBrains Mono" happens to be both, while a family
 * with a weight suffix would not be.
 */
data class DownloadableFont(
    val id: String,
    val displayName: String,
    val familyName: String,
    val description: String
)

/**
 * The free downloads the Font tab offers.
 *
 * These are deliberately *not* in `assets`: packaging three families, each with
 * a regular and a bold file, added megabytes to every install for a choice many
 * readers never open. The files are fetched once from Google Fonts and kept in
 * the app's own storage, so switching away and back does not download again.
 *
 * ## The three
 *
 * - JetBrains Mono: the coding face, for readers who want unambiguous glyphs.
 * - Inter: a neutral Google Fonts interface face.
 * - Roboto: Android's original interface face, offered as the familiar choice.
 *
 * Google's CSS endpoint is free and needs no API key. It is asked for TrueType
 * only - Android cannot decode the WOFF2 URLs it serves to browsers - and for
 * regular and bold only, which is what the type scale actually asks for.
 */
object GoogleFontCatalog {
    val fonts: List<DownloadableFont> = listOf(
        DownloadableFont(
            id = "jetbrains_mono",
            displayName = "JetBrains Mono",
            familyName = "JetBrains Mono",
            description = "A coding face with a tall x-height and a slashed zero."
        ),
        DownloadableFont(
            id = "inter",
            displayName = "Inter",
            familyName = "Inter",
            description = "A neutral Google Fonts face made for small interface text."
        ),
        DownloadableFont(
            id = "roboto",
            displayName = "Roboto",
            familyName = "Roboto",
            description = "Android's original interface face, in its current cut."
        )
    )
}

/** One `@font-face` URL from Google Fonts' CSS, with the weight it provides. */
internal data class GoogleFontFace(val weight: Int, val url: String)

/**
 * Reads the TrueType faces out of Google Fonts' `@font-face` CSS.
 *
 * The endpoint answers a browser user agent with WOFF2, which the platform's
 * font decoder cannot read. The request therefore sends no browser user agent
 * and this parser refuses anything that is not a `.ttf` on Google's font host;
 * a route change on Google's side fails at the download button instead of
 * handing an unreadable file to `Typeface`.
 */
internal fun parseGoogleFontFaces(css: String): List<GoogleFontFace> {
    val blocks = Regex("@font-face\\s*\\{([^}]*)\\}", RegexOption.DOT_MATCHES_ALL)
    return blocks.findAll(css).mapNotNull { block ->
        val body = block.groupValues[1]
        val weight = Regex("font-weight\\s*:\\s*(\\d+)")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: return@mapNotNull null
        val url = Regex("src\\s*:\\s*url\\(([^)]+)\\)")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.trim('\'', '"')
            ?: return@mapNotNull null
        val path = url.substringBefore('?')
        if (!url.startsWith("https://fonts.gstatic.com/") || !path.endsWith(".ttf")) {
            return@mapNotNull null
        }
        GoogleFontFace(weight = weight, url = url)
    }.toList()
}

/**
 * Downloads one [DownloadableFont] and keeps its regular and bold files.
 *
 * ## Why two files, not the whole family
 *
 * A Google Font family can be a dozen static files. The app's type scale asks for
 * normal and bold at most, so two files are downloaded; italics and the other
 * weights would never be drawn. The CSS endpoint is asked for `400` and `700`
 * and `subset=latin`, which keeps each family around a few hundred kilobytes.
 *
 * ## Why the files have stable names
 *
 * Android's `Typeface` caches decoded faces by path. A new download replacing the
 * bytes at the same path can keep showing the old face until the process
 * restarts, so downloaded names include the family id and every successful
 * download writes through a temporary file. A later download of the same family
 * is a cache hit and never touches the bytes.
 *
 * ## Why every failure is a [Result]
 *
 * A font download is a preference, not a dependency. No connection, a changed
 * Google URL or a malformed CSS should leave the current face working and show a
 * message on the download row; none of those may take the Settings screen down.
 */
class GoogleFontDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    suspend fun download(context: Context, font: DownloadableFont): Result<ImportedFont> =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "fonts").apply { mkdirs() }
                val regular = File(dir, "google_${font.id}_400.ttf")
                val bold = File(dir, "google_${font.id}_700.ttf")

                if (isUsable(regular) && isUsable(bold)) {
                    return@withContext Result.success(
                        ImportedFont(
                            path = regular.absolutePath,
                            name = font.displayName,
                            boldPath = bold.absolutePath
                        )
                    )
                }

                val faces = fetchFaces(font)
                val regularFace = faces.firstOrNull { it.weight == 400 }
                    ?: faces.minByOrNull { kotlin.math.abs(it.weight - 400) }
                    ?: error("Google Fonts returned no TrueType face for ${font.displayName}.")
                val boldFace = faces.firstOrNull { it.weight == 700 }

                val regularPath = downloadFace(regularFace.url, regular)
                val boldPath = boldFace
                    ?.takeIf { it.url != regularFace.url }
                    ?.let { downloadFace(it.url, bold) }
                    ?: bold.takeIf { isUsable(it) }?.absolutePath

                Result.success(
                    ImportedFont(
                        path = regularPath,
                        name = font.displayName,
                        boldPath = boldPath
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }

    private fun fetchFaces(font: DownloadableFont): List<GoogleFontFace> {
        val family = font.familyName.replace(" ", "+")
        val url = "https://fonts.googleapis.com/css2" +
            "?family=$family:wght@400;700&subset=latin"
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Google Fonts returned HTTP ${response.code} for ${font.displayName}.")
            }
            val css = response.body?.string().orEmpty()
            if (css.length > MAX_CSS_BYTES) {
                error("Google Fonts returned an unexpectedly large stylesheet.")
            }
            parseGoogleFontFaces(css).ifEmpty {
                error(
                    "Google Fonts returned no downloadable TrueType file for " +
                        "${font.displayName}. Try again later."
                )
            }
        }
    }

    private fun downloadFace(url: String, target: File): String {
        val parsed = url.toHttpUrlOrNull()
            ?: error("Google Fonts returned an invalid font URL.")
        if (parsed.scheme != "https" || parsed.host != "fonts.gstatic.com") {
            error("Google Fonts returned a font URL from an unexpected host.")
        }

        val temporary = File(target.parentFile, target.name + ".part")
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    error("The font file download failed with HTTP ${response.code}.")
                }
                val body = response.body ?: error("The font file was empty.")
                if (body.contentLength() > MAX_FONT_BYTES) {
                    error("The font file is too large to store.")
                }
                body.byteStream().use { input ->
                    temporary.outputStream().use { output -> copyLimited(input, output) }
                }
            }
            // The same decoder the theme will use; a bad response is refused now
            // rather than failing later on every screen that draws text.
            Typeface.createFromFile(temporary)
            moveIntoPlace(temporary, target)
            return target.absolutePath
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun isUsable(file: File): Boolean =
        file.isFile &&
            file.length() > 0 &&
            runCatching { Typeface.createFromFile(file) }.isSuccess

    private fun copyLimited(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_FONT_BYTES) {
                error("The font file is too large to store.")
            }
            output.write(buffer, 0, read)
        }
        if (total == 0L) error("The font file was empty.")
        return total
    }

    private fun moveIntoPlace(temporary: File, target: File) {
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        /** A CSS response is a handful of blocks; anything near this is not one. */
        const val MAX_CSS_BYTES = 256 * 1024

        /** One static face is normally 150-650 KB. */
        const val MAX_FONT_BYTES = 8L * 1024 * 1024
    }
}
