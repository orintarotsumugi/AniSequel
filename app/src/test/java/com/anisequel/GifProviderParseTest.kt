package com.anisequel

import com.anisequel.data.network.GiphyResponse
import com.anisequel.data.network.KlipyResponse
import com.anisequel.data.network.KonachanPost
import com.anisequel.data.network.NekosResponse
import com.anisequel.data.network.NetworkClient
import com.anisequel.data.network.booruNextCursor
import com.anisequel.data.network.booruPhraseTag
import com.anisequel.data.network.booruTags
import com.anisequel.data.network.gifSearchApiFor
import com.anisequel.data.network.konachanRequestTags
import com.anisequel.data.network.toGifPage
import com.anisequel.domain.gif.GifProvider
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three new GIF indexes, flattened from their own JSON.
 *
 * Pin the field names and the envelopes rather than the network: a renamed
 * `has_next`, a `file.hd.gif.url` that moved, or GIPHY's string dimensions coming
 * back as numbers all produce the same symptom - an empty grid that looks like a
 * search miss - and none of them can be told apart from one another without a
 * parser test. [GifSearchParseTest] already pins the two providers that shipped
 * first; this is the same contract for the ones added beside them.
 */
class GifProviderParseTest {

    @Test
    fun `klipy reads the double envelope and the small copy for the grid`() {
        val json = """
            {"result":true,"data":{
              "data":[
                {"id":123,"title":"Hug","file":{
                  "hd":{"gif":{"url":"https://x/hd.gif","width":480,"height":270}},
                  "sm":{"gif":{"url":"https://x/sm.gif","width":220,"height":124}}
                }},
                {"id":124,"title":"No gif","file":{"hd":{"webp":{"url":"https://x/a.webp"}}}}
              ],
              "current_page":1,"per_page":24,"has_next":true
            }}
        """.trimIndent()
        val parsed = NetworkClient.json.adapter(KlipyResponse::class.java).fromJson(json)!!
        val page = parsed.toGifPage(fallbackPage = 1)

        assertEquals("the full copy is what a comment carries", "https://x/hd.gif", page.results.single().embedUrl)
        assertEquals("the grid must not download the full animation", "https://x/sm.gif", page.results.single().gridUrl)
        assertEquals(480, page.results.single().width)
        assertEquals(270, page.results.single().height)
        assertEquals("has_next is the only honest end signal", "2", page.next)
    }

    @Test
    fun `klipy falls back through the smaller renditions when hd is missing`() {
        val json = """
            {"result":true,"data":{"data":[
              {"id":1,"file":{"md":{"gif":{"url":"https://x/md.gif","width":320,"height":180}}}}
            ],"current_page":2,"has_next":false}}
        """.trimIndent()
        val page = NetworkClient.json.adapter(KlipyResponse::class.java).fromJson(json)!!
            .toGifPage(fallbackPage = 2)

        assertEquals("https://x/md.gif", page.results.single().embedUrl)
        assertNull("a last page must not ask for another", page.next)
    }

    @Test
    fun `giphy dimensions arrive as strings and still size the tile`() {
        val json = """
            {"data":[{"id":"abc","title":"Wave","images":{
              "original":{"url":"https://x/o.gif","width":"480","height":"270"},
              "fixed_width":{"url":"https://x/w.gif","width":"200","height":"112"}
            }}],"pagination":{"offset":0,"total_count":48,"count":1},
            "meta":{"status":200,"msg":"OK"}}
        """.trimIndent()
        val parsed = NetworkClient.json.adapter(GiphyResponse::class.java).fromJson(json)!!
        val page = parsed.toGifPage(fallbackOffset = 0)

        assertEquals("https://x/o.gif", page.results.single().embedUrl)
        assertEquals("the fixed-width copy is the tile", "https://x/w.gif", page.results.single().gridUrl)
        assertEquals("a string width has to become an Int", 480, page.results.single().width)
        assertEquals(270, page.results.single().height)
        assertEquals("the next page is the offset plus the count", "1", page.next)
        assertEquals(48, page.totalCount)
    }

