package com.anisequel.ui.components.detail.info

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.anisequel.ui.components.openExternalUrlOrCopy
import java.net.URLEncoder

/**
 * The player view, and the one place that decides when it may be seen.
 *
 * ## Why a WebView and not a native player
 *
 * AniList hands the app a YouTube **id**, not a stream. The YouTube Android Player API
 * that used to play one was withdrawn, and what is left is either YouTube's own embed (a
 * web page) or a stream extractor - a dependency whose whole job is to break when YouTube
 * changes, fetching content the API did not license this app to serve. The embed is the
 * same answer the rest of the app already gives for a live SVG card: the one leaf of
 * Chromium that does the job nothing else can, contained. It is also why this is the one
 * `WebView` in the app with JavaScript enabled: the player *is* a web app, and without
 * scripting the embed is a black rectangle. Everything a document could do with that is
 * closed off instead - the page is a fixed wrapper holding one iframe, the only hosts it
 * may load are YouTube's, and a navigation that is not the player's own pages is
 * cancelled here.
 *
 * ## Why visibility changes, not alpha
 *
 * A `WebView` is a real Android view in Compose's interop layer. Applying alpha or
 * scale compositing around its video surface can leave audio and player chrome
 * visible while the video plane is black. The view therefore stays fully opaque and
 * hardware-backed; it starts `INVISIBLE` and becomes `VISIBLE` only when the page
 * says the player is ready (or the reveal watchdog decides a slow load has to show
 * something). The Compose loading ring sits above it only while it is invisible,
 * rather than trying to fade through a native video surface.
 *
 * ## The protocol: console in, script out
 *
 * Events come *out* of the page as console lines, because a `JavascriptInterface` is a
 * callable surface exposed to a frame this app does not control, and all this needs is
 * for one string to arrive. Commands and readings go *in* through
 * `evaluateJavascript`, which is a one-shot call the page cannot reach back through.
 *
 * The old protocol only had the events - so the viewer could tell whether the video was
 * playing but not where it was, and a seek bar was impossible. `snapshot()` now returns
 * `[position, duration, state]` as a JSON array (`evaluateJavascript` hands back the
 * value serialised, and an array needs no un-escaping; a string would arrive
 * double-quoted and escaped) and the viewer polls it while playing.
 */
