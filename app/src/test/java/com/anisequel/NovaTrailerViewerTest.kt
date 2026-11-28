package com.anisequel

import com.anisequel.ui.components.detail.info.NOVA_TRAILER_BASE_URL
import com.anisequel.ui.components.detail.info.NOVA_TRAILER_ORIGIN
import com.anisequel.ui.components.detail.info.isNovaTrailerHost
import com.anisequel.ui.components.detail.info.isNovaTrailerId
import com.anisequel.ui.components.detail.info.isNovaTrailerWatchPage
import com.anisequel.ui.components.detail.info.isNovaTrailerWebUrl
import com.anisequel.ui.components.detail.info.novaTrailerEmbedHtml
import com.anisequel.ui.components.detail.info.staysInNovaTrailer
import java.io.File
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
    fun `native controls wait for YouTube and seek in five second steps`() {
        val html = novaTrailerEmbedHtml("VQGCKyvzIM4")

        // The IFrame API's onReady event is the gate for the Compose controls;
        // the wrapper's earlier iframe-load signal is not enough to send commands.
        assertTrue(html.contains("new YT.Player('p'"))
        assertTrue(html.contains("anisequel:trailer-player-ready"))
        assertTrue(html.contains("anisequel:trailer-state:"))

        // Both seek buttons share one bounded offset function, and playback
        // follows the real player's state rather than a local optimistic toggle.
        assertTrue(html.contains("p.getCurrentTime()+offset"))
        assertTrue(html.contains("Math.max(0,p.getCurrentTime()+offset)"))
        assertTrue(html.contains("if(duration>0){target=Math.min(duration,target);}"))
        assertTrue(html.contains("p.seekTo(target,true)"))
        assertTrue(html.contains("p.pauseVideo()"))
        assertTrue(html.contains("p.playVideo()"))
    }

    /**
     * A refused video must leave the frame, not sit under live controls.
     *
     * The IFrame API's `onError` is the only signal that YouTube refused the video; the
     * frame itself shows YouTube's own error card. The page has to forward the code, and
     * the viewer has to swap the frame for the unavailable panel when it arrives.
     */
    @Test
    fun `a refused video is reported and replaces the frame`() {
        val html = novaTrailerEmbedHtml("VQGCKyvzIM4")
        assertTrue(html.contains("onError:function(event){console.log('anisequel:trailer-error:'+event.data);}"))

        val viewer = File(root, PLAYER).readText()
        assertTrue(viewer.contains("if (playerError == null) {"))
        assertTrue(viewer.contains("NovaTrailerUnavailable(code = playerError"))
    }

    @Test
    fun `native trailer controls stay disabled until the player is ready`() {
        val source = File(root, PLAYER).readText()

        // All three controls use the same actual onReady gate. The pause glyph is
        // driven by onStateChange rather than assuming that a tap succeeded.
        assertEquals(2, Regex("enabled = playerReady").findAll(source).count())
        assertTrue(source.contains("onPlayerReady = { playerReady = true }"))
        assertTrue(source.contains("onPlaybackStateChanged = { playbackState = it }"))
        assertTrue(
            source.contains("isPlaying = playbackState == 1") &&
                source.contains("imageVector = if (isPlaying) AppVectorIcons.Pause else AppVectorIcons.Play")
        )
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
        // The reported button is a `target="_blank"` link and this view has no popup
        // window, so the navigation happens inside the iframe: the frame test alone
        // followed it and the watch page replaced the trailer one layer down.
        assertFalse(
            "a target=_blank link arrives as a subframe navigation, not a main-frame one",
            staysInNovaTrailer("https://www.youtube.com/watch?v=x", isForMainFrame = false)
        )
        assertFalse(staysInNovaTrailer("https://example.com/", isForMainFrame = false))
        assertFalse(staysInNovaTrailer("https://m.youtube.com/watch?v=x&t=4", isForMainFrame = false))
        assertTrue(isNovaTrailerWatchPage("https://www.youtube.com/watch?v=x"))
        assertFalse("the embed is the player, not a watch page", isNovaTrailerWatchPage("https://www.youtube-nocookie.com/embed/x"))
    }

    @Test
    fun `a navigation that is not a web page is not a link`() {
        assertTrue(isNovaTrailerWebUrl("https://www.youtube.com/watch?v=x"))
        assertTrue(isNovaTrailerWebUrl("HTTP://example.com/"))
        // The player's own ad and measurement frames: never handed to a browser,
        // and never copied to the clipboard as a "link".
        assertFalse(isNovaTrailerWebUrl("about:blank"))
        assertFalse(isNovaTrailerWebUrl("data:text/html,<p></p>"))
        assertFalse(isNovaTrailerWebUrl("blob:https://www.youtube-nocookie.com/1"))
        assertFalse(isNovaTrailerWebUrl(""))
    }

    @Test
    fun `the player follows YouTube and nothing else`() {
        assertTrue(isNovaTrailerHost("https://www.youtube.com/embed/x"))
        assertTrue(isNovaTrailerHost("https://www.youtube-nocookie.com/embed/x?autoplay=1"))
        assertTrue(isNovaTrailerHost("https://i.ytimg.com/vi/x/hqdefault.jpg"))
        assertTrue(isNovaTrailerHost("https://r4---sn-abc.googlevideo.com/videoplayback"))

        // The authority with a port is still the host; matching it whole made the
        // player's own frame look like a stranger and handed it to a browser.
        assertTrue(isNovaTrailerHost("https://www.youtube.com:443/embed/x"))
        assertTrue(isNovaTrailerHost("https://user@www.youtube.com/embed/x"))

        assertFalse(isNovaTrailerHost("https://example.com/youtube.com"))
        // A lookalike, which a `contains("youtube.com")` check would have allowed.
        assertFalse(isNovaTrailerHost("https://youtube.com.example.net/embed/x"))
        assertFalse(isNovaTrailerHost(""))
    }

    /**
     * The player must not be left to the platform's default compositing.
     *
     * The report was "the audio is working but nothing is showing": the trailer
     * played - sound, the player's chrome, the title - over a black rectangle. This is
     * a video surface inside a floating dialog window, and without a layer of its own
     * the video plane is dropped while the rest of the page draws normally. A hardware
     * layer carries the whole output, video included, as one texture; a software layer
     * cannot hold a video surface at all, which is the same symptom from the other
     * side. Neither can be seen from a JVM test, so the call itself is pinned.
     */
    @Test
    fun `the player asks for a hardware layer`() {
        val text = File(root, EMBED).readText()
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .replace(Regex("//[^\\n]*"), "")

        assertTrue(
            "the player is composited by the platform default, so the video surface " +
                "can be dropped while the audio plays",
            text.contains("setLayerType(View.LAYER_TYPE_HARDWARE, null)")
        )
        assertFalse(
            "a software layer cannot hold a video surface: the audio plays and the " +
                "frame stays black",
            text.contains("LAYER_TYPE_SOFTWARE")
        )
    }

    private val root: File = findProjectRoot()

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }

    private companion object {
        const val PLAYER =
            "app/src/main/java/com/anisequel/ui/components/detail/info/NovaTrailerViewer.kt"
        const val EMBED =
            "app/src/main/java/com/anisequel/ui/components/detail/info/NovaTrailerEmbed.kt"
    }
}
