package com.anisequel.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.IOException
import java.util.concurrent.TimeUnit
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
 * Tenor used to answer anonymous searches - the public key in its documentation
 * was how every hobby client got GIFs - and it does not any more: `g.tenor.com/v1`
 * replies `{"code":7,"error":"Tenor API is discontinued"}` and `tenor.googleapis.com`
 * rejects the same string as an invalid key. Giphy's public beta key is 401,
 * DuckDuckGo's image endpoint answers a bot challenge, and Wikimedia Commons has
 * turned MIME search off at the API level. Something had to give, so the app offers
 * the two that actually answer:
 *
 * - **Openverse**, anonymous, no account and no key, filtered to `.gif` files by
 *   its `extension` argument. Its index is Creative Commons media, so an
 *   anime-specific query can come back empty - that is a real limit and the panel
 *   says so rather than pretending the search broke.
 * - **Tenor v2**, when the reader has pasted their own key in Settings. This is
 *   what a reaction-GIF picker used to be, with the coverage that implies, and the
 *   key is theirs, obtained free from Google.
 *
 * A key is never bundled. The whole point of the anonymous default is that nobody
 * has to hand over a credential to use the app, and a committed key would be a
 * rate limit shared by every install plus a secret in a public repository.
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
     * Whether an empty answer is a gap in this provider's catalogue rather than a
     * miss.
     *
     * Openverse indexes Creative Commons media, so a search for an anime reaction
     * can legitimately return nothing and the reader deserves to know that, and to
     * know what would have found it. A reaction-GIF index with the same empty answer
     * means only that the query was obscure, and the long explanation would be a
     * guess about a catalogue that has no such hole.
     */
    val catalogueIsNarrow: Boolean get() = false

    /**
     * One page of results for [query].
     *
     * [cursor] is whatever the previous page said to ask for next - a page number
     * for Openverse, a `pos` token for Tenor - which is why it is a string and not
     * an int: the two providers do not page the same way and neither should have to
     * pretend to. Null asks for the first page.
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
 * [gridUrl] is what the picker draws (small, and animated); [embedUrl] is what the
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

/** Openverse's anonymous image search, asked for animated GIFs only. */
class OpenverseGifs(
    private val client: OkHttpClient = defaultClient(),
    moshi: Moshi = NetworkClient.json
) : GifSearchApi {

    override val catalogueIsNarrow: Boolean get() = true

    private val adapter = moshi.adapter(OpenverseResponse::class.java)

    override suspend fun search(query: String, cursor: String?): Result<GifPage> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext Result.success(GifPage(emptyList()))

            val built = runCatching {
                ENDPOINT.toHttpUrl().newBuilder()
                    .addQueryParameter("q", term)
                    // `extension=gif` is what makes this a GIF picker rather than a
                    // photo search: Openverse filters the served file's extension,
                    // and a thumbnail-heavy index would otherwise answer with stills.
                    .addQueryParameter("extension", "gif")
                    .addQueryParameter("page", firstPageOf(cursor).toString())
                    .addQueryParameter("page_size", PAGE_SIZE.toString())
                    .build()
            }
            val request = built.getOrNull()?.let {
                Request.Builder()
                    .url(it)
                    .header("Accept", "application/json")
                    // Anonymous use of this API is rate limited and the throttle is
                    // keyed on the caller's identity, so a descriptive UA is both
                    // what the API asks for and the only way to be a distinct client.
                    .header("User-Agent", USER_AGENT)
                    .build()
            } ?: return@withContext Result.failure(GifSearchFailure("That search could not be built."))

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.code == 401 || response.code == 429) {
                        return@withContext Result.failure(
                            GifSearchFailure("The GIF index is rate limiting searches. Try again shortly.")
                        )
                    }
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(GifSearchFailure(describe(response.code)))
                    }
                    val parsed = runCatching { adapter.fromJson(body) }.getOrNull()
                        ?: return@withContext Result.failure(
                            GifSearchFailure("The GIF index answered with something unreadable.")
                        )
                    Result.success(parsed.toGifPage(page = firstPageOf(cursor)))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(GifSearchFailure("Could not reach the GIF index."))
            }
        }

    /** The page a cursor names, or the first one. */
    private fun firstPageOf(cursor: String?): Int =
        (cursor?.toIntOrNull() ?: 1).coerceAtLeast(1)

    private fun describe(status: Int): String = when {
        status == 402 -> "The GIF index has stopped answering anonymous searches."
        status in 500..599 -> "The GIF index is not answering right now (HTTP $status)."
        else -> "The GIF index refused that search (HTTP $status)."
    }

    companion object {
        private const val ENDPOINT = "https://api.openverse.org/v1/images/"
        private const val USER_AGENT = "AniSequel (Android; GIF search)"
        private const val PAGE_SIZE = 20

        internal fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/** Tenor v2, with the reader's own key. */
class TenorGifs(
    private val apiKey: String,
    private val client: OkHttpClient = OpenverseGifs.defaultClient(),
    moshi: Moshi = NetworkClient.json
) : GifSearchApi {

    private val adapter = moshi.adapter(TenorResponse::class.java)

    override suspend fun search(query: String, cursor: String?): Result<GifPage> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext Result.success(GifPage(emptyList()))
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    GifSearchFailure("No Tenor key is saved. Add one in Settings, or leave it blank to search Openverse.")
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


/**
 * Openverse's page shape, mapped.
 *
 * `url` is the file itself and `thumbnail` is the small preview; either can be
 * null for a record the index has not finished processing, and a GIF with no file
 * URL cannot be embedded, so such a row is dropped here rather than drawn as a
 * broken tile that the reader can tap.
 *
 * Top-level and internal because it is pure mapping over a response body: pinning
 * it in a JVM test is the only way to know a renamed field shows up as an empty
 * grid rather than as a silent hole in production.
 */
internal fun OpenverseResponse.toGifPage(page: Int): GifPage {
        val rows = results.orEmpty().filterNotNull().mapNotNull { row ->
            val file = row.url?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            GifResult(
                id = row.id ?: file,
                embedUrl = file,
                gridUrl = row.thumbnail?.takeIf { it.startsWith("http") } ?: file,
                title = row.title,
                width = row.width,
                height = row.height
            )
        }
        val more = (pageCount ?: 0) > page
        return GifPage(
            results = rows,
            next = if (more) (page + 1).toString() else null,
            totalCount = resultCount
        )
    }

/** Openverse's search response, as far as this app cares. */
@JsonClass(generateAdapter = true)
internal data class OpenverseResponse(
    @Json(name = "result_count") val resultCount: Int? = null,
    @Json(name = "page_count") val pageCount: Int? = null,
    @Json(name = "results") val results: List<OpenverseImage?>? = null
)

@JsonClass(generateAdapter = true)
internal data class OpenverseImage(
    val id: String? = null,
    val title: String? = null,
    val url: String? = null,
    val thumbnail: String? = null,
    val width: Int? = null,
    val height: Int? = null
)


/**
 * Tenor's media map, flattened to the two URLs this app needs.
 *
 * `gif` is the embed, `tinygif` is the tile, and `previewgif` is the fallback for
 * a record that carries only the smallest copy. A result with no GIF URL at all is
 * dropped rather than embedded as a blank.
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
        return GifPage(results = rows, next = next?.takeIf { rows.isNotEmpty() })
    }

/** Tenor v2's search response. */
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
