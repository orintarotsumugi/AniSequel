package com.anisequel

import com.anisequel.data.network.GraphQLQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The detail query, and the id-namespace collision it used to walk into.
 *
 * ## What was broken
 *
 * `GET_MEDIA_DETAIL` asked `Media(id: $id)` with **no type**. AniList media ids are
 * not unique across namespaces: `anilist.co/anime/1` and `anilist.co/manga/1` are
 * different entities, and a bare id is resolved against both, with AniList
 * answering with whichever it prefers.
 *
 * So opening a *manga* rendered the anime's title, synopsis, cast and relations.
 * Where the id matched nothing, AniList answered 404, `require("That entry is no
 * longer available on AniList.")` threw, and `MediaDetailStore` swallowed the
 * failure by design - leaving the thin summary node with no cast, no staff and no
 * relations at all. Both symptoms read as "this title has no relations" and
 * "the character list does not work", from a sheet that was flawless for anime.
 *
 * `MediaCoverKey` documents the same collision and `getMediaCoversQuery` is the
 * fix that shipped for covers; the detail query never received the same treatment.
 */
class MediaDetailQueryTest {

    @Test
    fun `the typed query passes the type to the Media field`() {
        val query = GraphQLQueries.getMediaDetailQuery("MANGA")

        assertTrue(
            "the typed query must scope the id to a namespace, or `manga/1` still " +
                "resolves to whatever `anime/1` is:\n$query",
            query.contains("Media(id: \$id, type: \$type)")
        )
    }

    /**
     * Declared `MediaType` rather than `MediaType!`, because a summary node from a
     * source that never carried `type` cannot supply one - and a non-null
     * declaration would make the whole document invalid for those callers, turning
     * a missing field into a 400 on every open.
     */
    @Test
    fun `the type variable is declared nullable`() {
        val query = GraphQLQueries.getMediaDetailQuery("MANGA")

        assertTrue(
            "a `MediaType!` declaration cannot accept a null type, which every " +
                "summary-only caller passes:\n$query",
            query.contains("\$type: MediaType")
        )
        assertFalse(
            "the type variable must not be declared non-null:\n$query",
            query.contains("\$type: MediaType!")
        )
    }

    /**
     * The typed and untyped forms must declare the same variables in the same shape,
     * because both go out through the same Retrofit method - a document declaring
     * `$type` while the request never sends it is an AniList 400, and one sending
     * `type` for a document that never declared it is the same failure the covers
     * batch hit.
     */
    @Test
    fun `the typed query declares both variables it uses`() {
        val query = GraphQLQueries.getMediaDetailQuery("ANIME")

        assertTrue(query.contains("\$id: Int"))
        assertTrue(query.contains("\$type: MediaType"))
        // Declared once and used once each. A document that declares `$type` without
        // using it, or uses one it never declared, is an AniList 400 - which is
        // exactly how the covers batch failed once.
        assertEquals(1, occurrences(query, "\$type: MediaType"))
        assertEquals(1, occurrences(query, "type: \$type"))
    }

    @Test
    fun `the untyped query declares only the id`() {
        val query = GraphQLQueries.getMediaDetailQuery(null)

        assertTrue(query.contains("Media(id: \$id)"))
        assertFalse(
            "an untyped query must not reference \$type at all - it is not " +
                "declared, so referencing it is a 400:\n$query",
            query.contains("\$type")
        )
    }

    /**
     * Both documents are named differently, because AniList caches responses per
     * operation name and a same-named operation with different arguments is the
     * shape that serves one namespace's answer for the other's.
     */
    @Test
    fun `the two documents are named differently`() {
        val typed = GraphQLQueries.getMediaDetailQuery("ANIME")
        val untyped = GraphQLQueries.getMediaDetailQuery(null)

        assertTrue(typed.contains("query GetMediaDetailTyped("))
        assertTrue(untyped.contains("query GetMediaDetail("))
    }

    // ---- the field list, shared by both ------------------------------------------

