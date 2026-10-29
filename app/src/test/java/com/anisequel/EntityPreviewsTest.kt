package com.anisequel

import com.anisequel.data.model.EntityPreviewKey
import com.anisequel.data.network.GraphQLQueries
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.entityPreviewKeysIn
import com.anisequel.ui.components.markdown.mediaCoverKeysIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the entity half of the markdown preview cards.
 *
 * ## Why this exists
 *
 * A character, staff and studio id are three separate namespaces, and none of
 * them is a media id. The cards used to draw every one of those links with the
 * **anime** icon and the URL's slug as a heading, because only anime and manga
 * were ever looked up. Sending an entity id to the media query instead would be
 * worse than not looking it up: it answers a confident wrong poster.
 *
 * The test therefore pins two boundaries that nothing else would catch:
 *
 *  - what counts as an entity key (numeric id, one of the three kinds), and
 *  - that a document declares exactly the variables it uses, since GraphQL
 *    rejects an unused one for the whole document - turning every card on the
 *    page back into a fallback, silently.
 */
class EntityPreviewsTest {

    private fun keys(markdown: String) =
        entityPreviewKeysIn(AniListMarkdownParser.parse(markdown))

    @Test
    fun `character staff and studio links keep their own kinds`() {
        val keys = keys(
            """
            [Spike](https://anilist.co/character/1)

            [Mary](https://anilist.co/staff/95269)

            [ufotable](https://anilist.co/studio/43)
            """.trimIndent()
        )

        assertEquals(
            listOf(
                EntityPreviewKey("CHARACTER", 1),
                EntityPreviewKey("STAFF", 95269),
                EntityPreviewKey("STUDIO", 43)
            ),
            keys
        )
    }

    @Test
    fun `a media link is not an entity key`() {
        assertTrue(keys("https://anilist.co/anime/1").isEmpty())
        assertTrue(keys("https://anilist.co/manga/1").isEmpty())
    }

    @Test
    fun `an entity id is never sent to the media cover lookup`() {
        val nodes = AniListMarkdownParser.parse("https://anilist.co/character/1")

        assertTrue(mediaCoverKeysIn(nodes).isEmpty())
    }

    @Test
    fun `a slug is not asked for as id zero`() {
        assertTrue(keys("https://anilist.co/studio/mappa").isEmpty())
        assertTrue(keys("https://anilist.co/character/gojo-satoru").isEmpty())
    }

    @Test
    fun `a user link is not batched`() {
        assertTrue(keys("https://anilist.co/user/rintarotsumugi").isEmpty())
    }

    @Test
    fun `the same entity linked twice is asked for once`() {
        val keys = keys(
            "[Spike](https://anilist.co/character/1) and [Spike again](https://anilist.co/character/1)"
        )

        assertEquals(listOf(EntityPreviewKey("CHARACTER", 1)), keys)
    }

    @Test
    fun `a single-type document declares only that type's variable`() {
        val query = GraphQLQueries.getEntityPreviewsQuery(listOf("CHARACTER"))

        assertTrue(query.contains("query EntityPreviews(\$characterIds: [Int])"))
        // An unused variable is a GraphQL validation error for the whole
        // document, so a bio that links only characters must not declare the
        // staff or studio lists.
        assertFalse(query.contains("\$staffIds"))
        assertFalse(query.contains("\$studioIds"))
        assertTrue(query.contains("characters(id_in: \$characterIds)"))
        assertFalse(query.contains("staff(id_in"))
    }

    @Test
    fun `a document asks once per type, not once per id`() {
        val query = GraphQLQueries.getEntityPreviewsQuery(listOf("CHARACTER", "STAFF", "STUDIO"))

        assertEquals(1, Regex("characters\\(id_in:").findAll(query).count())
        assertEquals(1, Regex("staff\\(id_in:").findAll(query).count())
        assertEquals(1, Regex("studios\\(id_in:").findAll(query).count())
    }

    @Test
    fun `every variable the repository fills is declared and used`() {
        val query = GraphQLQueries.getEntityPreviewsQuery(EntityPreviewKey.SUPPORTED_TYPES)

        EntityPreviewKey.SUPPORTED_TYPES.forEach { type ->
            val variable = GraphQLQueries.entityPreviewVariable(type)
            assertTrue(
                "$type's variable is not declared: $variable",
                query.contains("\$$variable: [Int]")
            )
            assertTrue(
                "$type's variable is not used: $variable",
                query.contains("(id_in: \$$variable)")
            )
        }
    }

    @Test
    fun `the page is large enough for a whole batch`() {
        val query = GraphQLQueries.getEntityPreviewsQuery(listOf("CHARACTER"))

        assertTrue(
            "the repository chunks by this size, so a smaller perPage would truncate",
            query.contains("perPage: ${GraphQLQueries.ENTITY_PREVIEW_PAGE_SIZE}")
        )
    }
}