@Suppress("SetJavaScriptEnabled", "OVERRIDE_DEPRECATION")
internal fun createNovaTrailerPlayer(
    context: Context,
    html: String,
    isDismissing: () -> Boolean,
    onReveal: () -> Unit,
    onSignal: (NovaTrailerSignal) -> Unit
): WebView {
    val webView = WebView(context)
    var revealed = false
    // Set by the page's onReady. Until then the failure watchdog has work to do.
    var ready = false
    // Idempotent: the IFrame API's ready event and the reveal watchdog can both ask.
    val reveal: () -> Unit = {
        if (!revealed && !isDismissing()) {
            revealed = true
            webView.visibility = View.VISIBLE
            onReveal()
        }
    }
    // Everything the page says is dropped once the window is on its way out: a state
    // change arriving mid-fade would repaint a panel the reader has already closed.
    val deliver: (NovaTrailerSignal) -> Unit = { signal ->
        if (signal is NovaTrailerSignal.Ready) {
            ready = true
            reveal()
        }
        if (!isDismissing()) onSignal(signal)
    }
    webView.apply {
        // Keep the native video surface opaque. The visibility switch above is the
        // only reveal mechanism; a fractional alpha can put the video through a
        // compositing layer and reproduce the audio-with-black-picture failure.
        alpha = 1f
        visibility = View.INVISIBLE
        setBackgroundColor(AndroidColor.BLACK)
        // A hardware layer, explicitly.
        //
        // The trailer played over a black rectangle: audio, the player's own chrome,
        // the title - and no picture. This view is a video surface inside a floating
        // dialog window, and without a layer of its own the platform composites it
        // through a path that drops the video plane while the rest of the page draws
        // normally, which is why it read as "the sound works and the picture does
        // not" rather than as a failed load. A hardware layer puts the WebView's
        // whole output, video included, through its render node as one texture.
        //
        // The opposite is the same report from the other side: `LAYER_TYPE_SOFTWARE`
        // cannot hold a video surface at all, so a player forced into one plays audio
        // and draws nothing. This is deliberately not that - and the app never sets a
        // software layer on a view that shows video.
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false
        // Explicit, because the navigation rule depends on it: with no popup
        // window a `target="_blank"` link - which is what "Watch on YouTube" is -
        // is loaded in the frame that asked for it, so it reaches
        // `shouldOverrideUrlLoading` as a subframe navigation and the watch-page
        // check can send it out. With windows enabled and no `onCreateWindow`, the
        // same tap would do nothing at all.
        settings.setSupportMultipleWindows(false)
        webChromeClient = NovaTrailerChromeClient(
            webView = this,
            onSignal = deliver
        )
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        webViewClient = object : WebViewClient() {
            // There is deliberately no `onPageFinished` reveal: it fires for the
            // wrapper, i.e. for the HTML this app wrote, and not for the player.
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val target = request?.url?.toString()
                if (request != null && staysInNovaTrailer(target.orEmpty(), request.isForMainFrame)) {
                    return false
                }
                // Only a web page is worth opening elsewhere. The player's own
                // `about:blank` / `data:` frames are not links, and handing them to the
                // opener copied their URL to the clipboard and announced it.
                if (target != null && isNovaTrailerWebUrl(target)) {
                    openExternalUrlOrCopy(context, target)
                }
                return true
            }
            // The deprecated `(WebView, String)` overload is gone rather
            // than suppressed: the platform stopped calling it in API 24
            // and `minSdk` is 31, so a second entry point to the same rule
            // could never fire.
        }
        // Not loaded here: a view built during composition has not been measured,
        // and this page's frame is sized in percentages of the viewport - so a load
        // that starts now lays the wrapper out against a frame with no size, and the
        // embed takes the picture's size from the frame it was given. It waits for
        // the first layout that has one.
        var loadPending = true
        // Nine parameters, not the four that matter: this is
        // `View.OnLayoutChangeListener`, and a shorter lambda is not a SAM
        // conversion - it is a compile error the debug build caught.
        addOnLayoutChangeListener { _, _, _, right, bottom, _, _, _, _ ->
            if (loadPending && right > 0 && bottom > 0) {
                loadPending = false
                webView.loadDataWithBaseURL(
                    NOVA_TRAILER_BASE_URL, html, "text/html", "UTF-8", null
                )
            }
        }
    }
    // Two watchdogs, not one, because they answer different questions.
    //
    // The first is the old last-resort reveal: a page that says nothing at all - no
    // WebView provider, a script the page declined to run - must stop being a spinner
    // eventually. Showing the surface at that point is right even on a slow network: the
    // reader sees YouTube's own loading state rather than an error that may not be true.
    //
    // The second is the new failure deadline. A player that never reports ready is a
    // player that will not play this video here, and the reader may only learn that from
    // the panel with a retry - a black frame and a spinner forever is the failure this
    // replaces. It is deliberately much later than the reveal, so a slow connection is
    // not mistaken for a broken one.
    val revealTimeout = Runnable { reveal() }
    val failureTimeout = Runnable {
        if (!ready && !isDismissing()) {
            onSignal(NovaTrailerSignal.Failed(NOVA_TRAILER_CODE_TIMEOUT))
        }
    }
    webView.postDelayed(revealTimeout, NOVA_TRAILER_REVEAL_TIMEOUT_MILLIS)
    webView.postDelayed(failureTimeout, NOVA_TRAILER_FAILURE_TIMEOUT_MILLIS)
    // A dialog can be closed before either fires, and releasing the player calls
    // `destroy()`. A callback still queued past that runs against a view the composition
    // has already thrown away, and holds the WebView - with the page it decoded and its
    // audio decoder - alive for the rest of the deadline. Removing them on detach is the
    // one hook that fires both for a close and for the rotation that recreates the dialog.
    webView.addOnAttachStateChangeListener(
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                view.removeCallbacks(revealTimeout)
                view.removeCallbacks(failureTimeout)
                view.removeOnAttachStateChangeListener(this)
            }
        }
    )
    return webView
}

