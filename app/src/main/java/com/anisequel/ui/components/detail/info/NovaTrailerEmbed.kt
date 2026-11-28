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
 * ## Why visibility changes, not alpha
 *
 * A `WebView` is a real Android view in Compose's interop layer. Applying alpha or
 * scale compositing around its video surface can leave audio and player chrome
 * visible while the video plane is black. The view therefore stays fully opaque and
 * hardware-backed; it starts `INVISIBLE` and becomes `VISIBLE` only when it is
 * ready to draw (or the watchdog has to reveal the fallback surface). The Compose
 * loading ring sits above it only while it is invisible, rather than trying to fade
 * through a native video surface.
 *
 * ## What counts as player-ready
 *
 * The wrapper's own load event only says that the local HTML document loaded; it
 * does not say YouTube can accept playback commands. The YouTube IFrame Player API's
 * `onReady` event is the signal used for both revealing the player and enabling the
 * native controls. Its `onStateChange` event keeps the play/pause button in sync.
 * No JavaScript interface is exposed to the remote frame: the wrapper reports these
 * two events through the WebView console. [NOVA_TRAILER_REVEAL_TIMEOUT_MILLIS] remains
 * the last-resort escape if the provider or remote API never reports readiness.
 */
@Suppress("SetJavaScriptEnabled", "OVERRIDE_DEPRECATION")
internal fun createNovaTrailerPlayer(
    context: Context,
    html: String,
    onReady: () -> Unit,
    onPlayerReady: () -> Unit,
    onPlaybackStateChanged: (Int) -> Unit,
    onPlayerError: (Int) -> Unit
): WebView {
    val webView = WebView(context)
    var revealed = false
    // Idempotent: the IFrame API's ready event and the watchdog can both request reveal.
    val reveal: () -> Unit = {
        if (!revealed) {
            revealed = true
            webView.visibility = View.VISIBLE
            onReady()
        }
    }
    webView.apply {
        // Keep the native video surface opaque. The visibility switch below is the
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
            onEmbedReady = reveal,
            onPlayerReady = onPlayerReady,
            onPlaybackStateChanged = onPlaybackStateChanged,
            onPlayerError = onPlayerError
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
    // A player that says nothing at all - no WebView provider, a script the page
    // declined to run - still has to stop being a spinner eventually.
    val timeout = Runnable { reveal() }
    webView.postDelayed(timeout, NOVA_TRAILER_REVEAL_TIMEOUT_MILLIS)
    // A dialog can be closed before it fires, and releasing the player calls
    // `destroy()`. A callback still queued past that runs the reveal against a
    // view the composition has already thrown away, and holds the WebView - with
    // the page it decoded and its audio decoder - alive for the rest of the seven
    // seconds. Removing it on detach is the one hook that fires both for a close
    // and for the rotation that recreates the dialog.
    webView.addOnAttachStateChangeListener(
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                view.removeCallbacks(timeout)
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

/** Emitted only when YouTube's IFrame API reports that the player is ready. */
private const val NOVA_TRAILER_PLAYER_READY_MESSAGE = "anisequel:trailer-player-ready"

/** Playback-state events forwarded from the IFrame API to Compose. */
private const val NOVA_TRAILER_STATE_PREFIX = "anisequel:trailer-state:"

/**
 * The IFrame API's `onError` code, forwarded so Compose can stop showing the frame.
 *
 * Without this a refused video (101/150 owner blocked embeds, 100 removed, 153 a
 * missing referer) rendered YouTube's own "Video unavailable" card inside the 16:9
 * hole, with the controls live over it and no way out but the small link underneath.
 */
private const val NOVA_TRAILER_ERROR_PREFIX = "anisequel:trailer-error:"

/** The last-resort reveal: a player that never reports must not spin forever. */
private const val NOVA_TRAILER_REVEAL_TIMEOUT_MILLIS = 7000L

/** Routes YouTube's full-screen video surface above the Compose dialog. */
@Suppress("OVERRIDE_DEPRECATION")
internal class NovaTrailerChromeClient(
    private val webView: WebView,
    private val onEmbedReady: () -> Unit,
    private val onPlayerReady: () -> Unit,
    private val onPlaybackStateChanged: (Int) -> Unit,
    private val onPlayerError: (Int) -> Unit
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
        val text = message.message()
        when {
            text == NOVA_TRAILER_PLAYER_READY_MESSAGE -> {
                onEmbedReady()
                onPlayerReady()
                return true
            }
            text.startsWith(NOVA_TRAILER_ERROR_PREFIX) -> {
                val code = text.substringAfter(NOVA_TRAILER_ERROR_PREFIX).toIntOrNull()
                    ?: return super.onConsoleMessage(message)
                onPlayerError(code)
                return true
            }
            text.startsWith(NOVA_TRAILER_STATE_PREFIX) -> {
                val state = text.substringAfter(NOVA_TRAILER_STATE_PREFIX).toIntOrNull()
                    ?: return super.onConsoleMessage(message)
                onPlaybackStateChanged(state)
                return true
            }
        }
        return super.onConsoleMessage(message)
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
 */
internal fun novaTrailerEmbedHtml(videoId: String): String {
    val safeId = if (isNovaTrailerId(videoId)) videoId else ""
    return buildString {
        append("<!doctype html><html><head>")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">")
        append("<style>")
        append("html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden;}")
        append("iframe{display:block;width:100%;height:100%;border:0;}")
        append("</style></head><body>")
        if (safeId.isEmpty()) {
            // An empty frame rather than an error string: the card only offers a
            // viewer for an id it just read, so this is the malformed case, and
            // the escape hatch under the frame is where the reader goes next.
            append("<div></div>")
        } else {
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
            // Readiness is the IFrame API's onReady event, not the iframe's earlier
            // network load. Until then the WebView stays invisible and the ring stays
            // visible; the independent WebView watchdog remains the last-resort escape.
            append("<script>(function(){")
            append("window.anisequelTrailer={player:null,")
            append("seek:function(offset){var p=this.player;if(!p){return;}")
            append("var duration=p.getDuration()||0;")
            append("var target=Math.max(0,p.getCurrentTime()+offset);")
            append("if(duration>0){target=Math.min(duration,target);}")
            append("p.seekTo(target,true);},")
            append("toggle:function(){var p=this.player;if(!p){return;}")
            append("if(p.getPlayerState()===1){p.pauseVideo();}else{p.playVideo();}}};")
            append("window.onYouTubeIframeAPIReady=function(){")
            append("window.anisequelTrailer.player=new YT.Player('p',{events:{")
            append("onReady:function(){console.log('")
            append(NOVA_TRAILER_PLAYER_READY_MESSAGE)
            append("');},onStateChange:function(event){console.log('")
            append(NOVA_TRAILER_STATE_PREFIX)
            append("'+event.data);},onError:function(event){console.log('")
            append(NOVA_TRAILER_ERROR_PREFIX)
            append("'+event.data);}}});};")
            append("var api=document.createElement('script');")
            append("api.src='https://www.youtube.com/iframe_api';")
            append("var first=document.getElementsByTagName('script')[0];")
            append("first.parentNode.insertBefore(api,first);")
            append("})();</script>")
        }
        append("</body></html>")
    }
}
