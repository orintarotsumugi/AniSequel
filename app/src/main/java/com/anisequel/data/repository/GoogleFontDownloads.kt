package com.anisequel.data.repository

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.net.URLEncoder
import java.util.Locale
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
    val description: String,
    /** The catalogue grouping, e.g. "Serif" or "Sans serif". */
    val category: String = ""
)

/**
 * The free downloads the Font tab offers.
 *
 * These are deliberately *not* in `assets`: packaging families, each with a
 * regular and a bold file, added megabytes to every install for a choice many
 * readers never open. The files are fetched once from Google Fonts and kept in
 * the app's own storage, so switching away and back does not download again.
 *
 * [popular] is a local index for searching, not a packaged font: every family is
 * downloaded on demand, and a name outside the index is still offered to Google's
 * CSS endpoint as an exact family.
 *
 * Google's CSS endpoint is free and needs no API key. It is asked for TrueType
 * only - Android cannot decode the WOFF2 URLs it serves to browsers - and for
 * regular and bold only, which is what the type scale actually asks for.
 */
object GoogleFontCatalog {

    /** The three families shown before anyone types a search. */
    val quickPicks: List<DownloadableFont> = listOf(
        googleFont("jetbrains_mono", "JetBrains Mono", "Monospace"),
        googleFont("inter", "Inter", "Sans serif"),
        googleFont("roboto", "Roboto", "Android's original")
    )

    /**
     * A local index of Google Fonts' most-used families.
     *
     * Google's metadata endpoint is a 2.7 MB document for every family it
     * publishes. Downloading and parsing that on a phone to answer one search
     * would cost more time and memory than the font being chosen, so this is the
     * short list a reader is actually likely to type. A query that is not in it
     * still works: [search] offers the exact name, and the CSS endpoint resolves
     * any family Google publishes.
     */
    val popular: List<DownloadableFont> = listOf(
        googleFont("inter", "Inter", "Sans serif"),
        googleFont("roboto", "Roboto", "Android's original"),
        googleFont("open_sans", "Open Sans", "Sans serif"),
        googleFont("lato", "Lato", "Sans serif"),
        googleFont("montserrat", "Montserrat", "Geometric sans"),
        googleFont("poppins", "Poppins", "Geometric sans"),
        googleFont("raleway", "Raleway", "Display sans"),
        googleFont("nunito", "Nunito", "Rounded sans"),
        googleFont("merriweather", "Merriweather", "Serif"),
        googleFont("playfair_display", "Playfair Display", "Display serif"),
        googleFont("oswald", "Oswald", "Condensed sans"),
        googleFont("source_sans_3", "Source Sans 3", "Sans serif"),
        googleFont("work_sans", "Work Sans", "Sans serif"),
        googleFont("quicksand", "Quicksand", "Rounded sans"),
        googleFont("rubik", "Rubik", "Rounded sans"),
        googleFont("manrope", "Manrope", "Sans serif"),
        googleFont("dm_sans", "DM Sans", "Sans serif"),
        googleFont("space_grotesk", "Space Grotesk", "Display sans"),
        googleFont("josefin_sans", "Josefin Sans", "Display sans"),
        googleFont("bebas_neue", "Bebas Neue", "Display sans"),
        googleFont("lobster", "Lobster", "Display"),
        googleFont("pacifico", "Pacifico", "Handwriting"),
        googleFont("caveat", "Caveat", "Handwriting"),
        googleFont("dancing_script", "Dancing Script", "Handwriting"),
        googleFont("jetbrains_mono", "JetBrains Mono", "Monospace"),
        googleFont("fira_code", "Fira Code", "Monospace"),
        googleFont("ibm_plex_mono", "IBM Plex Mono", "Monospace"),
        googleFont("barlow", "Barlow", "Sans serif"),
        googleFont("mulish", "Mulish", "Sans serif"),
        googleFont("outfit", "Outfit", "Display sans"),
        googleFont("figtree", "Figtree", "Sans serif"),
        googleFont("sora", "Sora", "Display sans"),
        googleFont("urbanist", "Urbanist", "Display sans"),
        googleFont("plus_jakarta_sans", "Plus Jakarta Sans", "Sans serif"),
        googleFont("noto_sans", "Noto Sans", "Sans serif"),
        googleFont("noto_serif", "Noto Serif", "Serif"),
        googleFont("pt_sans", "PT Sans", "Sans serif"),
        googleFont("pt_serif", "PT Serif", "Serif"),
        googleFont("crimson_text", "Crimson Text", "Serif"),
        googleFont("libre_baskerville", "Libre Baskerville", "Serif"),
        googleFont("eb_garamond", "EB Garamond", "Serif"),
        googleFont("lora", "Lora", "Serif"),
        googleFont("karla", "Karla", "Sans serif"),
        googleFont("cabin", "Cabin", "Sans serif"),
        googleFont("heebo", "Heebo", "Sans serif"),
        googleFont("assistant", "Assistant", "Sans serif"),
        googleFont("exo_2", "Exo 2", "Display sans"),
        googleFont("teko", "Teko", "Condensed sans"),
        googleFont("righteous", "Righteous", "Display"),
        googleFont("comfortaa", "Comfortaa", "Rounded sans"),
        googleFont("fredoka", "Fredoka", "Rounded sans"),
        googleFont("bricolage_grotesque", "Bricolage Grotesque", "Display sans"),
        googleFont("instrument_sans", "Instrument Sans", "Sans serif"),
        googleFont("geist", "Geist", "Sans serif")
    )

