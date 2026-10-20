package com.anisequel

import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.network.GraphQLQueries
import com.anisequel.data.repository.MAX_COVER_BATCH_KEYS
import com.anisequel.data.repository.mediaCoverBatches
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
 * AniList links have to cost one request, which is why each entry is its own
 * aliased field rather than fetched one at a time - see
 * [GraphQLQueries.getMediaCoversQuery].
 *
 * The second thing its shape decides is whether one bad link takes the rest with
 * it, which is what the `Page` wrapper below is about.
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
            query.contains("query MediaCovers(\$id0: Int, \$type0: MediaType")
        )
        // The sigil is not decoration. A declaration spelled `id0: Int` while the
        // use site says `$id0` is a syntax error, and AniList answers the *whole*
        // batch with **HTTP 400, "Expected $, found Name \"id0\""** - so no cover
        // ever arrived and every card in every bio kept its placeholder, with no
        // error on screen to say so. This assertion is the only one that could
        // have caught it, and the version of it that read
        // `contains("query MediaCovers(id0: Int")` asserted the broken spelling
        // instead. Verified against the live API.
        assertFalse(
            "every variable declaration needs its `$` sigil",
            Regex("""(?<!\$)id\d+: """).containsMatchIn(query)
        )
        assertFalse(
            "the type declaration needs one too",
            Regex("""(?<!\$)type\d+: """).containsMatchIn(query)
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

        assertTrue("the first entry is aliased", query.contains("m0: Page {"))
        assertTrue("the second entry is aliased", query.contains("m1: Page {"))
        assertEquals(
            "two keys sharing an id must still be two aliased fields",
            2,
            Regex("""m\d+: Page \{""").findAll(query).count()
        )
    }

    @Test
    fun `one unresolvable id cannot fail the whole batch`() {
        // Verified against the live API rather than reasoned about. Written as
        // `m0: Media(id: $id0, type: $type0)` - which is what this document was -
        // a batch containing an id with no media answers **HTTP 404** with
        // `errors: ["Not Found."]`, and the good aliases in `data` are then
        // discarded with it. One stale link in a hall-of-fame bio (a deleted
        // entry, or an id that belongs to the other media namespace) blanked
        // every poster on the card. `Page.media` filters instead of failing and
        // answers 200 with `"m0": { "media": [] }`.
        val query = GraphQLQueries.getMediaCoversQuery(keys("ANIME" to 1, "MANGA" to 2))

        assertFalse(
            "a Media root field 404s the whole document as soon as one id has no media",
            Regex("""m\d+: Media\(""").containsMatchIn(query)
        )
        assertTrue(
            "every aliased field must be the connection, which cannot fail",
            query.contains("m0: Page {") && query.contains("m1: Page {")
        )
        assertTrue(
            "the media is still asked for with its type, inside the connection",
            query.contains("media(id: \$id0, type: \$type0)") &&
                query.contains("media(id: \$id1, type: \$type1)")
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
    fun `a link-heavy bio is split into documents AniList will accept`() {
        // One document with an alias per link grows with the bio. AniList rejects
        // an over-complex document whole, and a cover failure is invisible, so an
        // uncapped batch would leave every poster in a "hall of fame" bio on a
        // placeholder with nothing on screen to explain it.
        val keys = (1..2 * MAX_COVER_BATCH_KEYS + 3).map { MediaCoverKey("ANIME", it) }

        val batches = mediaCoverBatches(keys)

        assertEquals(
            "the remainder must still be asked for, not dropped",
            keys,
            batches.flatten()
        )
        assertTrue("no document may exceed the cap", batches.all { it.size <= MAX_COVER_BATCH_KEYS })
        assertEquals(
            "a full batch, a full batch, then the remainder",
            listOf(MAX_COVER_BATCH_KEYS, MAX_COVER_BATCH_KEYS, 3),
            batches.map { it.size }
        )
        batches.forEach { batch ->
            // The cap only helps if each batch is still a valid document: one
            // alias and one pair of variables per key in that batch.
            val query = GraphQLQueries.getMediaCoversQuery(batch)
            assertEquals(
                "every key in a batch must have exactly one aliased field",
                batch.size,
                Regex("""m\d+: Page \{""").findAll(query).count()
            )
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