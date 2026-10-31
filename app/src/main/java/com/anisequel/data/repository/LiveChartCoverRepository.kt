package com.anisequel.data.repository

import com.anisequel.data.network.LiveChartArtwork
import com.anisequel.data.network.LiveChartPosterApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * LiveChart's poster for one title, kept for a session.
 *
 * ## Why this is cached, and why it mirrors [ThemeSongsRepository]
 *
 * Opening the artwork viewer is a repeated gesture - the same title is opened from
 * the hero, tapped again after a zoom, left and re-entered - and LiveChart is a
 * volunteer-run site that already 403s anything that does not look like a browser,
 * so it is the last thing the app should re-hit for art it just read. The URL is
 * also stable for a given title, so a day-long window costs one search request per
 * title per session. This is the same shape, and the same politeness argument, as
 * the AnimeThemes cache one directory over.
 *
 * A null answer is a miss rather than a failure to hide: the search may have found
 * nothing close, or the request may have dropped. Both forget in
 * [MISS_TTL_MILLIS] so a re-open retries, while a real poster is kept for a day.
 *
 * One instance per process: two would double the requests for the same pill.
 *
 * ## Two opens, one request
 *
 * The viewer fetches on composition, and a dialog can be dismissed and reopened - or
 * recomposed into a second `produceState` - before the first answer lands. The cache
 * below cannot absorb that, because the entry does not exist until the request
 * returns, so this class also keeps the *in-flight* fetch per key and hands the same
 * one to every caller. It runs in a process scope rather than the caller's, so
 * closing the viewer mid-fetch still fills the cache for the next open instead of
 * throwing the work away - which is what the old caller-scoped fetch did.
 */
class LiveChartCoverRepository(
    private val api: LiveChartPosterApi = LiveChartPosterApi(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    private data class Cached(val artwork: LiveChartArtwork, val storedAtMillis: Long)

    /** Guards [cache] and [inFlight]; both are touched under it, never across a suspend. */
    private val lock = Any()

    /** Small, because this is the handful of titles a session views full-screen. */
    private val cache = object : LinkedHashMap<String, Cached>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>): Boolean =
            size > MAX_ENTRIES
    }

    /** Fetches that have not answered yet, so concurrent opens share one request. */
    private val inFlight = HashMap<String, Deferred<LiveChartArtwork>>()

    /**
     * Outlives every viewer that asks.
     *
     * `Dispatchers.IO` because the API call blocks on an HTTP read; the supervisor
     * because one failed cover must not cancel the rest of the session's fetches.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The LiveChart poster for [queryTitles], via [searchUrl]; null when there is
     * none. A thin alias for [artwork], kept for callers that only draw a cover.
     */
    suspend fun posterUrl(searchUrl: String, queryTitles: List<String>): String? =
        artwork(searchUrl, queryTitles).posterUrl

    /**
     * LiveChart's poster and visuals for [queryTitles], via [searchUrl].
     *
     * Keyed on [searchUrl], which is what the fetch is addressed by and what the
     * caller keys its own state on (`FullScreenCoverViewer` keys `produceState` on
     * nothing else). Keying on the first title instead was a collision waiting for a
     * remake: LiveChart links both `Fruits Basket` (2001) and (2019) under the same
     * romaji, so the second title opened was served the first one's poster from
     * memory for the whole day, and their two fetches collapsed into one in-flight
     * request. Two spellings of *one* show still share an entry, because they share
     * the show's LiveChart URL.
     *
     * An empty [LiveChartArtwork] is cached for [MISS_TTL_MILLIS] like a failed
     * poster lookup, so a title LiveChart does not carry is not re-searched on
     * every open while a dropped request still gets a second chance in minutes.
     */
    suspend fun artwork(searchUrl: String, queryTitles: List<String>): LiveChartArtwork {
        val key = searchUrl
        cachedOrNull(key)?.let { return it }

        val pending = synchronized(lock) {
            inFlight.getOrPut(key) {
                scope.async {
                    try {
                        api.artwork(searchUrl, queryTitles).also { artwork ->
                            synchronized(lock) { cache[key] = Cached(artwork, nowMillis()) }
                        }
                    } catch (cancelled: CancellationException) {
                        // The scope is only cancelled at process teardown; rethrow so
                        // the deferred is marked cancelled rather than "no art".
                        throw cancelled
                    } catch (_: Exception) {
                        LiveChartArtwork()
                    } finally {
                        synchronized(lock) { inFlight.remove(key) }
                    }
                }
            }
        }
        return pending.await()
    }

    /** A fresh cache entry's artwork, or null when there is none or it has expired. */
    private fun cachedOrNull(key: String): LiveChartArtwork? {
        val now = nowMillis()
        synchronized(lock) {
            val entry = cache[key] ?: return null
            val ttl = if (entry.artwork.isEmpty) MISS_TTL_MILLIS else HIT_TTL_MILLIS
            if (now - entry.storedAtMillis <= ttl) return entry.artwork
            cache.remove(key)
        }
        return null
    }

    private companion object {
        /** A day: a title's cover changes on the scale of a re-release, not a session. */
        const val HIT_TTL_MILLIS = 24L * 60 * 60 * 1000

        /**
         * Ten minutes. Long enough to absorb a re-open, short enough that a dropped
         * request does not hide the pill for the rest of the session.
         */
        const val MISS_TTL_MILLIS = 10L * 60 * 1000

        const val MAX_ENTRIES = 64
    }
}

/** The one repository for this process. See [LiveChartCoverRepository] for why. */
object LiveChartCovers {

    @Volatile
    private var instance: LiveChartCoverRepository? = null

    fun repository(): LiveChartCoverRepository =
        instance ?: synchronized(this) {
            instance ?: LiveChartCoverRepository().also { instance = it }
        }
}
