package com.example

import com.example.data.model.MediaCoverKey
import com.example.data.network.GraphQLQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the batched cover query against the thing it has to get right.
 *
 * ## Why this document has its own test
 *
 * It is the one query in the app whose *shape* is the feature. A bio's dozen
 * AniList links have to cost one request, which is why the `Media` root field is
 * aliased per entry rather than fetched one at a time - see
 * [GraphQLQueries.getMediaCoversQuery].
 *
 * It is also the one query that can silently return the **wrong entity**. AniList
 * media ids are unique only within a media type, so `Media(id: 1)` is ambiguous
 * between `anilist.co/anime/1` and `anilist.co/manga/1` - and an ambiguous answer
 * is not a null, it is a confident wrong poster on a card, which is the one
 * failure mode the whole markdown preview feature guards against. The `type:`
 * argument is what removes the ambiguity, and nothing else in the app would fail
 * if it were dropped.
 */
class MediaCoversQueryTest {

    private fun keys(vararg pairs: Pair<String, Int>) =
        pairs.map { MediaCoverKey(type = it.first, id = it.second) }

    @Test
    fun `asks for a type alongside every id`() {
        val query = GraphQLQueries.getMediaCoversQuery(keys("ANIME" to 1, "MANGA" to 2))

        assertTrue(
            "Media must be asked for a type, or anime/1 and manga/1 are ambiguous",
            query.contains("type:")
        )
        // `$` appears only at the *use* site, inside the field; the declaration
        // list names the variables without it.
        assertTrue(
            "every aliased field must pass its own type variable",
            query.contains("type: \$type0") && query.contains("type: \$type1")
        )
    }

    @Test
    fun `declares an id and a type variable for each entry`() {
        val query = GraphQLQueries.getMediaCoversQuery(keys("ANIME" to 1, "MANGA" to 2, "ANIME" to 3))

        assertTrue(
            "AniList's type argument is a MediaType enum and must be declared as one",
            query.contains("query MediaCovers(id0: Int, type0: MediaType")
        )
        // Three entries, three declarations of each. A missing one is a document
        // AniList rejects outright, taking every poster in the bio with it.
        assertEquals(3, Regex("""\bid\d+: Int\b""").findAll(query).count())
        assertEquals(3, Regex("""\btype\d+: MediaType\b""").findAll(query).count())
    }

    @Test
    fun `does not declare the type variable as non-null`() {
        // `Media.type` is nullable in AniList's schema, so a `MediaType!` variable
        // would make the whole document invalid - and the failure would be a 400 on
        // every profile open, which is the worst possible place to discover it.
        val query = GraphQLQueries.getMediaCoversQuery(keys("ANIME" to 1))

        assertFalse(
            "MediaType! would not validate against a nullable argument",
            query.contains("MediaType!")
        )
    }

    @Test
    fun `aliases each entry so two sharing an id are still two fields`() {
        // The anime/manga id collision: under a bare-id key these two links are one
        // entry, and the one answer is then drawn on both cards.
        val query = GraphQLQueries.getMediaCoversQuery(keys("ANIME" to 1, "MANGA" to 1))

        assertTrue("the first entry is aliased", query.contains("m0: Media("))
        assertTrue("the second entry is aliased", query.contains("m1: Media("))
        assertEquals(
            "two keys sharing an id must still be two aliased fields",
            2,
            Regex("""m\d+: Media\(""").findAll(query).count()
        )
    }

    @Test
    fun `asks for the title that labels the card`() {
        // The card's heading falls back to the URL when there is no cover, and a
        // URL of `anilist.co/anime/1` has no readable name in it - so the title in
        // this selection is what turns `Anime #1` into the actual show. It was
        // already on the wire and discarded.
        val query = GraphQLQueries.getMediaCoversQuery(keys("ANIME" to 1))

        assertTrue(
            "the cover query must ask for the title the card is labelled with",
            query.contains("title {")
        )
        listOf("romaji", "english", "userPreferred").forEach { field ->
            assertTrue("title selection is missing $field", query.contains(field))
        }
    }

    @Test
    fun `an empty batch is rejected rather than sent`() {
        // `query MediaCovers()` with no fields is a syntax error, and a bio with
        // no AniList links is the common case, not an exceptional one.
        try {
            GraphQLQueries.getMediaCoversQuery(emptyList())
            throw AssertionError("an empty cover batch must be rejected, not sent")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.isNotEmpty() == true)
        }
    }

    @Test
    fun `the alias helper matches what the query writes`() {
        // `AniListRepositoryImpl` reads the response back by this name, so the two
        // cannot drift: an alias helper that disagreed with the query would leave
        // every card on its placeholder with no error anywhere.
        assertEquals("m0", GraphQLQueries.mediaCoverAlias(0))
        assertEquals("m7", GraphQLQueries.mediaCoverAlias(7))
    }
}