/**
 * The origin the wrapper page is loaded under, and the one the player is told it
 * is embedded in.
 *
 * This must not be a YouTube host. The player identifies its embedder by the
 * page's origin, and a page whose origin is `https://www.youtube.com` is read as
 * YouTube embedding itself: every trailer came back as error 152, "This video is
 * unavailable", before a frame played. That was the whole trailer library, not one
 * bad id. The app id is a real `https://` origin, is unique to this caller, and is
 * the identifier Google documents for a WebView embed - `loadDataWithBaseURL`
 * turns it into the `Referer` header, which is the identification YouTube now
 * refuses embeds without (error 153). The iframe itself still loads from
 * youtube-nocookie.com: the origin names the *page*, the host names the *player*,
 * and they are not supposed to be the same value here.
 */
internal const val NOVA_TRAILER_ORIGIN = "https://com.aistudio.anisequel.app"
internal const val NOVA_TRAILER_BASE_URL = "$NOVA_TRAILER_ORIGIN/"
private val NOVA_TRAILER_ORIGIN_QUERY: String =
    URLEncoder.encode(NOVA_TRAILER_ORIGIN, Charsets.UTF_8.name())

/** The page's handle, mirrored in the script below. */
private const val NOVA_TRAILER_HANDLE = "window.anisequelTrailer"

/** The IFrame API reported that the player can be commanded. Carries its duration. */
private const val NOVA_TRAILER_READY_PREFIX = "anisequel:trailer-ready:"

/** Playback-state events forwarded from the IFrame API to Compose. */
private const val NOVA_TRAILER_STATE_PREFIX = "anisequel:trailer-state:"

/**
 * A refusal: either the IFrame API's `onError` code, or one of the two codes this app
 * raises for a player that never appeared ([NOVA_TRAILER_CODE_TIMEOUT],
 * [NOVA_TRAILER_CODE_NO_PLAYER]).
 *
 * Without this a refused video (101/150 owner blocked embeds, 100 removed, 153 a
 * missing referer) rendered YouTube's own "Video unavailable" card inside the 16:9
 * hole, with the controls live over it and no way out but the small link underneath.
 */
private const val NOVA_TRAILER_ERROR_PREFIX = "anisequel:trailer-error:"

/** The last-resort reveal: a player that never reports must not spin forever. */
private const val NOVA_TRAILER_REVEAL_TIMEOUT_MILLIS = 7000L

/** How long a player that never reports ready may stay in the loading state. */
private const val NOVA_TRAILER_FAILURE_TIMEOUT_MILLIS = 20000L

/**
 * One event from the wrapper page.
 *
 * A sealed type rather than three nullable fields on a lambda: the viewer has to react
 * to each of these differently - ready reveals and starts the progress clock, a state
 * change drives one glyph, a failure replaces the frame - and a bare `(Int) -> Unit`
 * with three callbacks is how the previous version ended up with five `mutableStateOf`
 * values that could disagree.
 */
internal sealed interface NovaTrailerSignal {
    /** The IFrame API's `onReady`. [durationSeconds] is 0 when the video has none. */
    data class Ready(val durationSeconds: Float) : NovaTrailerSignal

    /** `onStateChange`: -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued. */
    data class StateChanged(val state: Int) : NovaTrailerSignal

    /** `onError`, or one of this app's own codes. */
    data class Failed(val code: Int) : NovaTrailerSignal
}

