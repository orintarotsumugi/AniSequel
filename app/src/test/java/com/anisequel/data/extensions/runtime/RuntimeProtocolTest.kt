package com.anisequel.data.extensions.runtime

import com.anisequel.data.extensions.SourceAnime
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Pins the host/runtime wire format without a device. Both processes encode
 * through this object, so a drift here is a silent mis-parse across the binder;
 * the round-trips and the error envelope are what keep the two ends honest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeProtocolTest {

    @Test
    fun `a successful envelope unwraps to its body`() {
        val payload = RuntimeProtocol.envelope { JSONObject().put("value", 42) }
        assertEquals(42, RuntimeProtocol.unwrap(payload).getInt("value"))
    }

    @Test
    fun `a failed envelope throws with the runtime's message`() {
        val payload = RuntimeProtocol.envelope { throw IOException("source exploded") }
        try {
            RuntimeProtocol.unwrap(payload)
            fail("expected the error envelope to throw")
        } catch (error: IOException) {
            assertEquals("source exploded", error.message)
        }
    }

    @Test
    fun `anime survives a round trip with nulls`() {
        val anime = SourceAnime(id = "/a", title = "T", coverUrl = null, description = "d", year = 2020, score = null)
        val back = RuntimeProtocol.animeFrom(RuntimeProtocol.animeToJson(anime))
        assertEquals(anime, back)
        assertNull(back.coverUrl)
        assertNull(back.score)
    }

    @Test
    fun `server headers survive a round trip`() {
        val server = SourceServer(
            id = "file", name = "1080p", url = "https://x/file", quality = "1080p",
            headers = mapOf("Referer" to "https://x/", "X-Token" to "abc")
        )
        val back = RuntimeProtocol.serverFrom(RuntimeProtocol.serverToJson(server))
        assertEquals(server, back)
    }

    @Test
    fun `episode with servers survives a round trip`() {
        val episode = SourceEpisode(
            id = "/e", name = "Ep", number = 4, thumbnailUrl = "thumb",
            servers = listOf(SourceServer("s", "S", "https://x/s", "720p")),
            durationSeconds = 1_400L
        )
        val back = RuntimeProtocol.episodeFrom(RuntimeProtocol.episodeToJson(episode))
        assertEquals(episode, back)
    }

    @Test
    fun `episode lists survive a round trip`() {
        val episodes = listOf(
            SourceEpisode("a", "A", 1, null, emptyList()),
            SourceEpisode("b", "B", 2, null, emptyList())
        )
        assertEquals(episodes, RuntimeProtocol.episodesFrom(RuntimeProtocol.episodesToJson(episodes)))
    }

    @Test
    fun `load request and result survive a round trip`() {
        val request = RuntimeProtocol.loadRequest("com.ext", listOf("com.ext.Source"), nsfw = true, hostUid = 10_000)
        val parsed = RuntimeProtocol.loadRequestFrom(JSONObject(request))
        assertEquals("com.ext", parsed.packageName)
        assertEquals(listOf("com.ext.Source"), parsed.sourceClasses)
        assertTrue(parsed.nsfw)
        assertEquals(10_000, parsed.hostUid)

        val sources = listOf(RuntimeSourceInfo("com.ext:1", "Source", "en", false))
        assertEquals(sources, RuntimeProtocol.loadResultFrom(RuntimeProtocol.loadResult(sources)))
    }

    @Test
    fun `a fetch request carries method, headers and body`() {
        val request = RuntimeFetchRequest(
            url = "https://x/api",
            method = "POST",
            headers = listOf("Content-Type" to "application/json"),
            body = byteArrayOf(1, 2, 3)
        )
        val back = RuntimeProtocol.fetchRequestFrom(RuntimeProtocol.fetchRequest(request))
        assertEquals(request.url, back.url)
        assertEquals(request.method, back.method)
        assertEquals(request.headers, back.headers)
        assertArrayEquals(request.body, back.body)
    }

    @Test
    fun `a fetch response carries status, final url and body`() {
        val payload = RuntimeProtocol.envelope {
            RuntimeProtocol.fetchResponseObject(
                200, "OK", "https://x/final", listOf("Content-Type" to "video/mp4"), byteArrayOf(7, 8)
            )
        }
        val back = RuntimeProtocol.fetchResponseFrom(RuntimeProtocol.unwrap(payload))
        assertEquals(200, back.code)
        assertEquals("https://x/final", back.url)
        assertEquals(listOf("Content-Type" to "video/mp4"), back.headers)
        assertArrayEquals(byteArrayOf(7, 8), back.body)
    }
}