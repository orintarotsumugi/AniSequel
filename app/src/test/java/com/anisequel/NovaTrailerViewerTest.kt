package com.anisequel

import com.anisequel.ui.components.detail.info.NOVA_TRAILER_BASE_URL
import com.anisequel.ui.components.detail.info.NOVA_TRAILER_CODE_NO_PLAYER
import com.anisequel.ui.components.detail.info.NOVA_TRAILER_CODE_TIMEOUT
import com.anisequel.ui.components.detail.info.NOVA_TRAILER_ORIGIN
import com.anisequel.ui.components.detail.info.NOVA_TRAILER_PAUSE_JS
import com.anisequel.ui.components.detail.info.NovaTrailerPhase
import com.anisequel.ui.components.detail.info.NovaTrailerSession
import com.anisequel.ui.components.detail.info.NovaTrailerSignal
import com.anisequel.ui.components.detail.info.formatNovaTrailerTime
import com.anisequel.ui.components.detail.info.isNovaTrailerHost
import com.anisequel.ui.components.detail.info.isNovaTrailerId
import com.anisequel.ui.components.detail.info.isNovaTrailerRetryable
import com.anisequel.ui.components.detail.info.isNovaTrailerWatchPage
import com.anisequel.ui.components.detail.info.isNovaTrailerWebUrl
import com.anisequel.ui.components.detail.info.novaTrailerEmbedHtml
import com.anisequel.ui.components.detail.info.novaTrailerFailureReason
import com.anisequel.ui.components.detail.info.parseNovaTrailerSignal
import com.anisequel.ui.components.detail.info.parseNovaTrailerSnapshot
import com.anisequel.ui.components.detail.info.staysInNovaTrailer
import java.io.File
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The one page in the app that runs with JavaScript on, and the protocol it speaks.
 *
 * AniList hands the app a YouTube **id** and nothing else, so playing a trailer inside
 * the app means an embed - and an embed only works in a `WebView` with scripting enabled,
 * because the player is a web app. That makes this the single place where a remote
 * payload could reach code execution, which is why the guards are asserted here rather
 * than trusted:
 *
 *  1. **The id is validated, not escaped.** It is interpolated into markup, so an id
 *     carrying a quote or an angle bracket would not be a bad video, it would be injected
 *     HTML. Anything outside YouTube's alphabet produces a wrapper with no frame at all.
 *  2. **The player may load only YouTube, and only in its own subframe.** The allowlist
 *     decides which hosts exist for the player at all, and a lookalike
 *     (`youtube.com.example.net`) must not pass it; the frame rule decides what happens
 *     to a link the reader chose, and a main-frame navigation must leave the app rather
 *     than replace the video with a watch page.
 *  3. **Nothing the page says is trusted.** Every line it prints is parsed by
 *     [parseNovaTrailerSignal] and every number it returns by
 *     [parseNovaTrailerSnapshot]; anything else is dropped, so a page that starts printing
 *     something else cannot put the viewer into a state it does not have.
 */
class NovaTrailerViewerTest {

    @Test
    fun `the embed is a single nocookie frame with inline playback`() {
        val html = novaTrailerEmbedHtml("VQGCKyvzIM4")

        assertTrue(html.contains("https://www.youtube-nocookie.com/embed/VQGCKyvzIM4"))
        // Inline playback is what keeps the video inside the frame instead of handing
        // the whole screen to the system player when the reader taps it.
        assertTrue(html.contains("playsinline=1"))
        assertTrue(
            "the player needs a valid referrer for YouTube's embed checks",
            html.contains("referrerpolicy=\"strict-origin-when-cross-origin\"")
        )
        assertEquals(1, html.split("<iframe").size - 1)
        // The frame is sized by the wrapper and the wrapper by the view: a fixed pixel
        // width here would letterbox the video on every screen that is not that width.
        assertTrue(html.contains("iframe{display:block;width:100%;height:100%;border:0;}"))
    }

