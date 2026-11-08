package com.anisequel

import com.anisequel.data.network.BUNDLED_KLIPY_KEY
import com.anisequel.data.network.GifPage
import com.anisequel.data.network.GifResult
import com.anisequel.data.network.GifSearchApi
import com.anisequel.data.network.GifSearchFailure
import com.anisequel.data.network.GifukaiGifs
import com.anisequel.data.network.KlipyGifs
import com.anisequel.data.network.gatherGifukaiScenes
import com.anisequel.data.network.gifSearchApiFor
import com.anisequel.domain.gif.GifProvider
import com.anisequel.ui.viewmodel.GifSearchStore
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gifukai's search as a grid, and its name hand-off.
 *
 * The endpoint answers one random scene per ask, so a search gathers several
 * draws and drops the repeats - a grid showing the same scene twice reads as a
 * broken picker, and an empty grid over a refused host is the silent failure
 * this app does not do. Both rules are pinned here on a fake draw, and the HTTP
 * shape (twelve parallel asks, the kiss `type`, no network on a refused name)
 * on an interceptor that never opens a socket.
 *
 * The hand-off is the other half: a query that names no action goes to the
 * wired name index and every answer - tiles, a miss, a refusal - has to say
 * which index gave it, because the placeholder still names Gifukai.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GifukaiSearchTest {

    private fun scene(id: String) = GifResult(
        id = id,
        embedUrl = "https://cdn.gifukai.com/hug/$id.gif",
        gridUrl = "https://cdn.gifukai.com/hug/$id.gif",
        title = "Hug",
        width = null,
        height = null
    )

    @Test
    fun `a batch caps at the grid's worth of scenes`() = runTest {
        var draws = 0
        val result = gatherGifukaiScenes(drawCount = 12, wanted = 9) {
            draws += 1
            Result.success(scene("$draws"))
        }
        assertEquals(12, draws)
        assertEquals("nine tiles, not twelve", 9, result.getOrThrow().size)
    }

    @Test
    fun `a batch drops the scenes the folder repeated`() = runTest {
        var draws = 0
        val result = gatherGifukaiScenes(drawCount = 12, wanted = 9) {
            draws += 1
            Result.success(scene("${draws % 5}"))
        }
        val scenes = result.getOrThrow()
        assertEquals("a five-scene folder is five tiles, not twelve", 5, scenes.size)
        assertEquals(scenes.size, scenes.map { it.id }.distinct().size)
    }

    @Test
    fun `a failed draw does not sink the scenes that arrived`() = runTest {
        var draws = 0
        val result = gatherGifukaiScenes(drawCount = 6, wanted = 9) {
            draws += 1
            if (draws % 2 == 0) Result.failure(GifSearchFailure("draw $draws failed"))
            else Result.success(scene("$draws"))
        }
        assertEquals(3, result.getOrThrow().size)
    }

    @Test
    fun `a batch where every draw failed reports the first failure`() = runTest {
        var draws = 0
        val result = gatherGifukaiScenes(drawCount = 3, wanted = 9) {
            draws += 1
            Result.failure(GifSearchFailure("failure $draws"))
        }
        assertEquals("failure 1", result.exceptionOrNull()?.message)
    }

    @Test
    fun `cancellation propagates instead of becoming a failed draw`() = runTest {
        val outcome = runCatching {
            gatherGifukaiScenes(drawCount = 2, wanted = 2) {
                throw CancellationException("the search moved on")
            }
        }
        assertTrue(
            "a cancelled search must stay cancelled, not become an error grid",
            outcome.exceptionOrNull() is CancellationException
        )
    }

    /** A name index that records what it was asked, without a socket. */
    private class RecordingNameIndex(
        private val answer: (String, String?) -> Result<GifPage>
    ) : GifSearchApi {
        override val providerName: String get() = "Klipy"
        val calls = mutableListOf<Pair<String, String?>>()

        override suspend fun search(query: String, cursor: String?): Result<GifPage> {
            calls += query to cursor
            return answer(query, cursor)
        }
    }

    @Test
    fun `a name is handed to the name index and the answer says so`() = runTest {
        val index = RecordingNameIndex { _, _ ->
            Result.success(GifPage(listOf(scene("frieren")), next = "2"))
        }
        val page = GifukaiGifs(nameSearch = index).search("frieren", null).getOrThrow()

        assertEquals(listOf("frieren" to null), index.calls)
        assertEquals("the hand-off keeps the name index's own cursor", "2", page.next)
        assertTrue("the answer must name the index that gave it: ${page.note}", page.note!!.contains("Klipy"))
        assertTrue(page.note!!.contains("Gifukai"))
    }

    @Test
    fun `a hand-off page keeps paging the name index`() = runTest {
        val index = RecordingNameIndex { _, _ -> Result.success(GifPage(listOf(scene("p2")))) }
        GifukaiGifs(nameSearch = index).search("frieren", "2").getOrThrow()
        assertEquals(listOf("frieren" to "2"), index.calls)
    }

    @Test
    fun `an empty hand-off says where to take the name next`() = runTest {
        val index = RecordingNameIndex { _, _ -> Result.success(GifPage(emptyList())) }
        val page = GifukaiGifs(nameSearch = index).search("frieren", null).getOrThrow()
        assertTrue("an empty hand-off names the query: ${page.note}", page.note!!.contains("frieren"))
        assertTrue(
            "the miss belongs to the index that answered, not the recommendation: ${page.note}",
            page.note!!.contains("Klipy took this search")
        )
        assertTrue("and names the tag index that can still answer: ${page.note}", page.note!!.contains("Safebooru"))
    }

    @Test
    fun `a failed hand-off says which index took the search`() = runTest {
        val index = RecordingNameIndex { _, _ ->
            Result.failure(GifSearchFailure("Klipy is limiting requests right now."))
        }
        val error = GifukaiGifs(nameSearch = index).search("frieren", null).exceptionOrNull()!!
        assertTrue(error.message!!.contains("Klipy took this search"))
        assertTrue(error.message!!.contains("Klipy is limiting requests right now."))
    }

    @Test
    fun `without a hand-off a name is refused without a request`() = runTest {
        val error = GifukaiGifs().search("frieren", null).exceptionOrNull()!!
        assertTrue(error.message!!.contains("reaction"))
    }

    @Test
    fun `the factory wires Klipy on the bundled key as the hand-off`() {
        val api = gifSearchApiFor(GifProvider.GIFUKAI, null, null, null) as GifukaiGifs
        val handoff = api.nameSearchInUse as KlipyGifs
        assertEquals(BUNDLED_KLIPY_KEY, handoff.keyInUse)

        val withReaderKey = gifSearchApiFor(GifProvider.GIFUKAI, null, "reader-key", null) as GifukaiGifs
        assertEquals(
            "a saved Klipy key wins for the hand-off too",
            "reader-key",
            (withReaderKey.nameSearchInUse as KlipyGifs).keyInUse
        )
    }

    /** An OkHttp client whose interceptor answers locally - no socket in a JVM test. */
    private class SceneClient(private val sceneUrl: (Int) -> String) : Interceptor {
        val requests = ConcurrentLinkedQueue<Request>()
        private val counter = AtomicInteger()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            val url = sceneUrl(counter.incrementAndGet())
            val body = """
                {"action":"hug","anime":"Blue Spring Ride","url":"$url",
                 "content_type":"image/gif","size_bytes":1000}
            """.trimIndent()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("ok")
                .body(body.toResponseBody())
                .build()
        }

        fun client(): OkHttpClient = OkHttpClient.Builder().addInterceptor(this).build()
    }

    @Test
    fun `an action search gathers a grid without touching the name index`() = runTest {
        // Seven distinct scenes behind twelve draws: the repeats are dropped.
        val http = SceneClient { "https://cdn.gifukai.com/hug/${it % 7}.gif" }
        val index = RecordingNameIndex { _, _ -> error("an action word must not reach the name index") }
        val page = GifukaiGifs(client = http.client(), nameSearch = index).search("hug", null).getOrThrow()

        assertEquals(12, http.requests.size)
        assertEquals(7, page.results.size)
        assertEquals(page.results.size, page.results.map { it.id }.distinct().size)
        assertNull("a random draw has no next page", page.next)
        assertNull("Gifukai's own answer needs no hand-off note", page.note)
        assertTrue(index.calls.isEmpty())
        assertTrue(http.requests.all { it.url.encodedPath == "/v1/hug" })
    }

    @Test
    fun `a typed kiss still carries its folder to the endpoint`() = runTest {
        val http = SceneClient { "https://cdn.gifukai.com/kiss/$it.gif" }
        GifukaiGifs(client = http.client()).search("kiss forehead", null).getOrThrow()
        assertTrue(http.requests.isNotEmpty())
        assertTrue(http.requests.all { it.url.queryParameter("type") == "forehead" })
    }

    @Test
    fun `a pairing named in the query reaches the endpoint`() = runTest {
        val http = SceneClient { "https://cdn.gifukai.com/hug/$it.gif" }
        GifukaiGifs(client = http.client()).search("two girls hug", null).getOrThrow()
        assertTrue(http.requests.isNotEmpty())
        assertTrue(
            "a two-girl search must ask for the ff pairing: " +
                http.requests.map { it.url.queryParameter("pairing") },
            http.requests.all { it.url.queryParameter("pairing") == "ff" }
        )
        assertNull("no folder was named", http.requests.first().url.queryParameter("type"))
    }

    @Test
    fun `a pairing and a kiss folder travel together`() = runTest {
        val http = SceneClient { "https://cdn.gifukai.com/kiss/$it.gif" }
        GifukaiGifs(client = http.client())
            .search("boy and girl kiss on the cheek", null)
            .getOrThrow()
        assertTrue(http.requests.isNotEmpty())
        assertTrue(http.requests.all { it.url.encodedPath == "/v1/kiss" })
        assertTrue(http.requests.all { it.url.queryParameter("pairing") == "mf" })
        assertTrue(http.requests.all { it.url.queryParameter("type") == "cheek" })
    }

    @Test
    fun `a cursor on an action answer asks nothing`() = runTest {
        val http = SceneClient { "https://cdn.gifukai.com/hug/$it.gif" }
        val page = GifukaiGifs(client = http.client()).search("hug", "2").getOrThrow()
        assertTrue(page.results.isEmpty())
        assertTrue("a catalogue with no pages must not be asked for one", http.requests.isEmpty())
    }

    @Test
    fun `the store shows the page's note over a full grid`() = runTest {
        val provider = object : GifSearchApi {
            override val rerollable: Boolean get() = true
            override suspend fun search(query: String, cursor: String?): Result<GifPage> =
                Result.success(GifPage(listOf(scene("a")), note = "Klipy took this search."))
        }
        val store = GifSearchStore(this, provider)
        store.setQuery("frieren")
        advanceUntilIdle()

        assertEquals("Klipy took this search.", store.state.value.note)
        assertTrue(store.rerollable)
    }
}