    @Test
    fun `giphy stops at the last page rather than asking past the offset cap`() {
        val json = """
            {"data":[{"id":"abc","images":{"original":{"url":"https://x/o.gif"}}}],
             "pagination":{"offset":4999,"total_count":100000,"count":24},"meta":{"status":200}}
        """.trimIndent()
        val page = NetworkClient.json.adapter(GiphyResponse::class.java).fromJson(json)!!
            .toGifPage(fallbackOffset = 4999)

        assertNull("GIPHY refuses an offset past 4999", page.next)
    }

    @Test
    fun `nekos best keeps the file url as both copies and has no next page`() {
        val json = """
            {"results":[{"url":"https://x/hug.gif","anime_name":"Hug",
              "dimensions":{"width":480,"height":270}}]}
        """.trimIndent()
        val parsed = NetworkClient.json.adapter(NekosResponse::class.java).fromJson(json)!!
        val page = parsed.toGifPage()

        assertEquals("https://x/hug.gif", page.results.single().gridUrl)
        assertEquals("https://x/hug.gif", page.results.single().embedUrl)
        assertEquals("the series is the nearest thing to a title", "Hug", page.results.single().title)
        assertNull("the API has no cursor", page.next)
    }

    /**
     * Konachan answers a *bare array* of posts, so the test reads it the way the
     * client does: one parameterized adapter, no envelope to unwrap.
     */
    private fun konachanPage(json: String) = NetworkClient.json
        .adapter<List<KonachanPost?>>(Types.newParameterizedType(List::class.java, KonachanPost::class.java))
        .fromJson(json)!!
        .toGifPage()

    @Test
    fun `konachan embeds the board gif and tiles it with the preview still`() {
        // Shaped from a live row: file_url is a 1920x1080 gif whose path carries the
        // tag list percent-encoded, sample_url is the SAME file, and the preview is a
        // 150px jpg. Choosing the sample because it is named "sample" is the failure
        // booruTileUrl documents, so the tile has to be the preview.
        val json = """
            [{"id":309459,"file_ext":"gif","rating":"s","width":1920,"height":1080,
              "file_url":"https://konachan.net/image/0fe/Konachan.com%20-%20309459%20animated%20hatsune_miku.gif",
              "sample_url":"https://konachan.net/image/0fe/Konachan.com%20-%20309459%20animated%20hatsune_miku.gif",
              "preview_url":"https://konachan.net/data/preview/0f/e5/0fe599e.jpg",
              "tags":"animated blue_eyes hatsune_miku source:some_board third-party_edit twintails vocaloid"}]
        """.trimIndent()
        val row = konachanPage(json).results.single()

        assertEquals("the comment carries the moving file", true, row.embedUrl.endsWith(".gif"))
        assertEquals("the tile is the still, not a 1080p frame", "https://konachan.net/data/preview/0f/e5/0fe599e.jpg", row.gridUrl)
        assertEquals("309459", row.id)
        assertEquals(1920, row.width)
        assertEquals(
            "a namespace tag and the tag that made the hit are not captions, and an " +
                "underscore is not a word",
            "blue eyes hatsune miku third-party edit twintails",
            row.title
        )
    }

    @Test
    fun `konachan drops what is unsafe and what is not a gif`() {
        // Measured on one live page of `animated`: 23 rows were gif and one was a PNG
        // (an APNG). The picker promises GIFs and the rating is a request the board can
        // ignore, so both checks run on the way out.
        val json = """
            [
              {"id":1,"file_ext":"gif","rating":"s","file_url":"https://k/1.gif"},
              {"id":2,"file_ext":"png","rating":"s","file_url":"https://k/2.png"},
              {"id":3,"file_ext":"gif","rating":"q","file_url":"https://k/3.gif"},
              {"id":4,"file_ext":"gif","rating":"s","file_url":null},
              {"id":5,"file_ext":"gif","rating":"s","file_url":"//k/5.gif"},
              {"id":6,"rating":"s","file_url":"https://k/6.gif"},
              null
            ]
        """.trimIndent()

        assertEquals(
            "only the safe gif survives, and a row with no file_ext is read from its URL",
            listOf("1", "6"),
            konachanPage(json).results.map { it.id }
        )
    }