    /**
     * The protocol, end to end, because both halves are strings that can drift apart.
     *
     * The page reports through the console and answers `evaluateJavascript`; the app reads
     * those strings in [parseNovaTrailerSignal] and [parseNovaTrailerSnapshot]. A renamed
     * prefix or a changed return shape compiles on both sides and fails only on a device,
     * which is exactly what a test is for.
     */
    @Test
    fun `the page reports ready, state and failure, and answers a snapshot`() {
        val html = novaTrailerEmbedHtml("VQGCKyvzIM4")

        // The IFrame API's onReady is the gate for the app's controls: the wrapper
        // document existing is not the player being able to play.
        assertTrue(html.contains("new YT.Player(\"p\""))
        assertTrue(html.contains("onReady: function (event) {"))
        assertTrue(html.contains("report(\"ready\", duration())"))
        assertTrue(html.contains("onStateChange: function (event) { report(\"state\", event.data); }"))
        assertTrue(html.contains("onError: function (event) { report(\"error\", event.data); }"))

        // Ready is also the moment the embed is told to play: the autoplay parameter
        // alone can leave the player cued and silent.
        assertTrue(html.contains("try { player.playVideo(); } catch (e) {}"))

        // A request that arrives before the page has a player is dropped, not thrown:
        // the close can land on a document that is still empty.
        assertTrue(html.contains("if (!player) { return; }"))
        assertTrue(html.contains("if (!window.YT || !window.YT.Player) { report(\"error\", -2); return; }"))

        // One reading, three numbers, as an array: evaluateJavascript serialises the
        // result, and an array arrives as [t,d,s] with nothing to unescape.
        assertTrue(html.contains("snapshot: function () {"))
        assertTrue(html.contains("return [position, end, state()];"))
    }

    /** The command the close path sends must be a pause, guarded, and nothing else. */
    @Test
    fun `closing the viewer sends a guarded pause`() {
        assertEquals(
            "if(window.anisequelTrailer){window.anisequelTrailer.pause()}",
            NOVA_TRAILER_PAUSE_JS
        )
        val html = novaTrailerEmbedHtml("VQGCKyvzIM4")
        assertTrue(
            html.contains("pause: function () { try { if (player) { player.pauseVideo(); } } catch (e) {} }")
        )
    }

    /**
     * A refused video must leave the frame, not sit under live controls.
     *
     * The IFrame API's `onError` is the only signal that YouTube refused the video; the
     * frame itself still shows YouTube's own error card. The page forwards the code, the
     * session turns it into a [NovaTrailerPhase.Failed], and the viewer swaps the frame
     * for the notice - which is a different layout, not a message drawn over the player.
     */
    @Test
    fun `a refused video is reported and replaces the frame`() {
        val viewer = File(root, VIEWER).readText()

        assertTrue(viewer.contains("val failed = session.phase as? NovaTrailerPhase.Failed"))
        assertTrue(viewer.contains("if (failed == null) {"))
        assertTrue(viewer.contains("NovaTrailerNotice("))
        assertTrue(viewer.contains("onRetry = session::retry"))
        // The retry is only offered when it could answer differently.
        assertTrue(viewer.contains("if (isNovaTrailerRetryable(code)) {"))
    }

