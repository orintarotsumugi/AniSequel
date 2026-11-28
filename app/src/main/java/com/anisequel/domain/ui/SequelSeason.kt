package com.anisequel.domain.ui

import java.util.Locale

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
 * Every spelling a title actually uses, in the order they are tried: `Season 2`,
 * `2nd Season`, `2nd Cour`, `Season III`, `Season Two`, `Part 2`, `S2` and a
 * trailing roman numeral (`Mushoku Tensei II`, `Overlord IV`), plus `Final Season`.
 *
 * Being conservative still matters more than being complete - a false positive
 * silently swaps a reader's chosen poster for their fallback - which is why the
 * loose patterns below are shaped the way they are. A bare trailing *digit* is not
 * accepted at all: it is how a title names itself rather than its season
 * ("86", "Blade Runner 2049", "Steins;Gate 0"), and one of those read as a season
 * would hide btttr art from a show that is not a sequel. The roman numerals are
 * safe because a title ending in `II`...`X` after a space is a continuation by
 * convention, and `S2` is only matched as its own token.
 */
object SequelSeason {

    /** A season name that is a word rather than a digit, e.g. `Season III`. */
    private val WORD_SEASON = Regex(
        """(?i)\bseason\s+(I{1,3}|IV|V|VI{0,3}|IX|X|\d{1,2})\b"""
    )

    /**
     * `2nd Season`, the spelling AniList's romaji titles use most often, and
     * `2nd Cour`, which the same titles use for a split run.
     */
    private val ORDINAL_SEASON = Regex(
        """(?i)\b(\d{1,2})(?:st|nd|rd|th)\s+(?:season|cour)\b"""
    )

    /** A split cour: `Part 2` / `Cour 2`. */
    private val PART = Regex("""(?i)\b(?:part|cour)\s+(\d{1,2})\b""")

    /** `Season Two` - the season spelled out, which English titles do use. */
    private val WORD_NUMBER_SEASON = Regex(
        """(?i)\bseason\s+(one|two|three|four|five|six|seven|eight|nine|ten)\b"""
    )

    /** `S2` / `S02`, the abbreviation the rest of the internet uses. */
    private val SHORT_SEASON = Regex("""(?i)(?<![A-Za-z])s(\d{1,2})\b""")

    /**
     * A trailing roman numeral: `Mushoku Tensei II`, `Overlord IV`, `Kaguya-sama
     * wa Kokurasetai: Ultra Romantic`'s successor spellings.
     *
     * Only after a space, only II-X (I is season one, and `I` alone is a pronoun in
     * half the English titles in existence), and only at the end of the title or
     * before its subtitle punctuation.
     *
     * The space is load-bearing rather than tidiness: without it a title that *is* a
     * roman numeral - CLAMP's `X`, which is not season ten - matches itself as a
     * season suffix and loses its btttr art.
     */
    private val SUFFIX_ROMAN = Regex(
        """(?i)(?<=\s)(II|III|IV|V|VI|VII|VIII|IX|X)\b(?=\s*(?:$|[:：–—-]))"""
    )

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
        WORD_NUMBER_SEASON.find(text)?.let { match ->
            return wordNumber(match.groupValues[1])
        }
        PART.find(text)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }
        SHORT_SEASON.find(text)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }
        SUFFIX_ROMAN.find(text)?.let { match ->
            return romanOrNumber(match.groupValues[1])
        }
        return null
    }

    /** True when [title] names a season after the first one. */
    fun isSequel(title: String?): Boolean = (numberIn(title) ?: 0) >= 2

    // `Locale.ROOT` on both: the word and the numeral are matched against English
    // spellings, and the device's locale turns `IV` into `İV` on a Turkish phone -
    // which made every roman-numeral sequel ("Mushoku Tensei II", "Overlord IV")
    // answer null and silently keep the shared series poster this class exists to
    // avoid.
    private fun wordNumber(value: String): Int? = when (value.lowercase(Locale.ROOT)) {
        "one" -> 1
        "two" -> 2
        "three" -> 3
        "four" -> 4
        "five" -> 5
        "six" -> 6
        "seven" -> 7
        "eight" -> 8
        "nine" -> 9
        "ten" -> 10
        else -> null
    }

    private fun romanOrNumber(value: String): Int? = value.toIntOrNull() ?: when (value.uppercase(Locale.ROOT)) {
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
