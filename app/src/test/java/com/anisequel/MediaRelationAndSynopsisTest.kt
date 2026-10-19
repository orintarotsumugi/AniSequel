package com.anisequel

import com.anisequel.data.model.FuzzyDate
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaRelationEdge
import com.anisequel.data.model.MediaRelations
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.descriptionBody
import com.anisequel.data.model.relatedMedia
import com.anisequel.data.model.synopsis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Three things the title page got wrong about what AniList sends it: which medium a
 * relation belongs to, what order the row is walked in, and what a description is
 * actually made of.
 *
 * All were verified against the live API rather than inferred, and the relation
 * failures each produced a page that was confidently wrong instead of empty - which is
 * why none of them showed up as a failure.
 */
class MediaRelationAndSynopsisTest {

    // ---- relations: the subject's own medium, its adaptation, and the order -------

    /**
     * A manga's relations are nearly all manga, and the adaptation is the exception.
     *
     * One Piece's real relation set, abridged to one entry of each kind it returns: a
     * `SPIN_OFF` manga, a `SIDE_STORY` manga, a `PREQUEL` manga, and the `ANIME`
     * adaptations. The filter used to keep only the last group, which listed the TV
     * series and none of the manga's own franchise; keeping only the same medium then
     * fixed that and hid the TV series in turn. Both belong on the row.
     */
    private val onePieceRelations = MediaRelations(
        edges = listOf(
            relation("SPIN_OFF", "MANGA", 110_000, "One Piece: Shokugeki no Sanji"),
            relation("SIDE_STORY", "MANGA", 110_001, "One Piece: Ace's Story"),
            relation("PREQUEL", "MANGA", 110_002, "Monsters"),
            relation("ADAPTATION", "ANIME", 110_003, "One Piece: Defeat the Pirate Ganzack!"),
            relation("ADAPTATION", "ANIME", 110_004, "One Piece: Episode of Merry"),
            // A CHARACTER edge is a connection between two titles, not a title.
            relation("CHARACTER", "MANGA", 110_005, "One Piece x Toriko")
        )
    )

    private fun relation(
        type: String,
        nodeType: String,
        id: Int,
        title: String,
        startYear: Int? = null
    ) = MediaRelationEdge(
        relationType = type,
        node = MediaNode(
            id = id,
            type = nodeType,
            title = MediaTitle(english = title),
            startDate = startYear?.let { FuzzyDate(year = it) }
        )
    )

    @Test
    fun `a manga page shows its own franchise and the anime adaptation`() {
        val manga = MediaNode(id = 30_013, type = "MANGA", relations = onePieceRelations)

        val titles = manga.relatedMedia.map { it.node.title?.english }

        assertTrue(
            "a manga page has to show the manga's own franchise - the spin-off and " +
                "the prequel are what a reader walking One Piece is looking for",
            titles.contains("One Piece: Shokugeki no Sanji") &&
                titles.contains("Monsters")
        )
        assertTrue(
            "and the anime it became has to be on that page too - AniList files it " +
                "as an ADAPTATION in both directions, so same-medium-only hid it:\n$titles",
            titles.contains("One Piece: Defeat the Pirate Ganzack!") &&
                titles.contains("One Piece: Episode of Merry")
        )
        assertFalse(
            "a CHARACTER edge is a connection between two titles, not a title:\n$titles",
            titles.contains("One Piece x Toriko")
        )
        assertEquals(
            "the row is the franchise spine, then the adaptation, then the side " +
                "material - AniList's raw order interleaves them:\n$titles",
            listOf(
                "Monsters",
                "One Piece: Defeat the Pirate Ganzack!",
                "One Piece: Episode of Merry",
                "One Piece: Shokugeki no Sanji",
                "One Piece: Ace's Story"
            ),
            titles
        )
    }

