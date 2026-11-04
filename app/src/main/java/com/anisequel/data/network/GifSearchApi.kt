package com.anisequel.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GIF search, from whichever provider the reader has chosen.
 *
 * ## The provider set, and why it is this one (November 2026)
 *
 * - **Safebooru** is the anonymous default. It is asked for `animated_gif` at
 *   `rating:safe`, its catalogue is anime, and it needs no account. See
 *   [SafebooruGifs] and [booruTiers] for how a sentence becomes three tag
 *   spellings one search is asked as.
 * - **nekos.best** is the second keyless index. It is small and reaction-shaped
 *   (`hug`, `pat`, `wave`), and it needs a User-Agent that names the app. See
 *   [NekosBestGifs].
 * - **Klipy** is the closest drop-in replacement for Tenor: a key in the path,
 *   page-numbered results, and `file.hd` / `file.sm` copies. See [KlipyGifs].
 * - **GIPHY** is the name everyone expects. Every new key is a beta key capped at
 *   100 calls an hour, its dimensions are JSON **strings**, and its terms forbid
 *   mixing its results into a grid with another provider's. See [GiphyGifs].
 * - **Tenor** is kept for an install that already saved a key, but its public API
 *   was retired in 2026, so it is no longer the default and its failures say so.
 *
 * A key is never bundled. The anonymous default is what lets somebody use the app
 * without handing over a credential, and a committed key would be one rate limit
 * shared by every install plus a secret in a public repository.
 *
 * ## What was measured and rejected
 *
 * Recorded so the next reader does not spend an afternoon re-discovering them:
 *
 * - **Gelbooru** DAPI: HTTP 401 for every anonymous shape; it now needs an
 *   account-issued `api_key` plus `user_id`.
 * - **Rule34**: HTTP 403 with a CAPTCHA page for any user agent.
 * - **Danbooru** `posts.json`: answers 200 anonymously and its CDN hotlinks fine,
 *   but the anonymous search is capped at **two tags per request**, and its default
 *   result set includes deleted and `q`-rated posts.
 * - **Wikimedia Commons**: `mimetype:image/gif` / `filetype:gif` searches return
 *   `totalhits: 0` - MIME search is switched off at the API level.
 * - **Openverse**: responds slowly, caps anonymous callers at 20 a minute, and has
 *   no anime reaction-GIF catalogue.
 * - **DuckDuckGo's** image endpoint: a bot challenge.
 * - **Konachan.net**: HTTP 522, origin not answering.
 *
 * ## Never the AniList token
 *
 * Every client is its own [OkHttpClient] with no interceptors, the same rule
 * [AnimeThemesApi] and [CatboxApi] record: the shared client attaches the reader's
 * bearer token to everything, and a GIF index has no business holding it.
 */
interface GifSearchApi {
    /**
     * Which index this picker is searching, in the words a reader would use.
     *
     * Asked for because the panel's hints are provider-specific and its results
     * are not the app's answer: a reader who is told to "try fewer words" should
     * also be able to see *whose* catalogue is being shallow. Blank means an
     * index the app does not name, and a picker then says just "a GIF index".
     */
    val providerName: String get() = ""

    /**
     * The sentence shown when this index is answering *instead of* the one the
     * reader chose.
     *
     * Null for every client that is answering because it was asked to. Set only on
     * the anonymous fallback, because a substitution that is not said out loud is
     * the reader searching Klipy, getting Safebooru's catalogue, and no way to find
     * out that the difference was a key they never saved. The picker's placeholder
     * names the index, which says *what*; this says *why* and where to fix it.
     */
    val substitutionNote: String? get() = null

    /**
     * Whether an empty answer is a gap in this provider's catalogue rather than a
     * miss.
     *
     * A booru matches tags, not sentences, so an obscure phrase can legitimately
     * return nothing - and the reader deserves the sentence that says what the index
     * can still be asked for rather than a grid that looks broken. A tagged catalogue
     * like Tenor's has no such hole, so it does not get the explanation.
     */
    val catalogueIsNarrow: Boolean get() = false

