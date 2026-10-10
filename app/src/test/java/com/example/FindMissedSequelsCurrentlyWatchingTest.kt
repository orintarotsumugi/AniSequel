package com.example

import com.example.data.model.FilterCriteria
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaListEntryItem
import com.example.data.model.MediaListGroup
import com.example.data.model.MediaNode
import com.example.data.model.MediaRelationEdge
import com.example.data.model.MediaRelations
import com.example.data.model.MediaTitle
import com.example.domain.usecase.FindMissedSequelsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindMissedSequelsCurrentlyWatchingTest {

    private val useCase = FindMissedSequelsUseCase()

    private val sequelMedia = MediaNode(
        id = 200,
        title = MediaTitle(english = "Chainsaw Man: Reze Arc"),
        format = "MOVIE",
        status = "NOT_YET_RELEASED"
    )

    private val parentMedia = MediaNode(
        id = 100,
        title = MediaTitle(english = "Chainsaw Man"),
        format = "TV",
        status = "FINISHED",
        episodes = 12,
        relations = MediaRelations(
            edges = listOf(
                MediaRelationEdge(relationType = "SEQUEL", node = sequelMedia)
            )
        )
    )

    @Test
    fun `default criteria has currently watching and in-list toggles off`() {
        val defaultCriteria = FilterCriteria()
        assertFalse("includeCurrentlyWatching must be off by default", defaultCriteria.includeCurrentlyWatching)
        assertFalse("includeInList must be off by default", defaultCriteria.includeInList)
    }

    @Test
    fun `finds sequel to currently watching parent when toggle is enabled`() {
        val collection = MediaListCollection(
            lists = listOf(
                MediaListGroup(
                    name = "Watching",
                    status = "CURRENT",
                    entries = listOf(
                        MediaListEntryItem(
                            status = "CURRENT",
                            progress = 6,
                            media = parentMedia
                        )
                    )
                )
            )
        )

        // With toggle off: parent is currently watching (not completed), so sequel is skipped
        val defaultResult = useCase.execute(collection, FilterCriteria())
        assertEquals(0, defaultResult.size)

        // With toggle on: parent currently watching is included
        val watchingResult = useCase.execute(
            collection,
            FilterCriteria(includeCurrentlyWatching = true)
        )
        assertEquals(1, watchingResult.size)
        assertEquals(200, watchingResult[0].sequelId)
        assertEquals("Chainsaw Man: Reze Arc", watchingResult[0].sequelTitle)
    }

    @Test
    fun `finds sequel that user is currently watching when toggle is enabled`() {
        val collection = MediaListCollection(
            lists = listOf(
                MediaListGroup(
                    name = "Completed",
                    status = "COMPLETED",
                    entries = listOf(
                        MediaListEntryItem(
                            status = "COMPLETED",
                            media = parentMedia
                        )
                    )
                ),
                MediaListGroup(
                    name = "Watching",
                    status = "CURRENT",
                    entries = listOf(
                        MediaListEntryItem(
                            status = "CURRENT",
                            progress = 2,
                            media = sequelMedia
                        )
                    )
                )
            )
        )

        // Default: sequel already in watching list is skipped
        val defaultResult = useCase.execute(collection, FilterCriteria())
        assertEquals(0, defaultResult.size)

        // Enabled: finds the currently watching sequel and flags isAddedToWatching
        val watchingResult = useCase.execute(
            collection,
            FilterCriteria(includeCurrentlyWatching = true)
        )
        assertEquals(1, watchingResult.size)
        assertEquals(200, watchingResult[0].sequelId)
        assertTrue(watchingResult[0].isAddedToWatching)
    }

    @Test
    fun `finds sequel already in completed list when includeInList is enabled`() {
        val collection = MediaListCollection(
            lists = listOf(
                MediaListGroup(
                    name = "Completed",
                    status = "COMPLETED",
                    entries = listOf(
                        MediaListEntryItem(
                            status = "COMPLETED",
                            media = parentMedia
                        ),
                        MediaListEntryItem(
                            status = "COMPLETED",
                            media = sequelMedia
                        )
                    )
                )
            )
        )

        // Default: already completed sequel is skipped
        val defaultResult = useCase.execute(collection, FilterCriteria())
        assertEquals(0, defaultResult.size)

        // Enabled: in-list sequels are surfaced
        val inListResult = useCase.execute(
            collection,
            FilterCriteria(includeInList = true)
        )
        assertEquals(1, inListResult.size)
        assertEquals(200, inListResult[0].sequelId)
    }

    @Test
    fun `isNarrowing reflects toggles`() {
        assertFalse(FilterCriteria().isNarrowing)
        assertTrue(FilterCriteria(includeCurrentlyWatching = true).isNarrowing)
        assertTrue(FilterCriteria(includeInList = true).isNarrowing)
    }
}