    /**
     * The mirror of the above: an anime page shows its sequel and its source manga.
     *
     * AniList files the manga as an `ADAPTATION` from the anime's point of view too,
     * which the same-medium rule dropped - the exact failure this pins.
     */
    @Test
    fun `an anime page shows the manga it was adapted from`() {
        val anime = MediaNode(
            id = 1,
            type = "ANIME",
            relations = MediaRelations(
                edges = listOf(
                    relation("SIDE_STORY", "ANIME", 1_000, "Cowboy Bebop: The Movie"),
                    relation("SIDE_STORY", "ANIME", 1_001, "Ein's Summer Vacation"),
                    relation("ADAPTATION", "MANGA", 1_002, "Cowboy Bebop"),
                    relation("ADAPTATION", "MANGA", 1_003, "Cowboy Bebop Shooting Star")
                )
            )
        )

        val titles = anime.relatedMedia.map { it.node.title?.english }

        assertEquals(
            "the adaptation leads the side material, and the film and the special " +
                "still follow it:\n$titles",
            listOf(
                "Cowboy Bebop",
                "Cowboy Bebop Shooting Star",
                "Cowboy Bebop: The Movie",
                "Ein's Summer Vacation"
            ),
            titles
        )
    }

    @Test
    fun `a source relation crosses the medium line like an adaptation`() {
        val anime = MediaNode(
            id = 1,
            type = "ANIME",
            relations = MediaRelations(
                edges = listOf(
                    relation("SOURCE", "MANGA", 2, "The original manga"),
                    relation("SUMMARY", "ANIME", 3, "Recap movie")
                )
            )
        )

        assertEquals(
            listOf("The original manga", "Recap movie"),
            anime.relatedMedia.map { it.node.title?.english }
        )
    }

    @Test
    fun `the row is the spine in story order, then the adaptation, then side material`() {
        val anime = MediaNode(
            id = 1,
            type = "ANIME",
            relations = MediaRelations(
                edges = listOf(
                    relation("SIDE_STORY", "ANIME", 10, "A 2020 special", startYear = 2020),
                    relation("SEQUEL", "ANIME", 11, "A 2017 sequel", startYear = 2017),
                    relation("PREQUEL", "ANIME", 12, "A 2012 prequel", startYear = 2012),
                    relation("ADAPTATION", "MANGA", 13, "A 2010 manga", startYear = 2010),
                    relation("SEQUEL", "ANIME", 14, "A 2023 sequel", startYear = 2023)
                )
            )
        )

        // The rank outranks the date across groups: the 2010 manga follows the 2012
        // prequel because the adaptation is its own answer, not a season.
        assertEquals(
            listOf(
                "A 2012 prequel",
                "A 2017 sequel",
                "A 2023 sequel",
                "A 2010 manga",
                "A 2020 special"
            ),
            anime.relatedMedia.map { it.node.title?.english }
        )
    }

    @Test
    fun `an undated relation sorts after the dated ones in its group`() {
        val anime = MediaNode(
            id = 1,
            type = "ANIME",
            relations = MediaRelations(
                edges = listOf(
                    relation("SEQUEL", "ANIME", 2, "No date yet"),
                    relation("PREQUEL", "ANIME", 3, "A 2014 prequel", startYear = 2014)
                )
            )
        )

        assertEquals(
            listOf("A 2014 prequel", "No date yet"),
            anime.relatedMedia.map { it.node.title?.english }
        )
    }

    @Test
    fun `a relation back to this title is never in its own row`() {
        val anime = MediaNode(
            id = 1,
            type = "ANIME",
            relations = MediaRelations(
                edges = listOf(relation("SUMMARY", "ANIME", 1, "Cowboy Bebop"))
            )
        )

        assertTrue(anime.relatedMedia.isEmpty())
    }