    /**
     * The close is the part the reader hears.
     *
     * The exit spring runs for a few hundred milliseconds. It used to run over a video
     * that was still playing and still drawing, so closing a trailer kept the sound going
     * and then removed a picture at full strength - an exit that reads as a cut. The order
     * is the fix, not a preference: stop (pause, then hide), fade, dismiss. The surface
     * cannot fade with everything else - that would put the video through a compositing
     * layer and bring the black-picture bug back - so it is switched off first while the
     * chrome and the scrim do the fading.
     */
    @Test
    fun `closing the viewer stops the video before the window leaves`() {
        val viewer = File(root, VIEWER).readText()
        val session = File(root, SESSION).readText()

        val stop = session.substringAfter("fun stop() {")
        val pause = stop.indexOf("pauseNovaTrailer(it)")
        val hide = stop.indexOf("it.visibility = View.INVISIBLE")
        assertTrue("the close must stop playback", pause >= 0)
        assertTrue("the surface must leave the same frame the sound does", pause < hide)

        val close = viewer.substringAfter("if (!isClosing) return@LaunchedEffect")
        val stopAt = close.indexOf("session.stop()")
        val exit = close.indexOf("entrance.animateTo(0f")
        val dismiss = close.indexOf("onDismiss()")
        assertTrue("the close must stop the player", stopAt >= 0)
        assertTrue("the player must stop before the chrome starts fading", stopAt < exit)
        assertTrue("the window may only be removed once the exit has settled", exit < dismiss)

        // A late ready must not switch the surface back on for the tail of the fade.
        assertTrue(File(root, EMBED).readText().contains("!revealed && !isDismissing()"))
        assertTrue(viewer.contains("isDismissing = { dismissing }"))
        // The factory keeps the lambda it was given, so the close has to be read through
        // a state object rather than captured by value.
        assertTrue(viewer.contains("val dismissing by rememberUpdatedState(closing)"))

        // Leaving the app is not a dismissal, but it must stop the sound: a dialog is not
        // a destination the platform pauses for us. The first reading of rememberIsResumed
        // is false, so only a transition counts.
        assertTrue(viewer.contains("val resumed = rememberIsResumed()"))
        assertTrue(viewer.contains("if (previous == null) return@LaunchedEffect"))
        assertTrue(viewer.contains("session.pauseForBackground()"))
        assertTrue(session.contains("it.onPause()"))

        // The chrome follows the window. The video area's black backing follows it only
        // on the way out: faded in, the loading ring would sit over the page showing
        // through the frame instead of over black.
        assertTrue(viewer.contains("graphicsLayer { alpha = entranceProgress }"))
        assertTrue(viewer.contains("if (closing) entranceProgress else 1f"))
    }

    /**
     * The timeline is a bound slider, polled, and never fights the thumb.
     *
     * Material 3 retired the value-first `Slider` because a state object that only reads
     * its initial value stops following an external write - and this thumb is written by
     * the player four times a second. The drag draft is what makes the seek land on
     * release instead of on every frame, and the poll has to be skipped while it is held.
     */
    @Test
    fun `the timeline follows the player and yields to the thumb`() {
        val viewer = File(root, VIEWER).readText()

        assertTrue(
            viewer.contains("state = rememberBoundSliderState(value = shown, trackRange = 0f..limit)")
        )
        assertFalse("the value-first overload is retired", viewer.contains("valueRange"))
        assertTrue(viewer.contains("onValueChangeFinished = onScrubFinished"))
        assertTrue(viewer.contains("scrubSeconds?.let(session::seekTo)"))
        assertTrue(viewer.contains("if (!session.isPlaying || scrubSeconds != null) return@LaunchedEffect"))
        assertTrue(viewer.contains("delay(NOVA_TRAILER_PROGRESS_TICK_MILLIS)"))
        assertTrue(viewer.contains("enabled = session.seekEnabled"))
        // A disabled track still shows where the player is: the clocks are outside the
        // slider, so they keep reading while the player is loading.
        assertTrue(viewer.contains("formatNovaTrailerTime(shown)"))
    }

    @Test
    fun `the transport is disabled until the player can take a command`() {
        val viewer = File(root, VIEWER).readText()
        val transport = viewer
            .substringAfter("private fun NovaTrailerTransport(session: NovaTrailerSession) {")
            .substringBefore("private fun NovaTrailerSkipButton(")

        assertTrue(viewer.contains("val enabled = session.controlsEnabled"))
        // Rewind, play/pause and forward: all three go through the same gate, so a
        // player that has not reported ready cannot be sent a command.
        assertEquals(3, Regex("enabled = enabled").findAll(transport).count())
        assertTrue(
            viewer.contains("imageVector = if (isPlaying) AppVectorIcons.Pause else AppVectorIcons.Play")
        )
        // Replay, not play, once the video has ended: a play call on an ended player may
        // simply do nothing.
        assertTrue(viewer.contains("hasEnded -> \"Replay the trailer\""))
    }

