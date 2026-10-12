package com.example

import com.example.data.model.MediaListEntryItem
import com.example.data.model.MediaNode
import com.example.data.model.MediaTitle
import com.example.ui.screens.profile.LibraryFilter
import com.example.ui.screens.profile.LibraryType
import com.example.ui.screens.profile.filterLibrary
import com.example.ui.screens.profile.libraryBucketOf
import com.example.ui.screens.profile.libraryCounts
import com.example.ui.screens.profile.libraryProgressLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the Library tab's logic.
 *
 * These were wrong in ways that produced a plausible-looking screen rather than an
 * obvious failure, which is why they are pinned here rather than left to be
 * discovered by looking at a phone.
 *
 *  - [filterLibrary] decides what a chip means. Comparing the *entry's* status but
 *    never the *media's* `type` is the bug that mattered: "Manga" showed anime, and
 *    it only looked right because the first page of most lists is anime.
 *  - [libraryCounts] decides what the number on a chip claims. A count computed
 *    over the already-narrowed set makes "Completed (0)" appear for a bucket the
 *    person had merely filtered away, which reads as a fact about their account.
 *  - [libraryProgressLabel] decides how a number is worded. A manga badge reading
 *    "Ep 142" is the inconsistency a person who reads both notices at once.
 */
class LibraryFilterTest {

    @Test
    fun `the manga filter returns manga and no anime`() {
        val entries = listOf(
            entry(id = 1, type = "ANIME", status = "current"),
            entry(id = 2, type = "MANGA", status = "current")
        )

        assertEquals(
            listOf(2),
            filterLibrary(entries, LibraryType.MANGA, LibraryFilter.ALL).map { it.media.id }
        )
    }

    @Test
    fun `the anime filter returns anime and no manga`() {
        val entries = listOf(
            entry(id = 1, type = "ANIME"),
            entry(id = 2, type = "MANGA")
        )

        assertEquals(
            listOf(1),
            filterLibrary(entries, LibraryType.ANIME, LibraryFilter.ALL).map { it.media.id }
        )
    }

    @Test
    fun `a status filter narrows to that status in either case`() {
        // AniList sends the enum name; a lower-case fixture has been seen, and a
        // filter that misses it shows an empty list for somebody who has entries.
        val entries = listOf(
            entry(id = 1, type = "ANIME", status = "CURRENT"),
            entry(id = 2, type = "ANIME", status = "completed")
        )

        assertEquals(
            listOf(1),
            filterLibrary(entries, LibraryType.ANIME, LibraryFilter.WATCHING).map { it.media.id }
        )
    }

    @Test
    fun `dropped and rewatching are reachable rather than only under all`() {
        // Both exist on AniList and both are common. A filter row without them
        // leaves those entries reachable only by scrolling all 271.
        val entries = listOf(
            entry(id = 1, type = "ANIME", status = "dropped"),
            entry(id = 2, type = "ANIME", status = "repeating")
        )

        assertEquals(
            listOf(1),
            filterLibrary(entries, LibraryType.ALL, LibraryFilter.DROPPED).map { it.media.id }
        )
        assertEquals(
            listOf(2),
            filterLibrary(entries, LibraryType.ALL, LibraryFilter.REWATCHING).map { it.media.id }
        )
    }

    @Test
    fun `favourites matches membership rather than a status`() {
        val entries = listOf(
            entry(id = 1, type = "ANIME", status = "completed"),
            entry(id = 2, type = "ANIME", status = "completed"),
            entry(id = 3, type = "MANGA", status = "current")
        )

        val result = filterLibrary(
            entries,
            LibraryType.ALL,
            LibraryFilter.FAVOURITES,
            favouriteIds = setOf(1, 3)
        )

        assertEquals(listOf(1, 3), result.map { it.media.id })
    }

    @Test
    fun `a search narrows by title without regard to case`() {
        val entries = listOf(
            entry(id = 1, title = "Cowboy Bebop"),
            entry(id = 2, title = "Trigun Stampede")
        )

        val result = filterLibrary(entries, LibraryType.ALL, LibraryFilter.ALL, query = "bebop")

        assertEquals(listOf(1), result.map { it.media.id })
    }

    @Test
    fun `a blank search does not hide everything`() {
        // The failure mode of a `contains` on a null title: every entry with no
        // title becomes a non-match and the list empties for a blank query.
        val entries = listOf(entry(id = 1, title = null), entry(id = 2, title = "Named"))

        val result = filterLibrary(entries, LibraryType.ALL, LibraryFilter.ALL, query = "   ")

        assertEquals(listOf(1, 2), result.map { it.media.id })
    }

    @Test
    fun `counts describe the whole scope not the narrowed view`() {
        val entries = listOf(
            entry(id = 1, status = "completed"),
            entry(id = 2, status = "completed"),
            entry(id = 3, status = "current")
        )

        val counts = libraryCounts(entries, favouriteIds = emptySet())

        assertEquals(3, counts[LibraryFilter.ALL])
        assertEquals(2, counts[LibraryFilter.COMPLETED])
        assertEquals(1, counts[LibraryFilter.WATCHING])
        // A bucket with nothing in it is absent rather than zero-on-the-chip, so
        // the filter row does not advertise a status this account has never used.
        assertNull(counts[LibraryFilter.DROPPED])
    }

    @Test
    fun `the favourites count is the number of favourites present in the scope`() {
        val entries = listOf(
            entry(id = 1, status = "completed"),
            entry(id = 2, status = "current")
        )

        val counts = libraryCounts(entries, favouriteIds = setOf(2, 99))

        // 99 is favourited but not in this list, so it must not be counted.
        assertEquals(1, counts[LibraryFilter.FAVOURITES])
    }

    @Test
    fun `an unknown status is filed under no bucket rather than under all`() {
        assertNull(libraryBucketOf(null))
        assertNull(libraryBucketOf("REPEATING_FORTH"))
    }

    @Test
    fun `manga progress reads as chapters and anime as episodes`() {
        assertEquals(
            "Ep 12/24",
            libraryProgressLabel(entry(id = 1, type = "ANIME", progress = 12, episodes = 24))
        )
        assertEquals(
            "Ch 142",
            libraryProgressLabel(entry(id = 2, type = "MANGA", progress = 142))
        )
    }

    @Test
    fun `no progress means no badge rather than a zero`() {
        assertNull(libraryProgressLabel(entry(id = 1, type = "ANIME")))
    }

    @Test
    fun `a zero total does not produce Ep 12-0`() {
        // AniList answers 0 for an ongoing show with no announced episode count,
        // and "Ep 12/0" is worse than no denominator at all.
        assertEquals(
            "Ep 12",
            libraryProgressLabel(entry(id = 1, type = "ANIME", progress = 12, episodes = 0))
        )
    }

    private fun entry(
        id: Int,
        type: String = "ANIME",
        status: String? = null,
        progress: Int? = null,
        episodes: Int? = null,
        title: String? = "Entry $id"
    ) = MediaListEntryItem(
        status = status,
        progress = progress,
        media = MediaNode(
            id = id,
            type = type,
            title = title?.let { MediaTitle(english = it) },
            episodes = episodes
        )
    )
}