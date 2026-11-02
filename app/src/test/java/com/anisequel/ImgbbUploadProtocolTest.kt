package com.anisequel

import com.anisequel.data.network.ImgbbApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The third host's answer, and the token its uploader is behind.
 *
 * imgbb is not the documented API but the form behind the website, so two things
 * are pinned here that a change on their side can break: the homepage still has
 * to hand out a token in the pattern the scrape expects, and a refusal - which
 * also arrives as HTTP 200 - must never become the image written into a comment.
 */
class ImgbbUploadProtocolTest {

    private val api = ImgbbApi()

    @Test
    fun `a served url is on the host's own file domain over https`() {
        assertTrue(ImgbbApi.isServedUrl("https://i.ibb.co/9k2p1f/photo.png"))
        assertFalse("plain http is not embeddable", ImgbbApi.isServedUrl("http://i.ibb.co/9k2p1f/a.png"))
        assertFalse(
            "the site that serves pages is not the domain that serves files",
            ImgbbApi.isServedUrl("https://imgbb.com/9k2p1f/a.png")
        )
        assertFalse(
            "a lookalike subdomain is a different site",
            ImgbbApi.isServedUrl("https://i.ibb.co.evil.example/a.png")
        )
        assertFalse("the site root stores no file", ImgbbApi.isServedUrl("https://i.ibb.co/"))
        assertFalse(
            "two urls glued together",
            ImgbbApi.isServedUrl("https://i.ibb.co/a.png https://i.ibb.co/b.png")
        )
    }

    @Test
    fun `an upload answer carries the stored file's url`() {
        val body = """
            {
                "status_code": 200,
                "success": true,
                "status_txt": "OK",
                "image": {
                    "url": "https://i.ibb.co/9k2p1f/photo.png",
                    "display_url": "https://i.ibb.co/9k2p1f/photo.png"
                }
            }
        """.trimIndent()

        assertEquals(
            "https://i.ibb.co/9k2p1f/photo.png",
            api.parseResponse(body).getOrNull()
        )
    }

    @Test
    fun `a refusal is not an image, however it is worded`() {
        // The host answers in JSON, and a JSON document is not a sentence the
        // composer can show: the reader is owed a reason in words, not a brace.
        assertEquals(
            "The image host did not return a usable URL.",
            api.parseResponse("""{"status_code":400,"success":false}""")
                .exceptionOrNull()?.message
        )
        assertEquals(
            "an error object the host wrote is the reason",
            api.parseResponse(
                """{"status_code":400,"success":false,"error":{"message":"Image is too large","code":313}}"""
            ).exceptionOrNull()?.message
        )
        assertEquals(
            "a status line is a reason too",
            api.parseResponse("""{"status_code":403,"status_txt":"Forbidden"}""")
                .exceptionOrNull()?.message
        )
        assertTrue(
            "success with no image is not a URL",
            api.parseResponse("""{"status_code":200,"success":true}""").isFailure
        )
        assertTrue(
            "a file URL off the host is refused",
            api.parseResponse(
                """{"status_code":200,"success":true,"image":{"url":"https://evil.example/a.png"}}"""
            ).isFailure
        )
        assertTrue(
            "a status that is not 200 is a refusal even when it carries a URL",
            api.parseResponse(
                """{"status_code":400,"image":{"url":"https://i.ibb.co/a.png"}}"""
            ).isFailure
        )
        assertTrue("an HTML page is not JSON", api.parseResponse("<!DOCTYPE html><html>").isFailure)
    }

    @Test
    fun `the upload token is read from the homepage`() {
        val html = """<script>var auth_token = "1a2b3c4d5e6f";</script>"""
        assertEquals("1a2b3c4d5e6f", ImgbbApi.authToken(html))
        assertEquals(
            "the page has written it without spaces before",
            "deadbeef",
            ImgbbApi.authToken("""var auth_token="deadbeef";""")
        )
        assertNull("a page with no token is not a crash", ImgbbApi.authToken("<html></html>"))
        assertNull("nor is an empty page", ImgbbApi.authToken(""))
    }
}