/**
 * Reads one console line as a player event, or null for YouTube's own noise.
 *
 * Pure and separate from the [WebChromeClient] because this is the part that can be
 * wrong in ways a device would hide: a prefix that drifts, a payload that stops
 * parsing, a code that arrives as a float. The page and the viewer are on opposite
 * sides of a language boundary, and this is the only place that boundary is crossed.
 */
internal fun parseNovaTrailerSignal(message: String): NovaTrailerSignal? = when {
    message.startsWith(NOVA_TRAILER_READY_PREFIX) -> NovaTrailerSignal.Ready(
        message.removePrefix(NOVA_TRAILER_READY_PREFIX).toFloatOrNull() ?: 0f
    )
    message.startsWith(NOVA_TRAILER_STATE_PREFIX) -> message
        .removePrefix(NOVA_TRAILER_STATE_PREFIX)
        .toIntOrNull()
        ?.let { NovaTrailerSignal.StateChanged(it) }
    message.startsWith(NOVA_TRAILER_ERROR_PREFIX) -> message
        .removePrefix(NOVA_TRAILER_ERROR_PREFIX)
        .toIntOrNull()
        ?.let { NovaTrailerSignal.Failed(it) }
    else -> null
}

/** One reading of the player: where it is, how long it is, what it is doing. */
internal data class NovaTrailerSnapshot(
    val positionSeconds: Float,
    val durationSeconds: Float,
    val state: Int
)

/**
 * Parses the JSON array `snapshot()` returns, or null when the page has no player yet.
 *
 * Hand-parsed rather than through `org.json`: the payload is three numbers this app
 * wrote, and a parser that accepts only that cannot be surprised by what a page
 * returns. `[0,0,-1]` is the page's "not ready" answer and reads as state -1.
 */
internal fun parseNovaTrailerSnapshot(raw: String?): NovaTrailerSnapshot? {
    val text = raw?.trim().orEmpty()
    if (!text.startsWith("[") || !text.endsWith("]")) return null
    val parts = text.substring(1, text.length - 1).split(',')
    if (parts.size != 3) return null
    val position = parts[0].trim().toFloatOrNull() ?: return null
    val duration = parts[1].trim().toFloatOrNull() ?: return null
    val state = parts[2].trim().toFloatOrNull()?.toInt() ?: return null
    // NaN and Infinity are valid Float conversions and not valid readings. A page that
    // answers with one would otherwise set the clock to a number no bar can draw.
    if (!position.isFinite() || !duration.isFinite()) return null
    return NovaTrailerSnapshot(position, duration, state)
}

/**
 * Every command is guarded, because every command can arrive before the page has a
 * player: the close runs on the frame the reader taps it, a lifecycle stop can follow
 * the activity straight into the background, and either can land while the document
 * is still empty. `evaluateJavascript` against an empty document has nothing to call,
 * and the throw it raises is a console error in a page nobody is reading.
 */
internal fun novaTrailerCall(call: String): String = "if($NOVA_TRAILER_HANDLE){$NOVA_TRAILER_HANDLE.$call}"

/** The close and a lifecycle stop both ask for this, and neither wants a toggle. */
internal val NOVA_TRAILER_PAUSE_JS: String = novaTrailerCall("pause()")

private val NOVA_TRAILER_PLAY_JS: String = novaTrailerCall("play()")
private val NOVA_TRAILER_TOGGLE_JS: String = novaTrailerCall("toggle()")
private val NOVA_TRAILER_SNAPSHOT_JS: String = novaTrailerCall("snapshot()")

/** Runs one guarded command. A destroyed `WebView` cannot evaluate at all, hence the wrap. */
private fun sendNovaTrailerCommand(player: WebView, script: String) {
    runCatching { player.evaluateJavascript(script, null) }
}

internal fun playNovaTrailer(player: WebView) = sendNovaTrailerCommand(player, NOVA_TRAILER_PLAY_JS)

/**
 * Stops the embed's playback, wherever it is and whatever state it is in.
 *
 * A pause, never a toggle: the player may already be paused (or paused by the system)
 * when this is called, and a toggle would start it in exactly that case.
 */
