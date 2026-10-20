package com.anisequel

import com.anisequel.data.model.FilterCriteria
import com.anisequel.data.model.FuzzyDate
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaListGroup
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaRelationEdge
import com.anisequel.data.model.MediaRelations
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.StatusFilter
import com.anisequel.domain.usecase.FindMissedSequelsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two filter bugs this class had, both of which produced an empty or
 * misleading list with no error and no explanation.
 */
class FindMissedSequelsUseCaseFilterTest {

    private val useCase = FindMissedSequelsUseCase()

    private val airedSequel = MediaNode(
        id = 201,
        title = MediaTitle(english = "Aired Sequel"),
        format = "TV",
        status = "FINISHED",
        episodes = 12
    )

    private val upcomingSequel = MediaNode(
        id = 202,
        title = MediaTitle(english = "Upcoming Sequel"),
        format = "TV",
        status = "NOT_YET_RELEASED",
        episodes = null
    )

    private fun parent(id: Int, vararg sequels: MediaNode) = MediaNode(
        id = id,
        title = MediaTitle(english = "Parent $id"),
        format = "TV",
        status = "FINISHED",
        episodes = 12,
        relations = MediaRelations(
            sequels.map { MediaRelationEdge("SEQUEL", it) }
        )
    )

    private fun collection(vararg entries: MediaListEntryItem) = MediaListCollection(
        lists = listOf(MediaListGroup(name = "Completed", status = "COMPLETED", entries = entries.toList()))
    )

    @Test
    fun `hide already planned off shows sequels that are already on the planning list`() {
        // The old expression was `!alreadyInUserList || isPlanned`, so a sequel the
        // user had already saved could never reappear by turning the switch off.
        val list = collection(
            MediaListEntryItem(
                status = "COMPLETED",
                media = parent(100, airedSequel, upcomingSequel)
            ),
            MediaListEntryItem(status = "PLANNING", media = airedSequel)
        )

        val hidden = useCase.execute(list, FilterCriteria())
        assertEquals("planned entries are hidden by default", 1, hidden.size)
        assertEquals(202, hidden.single().sequelId)

        val shown = useCase.execute(list, FilterCriteria(hideAlreadyPlanned = false))
        assertEquals("turning the filter off must reveal every sequel", 2, shown.size)
    }

    @Test
    fun `choosing the upcoming status works even when include unreleased is off`() {
        // Picking "Upcoming" in the status filter while the sheet's own
        // "Include unreleased" switch was off returned nothing at all.
        val list = collection(
            MediaListEntryItem(
                status = "COMPLETED",
                media = parent(100, airedSequel, upcomingSequel)
            )
        )

        val result = useCase.execute(
            list,
            FilterCriteria(
                statusFilter = StatusFilter.NOT_YET_RELEASED,
                includeUnreleased = false
            )
        )

        assertEquals(1, result.size)
        assertEquals(202, result.single().sequelId)
    }

    @Test
    fun `include unreleased off still hides upcoming sequels when no status is chosen`() {
        val list = collection(
            MediaListEntryItem(
                status = "COMPLETED",
                media = parent(100, airedSequel, upcomingSequel)
            )
        )

        val result = useCase.execute(list, FilterCriteria(includeUnreleased = false))

        assertEquals(1, result.size)
        assertTrue(result.none { it.isUnreleased })
    }

    @Test
    fun `choosing the airing status returns currently releasing sequels`() {
        // The filter compared the card's display label ("Airing") to the raw
        // AniList enum ("RELEASING"), so the Airing chip always produced an
        // empty list even when the card showed a releasing sequel.
        val airingSequel = MediaNode(
            id = 203,
            title = MediaTitle(english = "Airing Sequel"),
            format = "TV",
            status = "RELEASING",
            episodes = 12
        )
        val list = collection(
            MediaListEntryItem(
                status = "COMPLETED",
                media = parent(100, airedSequel, upcomingSequel, airingSequel)
            )
        )

        val result = useCase.execute(
            list,
            FilterCriteria(statusFilter = StatusFilter.RELEASING)
        )

        assertEquals(1, result.size)
        assertEquals(203, result.single().sequelId)
    }

