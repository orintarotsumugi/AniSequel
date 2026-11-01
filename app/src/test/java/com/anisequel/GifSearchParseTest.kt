package com.anisequel

import com.anisequel.data.network.SafebooruPost
import com.anisequel.data.network.TenorResponse
import com.anisequel.data.network.booruNextCursor
import com.anisequel.data.network.booruTags
import com.anisequel.data.network.booruTileUrl
import com.anisequel.data.network.fitsTileBudget
import com.anisequel.data.network.isNoMatchesBody
import com.anisequel.data.network.isSafeRating
import com.anisequel.data.network.toGifPage
import com.anisequel.data.network.FlexibleStringAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two GIF providers' response shapes, mapped without a socket.
 *
 * Both are third-party APIs whose field names this app does not control, and a
 * rename shows up as an empty grid - which is indistinguishable from "no results"
 * on screen and impossible to diagnose from a bug report. These are the shapes each
 * one answers with today, pinned.
 */
class GifSearchParseTest {

    private val moshi = Moshi.Builder().add(String::class.java, FlexibleStringAdapter).build()

    private inline fun <reified T : Any> adapter() = moshi.adapter(T::class.java)

    @Test
    fun `a booru post list becomes tiles and embeds, and an unsafe rating is dropped`() {
        // The rating strings are what the site actually answers with. It spells them
        // out (`general`, `safe`) rather than using the one-letter form the first
        // version of this test fed it, and comparing against `"g"` dropped every post
        // - an empty grid over a search that had just returned twenty-four.
        val posts = listOf(
            SafebooruPost(
                id = 7283385,
                fileUrl = "https://safebooru.org/images/855/holo.gif",
                sampleUrl = "https://safebooru.org/images/855/holo_sample.gif",
                previewUrl = "https://safebooru.org/thumbnails/855/holo.jpg",
                rating = "general",
                width = 500,
                height = 500,
                tags = "holo_(spice_and_wolf) animated_gif"
            ),
            // A post whose rating field disagrees with the query's filter. The
            // tag is a request to the site, not a guarantee, and this app puts the
            // result into a comment other people read.
            SafebooruPost(id = 1, fileUrl = "https://safebooru.org/images/9/q.gif", rating = "questionable"),
            SafebooruPost(id = 2, fileUrl = "https://safebooru.org/images/9/e.gif", rating = "explicit"),
            // The other spelling the site uses for the same thing.
            SafebooruPost(
                id = 3,
                fileUrl = "https://safebooru.org/images/856/other.gif",
                sampleUrl = "https://safebooru.org/images/856/other_sample.jpg",
                previewUrl = "https://safebooru.org/thumbnails/856/other.jpg",
                rating = "safe",
                width = 400,
                height = 300
            ),
            null
        )
        val page = posts.toGifPage()
        assertEquals(2, page.results.size)
        val gif = page.results.first()
        assertEquals("7283385", gif.id)
        assertEquals("https://safebooru.org/images/855/holo.gif", gif.embedUrl)
        // The tile is the animated sample, because that is the copy that both moves
        // and costs the grid least.
        assertEquals("https://safebooru.org/images/855/holo_sample.gif", gif.gridUrl)
        assertEquals("holo (spice and wolf) animated gif", gif.title)
    }

    @Test
    fun `a tile is animated when the animation is tile-sized and a still when it is not`() {
        // An animated drawable decodes at its intrinsic frame size however small the
        // cell is, so the original GIF is only used for a tile while it is a size the
        // grid can hold several of. Past that the tile is the JPEG still, and the
        // comment still embeds the full animation.
        val animatedSample = SafebooruPost(
            fileUrl = "https://safebooru.org/images/1/a.gif",
            sampleUrl = "https://safebooru.org/images/1/a_sample.gif",
            width = 900, height = 400
        )
        assertEquals(
            "https://safebooru.org/images/1/a_sample.gif",
            booruTileUrl(animatedSample)
        )

        val stillSampleSmallGif = SafebooruPost(
            fileUrl = "https://safebooru.org/images/1/b.gif",
            sampleUrl = "https://safebooru.org/images/1/b_sample.jpg",
            previewUrl = "https://safebooru.org/thumbnails/1/b.jpg",
            width = 500, height = 500
        )
        assertEquals(
            "the tile moves rather than showing a frozen frame",
            "https://safebooru.org/images/1/b.gif",
            booruTileUrl(stillSampleSmallGif)
        )

        val posterSizedGif = SafebooruPost(
            fileUrl = "https://safebooru.org/images/1/c.gif",
            sampleUrl = "https://safebooru.org/images/1/c_sample.jpg",
            previewUrl = "https://safebooru.org/thumbnails/1/c.jpg",
            width = 5200, height = 5200
        )
        assertEquals(
            "a 27-megapixel frame is not going into a 104dp cell",
            "https://safebooru.org/images/1/c_sample.jpg",
            booruTileUrl(posterSizedGif)
        )

        val dimensionsUnknown = SafebooruPost(
            fileUrl = "https://safebooru.org/images/1/d.gif",
            previewUrl = "https://safebooru.org/thumbnails/1/d.jpg"
        )
        assertEquals(
            "an answer this app cannot size is treated as too big",
            "https://safebooru.org/thumbnails/1/d.jpg",
            booruTileUrl(dimensionsUnknown)
        )
    }