    @Test
    fun `konachan asks for animated files best-voted first`() {
        // Both words fail silently: without `animated` the board answers wallpapers and
        // the picker embeds a still as a GIF; without `order:score` it answers newest
        // uploads (measured scores 8, 28, 22 against 321, 273, 270).
        assertEquals(
            "animated order:score hatsune_miku",
            konachanRequestTags("hatsune_miku")
        )
        assertEquals(
            "the words tier is what a board actually gets",
            "animated order:score spice wolf",
            konachanRequestTags(booruTags("spice and wolf"))
        )
        assertEquals(
            "and the phrase tier is the one that can hit a series tag",
            "animated order:score spice_and_wolf",
            konachanRequestTags(booruPhraseTag("spice and wolf")!!)
        )
        assertTrue("a blank query adds no trailing space", konachanRequestTags("").startsWith("animated"))
    }

    @Test
    fun `konachan pages from the board count not the filtered one`() {
        // A full page of rows this app then drops is still a page with more behind it;
        // reading the filtered length as the end stopped the grid after one page.
        val full = (1..24).joinToString(",", "[", "]") {
            """{"id":$it,"file_ext":"png","rating":"s","file_url":"https://k/$it.png"}"""
        }
        val page = konachanPage(full)
        assertTrue("every row was filtered out", page.results.isEmpty())
        assertEquals(
            "twenty-four raw rows means ask for the next page",
            "2",
            booruNextCursor(24, 24, "1")
        )
        assertNull(booruNextCursor(23, 24, "1"))
    }

    @Test
    fun `a chosen provider without its key searches the anonymous index`() {
        assertEquals(
            "Safebooru",
            gifSearchApiFor(GifProvider.KLIPY, null, null, null).providerName
        )
        assertEquals(
            "Safebooru",
            gifSearchApiFor(GifProvider.TENOR, "  ", null, null).providerName
        )
        assertEquals(
            "Klipy",
            gifSearchApiFor(GifProvider.KLIPY, null, "k", null).providerName
        )
        assertEquals(
            "GIPHY",
            gifSearchApiFor(GifProvider.GIPHY, null, null, "g").providerName
        )
        assertEquals(
            "nekos.best",
            gifSearchApiFor(GifProvider.NEKOS_BEST, null, null, null).providerName
        )
        assertEquals(
            "a third keyless index answers for itself, never for a missing key",
            "Konachan",
            gifSearchApiFor(GifProvider.KONACHAN, null, null, null).providerName
        )
    }

    @Test
    fun `each provider reads only its own key`() {
        assertEquals("t", GifProvider.TENOR.keyFrom("t", "k", "g"))
        assertEquals("k", GifProvider.KLIPY.keyFrom("t", "k", "g"))
        assertEquals("g", GifProvider.GIPHY.keyFrom("t", "k", "g"))
        assertNull("a keyless index has no credential to look for", GifProvider.SAFEBOORU.keyFrom("t", "k", "g"))
        assertNull("as does the board", GifProvider.KONACHAN.keyFrom("t", "k", "g"))
        assertTrue(GifProvider.selectable.first().needsKey.not())
        assertEquals(GifProvider.SAFEBOORU, GifProvider.DEFAULT)
        assertEquals(GifProvider.KLIPY, GifProvider.fromStorage("klipy"))
        assertEquals(GifProvider.KONACHAN, GifProvider.fromStorage("konachan"))
        assertNull(GifProvider.fromStorage("not-a-provider"))
    }
}
