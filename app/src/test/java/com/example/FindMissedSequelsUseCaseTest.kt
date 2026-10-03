package com.example

import com.example.data.model.FilterCriteria
import com.example.data.model.MediaCoverImage
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaListEntryItem
import com.example.data.model.MediaListGroup
import com.example.data.model.MediaNode
import com.example.data.model.MediaRelationEdge
import com.example.data.model.MediaRelations
import com.example.data.model.MediaTitle
import com.example.domain.usecase.FindMissedSequelsUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class FindMissedSequelsUseCaseTest {

    private val useCase = FindMissedSequelsUseCase()

    @Test
    fun `finds missed sequel when parent is completed and sequel is not in user list`() {
        val sequelMedia = MediaNode(
            id = 200,
            title = MediaTitle(english = "Kaguya-sama Season 2"),
            format = "TV",
            status = "FINISHED",
            episodes = 12
        )

        val parentMedia = MediaNode(
            id = 100,
            title = MediaTitle(english = "Kaguya-sama Season 1"),
            format = "TV",
            status = "FINISHED",
            episodes = 12,
            relations = MediaRelations(
                edges = listOf(
                    MediaRelationEdge(relationType = "SEQUEL", node = sequelMedia)
                )
            )
        )

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
                )
            )
        )

        val result = useCase.execute(collection, FilterCriteria())

        assertEquals(1, result.size)
        assertEquals(200, result[0].sequelId)
        assertEquals("Kaguya-sama Season 2", result[0].sequelTitle)
        assertEquals("Kaguya-sama Season 1", result[0].parentTitle)
    }

    @Test
    fun `ignores sequel if already completed in user list`() {
        val sequelMedia = MediaNode(
            id = 200,
            title = MediaTitle(english = "Mob Psycho 100 II"),
            format = "TV",
            status = "FINISHED",
            episodes = 12
        )

        val parentMedia = MediaNode(
            id = 100,
            title = MediaTitle(english = "Mob Psycho 100"),
            format = "TV",
            status = "FINISHED",
            episodes = 12,
            relations = MediaRelations(
                edges = listOf(
                    MediaRelationEdge(relationType = "SEQUEL", node = sequelMedia)
                )
            )
        )

        val collection = MediaListCollection(
            lists = listOf(
                MediaListGroup(
                    name = "Completed",
                    status = "COMPLETED",
                    entries = listOf(
                        MediaListEntryItem(status = "COMPLETED", media = parentMedia),
                        MediaListEntryItem(status = "COMPLETED", media = sequelMedia)
                    )
                )
            )
        )

        val result = useCase.execute(collection, FilterCriteria())
        assertEquals(0, result.size)
    }

    /**
     * "Don't show me this one again."
     *
     * The check lives in [FindMissedSequelsUseCase.discover] rather than in the
     * filter pass, so this asserts on `execute` - which runs both - to pin the
     * behaviour a user actually sees.
     */
    @Test
    fun `an entry the user has hidden is not offered`() {
        val collection = singleSequelCollection(sequelId = 200)

        assertEquals(1, useCase.execute(collection, FilterCriteria()).size)

        val hidden = useCase.execute(
            collection,
            FilterCriteria(hiddenMediaIds = setOf(200))
        )

        assertEquals(0, hidden.size)
    }

    /**
     * The hidden set must not hide anything else.
     *
     * An id that is not in the list at all is the shape of a stale entry, or one
     * hidden on a device that has since synced a different list, and it must not
     * take the rest of the list down with it.
     */
    @Test
    fun `hiding an unrelated entry leaves the list intact`() {
        val collection = singleSequelCollection(sequelId = 200)

        val result = useCase.execute(
            collection,
            FilterCriteria(hiddenMediaIds = setOf(9999))
        )

        assertEquals(1, result.size)
        assertEquals(200, result[0].sequelId)
    }

    /** One completed entry with one missing sequel, id [sequelId]. */
    private fun singleSequelCollection(sequelId: Int): MediaListCollection {
        val sequelMedia = MediaNode(
            id = sequelId,
            title = MediaTitle(english = "Season 2"),
            format = "TV",
            status = "FINISHED",
            episodes = 12
        )

        val parentMedia = MediaNode(
            id = 100,
            title = MediaTitle(english = "Season 1"),
            format = "TV",
            status = "FINISHED",
            episodes = 12,
            relations = MediaRelations(
                edges = listOf(MediaRelationEdge(relationType = "SEQUEL", node = sequelMedia))
            )
        )

        return MediaListCollection(
            lists = listOf(
                MediaListGroup(
                    name = "Completed",
                    status = "COMPLETED",
                    entries = listOf(
                        MediaListEntryItem(status = "COMPLETED", media = parentMedia)
                    )
                )
            )
        )
    }
}
