package com.anisequel.ui.components.novamarkdown

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * NovaMarkdown's dynamic SVG card.
 *
 * ## Why this is a WebView and not Coil
 *
 * AniList profiles routinely embed live SVG cards - stat cards, signature
 * banners, "currently watching" strips - and those files are not the flat SVG
 * `coil-svg` can draw. They nest base64 `data:image/svg+xml` inside `<image>`,
 * embed WOFF2 fonts in `@font-face`, animate with CSS and use filters.
 * AndroidSVG (which Coil wraps) implements none of the embedded-font or nested
 * image parts, so those cards render as an empty frame. Dantotsu gets them right
 * because it renders the whole bio in a Chromium WebView; NovaMarkdown keeps the
 * document native and gives only this leaf to Chromium. The result is the same
 * rendering engine for the one content type that needs it, without turning a
 * theme-aware, accessible bio into an opaque web page.
 *
 * ## Why it is a WebView for one frame and a bitmap after that
 *
 * Chromium is the right engine and the wrong thing to keep in a `LazyColumn`: each
 * view is a renderer with its own surface, it repaints on every frame of an
 * animated card, and the list destroys and rebuilds it whenever the bio leaves the
 * viewport - so scrolling past three stat cards and back paid for three page loads,
 * on the main thread, in the middle of a fling. The card is drawn once, captured to
 * a bitmap ([captureNovaSvg]), and released; from then on it is an `Image`, which
 * costs a blit instead of a page. A still frame is the trade: an animated banner
 * stops animating, and a bio with six cards stops stuttering.
 *
 * ## What is not negotiable
 *
 * The WebView gets a sanitised SVG, JavaScript disabled, file and content access
 * disabled, mixed content refused, and every navigation cancelled. It is a
 * picture, not a browser: the tap belongs to the Compose card around it, and the
 * view is disabled so it cannot swallow a scroll or a tap meant for its parent.
 */

private const val MAX_SVG_BYTES = 2L * 1024 * 1024

/**
 * The markup cache budget, in bytes of the `String` that is retained.
 *
 * Markup is kept only so a card can be re-rastered at a new width without another
 * request - it is never drawn. Twelve of these at the 2 MiB ceiling was 48 MiB of
 * UTF-16 heap; 8 MiB holds the whole of an ordinary bio several times over.
 */
private const val MAX_SVG_MARKUP_BYTES = 8 * 1024 * 1024

/**
 * The raster cache budget, in bytes of decoded bitmap.
 *
 * Sized against a phone: five typical cards at 1080x480 are about 10 MiB, so this
 * keeps a whole bio's worth on screen and a couple of screens beyond it. Bitmaps
 * live on the Java heap, so this is a budget the collector can act on rather than
 * an unbounded list of native allocations.
 */
private const val MAX_SVG_RASTER_BYTES = 32 * 1024 * 1024

private const val DEFAULT_SVG_ASPECT = 2f

private const val SVG_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

private val SVG_SCRIPT = Regex("""<script\b[\s\S]*?</script\s*>""", RegexOption.IGNORE_CASE)
private val SVG_EVENT_ATTRIBUTE =
    Regex("""\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""", RegexOption.IGNORE_CASE)