internal fun pauseNovaTrailer(player: WebView) =
    sendNovaTrailerCommand(player, NOVA_TRAILER_PAUSE_JS)

internal fun toggleNovaTrailer(player: WebView) =
    sendNovaTrailerCommand(player, NOVA_TRAILER_TOGGLE_JS)

/** Seeks to an absolute second. The page clamps to the video's own duration. */
internal fun seekNovaTrailer(player: WebView, seconds: Float) {
    if (!seconds.isFinite()) return
    sendNovaTrailerCommand(player, novaTrailerCall("seekTo($seconds)"))
}

/**
 * Asks the page where playback is, and hands the answer back if there is an answer.
 *
 * The callback runs on the main thread after the document has evaluated the script, so
 * a closed viewer simply never receives it. A page with no player answers `[-1,...]`
 * and [parseNovaTrailerSnapshot] passes that on; the session ignores a negative position.
 */
internal fun readNovaTrailerSnapshot(
    player: WebView,
    onSnapshot: (NovaTrailerSnapshot) -> Unit
) {
    runCatching {
        player.evaluateJavascript(NOVA_TRAILER_SNAPSHOT_JS) { raw ->
            parseNovaTrailerSnapshot(raw)?.let(onSnapshot)
        }
    }
}

/**
 * Routes YouTube's full-screen video surface above the Compose dialog.
 *
 * `fs=1` is on the frame, so a reader who taps fullscreen in the embed gets the real
 * thing: YouTube hands the platform a view, and it is attached to the dialog's root.
 */
@Suppress("OVERRIDE_DEPRECATION")
internal class NovaTrailerChromeClient(
    private val webView: WebView,
    private val onSignal: (NovaTrailerSignal) -> Unit
) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val root = webView.rootView as? ViewGroup
        if (root == null || customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        root.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onHideCustomView() = hideCustomView()

    // The player reports itself through the console rather than through a
    // JavaScript interface: a bridge is a callable surface exposed to a frame this
    // app does not control, and all this needs is for one string to arrive.
    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        val signal = parseNovaTrailerSignal(message.message())
            ?: return super.onConsoleMessage(message)
        onSignal(signal)
        return true
    }

    fun hideCustomView() {
        val view = customView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }
}

/** The hosts the player frame is allowed to load. */
private val NOVA_TRAILER_HOSTS = listOf("youtube.com", "youtube-nocookie.com", "ytimg.com", "googlevideo.com")

/**
 * Whether [url] belongs to the embed rather than to the web at large.
 *
 * The authority is matched without its port and without user-info. Matching it whole
 * meant `https://www.youtube.com:443/embed/x` read as the host `youtube.com:443`, which
 * is not YouTube by this rule - so a navigation the player needed was handed to a
 * browser mid-playback. The dot is anchored on both ends: `youtube.com.example.net`
 * is not YouTube, and neither is a bare `notyoutube.com`.
 */
