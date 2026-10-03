package com.example

import com.example.data.model.MediaNode
import com.example.data.model.MediaRanking
import com.example.data.model.MediaTag
import com.example.data.model.MediaTitle
import com.example.data.model.MissedSequel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The derived values on [MissedSequel] are memoised.
 *
 * `SequelCard` reads `description`, `topTags`, `topRanking` and `airingSeason`
 * for every visible row, and it recomposes on every scroll frame. Recomputing
 * them meant re-running the synopsis regex and re-filtering/re-sorting/mapping
 * the tag list a few hundred times a second to produce the same value each time.
 *
 * These tests pin the two things that matter about the memoisation:
 *
 *  - It returns the *same instance*, not just an equal value. An implementation
 *    that recomputed and compared equal would still have paid the cost, so
 *    `assertSame` is the assertion that actually catches a regression here.
 *  - It does not change the value. A cache that returns stale or wrong data is
 *    worse than no cache.
 */
class MissedSequelCachingTest {

    private fun sequel(
        description: String? = null,
        tags: List<MediaTag>? = null,
        rankings: List<MediaRanking>? = null,
        season: String? = null,
        seasonYear: Int? = null
    ) = MissedSequel(
        parentId = 1,
        parentTitle = "Season 1",
        sequelMedia = MediaNode(
            id = 2,
            title = MediaTitle(english = "Season 2"),
            description = description,
            tags = tags,
            rankings = rankings,
            season = season,
            seasonYear = seasonYear
        )
    )

    @Test
    fun `the synopsis is computed once and the same list is handed back`() {
        val entry = sequel(description = "<p>Attack on Titan</p>   humanity fights back.")

        val first = entry.description
        val second = entry.description

        assertEquals("Attack on Titan humanity fights back.", first)
        assertSame("the synopsis must be memoised, not recomputed per read", first, second)
    }

    @Test
    fun `an absent synopsis is null rather than a recomputed empty string`() {
        val entry = sequel(description = null)

        assertNull(entry.description)
        assertSame(entry.description, entry.description)
    }

    @Test
    fun `the tag list is filtered sorted and cached`() {
        val entry = sequel(
            tags = listOf(
                MediaTag(id = 1, name = "Low", rank = 10),
                MediaTag(id = 2, name = "High", rank = 90),
                MediaTag(id = 3, name = "Spoiler", rank = 100, isMediaSpoiler = true),
                MediaTag(id = 4, name = null, rank = 80)
            )
        )

        val tags = entry.topTags

        assertEquals(listOf("High", "Low"), tags)
        assertSame("the tag list must not be rebuilt on every card recomposition", tags, entry.topTags)
    }

    @Test
    fun `the ranking string is cached`() {
        val entry = sequel(
            rankings = listOf(
                MediaRanking(id = 1, rank = null, context = "ignored"),
                MediaRanking(id = 2, rank = 7, context = "Highest Rated")
            )
        )

        assertEquals("#7 Highest Rated", entry.topRanking)
        assertSame(entry.topRanking, entry.topRanking)
    }

    @Test
    fun `an entry with no ranking reports none`() {
        assertNull(sequel(rankings = emptyList()).topRanking)
    }

    @Test
    fun `the airing season is cached and still formatted the same way`() {
        val entry = sequel(season = "WINTER", seasonYear = 2019)

        assertEquals("Winter 2019", entry.airingSeason)
        assertSame(entry.airingSeason, entry.airingSeason)
    }

    @Test
    fun `an entry with no season reports none`() {
        assertNull(sequel(season = null, seasonYear = 2019).airingSeason)
    }

    /**
     * A detail load produces a new instance via `copy`, so the caches are
     * recomputed against the new media rather than serving the old value. This
     * is the correctness half of the memoisation: `by lazy` must not survive a
     * change to the data it was derived from.
     */
    @Test
    fun `a detail load invalidates the cached synopsis`() {
        val entry = sequel(description = null)
        assertNull(entry.description)

        val withDetail = entry.withDetail(
            MediaNode(id = 2, description = "<p>Freshly loaded</p>")
        )

        assertEquals("Freshly loaded", withDetail.description)
    }
}
