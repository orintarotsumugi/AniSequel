package com.anisequel.data.repository

import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.ThemeSong
import com.anisequel.data.network.AnimeThemesApi
import java.util.Locale

/**
 * The title page's openings and endings, kept for a session.
 *
 * ## Why this is cached at all
 *
 * Opening a title is the app's most repeated gesture: the same twelve cards are
 * opened from the dashboard, the library, a profile's favourites, a studio page,
 * and again when the reader comes back from a relation. AnimeThemes is a free,
 * volunteer-run service with a 90-requests-a-minute limit, and this is the one
 * request the app makes that does not come out of AniList's own budget - so the
 * second visit to a title must not spend another one.
 *
 * A theme set changes when a show airs a new opening, which is a scale of days, so
 * a day-long window is short enough to pick that up and long enough that a session
 * costs one request per title.
 *
 * ## The empty answer is cached too, but not for a day
 *
 * Most of AnimeThemes' catalogue has no themes for a given AniList id (short
 * specials, films, titles nobody has contributed yet). Answering that from memory
 * is the difference between a section that costs nothing and one that hits the
 * service every time the page opens. But an empty answer can also be a dropped
 * request, and a day-long memory of a failure is a section that never appears
 * again until the app restarts - so failures forget in [MISS_TTL_MILLIS] instead.
 *
 * One instance per process: two would double the requests *and* double the memory
 * for the same cards.
 */
class ThemeSongsRepository(
    private val api: AnimeThemesApi = AnimeThemesApi(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    private data class Cached(val songs: List<ThemeSong>, val storedAtMillis: Long)

    /**
     * An id *and* the namespace it belongs to, because a bare id is not an identity.
     *
     * AniList numbers anime and manga independently, so `anime/7` and `manga/7` are
     * two different shows - the same fact [MediaCoverKey] exists to record, and the
     * reason a reader walking a source relation crosses the two namespaces. Keyed on
     * the id alone, the manga visited first filed its answer under the number and the
     * anime then read that back: an empty answer cached for the whole miss window is a
     * title page whose Openings & Endings row simply is not there, and nothing says it
     * was never asked.
     */
    private fun cacheKey(mediaId: Int, mediaType: String?): MediaCoverKey =
        MediaCoverKey(type = mediaType?.uppercase(Locale.ROOT).orEmpty(), id = mediaId)

    /**
     * Small, because this is a section rather than a library: the working set is
     * the handful of titles a session visits, and an LRU keeps a long scroll
     * through a studio page from holding every one of them.
     */
    private val cache = object : LinkedHashMap<MediaCoverKey, Cached>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<MediaCoverKey, Cached>
        ): Boolean = size > MAX_ENTRIES
    }

    suspend fun themes(mediaId: Int, mediaType: String?, artworkUrl: String?): List<ThemeSong> {
        val key = cacheKey(mediaId, mediaType)
        val now = nowMillis()
        synchronized(cache) {
            cache[key]?.let { cached ->
                val ttl = if (cached.songs.isEmpty()) MISS_TTL_MILLIS else HIT_TTL_MILLIS
                if (now - cached.storedAtMillis <= ttl) return cached.songs
                cache.remove(key)
            }
        }

        val songs = api.themes(mediaId, artworkUrl)

        synchronized(cache) {
            cache[key] = Cached(songs, nowMillis())
        }
        return songs
    }

    private companion object {
        /** A day: new openings appear on the scale of a broadcast season. */
        const val HIT_TTL_MILLIS = 24L * 60 * 60 * 1000

        /**
         * Ten minutes. Long enough to absorb a re-open or a rotation, short enough
         * that a dropped request does not hide the section for the session.
         */
        const val MISS_TTL_MILLIS = 10L * 60 * 1000

        const val MAX_ENTRIES = 64
    }
}

/** The one repository for this process. See [ThemeSongsRepository] for why. */
object AniThemes {

    @Volatile
    private var instance: ThemeSongsRepository? = null

    fun repository(): ThemeSongsRepository =
        instance ?: synchronized(this) {
            instance ?: ThemeSongsRepository().also { instance = it }
        }
}