    /**
     * The sentence under an empty grid.
     *
     * It lives on the provider because the useful advice is specific to the index
     * that came back empty: a booru needs "name one tag", nekos.best needs "try a
     * reaction", and a keyed general index needs neither. The picker used to build
     * the booru sentence itself, which meant the second keyless provider got the
     * booru's explanation of a failure it does not have.
     */
    fun emptyHint(query: String): String = "Nothing found for \"$query\"."

    /**
     * One page of results for [query].
     *
     * [cursor] is whatever the previous page said to ask for next - a zero-based
     * post index for Safebooru, a `pos` token for Tenor - which is why it is a
     * string and not an int: the providers do not page the same way and neither
     * should have to pretend to. Null asks for the first page.
     *
     * A blank query is not a search: the caller is expected not to ask, and
     * implementations answer an empty page rather than an error, so a picker that
     * has not typed anything yet draws its placeholder instead of a failure.
     */
    suspend fun search(query: String, cursor: String? = null): Result<GifPage>
}

/**
 * One page of GIFs.
 *
 * [gridUrl] is what the picker draws (the small copy, animated whenever the provider
 * has an animation small enough for a tile); [embedUrl] is what the
 * comment gets, because the URL a reader pastes into a comment has to be the file
 * itself, at full size, not the thumbnail a search index uses to save bandwidth.
 */
data class GifResult(
    val id: String,
    val embedUrl: String,
    val gridUrl: String,
    val title: String?,
    val width: Int?,
    val height: Int?
)

data class GifPage(
    val results: List<GifResult>,
    val next: String? = null,
    val totalCount: Int? = null
)

/**
 * A GIF search that did not succeed, with the reason in the reader's terms.
 *
 * The three failure shapes a picker can meet - rate limited, refused, unreadable
 * answer - are worth separating from each other only in that the first tells the
 * reader to wait and the others tell them the provider itself is the problem. Each
 * is one sentence, because the panel has to show it under a search field.
 */
class GifSearchFailure(message: String) : IOException(message)

/** Tenor v2, with the reader's own key. */
class TenorGifs(
    private val apiKey: String,
    private val client: OkHttpClient = SafebooruGifs.defaultClient(),
    moshi: Moshi = NetworkClient.json
) : GifSearchApi {

    override val providerName: String get() = "Tenor"

    private val adapter = moshi.adapter(TenorResponse::class.java)

    override suspend fun search(query: String, cursor: String?): Result<GifPage> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext Result.success(GifPage(emptyList()))
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    GifSearchFailure("No Tenor key is saved. Add one in Settings, or leave it blank to search Safebooru.")
                )
            }

            val built = runCatching {
                ENDPOINT.toHttpUrl().newBuilder()
                    .addQueryParameter("q", term)
                    .addQueryParameter("key", apiKey.trim())
                    // v2 authenticates with `key` alone; `client_key` is the v1
                    // identifier and is ignored here. It is sent anyway because a
                    // proxy or a gateway that still speaks v1 then has the app name
                    // instead of an anonymous caller.
                    .addQueryParameter("client_key", CLIENT_KEY)
                    .addQueryParameter("limit", PAGE_SIZE.toString())
                    // Only the formats the picker actually uses. Requesting all of
                    // them makes every response several times bigger for tiles that
                    // are never drawn, and `nanogif` is the 90px copy that keeps a
                    // slow connection scrolling instead of stalled.
                    .addQueryParameter("media_filter", "gif,tinygif,nanogif,previewgif")
                    .addQueryParameter("contentfilter", CONTENT_FILTER)
                    .addQueryParameter("locale", LOCALE)
                    .apply { cursor?.let { addQueryParameter("pos", it) } }
                    .build()
            }
            val request = built.getOrNull()?.let {
                Request.Builder().url(it).header("Accept", "application/json").build()
            } ?: return@withContext Result.failure(GifSearchFailure("That search could not be built."))

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            GifSearchFailure(
                                when (response.code) {
                                    400, 401, 403, 404 -> "Tenor refused the saved key. Tenor's public API was retired in 2026, so a key may no longer be accepted; try Klipy or GIPHY in Settings."
                                    429 -> "Tenor is limiting requests. Wait a minute, then try again."
                                    else -> "Tenor is not answering right now (HTTP ${response.code})."
                                }
                            )
                        )
                    }
                    val parsed = runCatching { adapter.fromJson(body) }.getOrNull()
                        ?: return@withContext Result.failure(
                            GifSearchFailure("Tenor answered with something unreadable.")
                        )
                    Result.success(parsed.toGifPage())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(GifSearchFailure("Could not reach Tenor."))
            }
        }

    companion object {
        private const val ENDPOINT = "https://tenor.googleapis.com/v2/search"

        /**
         * Tenor's content filter, fixed rather than chosen.
         *
         * `medium` is the level a comment thread on AniList is at: enough of the
         * catalogue to be useful, without the app deciding on the reader's behalf
         * that they want everything. Making it a setting would be a second list of
         * names for one dropdown.
         */
        private const val CONTENT_FILTER = "medium"
        private const val PAGE_SIZE = 20

        /** The v1 app identifier, harmless on v2 and useful to a v1 gateway. */
        private const val CLIENT_KEY = "anisequel"

        /** The picker's copy is English; the catalogue still answers other languages. */
        private const val LOCALE = "en_US"
    }
}