private val SVG_JAVASCRIPT_URL = Regex(
    """\b(href|xlink:href|src)\s*=\s*(?:"\s*javascript:[^"]*"|'\s*javascript:[^']*'|javascript:[^\s>]+)""",
    RegexOption.IGNORE_CASE,
)
private val SVG_IMPORT = Regex("""@import\b[^;]*;""", RegexOption.IGNORE_CASE)
private val SVG_FOREIGN_OBJECT = Regex(
    """<foreignObject\b[^>]*>[\s\S]*?</foreignObject\s*>""",
    RegexOption.IGNORE_CASE,
)
private val SVG_ACTIVE_TAG = Regex(
    """</?(iframe|object|embed|form|input|button|textarea|select|body|html)\b[^>]*>""",
    RegexOption.IGNORE_CASE,
)
private val SVG_ROOT_TAG = Regex("""<svg\b[^>]*>""", RegexOption.IGNORE_CASE)
private val SVG_VIEW_BOX = Regex("""viewBox\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val SVG_WIDTH = Regex("""\bwidth\s*=\s*["']?([0-9.]+)""", RegexOption.IGNORE_CASE)
private val SVG_HEIGHT = Regex("""\bheight\s*=\s*["']?([0-9.]+)""", RegexOption.IGNORE_CASE)

internal data class NovaSvgAsset(
    val markup: String,
    val aspectRatio: Float
)

private sealed interface NovaSvgState {
    data object Loading : NovaSvgState
    data class Ready(val asset: NovaSvgAsset) : NovaSvgState
    data object Failed : NovaSvgState
}

/** Sanitised documents, keyed by URL. Read on the main thread, filled off it. */
private val svgMarkup = NovaByteCache<NovaSvgAsset>(MAX_SVG_MARKUP_BYTES) { it.markup.length * 2 }

/**
 * Captured cards, keyed by URL *and* slot size - see [novaSvgRasterKey].
 *
 * Written from the main thread only: the capture runs on the frame that finishes
 * the page load, which is where `LazyColumn` can hand the same raster straight back
 * on the next pass. The budget is what stops a long bio from pinning thirty
 * bitmaps for the life of the process.
 */
private val svgRasters = NovaByteCache<Bitmap>(MAX_SVG_RASTER_BYTES) { it.allocationByteCount }

/**
 * Loads already in flight, so two copies of one banner are one request.
 *
 * A bio that repeats a signature strip - or an author who splits the same card
 * across two paragraphs - used to download and sanitise it twice, concurrently, and
 * every byte of that happened while the reader was waiting for the card.
 */
private val svgInFlight = ConcurrentHashMap<String, CompletableDeferred<NovaSvgAsset?>>()

private val svgClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", SVG_USER_AGENT)
                    .header("Accept", "image/svg+xml,image/*,*/*")
                    .build()
            )
        }
        .build()
}

