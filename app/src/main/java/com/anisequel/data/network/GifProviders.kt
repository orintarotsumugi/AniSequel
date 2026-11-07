package com.anisequel.data.network

import com.anisequel.domain.gif.GifProvider
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/*
 * The keyed and keyless GIF indexes the picker can search, besides Safebooru.
 *
 * ## The state of the field, as measured
 *
 * Tenor's public API was retired on 30 June 2026. A reader's saved key is kept -
 * losing a credential silently is worse than a search that says why it failed - but
 * it is no longer the default answer, and the client says so when Google refuses.
 *
 * - **Gifukai** is the default: keyless, anime-only, hand-picked and safe by
 *   construction. One random reaction scene per action endpoint (`/v1/hug`, ...) -
 *   "search" is a word-to-action match, there is no paging, and a second ask
 *   re-rolls the same reaction. See [GifukaiGifs].
 * - **Klipy** is the closest thing to a drop-in general catalogue: a key in the URL
 *   path, page-numbered results, and `file.hd` / `file.sm` copies that map cleanly
 *   onto a grid tile and a comment embed. It is what the default hands a sentence
 *   to: the app ships a key for it (see the class doc of [BundledGifKeys]) so it
 *   answers without asking the reader to register anywhere, and a reader's pasted
 *   key replaces the bundled one.
 * - **GIPHY** is the name everybody already knows. A new key is a beta key at 100
 *   calls an hour, and its `images.*.width` fields are **strings**, which is why the
 *   model keeps them as strings and parses on the way out. Its terms also require
 *   client-side calls and forbid mixing its results with another provider's in one
 *   grid, which is one reason the picker searches one index at a time rather than
 *   merging several.
 * - **nekos.best** is the second keyless index. It has no page cursor: a request
 *   returns up to twenty reaction GIFs. Its category endpoints (`hug`, `pat`, `wave`)
 *   are the reliable half, so a one-word search that names a category goes there and
 *   anything else goes to its metadata search.
 *
 * ## Never the AniList token
 *
 * Each client is its own [OkHttpClient] with no interceptors, the same rule
 * [SafebooruGifs] and [TenorGifs] record: the shared client attaches the reader's
 * bearer token to everything, and a GIF index has no business holding it.
 */

/** Klipy's `gifs/search`, keyed by the app key in the path. */
class KlipyGifs(
    private val apiKey: String,
    private val client: OkHttpClient = SafebooruGifs.defaultClient(),
    moshi: Moshi = NetworkClient.json
) : GifSearchApi {

    override val providerName: String get() = "Klipy"

    /**
     * The key the factory handed in. Pinned by [GifKeyFallbackTest] and
     * `GifProviderParseTest`: an empty key here used to surface as an empty grid,
     * so the test asserts the bundled key actually flows through rather than
     * trusting the factory branch.
     */
    internal val keyInUse: String get() = apiKey

    private val adapter = moshi.adapter(KlipyResponse::class.java)

    override suspend fun search(query: String, cursor: String?): Result<GifPage> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext Result.success(GifPage(emptyList()))
            if (apiKey.isBlank()) {
                // Unreachable through [gifSearchApiFor], which always has the
                // bundled key to fall back to - this branch is for a direct build
                // with an empty credential, which must say so rather than ask
                // Klipy for a request it will only 401.
                return@withContext Result.failure(
                    GifSearchFailure("That Klipy key is empty. Paste one in Settings, or pick Safebooru or nekos.best.")
                )
            }

            val page = cursor?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val built = runCatching {
                ENDPOINT.toHttpUrl().newBuilder()
                    // A path segment, so a key with a slash or a space cannot
                    // change which endpoint the request reaches.
                    .addPathSegment(apiKey.trim())
                    .addPathSegment("gifs")
                    .addPathSegment("search")
                    .addQueryParameter("q", term)
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("per_page", PAGE_SIZE.toString())
                    .addQueryParameter("content_filter", CONTENT_FILTER)
                    .addQueryParameter("format_filter", MEDIA_FILTER)
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
                                    401, 403 -> "Klipy rejected the saved key. Check it in Settings."
                                    429 -> "Klipy is limiting requests right now. Wait a minute, or try a lower-key word."
                                    else -> "Klipy is not answering right now (HTTP ${response.code})."
                                }
                            )
                        )
                    }
                    val parsed = runCatching { adapter.fromJson(body) }.getOrNull()
                        ?: return@withContext Result.failure(
                            GifSearchFailure("Klipy answered with something unreadable.")
                        )
                    if (parsed.result == false) {
                        return@withContext Result.failure(
                            GifSearchFailure(parsed.message ?: "Klipy refused that search.")
                        )
                    }
                    Result.success(parsed.toGifPage(fallbackPage = page))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(GifSearchFailure("Could not reach Klipy."))
            }
        }

    companion object {
        private const val ENDPOINT = "https://api.klipy.com/api/v1/"

        /** Klipy's documented page size bounds are 8..50; 24 fills a phone grid. */
        private const val PAGE_SIZE = 24
        private const val CONTENT_FILTER = "medium"
        private const val MEDIA_FILTER = "gif,webp"
    }
}