internal fun isNovaTrailerHost(url: String): Boolean {
    val authority = url.substringAfter("://", "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
    if (host.isEmpty()) return false
    return NOVA_TRAILER_HOSTS.any { host == it || host.endsWith(".$it") }
}

/**
 * Whether [url] is a page worth handing to another app: an `http(s)` document.
 *
 * Everything else a player navigates to is its own scaffolding - `about:blank` for an
 * ad slot, `data:` or `blob:` for a measurement frame. Those used to reach the shared
 * opener too, so each one wrote its URL onto the reader's clipboard and showed the
 * "nothing here can open that link, so it is copied instead" toast - about a link no
 * reader chose, and while the trailer was still playing.
 */
internal fun isNovaTrailerWebUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

/**
 * Whether [url] is YouTube's watch page rather than the player's own document.
 *
 * The player only ever lives at `youtube(-nocookie).com/embed/...`; the watch page is
 * what a link resolves to. It is a separate question from [staysInNovaTrailer]'s frame
 * test because the reported link is a `target="_blank"` one: this view has no popup
 * window, so the navigation arrives *inside the iframe* - a subframe by
 * `isForMainFrame` - and the frame rule alone followed it, putting the watch page in
 * the 16:9 hole anyway.
 */
internal fun isNovaTrailerWatchPage(url: String): Boolean {
    if (!isNovaTrailerHost(url)) return false
    val path = url.substringAfter("://", "")
        .substringAfter('/', "")
        .substringBefore('?')
        .substringBefore('#')
        .trim('/')
    return path.equals("watch", ignoreCase = true)
}

/**
 * Whether a navigation belongs to the player and may stay inside its `WebView`.
 *
 * It has to be the player's own document: a **subframe**, on YouTube's hosts, that is
 * not the watch page. Each of those three conditions is a way this went wrong.
 *
 *  - A **subframe** is the player itself - its error card, a consent interstitial, the
 *    IFrame API's own pages. Nothing inside the embed works if those are not followed.
 *  - A **main frame** is somewhere the reader chose to go: "Watch on YouTube" on a
 *    refused video, the channel name, an ad. Following one replaced the video with the
 *    watch page inside a 16:9 hole that has no back button - the reported dead link.
 *  - A subframe **watch page** is that same reader choice arriving one layer lower. The
 *    button is a `target="_blank"` link and this view has no popup window, so the
 *    navigation happens in the iframe and passes the frame test; see
 *    [isNovaTrailerWatchPage].
 */
internal fun staysInNovaTrailer(url: String, isForMainFrame: Boolean): Boolean =
    !isForMainFrame && isNovaTrailerHost(url) && !isNovaTrailerWatchPage(url)

/** Whether [id] is a YouTube video id and therefore safe to put in a page. */
internal fun isNovaTrailerId(id: String): Boolean =
    id.isNotEmpty() && id.length <= 20 && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }

/**
 * The wrapper page: one iframe, filling it, and nothing else.
 *
 * `playsinline` is what keeps the video inside the frame instead of handing it to
 * the system's fullscreen player, `rel=0` stops the embed offering other videos on
 * top of the trailer once it ends, and `modestbranding` drops the badge that sits
 * over the picture. The frame is `allow="autoplay; encrypted-media"` because without
 * those the player starts muted or refuses to start at all.
 *
 * The id is validated rather than escaped: AniList's ids are `[A-Za-z0-9_-]`, and
 * anything else is not a trailer. That is what keeps a malformed payload from
 * becoming markup in a page with JavaScript on.
 */
internal fun novaTrailerEmbedHtml(videoId: String): String {
    val safeId = if (isNovaTrailerId(videoId)) videoId else ""
    if (safeId.isEmpty()) {
        // An empty document rather than an escaped string: the card only offers a
        // viewer for an id it just read, so this is the malformed case, and the escape
        // hatch in the viewer is where the reader goes next.
        return "<!doctype html><html><head><style>html,body{margin:0;background:#000}</style></head><body></body></html>"
    }
    return buildString {
        append("<!doctype html><html><head>")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">")
        append("<style>")
        append("html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden;}")
        append("iframe{display:block;width:100%;height:100%;border:0;}")
        append("</style></head><body>")
        append("<iframe id=\"p\" src=\"https://www.youtube-nocookie.com/embed/")
        append(safeId)
        append("?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=1")
        append("&enablejsapi=1&origin=")
        append(NOVA_TRAILER_ORIGIN_QUERY)
        // The widget referrer is the same origin by definition; the embed
        // API takes it separately, and it is the value the player uses for
        // attribution when the page is not a public site.
        append("&widget_referrer=")
        append(NOVA_TRAILER_ORIGIN_QUERY)
        append("\"")
        // `strict-origin-when-cross-origin` is the policy YouTube documents
        // and the one a browser sends by default. It still sends only the
        // origin cross-origin, which is all YouTube needs, and it stopped
        // being `origin` here so the attribute matches what the platform now
        // recommends rather than a value that happened to work.
        append(" referrerpolicy=\"strict-origin-when-cross-origin\"")
        append(" allow=\"autoplay; encrypted-media; picture-in-picture\"")
        append(" allowfullscreen frameborder=\"0\"></iframe>")
        append("<script>")
        append(NOVA_TRAILER_WRAPPER_JS)
        append("</script>")
        append("</body></html>")
    }
}

