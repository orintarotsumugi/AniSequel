package com.anisequel.domain.library

import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.namespaceKey
import java.util.Locale

/**
 * The Library sheet's filters, beside the status pill row.
 *
 * Status stays on the pills because it is the one axis people switch constantly;
 * these are the narrower questions - format, year, score, genre, favourites - and
 * they live behind the filter button so the page does not grow a second row of
 * controls for a search performed once a month.
 *
 * Kept pure and outside the composable so the rules can be pinned. A filter that
 * silently drops every unrated title, or treats a manga's missing episode count as
 * zero, produces a plausible empty screen rather than a crash.
 */
data class LibraryAdvancedFilters(
    val formats: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val minScore: Int? = null,
    val releaseYear: Int? = null,
    val onlyFavourites: Boolean = false
) {
    /** Whether anything here is actually narrowing the list. */
    val isActive: Boolean
        get() = formats.isNotEmpty() ||
            genres.isNotEmpty() ||
            minScore != null ||
            releaseYear != null ||
            onlyFavourites

    /** How many sections are narrowing, for the filter button's count. */
    val activeCount: Int
        get() = listOf(
            formats.isNotEmpty(),
            genres.isNotEmpty(),
            minScore != null,
            releaseYear != null,
            onlyFavourites
        ).count { it }
}

/**
 * Applies [filters] to one entry.
 *
 * Genres use OR, the same rule the sequel sheet documents: picking Action and
 * Comedy asks for either, and requiring both would make two selections almost
 * always empty. Format and genre are comparisons on AniList's raw enum/string
 * values, normalised because cached lists have arrived with mixed case before.
 */
fun MediaListEntryItem.matchesAdvancedFilters(
    filters: LibraryAdvancedFilters,
    favouriteIds: Set<String> = emptySet()
): Boolean {
    val node = media

    if (filters.formats.isNotEmpty()) {
        val format = node.format?.uppercase(Locale.ROOT) ?: return false
        if (format !in filters.formats) return false
    }

    if (filters.genres.isNotEmpty()) {
        val genres = node.genres.orEmpty().map { it.lowercase(Locale.ROOT) }.toSet()
        if (filters.genres.none { it.lowercase(Locale.ROOT) in genres }) return false
    }

    if (filters.minScore != null) {
        val score = node.averageScore ?: return false
        if (score < filters.minScore) return false
    }

    if (filters.releaseYear != null) {
        val year = node.seasonYear ?: node.startDate?.year ?: return false
        if (year != filters.releaseYear) return false
    }

    if (filters.onlyFavourites && node.namespaceKey !in favouriteIds) return false

    return true
}

/** The subset of [entries] the advanced sheet keeps. */
fun filterLibraryAdvanced(
    entries: List<MediaListEntryItem>,
    filters: LibraryAdvancedFilters,
    favouriteIds: Set<String> = emptySet()
): List<MediaListEntryItem> {
    if (!filters.isActive) return entries
    return entries.filter { entry -> entry.matchesAdvancedFilters(filters, favouriteIds) }
}

/** The years present in a scope, newest first, for the year picker. */
fun libraryReleaseYears(entries: List<MediaListEntryItem>): List<Int> =
    entries.mapNotNull { entry -> entry.media.seasonYear ?: entry.media.startDate?.year }
        .filter { it > 0 }
        .distinct()
        .sortedDescending()

/**
 * Every format the Library can hold, paired with its display word.
 *
 * Separate from the sequel sheet's list because a library also contains manga
 * formats, and because "All" is not a format - it is the absence of a choice, and
 * the sheet renders that as a Reset instead of a chip that would silently widen
 * the selection.
 */
val libraryFormatOptions: List<Pair<String, String>> = listOf(
    "TV" to "TV",
    "TV_SHORT" to "TV Short",
    "MOVIE" to "Movie",
    "OVA" to "OVA",
    "ONA" to "ONA",
    "SPECIAL" to "Special",
    "MANGA" to "Manga",
    "NOVEL" to "Novel",
    "ONE_SHOT" to "One Shot",
    "MUSIC" to "Music"
)