/**
 * Klipy's search answer.
 *
 * The double envelope is the API's own shape: `result` is the status and `data.data`
 * is the list, inside a `data` object that also carries the page. The flattened
 * `toGifPage` is internal so a field rename fails a JVM test rather than producing
 * an empty grid that looks like a search miss.
 */
internal fun KlipyResponse.toGifPage(fallbackPage: Int): GifPage {
    val rows = data?.items.orEmpty().filterNotNull().mapNotNull { item ->
        val files = item.file ?: return@mapNotNull null
        val embed = files.hd?.gif?.url ?: files.md?.gif?.url ?: files.sm?.gif?.url
            ?: files.xs?.gif?.url ?: return@mapNotNull null
        val grid = files.sm?.gif?.url ?: files.xs?.gif?.url ?: embed
        val dims = files.hd?.gif ?: files.md?.gif ?: files.sm?.gif ?: files.xs?.gif
        GifResult(
            id = item.id?.toString() ?: embed,
            embedUrl = embed,
            gridUrl = grid,
            title = item.title,
            width = dims?.width,
            height = dims?.height
        )
    }
    // `has_next` is the only honest end-of-results signal: the page can be short
    // because a content filter dropped rows while the index still has more behind
    // it, and reading the short list as the end stopped the grid early.
    val next = if (data?.hasNext == true && rows.isNotEmpty()) {
        ((data.currentPage ?: fallbackPage) + 1).toString()
    } else {
        null
    }
    return GifPage(results = rows, next = next)
}

@JsonClass(generateAdapter = true)
internal data class KlipyResponse(
    val result: Boolean? = null,
    val message: String? = null,
    val data: KlipyPage? = null
)

@JsonClass(generateAdapter = true)
internal data class KlipyPage(
    @Json(name = "data") val items: List<KlipyItem?>? = null,
    @Json(name = "current_page") val currentPage: Int? = null,
    @Json(name = "per_page") val perPage: Int? = null,
    @Json(name = "has_next") val hasNext: Boolean? = null
)

@JsonClass(generateAdapter = true)
internal data class KlipyItem(
    val id: Long? = null,
    val title: String? = null,
    val file: KlipyFileSet? = null
)

@JsonClass(generateAdapter = true)
internal data class KlipyFileSet(
    val hd: KlipyFormatSet? = null,
    val md: KlipyFormatSet? = null,
    val sm: KlipyFormatSet? = null,
    val xs: KlipyFormatSet? = null
)

@JsonClass(generateAdapter = true)
internal data class KlipyFormatSet(
    val gif: KlipyFile? = null,
    val webp: KlipyFile? = null
)

