package com.anisequel

import com.anisequel.data.model.FilterCriteria
import com.anisequel.data.model.MediaCoverImage
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaListGroup
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaRelationEdge
import com.anisequel.data.model.MediaRelations
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.RelationKind
import com.anisequel.data.model.StudioConnection
import com.anisequel.data.model.StudioNode
import com.anisequel.domain.usecase.FindMissedSequelsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Franchise gaps other than "the next season".
 *
 * Someone who finished season 2 without ever starting season 1 has a missed
 * entry, but it is a prequel, and describing it as "Sequel to: Season 2" is
 * wrong in a way a user notices immediately.
 */
class RelationGapTest {

    private val useCase = FindMissedSequelsUseCase()

    private val seasonOne = MediaNode(
        id = 101922,
        title = MediaTitle(english = "Season 1"),
        format = "TV",
        status = "FINISHED",
        episodes = 26
    )

    private val seasonTwo = MediaNode(
        id = 142329,
        title = MediaTitle(english = "Season 2"),
        format = "TV",
        status = "FINISHED",
        episodes = 12
    )

    private val sideStory = MediaNode(
        id = 300,
        title = MediaTitle(english = "Side Story"),
        format = "ONA",
        status = "FINISHED",
        episodes = 1
    )

    /** A completed entry whose relations are the given (type, node) pairs. */
    private fun parentWith(vararg relations: Pair<String, MediaNode>) = MediaNode(
        id = 100,
        title = MediaTitle(english = "Watched Show"),
        format = "TV",
        status = "FINISHED",
        episodes = 12,
        relations = MediaRelations(
            relations.map { MediaRelationEdge(it.first, it.second) }
        )
    )

    private fun collectionOf(entries: List<MediaListEntryItem>) = MediaListCollection(
        lists = listOf(MediaListGroup(name = "Completed", status = "COMPLETED", entries = entries))
    )

    @Test
    fun `prequels are not shown unless the user asks for them`() {
        val list = collectionOf(
            listOf(
                MediaListEntryItem(
                    status = "COMPLETED",
                    media = parentWith("PREQUEL" to seasonOne, "SEQUEL" to seasonTwo)
                )
            )
        )

        val defaults = useCase.execute(list, FilterCriteria())
        assertEquals("only the sequel by default", listOf(142329), defaults.map { it.sequelId })

        val withPrequels = useCase.execute(
            list,
            FilterCriteria(
                includedRelations = setOf(RelationKind.SEQUEL, RelationKind.PREQUEL)
            )
        )
        assertEquals(setOf(101922, 142329), withPrequels.map { it.sequelId }.toSet())
    }

    @Test
    fun `a prequel is labelled as one rather than as a sequel`() {
        val prequel = MissedSequel(
            parentId = 100,
            parentTitle = "Watched Show",
            sequelMedia = seasonOne,
            relationType = RelationKind.PREQUEL.apiValue
        )

        assertEquals("Prequel to", prequel.relationLabel)
        assertTrue(prequel.isEarlierInFranchise)

        val sequel = prequel.copy(relationType = RelationKind.SEQUEL.apiValue)
        assertEquals("Sequel to", sequel.relationLabel)
        assertFalse(sequel.isEarlierInFranchise)
    }

    @Test
    fun `an unrecognised relation type falls back to a sequel instead of vanishing`() {
        val odd = MissedSequel(
            parentId = 1,
            parentTitle = "Parent",
            sequelMedia = seasonTwo,
            relationType = "SOME_FUTURE_TYPE"
        )

        assertEquals(RelationKind.SEQUEL, RelationKind.fromApi("SOME_FUTURE_TYPE"))
        assertEquals("Sequel to", odd.relationLabel)
    }

    @Test
    fun `discovery and filtering compose to the same result as the one-shot call`() {
        val list = collectionOf(
            listOf(
                MediaListEntryItem(
                    status = "COMPLETED",
                    media = parentWith(
                        "PREQUEL" to seasonOne,
                        "SEQUEL" to seasonTwo,
                        "SIDE_STORY" to sideStory
                    )
                )
            )
        )

        val criteria = FilterCriteria(
            searchQuery = "season",
            includedRelations = setOf(RelationKind.SEQUEL, RelationKind.SIDE_STORY)
        )

        assertEquals(
            useCase.execute(list, criteria).map { it.sequelId },
            useCase.applyFilters(useCase.discover(list, criteria), criteria).map { it.sequelId }
        )
    }

