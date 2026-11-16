package com.anisequel.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * The reader's local font, or null when there is none or it can no longer load.
 *
 * The files live in the app's own storage - `CustomFontStore` copies imported
 * documents there and `GoogleFontDownloader` stores downloaded families there -
 * so this is an existence check, a decode and a lazy `Font` wrapper. A file that
 * disappeared (cleared data, a failed restore) falls back to the system face
 * rather than failing the theme: a font is a preference, not a dependency.
 *
 * [boldPath] is the downloaded family's 700 file. It is optional because a
 * document import is a single file; without it the platform synthesises bold from
 * the regular face. Keeping each weight as its own `Font` is what stops every
 * heading in the app from being a fake-bold copy of the body, which is visibly
 * heavier and narrower than the family's real bold cut.
 *
 * ## Why decode here as well as at import
 *
 * `Font(file)` is lazy, so a file that is not a font would fail at the first draw,
 * inside the theme, on every screen. The download and import paths already refuse
 * bad bytes, but a stored path can also come from an older build, a restored
 * backup or a hand-edited preference. Decoding once here turns all of those into
 * the system face instead of a crash, and the result is cached so the cost is paid
 * once per file rather than on every theme read.
 *
 * The cache is keyed on both paths *and* both file lengths. Downloads and imports
 * use fresh file names each time, so the path alone would usually be enough; the
 * lengths also cover a file replaced by something outside the app, where a path
 * stays the same but the bytes do not.
 *
 * ## The cache is read before the decode
 *
 * This used to validate both files - `Typeface.createFromFile` on each - and only
 * then look at the cache, so every rebuild of the theme decoded a font it already
 * had. Opening the app is one rebuild; rotating the device or recreating the
 * Activity is another, and both paid two decodes on the main thread before the
 * first frame. The key needs two `stat` calls, so a hit is now free of the only
 * expensive part. A family the platform cannot read is cached too, which stops a
 * broken stored path from being re-decoded on every theme build.
 */
internal fun customFontFamily(path: String?, boldPath: String? = null): FontFamily? {
    if (path.isNullOrBlank()) return null
    val regularFile = File(path).takeIf { it.isFile } ?: return null
    val boldFile = boldPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    val regularBytes = regularFile.length()
    val boldBytes = boldFile?.length() ?: 0L

    cachedFont?.let { cached ->
        if (
            cached.path == path &&
            cached.bytes == regularBytes &&
            cached.boldPath == boldPath &&
            cached.boldBytes == boldBytes
        ) {
            return cached.family
        }
    }

    val family = runCatching {
        // The same decoder the platform will use, for both cuts: a file that is
        // not a font must be refused here rather than at the first draw, on every
        // screen.
        Typeface.createFromFile(regularFile)
        boldFile?.let { Typeface.createFromFile(it) }
        val fonts = buildList {
            add(Font(regularFile))
            boldFile?.let { add(Font(it, weight = FontWeight.Bold)) }
        }
        FontFamily(*fonts.toTypedArray())
    }.getOrNull()

    cachedFont = CachedFont(
        path = path,
        bytes = regularBytes,
        boldPath = boldPath,
        boldBytes = boldBytes,
        family = family
    )
    return family
}

private data class CachedFont(
    val path: String?,
    val bytes: Long,
    val boldPath: String?,
    val boldBytes: Long,
    val family: FontFamily?
)

/**
 * One entry is enough: there is one local font at a time, and the theme asks for
 * the same paths on every recomposition. Volatile because a caller is allowed to
 * resolve this off the main thread; the worst a race can do is decode twice.
 */
@Volatile
private var cachedFont: CachedFont? = null