/** Tenor v2's search response. */
/**
 * Tenor's media map, flattened to the two URLs this app needs.
 *
 * `gif` is the embed, `tinygif` is the tile, and `previewgif` is the fallback for a
 * record that carries only the smallest copy. A result with no GIF URL at all is
 * dropped rather than embedded as a blank. Top-level and internal so a renamed
 * field shows up in a JVM test rather than as an empty grid in somebody's picker.
 */
internal fun TenorResponse.toGifPage(): GifPage {
    val rows = results.orEmpty().filterNotNull().mapNotNull { entry ->
        val formats = entry.mediaFormats ?: return@mapNotNull null
        val embed = formats.gif?.url ?: formats.tinygif?.url ?: formats.nanoGif?.url
            ?: formats.previewGif?.url ?: return@mapNotNull null
        val grid = formats.tinygif?.url ?: formats.nanoGif?.url
            ?: formats.previewGif?.url ?: embed
        val dims = formats.gif?.dims
        GifResult(
            id = entry.id ?: embed,
            embedUrl = embed,
            gridUrl = grid,
            title = entry.title,
            width = dims?.getOrNull(0),
            height = dims?.getOrNull(1)
        )
    }
    // A blank `next` is Tenor's end of results; passing it back as `pos=` asked for
    // the first page again and appended it under itself.
    return GifPage(results = rows, next = next?.takeIf { rows.isNotEmpty() && it.isNotBlank() })
}

@JsonClass(generateAdapter = true)
internal data class TenorResponse(
    val results: List<TenorEntry?>? = null,
    val next: String? = null
)

@JsonClass(generateAdapter = true)
internal data class TenorEntry(
    val id: String? = null,
    val title: String? = null,
    @Json(name = "media_formats") val mediaFormats: TenorFormats? = null
)

@JsonClass(generateAdapter = true)
internal data class TenorFormats(
    val gif: TenorFormat? = null,
    val tinygif: TenorFormat? = null,
    @Json(name = "nanogif") val nanoGif: TenorFormat? = null,
    @Json(name = "previewgif") val previewGif: TenorFormat? = null
)

@JsonClass(generateAdapter = true)
internal data class TenorFormat(
    val url: String? = null,
    val dims: List<Int>? = null
)
