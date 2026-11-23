package com.anisequel

import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverImage
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.network.GraphQLQueries
import com.anisequel.data.repository.MAX_COVER_BATCH_KEYS
import com.anisequel.data.repository.mediaCoverBatchIds
import com.anisequel.data.repository.mediaCoverBatches
import com.anisequel.data.repository.mediaCoversFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the batched cover query against the thing it has to get right.
 *
 * ## Why this document has its own test
 *
 * It is the one query in the app whose *shape* is a feature, and that shape
 * changed on 2026-11-23 when AniList started counting **every aliased root and
 * every `Page` in a request against the rate limit separately**. The old
 * document emitted one `Page` alias per link (`m0`, `m1`, ...), so a twelve-link
 * bio spent twelve of the thirty requests a minute the batch existed to save -
 * verified against the live API, where five aliases took `X-RateLimit-Remaining`
 * down by five. The batch is now one `Page.media(id_in:)` field, which the live
 * API charges as one request however many ids it carries.
 *
 * The second thing its shape decides is whether one bad link takes the rest with
 * it, which is what the `Page` wrapper below is about.
 *
 * It is also the one query that can silently return the **wrong entity**. AniList
 * media ids are unique only within a media type, so `anime/1` and `manga/1` share
 * a number - and `id_in` cannot say which namespace was requested, so an answer
 * for one id can contain both. An ambiguous answer is not a null, it is a
 * confident wrong poster on a card, which is the one failure mode the whole
 * markdown preview feature guards against. [mediaCoversFrom] is the rule that
 * keeps them apart.
 */
class MediaCoversQueryTest {

    private fun keys(vararg pairs: Pair<String, Int>) =
        pairs.map { MediaCoverKey(type = it.first, id = it.second) }

    private fun node(type: String, id: Int, url: String? = "https://img/$type/$id.jpg") =
        MediaNode(
            id = id,
            type = type,
            title = MediaTitle(romaji = "$type $id"),
            coverImage = url?.let { MediaCoverImage(large = it) }
        )

    @Test
    fun `asks for the whole batch as one connection`() {
        val query = GraphQLQueries.getMediaCoversQuery(keys("ANIME" to 1, "MANGA" to 2, "ANIME" to 3))

        // The aliases are the thing that used to cost a request each. One root
        // field answers any number of ids, so a batch is one rate-limit unit.
        assertEquals(
            "a batch must be one Page root field, not one alias per link",
            1,
            Regex("""\bPage\b""").findAll(query).count()
        )
        assertFalse(
            "any alias in this document would be charged as another request",
            Regex("""\bm\d+: """).containsMatchIn(query)
        )
        assertTrue(
            "the ids travel as one list, which is what makes one root enough",
            query.contains("media(id_in: \$ids)")
        )
    }

    @Test
    fun `declares the ids once as a nullable Int list`() {
        val query = GraphQLQueries.getMediaCoversQuery(keys("ANIME" to 1))

        // `id_in` is `[Int]`, not `[Int]!`, and the sigil is not decoration: a
        // declaration spelled `ids: [Int]` while the use site says `$ids` is a
        // syntax error, and AniList answers the whole batch with HTTP 400 - so no
        // cover ever arrived and every card kept its placeholder with nothing on
        // screen to say why.
        assertTrue(query.contains("query MediaCovers(\$ids: [Int])"))
        assertFalse(
            "the list argument is not non-null in AniList's schema",
            query.contains("[Int]!")
        )
    }

    @Test
    fun `the page is large enough for both media namespaces`() {
        val size = MAX_COVER_BATCH_KEYS
        val query = GraphQLQueries.getMediaCoversQuery(
            (1..size).map { MediaCoverKey("ANIME", it) }
        )
        val perPage = Regex("""perPage: (\d+)""").find(query)
            ?.groupValues
            ?.get(1)
            ?.toInt()

        assertTrue("the page must declare a perPage", perPage != null)
        assertTrue(
            "one id can resolve in both namespaces, so a page of $size would truncate the answer",
            perPage!! >= size * 2
        )
        assertTrue("Page rejects a perPage over 50", perPage <= 50)
    }

