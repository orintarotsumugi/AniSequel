package com.anisequel.data.network

import com.anisequel.domain.ui.PosterOverlays
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

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
 * ## The caches, and why there are three
 *
 * A card asks for its poster once per composition, and a library is hundreds of
 * cards: without a cache the same list scrolled twice would be hundreds of ARM
 * requests, which is both slow and rude to a free service. The layers are:
 *
 *  - **id cache** - AniList id to the IMDb series and the season ARM places the
 *    entry at. Keyed on the id alone, so flipping an overlay switch does not re-run
 *    the lookup. Result *including* the negative is remembered for the session, in
 *    a bounded LRU.
 *  - **in-flight map** - the same id asked for by several cards at once shares one
 *    request instead of firing one per card. This is the difference between six
 *    requests and one when a title appears in a rail and again in the grid below it.
 *  - **disk cache** - the positive half of the id cache is written to a small JSON
 *    file, so a cold start paints btttr posters without waiting for ARM at all. The
 *    negatives stay in memory: a title btttr does not carry is cheap to re-ask once
 *    per launch, and a stale negative on disk would outlive the service's own fix.
 *  - **URL cache** - (id, overlays) to the final URL, so a variant is not rebuilt
 *    and re-parsed while scrolling.
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
    moshi: Moshi = NetworkClient.json,
    /**
     * Where the AniList-to-IMDb mapping survives a restart.
     *
     * Null in tests and previews, which keeps this object pure in memory there.
     */
    private val cacheFile: File? = null
) {

    /**
     * ARM answers a flat object of primitives (`{"anilist":154587,"imdb":"tt…"}`),
     * and almost every key is optional depending on the title - so this is read as a
     * map rather than a data class whose missing fields would each need a default.
     */
    private val idsAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    /** The same shape for the on-disk cache, which stores only strings. */
    private val storedIdsAdapter: JsonAdapter<Map<String, String>> = moshi.adapter(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

    /**
     * AniList id to the IMDb series and season, or to null when ARM has no match.
     *
     * Access-ordered so the entries a reader scrolls past most often are the ones
     * that survive; see the class doc for why a null is stored at all.
     */
    private val imdbIds = object : LinkedHashMap<Int, PosterMapping?>(CACHE_SEED, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Int, PosterMapping?>?
        ): Boolean = size > MAX_CACHE_ENTRIES
    }

    /** The URL for a specific overlay variant, built once per (id, overlays). */
    private val posterUrls = object :
        LinkedHashMap<Pair<Int, PosterOverlays>, String?>(CACHE_SEED, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Pair<Int, PosterOverlays>, String?>?
        ): Boolean = size > MAX_CACHE_ENTRIES
    }

    /** Lookups already running, so ten cards for one title make one request. */
    private val inFlight = HashMap<Int, CompletableDeferred<PosterMapping?>>()

    private val lock = Any()
    private var diskLoaded = false

    /**
     * btttr's poster for [anilistId], or null when the title has no IMDb id, btttr
     * has no poster for it, or either request failed.
     *
     * Null is not an error state here: it is "draw the cover AniList sent", which is
     * what the card does with it.
     *
     * @param skipSequels when true, a title ARM places at season 2 or later of its
     * IMDb series answers null even though btttr has a picture for that series.
     * btttr cannot tell the seasons apart - it addresses one IMDb id for all of
     * them - so "Nisemonogatari" was being drawn with the same art as
     * "Bakemonogatari". ARM knows Nisemonogatari is season 2 of `tt1480925`, and
     * that is the answer this flag acts on. The skipped null is deliberately not
     * cached: turning the switch back off must not have to wait for a restart.
     */
    suspend fun posterUrl(
        anilistId: Int,
        overlays: PosterOverlays = PosterOverlays.DEFAULT,
        skipSequels: Boolean = false
    ): String? {
        val key = anilistId to overlays
        // `containsKey` rather than a null check: a remembered null is an answer.
        // Only read it when this call is not asking a season question - a URL
        // cached before the switch was turned on belongs to the series, and the
        // mapping below is what says whether this season may use it.
        if (!skipSequels) {
            synchronized(lock) {
                if (posterUrls.containsKey(key)) return posterUrls[key]
            }
        }
        val mapping = posterMapping(anilistId)
        if (skipSequels && (mapping?.season ?: 0) > 1) return null
        val url = mapping?.imdb?.let { id -> betterPosterUrl(id, overlays) }
        // Only remember a null that came from a lookup that actually answered. A
        // cancelled leader hands its waiters null so they stop waiting, but that is
        // not "btttr has no poster for this title" - caching it would leave the
        // card blank for the rest of the session after one fast scroll.
        val answered = synchronized(lock) { imdbIds.containsKey(anilistId) }
        if (!skipSequels && (url != null || answered)) {
            synchronized(lock) { posterUrls[key] = url }
        }
        return url
    }

    /**
     * Every poster btttr renders for [anilistId], the reader's chosen overlay first.
     *
     * ## Why a title has more than one poster
     *
     * btttr does not publish a gallery - it renders *one* piece of key art at four
     * overlay combinations, addressed by the first path segment (`poster`,
     * `poster-r`, `poster-g`, `poster-n`). A card only ever wants the variant the
     * reader configured, but the full-screen viewer is where art is looked at: the
     * clean render, the one with the score, the one with the genre line and the full
     * poster are four genuinely different pictures of the same title, and they were
     * unreachable from the app because [posterUrl] answers with one string.
     *
     * The chosen variant leads so the viewer opens on the picture the reader's cards
     * already show; see [btttrVariantOverlays] for the order of the rest.
     *
     * Empty - not null - when btttr has nothing: no IMDb match, both requests failed,
     * or [skipSequels] ruled out a later season. A caller draws no BetterPosters
     * source at all, exactly as it would for a null from [posterUrl].
     *
     * This shares [posterMapping]'s caches with the cards, so a title the Library has
     * already drawn costs no request at all, and it never writes [posterUrls]: that
     * cache answers "the URL for this variant", which is a different question.
     */
    suspend fun posterVariants(
        anilistId: Int,
        overlays: PosterOverlays = PosterOverlays.DEFAULT,
        skipSequels: Boolean = false
    ): List<BetterPosterVariant> {
        val mapping = posterMapping(anilistId)
        if (skipSequels && (mapping?.season ?: 0) > 1) return emptyList()
        val imdb = mapping?.imdb ?: return emptyList()
        return btttrVariantOverlays(overlays).map { variant ->
            BetterPosterVariant(url = betterPosterUrl(imdb, variant), overlays = variant)
        }
    }

    /**
     * AniList id to IMDb id and season, shared by every caller asking for the same
     * title.
     *
     * The leader of a lookup is the first caller; the rest await its deferred and
     * never touch the network. A cancelled leader completes the deferred with null
     * in `finally`, so a card that scrolls away cannot leave its neighbours waiting.
     */
    private suspend fun posterMapping(anilistId: Int): PosterMapping? {
        val lookup = synchronized(lock) {
            ensureDiskLoadedLocked()
            when {
                imdbIds.containsKey(anilistId) -> Lookup.Known(imdbIds[anilistId])
                inFlight[anilistId] != null -> Lookup.Waiting(inFlight.getValue(anilistId))
                else -> {
                    val deferred = CompletableDeferred<PosterMapping?>()
                    inFlight[anilistId] = deferred
                    Lookup.Leading(deferred)
                }
            }
        }

        return when (lookup) {
            is Lookup.Known -> lookup.mapping
            is Lookup.Waiting -> lookup.deferred.await()
            is Lookup.Leading -> {
                var result: PosterMapping? = null
                // A cancelled lookup is not an answer. Caching its null would turn
                // "the reader scrolled past too fast" into "btttr has no poster for
                // this title" for the rest of the session, and the card would never
                // retry. Only a request that actually came back is remembered.
                var completed = false
                try {
                    result = fetchPosterMapping(anilistId)
                    completed = true
                } finally {
                    val snapshot = synchronized(lock) {
                        if (completed) imdbIds[anilistId] = result
                        inFlight.remove(anilistId)
                        if (completed && result?.imdb != null) snapshotLocked() else null
                    }
                    // Before the write, and outside any suspension: a cancelled
                    // leader must still release the cards awaiting its answer.
                    lookup.deferred.complete(result)
                    if (snapshot != null) writeSnapshot(snapshot)
                }
                result
            }
        }
    }

    private suspend fun fetchPosterMapping(anilistId: Int): PosterMapping? = withContext(Dispatchers.IO) {
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
                    ?: return@withContext null
                val imdb = (parsed["imdb"] as? String)?.takeIf(IMDB_ID::matches)
                    ?: return@withContext null
                PosterMapping(imdb, posterSeason(parsed))
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

    /** Reads the on-disk mapping once, under the lock that also guards the map. */
    private fun ensureDiskLoadedLocked() {
        if (diskLoaded) return
        diskLoaded = true
        val text = runCatching { cacheFile?.takeIf { it.isFile }?.readText() }.getOrNull()
            ?: return
        val stored = runCatching { storedIdsAdapter.fromJson(text) }.getOrNull() ?: return
        for ((key, value) in stored) {
            val id = key.toIntOrNull() ?: continue
            // `tt…` is what the first cache wrote; `tt…|2` is the same mapping
            // with the season ARM reported, so a restart keeps the sequel rule.
            val parts = value.split('|', limit = 2)
            val imdb = parts[0]
            if (!IMDB_ID.matches(imdb)) continue
            if (imdbIds.size >= MAX_CACHE_ENTRIES) break
            val season = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
            imdbIds[id] = PosterMapping(imdb, season)
        }
    }

    /** The positive entries, as the flat string map the disk cache stores. */
    private fun snapshotLocked(): Map<String, String> {
        val snapshot = HashMap<String, String>(imdbIds.size)
        for ((id, mapping) in imdbIds) {
            val imdb = mapping?.imdb ?: continue
            snapshot[id.toString()] = mapping.season?.let { "$imdb|$it" } ?: imdb
        }
        return snapshot
    }

    /**
     * Writes the mapping off the main thread.
     *
     * The body is small (a few hundred short strings), and the write is atomic by
     * rename so a process death mid-write cannot leave a half-parsed cache behind.
     * Two cards landing at once can both write; the later write is a superset, and
     * the rename is what makes the race harmless.
     */
    private suspend fun writeSnapshot(ids: Map<String, String>) {
        val file = cacheFile ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                val json = storedIdsAdapter.toJson(ids)
                val temporary = File(file.parentFile, "${file.name}.tmp")
                temporary.writeText(json)
                if (!temporary.renameTo(file)) {
                    file.writeText(json)
                    temporary.delete()
                }
            }
        }
    }

    /** The three answers [posterMapping] can get from the cache, without suspending. */
    private sealed interface Lookup {
        data class Known(val mapping: PosterMapping?) : Lookup
        data class Waiting(val deferred: CompletableDeferred<PosterMapping?>) : Lookup
        data class Leading(val deferred: CompletableDeferred<PosterMapping?>) : Lookup
    }

    /**
     * ARM's answer for one title.
     *
     * [season] is the position inside the IMDb series, not a property of the
     * AniList entry: `tt1480925` is one IMDb id for the whole Monogatari run, and
     * Bakemonogatari is season 1 while Nisemonogatari is season 2 of it. Null when
     * ARM has no season (a film, or a mapping that only carries the id).
     */
    private data class PosterMapping(val imdb: String, val season: Int?)

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

        /**
         * Short enough that a title btttr does not carry falls back quickly.
         *
         * The card has already drawn the AniList cover by the time this runs, so a
         * slow lookup is not a blank card; but a long timeout keeps a request alive
         * behind a reader who has moved on, and ARM answers in tens of milliseconds
         * when it answers at all.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * The season ARM places an entry at inside its IMDb series, or null when it does
 * not say.
 *
 * This is the answer the title parser cannot give: "Nisemonogatari" and
 * "Bakemonogatari" are two AniList entries and one IMDb id (`tt1480925`), and only
 * ARM's `themoviedb-season` / `thetvdb-season` fields say which is which. Reading
 * both and taking the larger is deliberate: the providers occasionally split a run
 * differently, and the only question here is "is this later than season one?" - a
 * disagreement should keep the AniList cover rather than repeat season one's art.
 */
