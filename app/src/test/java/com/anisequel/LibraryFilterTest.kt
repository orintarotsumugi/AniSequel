package com.anisequel

import com.anisequel.data.model.FuzzyDate
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.namespaceKey
import com.anisequel.domain.list.ListStatuses
import com.anisequel.ui.screens.profile.LibraryFilter
import com.anisequel.ui.screens.profile.LibrarySort
import com.anisequel.ui.screens.profile.LibraryType
import com.anisequel.ui.screens.profile.filterLibrary
import com.anisequel.ui.screens.profile.libraryBucketOf
import com.anisequel.ui.screens.profile.libraryCounts
import com.anisequel.ui.screens.profile.libraryProgressLabel
import com.anisequel.ui.screens.profile.selectSort
import com.anisequel.ui.screens.profile.sortLibrary
import com.anisequel.ui.screens.profile.toggleSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Library's filtering logic.
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
 *  - The favourites membership is a namespace *and* an id. Bare ids let `manga/2`
 *    answer to a heart on `anime/2`, and the merged grid that the chip filters then
 *    held two rows claiming one lazy key, which fails outright rather than looking
 *    slightly wrong.
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
            // The other namespace's claim on the same number. AniList numbers anime
            // and manga independently, so a heart on `ANIME_2` says nothing about this
            // row - and the favourites set used to be bare ids, which said otherwise
            // and pulled this manga under the chip for a show nobody favourited.
            entry(id = 2, type = "MANGA", status = "current"),
            entry(id = 3, type = "MANGA", status = "current")
        )

        val result = filterLibrary(
            entries,
            LibraryType.ALL,
            LibraryFilter.FAVOURITES,
            favouriteIds = setOf("ANIME_1", "ANIME_2", "MANGA_3")
        )

        assertEquals(listOf(1, 2, 3), result.map { it.media.id })
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

        val counts = libraryCounts(entries, favouriteIds = setOf("ANIME_2", "ANIME_99"))

        // 99 is favourited but not in this list, so it must not be counted.
        assertEquals(1, counts[LibraryFilter.FAVOURITES])
    }

    @Test
    fun `an unknown status is filed under no bucket rather than under all`() {
        assertNull(libraryBucketOf(null))
        assertNull(libraryBucketOf("REPEATING_FORTH"))
    }

    @Test
    fun `the same number in the other namespace is a different key`() {
        // The Library grid is `animeEntries + mangaEntries` under the ALL scope and
        // the favourites set spans both, so a key that is only a number is not an
        // identity: two rows would claim one lazy key, and a manga would answer to
        // its anime twin's heart.
        assertEquals("ANIME_21", entry(id = 21, type = "ANIME").media.namespaceKey)
        assertEquals("MANGA_21", entry(id = 21, type = "MANGA").media.namespaceKey)
        assertNotEquals(
            entry(id = 21, type = "ANIME").media.namespaceKey,
            entry(id = 21, type = "MANGA").media.namespaceKey
        )
    }

    @Test
    fun `a manga filter says reading and an anime filter says watching`() {
        // AniList records one `status` enum for both media types, so a shared
        // label means every manga row claims the reader is watching something.
        assertEquals("Reading", LibraryFilter.WATCHING.labelFor(isManga = true))
        assertEquals("Watching", LibraryFilter.WATCHING.labelFor(isManga = false))
        assertEquals("Rereading", LibraryFilter.REWATCHING.labelFor(isManga = true))
        assertEquals("Rewatching", LibraryFilter.REWATCHING.labelFor(isManga = false))
        // Buckets that mean the same thing either way are not reworded.
        assertEquals("Completed", LibraryFilter.COMPLETED.labelFor(isManga = true))
        assertEquals("Completed", LibraryFilter.COMPLETED.labelFor(isManga = false))
    }

    @Test
    fun `a chip words a status exactly the way the entry editor words it`() {
        // The chips used to carry their own spelling, and it drifted: the chip said
        // "Paused" and "Planning" while the editor the reader had just used said
        // "On-Hold" and "Plan to Watch". Both halves now come from ListStatuses, so
        // there is one vocabulary in the app and a status cannot be renamed in one
        // place without the other following.
        LibraryFilter.entries
            .filter { it.statusToken != null }
            .forEach { filter ->
                assertEquals(
                    ListStatuses.label(filter.statusToken, isManga = true),
                    filter.labelFor(isManga = true)
                )
                assertEquals(
                    ListStatuses.label(filter.statusToken, isManga = false),
                    filter.labelFor(isManga = false)
                )
            }
        assertEquals("On-Hold", LibraryFilter.PAUSED.labelFor(isManga = false))
        assertEquals("Plan to Watch", LibraryFilter.PLANNING.labelFor(isManga = false))
        assertEquals("Plan to Read", LibraryFilter.PLANNING.labelFor(isManga = true))
    }

    @Test
    fun `an entry whose status is an older spelling still reaches its chip`() {
        // API v1 named three statuses differently and the app used to compare the
        // raw wire string, so a cached or imported `ON_HOLD` matched no chip and
        // disappeared from every filter while still counting under "All".
        assertEquals(LibraryFilter.PAUSED, libraryBucketOf("ON_HOLD"))
        assertEquals(LibraryFilter.WATCHING, libraryBucketOf("WATCHING"))
        assertEquals(LibraryFilter.PLANNING, libraryBucketOf("PLAN_TO_READ"))
        assertEquals(LibraryFilter.DROPPED, libraryBucketOf("CLEARED"))
    }

    @Test
    fun `sorting by title is alphabetical and ignores case`() {
        val entries = listOf(
            entry(id = 1, title = "zeta"),
            entry(id = 2, title = "Alpha"),
            entry(id = 3, title = "beta")
        )

        val result = sortLibrary(entries, LibrarySort.TITLE, ascending = true)

        assertEquals(listOf(2, 3, 1), result.map { it.media.id })
    }

    @Test
    fun `an unrated title sorts last in both directions`() {
        // The failure this pins: null coerced to zero is a perfect score's
        // neighbour, so in a descending list every unrated title floats to the
        // top and the grid reads as a list of favourites.
        val entries = listOf(
            entry(id = 1, score = 50),
            entry(id = 2, score = null),
            entry(id = 3, score = 90)
        )

        // Both directions check `.last()`. An earlier version of this asserted
        // `.first()` while the message demanded it not be first - so the test
        // passed on the very behaviour it was written to catch, because it was
        // asserting the ordering was wrong rather than that it was right.
        assertEquals(
            "descending must not put an unrated title first",
            2,
            sortLibrary(entries, LibrarySort.SCORE, ascending = false).last().media.id
        )
        assertEquals(
            2,
            sortLibrary(entries, LibrarySort.SCORE, ascending = true).last().media.id
        )
        assertEquals(
            "descending still orders the rated titles among themselves",
            listOf(3, 1, 2),
            sortLibrary(entries, LibrarySort.SCORE, ascending = false).map { it.media.id }
        )
    }

    @Test
    fun `tapping the active sort toggles its direction`() {
        assertEquals(
            LibrarySort.TITLE to false,
            toggleSort(LibrarySort.TITLE, currentAscending = true)
        )
        assertEquals(
            LibrarySort.TITLE to true,
            toggleSort(LibrarySort.TITLE, currentAscending = false)
        )
    }

    @Test
    fun `recent is left in the order anilist returned it`() {
        // The list query carries no timestamp, so "Recent" cannot be computed -
        // it is the order AniList already gave. Re-sorting it would be inventing
        // a claim about dates the data does not support.
        val entries = listOf(entry(id = 9), entry(id = 3), entry(id = 7))

        assertEquals(
            listOf(9, 3, 7),
            sortLibrary(entries, LibrarySort.LIST_ORDER, ascending = true).map { it.media.id }
        )
    }

    @Test
    fun `list order is left exactly as anilist returned it`() {
        // AniList returns the library grouped by status - all Completed, then
        // all Watching, then all Planned - and the list query carries no sort
        // argument and no timestamp on an entry. So this is not recency, and the
        // old "Recent" label claimed it was. The order is preserved rather than
        // reversed, because a direction on a sort that has none is a lie.
        val entries = listOf(entry(id = 1), entry(id = 2), entry(id = 3))

        assertEquals(
            listOf(1, 2, 3),
            sortLibrary(entries, LibrarySort.LIST_ORDER, ascending = true).map { it.media.id }
        )
        assertEquals(
            listOf(1, 2, 3),
            sortLibrary(entries, LibrarySort.LIST_ORDER, ascending = false).map { it.media.id }
        )
    }

    @Test
    fun `tapping list order moves on instead of appearing to do nothing`() {
        // A control that visibly ignores a tap is one the reader learns to
        // distrust, and this one used to do exactly that.
        val order = toggleSort(LibrarySort.LIST_ORDER, currentAscending = true)

        assertEquals(LibrarySort.TITLE, order.first)
    }

    @Test
    fun `only sorts with a real direction claim one`() {
        assertFalse(LibrarySort.LIST_ORDER.isDirectional)
        assertTrue(LibrarySort.TITLE.isDirectional)
        assertTrue(LibrarySort.SCORE.isDirectional)
        assertTrue(LibrarySort.RELEASE_YEAR.isDirectional)
        assertTrue(LibrarySort.UPDATED_AT.isDirectional)
    }

    @Test
    fun `recently updated orders by the viewer's own last edit`() {
        // The recency sort "List order" never was: AniList's entry carries no
        // date, so this reads `updatedAt`, the moment the viewer last changed it.
        val entries = listOf(
            entry(id = 1, updatedAt = 1_700_000_000),
            entry(id = 2, updatedAt = 1_760_000_000),
            entry(id = 3, updatedAt = 1_730_000_000)
        )

        assertEquals(
            listOf(2, 3, 1),
            sortLibrary(entries, LibrarySort.UPDATED_AT, ascending = false).map { it.media.id }
        )
        assertEquals(
            listOf(1, 3, 2),
            sortLibrary(entries, LibrarySort.UPDATED_AT, ascending = true).map { it.media.id }
        )
    }

    @Test
    fun `a title with no update time sorts last when sorting by recency`() {
        val entries = listOf(
            entry(id = 1, updatedAt = 1_700_000_000),
            entry(id = 2, updatedAt = null)
        )

        assertEquals(
            2,
            sortLibrary(entries, LibrarySort.UPDATED_AT, ascending = false).last().media.id
        )
        assertEquals(
            2,
            sortLibrary(entries, LibrarySort.UPDATED_AT, ascending = true).last().media.id
        )
    }

    /**
     * The chip row's bug.
     *
     * Every chip sent its tap through `toggleSort` with the *current* sort, so
     * pressing "Score" while "List order" was active selected "A-Z": the chip the
     * reader pressed and the sort they got were different controls. Selecting a
     * chip now selects that chip, in its own opening direction.
     */
    @Test
    fun `selecting a sort opens it in its own direction rather than advancing`() {
        assertEquals(LibrarySort.SCORE to false, selectSort(LibrarySort.SCORE))
        assertEquals(LibrarySort.UPDATED_AT to false, selectSort(LibrarySort.UPDATED_AT))
        assertEquals(LibrarySort.TITLE to true, selectSort(LibrarySort.TITLE))
        assertEquals(LibrarySort.LIST_ORDER to true, selectSort(LibrarySort.LIST_ORDER))
    }

    @Test
    fun `release year sorts by season year, falling back to the start date`() {
        val entries = listOf(
            entry(id = 1, seasonYear = 2019),
            entry(id = 2, startYear = 2011),
            entry(id = 3, seasonYear = 2024)
        )

        val result = sortLibrary(entries, LibrarySort.RELEASE_YEAR, ascending = true)

        assertEquals(listOf(2, 1, 3), result.map { it.media.id })
    }

    @Test
    fun `an undated title sorts last in either direction`() {
        val entries = listOf(
            entry(id = 1, seasonYear = 2019),
            entry(id = 2, seasonYear = null, startYear = null)
        )

        assertEquals(
            2,
            sortLibrary(entries, LibrarySort.RELEASE_YEAR, ascending = false).last().media.id
        )
        assertEquals(
            2,
            sortLibrary(entries, LibrarySort.RELEASE_YEAR, ascending = true).last().media.id
        )
    }

    @Test
    fun `a completed entry with no progress still shows its total`() {
        // AniList does not fill `progress` from the episode count when a title is
        // marked finished, so `status` becomes "completed" while `progress` stays
        // null. The badge used to vanish at exactly that moment - when the person
        // had just finished watching it, and most wanted the number.
        val done = entry(id = 1, type = "ANIME", status = "completed", episodes = 24)

        assertEquals("Ep 24/24", libraryProgressLabel(done))
    }

    @Test
    fun `an unrated but completed entry with no episode count still has no badge`() {
        // Nothing is known, so there is nothing honest to draw. Inventing "Ep 0"
        // would be worse than no badge.
        val unknown = entry(id = 1, status = "completed", episodes = null)

        assertNull(libraryProgressLabel(unknown))
    }

    @Test
    fun `selecting score sorts highest first, not lowest`() {
        // Entering the list kept the previous direction, so the first thing shown
        // was the title the reader liked least.
        val order = toggleSort(LibrarySort.SCORE, currentAscending = true)

        assertEquals(LibrarySort.SCORE, order.first)
        assertFalse("score must open descending", order.second)
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

    /**
     * A rewatch sits at its finished progress while `status` says `REPEATING`, so
     * the plain total reads "24/24" - which says the reader is *done*, the exact
     * opposite of what `REPEATING` records. They are three episodes into a second
     * pass of a show they have already finished once.
     *
     * The percentage is the only number that stays true in that state.
     */
    @Test
    fun `a repeating entry shows a percentage rather than a finished total`() {
        assertEquals(
            "Ep 100%",
            libraryProgressLabel(
                entry(id = 1, type = "ANIME", progress = 24, episodes = 24, status = "REPEATING")
            )
        )
        assertEquals(
            "a manga rewatch reads as a percentage too, not a finished chapter total",
            "Ch 25%",
            libraryProgressLabel(
                entry(id = 2, type = "MANGA", progress = 50, chapters = 200, status = "REPEATING")
            )
        )
    }

    /**
     * `progress` can exceed the announced total on a show that gained episodes
     * after AniList recorded the entry, and the badge must not read past 100%.
     */
    @Test
    fun `a rewatch past the announced total does not exceed one hundred percent`() {
        assertEquals(
            "Ep 100%",
            libraryProgressLabel(
                entry(id = 1, type = "ANIME", progress = 26, episodes = 24, status = "REPEATING")
            )
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
        chapters: Int? = null,
        score: Int? = null,
        seasonYear: Int? = null,
        startYear: Int? = null,
        updatedAt: Int? = null,
        title: String? = "Entry $id"
    ) = MediaListEntryItem(
        status = status,
        progress = progress,
        updatedAt = updatedAt,
        media = MediaNode(
            id = id,
            type = type,
            title = title?.let { MediaTitle(english = it) },
            episodes = episodes,
            chapters = chapters,
            averageScore = score,
            seasonYear = seasonYear,
            startDate = startYear?.let { FuzzyDate(year = it) }
        )
    )
}