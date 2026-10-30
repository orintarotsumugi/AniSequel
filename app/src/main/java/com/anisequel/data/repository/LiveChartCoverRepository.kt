package com.anisequel.data.repository

import com.anisequel.data.network.LiveChartPosterApi

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
 */
class LiveChartCoverRepository(
    private val api: LiveChartPosterApi = LiveChartPosterApi(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    private data class Cached(val url: String?, val storedAtMillis: Long)

    /** Small, because this is the handful of titles a session views full-screen. */
    private val cache = object : LinkedHashMap<String, Cached>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>): Boolean =
            size > MAX_ENTRIES
    }

    /**
     * The LiveChart poster for [queryTitles], via [searchUrl]; null when there is
     * none. The key is the first non-blank title so two spellings of one show do
     * not occupy two entries; with none to key on the search URL itself is used.
     */
    suspend fun posterUrl(searchUrl: String, queryTitles: List<String>): String? {
        val key = queryTitles.firstOrNull { it.isNotBlank() } ?: searchUrl
        val now = nowMillis()
        synchronized(cache) {
            cache[key]?.let { cached ->
                val ttl = if (cached.url != null) HIT_TTL_MILLIS else MISS_TTL_MILLIS
                if (now - cached.storedAtMillis <= ttl) return cached.url
                cache.remove(key)
            }
        }

        val url = api.posterUrl(searchUrl, queryTitles)

        synchronized(cache) {
            cache[key] = Cached(url, nowMillis())
        }
        return url
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
