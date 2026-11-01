package com.anisequel

import com.anisequel.data.network.OpenverseResponse
import com.anisequel.data.network.TenorResponse
import com.anisequel.data.network.toGifPage
import com.anisequel.data.network.FlexibleStringAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
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
    fun `openverse answers with files, a thumbnail and a page count`() {
        val json = """
            {"result_count":240,"page_count":12,"page_size":20,"page":1,
             "results":[
               {"id":"a1","title":"Cat Fish","foreign_landing_url":"https://flickr/1",
                "url":"https://live.staticflickr.com/3313/3481540500_c846c62863_b.gif",
                "thumbnail":"https://live.staticflickr.com/3313/3481540500_q80.gif",
                "width":640,"height":480},
               {"id":"a2","title":"No file","url":null,"thumbnail":null}
             ]}
        """.trimIndent()
        val page = adapter<OpenverseResponse>().fromJson(json)!!.toGifPage(page = 1)

        assertEquals("a result with no file URL cannot be embedded", 1, page.results.size)
        val gif = page.results.single()
        assertEquals("a1", gif.id)
        assertTrue("the comment must carry the file itself", gif.embedUrl.endsWith("_b.gif"))
        assertEquals("https://live.staticflickr.com/3313/3481540500_q80.gif", gif.gridUrl)
        assertEquals(640, gif.width)
        assertEquals(240, page.totalCount)
        assertEquals("2", page.next)
    }

    @Test
    fun `openverse stops paging on its last page`() {
        val json = """{"result_count":20,"page_count":1,"results":[{"id":"a","url":"https://x/y.gif"}]}"""
        val page = adapter<OpenverseResponse>().fromJson(json)!!.toGifPage(page = 1)
        assertNull("page 1 of 1 has nothing after it", page.next)
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

        val gif = page.results.single()
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
        // The empty-result sentence belongs to Openverse only; a Tenor miss is a
        // miss, and telling the reader about the CC catalogue would be a guess about
        // an index that has no such hole.
        assertEquals(
            true,
            com.anisequel.data.network.OpenverseGifs().catalogueIsNarrow
        )
        assertEquals(
            false,
            com.anisequel.data.network.TenorGifs("key").catalogueIsNarrow
        )
    }
}
