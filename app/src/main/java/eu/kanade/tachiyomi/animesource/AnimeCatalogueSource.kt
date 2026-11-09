package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import rx.Observable

/** Clean-room Aniyomi lib-14 catalogue ABI; see [AnimeSource]. */
@Suppress("unused", "DEPRECATION")
interface AnimeCatalogueSource : AnimeSource {
    val lang: String
    val supportsLatest: Boolean

    fun getFilterList(): AnimeFilterList

    suspend fun getPopularAnime(page: Int): AnimesPage = throw UnsupportedOperationException()
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        throw UnsupportedOperationException()
    suspend fun getLatestUpdates(page: Int): AnimesPage = throw UnsupportedOperationException()

    @Deprecated("Use the non-RxJava API instead")
    fun fetchPopularAnime(page: Int): Observable<AnimesPage>

    @Deprecated("Use the non-RxJava API instead")
    fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage>

    @Deprecated("Use the non-RxJava API instead")
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage>
}