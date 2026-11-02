package com.anisequel

import com.anisequel.data.network.GifPage
import com.anisequel.data.network.GifResult
import com.anisequel.data.network.GifSearchApi
import com.anisequel.data.network.PHRASE_CURSOR
import com.anisequel.data.network.TenorResponse
import com.anisequel.data.network.booruCursor
import com.anisequel.data.network.booruPhraseTag
import com.anisequel.data.network.booruTags
import com.anisequel.data.network.toGifPage
import com.anisequel.ui.viewmodel.GifSearchStore
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GifSearchRelevanceTest {

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
        assertEquals(false to 0, booruCursor(null))
        assertEquals(false to 2, booruCursor("2"))
        assertEquals(true to 3, booruCursor("${PHRASE_CURSOR}3"))
        assertEquals(true to 0, booruCursor("${PHRASE_CURSOR}junk"))
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
        val store = GifSearchStore(backgroundScope, provider)

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
}
