package com.anisequel

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
 * Two things the title page got wrong about what AniList sends it: which medium a
 * relation belongs to, and what a description is actually made of.
 *
 * Both were verified against the live API rather than inferred, and both produced a
 * page that was confidently wrong instead of empty - which is why neither showed up
 * as a failure.
 */
class MediaRelationAndSynopsisTest {

    // ---- relations: the subject's own medium ------------------------------------

    /**
     * A manga's relations are nearly all manga.
     *
     * One Piece's real relation set, abridged to one entry of each kind it returns:
     * a `SPIN_OFF` manga, a `SIDE_STORY` manga, a `PREQUEL` manga, and the `ANIME`
     * adaptations. The old filter kept only the last group, so a manga page listed
     * the TV series and none of the manga's own franchise.
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

    private fun relation(type: String, nodeType: String, id: Int, title: String) =
        MediaRelationEdge(
            relationType = type,
            node = MediaNode(id = id, type = nodeType, title = MediaTitle(english = title))
        )

    @Test
    fun `a manga's relations are the manga's, not the adaptations`() {
        val manga = MediaNode(id = 30_013, type = "MANGA", relations = onePieceRelations)

        val titles = manga.relatedMedia.map { it.node.title?.english }

        assertTrue(
            "a manga page has to show the manga's own franchise - the spin-off and " +
                "the prequel are what a reader walking One Piece is looking for",
            titles.contains("One Piece: Shokugeki no Sanji") &&
                titles.contains("Monsters")
        )
        assertFalse(
            "the anime adaptations are a different medium and belong on the anime " +
                "page, not on the manga's:\n$titles",
            titles.any { it != null && it.contains("Defeat the Pirate Ganzack") }
        )
    }

    /**
     * The mirror of the above, and the reason the filter is keyed on the subject's
     * type rather than special-cased per medium.
     *
     * AniList files an adaptation in *both* directions: the manga is an `ADAPTATION`
     * from the anime's point of view. So the same rule that keeps a manga's TV
     * adaptations out of its page also keeps an anime's manga adaptations out of
     * its own - and a rule written as "filter to manga for manga, and to anime for
     * everything else" would have had to be stated twice.
     */
    @Test
    fun `an anime's relations are the anime's, not the manga adaptations`() {
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

        assertTrue(
            "the film and the special belong on the anime's own page:\n$titles",
            titles.contains("Cowboy Bebop: The Movie") && titles.contains("Ein's Summer Vacation")
        )
        assertFalse(
            "the manga adaptations are a different medium:\n$titles",
            titles.contains("Cowboy Bebop Shooting Star")
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

        assertEquals(listOf("Next season"), typeless.relatedMedia.map { it.node.title?.english })
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