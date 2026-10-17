package com.anisequel

import com.anisequel.data.network.GraphQLQueries
import com.anisequel.data.repository.STUDIO_PAGE_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The studio query, which is the one document here AniList will reject outright if it
 * is written the obvious way.
 *
 * `Page.media` looks like the natural home for "every title by this studio" - it is
 * the connection every other list in this app asks through - and it has **no
 * `studioId` argument**. The studio's own `media` connection is the only documented
 * route, so the shape of this document is a requirement rather than a style choice,
 * and it is not something the compiler or the UI can catch: a wrong document is a 400
 * on a page that then shows an error.
 */
class StudioQueryTest {

    @Test
    fun `the studio is the root field, not a page filter`() {
        val query = GraphQLQueries.getStudioQuery()

        assertTrue(
            "the studio's own `media` connection is the only documented way to ask " +
                "this - `Page.media` has no `studioId` argument and answers 400:\n$query",
            query.contains("Studio(id: \$id) {")
        )
        assertFalse(
            "no `Page { media(...) }` wrapper: it cannot filter by studio:\n$query",
            query.contains("Page {")
        )
        assertTrue(query.contains("media(page: \$page, perPage: \$perPage, sort: [START_DATE_DESC])"))
    }

    /**
     * 25 is AniList's documented maximum on `Studio.media` - not the root `Page`
     * query's 50, which is where a copied page size comes from. Exceeding it is a 400
     * on the whole document, so the number lives in one named constant and is pinned
     * here rather than trusted to a caller.
     */
    @Test
    fun `the page size is the documented ceiling`() {
        assertEquals(25, STUDIO_PAGE_SIZE)
    }

    /**
     * The year shelves group on `startDate`, so the document has to ask for it - and
     * for the fallback, and for the `type` that makes a tapped card's detail fetch
     * resolve against the right namespace.
     */
    @Test
    fun `each title carries what a shelf card draws and what a tap needs`() {
        val query = GraphQLQueries.getStudioQuery()

        listOf(
            "id",
            "type",
            "title { romaji english native userPreferred }",
            "format",
            "startDate { year month day }",
            "seasonYear",
            "coverImage",
            "averageScore"
        ).forEach { field ->
            assertTrue("the studio's media nodes must carry `$field`:\n$query", query.contains(field))
        }
    }

    /**
     * The studio itself is asked for the facts the page's header states: its name,
     * whether it animates, and how many people have favourited it.
     */
    @Test
    fun `the studio header facts are selected`() {
        val query = GraphQLQueries.getStudioQuery()

        listOf("name", "isAnimationStudio", "favourites", "siteUrl").forEach { field ->
            assertTrue("the header draws `$field`:\n$query", query.contains(field))
        }
    }
}