    /**
     * These are the sections the Info page stopped drawing when the fetch resolved
     * to the wrong namespace, so their absence from the shared field list is the
     * bug rather than a cosmetic difference.
     */
    @Test
    fun `both forms carry the sections the page renders`() {
        val typed = GraphQLQueries.getMediaDetailQuery("MANGA")
        val untyped = GraphQLQueries.getMediaDetailQuery(null)

        listOf(typed, untyped).forEach { query ->
            assertTrue("characters missing:\n$query", query.contains("characters("))
            assertTrue("voiceActors missing:\n$query", query.contains("voiceActors("))
            assertTrue("staff missing:\n$query", query.contains("staff("))
            assertTrue("relations missing:\n$query", query.contains("relations {"))
            assertTrue("rankings missing:\n$query", query.contains("rankings {"))
            assertTrue("tags missing:\n$query", query.contains("tags {"))
        }
    }

    /**
     * Voice actors hang off the *edge*, not the character: AniList returns the same
     * character with a different actor per language, so only the edge can say "this
     * actor plays this character". Selecting `voiceActors` anywhere else is a
     * query that compiles and returns no pairing.
     */
    @Test
    fun `voice actors are selected on the character edge`() {
        val query = GraphQLQueries.getMediaDetailQuery("ANIME")

        val charactersBlock = query.substringAfter("characters(").substringBefore("staff(")
        assertTrue(
            "voiceActors must be an edge field, alongside `role`:\n$charactersBlock",
            charactersBlock.contains("voiceActors(") &&
                Regex("""role\s*\n\s*node \{""").containsMatchIn(charactersBlock)
        )
    }

    /**
     * The untyped form exists only for a caller that cannot know the namespace, and
     * it is the form that can render the wrong title. It must therefore carry
     * exactly the same fields as the typed one - a divergence here is how a section
     * ends up working for anime and silently missing for manga.
     *
     * The `Media(...)` line itself is excluded: that is the one line the two forms
     * are *meant* to differ on.
     */
    @Test
    fun `the two forms select the same fields`() {
        fun selections(query: String): List<String> =
            query.lines()
                .map { it.trim() }
                .dropWhile { !it.startsWith("Media(") }
                .drop(1)
                .filter { it.isNotEmpty() && it != "}" }

        assertEquals(
            "the typed and untyped detail documents must select the same fields; " +
                "a divergence is how a section works for anime and silently " +
                "disappears for manga",
            selections(GraphQLQueries.getMediaDetailQuery(null)),
            selections(GraphQLQueries.getMediaDetailQuery("ANIME"))
        )
    }

    /**
     * `type` is selected on the node itself so the caller can pass it to the *next*
     * open - which is what makes following a chain of related titles work at all.
     * Without it on the node, opening a relation would immediately lose the
     * namespace and the second hop would resolve untyped.
     */
    @Test
    fun `the node's own type is selected`() {
        val query = GraphQLQueries.getMediaDetailQuery("ANIME")

        assertTrue(
            "the detail node must carry its own `type`, or the next open is " +
                "untyped and walks into the same collision:\n$query",
            Regex("""\bMedia\(id:[^\)]*\) \{\s*\n\s*id\s*\n\s*type\b""").containsMatchIn(query)
        )
    }

    /**
     * `genres` is what the Genres chip card reads, and it was on the old field list.
     */
    @Test
    fun `genres are selected`() {
        val query = GraphQLQueries.getMediaDetailQuery("ANIME")

        assertTrue(query.contains("genres"))
    }

    /**
     * Braces and parens balanced, in both forms.
     *
     * The document is assembled from a shared field list and a conditional
     * `Media(...)` line, so an unbalanced field is a plausible edit - and the
     * symptom would be an AniList 400 on every single detail open, with nothing on
     * screen explaining it.
     */
    @Test
    fun `both documents are balanced`() {
        listOf(
            "typed" to GraphQLQueries.getMediaDetailQuery("MANGA"),
            "untyped" to GraphQLQueries.getMediaDetailQuery(null)
        ).forEach { (name, query) ->
            assertEquals("$name braces", query.count { it == '{' }, query.count { it == '}' })
            assertEquals("$name parens", query.count { it == '(' }, query.count { it == ')' })
        }
    }

    /**
     * Counted without regex: the needles contain `$`, which a raw-string regex
     * cannot express without escaping gymnastics that are themselves a way to
     * write a test that passes for the wrong reason.
     */
    private fun occurrences(haystack: String, needle: String): Int =
        haystack.split(needle).size - 1
}