    @Test
    fun `discovery ignores the search box, filtering does not`() {
        val list = collectionOf(
            listOf(
                MediaListEntryItem(
                    status = "COMPLETED",
                    media = parentWith("SEQUEL" to seasonTwo)
                )
            )
        )

        val candidates = useCase.discover(list, FilterCriteria(searchQuery = "nothing matches"))

        assertEquals("the gap exists regardless of what is typed", 1, candidates.size)
        assertEquals(0, useCase.applyFilters(candidates, FilterCriteria(searchQuery = "nothing matches")).size)
    }

    /**
     * The detail query fills in what the list query left out, and must not wipe
     * what the list query already found.
     */
    @Test
    fun `merging lazily fetched detail keeps the fields the list already had`() {
        val fromList = MediaNode(
            id = 142329,
            title = MediaTitle(english = "Season 2"),
            format = "TV",
            status = "FINISHED",
            coverImage = MediaCoverImage(large = "https://cdn/cover.jpg")
        )
        val fromDetail = MediaNode(
            id = 142329,
            description = "Tanjiro and the Sound Hashira...",
            bannerImage = "https://cdn/banner.jpg",
            studios = StudioConnection(listOf(StudioNode(null, "ufotable")))
        )

        val merged = fromList.withDetail(fromDetail)

        assertEquals("Tanjiro and the Sound Hashira...", merged.description)
        assertEquals("https://cdn/banner.jpg", merged.bannerImage)
        assertEquals("ufotable", merged.studios?.nodes?.single()?.name)
        // The list query's cover survives a detail payload that has none.
        assertEquals("https://cdn/cover.jpg", merged.coverImage?.large)
    }

    /**
     * The detail cover's `extraLarge` is the reason the lookup exists, and the merge
     * used to throw it away.
     *
     * It copied `detail.coverImage.large` and nothing else, so a node whose cover had
     * arrived by a thinner route - a profile appearance, built from `coverImage {
     * large }` and promoted to `extraLarge` at that size - kept drawing its 230px
     * `large` in the detail hero even though the response had just handed over the
     * 460px `extraLarge`. A list node already had `extraLarge` and kept it, which is
     * why only the covers that arrived the other way round looked soft.
     */
    @Test
    fun `merging detail keeps the larger of the two covers`() {
        val fromProfile = MediaNode(
            id = 142329,
            coverImage = MediaCoverImage(
                extraLarge = "https://cdn/large.jpg",
                large = "https://cdn/large.jpg"
            )
        )
        val fromDetail = MediaNode(
            id = 142329,
            coverImage = MediaCoverImage(
                extraLarge = "https://cdn/xl.jpg",
                large = "https://cdn/large.jpg",
                color = "#f1a143"
            )
        )

        val merged = fromProfile.withDetail(fromDetail)

        assertEquals(
            "the detail response's extraLarge has to win over a smaller cover the " +
                "row happened to be built with",
            "https://cdn/xl.jpg",
            merged.coverImage?.bestUrl
        )
        assertEquals(
            "and a tint only the detail response knew about must come through too - " +
                "it is the colour the hero falls back to before the image decodes",
            "#f1a143",
            merged.coverImage?.color
        )

        val listNode = MediaNode(
            id = 142329,
            coverImage = MediaCoverImage(extraLarge = "https://cdn/xl.jpg", large = "https://cdn/large.jpg")
        )
        val thinDetail = MediaNode(
            id = 142329,
            coverImage = MediaCoverImage(large = "https://cdn/other-large.jpg")
        )

        assertEquals(
            "and the merge must not downgrade a cover the node already had",
            "https://cdn/xl.jpg",
            listNode.withDetail(thinDetail).coverImage?.bestUrl
        )
    }

    @Test
    fun `a sequel keeps its list data when the detail query returns nothing new`() {
        val fromList = MediaNode(id = 1, description = "Already known")
        val emptyDetail = MediaNode(id = 1)

        assertEquals("Already known", fromList.withDetail(emptyDetail).description)
        assertNull(fromList.withDetail(emptyDetail).bannerImage)
    }
}