@JsonClass(generateAdapter = true)
internal data class KlipyFile(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

/** GIPHY's `/v1/gifs/search`, keyed by the reader's beta or production key. */
class GiphyGifs(
    private val apiKey: String,
    private val client: OkHttpClient = SafebooruGifs.defaultClient(),
    moshi: Moshi = NetworkClient.json
) : GifSearchApi {

    override val providerName: String get() = "GIPHY"

    private val adapter = moshi.adapter(GiphyResponse::class.java)

    override suspend fun search(query: String, cursor: String?): Result<GifPage> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext Result.success(GifPage(emptyList()))
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    GifSearchFailure("No GIPHY key is saved. Add one in Settings, or leave it blank to search Safebooru.")
                )
            }

            val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val built = runCatching {
                ENDPOINT.toHttpUrl().newBuilder()
                    .addQueryParameter("api_key", apiKey.trim())
                    // GIPHY caps `q` at 50 characters and answers 400 past that.
                    // Truncating is the only option a search field has: the reader
                    // can see which 50 characters were sent.
                    .addQueryParameter("q", term.take(MAX_QUERY_LENGTH))
                    .addQueryParameter("limit", PAGE_SIZE.toString())
                    .addQueryParameter("offset", offset.toString())
                    .addQueryParameter("rating", RATING)
                    .addQueryParameter("lang", LANGUAGE)
                    // Smaller renditions and no clip variants: the payload is the
                    // grid, and a phone does not need the animation twice.
                    .addQueryParameter("bundle", BUNDLE)
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
                                    401, 403 -> "GIPHY rejected the saved key. Check it in Settings."
                                    429 -> "GIPHY's beta limit is 100 calls an hour and it is spent. Wait a little and try again."
                                    else -> "GIPHY is not answering right now (HTTP ${response.code})."
                                }
                            )
                        )
                    }
                    val parsed = runCatching { adapter.fromJson(body) }.getOrNull()
                        ?: return@withContext Result.failure(
                            GifSearchFailure("GIPHY answered with something unreadable.")
                        )
                    // `meta` is a separate nullable object, so the status check has
                    // to keep the object it read: `parsed.meta.msg` after a null
                    // check on a *different* expression does not compile, and
                    // reading it again is a second chance to disagree.
                    val meta = parsed.meta
                    if (meta != null && meta.status != null && meta.status != 200) {
                        return@withContext Result.failure(
                            GifSearchFailure(meta.msg ?: "GIPHY refused that search.")
                        )
                    }
                    Result.success(parsed.toGifPage(fallbackOffset = offset))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(GifSearchFailure("Could not reach GIPHY."))
            }
        }

    companion object {
        private const val ENDPOINT = "https://api.giphy.com/v1/gifs/search"
        private const val PAGE_SIZE = 24
        private const val MAX_QUERY_LENGTH = 50
        private const val RATING = "g"
        private const val LANGUAGE = "en"
        private const val BUNDLE = "messaging_non_clips"
    }
}

/**
 * GIPHY's answer, flattened.
 *
 * `original` is the file a comment should carry and `fixed_width` is the tile. The
 * widths arrive as JSON strings, so they are kept as strings and parsed here: a
 * generated adapter with an `Int?` field would fail on every result.
 */
internal fun GiphyResponse.toGifPage(fallbackOffset: Int): GifPage {
    val rows = data.orEmpty().filterNotNull().mapNotNull { item ->
        val images = item.images ?: return@mapNotNull null
        val embed = images.original?.url ?: images.downsized?.url ?: return@mapNotNull null
        val grid = images.fixedWidth?.url ?: images.fixedWidthSmall?.url ?: embed
        val dims = images.original
        GifResult(
            id = item.id ?: embed,
            embedUrl = embed,
            gridUrl = grid,
            title = item.title,
            width = dims?.width?.toIntOrNull(),
            height = dims?.height?.toIntOrNull()
        )
    }
    val page = pagination
    val next = if (page != null && rows.isNotEmpty()) {
        val offset = page.offset ?: fallbackOffset
        val count = page.count ?: rows.size
        val total = page.totalCount
        val nextOffset = offset + count
        // `total_count` is the only field that says "there is more"; an offset past
        // it asked for a page that does not exist and appended nothing.
        if (nextOffset < (total ?: 0) && nextOffset <= MAX_OFFSET) nextOffset.toString() else null
    } else {
        null
    }
    return GifPage(results = rows, next = next, totalCount = page?.totalCount)
}

/** GIPHY refuses an offset past 4999, so the picker never asks for one. */
internal const val MAX_OFFSET = 4999

@JsonClass(generateAdapter = true)
internal data class GiphyResponse(
    val data: List<GiphyItem?>? = null,
    val pagination: GiphyPagination? = null,
    val meta: GiphyMeta? = null
)

