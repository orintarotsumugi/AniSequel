package com.anisequel

import com.anisequel.ui.components.detail.info.NOVA_TRAILER_BASE_URL
import com.anisequel.ui.components.detail.info.NOVA_TRAILER_ORIGIN
import com.anisequel.ui.components.detail.info.isNovaTrailerHost
import com.anisequel.ui.components.detail.info.isNovaTrailerId
import com.anisequel.ui.components.detail.info.novaTrailerEmbedHtml
import com.anisequel.ui.components.detail.info.staysInNovaTrailer
import java.net.URLEncoder
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
 *  2. **The player may load only YouTube, and only in its own subframe.** The
 *     allowlist decides which hosts exist for the player at all, and a lookalike
 *     (`youtube.com.example.net`) must not pass it; the frame rule decides what
 *     happens to a link the reader chose, and a main-frame navigation must leave
 *     the app rather than replace the video with a watch page.
 *
 * The URL construction and the navigation rule are pure functions for exactly this
 * reason: there is no way to assert that a `WebView` did the safe thing, only that
 * it was given a safe page and a decision function that cannot wander.
 */
class NovaTrailerViewerTest {

    @Test
    fun `the embed is a single nocookie frame with inline playback`() {
        val html = novaTrailerEmbedHtml("VQGCKyvzIM4")

        assertTrue(html.contains("https://www.youtube-nocookie.com/embed/VQGCKyvzIM4"))
        // Inline playback is what keeps the video inside the card instead of
        // handing the whole screen to the system player when the reader taps it.
        assertTrue(html.contains("playsinline=1"))
        assertTrue("the player needs a valid referrer for YouTube's embed checks", html.contains("referrerpolicy=\"strict-origin-when-cross-origin\""))
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
    fun `the page identifies itself as the app, never as youtube`() {
        val html = novaTrailerEmbedHtml("VQGCKyvzIM4")
        val origin = URLEncoder.encode(NOVA_TRAILER_ORIGIN, Charsets.UTF_8.name())

        assertTrue(NOVA_TRAILER_ORIGIN.startsWith("https://"))
        assertFalse(
            "a page whose origin is youtube.com is read as YouTube embedding itself and refused with error 152",
            NOVA_TRAILER_ORIGIN.contains("youtube")
        )
        // The base URL is what WebView turns into the Referer header, so it has to
        // be the same origin the player is told about, or the API messages and the
        // embed check disagree.
        assertEquals("$NOVA_TRAILER_ORIGIN/", NOVA_TRAILER_BASE_URL)
        assertTrue(html.contains("enablejsapi=1&origin=$origin"))
        assertTrue("attribution must name the same origin", html.contains("widget_referrer=$origin"))
        assertFalse("the old youtube.com origin is exactly what YouTube refuses", html.contains("origin=https%3A%2F%2Fwww.youtube.com"))
    }

    @Test
    fun `a link the reader chose leaves the player`() {
        assertTrue(staysInNovaTrailer("https://www.youtube-nocookie.com/embed/x", isForMainFrame = false))
        assertFalse(
            "the watch page would replace the video with a page that has no back button",
            staysInNovaTrailer("https://www.youtube.com/watch?v=x", isForMainFrame = true)
        )
        assertFalse(staysInNovaTrailer("https://example.com/", isForMainFrame = false))
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
