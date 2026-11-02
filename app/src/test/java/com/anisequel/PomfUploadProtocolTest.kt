package com.anisequel

import com.anisequel.data.network.PomfApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fallback host's answer, and what may be trusted from it.
 *
 * pomf answers a JSON document rather than catbox's bare-text URL, and its file
 * domain (`pomf2.lain.la`) is not its uploader's (`pomf.lain.la`), so the success
 * test is on the parsed host rather than a string prefix. Everything here exists
 * because the value that comes out is written into a public comment as an image:
 * a CDN interstitial or a `success: false` body must never become the embed.
 */
class PomfUploadProtocolTest {

    private val api = PomfApi()

    @Test
    fun `a served url is on the host's own domain over https`() {
        assertTrue(PomfApi.isServedUrl("https://pomf2.lain.la/f/qvyicht9.png"))
        assertFalse("plain http is not embeddable", PomfApi.isServedUrl("http://pomf2.lain.la/f/a.png"))
        assertFalse(
            "a host that merely ends with the same letters is not the host",
            PomfApi.isServedUrl("https://notlain.la/f/a.png")
        )
        assertFalse(
            "a lookalike subdomain is a different site",
            PomfApi.isServedUrl("https://pomf2.lain.la.evil.example/a.png")
        )
        assertFalse("the site root stores no file", PomfApi.isServedUrl("https://pomf2.lain.la/"))
        assertFalse(
            "two urls glued together",
            PomfApi.isServedUrl("https://pomf2.lain.la/a.png https://pomf2.lain.la/b.png")
        )
    }

    @Test
    fun `an upload answer carries the stored file's url`() {
        val body = """
            {
                "success": true,
                "files": [
                    {
                        "hash": "c530c06cf89c410c0355d7852644a73fc3ec8c04",
                        "name": "anisequel-upload-test.png",
                        "url": "https://pomf2.lain.la/f/qvyicht9.png",
                        "size": 67
                    }
                ]
            }
        """.trimIndent()

        assertEquals(
            "https://pomf2.lain.la/f/qvyicht9.png",
            api.parseResponse(body).getOrNull()
        )
    }

    @Test
    fun `a refusal is not an image, however it is worded`() {
        assertTrue(api.parseResponse("""{"success":false}""").isFailure)
        // The host answers in JSON, and a JSON document is not a sentence the
        // composer can show: the reader is owed a reason in words, not a brace.
        assertEquals(
            "The image host did not return a usable URL.",
            api.parseResponse("""{"success":false}""").exceptionOrNull()?.message
        )
        assertEquals(
            "an error field the host wrote is the reason",
            api.parseResponse("""{"success":false,"error":"file too large"}""")
                .exceptionOrNull()?.message
        )
        assertTrue(
            "success with no file is not a URL",
            api.parseResponse("""{"success":true,"files":[]}""").isFailure
        )
        assertTrue(
            "a file URL off the host is refused",
            api.parseResponse(
                """{"success":true,"files":[{"url":"https://evil.example/a.png"}]}"""
            ).isFailure
        )
        assertTrue("an HTML page is not JSON", api.parseResponse("<!DOCTYPE html><html>").isFailure)
    }
}
