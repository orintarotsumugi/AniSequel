package com.anisequel

import com.anisequel.data.network.GifPage
import com.anisequel.data.network.GifResult
import com.anisequel.data.network.GifSearchApi
import com.anisequel.data.network.PHRASE_CURSOR
import com.anisequel.data.network.PREFIX_CURSOR
import com.anisequel.data.network.TenorResponse
import com.anisequel.data.network.booruCursor
import com.anisequel.data.network.booruPhraseTag
import com.anisequel.data.network.booruPrefixTag
import com.anisequel.data.network.booruTags
import com.anisequel.data.network.booruTiers
import com.anisequel.data.network.tierAnswers
import com.anisequel.data.network.toGifPage
import com.anisequel.ui.viewmodel.GifSearchStore
import java.io.IOException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GifSearchRelevanceTest {

    private val sampleResult =
        GifResult("1", "https://x/a.gif", "https://x/a.gif", null, 10, 10)
    private val ioError = IOException("refused")

    @Test
    fun `a series name becomes one tag with its stop words kept`() {
        // Live: `animated_gif rating:safe spice wolf` answers nothing, while
        // `spice_and_wolf` answers the series. Dropping "and" is right for the
        // word-by-word search and wrong for the phrase.
        assertEquals("spice_and_wolf", booruPhraseTag("Spice and  Wolf"))
        assertNull("one word is already one tag", booruPhraseTag("holo"))
        assertNull(booruPhraseTag("   "))
    }

    @Test
    fun `booru tags ignore the case the reader typed`() {
        assertEquals("holo crying", booruTags("Holo Crying"))
    }

    @Test
    fun `a phrase page keeps paging the phrase rather than the words`() {
        assertEquals(0 to 0, booruCursor(null))
        assertEquals(0 to 2, booruCursor("2"))
        assertEquals(1 to 3, booruCursor("${PHRASE_CURSOR}3"))
        assertEquals(1 to 0, booruCursor("${PHRASE_CURSOR}junk"))
        // The prefix tier's cursor must not read as the phrase's: paging `kaguya*`
        // asked the phrase tag for its third page instead.
        assertEquals(2 to 3, booruCursor("${PREFIX_CURSOR}3"))
        assertEquals(2 to 0, booruCursor("${PREFIX_CURSOR}junk"))
    }

    @Test
    fun `a phrase is asked three ways and a single word only once`() {
        // The series case: `spice wolf` matches no post (the site ANDs the words)
        // and the reader's own words are not the tag, so the phrase and then the
        // prefix are what can find it.
        val tiers = booruTiers("Spice and Wolf")
        assertEquals(
            listOf("spice wolf" to "", "spice_and_wolf" to PHRASE_CURSOR, "spice*" to PREFIX_CURSOR),
            tiers.map { it.tagString to it.cursorPrefix }
        )
        // A one-word search already is a tag. `holo*` is a wider and worse question,
        // so it is not asked.
        assertEquals(
            listOf("holo" to ""),
            booruTiers("holo").map { it.tagString to it.cursorPrefix }
        )
        assertTrue("a blank query asks nothing", booruTiers("   ").isEmpty())
    }

    @Test
    fun `a two-letter first word is not a usable prefix`() {
        // `pu el`-style queries would prefix-match half the index.
        assertNull(booruPrefixTag("pi kawaii"))
        assertEquals("kaguya*", booruPrefixTag("Kaguya sama"))
        assertNull("one word is never a prefix tier", booruPrefixTag("holo"))
    }

    @Test
    fun `a full page that filtered to nothing is not a miss`() {
        // The server filled the limit and this app dropped every row for its rating:
        // there are more rows behind it, so the words tier keeps paging rather than
        // handing the search to the prefix spelling.
        val more = Result.success(GifPage(emptyList(), next = "1"))
        assertTrue(tierAnswers(more))
        assertTrue(tierAnswers(Result.success(GifPage(listOf(sampleResult)))))
        assertFalse("a genuine empty page falls through", tierAnswers(Result.success(GifPage(emptyList()))))
        assertTrue("a refusal is an answer, not a miss", tierAnswers(Result.failure(ioError)))
    }

    @Test
    fun `a blank tenor cursor ends the results instead of restarting them`() {
        // `pos=` with nothing after it is the first page: the grid appended page
        // one under itself when the reader reached the end.
        val json = """
            {"results":[{"id":"1","media_formats":{"gif":{"url":"https://media.tenor.com/a.gif"}}}],
             "next":""}
        """.trimIndent()
        val page = Moshi.Builder().build().adapter(TenorResponse::class.java).fromJson(json)!!.toGifPage()
        assertEquals(1, page.results.size)
        assertNull(page.next)
    }

    private class CountingProvider : GifSearchApi {
        val queries = mutableListOf<String>()
        override suspend fun search(query: String, cursor: String?): Result<GifPage> {
            queries += query
            return Result.success(
                GifPage(listOf(GifResult(query, "https://x/$query.gif", "https://x/$query.gif", null, 10, 10)))
            )
        }
    }

    @Test
    fun `a query already answered is not sent again`() = runTest {
        val provider = CountingProvider()
        // The test's own scope, not `backgroundScope`: idle-advancing skips
        // background work, so the debounced search never ran.
        val store = GifSearchStore(this, provider)

        store.setQuery("crying")
        advanceUntilIdle()
        store.setQuery("cryin")
        advanceUntilIdle()
        store.setQuery("Crying ")
        runCurrent()

        assertEquals(listOf("crying", "cryin"), provider.queries)
        assertFalse(store.state.value.isSearching)
        assertEquals("crying", store.state.value.results.single().id)
        assertTrue(GifSearchStore.cacheKey(" Holo  Crying ") == "holo crying")
    }

    /** A first page that answers, and a second page that refuses exactly once. */
    private class RefusingSecondPage : GifSearchApi {
        var calls = 0
        private var refused = false

        override suspend fun search(query: String, cursor: String?): Result<GifPage> {
            calls += 1
            if (cursor == null) {
                return Result.success(
                    GifPage(listOf(GifResult("1", "https://x/1.gif", "https://x/1.gif", null, 10, 10)), next = "2")
                )
            }
            if (!refused) {
                refused = true
                return Result.failure(IOException("the next page refused"))
            }
            return Result.success(
                GifPage(listOf(GifResult("2", "https://x/2.gif", "https://x/2.gif", null, 10, 10)))
            )
        }
    }

    @Test
    fun `a failed page waits for a tap instead of re-firing at the scroll position`() = runTest {
        val provider = RefusingSecondPage()
        val store = GifSearchStore(this, provider)
        store.setQuery("holo")
        advanceUntilIdle()
        assertEquals("2", store.state.value.next)

        store.loadMore()
        advanceUntilIdle()
        assertEquals(2, provider.calls)
        assertNotNull("the failed page says so", store.state.value.error)
        assertEquals("the tiles already drawn stay readable", 1, store.state.value.results.size)

        // The grid's end-of-list trigger asks again the moment the loading flag
        // drops, for as long as the reader is parked at the bottom. While the
        // error stands that ask must not reach the provider - it used to loop.
        store.loadMore()
        advanceUntilIdle()
        assertEquals("a failed page is not re-asked on its own", 2, provider.calls)

        store.retryPage()
        advanceUntilIdle()
        assertEquals("the explicit retry is what asks again", 3, provider.calls)
        assertNull("a recovered page clears the error", store.state.value.error)
        assertEquals(2, store.state.value.results.size)
    }

    @Test
    fun `answered searches become chips, newest first and one per word`() = runTest {
        val store = GifSearchStore(this, CountingProvider())

        store.setQuery("holo")
        advanceUntilIdle()
        store.setQuery("kaguya")
        advanceUntilIdle()
        // The same search in a different shape is one chip, in its newest spelling.
        store.setQuery("Holo ")
        advanceUntilIdle()

        assertEquals(listOf("Holo", "kaguya"), store.recentQueries.value)

        // Clearing the field is when the chips are drawn, so a reset keeps them.
        store.reset()
        assertEquals(listOf("Holo", "kaguya"), store.recentQueries.value)
    }

    @Test
    fun `a search that found nothing is not offered as a chip`() = runTest {
        val provider = object : GifSearchApi {
            override suspend fun search(query: String, cursor: String?): Result<GifPage> =
                Result.success(GifPage(emptyList()))
        }
        val store = GifSearchStore(this, provider)
        store.setQuery("nothing")
        advanceUntilIdle()
        assertTrue(
            "a chip that re-runs an empty search reads as a broken picker",
            store.recentQueries.value.isEmpty()
        )
    }

    @Test
    fun `the chip row keeps a sheet's worth of searches`() = runTest {
        val store = GifSearchStore(this, CountingProvider())
        for (i in 1..9) {
            store.setQuery("word$i")
            advanceUntilIdle()
        }
        assertEquals(GifSearchStore.MAX_RECENT_QUERIES, store.recentQueries.value.size)
        assertEquals("word9", store.recentQueries.value.first())
        assertFalse("the oldest search fell off", store.recentQueries.value.contains("word1"))
    }
}