    @Test
    fun `one unresolvable id cannot fail the whole batch`() {
        // Verified against the live API rather than reasoned about. Written as
        // `Media(id: $id)`, a batch containing an id with no media answers **HTTP
        // 404** with `errors: ["Not Found."]`, and the good ids in `data` are then
        // discarded with it. One stale link in a hall-of-fame bio blanked every
        // poster on the card. `Page.media` filters instead of failing and answers
        // 200 with the missing id absent.
        val query = GraphQLQueries.getMediaCoversQuery(keys("ANIME" to 1, "MANGA" to 2))

        assertFalse(
            "a Media root field 404s the whole document as soon as one id has no media",
            Regex("""(^|[^.\w])Media\(""").containsMatchIn(query)
        )
        assertTrue("the ids must be resolved through the connection", query.contains("media(id_in:"))
    }

    @Test
    fun `asks for the title that labels the card`() {
        // The card's heading falls back to the URL when there is no cover, and a
        // URL of `anilist.co/anime/1` has no readable name in it - so the title in
        // this selection is what turns `Anime #1` into the actual show.
        val query = GraphQLQueries.getMediaCoversQuery(keys("ANIME" to 1))

        assertTrue(
            "the cover query must ask for the title the card is labelled with",
            query.contains("title {")
        )
        listOf("romaji", "english", "userPreferred").forEach { field ->
            assertTrue("title selection is missing $field", query.contains(field))
        }
        // The response is matched back to the request by these two, so neither can
        // be dropped from the selection.
        assertTrue("the answer cannot be keyed without `type`", query.contains("type"))
        assertTrue("the answer cannot be keyed without `id`", query.contains("id"))
    }

    @Test
    fun `an empty batch is rejected rather than sent`() {
        // `query MediaCovers { media: Page { media(id_in: []) } }` is a wasted
        // request, and a bio with no AniList links is the common case, not an
        // exceptional one.
        try {
            GraphQLQueries.getMediaCoversQuery(emptyList())
            throw AssertionError("an empty cover batch must be rejected, not sent")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.isNotEmpty() == true)
        }
    }

    @Test
    fun `a link-heavy bio is split into documents AniList will accept`() {
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
            // connection for the batch, and a page big enough to answer it.
            val query = GraphQLQueries.getMediaCoversQuery(batch)
            assertEquals(
                "every batch must be exactly one root field now",
                1,
                Regex("""\bPage\b""").findAll(query).count()
            )
        }
    }

    @Test
    fun `the same id in both namespaces is sent once`() {
        // `id_in` is a list of numbers, so anime/1 and manga/1 are the same entry
        // to the server. Sending it twice asks for the same lookup twice.
        assertEquals(
            listOf(1, 2),
            mediaCoverBatchIds(keys("ANIME" to 1, "MANGA" to 1, "ANIME" to 2))
        )
    }

    @Test
    fun `a cover is matched back by namespace, not by id`() {
        // The live API answers `id_in: [1]` with both anime/1 and manga/1 when both
        // exist. Attaching by id alone would draw the manga poster on the anime
        // card - a confident wrong answer, not a missing one.
        val found = mediaCoversFrom(
            batch = keys("ANIME" to 1),
            nodes = listOf(node("ANIME", 1, "https://img/anime/1.jpg"), node("MANGA", 1, "https://img/manga/1.jpg"))
        )

        assertEquals(setOf(MediaCoverKey("ANIME", 1)), found.keys)
        assertEquals("https://img/anime/1.jpg", found[MediaCoverKey("ANIME", 1)]?.url)
    }

    @Test
    fun `both namespaces of one id are kept when both were asked for`() {
        val found = mediaCoversFrom(
            batch = keys("ANIME" to 1, "MANGA" to 1),
            nodes = listOf(node("ANIME", 1, "https://img/anime/1.jpg"), node("MANGA", 1, "https://img/manga/1.jpg"))
        )

        assertEquals(
            mapOf(
                MediaCoverKey("ANIME", 1) to MediaCover("https://img/anime/1.jpg", "ANIME 1"),
                MediaCoverKey("MANGA", 1) to MediaCover("https://img/manga/1.jpg", "MANGA 1")
            ),
            found
        )
    }

    @Test
    fun `a node with no cover or no type is skipped rather than guessed at`() {
        val found = mediaCoversFrom(
            batch = keys("ANIME" to 1, "ANIME" to 2, "ANIME" to 3),
            nodes = listOf(
                node("ANIME", 1),
                node("ANIME", 2, url = null),
                MediaNode(id = 3, type = null, title = MediaTitle(romaji = "no type"))
            )
        )

        assertEquals(setOf(MediaCoverKey("ANIME", 1)), found.keys)
    }
}
