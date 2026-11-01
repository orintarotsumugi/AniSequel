package com.anisequel

import com.anisequel.data.model.ArrivingEntry
import com.anisequel.ui.screens.dashboard.arrivingRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two layouts of "Currently arriving", without a screen to draw them on.
 *
 * The combined row is the shipped default and the separate groups are a
 * setting, so the failure this pins is the setting changing the *data* rather
 * than the drawing: an entry that moved groups, disappeared, or appeared twice
 * would make the section disagree with the list it came from. Order is part of
 * the contract - airing before upcoming, and the use case's soonest-first order
 * kept inside each half.
 */
class ArrivingLayoutTest {

    private fun entry(id: Int, airing: Boolean) = ArrivingEntry(
        mediaId = id,
        title = "Show $id",
        coverUrl = null,
        coverColor = null,
        status = if (airing) "RELEASING" else "NOT_YET_RELEASED",
        startDate = null,
        nextAiringAt = null,
        nextEpisodeNumber = null,
        episodes = 12,
        progress = 0
    )

    private val airingOne = entry(1, airing = true)
    private val upcomingOne = entry(2, airing = false)
    private val airingTwo = entry(3, airing = true)
    private val upcomingTwo = entry(4, airing = false)

    @Test
    fun `nothing arriving draws no rows`() {
        assertTrue(arrivingRows(emptyList(), separateGroups = false).isEmpty())
        assertTrue(arrivingRows(emptyList(), separateGroups = true).isEmpty())
    }

    @Test
    fun `the combined layout is one unlabelled row, airing first`() {
        val rows = arrivingRows(
            listOf(upcomingOne, airingOne, upcomingTwo, airingTwo),
            separateGroups = false
        )

        assertEquals(1, rows.size)
        assertNull("a combined row does not need a heading", rows.single().label)
        assertTrue("the row contains airing entries", rows.single().airing)
        assertEquals(
            listOf(1, 3, 2, 4),
            rows.single().entries.map { it.mediaId }
        )
    }

    @Test
    fun `a combined row of only upcoming entries is not marked as airing`() {
        val row = arrivingRows(
            listOf(upcomingOne, upcomingTwo),
            separateGroups = false
        ).single()

        assertFalse(
            "the row's flag keys its colour and tag; only-upcoming must not claim " +
                "to be airing",
            row.airing
        )
        assertEquals(listOf(2, 4), row.entries.map { it.mediaId })
    }

    @Test
    fun `separating the groups draws two labelled rows in airing-first order`() {
        val rows = arrivingRows(
            listOf(upcomingOne, airingOne, upcomingTwo, airingTwo),
            separateGroups = true
        )

        assertEquals(2, rows.size)
        assertEquals("Airing now", rows[0].label)
        assertTrue(rows[0].airing)
        assertEquals(listOf(1, 3), rows[0].entries.map { it.mediaId })

        assertEquals("Upcoming", rows[1].label)
        assertFalse(rows[1].airing)
        assertEquals(listOf(2, 4), rows[1].entries.map { it.mediaId })
    }

    @Test
    fun `an empty group is dropped rather than drawn as an empty strip`() {
        val onlyAiring = arrivingRows(listOf(airingOne, airingTwo), separateGroups = true)
        assertEquals(1, onlyAiring.size)
        assertEquals("Airing now", onlyAiring.single().label)
        assertTrue(onlyAiring.single().entries.all { it.isAiring })

        val onlyUpcoming = arrivingRows(listOf(upcomingOne), separateGroups = true)
        assertEquals(1, onlyUpcoming.size)
        assertEquals("Upcoming", onlyUpcoming.single().label)
        assertFalse(onlyUpcoming.single().airing)
        assertTrue(onlyUpcoming.single().entries.none { it.isAiring })
    }

    @Test
    fun `no entry is lost or duplicated by either layout`() {
        val entries = listOf(upcomingOne, airingOne, upcomingTwo, airingTwo)

        val combined = arrivingRows(entries, separateGroups = false)
            .flatMap { it.entries }
            .map { it.mediaId }
        val separated = arrivingRows(entries, separateGroups = true)
            .flatMap { it.entries }
            .map { it.mediaId }

        assertEquals(entries.map { it.mediaId }.sorted(), combined.sorted())
        assertEquals(entries.map { it.mediaId }.sorted(), separated.sorted())
    }
}
