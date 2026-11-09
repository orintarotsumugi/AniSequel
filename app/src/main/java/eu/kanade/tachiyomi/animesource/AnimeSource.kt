package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import rx.Observable

/**
 * Clean-room Aniyomi extensions-lib 14 source ABI.
 *
 * The deprecated RxJava methods are part of the published contract an extension
 * was compiled against, so they are declared and implemented in the base class
 * even though this runtime only calls the suspend API.
 */
@Suppress("unused", "DEPRECATION")
interface AnimeSource {
    val id: Long
    val name: String

    suspend fun getAnimeDetails(anime: SAnime): SAnime
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode>
    suspend fun getVideoList(episode: SEpisode): List<Video>

    @Deprecated("Use the non-RxJava API instead")
    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime>

    @Deprecated("Use the non-RxJava API instead")
    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>>

    @Deprecated("Use the non-RxJava API instead")
    fun fetchVideoList(episode: SEpisode): Observable<List<Video>>
}