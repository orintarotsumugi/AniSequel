package com.anisequel.ui.components.detail.info

import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.trailerYoutubeUrl
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.unseamedCover
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.openExternalUrlOrCopy

/**
 * The official trailer: the card that advertises it, and the player it opens.
 *
 * ## Why the player is in the app now
 *
 * Tapping this card used to hand the video to whatever browser the device had -
 * which left the app, lost the title, and on a phone with no browser silently
 * copied a URL to the clipboard instead. A trailer is one of the two things a
 * reader of a title page came for, and leaving the app for it is the sort of
 * hand-off that makes an app feel like a directory of other apps. The card opens
 * [NovaTrailerViewer] now, and the external route is still there as the *escape
 * hatch* inside it: a player that will not play must never be the end of the
 * story, so "Open on YouTube" sits under the frame.
 *
 * ## Why a WebView and not a native player
 *
 * AniList gives the app a YouTube **id**, not a stream. Playing that id needs either
 * YouTube's embed (a web page) or a stream extractor, i.e. a dependency whose whole
 * job is to break when YouTube changes, to fetch content the API did not licence
 * this app to serve. The embed is the same answer the rest of the app already gives
 * for a live SVG card: the one leaf of Chromium that does the job nothing else can,
 * contained. It is also why this is the one `WebView` in the app with JavaScript
 * enabled - the player *is* a web app, and without scripting the embed is a black
 * rectangle. Everything a document could do with that is closed off instead: the
 * page is a fixed wrapper holding one iframe, the only hosts it may load are
 * YouTube's, and any navigation anywhere else is cancelled here and handed to the
 * external opener.
 */
@Composable
internal fun NovaTrailerCard(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val url = media.trailerYoutubeUrl ?: return
    val videoId = media.trailer?.id?.takeIf { it.isNotBlank() } ?: return
    val thumbnail = remember(videoId) { "https://img.youtube.com/vi/$videoId/hqdefault.jpg" }
    val shape = MaterialTheme.shapes.large
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "nova_trailer_play_scale"
    )
    val playContainer by animateColorAsState(
        targetValue = if (pressed) Color.Black.copy(alpha = 0.68f) else Color.Black.copy(alpha = 0.52f),
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nova_trailer_play_container"
    )
    val scrimBottom by animateColorAsState(
        targetValue = if (pressed) Color.Black.copy(alpha = 0.82f) else Color.Black.copy(alpha = 0.76f),
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nova_trailer_scrim"
    )
    var playing by remember(videoId) { mutableStateOf(false) }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .bouncyPress(pressedScale = 0.98f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClickLabel = "Play the official trailer"
            ) { playing = true }
            .testTag("nova_info_trailer_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .unseamedCover()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.05f),
                            0.55f to Color.Black.copy(alpha = 0.22f),
                            1f to scrimBottom
                        )
                    )
            )
            Surface(
                shape = CircleShape,
                color = playContainer,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.42f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
                    .graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
                    }
                    .testTag("nova_info_trailer_play")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppVectorIcons.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(26.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Official trailer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = media.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (playing) {
        NovaTrailerViewer(
            videoId = videoId,
            title = media.displayName,
            fallbackUrl = url,
            onDismiss = { playing = false }
        )
    }
}

/**
 * The trailer, playing over the page.
 *
 * A `Dialog` and not a sheet: this is a video, and a sheet that can be dragged
 * down to a third of the screen is a way to watch a trailer in a letterbox the size
 * of a thumbnail. The window is the whole display, the page behind it is dimmed, and
 * the frame is 16:9 at the top of the safe area so the controls sit where a thumb
 * can reach them.
 *
 * ## Open and close are both animated, and the close finishes before the dismiss
 *
 * `playing` in the card goes false the moment the viewer is popped, so a dialog
 * that simply unmounted would blink out of existence - the one frame that reads as
 * a crash rather than as an exit. [entrance] runs one way for the open and the other
 * for the close, and only then does [onDismiss] tell the caller, which is the same
 * handover `SequelDetailSheet` makes for the title page itself.
 */