internal fun posterSeason(ids: Map<String, Any?>): Int? {
    fun season(key: String) = (ids[key] as? Number)?.toInt()?.takeIf { it > 0 }
    return listOfNotNull(season("themoviedb-season"), season("thetvdb-season")).maxOrNull()
}

/**
 * The poster URL btttr serves for one IMDb id, at the overlay variant asked for.
 *
 * The first path segment is the variant: `poster` is the full rendered poster with
 * the score and the genre line, and `poster-r`, `poster-g` and `poster-n` are btttr's
 * own spelling for "score only", "genre only" and "neither", taken from its Jellyfin
 * integration rather than guessed. The third segment (`poster-default`) is the
 * size/style slot and stays put; moving the letter into it, as an earlier draft of
 * this function did, would request a path the service does not serve. `?tag=none`
 * drops the trend label, the one overlay that is a query parameter rather than a path
 * letter. The `q`/`a` letters (quality and age badges) are deliberately not offered:
 * this setting is about removing what the app cannot otherwise hide, not adding
 * badges nobody asked for.
 *
 * The `.jpg` suffix is part of the path even though the bytes are WebP - that is what
 * the host accepts, and dropping the extension answers 404.
 */
internal fun betterPosterUrl(
    imdbId: String,
    overlays: PosterOverlays = PosterOverlays.DEFAULT
): String {
    val code = overlays.pathCode
    val variant = if (code.isEmpty()) "poster" else "poster-$code"
    val trend = if (overlays.showTrendTags) "" else "?tag=none"
    return "https://btttr.cc/$variant/imdb/poster-default/$imdbId.jpg$trend"
}