    @Test
    fun `an id outside YouTube's alphabet produces no frame`() {
        // A quote would close the src attribute, an angle bracket would open an element.
        val html = novaTrailerEmbedHtml("x\" onload=\"alert(1)")

        assertFalse("nothing may be interpolated into the page", html.contains("<iframe"))
        assertFalse(html.contains("alert(1)"))
        assertFalse(html.contains("<script>"))
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
            "a page whose origin is youtube.com is read as YouTube embedding itself and " +
                "refused with error 152",
            NOVA_TRAILER_ORIGIN.contains("youtube")
        )
        // The base URL is what WebView turns into the Referer header, so it has to be the
        // same origin the player is told about, or the API messages and the embed check
        // disagree.
        assertEquals("$NOVA_TRAILER_ORIGIN/", NOVA_TRAILER_BASE_URL)
        assertTrue(html.contains("enablejsapi=1&origin=$origin"))
        assertTrue("attribution must name the same origin", html.contains("widget_referrer=$origin"))
        assertFalse(
            "the old youtube.com origin is exactly what YouTube refuses",
            html.contains("origin=https%3A%2F%2Fwww.youtube.com")
        )
    }

    @Test
    fun `a link the reader chose leaves the player`() {
        assertTrue(staysInNovaTrailer("https://www.youtube-nocookie.com/embed/x", isForMainFrame = false))
        assertFalse(
            "the watch page would replace the video with a page that has no back button",
            staysInNovaTrailer("https://www.youtube.com/watch?v=x", isForMainFrame = true)
        )
        // The reported button is a target=_blank link and this view has no popup window,
        // so the navigation happens inside the iframe: the frame test alone followed it
        // and the watch page replaced the trailer one layer down.
        assertFalse(
            "a target=_blank link arrives as a subframe navigation, not a main-frame one",
            staysInNovaTrailer("https://www.youtube.com/watch?v=x", isForMainFrame = false)
        )
        assertFalse(staysInNovaTrailer("https://example.com/", isForMainFrame = false))
        assertFalse(staysInNovaTrailer("https://m.youtube.com/watch?v=x&t=4", isForMainFrame = false))
        assertTrue(isNovaTrailerWatchPage("https://www.youtube.com/watch?v=x"))
        assertFalse(
            "the embed is the player, not a watch page",
            isNovaTrailerWatchPage("https://www.youtube-nocookie.com/embed/x")
        )
    }

    @Test
    fun `a navigation that is not a web page is not a link`() {
        assertTrue(isNovaTrailerWebUrl("https://www.youtube.com/watch?v=x"))
        assertTrue(isNovaTrailerWebUrl("HTTP://example.com/"))
        // The player's own ad and measurement frames: never handed to a browser, and
        // never copied to the clipboard as a "link".
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
        // A lookalike, which a contains("youtube.com") check would have allowed.
        assertFalse(isNovaTrailerHost("https://youtube.com.example.net/embed/x"))
        assertFalse(isNovaTrailerHost(""))
    }

    /**
     * The player must not be left to the platform's default compositing.
     *
     * The report was "the audio is working but nothing is showing": the trailer played -
     * sound, the player's chrome, the title - over a black rectangle. This is a video
     * surface inside a floating dialog window, and without a layer of its own the video
     * plane is dropped while the rest of the page draws normally. A hardware layer carries
     * the whole output, video included, as one texture; a software layer cannot hold a
     * video surface at all, which is the same symptom from the other side. Neither can be
     * seen from a JVM test, so the call itself is pinned.
     */
    @Test
    fun `the player asks for a hardware layer`() {
        val text = codeOnly(File(root, EMBED).readText())

        assertTrue(
            "the player is composited by the platform default, so the video surface can " +
                "be dropped while the audio plays",
            text.contains("setLayerType(View.LAYER_TYPE_HARDWARE, null)")
        )
        assertFalse(
            "a software layer cannot hold a video surface: the audio plays and the frame " +
                "stays black",
            text.contains("LAYER_TYPE_SOFTWARE")
        )
        // The surface is switched, never faded: alpha on a native video surface is the
        // same failure from the other side.
        assertTrue(text.contains("alpha = 1f"))
        assertTrue(text.contains("visibility = View.INVISIBLE"))
        assertTrue(text.contains("webView.visibility = View.VISIBLE"))
    }

    @Test
    fun `a console line is a signal only when it is one the page wrote`() {
        assertEquals(
            NovaTrailerSignal.Ready(212.5f),
            parseNovaTrailerSignal("anisequel:trailer-ready:212.5")
        )
        assertEquals(NovaTrailerSignal.Ready(0f), parseNovaTrailerSignal("anisequel:trailer-ready:"))
        assertEquals(NovaTrailerSignal.StateChanged(1), parseNovaTrailerSignal("anisequel:trailer-state:1"))
        assertEquals(NovaTrailerSignal.Failed(150), parseNovaTrailerSignal("anisequel:trailer-error:150"))
        assertEquals(NovaTrailerSignal.Failed(-2), parseNovaTrailerSignal("anisequel:trailer-error:-2"))

        // YouTube's own console noise, a truncated payload and a prefix we did not write
        // are all "nothing", not "a player that is now broken".
        assertNull(parseNovaTrailerSignal("[YT] something happened"))
        assertNull(parseNovaTrailerSignal("anisequel:trailer-state:"))
        assertNull(parseNovaTrailerSignal("anisequel:trailer-ready:soon"))
        assertNull(parseNovaTrailerSignal(""))
        assertNull(parseNovaTrailerSignal("anisequel:trailer-something-else:1"))
    }

    @Test
    fun `a snapshot is three numbers or nothing`() {
        val snapshot = parseNovaTrailerSnapshot("[12.5,240,-1]")
        assertNotNull(snapshot)
        assertEquals(12.5f, snapshot!!.positionSeconds, 0.001f)
        assertEquals(240f, snapshot.durationSeconds, 0.001f)
        assertEquals(-1, snapshot.state)

        assertNull(parseNovaTrailerSnapshot(null))
        assertNull(parseNovaTrailerSnapshot(""))
        assertNull(parseNovaTrailerSnapshot("[]"))
        assertNull(parseNovaTrailerSnapshot("[1,2]"))
        assertNull(parseNovaTrailerSnapshot("[1,2,3,4]"))
        assertNull(parseNovaTrailerSnapshot("null"))
        assertNull(parseNovaTrailerSnapshot("[NaN,1,1]"))
        assertNull(parseNovaTrailerSnapshot("[1,Infinity,1]"))
    }

    @Test
    fun `a clock reads like a clock`() {
        assertEquals("0:00", formatNovaTrailerTime(0f))
        assertEquals("0:59", formatNovaTrailerTime(59.9f))
        assertEquals("1:01", formatNovaTrailerTime(61f))
        assertEquals("10:00", formatNovaTrailerTime(600f))
        assertEquals("1:01:11", formatNovaTrailerTime(3671f))
        // The page reports -1 before it has a player; a clock must never render that.
        assertEquals("0:00", formatNovaTrailerTime(-1f))
        assertEquals("0:00", formatNovaTrailerTime(Float.NaN))
    }

    @Test
    fun `a refusal says what a reader can do about it`() {
        assertTrue(novaTrailerFailureReason(101).contains("owner"))
        assertEquals(novaTrailerFailureReason(101), novaTrailerFailureReason(150))
        assertTrue(novaTrailerFailureReason(100).contains("removed"))
        assertTrue(novaTrailerFailureReason(153).contains("referrer"))
        assertTrue(novaTrailerFailureReason(NOVA_TRAILER_CODE_TIMEOUT).contains("did not answer"))
        assertTrue(novaTrailerFailureReason(NOVA_TRAILER_CODE_NO_PLAYER).contains("could not be loaded"))
        // Every code has a sentence; an unknown one is not a blank panel.
        assertTrue(novaTrailerFailureReason(7).isNotBlank())
        assertNotEquals(novaTrailerFailureReason(2), novaTrailerFailureReason(100))

        // A retry only makes sense for the failures that are about this attempt.
        assertTrue(isNovaTrailerRetryable(NOVA_TRAILER_CODE_TIMEOUT))
        assertTrue(isNovaTrailerRetryable(NOVA_TRAILER_CODE_NO_PLAYER))
        assertTrue(isNovaTrailerRetryable(5))
        assertFalse(isNovaTrailerRetryable(100))
        assertFalse(isNovaTrailerRetryable(101))
        assertFalse(isNovaTrailerRetryable(150))
        assertFalse(isNovaTrailerRetryable(153))
    }

    private fun codeOnly(text: String): String =
        text.replace(Regex("/\\*[\\s\\S]*?\\*/"), "").replace(Regex("//[^\\n]*"), "")

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
        const val VIEWER =
            "app/src/main/java/com/anisequel/ui/components/detail/info/NovaTrailerViewer.kt"
        const val EMBED =
            "app/src/main/java/com/anisequel/ui/components/detail/info/NovaTrailerEmbed.kt"
        const val SESSION =
            "app/src/main/java/com/anisequel/ui/components/detail/info/NovaTrailerSession.kt"
    }
}

