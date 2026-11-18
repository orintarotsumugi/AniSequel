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
     * Everything LiveChart carries for [queryTitles]: its poster and its gallery.
     *
     * Two requests, because the search page names the poster while the artwork
     * lives on the title's own `/visuals` tab. The second request is skipped when
     * the first found no title match, so a search that comes back empty costs one
     * round trip - the same as the poster-only version this replaces.
     *
     * [searchUrl] is the built search page; [queryTitles] are the candidate titles
     * to match the results against, best first (romaji, then English, then the
     * display name - see [selectLiveChartPoster]). A miss is a miss, never a
     * failure: an empty [LiveChartArtwork] is the honest answer for an offline
     * phone, a 403, or a title LiveChart does not carry.
     */
    suspend fun artwork(searchUrl: String, queryTitles: List<String>): LiveChartArtwork =
        withContext(Dispatchers.IO) {
            val searchHtml = fetch(searchUrl) ?: return@withContext LiveChartArtwork()
            val posterUrl = selectLiveChartPoster(
                parseLiveChartPosterCandidates(searchHtml),
                queryTitles
            )
            // The poster URL carries the title's own id
            // (`/anime/11376/poster_image/...`), so the gallery page is derived
            // from the one result the matcher actually chose. Reading the search
            // page's anchor text instead would be a second, weaker title match for
            // the same answer.
            val animePath = posterUrl?.let(::liveChartAnimePathFromPoster)
            val visualUrls = animePath
                ?.let { path -> fetch(visualsPageUrl(path)) }
                ?.let(::parseLiveChartVisualUrls)
                .orEmpty()
            LiveChartArtwork(posterUrl = posterUrl, visualUrls = visualUrls)
        }

    /**
     * The full-size LiveChart poster for [queryTitles], found on [searchUrl].
     *
     * Kept for the poster-only path (and its tests); the cover viewer asks for
     * [artwork] because it wants the gallery as well. Null when the page has no
     * result close enough to any of them, or on any error.
     */
    suspend fun posterUrl(searchUrl: String, queryTitles: List<String>): String? =
        withContext(Dispatchers.IO) {
            val html = fetch(searchUrl) ?: return@withContext null
            selectLiveChartPoster(parseLiveChartPosterCandidates(html), queryTitles)
        }

    /** One browser-shaped GET. Every failure is "no art", never a thrown screen. */
    private fun fetch(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en")
            // LiveChart's visuals tab is happy to answer a browser navigating
            // from its own search results; the Referer is what makes this request
            // look like that and not like a bot walking URLs.
            .header("Referer", "https://www.livechart.me/")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: CancellationException) {
            // Leaving the viewer cancels this; that is not a failure to swallow.
            throw e
        } catch (e: Exception) {
            // Offline, a 403, a 500, a body that is no longer the page we parse:
            // all of them mean "no extra artwork", never "fail the viewer".
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

/**
 * What LiveChart has for one title: its poster and the artwork on its visuals tab.
 *
 * The owner asked for the gallery, not just the cover - LiveChart's `/visuals`
 * page carries key visuals, character art and seasonal pieces AniList's single
 * banner cannot represent. [visualUrls] is ordered as the page ranks it, so the
 * newest key visual leads the gallery; it is empty for a title with no visuals,
 * and the viewer then falls back to [posterUrl] rather than losing the source.
 *
 * A data class rather than two calls because the poster and the gallery are the
 * same scrape of the same title: caching one without the other would either spend
 * a second search on the next open or show a gallery with no cover behind it.
 */
data class LiveChartArtwork(
    val posterUrl: String? = null,
    val visualUrls: List<String> = emptyList()
) {
    /** True when LiveChart has nothing usable for this title. */
    val isEmpty: Boolean get() = posterUrl == null && visualUrls.isEmpty()
}

/**
 * The title's own LiveChart page, read out of its poster URL.
 *
 * `https://u.livechart.me/anime/11376/poster_image/{hash}.webp/small.jpg` names
 * the anime id in the same path segment the website does, so the visuals page is
 * one string away. Returning a path rather than a full URL keeps this pure and
 * testable; [visualsPageUrl] is the one place the host is spelled.
 */
internal fun liveChartAnimePathFromPoster(posterUrl: String): String? {
    val match = Regex("/anime/(\\d+)(?:/|$)", RegexOption.IGNORE_CASE).find(posterUrl) ?: return null
    return "/anime/${match.groupValues[1]}"
}

/** The gallery tab for one anime path. */
internal fun visualsPageUrl(animePath: String): String =
    "https://www.livechart.me" + animePath.trimEnd('/') + "/visuals"

/**
 * Every visual image on a title's `/visuals` page.
 *
 * The page's own lazy-loading contract puts each card's full-size image in
 * `data-anime-visual-url-value`; the `src` beside it is a thumbnail that is often
 * not even the same crop. Reading the attribute is deliberate: it is the URL the
 * site's lightbox opens, which is the image a full-screen gallery wants.
 *
 * Duplicates are dropped in page order. The same visual is rendered twice in the
 * markup for some cards (a `<picture>` source plus its `<img>` fallback), and a
 * gallery that pages onto the same artwork twice reads as a broken swipe.
 */
internal fun parseLiveChartVisualUrls(html: String): List<String> {
    val urls = VISUAL_URL_ATTR.findAll(html)
        .mapNotNull { match ->
            val raw = match.groupValues[1].ifBlank { match.groupValues[2] }
            normalizeLiveChartImageUrl(raw)
        }
        .distinct()
        .toList()
    return urls.take(MAX_VISUALS)
}

/**
 * One image URL from the visuals page, or null when it is not a usable image.
 *
 * Handles the three spellings the page has used: an absolute URL, a protocol
 * relative one, and a root-relative one. Only LiveChart's image host is accepted,
 * so a tracking pixel, an advertisement or a `data:` placeholder cannot become a
 * full-screen page of nothing.
 */
internal fun normalizeLiveChartImageUrl(raw: String): String? {
    val decoded = decodeHtmlEntities(raw.trim())
    if (decoded.isEmpty() || decoded.startsWith("data:", ignoreCase = true)) return null
    val absolute = when {
        decoded.startsWith("//") -> "https:$decoded"
        decoded.startsWith("http://") -> "https://" + decoded.removePrefix("http://")
        decoded.startsWith("https://") -> decoded
        decoded.startsWith("/") -> "https://www.livechart.me$decoded"
        else -> return null
    }
    return absolute.takeIf { it.contains("livechart.me", ignoreCase = true) }
}

/** The visuals lightbox attribute, accepting either quote style. */
private val VISUAL_URL_ATTR = Regex(
    "\\bdata-anime-visual-url-value\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')",
    RegexOption.IGNORE_CASE
)

/**
 * A ceiling on one title's gallery.
 *
 * Long-running shows collect dozens of character visuals; 40 is more than a
 * reader will page through in one sitting and keeps the parse, the pager and the
 * Coil cache bounded. The page's order is preserved, so the ceiling drops the
 * oldest, least interesting end - not an arbitrary slice.
 */
private const val MAX_VISUALS = 40

/** The poster host, and the path segment only a cover carries - the two together
 *  are what tells a result poster from a nav icon, a footer logo or an ad. */
private const val POSTER_HOST = "u.livechart.me"
private const val POSTER_PATH_MARKER = "/poster_image/"

private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val SRC_ATTR = Regex("\\bsrc\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
private val ALT_ATTR = Regex("\\balt\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)

/**
 * Any quoted attribute that carries a poster URL, whatever the attribute is called.
 *
 * The page is read by attribute *value* rather than by name because the markup has
 * two spellings for the same picture that changed shape as the site started lazily
 * loading its results: `src` is the eager thumbnail, `srcset` carries the 1x and 2x
 * variants, and a lazy result keeps its real URL in a `data-*` attribute with a
 * placeholder in `src`. Matching only `src` (which this did) meant a page whose
 * results had not been hydrated yet produced no candidates at all - the viewer then
 * showed no LiveChart pill and nothing said why. The host and path filter is what
 * keeps nav icons, logos and ads out, so it does not matter what an attribute is
 * named.
 */
private val POSTER_ATTR_VALUE = Regex(
    "\"([^\"]*u\\.livechart\\.me[^\"]*/poster_image/[^\"]*)\"",
    RegexOption.IGNORE_CASE
)

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
        val src = posterSourceIn(tag) ?: return@mapNotNull null
        val alt = decodeHtmlEntities(ALT_ATTR.find(tag)?.groupValues?.getOrNull(1).orEmpty())
        if (alt.isBlank()) return@mapNotNull null
        LiveChartPosterCandidate(title = alt, imageUrl = src)
    }.toList()

/**
 * The poster URL in one `<img>` tag, or null when the tag is not a result poster.
 *
 * `src` is tried first on purpose: it is what the page actually loads, and the
 * `srcset`'s 2x entry is the large poster. Taking the last of those here would
 * make [preferLiveChartLargePoster] a no-op on every result and, worse, would skip
 * the title match by handing the selector an image of the wrong result.
 */
private fun posterSourceIn(tag: String): String? {
    SRC_ATTR.find(tag)?.groupValues?.getOrNull(1)?.trim()?.let { src ->
        if (src.isPosterUrl()) return normalizePosterUrl(src)
    }

    // A srcset value is `url 1x, url 2x`; a data attribute holds one URL. Splitting
    // on both separators and testing each token handles the two shapes, and returns
    // the first (smallest) variant - the swap to large happens once, on the chosen
    // result.
    POSTER_ATTR_VALUE.findAll(tag).forEach { match ->
        match.groupValues[1].split(',', ' ', '\n', '\t').forEach { token ->
            val url = token.trim()
            if (url.isPosterUrl()) return normalizePosterUrl(url)
        }
    }
    return null
}

private fun String.isPosterUrl(): Boolean =
    contains(POSTER_HOST) && contains(POSTER_PATH_MARKER)

/** `//u.livechart.me/...` is the same host over https, and Coil cannot load a
 *  scheme-less URL. */
private fun normalizePosterUrl(url: String): String =
    if (url.startsWith("//")) "https:$url" else url

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

    // What season the reader actually asked for. `null` means the titles name no
    // season - the entry is season one.
    //
    // This is not decoration: AniList spells a season "Season 2" in an English
    // title and "2nd Season" in the romaji one, and LiveChart uses either. Without
    // the number, a query for "Frieren Season 2" did not match "Sousou no Frieren
    // 2nd Season" at all (the words between "Frieren" and "2" differ), fell back
    // to the base title's score, and opened season one's poster for a season-two
    // entry. Stripping the season before comparing fixes the spelling; keeping the
    // number decides which of the base and the sequel is the answer.
    val querySeason = keys.mapNotNull(::seasonNumberIn).maxOrNull()
    val queryBases = keys.map(::withoutSeasonMarker).filter { it.isNotEmpty() }
    if (queryBases.isEmpty()) return null

    var best: LiveChartPosterCandidate? = null
    var bestScore = 0
    var bestLength = Int.MAX_VALUE
    candidates.forEach { candidate ->
        val normalized = normalizeLiveChartTitle(candidate.title)
        if (normalized.isEmpty()) return@forEach
        val candidateSeason = seasonNumberIn(normalized)
        // A season names a *specific* run. If the query names one, only that season
        // can answer; if it names none, a later season is a different show and must
        // not wear season one's name. This is what stops a search page that carries
        // both from handing a sequel's art to the original - and, more importantly,
        // the original's art to a sequel. Both are the wrong picture, and the viewer
        // would have no way to say so.
        if (querySeason != null) {
            if (candidateSeason != querySeason) return@forEach
        } else if ((candidateSeason ?: 1) > 1) {
            return@forEach
        }
        val base = withoutSeasonMarker(normalized)
        if (base.isEmpty()) return@forEach
        val score = queryBases.maxOfOrNull { key ->
            when {
                base == key -> 3
                base.startsWith(key) || key.startsWith(base) -> 2
                base.contains(key) || key.contains(base) -> 1
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

/** "Final Season" answers this, because no number describes it. */
private const val FINAL_LIVE_CHART_SEASON = 99

private val NAMED_SEASON = Regex("""\b(?:season|part|cour)\s+(\d{1,2})\b""")
private val ORDINAL_SEASON = Regex("""\b(\d{1,2})(?:st|nd|rd|th)\s+(?:season|cour)\b""")

/** `s2`, the abbreviation LiveChart and AniList both use in short titles. */
private val SHORT_SEASON = Regex("""(?<![a-z])s(\d{1,2})\b""")

/** A trailing roman numeral, the way `Mushoku Tensei II` names its second run. */
private val TRAILING_ROMAN = Regex("""\s(ii|iii|iv|v|vi|vii|viii|ix|x)$""")

/**
 * The season a folded title declares, or null when it declares none.
 *
 * Deliberately narrower than `SequelSeason` in the domain layer: this is scoring
 * two strings from two websites, not deciding whether to hide a card's poster, and
 * a false positive here merely lowers a candidate's score. It understands the
 * spellings LiveChart's result titles actually use - `Season 2`, `2nd Season`,
 * `Part 2`, `S2`, a trailing roman numeral and `Final Season`.
 */
private fun seasonNumberIn(title: String): Int? {
    if (title.contains("final season")) return FINAL_LIVE_CHART_SEASON
    ORDINAL_SEASON.find(title)?.let { match -> return match.groupValues[1].toIntOrNull() }
    NAMED_SEASON.find(title)?.let { match -> return match.groupValues[1].toIntOrNull() }
    SHORT_SEASON.find(title)?.let { match -> return match.groupValues[1].toIntOrNull() }
    TRAILING_ROMAN.find(title)?.let { match ->
        return when (match.groupValues[1]) {
            "ii" -> 2
            "iii" -> 3
            "iv" -> 4
            "v" -> 5
            "vi" -> 6
            "vii" -> 7
            "viii" -> 8
            "ix" -> 9
            "x" -> 10
            else -> null
        }
    }
    return null
}

/** The title with its season marker removed, so `Season 2` and `2nd Season` meet. */
private fun withoutSeasonMarker(title: String): String =
    title
        .replace("final season", " ")
        .replace(ORDINAL_SEASON, " ")
        .replace(NAMED_SEASON, " ")
        .replace(SHORT_SEASON, " ")
        .replace(TRAILING_ROMAN, " ")
        .replace(MULTI_SPACE, " ")
        .trim()

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
 * The result thumbnail's small variant swapped for the page's own large one.
 *
 * LiveChart renders each stored source image at fixed sizes behind path segments
 * (`.../{hash}.webp/small.jpg` in the thumbnail, `.../{hash}.webp/large.jpg` as the
 * `og:image`), and the format is not always jpg - a webp-master poster offers
 * `small.webp` too. Only the size segment is rewritten and the extension is kept,
 * so the URL still names the same stored image; anything that does not end in a
 * known size segment is returned untouched, so an unexpected shape degrades to
 * "still a valid poster" rather than a broken swap.
 */
private val POSTER_SIZE_SEGMENT = Regex("/(small|medium|thumb)\\.(jpg|jpeg|png|webp)$", RegexOption.IGNORE_CASE)

private fun preferLiveChartLargePoster(url: String): String {
    val match = POSTER_SIZE_SEGMENT.find(url) ?: return url
    val extension = match.groupValues[2]
    return url.substring(0, match.range.first) + "/large." + extension
}

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
