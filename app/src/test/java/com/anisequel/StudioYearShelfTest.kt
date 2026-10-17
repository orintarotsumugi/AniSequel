package com.anisequel

import com.anisequel.data.model.FuzzyDate
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.domain.studio.STUDIO_UNDATED_SHELF
import com.anisequel.domain.studio.studioYear
import com.anisequel.domain.studio.studioYearShelves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The studio page's shelves.
 *
 * This is the whole shape of that screen decided in pure Kotlin, so it is checked
 * here rather than by opening it: which title belongs to which year, what a title
 * with no date does, and what happens when the same media arrives twice - which it
 * does, because a studio's filmography is the one list in the app that is built by
 * appending pages fetched minutes apart while AniList re-orders its own results.
 */
class StudioYearShelfTest {

    @Test
    fun `titles are shelved by the year of their start date`() {
        val shelves = studioYearShelves(
            listOf(
                media(id = 1, startYear = 2024),
                media(id = 2, startYear = 2022)
            )
        )

        assertEquals(listOf("2024", "2022"), shelves.map { it.label })
        assertEquals(listOf(1), shelves[0].titles.map { it.id })
        assertEquals(listOf(2), shelves[1].titles.map { it.id })
    }

    /**
     * `seasonYear` is the fallback, and it has to be: AniList sets it on anything it
     * has scheduled, while an older title often arrives with a full start date and no
     * season at all. A shelf that read only one of the two would show a studio's
     * older work under `TBA` next to its newer work under a year.
     */
    @Test
    fun `the year falls back to seasonYear when the start date is missing`() {
        val shelves = studioYearShelves(listOf(media(id = 1, seasonYear = 2011)))

        assertEquals(listOf("2011"), shelves.map { it.label })
    }

    /**
     * Years run newest first, because the question a studio page answers is "what
     * have they done lately".
     */
    @Test
    fun `years descend`() {
        val shelves = studioYearShelves(
            listOf(
                media(id = 1, startYear = 2019),
                media(id = 2, startYear = 2026),
                media(id = 3, startYear = 2021)
            )
        )

        assertEquals(listOf("2026", "2021", "2019"), shelves.map { it.label })
    }

    /**
     * The undated shelf is first, not last.
     *
     * An announced title is the most interesting thing on a studio's page and it has
     * no number to sort by - so it goes at the top deliberately, rather than being
     * pushed to the bottom by a null.
     */
    @Test
    fun `announced titles sit on their own shelf above the years`() {
        val shelves = studioYearShelves(
            listOf(
                media(id = 1, startYear = 2025),
                media(id = 2)
            )
        )

        assertEquals(STUDIO_UNDATED_SHELF, shelves.first().label)
        assertNull(shelves.first().year)
        assertEquals(listOf(2), shelves.first().titles.map { it.id })
        assertEquals(listOf("2025"), shelves.drop(1).map { it.label })
    }

    /**
     * Within a shelf, the order AniList sent is kept.
     *
     * The query asks for `START_DATE_DESC`, so two titles in the same year are already
     * in the right order by month and day - fields this grouping does not carry. A
     * re-sort here would be by a number the code does not have.
     */
    @Test
    fun `order inside a year is left as AniList sent it`() {
        val shelves = studioYearShelves(
            listOf(
                media(id = 7, startYear = 2023),
                media(id = 4, startYear = 2023),
                media(id = 9, startYear = 2023)
            )
        )

        assertEquals(listOf(7, 4, 9), shelves.single().titles.map { it.id })
    }

    /**
     * A title that is somehow on no shelf at all is still on the page.
     *
     * The failure this guards is a `filterNot` written the wrong way round, which
     * loses an entry silently - the shelf labels would all look right.
     */
    @Test
    fun `every title appears exactly once`() {
        val titles = listOf(
            media(id = 1, startYear = 2024),
            media(id = 2),
            media(id = 3, seasonYear = 1999)
        )

        val shelved = studioYearShelves(titles).flatMap { it.titles }.map { it.id }

        assertEquals(setOf(1, 2, 3), shelved.toSet())
        assertEquals(titles.size, shelved.size)
    }

    /**
     * A duplicate - the same media id seen twice, across a page boundary - is shelved
     * once.
     */
    @Test
    fun `a title fetched twice is shelved once`() {
        val shelves = studioYearShelves(
            listOf(
                media(id = 5, startYear = 2020),
                media(id = 5, startYear = 2020),
                media(id = 6, startYear = 2020)
            )
        )

        assertEquals(listOf(5, 6), shelves.single().titles.map { it.id })
    }

    @Test
    fun `no titles is no shelves`() {
        assertTrue(studioYearShelves(emptyList()).isEmpty())
    }

    /**
     * The derivation itself, pinned: a title in the fall of 2024 but with a 2025
     * season year belongs to 2024, which is when it actually started.
     */
    @Test
    fun `the start date outranks the season year`() {
        assertEquals(2024, media(id = 1, startYear = 2024, seasonYear = 2025).studioYear)
    }

    private fun media(
        id: Int,
        startYear: Int? = null,
        seasonYear: Int? = null
    ): MediaNode = MediaNode(
        id = id,
        type = "ANIME",
        title = MediaTitle(romaji = "Title $id"),
        seasonYear = seasonYear,
        startDate = startYear?.let { FuzzyDate(year = it) }
    )
}
