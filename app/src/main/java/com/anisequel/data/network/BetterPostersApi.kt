package com.anisequel.data.network

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * A btttr.cc ("BetterPosters") poster URL for one AniList title.
 *
 * ## Why two requests for one picture
 *
 * btttr addresses its posters by **IMDb** id - `btttr.cc/poster/imdb/poster-default/
 * tt22248376.jpg` - and AniList has no IMDb field on `Media`: its `externalLinks`
 * are official sites and streaming services, and `idMal` is MyAnimeList's numbering,
 * not IMDb's. So the id has to be looked up, and the community mapping service ARM
 * (`arm.haglund.dev`) is the one that answers it from an AniList id in a single GET.
 * Verified against both services: btttr answers 200 `image/webp` for a real IMDb id
 * at that path and 404 for an AniList or MAL id in the same slot, so guessing an
 * "anilist" path would have been a feature that never drew a poster.
 *
 * ## What a miss costs, and why it is cached
 *
 * A card asks for its poster once per composition, and a library is hundreds of
 * cards: without a cache the same list scrolled twice would be hundreds of ARM
 * requests, which is both slow and rude to a free service. Results - *including*
 * "no IMDb id for this title" - are remembered for the life of the process, in a
 * bounded LRU so a very long session cannot grow it without limit. The negative is
 * cached deliberately: a title btttr does not carry is the common case for older and
 * obscure shows, and re-asking on every scroll is the expensive way to learn it again.
 *
 * ## What it is not allowed to do
 *
 * - **Never send the AniList token.** Both hosts are third parties; the client below
 *   is its own `OkHttpClient` with no interceptors, exactly as `AnimeThemesApi`,
 *   `LiveChartPosterApi` and the update client keep theirs.
 * - **Never block a card.** Short timeouts, `Dispatchers.IO`, and every failure path
 *   returns null - which the caller reads as "keep the AniList cover". A poster is
 *   decoration; a card that waited on two third parties for it would be worse than
 *   one with the art the list already had.
 */
class BetterPostersApi(
    private val client: OkHttpClient = defaultClient(),
    moshi: Moshi = NetworkClient.json
) {

    /**
     * ARM answers a flat object of primitives (`{"anilist":154587,"imdb":"tt…"}`),
     * and almost every key is optional depending on the title - so this is read as a
     * map rather than a data class whose missing fields would each need a default.
     */
    private val idsAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    /**
     * AniList id to poster URL, or to null when there is no IMDb match.
     *
     * Access-ordered so the entries a reader scrolls past most often are the ones
     * that survive; see the class doc for why a null is stored at all.
     */
    private val cache = object : LinkedHashMap<Int, String?>(CACHE_SEED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String?>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    /**
     * btttr's poster for [anilistId], or null when the title has no IMDb id, btttr
     * has no poster for it, or either request failed.
     *
     * Null is not an error state here: it is "draw the cover AniList sent", which is
     * what the card does with it.
     */
    suspend fun posterUrl(anilistId: Int): String? {
        // `containsKey` rather than a null check: a remembered null is an answer.
        synchronized(cache) {
            if (cache.containsKey(anilistId)) return cache[anilistId]
        }
        val url = imdbId(anilistId)?.let(::betterPosterUrl)
        synchronized(cache) { cache[anilistId] = url }
        return url
    }

    private suspend fun imdbId(anilistId: Int): String? = withContext(Dispatchers.IO) {
        val url = runCatching {
            IDS_URL.toHttpUrl().newBuilder()
                .addQueryParameter("source", "anilist")
                .addQueryParameter("id", anilistId.toString())
                .build()
        }.getOrNull() ?: return@withContext null

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val parsed = runCatching { idsAdapter.fromJson(body) }.getOrNull()
                (parsed?.get("imdb") as? String)?.takeIf(IMDB_ID::matches)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Scrolling past a card cancels its lookup; that is not a failure.
            throw e
        } catch (e: Exception) {
            // Offline, a 404, a rate limit, a body that is suddenly HTML: all of them
            // mean "this card keeps its AniList cover", never "fail the list".
            null
        }
    }

    private companion object {
        /**
         * ARM's v2 endpoint. It is the community mapping service AniList's own wiki
         * points at; the `source`/`id` pair is its documented lookup shape and any
         * other spelling answers 400 rather than an empty object.
         */
        const val IDS_URL = "https://arm.haglund.dev/api/v2/ids"

        /** IMDb's own id shape, so a garbled answer cannot be pasted into a URL. */
        val IMDB_ID = Regex("^tt\\d{5,}$")

        const val CACHE_SEED = 64
        const val MAX_CACHE_ENTRIES = 512

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * The poster URL btttr serves for one IMDb id.
 *
 * `poster-default` is the plain rendered poster: the `-g`/`-r`/`-q`/`-a` suffixes in
 * btttr's own Jellyfin integration each add an overlay, and this setting exists to
 * change the *source*, not to start configuring badges nobody asked for.
 *
 * The `.jpg` suffix is part of the path even though the bytes are WebP - that is what
 * the host accepts, and dropping the extension answers 404.
 */
internal fun betterPosterUrl(imdbId: String): String =
    "https://btttr.cc/poster/imdb/poster-default/$imdbId.jpg"
