package com.anisequel.ui.components.novamarkdown

import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
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
 * ## What is not negotiable
 *
 * The WebView gets a sanitised SVG, JavaScript disabled, file and content access
 * disabled, mixed content refused, and every navigation cancelled. It is a
 * picture, not a browser: the tap belongs to the Compose card around it, and the
 * view is disabled so it cannot swallow a scroll or a tap meant for its parent.
 */

private const val MAX_SVG_BYTES = 2L * 1024 * 1024
private const val MAX_SVG_CACHE = 12
private const val DEFAULT_SVG_ASPECT = 2f

private const val SVG_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

private val SVG_SCRIPT = Regex("""<script\b[\s\S]*?</script\s*>""", RegexOption.IGNORE_CASE)
private val SVG_EVENT_ATTRIBUTE =
    Regex("""\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""", RegexOption.IGNORE_CASE)
private val SVG_JAVASCRIPT_URL =
    Regex("""\b(href|xlink:href|src)\s*=\s*("javascript:[^"]*"|'javascript:[^']*')""", RegexOption.IGNORE_CASE)
private val SVG_IMPORT = Regex("""@import\b[^;]*;""", RegexOption.IGNORE_CASE)
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

private class SvgCache(private val max: Int) :
    LinkedHashMap<String, NovaSvgAsset>(max, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NovaSvgAsset>?): Boolean =
        size > max
}

private val svgCache = SvgCache(MAX_SVG_CACHE)

private val svgClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
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

    LaunchedEffect(url) {
        state = NovaSvgState.Loading
        state = withContext(Dispatchers.IO) {
            loadNovaSvg(url)?.let { NovaSvgState.Ready(it) } ?: NovaSvgState.Failed
        }
    }

    Box(
        modifier = if (contentDescription.isNullOrBlank()) {
            modifier
        } else {
            modifier.semantics { this.contentDescription = contentDescription }
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

            is NovaSvgState.Ready -> NovaSvgWebView(
                asset = current.asset,
                url = url,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(current.asset.aspectRatio)
            )

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

@Suppress("DEPRECATION")
@Composable
private fun NovaSvgWebView(
    asset: NovaSvgAsset,
    url: String,
    modifier: Modifier
) {
    val html = remember(asset) { novaSvgHtml(asset.markup) }

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
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true

                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
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
        onRelease = { view -> view.destroy() }
    )
}

private fun loadNovaSvg(url: String): NovaSvgAsset? {
    synchronized(svgCache) { svgCache[url] }?.let { return it }

    return try {
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
            val asset = NovaSvgAsset(sanitized, novaSvgAspect(sanitized))
            synchronized(svgCache) { svgCache[url] = asset }
            asset
        }
    } catch (_: Exception) {
        // A blocked host, a timeout and an offline device are the same answer
        // here: the card falls back to the image path. Logging a stack trace for
        // a decorative banner would drown the failures that matter.
        null
    }
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
private fun sanitizeNovaSvg(source: String): String? {
    val start = source.indexOf("<svg", ignoreCase = true)
    if (start < 0) return null
    val end = source.lastIndexOf("</svg>", ignoreCase = true)
    var svg = if (end > start) source.substring(start, end + 6) else source.substring(start)

    svg = SVG_SCRIPT.replace(svg, "")
    svg = SVG_EVENT_ATTRIBUTE.replace(svg, "")
    svg = SVG_JAVASCRIPT_URL.replace(svg, "")
    svg = SVG_IMPORT.replace(svg, "")
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
private fun novaSvgAspect(markup: String): Float {
    val root = SVG_ROOT_TAG.find(markup)?.value ?: return DEFAULT_SVG_ASPECT
    val viewBox = SVG_VIEW_BOX.find(root)?.groupValues?.get(1)
    if (viewBox != null) {
        val values = viewBox.trim().split(Regex("""[\s,]+""")).mapNotNull { it.toFloatOrNull() }
        if (values.size == 4 && values[2] > 0f && values[3] > 0f) {
            return (values[2] / values[3]).coerceIn(0.35f, 4f)
        }
    }
    val width = SVG_WIDTH.find(root)?.groupValues?.get(1)?.toFloatOrNull()
    val height = SVG_HEIGHT.find(root)?.groupValues?.get(1)?.toFloatOrNull()
    if (width != null && height != null && width > 0f && height > 0f) {
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
