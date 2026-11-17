package com.anisequel.domain.ui

/**
 * The season a title's own name declares, if it declares one.
 *
 * ## Why this exists
 *
 * btttr addresses a poster by **IMDb series** id, and every season of a show shares
 * that series entry. "Frieren", "Frieren Season 2" and "Frieren Season 3" therefore
 * resolve to the same IMDb id and the same picture - the service has no season
 * parameter to ask for anything else (verified against its configure page and by
 * requesting `?season=2`, which it ignores). The result is a grid where three
 * different list entries wear one poster.
 *
 * The app's answer is not to invent a season render it cannot get, but to stop
 * asking btttr for the seasons it cannot tell apart: a sequel keeps the cover
 * AniList sent for *that* entry, which is season-specific and usually carries the
 * season's own name in its artwork. [numberIn] is how the poster layer decides.
 *
 * ## What counts as a season
 *
 * Only spellings a title actually uses: `Season 2`, `2nd Season`, `Season III`,
 * `Part 2` and `Final Season`. Being conservative matters more than being complete:
 * a false positive silently swaps a reader's chosen poster for their fallback, so an
 * unclear title is treated as season one and keeps the btttr art.
 */
object SequelSeason {

    /** A season name that is a word rather than a digit, e.g. `Season III`. */
    private val WORD_SEASON = Regex(
        """(?i)\bseason\s+(I{1,3}|IV|V|VI{0,3}|IX|X|\d{1,2})\b"""
    )

    /** `2nd Season`, the spelling AniList's romaji titles use most often. */
    private val ORDINAL_SEASON = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)\s+season\b""")

    /** A split cour: `Part 2` / `Cour 2`. */
    private val PART = Regex("""(?i)\b(?:part|cour)\s+(\d{1,2})\b""")

    /** A named final season, which is a sequel whatever number it follows. */
    private val FINAL = Regex("""(?i)\bfinal\s+season\b""")

    /** Returned for "Final Season": a sequel no explicit number describes. */
    const val FINAL_SEASON = 99

    /** The season number in [title], or null when the title does not name one. */
    fun numberIn(title: String?): Int? {
        val text = title?.takeIf { it.isNotBlank() } ?: return null
        if (FINAL.containsMatchIn(text)) return FINAL_SEASON
        ORDINAL_SEASON.find(text)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }
        WORD_SEASON.find(text)?.let { match ->
            return romanOrNumber(match.groupValues[1])
        }
        PART.find(text)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }
        return null
    }

    /** True when [title] names a season after the first one. */
    fun isSequel(title: String?): Boolean = (numberIn(title) ?: 0) >= 2

    private fun romanOrNumber(value: String): Int? = value.toIntOrNull() ?: when (value.uppercase()) {
        "I" -> 1
        "II" -> 2
        "III" -> 3
        "IV" -> 4
        "V" -> 5
        "VI" -> 6
        "VII" -> 7
        "VIII" -> 8
        "IX" -> 9
        "X" -> 10
        else -> null
    }
}