    @Test
    fun `the tile budget caps both the area and the longest edge`() {
        assertTrue(fitsTileBudget(1000, 1000))
        assertFalse(fitsTileBudget(2000, 700))
        // 160k pixels, but 4000 wide: the area alone would let this through.
        assertFalse(fitsTileBudget(4000, 40))
        assertFalse("an unknown size is not assumed small", fitsTileBudget(null, 500))
        assertFalse(fitsTileBudget(0, 0))
    }

    @Test
    fun `the site's rating spellings all read, and only the unsafe ones are dropped`() {
        listOf("general", "safe", "g", "s", "GENERAL").forEach {
            assertTrue("'$it' is a safe rating", isSafeRating(it))
        }
        listOf("questionable", "explicit", "q", "r").forEach {
            assertFalse("'$it' is not", isSafeRating(it))
        }
        assertTrue(isSafeRating(null))
    }

    @Test
    fun `an empty answer is no matches, not a corrupt response`() {
        // Safebooru replies HTTP 200 with *nothing at all* for a tag combination it
        // cannot resolve. That reached the parser, which failed, and the picker blamed
        // the provider for a search that had simply found nothing.
        assertTrue(isNoMatchesBody(""))
        assertTrue(isNoMatchesBody("\n  \n"))
        assertTrue(isNoMatchesBody("[]"))
        assertFalse(isNoMatchesBody("[{\"id\":1}]"))
        assertFalse(isNoMatchesBody("<html>oops</html>"))
    }

    @Test
    fun `the next page is decided by the server's page, not by what survived filtering`() {
        // Twenty-four posts arrive, the rating check drops twenty of them, and the
        // site still has rows behind this page. Reading the short filtered list as a
        // short page stopped a scrollable search after its first page.
        assertEquals("1", booruNextCursor(rawPostCount = 24, requestedLimit = 24, cursor = null))
        assertEquals("2", booruNextCursor(rawPostCount = 24, requestedLimit = 24, cursor = "1"))
        assertNull(booruNextCursor(rawPostCount = 4, requestedLimit = 24, cursor = null))
        assertEquals("1", booruNextCursor(rawPostCount = 24, requestedLimit = 24, cursor = "not-a-number"))
    }

    @Test
    fun `a search phrase becomes booru tags without losing tags the reader typed`() {
        // Stop words go, because `and` is never part of a tag and a phrase that
        // carries it matches nothing. The rest is passed through as separate tags -
        // the site ANDs them - and nothing here tries to know that "spice and wolf"
        // is one tag, which would need the site's tag list to guess.
        assertEquals("spice wolf holo", booruTags("spice and wolf holo"))
        assertEquals("holo_(spice_and_wolf) apple", booruTags("holo_(spice_and_wolf) apple"))
        assertEquals("", booruTags("   "))
    }

    @Test
    fun `tenor gives one gif per format and the picker takes the embed and the tile`() {
        val json = """
            {"results":[{"id":"9314","title":"happy dance",
              "media_formats":{
                "gif":{"url":"https://media.tenor.com/happy.gif","dims":[220,120],"size":104513},
                "tinygif":{"url":"https://media.tenor.com/happy_tiny.gif","dims":[220,120]},
                "previewgif":{"url":"https://media.tenor.com/happy_preview.gif"}
              }}],
             "next":"10000000000000000000"}
        """.trimIndent()
        val page = adapter<TenorResponse>().fromJson(json)!!.toGifPage()

        val gif = page.results.first()
        assertEquals("https://media.tenor.com/happy.gif", gif.embedUrl)
        assertEquals("https://media.tenor.com/happy_tiny.gif", gif.gridUrl)
        assertEquals(220, gif.width)
        assertEquals(120, gif.height)
        assertEquals("10000000000000000000", page.next)
    }

    @Test
    fun `a tenor result with no gif url is dropped rather than embedded as a blank`() {
        val json = """{"results":[{"id":"x","title":"no media","media_formats":{}}],"next":"2"}"""
        val page = adapter<TenorResponse>().fromJson(json)!!.toGifPage()
        assertTrue(page.results.isEmpty())
    }

    @Test
    fun `the recent strip keeps one copy of a pick and drops the oldest`() {
        val store = com.anisequel.ui.viewmodel.GifSearchStore(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            provider = com.anisequel.data.network.SafebooruGifs()
        )
        (1..14).forEach { store.rememberUsed("https://x/$it.gif") }
        assertEquals(
            "capped at the sheet's own constant",
            com.anisequel.ui.viewmodel.GifSearchStore.MAX_USED,
            store.usedThisSession().size
        )
        assertEquals("https://x/14.gif", store.usedThisSession().first())
        store.rememberUsed("https://x/9.gif")
        assertEquals("a repeat moves to the front rather than doubling", 1, store.usedThisSession().count { it == "https://x/9.gif" })
        assertEquals("https://x/9.gif", store.usedThisSession().first())
    }

    @Test
    fun `the tag index gets the hint about matching tags and the keyed index does not`() {
        // Safebooru matches tags rather than sentences, so an empty answer is usually
        // a gap in its catalogue and the reader needs the "try fewer words" sentence.
        // This was `false`, which sent every booru miss to the flat "Nothing found"
        // line written for Tenor - so a search for `spice and wolf holo` looked like a
        // broken picker rather than a three-tag AND.
        assertTrue(com.anisequel.data.network.SafebooruGifs().catalogueIsNarrow)
        // A keyed index like Tenor's has no such hole, so a miss there is a miss.
        assertFalse(com.anisequel.data.network.TenorGifs("key").catalogueIsNarrow)
    }
}