@Composable
private fun NovaTrailerViewer(
    videoId: String,
    title: String,
    fallbackUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val entrance = remember(videoId) { Animatable(0f) }
    var isClosing by remember(videoId) { mutableStateOf(false) }
    var loaded by remember(videoId) { mutableStateOf(false) }

    LaunchedEffect(videoId) { entrance.animateTo(1f, ExpressiveMotion.SettleSpatial) }
    LaunchedEffect(videoId, isClosing) {
        if (!isClosing) return@LaunchedEffect
        entrance.animateTo(0f, ExpressiveMotion.SettleSpatial)
        onDismiss()
    }
    val requestClose: () -> Unit = { if (!isClosing) isClosing = true }

    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        // The scrim, the frame and the chrome move as one window: fading only the
        // player would leave a black page pretending to be closed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = entrance.value }
                .background(Color.Black.copy(alpha = 0.94f * entrance.value))
                .padding(horizontal = 12.dp)
                .testTag("nova_trailer_viewer")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .graphicsLayer {
                        // A player that grows into place rather than one that
                        // appears: 0.92 to 1 on the settling spring, no overshoot -
                        // a video frame that passes its own size and comes back
                        // reads as the window resizing, not as the frame arriving.
                        val scale = 0.92f + 0.08f * entrance.value
                        scaleX = scale
                        scaleY = scale
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Close the trailer") {
                                requestClose()
                            }
                            .testTag("nova_trailer_close")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppVectorIcons.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.Black)
                ) {
                    // The loading mark is the page's own wavy ring, spinning: the
                    // frame is a black rectangle for as long as the embed takes to
                    // paint, and a spinner sitting in the middle of it is the
                    // difference between "starting" and "broken".
                    if (!loaded) {
                        NovaWavyRing(
                            progress = null,
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(44.dp)
                        )
                    }
                    NovaTrailerPlayer(
                        videoId = videoId,
                        modifier = Modifier.fillMaxSize(),
                        onLoaded = { loaded = true }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The escape hatch. An embed can be refused - region locks,
                    // age gates, a build with no WebView provider - and a player
                    // with no way out of it is a dead end in the one screen that
                    // took over the whole display.
                    Text(
                        text = "Open on YouTube",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.86f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Open the trailer on YouTube"
                            ) {
                                openExternalUrlOrCopy(context, fallbackUrl)
                                requestClose()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("nova_trailer_external")
                    )
                }
            }
        }
    }
}

/**
 * The embed, in the one `WebView` that may run scripting.
 *
 * Loaded from a wrapper page on `https://www.youtube.com/` rather than pointed at
 * the embed URL directly: YouTube's player checks the referer, and a `WebView`
 * asked for the embed with no origin is served "Video unavailable" often enough
 * that the direct route is not worth taking. The wrapper is a string this function
 * builds from a validated id - no remote document is ever composed into it.
 *
 * The id is validated rather than escaped: AniList's ids are `[A-Za-z0-9_-]`, and
 * anything else is not a trailer. That is what keeps a malformed payload from
 * becoming markup in a page with JavaScript on.
 */
@Suppress("SetJavaScriptEnabled", "OVERRIDE_DEPRECATION")
@Composable
private fun NovaTrailerPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    onLoaded: () -> Unit
) {
    val context = LocalContext.current
    val html = remember(videoId) { novaTrailerEmbedHtml(videoId) }
    AndroidView(
        modifier = modifier.testTag("nova_trailer_player"),
        factory = {
            WebView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.loadsImagesAutomatically = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.mediaPlaybackRequiresUserGesture = false
                val chromeClient = NovaTrailerChromeClient(this)
                webChromeClient = chromeClient
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        onLoaded()
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = handle(playerNav = request?.url?.toString())

                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                        handle(playerNav = url)

                    private fun handle(playerNav: String?): Boolean {
                        // `true` for everything: this view is a player, not a
                        // browser. YouTube's own "watch on YouTube" button and any
                        // link inside the frame leave the app through the same
                        // opener the card used, and nothing else is followed at all.
                        if (playerNav != null && isNovaTrailerHost(playerNav)) return false
                        playerNav?.let { openExternalUrlOrCopy(context, it) }
                        return true
                    }
                }
                loadDataWithBaseURL(
                    NOVA_TRAILER_BASE_URL,
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        onRelease = { view ->
            // Stop before destroy: an embed still buffering when the reader closes
            // the viewer tears down a live media pipeline otherwise. Remove a
            // fullscreen surface first, or it would outlive its WebView in the dialog.
            (view.webChromeClient as? NovaTrailerChromeClient)?.hideCustomView()
            view.stopLoading()
            view.destroy()
        }
    )
}

/**
 * YouTube requires the API's origin to match the wrapper's secure base URL. The
 * old iframe omitted it, so some providers showed a player shell that never started.
 */
private const val NOVA_TRAILER_BASE_URL = "https://www.youtube.com/"
private const val NOVA_TRAILER_ORIGIN_QUERY = "https%3A%2F%2Fwww.youtube.com"

/** Routes YouTube's full-screen video surface above the Compose dialog. */
@Suppress("OVERRIDE_DEPRECATION")
private class NovaTrailerChromeClient(private val webView: WebView) : WebChromeClient() {
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

/** Whether [url] belongs to the embed rather than to the web at large. */
internal fun isNovaTrailerHost(url: String): Boolean {
    val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?').lowercase()
    if (host.isEmpty()) return false
    return NOVA_TRAILER_HOSTS.any { host == it || host.endsWith(".$it") }
}

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
            append("<iframe src=\"https://www.youtube-nocookie.com/embed/")
            append(safeId)
            append("?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=1")
            append("&enablejsapi=1&origin=")
            append(NOVA_TRAILER_ORIGIN_QUERY)
            append("\"")
            append(" referrerpolicy=\"origin\"")
            append(" allow=\"autoplay; encrypted-media; picture-in-picture\"")
            append(" allowfullscreen frameborder=\"0\"></iframe>")
        }
        append("</body></html>")
    }
}
