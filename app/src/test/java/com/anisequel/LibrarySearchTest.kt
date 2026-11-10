package com.anisequel

import com.anisequel.data.model.FuzzyDate
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.matchesTitleQuery
import com.anisequel.domain.library.LibraryAdvancedFilters
import com.anisequel.domain.library.filterLibraryAdvanced
import com.anisequel.domain.library.libraryReleaseYears
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Library search and its advanced filters.
 *
 * Both were wrong in ways that read as a missing title rather than as a broken
 * control:
 *
 *  - the search compared only `displayTitle`, which prefers English, so a reader
 *    who knew the romaji of a show with an English name got an empty list;
 *  - a two-word query had to appear contiguously, so "dragon ball" missed
 *    "Dragon Ball Super: Beerus" as soon as anything sat between the words;
 *  - the advanced sheet's minimum score used to treat an unrated title as a zero,
 *    which put it at the bottom instead of dropping it - and a filter that keeps
 *    everything looks like it did nothing.
 */
class LibrarySearchTest {

    @Test
    fun `the romaji spelling finds a title displayed under its English name`() {
        val entry = entry(
            id = 1,
            romaji = "Sousou no Frieren",
            english = "Frieren: Beyond Journey's End"
        )

        assertTrue("the romaji was not searched", entry.media.matchesTitleQuery("sousou"))
        assertTrue("the English name was not searched", entry.media.matchesTitleQuery("frieren"))
    }

    @Test
    fun `a single word matches anywhere in the title`() {
        val entry = entry(id = 1, english = "Dragon Ball Super: Beerus")

        assertTrue("a word inside the title did not match", entry.media.matchesTitleQuery("super"))
        assertFalse("an unrelated word matched", entry.media.matchesTitleQuery("goku"))
    }

    @Test
    fun `every word of a multi-word query has to appear, in any order`() {
        val entry = entry(id = 1, english = "Dragon Ball Super: Beerus")

        assertTrue(entry.media.matchesTitleQuery("dragon ball"))
        assertTrue(entry.media.matchesTitleQuery("ball super"))
        assertFalse(entry.media.matchesTitleQuery("dragon z"))
    }

    @Test
    fun `synonyms and the native spelling are searchable too`() {
        val entry = MediaListEntryItem(
            media = MediaNode(
                id = 1,
                title = MediaTitle(english = "Attack on Titan", native = "進撃の巨人"),
                synonyms = listOf("Shingeki no Kyojin")
            )
        )

        assertTrue(entry.media.matchesTitleQuery("shingeki"))
        assertTrue(entry.media.matchesTitleQuery("巨人"))
    }

    @Test
    fun `a blank query keeps every title`() {
        val entry = entry(id = 1, english = "Anything")

        assertTrue(entry.media.matchesTitleQuery("   "))
    }

    @Test
    fun `a minimum score drops unrated titles rather than ranking them last`() {
        val entries = listOf(
            entry(id = 1, score = 88),
            entry(id = 2, score = null),
            entry(id = 3, score = 61)
        )

        val result = filterLibraryAdvanced(entries, LibraryAdvancedFilters(minScore = 70))

        assertEquals(listOf(1), result.map { it.media.id })
    }

    @Test
    fun `a format filter keeps only that format`() {
        val entries = listOf(
            entry(id = 1, format = "TV"),
            entry(id = 2, format = "MANGA"),
            entry(id = 3, format = "TV")
        )

        val result = filterLibraryAdvanced(entries, LibraryAdvancedFilters(formats = setOf("TV")))

        assertEquals(listOf(1, 3), result.map { it.media.id })
    }

    @Test
    fun `genres are an OR, not an AND`() {
        val entries = listOf(
            entry(id = 1, genres = listOf("Action")),
            entry(id = 2, genres = listOf("Comedy")),
            entry(id = 3, genres = listOf("Drama"))
        )

        val result = filterLibraryAdvanced(
            entries,
            LibraryAdvancedFilters(genres = setOf("Action", "Comedy"))
        )

        assertEquals(listOf(1, 2), result.map { it.media.id })
    }

    @Test
    fun `the release year filter reads season year before the start date`() {
        val entries = listOf(
            entry(id = 1, seasonYear = 2024),
            entry(id = 2, startYear = 2024),
            entry(id = 3, seasonYear = 2019)
        )

        val result = filterLibraryAdvanced(entries, LibraryAdvancedFilters(releaseYear = 2024))

        assertEquals(listOf(1, 2), result.map { it.media.id })
        assertEquals(listOf(2024, 2019), libraryReleaseYears(entries))
    }

    @Test
    fun `favourites only tests the namespaced key`() {
        val entries = listOf(
            entry(id = 1, type = "ANIME"),
            entry(id = 2, type = "MANGA")
        )

        val result = filterLibraryAdvanced(
            entries,
            LibraryAdvancedFilters(onlyFavourites = true),
            favouriteIds = setOf("ANIME_1")
        )

        assertEquals(listOf(1), result.map { it.media.id })
    }

    @Test
    fun `an inactive filter set returns the same list instance`() {
        val entries = listOf(entry(id = 1), entry(id = 2))

        val result = filterLibraryAdvanced(entries, LibraryAdvancedFilters())

        assertEquals(entries, result)
    }

    private fun entry(
        id: Int,
        type: String = "ANIME",
        english: String? = null,
        romaji: String? = null,
        format: String? = null,
        score: Int? = null,
        genres: List<String>? = null,
        seasonYear: Int? = null,
        startYear: Int? = null
    ) = MediaListEntryItem(
        media = MediaNode(
            id = id,
            type = type,
            title = MediaTitle(english = english, romaji = romaji),
            format = format,
            averageScore = score,
            genres = genres,
            seasonYear = seasonYear,
            startDate = startYear?.let { FuzzyDate(year = it) }
        )
    )
}
