package com.anisequel

import com.anisequel.data.model.MediaSearchType
import com.anisequel.data.network.GraphQLQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue search asks one namespace at a time.
 *
 * The request shape is the contract: if the `type` variable is dropped from the
 * document, every search goes back to answering with both anime and manga while
 * the page still draws one chip as selected. The failure is silent - the results
 * look plausible - which is why the variable is pinned here rather than left to
 * a live API check nobody runs on a PR.
 */
class CatalogueSearchTypeTest {

    @Test
    fun `each search type carries AniList's own enum spelling`() {
        assertEquals("ANIME", MediaSearchType.ANIME.apiValue)
        assertEquals("MANGA", MediaSearchType.MANGA.apiValue)
        assertEquals("Anime", MediaSearchType.ANIME.displayName)
        assertEquals("Manga", MediaSearchType.MANGA.displayName)
        assertEquals(MediaSearchType.ANIME, MediaSearchType.DEFAULT)
    }

    @Test
    fun `the search document declares and applies the type variable`() {
        val document = GraphQLQueries.SEARCH_MEDIA

        assertTrue(
            "the document no longer accepts a MediaType, so the page's selected " +
                "namespace cannot reach AniList",
            document.contains("query SearchMedia(\$search: String, \$type: MediaType)")
        )
        assertTrue(
            "the type variable is declared but never passed to media(), which is " +
                "the same as not filtering at all",
            document.contains("media(search: \$search, type: \$type, sort: SEARCH_MATCH")
        )
    }
}