    /**
     * Families whose name, category or spelling contains [query].
     *
     * Blank shows [quickPicks]. A query with no local match is still offered as
     * an exact family, because Google's CSS endpoint knows far more families than
     * this index does and the download path is the same for both.
     */
    fun search(query: String, limit: Int = 8): List<DownloadableFont> {
        val trimmed = query.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return quickPicks

        val needle = trimmed.lowercase(Locale.ROOT)
        val local = popular
            .filter { font ->
                font.displayName.contains(needle, ignoreCase = true) ||
                    font.category.contains(needle, ignoreCase = true)
            }
            .sortedWith(
                compareBy(
                    // A family whose name starts with the query is the likeliest
                    // selection. After that, a category that starts with it beats
                    // one that merely contains it: "serif" should surface Serif
                    // families before every "Sans serif" description.
                    { !it.displayName.startsWith(needle, ignoreCase = true) },
                    { !it.category.startsWith(needle, ignoreCase = true) },
                    { it.displayName.lowercase(Locale.ROOT) }
                )
            )
        if (local.isNotEmpty()) return local.take(limit)

        val family = trimmed.take(MAX_FAMILY_NAME_LENGTH)
        return listOf(
            DownloadableFont(
                id = familySlug(family),
                displayName = family,
                familyName = family,
                description = "Exact Google Fonts family"
            )
        )
    }

    /**
     * The index entry for [id], or null when this short list does not know it.
     *
     * A family downloaded by an exact-name search is deliberately outside the
     * index, so this is a lookup for the name and description only - the downloaded
     * files are the record of what exists, never this.
     */
    fun byId(id: String): DownloadableFont? =
        popular.firstOrNull { it.id == id } ?: quickPicks.firstOrNull { it.id == id }

    private fun googleFont(id: String, family: String, category: String) =
        DownloadableFont(
            id = id,
            displayName = family,
            familyName = family,
            description = "$category family from Google Fonts",
            category = category
        )

    private fun familySlug(family: String): String =
        family.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "font" }

    private const val MAX_FAMILY_NAME_LENGTH = 64
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
                val finished = File(dir, "google_${font.id}.done")

                // A family that has already been downloaded is served from disk.
                //
                // The marker is what makes that true for a family Google publishes
                // no 700 cut for, which is common among display and handwriting
                // faces: the old check required a usable bold file, so those
                // families looked half-downloaded forever and every tap fetched the
                // CSS and the regular file again.
                val storedBold = bold.takeIf { isUsable(it) }?.absolutePath
                if (isUsable(regular) && (storedBold != null || finished.isFile)) {
                    recordFamily(dir, font)
                    return@withContext Result.success(
                        ImportedFont(
                            path = regular.absolutePath,
                            name = font.displayName,
                            boldPath = storedBold
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

                recordFamily(dir, font)
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
        // Encode the family as one query component: a search can name any family
        // Google publishes, including one with a space, ampersand or apostrophe.
        val family = URLEncoder.encode(font.familyName, "UTF-8").replace("+", "%20")
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

    /**
     * Writes the two sidecars that describe a finished download.
     *
     * A name file rather than a stored preference: the fonts directory is already
     * the record of what exists, and a name written beside its files cannot
     * outlive them, disagree with them, or survive a preference clear that leaves
     * the bytes in place. `.done` records that a completed download simply had no
     * bold cut, which is a different fact from "the bold file failed" and is why
     * such a family is not fetched again.
     *
     * Best effort by design: a family that downloaded successfully must still be
     * applied if these two tiny writes fail. The consequences are a fallback name
     * and one unnecessary re-download, not a broken font.
     */
    private fun recordFamily(dir: File, font: DownloadableFont) {
        runCatching {
            File(dir, "google_${font.id}.name").writeText(font.displayName)
            File(dir, "google_${font.id}.done").createNewFile()
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

    companion object {
        /**
         * The process-wide downloader.
         *
         * The Font tab used to build one inside `remember { }`, which allocates an
         * `OkHttpClient` - a dispatcher, a connection pool and their threads - each
         * time the reader opens Type & reading. One client also keeps the
         * connections to fonts.gstatic.com warm between downloads.
         */
        val shared: GoogleFontDownloader by lazy { GoogleFontDownloader() }

        /** A CSS response is a handful of blocks; anything near this is not one. */
        private const val MAX_CSS_BYTES = 256 * 1024

        /** One static face is normally 150-650 KB. */
        private const val MAX_FONT_BYTES = 8L * 1024 * 1024
    }
}
