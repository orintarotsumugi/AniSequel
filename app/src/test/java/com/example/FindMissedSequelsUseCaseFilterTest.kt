package com.example

import com.example.data.model.FilterCriteria
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaListEntryItem
import com.example.data.model.MediaListGroup
import com.example.data.model.MediaNode
import com.example.data.model.MediaRelationEdge
import com.example.data.model.MediaRelations
import com.example.data.model.MediaTitle
import com.example.data.model.StatusFilter
import com.example.domain.usecase.FindMissedSequelsUseCase
import org.junit.Assert.assertEquals
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
}