/**
 * The script that turns the frame into something the app can command.
 *
 * It binds to the iframe the HTML already created (`YT.Player('p')`) rather than letting
 * the API build one, because the iframe starts loading with the document: waiting for a
 * second script before even asking for the video was the difference between a spinner
 * and a picture on a slow connection.
 *
 * Three things here are load-bearing:
 *
 *  - **`onReady` reports the duration and calls `playVideo()`.** The embed is asked to
 *    autoplay, but a WebView can still hold the first frame in `cued`; the explicit
 *    play is what turns "ready" into "playing". It is idempotent.
 *  - **Every command is guarded and wrapped.** A command can arrive between the
 *    document existing and the player existing, and an exception there would surface as
 *    a console error in a page nobody is reading.
 *  - **`snapshot()` returns an array, not a string.** `evaluateJavascript` serialises
 *    its result as JSON, so a string arrives double-quoted and escaped; an array of
 *    three numbers arrives as `[t,d,s]` and parses without a JSON reader.
 *
 * The API script is inserted with an `onerror` hook: a network that refuses
 * `youtube.com/iframe_api` used to leave the spinner running until the failure
 * watchdog, which reads exactly like a slow connection. Here it is a reported failure.
 */
private val NOVA_TRAILER_WRAPPER_JS = """
    (function () {
      "use strict";
      var player = null;
      function say(line) { try { console.log(line); } catch (e) {} }
      function report(kind, value) { say("anisequel:trailer-" + kind + ":" + value); }
      function state() { try { return player ? (player.getPlayerState() | 0) : -1; } catch (e) { return -1; } }
      function duration() { try { return player ? (player.getDuration() || 0) : 0; } catch (e) { return 0; } }
      function now() { try { return player ? (player.getCurrentTime() || 0) : 0; } catch (e) { return 0; } }
      function seek(seconds) {
        try {
          if (!player) { return; }
          var end = duration();
          var target = Math.max(0, seconds);
          if (end > 0) { target = Math.min(end, target); }
          player.seekTo(target, true);
        } catch (e) {}
      }
      window.anisequelTrailer = {
        player: null,
        play: function () { try { if (player) { player.playVideo(); } } catch (e) {} },
        pause: function () { try { if (player) { player.pauseVideo(); } } catch (e) {} },
        toggle: function () {
          try {
            if (!player) { return; }
            if (state() === 1) { player.pauseVideo(); } else { player.playVideo(); }
          } catch (e) {}
        },
        seekTo: function (seconds) { seek(seconds); },
        seekBy: function (offset) { seek(now() + offset); },
        snapshot: function () {
          var position = now();
          var end = duration();
          if (!isFinite(position)) { position = 0; }
          if (!isFinite(end)) { end = 0; }
          return [position, end, state()];
        }
      };
      window.onYouTubeIframeAPIReady = function () {
        if (!window.YT || !window.YT.Player) { report("error", -2); return; }
        try {
          new YT.Player("p", {
            events: {
              onReady: function (event) {
                player = event.target;
                window.anisequelTrailer.player = player;
                report("ready", duration());
                try { player.playVideo(); } catch (e) {}
              },
              onStateChange: function (event) { report("state", event.data); },
              onError: function (event) { report("error", event.data); }
            }
          });
        } catch (e) { report("error", -2); }
      };
      var api = document.createElement("script");
      api.src = "https://www.youtube.com/iframe_api";
      api.onerror = function () { report("error", -2); };
      var first = document.getElementsByTagName("script")[0];
      first.parentNode.insertBefore(api, first);
    })();
""".trimIndent()
