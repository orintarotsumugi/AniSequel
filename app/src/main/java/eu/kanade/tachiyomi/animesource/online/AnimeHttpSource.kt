package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.security.MessageDigest

/**
 * Clean-room implementation of the Aniyomi lib-14 HTTP source base.
 *
 * The request/parse contract is the published behaviour: a source builds a
 * [Request] and parses a [Response], and this base owns the id generation, the
 * default headers and the network call. The network client is the runtime's
 * host-tunnelled one (see [NetworkHelper]); nothing here opens a socket itself.
 */
@Suppress("unused", "UNUSED_PARAMETER", "DEPRECATION")
abstract class AnimeHttpSource : AnimeCatalogueSource {

    protected val network: NetworkHelper by injectLazy()

    abstract val baseUrl: String

    open val versionId: Int = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }

    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient get() = network.client

    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        add("User-Agent", network.defaultUserAgentProvider())
    }

    override fun toString(): String = "$name (${lang.uppercase()})"

    override suspend fun getPopularAnime(page: Int): AnimesPage = withContext(Dispatchers.IO) {
        fetchPopularAnime(page).toBlocking().first()
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        withContext(Dispatchers.IO) {
            fetchSearchAnime(page, query, filters).toBlocking().first()
        }

    override suspend fun getLatestUpdates(page: Int): AnimesPage = withContext(Dispatchers.IO) {
        fetchLatestUpdates(page).toBlocking().first()
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = withContext(Dispatchers.IO) {
        fetchAnimeDetails(anime).toBlocking().first()
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = withContext(Dispatchers.IO) {
        fetchEpisodeList(anime).toBlocking().first()
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> = withContext(Dispatchers.IO) {
        fetchVideoList(episode).toBlocking().first()
    }

    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> = Observable.fromCallable {
        popularAnimeParse(client.newCall(popularAnimeRequest(page)).execute().requireSuccess())
    }

    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
        Observable.fromCallable {
            searchAnimeParse(client.newCall(searchAnimeRequest(page, query, filters)).execute().requireSuccess())
        }

    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = Observable.fromCallable {
        latestUpdatesParse(client.newCall(latestUpdatesRequest(page)).execute().requireSuccess())
    }

    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> = Observable.fromCallable {
        animeDetailsParse(client.newCall(animeDetailsRequest(anime)).execute().requireSuccess())
            .apply { initialized = true }
    }

    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> = Observable.fromCallable {
        episodeListParse(client.newCall(episodeListRequest(anime)).execute().requireSuccess())
    }

    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> = Observable.fromCallable {
        videoListParse(client.newCall(videoListRequest(episode)).execute().requireSuccess()).sort()
    }

    open fun fetchVideoUrl(video: Video): Observable<String> = Observable.fromCallable {
        videoUrlParse(client.newCall(videoUrlRequest(video)).execute().requireSuccess())
    }

    protected open fun popularAnimeRequest(page: Int): Request = throw UnsupportedOperationException()
    protected open fun popularAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    protected open fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        throw UnsupportedOperationException()
    protected open fun searchAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    protected open fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    protected open fun latestUpdatesParse(response: Response): AnimesPage = throw UnsupportedOperationException()

    open fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)
    protected open fun animeDetailsParse(response: Response): SAnime = throw UnsupportedOperationException()
    protected open fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)
    protected open fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException()
    protected open fun videoListRequest(episode: SEpisode): Request = GET(baseUrl + episode.url, headers)
    protected open fun videoListParse(response: Response): List<Video> = throw UnsupportedOperationException()
    protected open fun List<Video>.sort(): List<Video> = this
    protected open fun videoUrlRequest(video: Video): Request = GET(baseUrl + video.url, headers)
    protected open fun videoUrlParse(response: Response): String = throw UnsupportedOperationException()

    fun SEpisode.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SAnime.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    private fun getUrlWithoutDomain(orig: String): String = try {
        URI(orig).let { uri ->
            buildString {
                append(uri.rawPath.orEmpty())
                uri.rawQuery?.let { append('?').append(it) }
                uri.rawFragment?.let { append('#').append(it) }
            }
        }
    } catch (_: Exception) {
        orig
    }

    open fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url
    open fun getEpisodeUrl(episode: SEpisode): String = baseUrl + episode.url
    open fun prepareNewEpisode(episode: SEpisode, anime: SAnime) {}

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    private fun Response.requireSuccess(): Response {
        if (!isSuccessful) {
            close()
            throw HttpException(code)
        }
        return this
    }
}