/**
 * The trailer's state machine, away from Compose and away from a device.
 *
 * Every transition here was reachable in the previous viewer, where five separate state
 * values could disagree: a failure that a later state event overwrote, an ended position
 * that stayed at its last poll instead of the duration, a retry that left the old duration
 * on the new player. The class is pure Kotlin, so it is cheap to pin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NovaTrailerSessionTest {

    private fun readySession(duration: Float = 240f): NovaTrailerSession {
        val session = NovaTrailerSession()
        session.onSignal(NovaTrailerSignal.Ready(duration))
        return session
    }

    @Test
    fun `ready carries the duration and unlocks the controls`() {
        val session = NovaTrailerSession()
        assertFalse(session.controlsEnabled)
        assertFalse(session.seekEnabled)

        session.onSignal(NovaTrailerSignal.Ready(240f))

        assertEquals(NovaTrailerPhase.Ready, session.phase)
        assertEquals(240f, session.durationSeconds, 0.001f)
        assertTrue(session.controlsEnabled)
        assertTrue(session.seekEnabled)
    }

    @Test
    fun `a zero duration means the video has none, not that it is over`() {
        val session = readySession(duration = 0f)

        assertEquals(
            "a live stream has no duration and must not be reported as a failure",
            NovaTrailerPhase.Ready,
            session.phase
        )
        assertFalse("nothing to seek in", session.seekEnabled)
        assertTrue("but it can still be paused", session.controlsEnabled)
    }

    @Test
    fun `playback state drives the glyph and the end of the video`() {
        val session = readySession()

        session.onSignal(NovaTrailerSignal.StateChanged(1))
        assertTrue(session.isPlaying)

        session.onSignal(NovaTrailerSignal.StateChanged(2))
        assertFalse(session.isPlaying)

        // Buffering keeps what the reader last chose: YouTube reports it both while a
        // playing video stalls and while a paused one is skipped forward.
        session.onSignal(NovaTrailerSignal.StateChanged(1))
        session.onSignal(NovaTrailerSignal.StateChanged(3))
        assertTrue(session.isPlaying)

        session.onSignal(NovaTrailerSignal.StateChanged(0))
        assertFalse(session.isPlaying)
        assertEquals(NovaTrailerPhase.Ended, session.phase)
        assertEquals(
            "the bar sits at the end, not at the last poll",
            240f,
            session.positionSeconds,
            0.001f
        )
        assertTrue("an ended trailer can be replayed", session.hasEnded)
        assertTrue("and its controls still work", session.controlsEnabled)

        session.onSignal(NovaTrailerSignal.StateChanged(1))
        assertEquals("a replay is not still 'ended'", NovaTrailerPhase.Ready, session.phase)
    }

    /**
     * A refusal outlives a late ready; a timeout does not.
     *
     * The IFrame API can emit its ready event after an error for the same frame. Accepting
     * it hid the explanation while YouTube's own error card sat in the 16:9 hole. A
     * timeout is a statement about the clock, so a player that arrives late is still
     * welcome.
     */
    @Test
    fun `a refusal is final but a timeout is not`() {
        val refused = NovaTrailerSession()
        refused.onSignal(NovaTrailerSignal.Failed(150))
        refused.onSignal(NovaTrailerSignal.Ready(210f))
        assertEquals(NovaTrailerPhase.Failed(150), refused.phase)
        assertEquals(0f, refused.durationSeconds, 0.001f)

        val slow = NovaTrailerSession()
        slow.onSignal(NovaTrailerSignal.Failed(NOVA_TRAILER_CODE_TIMEOUT))
        slow.onSignal(NovaTrailerSignal.Ready(210f))
        assertEquals(NovaTrailerPhase.Ready, slow.phase)
        assertEquals(210f, slow.durationSeconds, 0.001f)
    }

    @Test
    fun `a failure is not overwritten by the state that follows it`() {
        val session = NovaTrailerSession()
        session.onSignal(NovaTrailerSignal.Failed(101))

        // The player keeps emitting state changes while its error card is up.
        session.onSignal(NovaTrailerSignal.StateChanged(2))
        session.onSignal(NovaTrailerSignal.Failed(NOVA_TRAILER_CODE_NO_PLAYER))

        assertEquals(
            "the first explanation is the informative one",
            NovaTrailerPhase.Failed(101),
            session.phase
        )
        assertFalse(session.isPlaying)
        assertFalse(session.controlsEnabled)
    }

    @Test
    fun `a snapshot moves the clock without becoming a second source of truth`() {
        val session = readySession()

        session.onSnapshot("[30,240,1]")
        assertEquals(30f, session.positionSeconds, 0.001f)
        assertTrue("state 1 in a snapshot is the same answer as an event", session.isPlaying)

        // The page's "no player" answer is [-1,-1,-1]: it must not drag the bar back to
        // zero while a real position is known.
        session.onSnapshot("[-1,-1,-1]")
        assertEquals(30f, session.positionSeconds, 0.001f)

        session.onSnapshot("[241,240,0]")
        assertEquals("an ended video parks at its duration", 240f, session.positionSeconds, 0.001f)
        assertEquals(NovaTrailerPhase.Ended, session.phase)
    }

    @Test
    fun `seeking is clamped to the video and written before it is sent`() {
        val session = readySession()

        session.seekTo(300f)
        assertEquals(240f, session.positionSeconds, 0.001f)

        session.seekTo(-5f)
        assertEquals(0f, session.positionSeconds, 0.001f)

        session.seekTo(30f)
        session.seekBy(10f)
        assertEquals(40f, session.positionSeconds, 0.001f)

        session.seekTo(Float.NaN)
        assertEquals("a non-finite seek is ignored, not sent to the page", 40f, session.positionSeconds, 0.001f)
    }

    @Test
    fun `a retry clears everything the failed attempt left behind`() {
        val session = readySession()
        session.onSnapshot("[100,240,1]")
        session.onSignal(NovaTrailerSignal.Failed(5))
        session.onRevealed()

        val attempt = session.attempt
        session.retry()

        assertEquals(NovaTrailerPhase.Loading, session.phase)
        assertEquals(attempt + 1, session.attempt)
        assertFalse(session.isPlaying)
        assertFalse("the ring comes back", session.revealed)
        assertEquals(0f, session.positionSeconds, 0.001f)
        assertEquals(0f, session.durationSeconds, 0.001f)
        assertFalse(session.controlsEnabled)
    }

    @Test
    fun `a close and a backgrounding are harmless before the page exists`() {
        val session = readySession()
        session.onSignal(NovaTrailerSignal.StateChanged(1))

        session.stop()
        assertFalse(session.isPlaying)

        // Every command below is reachable during the first frame of the dialog, before
        // the view has attached its page.
        session.togglePlayback()
        session.syncPosition()
        session.pauseForBackground()
        session.resumeFromBackground()
        session.detach(android.webkit.WebView(RuntimeEnvironment.getApplication()))
        assertFalse(session.isPlaying)
    }
}
