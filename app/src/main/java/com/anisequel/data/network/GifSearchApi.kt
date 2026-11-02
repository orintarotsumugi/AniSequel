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
 * GIF search, from whichever provider the reader has.
 *
 * ## Why there are two of them
 *
 * Tenor used to answer anonymous searches - the public key in its documentation was
 * how every hobby client got GIFs - and it does not any more: `g.tenor.com/v1`
 * replies HTTP 403 with `{"code":7,"error":"Tenor API is discontinued"}` and
 * `tenor.googleapis.com` rejects the same string as `API_KEY_INVALID`.
 *
 * Giphy is the other name everybody expects here, and it is worth recording how it
 * manages access, because the answer is "not anonymously, and not any more, ever".
 * A key now requires signing up at developers.giphy.com; every new key is a **beta
 * key limited to 100 calls an hour** until Giphy approves an application for
 * production; the `media.giphy.com/api/v5` search surface is OAuth-issued rather
 * than key-authenticated; and the historically published public beta key
 * (`dc6zaTOxFJmzC`) answers HTTP 403 with `"msg":"BANNED"` - identically for a curl,
 * a browser and an OkHttp user agent, which is what makes it a blacklisted *key*
 * rather than a blocked client. A second leaked one is 401. There is no anonymous
 * token endpoint. So there is nothing for this app to hold: the only Giphy path is
 * the reader's own key, which is the same shape as Tenor's, and neither is bundled.
 *
 * The rest of the candidates were measured too, and are recorded so the next reader
 * does not spend an afternoon re-discovering them (November 2026):
 *
 * - **Gelbooru** DAPI: HTTP 401 for every anonymous shape; it now needs an
 *   account-issued `api_key` plus `user_id`.
 * - **Rule34**: HTTP 403 with a CAPTCHA page for any user agent.
 * - **Danbooru** `posts.json`: answers 200 anonymously and its CDN hotlinks fine,
 *   but the anonymous search is capped at **two tags per request**, which is not
 *   enough to ask for a safe animated series GIF (`animated_gif rating:general
 *   order:score <series>` is four), and its default result set includes deleted and
 *   `q`-rated posts. It is a poor fit for a picker that must filter safely, so the
 *   app does not use it.
 * - **Wikimedia Commons**: `mimetype:image/gif` / `filetype:gif` searches return
 *   `totalhits: 0` - MIME search is switched off at the API level.
 * - **Openverse**: responds, but slowly (timeouts on anime queries), caps anonymous
 *   callers at 20 a minute, and has no anime reaction-GIF catalogue to search.
 * - **DuckDuckGo's** image endpoint: a bot challenge.
 * - **Konachan.net**: HTTP 522, origin not answering.
 *
 * What is left is the two that actually answer, and both are offered:
 *
 * - **Safebooru**, anonymous, no account and no key, asked for `animated_gif` at
 *   `rating:safe`. Its catalogue is anime, which is what a reader on AniList is
 *   looking for, and this is the source the browser GIF pickers in that community
 *   already use. See [SafebooruGifs] and [booruTiers] for how a sentence becomes the
 *   three tag spellings one search is asked as.
 * - **Tenor v2**, when the reader has pasted their own key in Settings. This is
 *   what a reaction-GIF picker used to be, with the coverage that implies, and the
 *   key is theirs, obtained free from Google.
 *
 * A key is never bundled. The whole point of the anonymous default is that nobody
 * has to hand over a credential to use the app, and a committed key would be a
 * rate limit shared by every install plus a secret in a public repository - which is
 * also exactly what Giphy's 100-an-hour beta ceiling would become: one quota,
 * shared, for every install in the wild.
 *
 * ## Never the AniList token
 *
 * Both clients are their own [OkHttpClient] with no interceptors, the same rule
 * [AnimeThemesApi] and [CatboxApi] record: the shared client attaches the reader's
 * bearer token to everything, and neither a search index nor a file host has any
 * business holding it.
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
                    .addQueryParameter("limit", PAGE_SIZE.toString())
                    // Only the formats the picker actually uses. Requesting all of
                    // them makes every response several times bigger for tiles that
                    // are never drawn.
                    .addQueryParameter("media_filter", "gif,tinygif,previewgif")
                    .addQueryParameter("contentfilter", CONTENT_FILTER)
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
                                if (response.code == 403 || response.code == 400) {
                                    "Tenor rejected the saved key. Check it in Settings."
                                } else {
                                    "Tenor is not answering right now (HTTP ${response.code})."
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
        val embed = formats.gif?.url ?: formats.tinygif?.url ?: formats.previewGif?.url
            ?: return@mapNotNull null
        val grid = formats.tinygif?.url ?: formats.previewGif?.url ?: embed
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
    @Json(name = "previewgif") val previewGif: TenorFormat? = null
)

@JsonClass(generateAdapter = true)
internal data class TenorFormat(
    val url: String? = null,
    val dims: List<Int>? = null
)
