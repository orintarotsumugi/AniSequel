package com.example

import com.example.ui.components.markdown.mediaPreviewIdsIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards which AniList links are treated as media.
 *
 * The failure this replaces is not "no cover appears" - it is *the wrong cover
 * appears*. An AniList URL names six different things in six different id
 * namespaces, so `anilist.co/studio/128893` resolved through a `Media(id:)` query
 * returns the anime 128893 and puts an unrelated poster on a studio card. Nothing
 * about that looks broken; it looks correct.
 *
 * The pattern also accepts a slug where an id goes (`anilist.co/anime/one-piece`),
 * which is not numeric. Anything that reads it as an integer asks for 0.
 */
class MarkdownMediaPreviewTest {

    @Test
    fun `an anime link yields its media id`() {
        assertEquals(
            listOf(128893),
            mediaPreviewIdsIn("https://anilist.co/anime/128893")
        )
    }

    @Test
    fun `a manga link yields its media id`() {
        assertEquals(
            listOf(30013),
            mediaPreviewIdsIn("https://anilist.co/manga/30013")
        )
    }

    @Test
    fun `a non-media link is never resolved as media`() {
        // Same numeric id, different namespace. Resolving these would put the
        // anime 128893's poster on a character, a studio and a user card.
        listOf(
            "https://anilist.co/character/128893",
            "https://anilist.co/staff/128893",
            "https://anilist.co/studio/128893",
            "https://anilist.co/user/128893"
        ).forEach { url ->
            assertTrue("$url must not be resolved as media", mediaPreviewIdsIn(url).isEmpty())
        }
    }

    @Test
    fun `a slug is not asked for as a number`() {
        assertTrue(
            "a slug id is not numeric and would be requested as 0",
            mediaPreviewIdsIn("https://anilist.co/anime/one-piece").isEmpty()
        )
    }

    @Test
    fun `a bio's links are all collected, in order, without duplicates`() {
        val bio = """
            **Favourites**

            https://anilist.co/anime/128893
            https://anilist.co/manga/30013
            https://anilist.co/anime/128893
            https://anilist.co/studio/1
            https://anilist.co/anime/1535
        """.trimIndent()

        // "Twice" must not become two ids to fetch; the studio must not appear.
        assertEquals(listOf(128893, 30013, 1535), mediaPreviewIdsIn(bio))
    }

    @Test
    fun `a bio with no anilist links asks for nothing`() {
        assertTrue(
            mediaPreviewIdsIn("Just a person who watches a lot of anime and writes about it.")
                .isEmpty()
        )
    }

    @Test
    fun `zero is not a valid media id`() {
        assertTrue(mediaPreviewIdsIn("https://anilist.co/anime/0").isEmpty())
    }

    /**
     * The half that makes a card show a *cover*.
     *
     * This collector drives the single batched request, so a link it cannot see is
     * a card with a title and a permanently empty poster frame. The shape that
     * matters is the labelled link inside a bold run every "hall of fame" bio
     * uses - not the bare-URL line this already handled.
     */
    @Test
    fun `a labelled link inside a bold run still yields its id`() {
        val bio = "**✨ [#1] [Hell's Paradise](https://anilist.co/anime/128893)**\n\n*Loyal to it.*"

        assertEquals(listOf(128893), mediaPreviewIdsIn(bio))
    }
}