/**
 * One of btttr's renders of a title: the URL to draw with, and which overlays it
 * was rendered with.
 *
 * The overlays travel with the URL because the viewer names the page after them -
 * "Score only", "Clean" - and re-deriving that from the URL would mean parsing a
 * path segment back into the settings it came from.
 */
data class BetterPosterVariant(
    val url: String,
    val overlays: PosterOverlays
)

/**
 * Every overlay set btttr renders for one poster, [chosen] first.
 *
 * The four are btttr's own path letters (see [betterPosterUrl]) and the set is the
 * whole choice space: score on/off against genre on/off. `showTrendTags` is copied
 * from [chosen] rather than varied, because it is a query parameter on the same
 * render rather than a different path - a gallery that also flipped the trend strip
 * would double for a difference the reader set once in Settings.
 *
 * The remaining three follow in the order a reader is most likely to want them: the
 * full poster, then the two single overlays, then the clean one. [chosen] is dropped
 * from that tail so the list never pages onto the same URL twice, which reads as a
 * broken swipe.
 */
internal fun btttrVariantOverlays(chosen: PosterOverlays): List<PosterOverlays> {
    val trend = chosen.showTrendTags
    val rendered = listOf(
        PosterOverlays(showRating = true, showGenre = true, showTrendTags = trend),
        PosterOverlays(showRating = true, showGenre = false, showTrendTags = trend),
        PosterOverlays(showRating = false, showGenre = true, showTrendTags = trend),
        PosterOverlays(showRating = false, showGenre = false, showTrendTags = trend)
    )
    return (listOf(chosen) + rendered.filter { it != chosen }).distinct()
}
