package com.anisequel.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * The reader's imported font, or null when there is none or it can no longer load.
 *
 * The file lives in the app's own storage - `CustomFontStore` copies it there on
 * import - so this is an existence check, a decode and a lazy `Font` wrapper. A
 * file that disappeared (cleared data, a failed restore) falls back to the system
 * face rather than failing the theme: a font is a preference, not a dependency.
 *
 * ## Why decode here as well as at import
 *
 * `Font(file)` is lazy, so a file that is not a font would fail at the first draw,
 * inside the theme, on every screen. The import path already refuses bad bytes,
 * but the stored path can also come from an older build, a restored backup or a
 * hand-edited preference. Decoding once here turns all of those into the system
 * face instead of a crash, and the result is cached so the cost is paid once per
 * file rather than on every theme read.
 *
 * The cache is keyed on the path *and* the file's length. Imports now use a fresh
 * file name each time (see `CustomFontStore`), so the path alone would be enough;
 * the length is there for a file replaced by something outside the app, where the
 * path stays the same but the bytes do not.
 */
internal fun customFontFamily(path: String?): FontFamily? {
    if (path.isNullOrBlank()) return null
    val file = File(path)
    if (!file.isFile) return null

    val bytes = file.length()
    cachedFont?.let { cached ->
        if (cached.path == path && cached.bytes == bytes) return cached.family
    }

    val family = runCatching {
        // The same decoder the platform will use for `Font(file)`; throws for
        // anything that is not a usable font.
        Typeface.createFromFile(file)
        FontFamily(Font(file))
    }.getOrNull()

    cachedFont = CachedFont(path = path, bytes = bytes, family = family)
    return family
}

private data class CachedFont(val path: String, val bytes: Long, val family: FontFamily?)

/**
 * One entry is enough: there is one imported font at a time, and the theme asks
 * for the same path on every recomposition. Volatile because a caller is allowed
 * to resolve this off the main thread; the worst a race can do is decode twice.
 */
@Volatile
private var cachedFont: CachedFont? = null