@Composable
internal fun NovaDynamicSvg(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var state by remember(url) { mutableStateOf<NovaSvgState>(NovaSvgState.Loading) }
    var visible by remember(url) { mutableStateOf(false) }
    val rootView = LocalView.current.rootView

    LaunchedEffect(url) {
        snapshotFlow { visible }.first { it }
        state = loadNovaSvg(url)?.let { NovaSvgState.Ready(it) } ?: NovaSvgState.Failed
    }

    Box(
        modifier = (if (contentDescription.isNullOrBlank()) {
            modifier
        } else {
            modifier.semantics { this.contentDescription = contentDescription }
        }).onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            val width = rootView.width.toFloat()
            val height = rootView.height.toFloat()
            // Long bios compose their markdown blocks together. Fetching every
            // remote card at once delayed the first visible one and made a profile
            // start many decorative requests it might never scroll down to.
            visible = coordinates.isAttached && width > 0f && height > 0f &&
                bounds.right > 0f && bounds.bottom > 0f &&
                bounds.left < width && bounds.top < height
        },
        contentAlignment = Alignment.Center
    ) {
        when (val current = state) {
            NovaSvgState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(DEFAULT_SVG_ASPECT),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            is NovaSvgState.Ready -> NovaSvgArtwork(url = url, asset = current.asset)

            // A failed fetch is not an error page. Coil may still draw a flat SVG
            // that failed Chromium's stricter path; if it cannot, its own error
            // painter says so, which is the same treatment every other image gets.
            NovaSvgState.Failed -> AsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(DEFAULT_SVG_ASPECT),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * The card itself: a cached raster if there is one, a WebView until there is.
 *
 * The slot is measured before the cache is consulted, because the raster is only
 * valid for the width it was captured at - a rotation lands here with a different
 * `slot` and a miss, which is what puts a WebView back on screen exactly once.
 */
@Composable
private fun NovaSvgArtwork(url: String, asset: NovaSvgAsset) {
    var slot by remember(url) { mutableStateOf(IntSize.Zero) }
    var captured by remember(url) { mutableStateOf<Pair<IntSize, Bitmap>?>(null) }
    var failedCaptureSize by remember(url) { mutableStateOf<IntSize?>(null) }

    val raster = captured?.takeIf { it.first == slot }?.second
        ?: if (slot == IntSize.Zero) {
            null
        } else {
            svgRasters.get(novaSvgRasterKey(url, slot.width, slot.height))
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(asset.aspectRatio)
            .onSizeChanged { slot = it }
    ) {
        if (raster != null) {
            NovaSvgRasterImage(bitmap = raster)
        } else if (slot == IntSize.Zero) {
            // onSizeChanged runs after this slot's first measure; do not create a
            // renderer for a zero-sized placeholder that is replaced immediately.
            Box(Modifier.fillMaxSize())
        } else if (failedCaptureSize == slot) {
            // A WebView that cannot produce software pixels stayed alive forever,
            // repainting during every fling. Prefer Coil's SVG decoder after the
            // bounded capture attempts: simpler SVGs still render, and scrolling
            // no longer carries a Chromium surface that cannot be cached.
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            // Slot size is part of the capture session. A resize needs fresh
            // attempts; a previously exhausted session left the new-size card as
            // a live WebView permanently.
            key(url, slot) {
                NovaSvgWebView(
                    asset = asset,
                    url = url,
                    modifier = Modifier.fillMaxSize(),
                    onCaptured = { bitmap, width, height ->
                        val size = IntSize(width, height)
                        svgRasters.put(novaSvgRasterKey(url, width, height), bitmap)
                        captured = size to bitmap
                        failedCaptureSize = null
                    },
                    onCaptureFailed = { failedCaptureSize = slot }
                )
            }
        }
    }
}

@Composable
private fun NovaSvgRasterImage(bitmap: Bitmap) {
    // Wrapped once per bitmap rather than per frame: `asImageBitmap` allocates, and
    // this is called from a composable that sits in a scrolling list.
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize()
    )
}

@Suppress("DEPRECATION")
@Composable
private fun NovaSvgWebView(
    asset: NovaSvgAsset,
    url: String,
    modifier: Modifier,
    onCaptured: (bitmap: Bitmap, width: Int, height: Int) -> Unit,
    onCaptureFailed: () -> Unit
) {
    val html = remember(asset) { novaSvgHtml(asset.markup) }
    val captured = rememberUpdatedState(onCaptured)
    // Keyed on the URL: a session that has spent its capture attempts on one card
    // must not be inherited by the next one, or that card would never be captured.
    val session = remember(url) { NovaSvgCaptureSession() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.loadsImagesAutomatically = true
                settings.blockNetworkImage = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.mediaPlaybackRequiresUserGesture = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                isClickable = false
                // A disabled view does not consume touch, so the Compose card's
                // tap and the LazyColumn's scroll reach their owner. CSS/SMIL
                // animation still runs; it does not need input.
                isEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String?) {
                        val card = view
                        // The capture hands back the pixels and the caller swaps
                        // this view for them, which is what releases Chromium.
                        session.schedule(
                            view = card,
                            onCaptured = { bitmap ->
                                captured.value(bitmap, card.width, card.height)
                            },
                            onExhausted = { onCaptureFailed() }
                        )
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true
                    // The deprecated `shouldOverrideUrlLoading(WebView, String)`
                    // override is deliberately absent, not forgotten: the platform
                    // stopped calling it in API 24, and this app's floor is 31, so
                    // the copy that used to sit here was a branch that could never
                    // run - it only produced an OVERRIDE_DEPRECATION warning.
                }
                tag = url
                loadDataWithBaseURL("https://anilist.co/", html, "text/html", "UTF-8", null)
            }
        },
        update = { view ->
            if (view.tag != url) {
                view.tag = url
                view.loadDataWithBaseURL("https://anilist.co/", html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { view ->
            // The order matters: a capture scheduled for the next frame must not
            // touch a destroyed WebView, and a page still loading must be stopped
            // before the view it is loading into goes away.
            session.released = true
            view.stopLoading()
            view.destroy()
        }
    )
}

/**
 * The sanitised document for [url], from the cache, from a request, or from the
 * request already made for an identical card.
 */
private suspend fun loadNovaSvg(url: String): NovaSvgAsset? {
    svgMarkup.get(url)?.let { return it }

    val mine = CompletableDeferred<NovaSvgAsset?>()
    val pending = svgInFlight.putIfAbsent(url, mine)
    if (pending != null) return pending.await()

    var asset: NovaSvgAsset? = null
    try {
        asset = withContext(Dispatchers.IO) { fetchNovaSvg(url) }
        // Publish before waking waiters. The old order left a small window where
        // a second caller missed both the in-flight entry and cache, then fetched
        // and sanitised the same card again.
        asset?.let { svgMarkup.put(url, it) }
        return asset
    } finally {
        svgInFlight.remove(url, mine)
        mine.complete(asset)
    }
}

private fun fetchNovaSvg(url: String): NovaSvgAsset? = try {
    val request = Request.Builder().url(url).get().build()
    svgClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        if (body.contentLength() > MAX_SVG_BYTES) return null

        val source = body.source()
        val buffer = Buffer()
        var total = 0L
        while (true) {
            val read = source.read(buffer, 8192L)
            if (read == -1L) break
            total += read
            if (total > MAX_SVG_BYTES) return null
        }
        val text = buffer.readUtf8()
        val sanitized = sanitizeNovaSvg(text) ?: return null
        NovaSvgAsset(sanitized, novaSvgAspect(sanitized))
    }
} catch (_: Exception) {
    // A blocked host, a timeout and an offline device are the same answer here:
    // the card falls back to the image path. Logging a stack trace for a
    // decorative banner would drown the failures that matter.
    null
}

