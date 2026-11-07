package com.anisequel

import com.anisequel.data.network.GiphyResponse
import com.anisequel.data.network.GifukaiGifs
import com.anisequel.data.network.GifukaiResponse
import com.anisequel.data.network.KlipyResponse
import com.anisequel.data.network.NekosResponse
import com.anisequel.data.network.NetworkClient
import com.anisequel.data.network.gifSearchApiFor
import com.anisequel.data.network.toGifPage
import com.anisequel.data.network.toGifResult
import com.anisequel.domain.gif.GifProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `a chosen provider without its key falls back or answers on the bundled one`() {
        // Klipy still answers on the app's key when the reader has none: the
        // default moved to Gifukai, but Klipy's bundled key and its replacement
        // slot in Settings stayed. A keyed provider with no key (Tenor, GIPHY)
        // falls to the neutral tag index, not to another general index.
        assertEquals(
            "Gifukai",
            gifSearchApiFor(GifProvider.GIFUKAI, null, null, null).providerName
        )
        assertEquals(
            "Klipy",
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
    }

    @Test
    fun `each provider reads only its own key`() {
        assertEquals("t", GifProvider.TENOR.keyFrom("t", "k", "g"))
        assertEquals("k", GifProvider.KLIPY.keyFrom("t", "k", "g"))
        assertEquals("g", GifProvider.GIPHY.keyFrom("t", "k", "g"))
        assertNull("a keyless index has no credential to look for", GifProvider.SAFEBOORU.keyFrom("t", "k", "g"))
        assertNull("Gifukai is keyless", GifProvider.GIFUKAI.keyFrom("t", "k", "g"))
        assertNull("nekos.best is keyless", GifProvider.NEKOS_BEST.keyFrom("t", "k", "g"))
        assertTrue(GifProvider.selectable.first().needsKey.not())
        // The default must be an index that answers on its own and asks the reader
        // for nothing: a shipped choice that refused until registration is the trap
        // a retired Tenor set. Gifukai is keyless and safe by construction; Klipy
        // (bundled key, general catalogue) is still a keyless-feeling answer and
        // still selectable beside it.
        assertEquals(GifProvider.GIFUKAI, GifProvider.DEFAULT)
        assertFalse(GifProvider.GIFUKAI.needsKey)
        assertFalse("a reader key is optional while the app carries one", GifProvider.KLIPY.needsKey)
        assertEquals(GifProvider.GIFUKAI, GifProvider.fromStorage("gifukai"))
        assertEquals(GifProvider.KLIPY, GifProvider.fromStorage("klipy"))
        assertNull(GifProvider.fromStorage("not-a-provider"))
    }

    @Test
    fun `gifukai reads the anime and the action and exposes the file as both copies`() {
        val json = """
            {"action":"hug","pairing":"mf","anime":"Blue Spring Ride",
             "url":"https://cdn.gifukai.com/hug/x.gif",
             "filename":"x.gif","content_type":"image/gif","size_bytes":1393045}
        """.trimIndent()
        val parsed = NetworkClient.json.adapter(GifukaiResponse::class.java).fromJson(json)!!
        val gif = parsed.toGifResult()!!

        assertEquals("https://cdn.gifukai.com/hug/x.gif", gif.embedUrl)
        assertEquals("the same file is the tile: there is no smaller copy", gif.gridUrl, gif.embedUrl)
        assertEquals("gifukai names the anime, which Tenor's index never does", "Blue Spring Ride - Hug", gif.title)
        assertNull("dimensions are not in the response; the tile crops to a square", gif.width)
    }

    @Test
    fun `gifukai resolves reader words to actions and keeps a kiss type`() {
        assertEquals("hug" to null, GifukaiGifs.resolve("hug"))
        assertEquals("cry" to null, GifukaiGifs.resolve("crying"))
        assertEquals("cry" to null, GifukaiGifs.resolve("sob"))
        assertEquals("cry" to null, GifukaiGifs.resolve("sad crying"))
        assertEquals("laugh" to null, GifukaiGifs.resolve("lmao"))
        assertEquals("kiss" to "cheek", GifukaiGifs.resolve("peck"))
        assertEquals("kiss" to "forehead", GifukaiGifs.resolve("kiss forehead"))
        assertEquals("kiss" to null, GifukaiGifs.resolve("kiss"))
        assertNull("a sentence it cannot answer is refused, not answered with a wrong GIF", GifukaiGifs.resolve("naruto running"))
        assertNull(GifukaiGifs.resolve("   "))
    }
}