    @Test
    fun `the two statuses the chip could draw but the filter could not select are selectable`() {
        // Hiatus and Cancelled were drawn by `SequelStatusChip` - with their own
        // colours and icons - and absent from `StatusFilter`, so a cancelled entry
        // could be seen on a card with no way to ask for the cancelled ones. The
        // only route to them was scrolling the unfiltered list.
        val hiatus = MediaNode(
            id = 401,
            title = MediaTitle(english = "On hold"),
            format = "TV",
            status = "HIATUS"
        )
        val cancelled = MediaNode(
            id = 402,
            title = MediaTitle(english = "Never finished"),
            format = "TV",
            status = "CANCELLED"
        )
        val list = collection(
            MediaListEntryItem(
                status = "COMPLETED",
                media = parent(100, airedSequel, upcomingSequel, hiatus, cancelled)
            )
        )

        assertEquals(
            listOf(401),
            useCase.execute(list, FilterCriteria(statusFilter = StatusFilter.HIATUS))
                .map { it.sequelId }
        )
        assertEquals(
            listOf(402),
            useCase.execute(list, FilterCriteria(statusFilter = StatusFilter.CANCELLED))
                .map { it.sequelId }
        )
        // The pre-existing statuses keep meaning what they meant.
        assertEquals(
            listOf(201),
            useCase.execute(list, FilterCriteria(statusFilter = StatusFilter.FINISHED))
                .map { it.sequelId }
        )
    }

    /**
     * The format filter compares against AniList's own enum key.
     *
     * It used to compare against `MissedSequel.format`, which is
     * `MediaNode.formatLabel` - a value spelled for a *reader*. Once `formatLabel`
     * stopped returning the raw enum, "Movie" no longer equalled `MOVIE` and every
     * format chip narrowed the list to nothing: a filter that silently returns an
     * empty list, with no error and an empty state the reader cannot tell from
     * "you have no sequels". All the fixtures here are `TV`, which is why no
     * existing test caught it - `TV` happens to be its own readable label.
     */
    @Test
    fun `the format filter matches AniList's enum key, not the readable label`() {
        val movie = MediaNode(
            id = 203,
            title = MediaTitle(english = "A Movie"),
            format = "MOVIE",
            status = "FINISHED",
            episodes = 100
        )
        val special = MediaNode(
            id = 204,
            title = MediaTitle(english = "A Special"),
            format = "SPECIAL",
            status = "FINISHED",
            episodes = 1
        )
        val tvShort = MediaNode(
            id = 205,
            title = MediaTitle(english = "A Short"),
            format = "TV_SHORT",
            status = "FINISHED",
            episodes = 1
        )
        val list = collection(
            MediaListEntryItem(
                status = "COMPLETED",
                media = parent(300, airedSequel, movie, special, tvShort)
            )
        )

        assertEquals(
            "choosing Movies must keep the movie and drop the TV entries",
            listOf(203),
            useCase.execute(list, FilterCriteria(selectedFormat = "MOVIE"))
                .map { it.sequelId }
        )

        assertEquals(
            "choosing TV series must keep the TV entry and not reach across to the " +
                "movie, now that 'TV' and 'Tv Short' are different readable labels",
            listOf(201),
            useCase.execute(list, FilterCriteria(selectedFormat = "TV"))
                .map { it.sequelId }
        )

        assertEquals(
            "Special is its own chip and its own key",
            listOf(204),
            useCase.execute(list, FilterCriteria(selectedFormat = "SPECIAL"))
                .map { it.sequelId }
        )
    }

    /**
     * A documented gap, pinned rather than papered over.
     *
     * The format chips hold AniList's keys - "TV", "MOVIE", "OVA", "ONA",
     * "SPECIAL" - and the comparison is exact, so a `TV_SHORT` matches **none** of
     * them: not "TV" (different string) and not "SPECIAL" (also different). A TV
     * special is therefore unreachable by any format filter while remaining in the
     * unfiltered list.
     *
     * Not fixed here because "which chip should a TV short belong to" is a product
     * decision, not a bug: folding it into "TV" is defensible and so is giving it a
     * chip of its own. What is a bug is not *knowing* it, which is what this test
     * does - the shape is recorded so the decision is made deliberately later.
     */
    @Test
    fun `a TV short matches no format chip, which is a known gap`() {
        val tvShort = MediaNode(
            id = 205,
            title = MediaTitle(english = "A Short"),
            format = "TV_SHORT",
            status = "FINISHED",
            episodes = 1
        )
        val list = collection(
            MediaListEntryItem(
                status = "COMPLETED",
                media = parent(300, airedSequel, tvShort)
            )
        )

        listOf("TV", "MOVIE", "OVA", "ONA", "SPECIAL").forEach { chip ->
            assertFalse(
                "'$chip' unexpectedly matched a TV_SHORT (id 205)",
                useCase.execute(list, FilterCriteria(selectedFormat = chip))
                    .any { it.sequelId == 205 }
            )
        }

        assertTrue(
            "and it is in the list when no format is chosen, so it is reachable at all",
            useCase.execute(list, FilterCriteria()).any { it.sequelId == 205 }
        )
    }