/**
 * The SVG document, and nothing that can act.
 *
 * A remote SVG is HTML's sibling: it can carry `<script>`, `onload` handlers and
 * `javascript:` hrefs. JavaScript is disabled in the WebView as well, but
 * sanitising here means a future caller cannot lose that protection by rendering
 * the same markup somewhere else. The document is cut from the first `<svg>` to
 * the last `</svg>` so an XML prolog or a wrapper page does not become the
 * document body.
 */
internal fun sanitizeNovaSvg(source: String): String? {
    val start = source.indexOf("<svg", ignoreCase = true)
    if (start < 0) return null
    val end = source.lastIndexOf("</svg>", ignoreCase = true)
    var svg = if (end > start) source.substring(start, end + 6) else source.substring(start)

    svg = SVG_SCRIPT.replace(svg, "")
    svg = SVG_EVENT_ATTRIBUTE.replace(svg, "")
    svg = SVG_JAVASCRIPT_URL.replace(svg, "")
    svg = SVG_IMPORT.replace(svg, "")
    svg = SVG_FOREIGN_OBJECT.replace(svg, "")
    svg = SVG_ACTIVE_TAG.replace(svg, "")
    return svg
}

/**
 * The aspect ratio the SVG asks for, so layout reserves the right height before
 * Chromium paints.
 *
 * `viewBox` wins over `width`/`height`: a card is usually written
 * `width="100%" viewBox="0 0 1200 630"`, and the percentage says nothing about
 * shape. The result is clamped to a sane portrait-to-landscape band so a
 * malformed `viewBox` cannot reserve a 40-screen-tall card.
 */
internal fun novaSvgAspect(markup: String): Float {
    val root = SVG_ROOT_TAG.find(markup)?.value ?: return DEFAULT_SVG_ASPECT
    val viewBox = SVG_VIEW_BOX.find(root)?.groupValues?.get(1)
    if (viewBox != null) {
        val values = viewBox.trim().split(Regex("""[\s,]+""")).mapNotNull { it.toFloatOrNull() }
        if (values.size == 4 && values[2].isFinite() && values[3].isFinite() &&
            values[2] > 0f && values[3] > 0f
        ) {
            return (values[2] / values[3]).coerceIn(0.35f, 4f)
        }
    }
    val width = SVG_WIDTH.find(root)?.groupValues?.get(1)?.toFloatOrNull()
    val height = SVG_HEIGHT.find(root)?.groupValues?.get(1)?.toFloatOrNull()
    if (width != null && height != null && width.isFinite() && height.isFinite() &&
        width > 0f && height > 0f
    ) {
        return (width / height).coerceIn(0.35f, 4f)
    }
    return DEFAULT_SVG_ASPECT
}

/**
 * A transparent page whose only child is the SVG.
 *
 * `width:100%` and `height:auto` let the SVG scale to the Compose slot instead of
 * its authored pixel width; `overflow:hidden` stops a stray element from drawing
 * a scrollbar inside a card that is not scrollable.
 */
private fun novaSvgHtml(svg: String): String = buildString {
    append("<!doctype html><html><head>")
    append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">")
    append("<style>")
    append("html,body{margin:0;padding:0;background:transparent;overflow:hidden;}")
    append("svg{display:block;width:100%;height:auto;max-width:100%;}")
    append("img{max-width:100%;height:auto;}")
    append("</style></head><body>")
    append(svg)
    append("</body></html>")
}