@JsonClass(generateAdapter = true)
internal data class GiphyPagination(
    val offset: Int? = null,
    @Json(name = "total_count") val totalCount: Int? = null,
    val count: Int? = null
)

@JsonClass(generateAdapter = true)
internal data class GiphyMeta(
    val status: Int? = null,
    val msg: String? = null
)

@JsonClass(generateAdapter = true)
internal data class GiphyItem(
    val id: String? = null,
    val title: String? = null,
    val images: GiphyImages? = null
)

@JsonClass(generateAdapter = true)
internal data class GiphyImages(
    val original: GiphyImage? = null,
    val downsized: GiphyImage? = null,
    @Json(name = "fixed_width") val fixedWidth: GiphyImage? = null,
    @Json(name = "fixed_width_small") val fixedWidthSmall: GiphyImage? = null
)

@JsonClass(generateAdapter = true)
internal data class GiphyImage(
    val url: String? = null,
    val width: String? = null,
    val height: String? = null
)

/**
 * nekos.best's keyless anime reaction index.
 *
 * ## Why a category check rather than always searching
 *
 * Its `/search` endpoint searches *metadata* - anime names, artists, sources - so
 * `hug` finds a GIF whose series is named "Hug", not a hugging GIF. The category
 * endpoints are the half that does what a reader means by a reaction search, so a
 * one-word query that names a category goes there first; anything else gets a
 * metadata search, which is still better than an empty grid.
 *
 * ## Why there is no cursor
 *
 * The API returns up to twenty results and has no page token. Paging past that would
 * ask for the same twenty again, so `next` is always null.
 */
class NekosBestGifs(
    private val client: OkHttpClient = SafebooruGifs.defaultClient(),
    moshi: Moshi = NetworkClient.json
) : GifSearchApi {

    override val providerName: String get() = "nekos.best"

    override val catalogueIsNarrow: Boolean get() = true

    override fun emptyHint(query: String): String =
        "No GIF on nekos.best matches \"$query\". It indexes reaction names rather " +
            "than plot points, so try a feeling like \"hug\", \"laugh\" or \"pat\"."

    private val adapter = moshi.adapter(NekosResponse::class.java)

    override suspend fun search(query: String, cursor: String?): Result<GifPage> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext Result.success(GifPage(emptyList()))

            val category = term.lowercase().takeIf { it in NEKOS_CATEGORIES }
            // Asked and answered against the live API: `search` needs a *category*
            // in `type`, so `?type=2` - the value nekos.best's older documentation
            // gave for "GIFs" - comes back "Invalid type", and a word that is not
            // one of these categories has no valid request at all. It used to send
            // one anyway and take a 400, which the grid then reported as "Could not
            // reach nekos.best" about a host it had reached perfectly well.
            if (category == null) {
                return@withContext Result.failure(
                    GifSearchFailure(
                        "nekos.best only answers its reaction words, like " +
                            NEKOS_HINT.joinToString(", ") +
                            ". Pick Safebooru to search a title or a feeling."
                    )
                )
            }
            val built = runCatching {
                "$ENDPOINT/$category".toHttpUrl().newBuilder()
                    .addQueryParameter("amount", PAGE_SIZE.toString())
                    .build()
            }
            val request = built.getOrNull()?.let {
                Request.Builder()
                    .url(it)
                    .header("Accept", "application/json")
                    // nekos.best blocks a default OkHttp user agent. The contact
                    // address is the project's own repository, which is what its
                    // documentation asks for.
                    .header("User-Agent", USER_AGENT)
                    .build()
            } ?: return@withContext Result.failure(GifSearchFailure("That search could not be built."))

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            GifSearchFailure(
                                when (response.code) {
                                    429 -> "nekos.best is limiting requests. Wait a moment and try again."
                                    else -> "nekos.best is not answering right now (HTTP ${response.code})."
                                }
                            )
                        )
                    }
                    val parsed = runCatching { adapter.fromJson(body) }.getOrNull()
                        ?: return@withContext Result.failure(
                            GifSearchFailure("nekos.best answered with something unreadable.")
                        )
                    Result.success(parsed.toGifPage())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(GifSearchFailure("Could not reach nekos.best."))
            }
        }

    companion object {
        /** Named in the refusal above, so the reader has something to type. */
        private val NEKOS_HINT = listOf("cry", "clap", "blush")

        private const val ENDPOINT = "https://nekos.best/api/v2"
        private const val PAGE_SIZE = 20
        private const val USER_AGENT =
            "AniSequel (https://github.com/orintarotsumugi/AniSequel)"

        /** The documented GIF categories that read as a reaction a comment makes. */
        private val NEKOS_CATEGORIES = setOf(
            "angry", "baka", "bite", "blowkiss", "bonk", "carry", "clap", "cry",
            "cuddle", "dance", "handhold", "happy", "highfive", "hug", "kick", "kiss",
            "laugh", "nod", "nom", "pat", "poke", "punch", "salute", "shrug", "slap",
            "sleep", "smile", "smug", "stare", "tableflip", "teehee", "thumbsup",
            "tickle", "wag", "wave", "wink", "yawn", "yeet"
        )
    }
}

