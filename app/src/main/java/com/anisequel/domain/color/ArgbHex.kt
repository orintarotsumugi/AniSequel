package com.anisequel.domain.color

/**
 * Hex ARGB strings, the one format the theme preferences store.
 *
 * Parsing lives here rather than in the UI builder because more than one layer
 * now writes or reads it: the detail sheet stores an extracted seed, the theme
 * builder parses a preference, and the cover-colour store round-trips the same
 * string. Two hand-rolled parsers is how a preference written by one half stops
 * being readable by the other.
 */
object ArgbHex {

    /** `RRGGBB` or `AARRGGBB`, with or without a leading `#`. Null when invalid. */
    fun parse(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6 && clean.length != 8) return null
        if (!clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return clean.toLongOrNull(16)?.let { value ->
            if (clean.length == 6) (0xFF000000L or value).toInt() else value.toInt()
        }
    }

    /** Always six digits and a leading `#`; alpha is not part of a theme seed. */
    fun format(argb: Int): String =
        String.format(java.util.Locale.ROOT, "#%06X", argb and 0xFFFFFF)
}