    /**
     * A node with no `type` of its own is read as anime.
     *
     * The activity feed and the widget fixtures are the two sources that can build a
     * node without one, and they did get a relations row before this changed - so
     * treating an unknown as a manga would silently empty the row on exactly the
     * screens that had one.
     */
    @Test
    fun `a node with no type of its own is read as anime`() {
        val typeless = MediaNode(
            id = 5,
            relations = MediaRelations(
                edges = listOf(
                    relation("SEQUEL", "ANIME", 6, "Next season"),
                    relation("ADAPTATION", "MANGA", 7, "The manga")
                )
            )
        )

        assertEquals(
            listOf("Next season", "The manga"),
            typeless.relatedMedia.map { it.node.title?.english }
        )
    }

    @Test
    fun `a character edge is not a title`() {
        val anime = MediaNode(
            id = 1,
            type = "ANIME",
            relations = MediaRelations(
                edges = listOf(
                    relation("CHARACTER", "ANIME", 8, "Some character movie"),
                    relation("SEQUEL", "ANIME", 9, "A real sequel")
                )
            )
        )

        assertEquals(listOf("A real sequel"), anime.relatedMedia.map { it.node.title?.english })
    }

    // ---- the synopsis: AniList's credit line is not part of it -------------------

    /**
     * The shape AniList actually returns, including the `<br>` glue and the trailing
     * credit. Taken from `manga/30013`, which ends `...\n<br><br>\n(Source: VIZ Media)`.
     */
    private val onePieceDescription =
        "\"One Piece,\" said to be the greatest treasure in the world..." +
            "\n<br><br>\n(Source: VIZ Media)"

    @Test
    fun `the source credit is not part of the synopsis`() {
        val manga = MediaNode(id = 30_013, type = "MANGA", description = onePieceDescription)

        val body = manga.descriptionBody!!

        assertFalse(
            "AniList's provenance line is metadata; left in, the card printed " +
                "\"(Source: VIZ Media)\" as the last paragraph of the story:\n$body",
            body.contains("Source")
        )
        assertTrue(
            "and the story itself has to survive the strip:\n$body",
            body.contains("greatest treasure in the world")
        )
    }

    @Test
    fun `the plain synopsis drops the credit and the html`() {
        val manga = MediaNode(id = 30_013, type = "MANGA", description = onePieceDescription)

        val text = manga.synopsis!!

        assertFalse(text.contains("Source"))
        assertFalse(text.contains("<br>"))
        assertTrue(text.startsWith("\"One Piece,\""))
    }

    /**
     * A long description is commonly credited per section, so more than one line can
     * match - and a strip that only removed the last would leave the rest behind.
     */
    @Test
    fun `every source credit is removed, not only the last`() {
        val node = MediaNode(
            id = 1,
            type = "ANIME",
            description = "First part of the story.\n\n(Source: Wikipedia)\n\n" +
                "Second part of the story.\n\n(Source: AniList)"
        )

        val body = node.descriptionBody!!

        assertFalse(body.contains("Source"))
        assertTrue(body.contains("First part"))
        assertTrue(body.contains("Second part"))
    }

    /**
     * The strip is anchored per line on purpose.
     *
     * A synopsis that merely *mentions* a source is prose, and dropping it would
     * delete a sentence rather than a credit.
     */
    @Test
    fun `a source mentioned inside a sentence is left alone`() {
        val node = MediaNode(
            id = 1,
            type = "ANIME",
            description = "Adapted from the novel (Source: unknown) of the same name."
        )

        assertTrue(node.descriptionBody!!.contains("(Source: unknown)"))
    }

    @Test
    fun `a description that is nothing but a credit is not a synopsis`() {
        val node = MediaNode(id = 1, type = "ANIME", description = "(Source: AniList)")

        // Null rather than an empty string, so the card says AniList has no synopsis
        // rather than drawing an empty paragraph.
        assertNull(node.descriptionBody)
        assertNull(node.synopsis)
    }

    @Test
    fun `a description with no credit is returned unchanged`() {
        val node = MediaNode(
            id = 1,
            type = "ANIME",
            description = "Enter a world in the distant future, where Bounty Hunters roam."
        )

        assertEquals(
            "Enter a world in the distant future, where Bounty Hunters roam.",
            node.descriptionBody
        )
    }
}