internal fun NekosResponse.toGifPage(): GifPage {
    val rows = results.orEmpty().filterNotNull().mapNotNull { item ->
        val url = item.url ?: return@mapNotNull null
        GifResult(
            id = url,
            embedUrl = url,
            gridUrl = url,
            title = item.animeName,
            width = item.dimensions?.width,
            height = item.dimensions?.height
        )
    }
    return GifPage(results = rows, next = null, totalCount = rows.size)
}

@JsonClass(generateAdapter = true)
internal data class NekosResponse(
    val results: List<NekosItem?>? = null
)

@JsonClass(generateAdapter = true)
internal data class NekosItem(
    val url: String? = null,
    @Json(name = "anime_name") val animeName: String? = null,
    val dimensions: NekosDimensions? = null
)

@JsonClass(generateAdapter = true)
internal data class NekosDimensions(
    val width: Int? = null,
    val height: Int? = null
)

/**
 * The client for a chosen provider, falling back to the anonymous index when the
 * chosen one needs a key that is not there.
 *
 * The fallback is the same provider the Settings page says will be used
 * ([CommentMediaSettings.resolvedGifProvider]), so the grid's placeholder and the
 * search it sends cannot disagree.
 */
fun gifSearchApiFor(
    provider: GifProvider,
    tenorKey: String?,
    klipyKey: String?,
    giphyKey: String?
): GifSearchApi {
    // The keyed clients refuse a blank key in their own `search`, which used to make
    // their "No Klipy key is saved. Add one in Settings" sentences unreachable: this
    // factory never builds one without a key, so the substitution happened here, in
    // silence. It still substitutes - a missing credential must not leave the picker
    // with no search at all - but it now hands back the line that says which index
    // was asked for, which one is answering, and where to fix it.
    fun standingInFor(name: String) =
        "No $name key is saved, so Safebooru is answering instead. " +
            "Add a key in Settings > AniList, or choose Safebooru to keep using it."

    return when (provider) {
        GifProvider.SAFEBOORU -> SafebooruGifs()
        GifProvider.NEKOS_BEST -> NekosBestGifs()
        GifProvider.GIFUKAI -> GifukaiGifs()
        GifProvider.TENOR -> tenorKey?.takeIf { it.isNotBlank() }
            ?.let { TenorGifs(it) } ?: SafebooruGifs(substitutionNote = standingInFor(GifProvider.TENOR.displayName))

        // The app's own key is what makes Klipy the default ([GifProvider.DEFAULT]:
        // a shipped choice must be able to answer without asking the reader for a
        // credential first). A saved reader key replaces it for as long as it is
        // there; clearing the field returns the search to the bundled key rather
        // than to a refusal, so no branch here can produce a keyless Klipy client.
        GifProvider.KLIPY -> KlipyGifs(
            klipyKey?.takeIf { it.isNotBlank() } ?: BUNDLED_KLIPY_KEY
        )

        GifProvider.GIPHY -> giphyKey?.takeIf { it.isNotBlank() }
            ?.let { GiphyGifs(it) } ?: SafebooruGifs(substitutionNote = standingInFor(GifProvider.GIPHY.displayName))
    }
}
