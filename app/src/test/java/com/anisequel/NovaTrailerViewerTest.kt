package com.anisequel

import com.anisequel.ui.components.detail.info.isNovaTrailerHost
import com.anisequel.ui.components.detail.info.isNovaTrailerId
import com.anisequel.ui.components.detail.info.novaTrailerEmbedHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one page in the app that runs with JavaScript on.
 *
 * AniList hands the app a YouTube **id** and nothing else, so playing a trailer
 * inside the app means an embed - and an embed only works in a `WebView` with
 * scripting enabled, because the player is a web app. That makes this the single
 * place where a remote payload could reach code execution, which is why the two
 * guards are asserted here rather than trusted:
 *
 *  1. **The id is validated, not escaped.** It is interpolated into markup, so an
 *     id carrying `"` or `<` would not be a bad video, it would be injected HTML.
 *     Anything outside YouTube's alphabet produces a wrapper with no frame at all.
 *  2. **Nothing but YouTube may be followed.** The client cancels every navigation
 *     and hands the URL to the external opener; the allowlist is what decides, and
 *     a lookalike host (`youtube.com.example.net`) must not pass it.
 *
 * The URL construction is a pure function for exactly this reason: there is no way
 * to assert that a `WebView` did the safe thing, only that it was given a safe page.
 */
class NovaTrailerViewerTest {

    @Test
    fun `the embed is a single nocookie frame with inline playback`() {
        val html = novaTrailerEmbedHtml("VQGCKyvzIM4")

        assertTrue(html.contains("https://www.youtube-nocookie.com/embed/VQGCKyvzIM4"))
        // Inline playback is what keeps the video inside the card instead of
        // handing the whole screen to the system player when the reader taps it.
        assertTrue(html.contains("playsinline=1"))
        assertTrue("the embed needs the secure origin to initialise YouTube's player API", html.contains("enablejsapi=1&origin=https%3A%2F%2Fwww.youtube.com"))
        assertTrue("the player needs a valid referrer for YouTube's embed checks", html.contains("referrerpolicy=\"origin\""))
        assertEquals(1, html.split("<iframe").size - 1)
    }

    @Test
    fun `an id outside YouTube's alphabet produces no frame`() {
        // `"` would close the src attribute, `<` would open an element.
        val html = novaTrailerEmbedHtml("""x" onload="alert(1)""")

        assertFalse("nothing may be interpolated into the page", html.contains("<iframe"))
        assertFalse(html.contains("alert(1)"))
    }

    @Test
    fun `only YouTube's alphabet is an id`() {
        assertTrue(isNovaTrailerId("VQGCKyvzIM4"))
        assertTrue(isNovaTrailerId("a-z_0"))
        assertFalse(isNovaTrailerId(""))
        assertFalse(isNovaTrailerId("id with space"))
        assertFalse(isNovaTrailerId("<script>"))
        assertFalse(isNovaTrailerId("x".repeat(21)))
    }

    @Test
    fun `the player follows YouTube and nothing else`() {
        assertTrue(isNovaTrailerHost("https://www.youtube.com/embed/x"))
        assertTrue(isNovaTrailerHost("https://www.youtube-nocookie.com/embed/x?autoplay=1"))
        assertTrue(isNovaTrailerHost("https://i.ytimg.com/vi/x/hqdefault.jpg"))
        assertTrue(isNovaTrailerHost("https://r4---sn-abc.googlevideo.com/videoplayback"))

        assertFalse(isNovaTrailerHost("https://example.com/youtube.com"))
        // A lookalike, which a `contains("youtube.com")` check would have allowed.
        assertFalse(isNovaTrailerHost("https://youtube.com.example.net/embed/x"))
        assertFalse(isNovaTrailerHost(""))
    }
}
