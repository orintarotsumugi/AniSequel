package com.anisequel

import com.anisequel.data.network.CatboxApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How catbox answers an upload, and what may be trusted from that answer.
 *
 * The API replies with the file's URL as a plain-text body, and replies with a
 * refusal the same way - HTTP 200 and prose. So the whole success test is whether
 * the body is a URL on the host that serves the files, and that test decides
 * whether a comment gets an image or a sentence.
 */
class CatboxUploadProtocolTest {

    @Test
    fun `a served url is a success`() {
        assertTrue(CatboxApi.isServedUrl("https://files.catbox.moe/9k2p1f.gif"))
        // A body with anything after the URL is not a URL. `upload` trims the
        // response before asking, which is where a host's trailing newline is
        // dealt with; the check itself stays strict so a padded or doubled body
        // cannot become the address written into a comment.
        assertFalse(CatboxApi.isServedUrl("https://files.catbox.moe/abc123.png\n"))
    }

    @Test
    fun `a refusal is not a url however it is worded`() {
        assertFalse(CatboxApi.isServedUrl("Invalid file type."))
        assertFalse(CatboxApi.isServedUrl("https://catbox.moe/user/api.php"))
        assertFalse(CatboxApi.isServedUrl("https://files.catbox.moe/"))
        assertFalse("an empty body is not a stored file", CatboxApi.isServedUrl(""))
        // A URL that carries whitespace is two responses glued together, or an HTML
        // page that happens to start with the host. Neither can go in a comment.
        assertFalse(CatboxApi.isServedUrl("https://files.catbox.moe/a.gif https://files.catbox.moe/b.gif"))
        // And a host that merely starts with the same letters is not the host.
        assertFalse(CatboxApi.isServedUrl("https://files.catbox.moe.evil.example/a.gif"))
    }
}