    @Test
    fun `search matches both the sequel title and the parent it came from`() {
        val list = collection(
            MediaListEntryItem(status = "COMPLETED", media = parent(100, airedSequel))
        )

        assertEquals(1, useCase.execute(list, FilterCriteria(searchQuery = "aired")).size)
        assertEquals(1, useCase.execute(list, FilterCriteria(searchQuery = "parent 100")).size)
        assertEquals(0, useCase.execute(list, FilterCriteria(searchQuery = "nothing here")).size)
    }

    @Test
    fun `a sequel reached from two parents appears once`() {
        val list = collection(
            MediaListEntryItem(status = "COMPLETED", media = parent(100, airedSequel)),
            MediaListEntryItem(status = "COMPLETED", media = parent(101, airedSequel))
        )

        assertEquals(1, useCase.execute(list, FilterCriteria()).size)
    }

    private fun sequel(id: Int, startYear: Int?): MediaNode = MediaNode(
        id = id,
        title = MediaTitle(english = "Sequel $id"),
        format = "TV",
        status = "FINISHED",
        episodes = 12,
        startDate = startYear?.let { FuzzyDate(it, 7, 1) }
    )

    private fun completedEntry(parent: MediaNode, completedYear: Int?) =
        MediaListEntryItem(
            status = "COMPLETED",
            completedAt = completedYear?.let { FuzzyDate(it, 3, 1) },
            media = parent
        )

    private fun released(list: MediaListCollection, criteria: FilterCriteria, year: Int) =
        useCase.applyFilters(useCase.discover(list, criteria), criteria, currentYear = year)

    @Test
    fun `sequel released this year keeps only sequels whose season starts this year`() {
        val list = collection(
            MediaListEntryItem(
                status = "COMPLETED",
                media = parent(100, sequel(301, 2026), sequel(302, 2025))
            )
        )

        val result = released(list, FilterCriteria(sequelReleasedThisYear = true), 2026)

        assertEquals(listOf(301), result.map { it.sequelId })
    }

    @Test
    fun `a sequel with no announced start date is not releasing this year`() {
        val list = collection(
            MediaListEntryItem(
                status = "COMPLETED",
                media = parent(100, sequel(303, 2026), sequel(304, null))
            )
        )

        val result = released(list, FilterCriteria(sequelReleasedThisYear = true), 2026)

        assertEquals(listOf(303), result.map { it.sequelId })
    }

    @Test
    fun `parent completed this year keeps only sequels from parents finished this year`() {
        val oldParent = parent(105, sequel(305, 2026))
        val thisYearParent = parent(106, sequel(306, 2025))
        val list = collection(
            completedEntry(oldParent, 2025),
            completedEntry(thisYearParent, 2026)
        )

        val result = released(list, FilterCriteria(parentCompletedThisYear = true), 2026)

        assertEquals(listOf(306), result.map { it.sequelId })
    }

    @Test
    fun `year axes compose, a candidate must match both`() {
        val thisYearParent = parent(
            107,
            sequel(307, 2026),
            sequel(308, 2025)
        )
        val oldParent = parent(108, sequel(309, 2026))
        val list = collection(
            completedEntry(thisYearParent, 2026),
            completedEntry(oldParent, 2025)
        )

        val criteria = FilterCriteria(
            sequelReleasedThisYear = true,
            parentCompletedThisYear = true
        )
        val result = released(list, criteria, 2026)

        assertEquals(listOf(307), result.map { it.sequelId })
    }

    @Test
    fun `a parent the viewer never marked completed was not completed this year`() {
        val unfinishedParent = parent(109, sequel(310, 2026))
        val list = collection(completedEntry(unfinishedParent, null))

        val result = released(list, FilterCriteria(parentCompletedThisYear = true), 2026)

        assertTrue(result.isEmpty())
    }
}