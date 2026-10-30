package com.anisequel.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * LiveChart's own poster for one title, read from its public search page.
 *
 * ## The owner reversed the "link, never scrape" rule
 *
 * This file did not exist, and [com.anisequel.ui.components.LiveChart] said so:
 * LiveChart publishes no API, so the app only ever linked out to its search and
 * drew no LiveChart art. The owner has since decided that LiveChart's posters -
 * which are more numerous and higher quality than AniList's for many titles -
 * are worth showing as an extra source in the full-screen cover viewer. That is a
 * deliberate policy change, not an oversight: the viewer keeps scraping nothing
 * else from the site, and the outbound "LiveChart" link still goes to the search
 * page it always did. The one thing read is the poster thumbnail of the best
 * title match.
 *
 * ## Why this reads the search page rather than an endpoint
 *
 * There is no endpoint to call - LiveChart's FAQ offers only RSS for the
 * schedule, and its artwork is on its pages, not behind an API. The search page
 * (`https://www.livechart.me/search?q={term}`, the shape the site advertises in
 * its own schema.org `SearchAction`) lists each result with its poster already in
 * the HTML, so the whole job is: fetch that one page, find the result whose title
 * matches, take its image. The URL is built by the caller
 * ([com.anisequel.ui.components.liveChartUrl]) so the data layer stays free of the
 * UI layer, and this file never imports either.
 *
 * ## A browser User-Agent is required, not polite
 *
 * Verified against the live site: `https://www.livechart.me` answers **HTTP 403**
 * to a non-browser agent (OkHttp's own default included) and only returns the
 * page for a normal desktop Chrome string. The poster host `u.livechart.me` is
 * less strict - Coil loads the image straight from the URL - but the *page* the
 * URL is discovered on needs the header below, or the fetch returns nothing and
 * the pill simply never appears.
 *
 * ## What it is not allowed to do
 *
 * - **Never send the AniList token.** This is a third party, and the client below
 *   is its own `OkHttpClient` with no interceptors, exactly as
 *   [AnimeThemesApi] and the update client keep theirs.
 * - **Never block the viewer.** Short timeouts, `Dispatchers.IO`, and every failure
 *   path returns null. The AniList poster and banner paint immediately; a LiveChart
 *   cover is an addition that arrives later or not at all, and a 403, an offline
 *   phone or a page that changed shape all mean "one fewer pill", never "no cover".
 */
class LiveChartPosterApi(
    private val client: OkHttpClient = defaultClient()
) {

    /**
     * The full-size LiveChart poster for [queryTitles], found on [searchUrl].
     *
     * [searchUrl] is the built search page; [queryTitles] are the candidate titles
     * to match the results against, best first (romaji, then English, then the
     * display name - see [selectLiveChartPoster]). Null when the page has no result
     * close enough to any of them, or on any error.
     */
    suspend fun posterUrl(searchUrl: String, queryTitles: List<String>): String? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val html = response.body?.string() ?: return@withContext null
                    selectLiveChartPoster(parseLiveChartPosterCandidates(html), queryTitles)
                }
            } catch (e: CancellationException) {
                // Leaving the viewer cancels this; that is not a failure to swallow.
                throw e
            } catch (e: Exception) {
                // Offline, a 403, a 500, a body that is no longer the page we parse:
                // all of them mean "no extra cover", never "fail the viewer".
                null
            }
        }

    private companion object {
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            // A ceiling on the whole call, so a slow edge cannot hold the fetch (and
            // the promise the viewer waits on) open indefinitely the way a bare
            // `readTimeout` can when bytes trickle in.
            .callTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * One result on a LiveChart search page: its title as the site spells it, and the
 * URL of the poster thumbnail it drew for that result.
 */
internal data class LiveChartPosterCandidate(
    val title: String,
    val imageUrl: String
)

/** The poster host, and the path segment only a cover carries - the two together
 *  are what tells a result poster from a nav icon, a footer logo or an ad. */
private const val POSTER_HOST = "u.livechart.me"
private const val POSTER_PATH_MARKER = "/poster_image/"

private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val SRC_ATTR = Regex("\\bsrc\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
private val ALT_ATTR = Regex("\\balt\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)

/**
 * Every poster thumbnail a LiveChart search page carries, in the order the site
 * ranked them.
 *
 * Pure and separate from the request because this is the half that breaks when the
 * site's markup shifts, and the half a JVM test can pin without a network. Each
 * result's poster `img` is tagged with the title as its `alt`, so a single pass
 * over the `img` tags yields both the match key and the URL; the host and path
 * filter drops everything that is not a cover.
 */
internal fun parseLiveChartPosterCandidates(html: String): List<LiveChartPosterCandidate> =
    IMG_TAG.findAll(html).mapNotNull { match ->
        val tag = match.value
        val src = SRC_ATTR.find(tag)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (!src.contains(POSTER_HOST) || !src.contains(POSTER_PATH_MARKER)) return@mapNotNull null
        val alt = decodeHtmlEntities(ALT_ATTR.find(tag)?.groupValues?.getOrNull(1).orEmpty())
        if (alt.isBlank()) return@mapNotNull null
        LiveChartPosterCandidate(title = alt, imageUrl = src)
    }.toList()

/**
 * The best-matching result's full-size poster URL, or null when nothing is close.
 *
 * The search page is already ranked by LiveChart, so "best" means the strongest
 * title agreement rather than the first result: an exact match wins outright, then
 * a title that is the query with a suffix (a sequel season) or the query inside it,
 * then any partial overlap. Ties go to the *shortest* candidate so a search for
 * "Sousou no Frieren" settles on the base title rather than its 2nd Season, and a
 * candidate with no overlap at all is dropped rather than returned - showing a
 * poster for an unrelated show is worse than showing no extra pill.
 *
 * The returned URL is the `large.jpg` variant LiveChart itself uses for its
 * `og:image`, not the `small.jpg` the result thumbnail loads, because the whole
 * point of this source is the higher-quality art.
 */
internal fun selectLiveChartPoster(
    candidates: List<LiveChartPosterCandidate>,
    queryTitles: List<String>
): String? {
    val keys = queryTitles.map(::normalizeLiveChartTitle).filter { it.isNotEmpty() }
    if (keys.isEmpty() || candidates.isEmpty()) return null

    var best: LiveChartPosterCandidate? = null
    var bestScore = 0
    var bestLength = Int.MAX_VALUE
    candidates.forEach { candidate ->
        val normalized = normalizeLiveChartTitle(candidate.title)
        if (normalized.isEmpty()) return@forEach
        val score = keys.maxOfOrNull { key ->
            when {
                normalized == key -> 3
                normalized.startsWith(key) || key.startsWith(normalized) -> 2
                normalized.contains(key) || key.contains(normalized) -> 1
                else -> 0
            }
        } ?: 0
        if (score <= 0) return@forEach
        if (score > bestScore || (score == bestScore && normalized.length < bestLength)) {
            best = candidate
            bestScore = score
            bestLength = normalized.length
        }
    }
    return best?.let { preferLiveChartLargePoster(it.imageUrl) }
}

/** Punctuation and case are noise for matching; LiveChart and AniList spell the
 *  same title with different separators routinely. */
private val NON_TITLE_CHARS = Regex("[^\\p{L}\\p{N} ]+")
private val MULTI_SPACE = Regex("\\s+")

/** A title folded to the comparable form [selectLiveChartPoster] scores on. */
internal fun normalizeLiveChartTitle(value: String): String =
    value.lowercase(Locale.ROOT)
        .replace(NON_TITLE_CHARS, " ")
        .replace(MULTI_SPACE, " ")
        .trim()

/**
 * The result thumbnail's `small.jpg` swapped for the page's own `large.jpg`.
 *
 * LiveChart renders each stored source image at fixed sizes behind path segments
 * (`.../{hash}.webp/small.jpg` in the thumbnail, `.../{hash}.webp/large.jpg` as the
 * `og:image`). A URL that does not end in that segment is returned untouched, so an
 * unexpected shape degrades to "still a valid poster" rather than a broken swap.
 */
private fun preferLiveChartLargePoster(url: String): String =
    if (url.endsWith("/small.jpg")) url.removeSuffix("/small.jpg") + "/large.jpg" else url

/**
 * The handful of entities a title's `alt` actually carries. Deliberately not a
 * full HTML decoder - these are titles, not bodies, and `&amp;` is done last so an
 * encoded `&amp;quot;` cannot double-decode into a stray quote.
 */
private fun decodeHtmlEntities(value: String): String =
    value
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
