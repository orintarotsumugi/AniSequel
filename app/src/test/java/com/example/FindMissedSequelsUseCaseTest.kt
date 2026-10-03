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

    /**
     * The other half of "an entry the user has hidden is not offered".
     *
     * Hiding has to be reversible, and the route back is a list the user can
     * read. That list cannot be built from the stored preference alone - it
     * holds bare AniList ids - so the walk has to be able to produce the hidden
     * entries as well, with the titles and parents the UI renders them from.
     *
     * This is the flag that makes that possible, and it defaults to off so the
     * ordinary path still never builds a hidden entry.
     */
    @Test
    fun `includeHidden surfaces a hidden entry so it can be restored`() {
        val collection = singleSequelCollection(sequelId = 200)

        val withHidden = useCase.discover(
            collection = collection,
            filterCriteria = FilterCriteria(hiddenMediaIds = setOf(200)),
            includeHidden = true
        )

        assertEquals(1, withHidden.size)
        assertEquals(200, withHidden[0].sequelId)
        assertEquals("Season 2", withHidden[0].sequelTitle)
    }

    /**
     * The default is the behaviour the dashboard depends on.
     *
     * Pinning it explicitly, because "the walk always includes hidden entries"
     * and "the walk honours the hidden set" are both defensible readings of
     * `discover`, and only one of them is what the filter path expects.
     */
    @Test
    fun `discover excludes hidden entries unless asked`() {
        val collection = singleSequelCollection(sequelId = 200)

        val byDefault = useCase.discover(
            collection = collection,
            filterCriteria = FilterCriteria(hiddenMediaIds = setOf(200))
        )

        assertEquals(0, byDefault.size)
    }

    /**
     * The split that feeds the two halves of the screen.
     *
     * The visible half must be exactly what the dashboard would have drawn, and
     * the hidden half must carry the hidden entry rather than dropping it - that
     * partition is the whole reason a restore is possible at all.
     */
    @Test
    fun `splitHidden separates hidden entries and keeps both halves intact`() {
        val collection = singleSequelCollection(sequelId = 200)

        val candidates = useCase.discover(collection, FilterCriteria(), includeHidden = true)
        val split = useCase.splitHidden(candidates, setOf(200))

        assertEquals(0, split.visible.size)
        assertEquals(1, split.hidden.size)
        assertEquals(200, split.hidden[0].sequelId)
    }

    /**
     * Restoring is the point, so the hidden half must not be silently emptied.
     *
     * An empty hidden set is the common case (nobody has hidden anything yet)
     * and must be a cheap no-op rather than an error path.
     */
    @Test
    fun `splitHidden with nothing hidden returns everything as visible`() {
        val collection = singleSequelCollection(sequelId = 200)

        val candidates = useCase.discover(collection, FilterCriteria(), includeHidden = true)
        val split = useCase.splitHidden(candidates, emptySet())

        assertEquals(1, split.visible.size)
        assertEquals(0, split.hidden.size)
    }

    /**
     * The hidden list is a lookup table, so it is sorted by title.
     *
     * Someone looking for the one anime they dismissed is far more likely to
     * half-remember a name than a release date, and this list deliberately
     * ignores the sort and filter chips - it is not a view of the dashboard.
     */
    @Test
    fun `the hidden half is sorted by title`() {
        val collection = threeSequelCollection()
        val candidates = useCase.discover(collection, FilterCriteria(), includeHidden = true)

        val split = useCase.splitHidden(candidates, setOf(200, 300))

        assertEquals(
            listOf("Apple Season 2", "Cherry Season 4"),
            split.hidden.map { it.sequelTitle }
        )
    }

    /** One completed entry per sequel, each named so sort order is checkable. */
    private fun threeSequelCollection(): MediaListCollection {
        fun sequel(id: Int, title: String) = MediaNode(
            id = id,
            title = MediaTitle(english = title),
            format = "TV",
            status = "FINISHED",
            episodes = 12
        )

        return MediaListCollection(
            lists = listOf(
                MediaListGroup(
                    name = "Completed",
                    status = "COMPLETED",
                    entries = listOf(200, 300, 400).map { id ->
                        val title = when (id) {
                            200 -> "Apple Season 2"
                            300 -> "Cherry Season 4"
                            else -> "Banana Season 6"
                        }
                        MediaListEntryItem(
                            status = "COMPLETED",
                            media = MediaNode(
                                id = id + 1000,
                                title = MediaTitle(english = "Parent $id"),
                                format = "TV",
                                status = "FINISHED",
                                episodes = 12,
                                relations = MediaRelations(
                                    edges = listOf(
                                        MediaRelationEdge(
                                            relationType = "SEQUEL",
                                            node = sequel(id, title)
                                        )
                                    )
                                )
                            )
                        )
                    }
                )
            )
        )
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
