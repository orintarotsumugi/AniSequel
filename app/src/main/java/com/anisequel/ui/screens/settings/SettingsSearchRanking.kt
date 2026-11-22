package com.anisequel.ui.screens.settings

import java.text.Normalizer
import java.util.Locale

/**
 * How a Settings search query is matched and ranked.
 *
 * ## Why this is not part of the index any more
 *
 * The index file was doing two jobs: it holds the *data* - a hand-curated result
 * per setting, plus the keyword map that lets "dpi" find "Display size" - and it
 * held the *engine* that scores a query against that data. They change for
 * different reasons. Adding a setting edits the data; changing how a typo is
 * forgiven edits the engine; the two never needed to change together.
 *
 * Splitting them is also what let the data grow by four pages' worth of sections
 * without pushing one file further past the size the project allows it. The
 * functions below are pure - no Compose, no DataStore - which is what lets
 * `SettingsSearchTest` pin their answers without a harness.
 */

internal fun score(
    result: SettingsSearchResult,
    tokens: List<String>,
    normalizedQuery: String
): Int {
    val title = normalize(result.title)
    val subtitle = normalize(result.subtitle)
    val keywords = normalize(result.keywords)
    val haystack = "$title $subtitle $keywords"
    val compactHaystack = haystack.filter { it.isLetterOrDigit() }
    if (tokens.any { !matchesToken(it, haystack, compactHaystack) }) return 0

    var score = result.priority
    val titleWords = title.split(' ').filter { it.isNotEmpty() }
    val compactTitle = title.filter { it.isLetterOrDigit() }
    val compactQuery = normalizedQuery.filter { it.isLetterOrDigit() }
    if (title == normalizedQuery || compactTitle == compactQuery) {
        score += 1600
    } else if (
        title.startsWith(normalizedQuery) ||
        (compactQuery.isNotEmpty() && compactTitle.startsWith(compactQuery))
    ) {
        score += 1200
    } else if (titleWords.any { word -> word == tokens.first() }) {
        score += 900
    }
    if (title.contains(normalizedQuery)) score += 600
    if (subtitle.contains(normalizedQuery)) score += 320
    if (keywords.contains(normalizedQuery)) score += 240
    tokens.forEach { token ->
        if (title.contains(token)) score += 80
        if (subtitle.contains(token)) score += 30
        if (keywords.contains(token)) score += 20
    }
    return score
}

internal fun fuzzyScore(result: SettingsSearchResult, tokens: List<String>): Int {
    val title = normalize(result.title)
    val subtitle = normalize(result.subtitle)
    val keywords = normalize(result.keywords)
    val haystack = "$title $subtitle $keywords"
    val compactHaystack = haystack.filter { it.isLetterOrDigit() }
    val words = haystack.split(' ').filter { it.isNotEmpty() }
    var mistakes = 0
    tokens.forEach { token ->
        if (matchesToken(token, haystack, compactHaystack)) return@forEach
        if (token.length < 4) return 0
        val closest = words.minOfOrNull { word -> distance(token, word) } ?: return 0
        val allowance = if (token.length >= 7) 2 else 1
        if (closest > allowance) return 0
        mistakes += closest
    }
    return 100 - mistakes * 10 + result.priority
}

internal fun matchesToken(
    token: String,
    haystack: String,
    compactHaystack: String
): Boolean {
    if (haystack.split(' ').any { word -> word.startsWith(token) }) return true
    // The compact pass is what lets "fullscreen" find "Full-screen"; it is
    // restricted to tokens of four or more characters so short strings do not
    // match across the inside of unrelated words.
    return token.length >= 4 && compactHaystack.contains(token)
}

internal fun tokens(query: String): List<String> =
    normalize(query).split(' ').filter { it.isNotEmpty() }

internal fun normalize(value: String): String {
    val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
    return decomposed
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_WORD, " ")
        .trim()
}

internal fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
    if (ranges.isEmpty()) return emptyList()
    val sorted = ranges.sortedBy { it.first }
    val merged = mutableListOf(sorted.first())
    sorted.drop(1).forEach { range ->
        val previous = merged.last()
        if (range.first <= previous.last + 1) {
            merged[merged.lastIndex] = previous.first..maxOf(previous.last, range.last)
        } else {
            merged += range
        }
    }
    return merged
}

/** Bounded Levenshtein; only ever called on one word at a time. */
internal fun distance(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    var current = IntArray(right.length + 1)
    left.forEachIndexed { leftIndex, leftCharacter ->
        current[0] = leftIndex + 1
        right.forEachIndexed { rightIndex, rightCharacter ->
            val substitution = previous[rightIndex] +
                if (leftCharacter == rightCharacter) 0 else 1
            current[rightIndex + 1] = minOf(
                current[rightIndex] + 1,
                previous[rightIndex + 1] + 1,
                substitution
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.length]
}


internal data class Scored(val result: SettingsSearchResult, val score: Int)

private val COMBINING_MARKS = Regex("\\p{M}+")
private val NON_WORD = Regex("[^a-z0-9]+")

