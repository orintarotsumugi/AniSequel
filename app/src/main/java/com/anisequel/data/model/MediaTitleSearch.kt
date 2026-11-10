package com.anisequel.data.model

import java.util.Locale

/**
 * Title matching for the Library search box.
 *
 * The list query carries three spellings of a title plus synonyms, and a reader
 * may know any one of them. The old search compared only [MediaTitle.displayTitle],
 * which prefers English - so somebody typing the romaji of a show with an English
 * title got an empty list from their own library, and it looked like the title was
 * missing rather than like the wrong field had been searched.
 *
 * Every query word must appear somewhere in one of the titles. That is the part
 * that makes "super" find "Dragon Ball Super" and "dragon ball" find it too,
 * without turning a two-word query into an OR that returns every title carrying
 * either word.
 */
fun MediaNode.matchesTitleQuery(query: String): Boolean {
    val words = query.trim()
        .lowercase(Locale.ROOT)
        .split(WHITESPACE)
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return true

    val titles = titleSpellings()
    if (titles.isEmpty()) return false

    return words.all { word -> titles.any { title -> title.contains(word) } }
}

/**
 * Every spelling a title search may reasonably match.
 *
 * `displayTitle` is included because it is the one spelling the card actually
 * shows, and a search that rejects the words on screen is indefensible even when
 * the API sent them under a different field.
 */
private fun MediaNode.titleSpellings(): List<String> {
    val spellings = buildList {
        title?.displayTitle?.takeIf { it.isNotBlank() }?.let(::add)
        title?.english?.takeIf { it.isNotBlank() }?.let(::add)
        title?.romaji?.takeIf { it.isNotBlank() }?.let(::add)
        title?.native?.takeIf { it.isNotBlank() }?.let(::add)
        title?.userPreferred?.takeIf { it.isNotBlank() }?.let(::add)
        synonyms.orEmpty().filterTo(this) { it.isNotBlank() }
    }
    return spellings.map { it.lowercase(Locale.ROOT) }.distinct()
}

private val WHITESPACE = Regex("\\s+")
