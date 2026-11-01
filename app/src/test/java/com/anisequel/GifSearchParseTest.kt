package com.anisequel

import com.anisequel.data.network.SafebooruPost
import com.anisequel.data.network.TenorResponse
import com.anisequel.data.network.booruTags
import com.anisequel.data.network.toGifPage
import com.anisequel.data.network.FlexibleStringAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `a booru post list becomes tiles and embeds, and a non-general rating is dropped`() {
        val posts = listOf(
            SafebooruPost(
                id = 7283385,
                fileUrl = "https://safebooru.org/images/855/holo.gif",
                sampleUrl = "https://safebooru.org/images/855/holo_sample.gif",
                previewUrl = "https://safebooru.org/thumbnails/855/holo.jpg",
                rating = "g",
                width = 500,
                height = 500,
                tags = "holo_(spice_and_wolf) animated_gif"
            ),
            // A post whose rating field disagrees with the query's filter. The
            // tag is a request to the site, not a guarantee, and this app puts the
            // result into a comment other people read.
            SafebooruPost(id = 1, fileUrl = "https://safebooru.org/images/9/q.gif", rating = "q"),
            // No sample: the tile falls back to the thumbnail rather than being
            // dropped, because a tile that is missing is worse than a small one.
            SafebooruPost(
                id = 2,
                fileUrl = "https://safebooru.org/images/856/other.gif",
                previewUrl = "https://safebooru.org/thumbnails/856/other.jpg",
                rating = "g"
            ),
            null
        )
        val page = posts.toGifPage()
        assertEquals(2, page.results.size)
        val gif = page.results.first()
        assertEquals("7283385", gif.id)
        assertEquals("https://safebooru.org/images/855/holo.gif", gif.embedUrl)
        // The tile is the *sample*, which is what the picker can afford to animate;
        // the thumbnail is only reached when a post has no sample.
        assertEquals("https://safebooru.org/images/855/holo_sample.gif", gif.gridUrl)
        assertEquals("holo (spice and wolf) animated gif", gif.title)
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
    fun `an anonymous provider is allowed to have a narrow catalogue`() {
        // The tag-search hint belongs to the booru only; a Tenor miss is a
        // miss, and telling the reader about the CC catalogue would be a guess about
        // an index that has no such hole.
        assertFalse(com.anisequel.data.network.SafebooruGifs().catalogueIsNarrow)
        assertFalse(com.anisequel.data.network.TenorGifs("key").catalogueIsNarrow)
    }
}
