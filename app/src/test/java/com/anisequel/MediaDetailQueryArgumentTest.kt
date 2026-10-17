package com.anisequel

import com.anisequel.data.network.GraphQLQueries
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The detail document must only pass arguments AniList's schema actually defines.
 *
 * ## What was broken
 *
 * `Media.studios` was selected as `studios(perPage: 25) { edges { ... } }`. The
 * assumption behind it was that a studio connection paginates the way `characters`
 * and `staff` on the same node do. It does not: the field takes a single optional
 * `sort` and nothing else, so `perPage` is not an argument it has.
 *
 * ## Why one bad argument emptied the whole page
 *
 * GraphQL validates a document before it resolves any of it, and AniList answers an
 * unknown argument with **HTTP 400 and `data: null`**:
 *
 *     {"errors":[{"message":"Unknown argument \"perPage\" on field \"studios\" of
 *                 type \"Media\".","status":400}],"data":null}
 *
 * So the cost was not "no studios" - it was no response at all, and with it every
 * section that only the detail query carries. The synopsis, the studio credits and
 * the hero's studio chip, the cast, the crew, the tags, the rankings, the trailer.
 *
 * The dashboard hid most of it, because `withDetail` merges onto a list node that
 * already arrived with `relations` and keeps `detail.relations ?: relations`. The
 * paths with no such fallback were the ones a reader could see: a title opened from
 * a profile activity row, where `withSummaryProgress` hands the screen the *detail*
 * node, rendered with nothing but its summary - which is "the description does not
 * work" and "the studios are not there".
 *
 * Verified against the live API: the same document answers 400 with `perPage` and
 * 200 with every section populated without it.
 *
 * ## Why this is a test and not a note
 *
 * Nothing in the repository catches an argument AniList does not have. The existing
 * `MediaDetailQueryTest` asserted the *shape* of the credits selection - the edges,
 * the `isMain`, the node's three fields - and passed with `perPage` sitting in front
 * of all of them, because it read `substringAfter("studios(")`, which swallows the
 * argument list along with the parenthesis. The bug lived in exactly the span that
 * test skipped.
 */
class MediaDetailQueryArgumentTest {

    /**
     * `Media.studios` takes no arguments at all.
     *
     * Asserted as a bare `studios {`, so it pins the whole selection including the
     * absence of anything in the parentheses - which is what a `substringAfter`
     * reading of this query cannot see.
     */
    @Test
    fun `studios is selected with no arguments`() {
        listOf(
            "typed" to GraphQLQueries.getMediaDetailQuery("ANIME"),
            "untyped" to GraphQLQueries.getMediaDetailQuery(null)
        ).forEach { (name, query) ->
            assertTrue(
                "$name: `Media.studios` takes a `sort` and nothing else - a bare " +
                    "`studios {` is the only selection that validates:\n$query",
                query.contains("studios { edges {")
            )
        }
    }

    /**
     * The regression itself, named so a failure names the bug.
     *
     * The span this reads is `studios` up to `coverImage`, which is the credits
     * selection and its argument list - the span the shape assertions above skipped.
     */
    @Test
    fun `no field in the credits selection is paginated`() {
        val credits = GraphQLQueries.getMediaDetailQuery("ANIME")
            .substringAfter("studios")
            .substringBefore("coverImage")

        assertFalse(
            "`Media.studios` is not paginated, and asking it to be is an AniList " +
                "400 that answers `data: null` for the entire detail document - the " +
                "synopsis, studios, cast, staff, tags and rankings all go empty at " +
                "once:\n$credits",
            credits.contains("perPage")
        )
        assertFalse(
            "an argument that is not `sort` is not in `Media.studios`' schema, and " +
                "the document is rejected as a whole for any argument it does not " +
                "have:\n$credits",
            Regex("""studios\s*\(\s*(?!sort)""").containsMatchIn(credits)
        )
    }

    /**
     * The whole point of the assertion: the fields the page renders have to survive.
     *
     * Stated separately from the argument test so a 400 is never "fixed" by deleting
     * a selection. `description` and the credits are the two this report named;
     * `characters` and `staff` went with them in the same response.
     */
    @Test
    fun `the sections that vanished with the 400 are all still selected`() {
        val query = GraphQLQueries.getMediaDetailQuery("ANIME")

        listOf(
            "description" to "description(asHtml: false)",
            "studios" to "studios { edges {",
            "coverImage" to "coverImage {",
            "bannerImage" to "bannerImage",
            "trailer" to "trailer {",
            "characters" to "characters(",
            "staff" to "staff(",
            "tags" to "tags {",
            "rankings" to "rankings {",
            "synonyms" to "synonyms"
        ).forEach { (field, needle) ->
            assertTrue(
                "the detail document must still select `$field`, or opening any " +
                    "title shows a page with that section missing:\n$query",
                query.contains(needle)
            )
        }
    }

    /**
     * `perPage` is legitimate on the two connections that *are* paginated, so this
     * pins that the fix removed it from one place rather than from the document.
     *
     * A "delete every `perPage`" fix would satisfy the tests above and break the
     * cast and the crew at the same time, which is the trade this document must not
     * make: they are the two the same 400 took away in the first place.
     */
    @Test
    fun `the paginated connections still keep their page sizes`() {
        val query = GraphQLQueries.getMediaDetailQuery("ANIME")

        val characters = query.substringAfter("characters(").substringBefore("staff(")
        assertTrue(
            "`characters` is paginated and needs its page size:\n$characters",
            characters.contains("perPage:")
        )

        // `staff` is the document's last selection, so its span runs to the end
        // rather than to a following field name. `relations` would be the obvious
        // bound and it is a no-op here - it is asked for *before* `staff`, so
        // `substringAfter("staff(").substringBefore("relations {")` would hand back
        // the whole remainder and match anything.
        val staff = query.substringAfter("staff(")
        assertTrue(
            "`staff` is paginated and needs its page size:\n$staff",
            staff.contains("perPage:")
        )
    }
}