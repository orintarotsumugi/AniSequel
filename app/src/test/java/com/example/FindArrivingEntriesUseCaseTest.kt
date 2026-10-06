package com.example

import com.example.data.model.ArrivingEntry
import com.example.data.model.FuzzyDate
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaListEntryItem
import com.example.data.model.MediaListGroup
import com.example.data.model.MediaNode
import com.example.data.model.MediaTitle
import com.example.domain.usecase.FindArrivingEntriesUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the "Currently arriving" section's derivation: only the viewer's own
 * entries whose media is airing or not-yet-released, airing before upcoming,
 * soonest first, one row per show.
 */
class FindArrivingEntriesUseCaseTest {

    private val useCase = FindArrivingEntriesUseCase()

    private fun show(
        id: Int,
        title: String = "Show $id",
        status: String? = "RELEASING",
        episodes: Int? = 12,
        startDate: FuzzyDate? = null,
        nextEpisode: Int? = null,
        airingAt: Long? = null
    ) = MediaNode(
        id = id,
        title = MediaTitle(english = title),
        format = "TV",
        status = status,
        episodes = episodes,
        startDate = startDate,
        nextAiringEpisode = airingAt?.let { com.example.data.model.NextAiringEpisode(nextEpisode, it) }
    )

    private fun entry(
        media: MediaNode,
        status: String? = "CURRENT",
        progress: Int? = null
    ) = MediaListEntryItem(status = status, progress = progress, media = media)

    private fun collection(vararg groups: MediaListGroup) = MediaListCollection(lists = groups.toList())

    @Test
    fun `only releasing and not-yet-released media arrives`() {
        val list = collection(
            MediaListGroup(name = "Watching", status = "CURRENT", entries = listOf(
                entry(show(1, status = "RELEASING")),
                entry(show(2, status = "NOT_YET_RELEASED")),
                entry(show(3, status = "FINISHED")),
                entry(show(4, status = "CANCELLED"))
            ))
        )

        val result = useCase.execute(list)

        assertEquals(listOf(1, 2), result.map { it.mediaId })
    }

    @Test
    fun `completed and dropped entries never arrive`() {
        val list = collection(
            MediaListGroup(name = "Rewatching", status = "CURRENT", entries = listOf(
                entry(show(1), status = "COMPLETED"),
                entry(show(2), status = "DROPPED"),
                entry(show(3), status = "PAUSED")
            ))
        )

        val result = useCase.execute(list)

        assertEquals(listOf(3), result.map { it.mediaId })
    }

    @Test
    fun `airing shows come before upcoming shows`() {
        val list = collection(
            MediaListGroup(name = "Watching", status = "CURRENT", entries = listOf(
                entry(show(21, status = "NOT_YET_RELEASED", startDate = FuzzyDate(2026, 1, 1))),
                entry(show(22, status = "RELEASING", airingAt = 5_000L, nextEpisode = 7))
            ))
        )

        val result = useCase.execute(list)

        assertEquals(listOf(22, 21), result.map { it.mediaId })
    }

    @Test
    fun `soonest first inside each group`() {
        val list = collection(
            MediaListGroup(name = "Watching", status = "CURRENT", entries = listOf(
                entry(show(31, airingAt = 9_000L, nextEpisode = 8)),
                entry(show(32, airingAt = 1_000L, nextEpisode = 7)),
                entry(show(33, status = "NOT_YET_RELEASED", startDate = FuzzyDate(2026, 10, 15))),
                entry(show(34, status = "NOT_YET_RELEASED", startDate = FuzzyDate(2026, 4, 5)))
            ))
        )

        val result = useCase.execute(list)

        assertEquals(listOf(32, 31, 34, 33), result.map { it.mediaId })
    }

    @Test
    fun `an unknown start sorts last among upcoming`() {
        val list = collection(
            MediaListGroup(name = "Planning", status = "PLANNING", entries = listOf(
                entry(show(41, status = "NOT_YET_RELEASED")),
                entry(show(42, status = "NOT_YET_RELEASED", startDate = FuzzyDate(2026, 6, 1)))
            ))
        )

        val result = useCase.execute(list)

        assertEquals(listOf(42, 41), result.map { it.mediaId })
    }

    @Test
    fun `an entry sitting in two lists appears once`() {
        val repeated = show(51)
        val list = collection(
            MediaListGroup(name = "Watching", status = "CURRENT", entries = listOf(
                entry(repeated), entry(repeated)
            ))
        )

        assertEquals(1, useCase.execute(list).size)
    }

    @Test
    fun `entry fields are carried through`() {
        val list = collection(
            MediaListGroup(name = "Watching", status = "CURRENT", entries = listOf(
                entry(
                    show(61, startDate = FuzzyDate(2026, 10, 5), episodes = 24),
                    progress = 6
                )
            ))
        )

        val result = useCase.execute(list).single()

        assertEquals("Show 61", result.title)
        assertEquals(24, result.episodes)
        assertEquals(6, result.progress)
        assertEquals("5 Oct 2026", result.startLabel)
        assertEquals("https://anilist.co/anime/61", result.siteUrl)
    }

    @Test
    fun `start label degrades as the announced date gets vaguer`() {
        assertEquals("Oct 2026", ArrivingEntryFixture.entry(FuzzyDate(2026, 10, null)).startLabel)
        assertEquals("2026", ArrivingEntryFixture.entry(FuzzyDate(2026, null, null)).startLabel)
        assertNull(ArrivingEntryFixture.entry(null).startLabel)
    }

    @Test
    fun `no airing time means no countdown`() {
        assertNull(useCase.execute(collection(
            MediaListGroup(name = "Watching", status = "CURRENT", entries = listOf(entry(show(71))))
        )).single().nextAiringCountdown(nowMillis = 1_000_000L))
    }
}

private object ArrivingEntryFixture {
    fun entry(startDate: FuzzyDate?): ArrivingEntry = ArrivingEntry(
        mediaId = 1,
        title = "Show",
        coverUrl = null,
        coverColor = null,
        status = "NOT_YET_RELEASED",
        startDate = startDate,
        nextAiringAt = null,
        nextEpisodeNumber = null,
        episodes = null,
        progress = null
    )
}