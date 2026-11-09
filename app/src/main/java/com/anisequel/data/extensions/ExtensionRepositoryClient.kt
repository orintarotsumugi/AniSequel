package com.anisequel.data.extensions

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The result of adding a repository URL, before it is persisted. */
data class RepositoryDiscovery(
    val repository: ExtensionRepository,
    val entries: List<CatalogExtension>
)

/** An HTTP answer that is a valid response but not a success. */
internal class HttpStatusException(
    val code: Int,
    message: String,
    val retryAfterSeconds: Long? = null
) : IOException(message) {
    val retryable: Boolean
        get() = code == 408 || code == 425 || code == 429 || code in 500..599
}

/**
 * The one HTTP client extension code uses.
 *
 * It is deliberately not `NetworkClient`'s AniList client. That client attaches
 * the viewer's bearer token through `AuthInterceptor`; sending an AniList token to
 * a random GitHub index or an Archive.org file would leak the session to a third
 * party. This client sends a browser-shaped User-Agent and nothing else.
 *
 * A small disk cache is installed from the app's cache directory and used for
 * repository indexes and Archive searches. It is an optimisation, not a source of
 * truth: a cache miss is one normal request, and clearing the app's cache can
 * never leave the catalogue empty - the manager also keeps the last good catalog
 * in memory.
 */
internal object ExtensionHttp {
    private const val MAX_BYTES = 16 * 1024 * 1024
    private const val MAX_ATTEMPTS = 3

    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Volatile
    private var cachedClient: OkHttpClient? = null

    val client: OkHttpClient get() = cachedClient ?: baseClient

    /** Installs a size-capped HTTP cache beside the app's other caches. */
    fun installCache(context: Context) {
        if (cachedClient != null) return
        val directory = File(context.cacheDir, "extension-http")
        runCatching {
            cacheDirReady(directory)
            val cache = Cache(directory, 12L * 1024 * 1024)
            cachedClient = baseClient.newBuilder()
                .cache(cache)
                .build()
        }
    }

    private fun cacheDirReady(directory: File) {
        if (!directory.exists()) directory.mkdirs()
        if (!directory.isDirectory) throw IOException("Extension cache is not a directory")
    }

    suspend fun getText(url: String, maxBytes: Int = MAX_BYTES): String {
        var last: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return requestText(url, maxBytes)
            } catch (error: HttpStatusException) {
                last = error
                if (!error.retryable || attempt == MAX_ATTEMPTS - 1) throw error
                delay(retryDelayMillis(error, attempt))
            } catch (error: IOException) {
                last = error
                if (attempt == MAX_ATTEMPTS - 1) throw error
                delay(350L * (attempt + 1))
            }
        }
        throw last ?: IOException("The request did not finish")
    }

    /**
     * Enqueues the request instead of blocking a dispatcher thread.
     *
     * This is what makes `withTimeout` meaningful: a blocking `execute()` cannot
     * be interrupted by coroutine cancellation, so a host that accepted a
     * connection and then stopped talking held a worker until OkHttp's own call
     * timeout. Cancelling the `Call` aborts the socket immediately.
     */
    private suspend fun requestText(url: String, maxBytes: Int): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 AniSequel"
            )
            .header("Accept", "application/json, text/plain, */*")
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!it.isSuccessful) {
                                val retryAfter = it.header("Retry-After")
                                    ?.toLongOrNull()
                                    ?.coerceIn(0L, 5L)
                                throw HttpStatusException(
                                    code = it.code,
                                    message = "HTTP ${it.code} from ${request.url.host}",
                                    retryAfterSeconds = retryAfter
                                )
                            }
                            val body = it.body
                                ?: throw IOException("Empty answer from ${request.url.host}")
                            val declared = body.contentLength()
                            if (declared > maxBytes) throw IOException("The answer is too large")
                            val bytes = body.bytes()
                            if (bytes.size > maxBytes) throw IOException("The answer is too large")
                            if (!continuation.isCancelled) {
                                continuation.resume(bytes.toString(Charsets.UTF_8))
                            }
                        }
                    } catch (error: Exception) {
                        if (!continuation.isCancelled) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }

    private fun retryDelayMillis(error: HttpStatusException, attempt: Int): Long {
        val retryAfter = error.retryAfterSeconds
        return if (retryAfter != null && retryAfter > 0L) {
            retryAfter * 1_000L
        } else {
            450L * (attempt + 1)
        }
    }
}

/**
 * Fetches and validates repository indexes.
 *
 * [discover] is the Add Repo path: it may try several conventional index URLs for
 * a pasted repository page. [fetch] is the refresh path and asks for exactly the
 * URL that was saved, so a repository that has already been resolved never pays
 * for the probing a second time.
 *
 * Every repository read gets its own deadline. Without it, one host that accepts
 * a connection and then stops talking held the whole parallel load open until
 * OkHttp's call timeout, and the catalogue looked empty for that entire wait.
 */
class ExtensionRepositoryClient {

    suspend fun discover(input: String): RepositoryDiscovery {
        val candidates = ExtensionIndex.candidateUrls(input)
        if (candidates.isEmpty()) throw IOException("Paste a repository URL first")

        val failures = mutableListOf<String>()
        for (candidate in candidates) {
            val discovered = runCatching {
                withTimeout(REPOSITORY_TIMEOUT_MS) { readCandidate(candidate) }
            }.getOrElse { error ->
                failures += "${candidate.substringAfter("://").take(48)}: ${error.message}"
                continue
            }
            if (discovered != null) return discovered
            failures += "${candidate.substringAfter("://").take(48)}: no extension list"
        }

        throw IOException(
            failures.firstOrNull()?.let { "No extension index answered. $it" }
                ?: "No extension index answered at this URL"
        )
    }

    suspend fun fetch(repository: ExtensionRepository): List<CatalogExtension> =
        withTimeout(REPOSITORY_TIMEOUT_MS) {
            val body = ExtensionHttp.getText(repository.url)
            if (!ExtensionIndex.looksLikeJson(body)) {
                throw IOException("${repository.displayHost} did not answer with an index")
            }
            readEntries(repository, body)
        }

    /**
     * Reads one candidate URL and returns a discovery only when it yielded a
     * catalogue. A CloudStream manifest is followed into its plugin lists here,
     * so the stored repository URL remains the manifest the reader pasted.
     */
    private suspend fun readCandidate(candidate: String): RepositoryDiscovery? {
        val body = ExtensionHttp.getText(candidate)
        if (!ExtensionIndex.looksLikeJson(body)) return null
        val kind = ExtensionIndex.kindFor(body, candidate)
        val content = ExtensionIndex.contentForUrl(candidate, kind)
        val repository = ExtensionRepository(
            id = candidate,
            url = candidate,
            kind = kind,
            content = content
        )
        val entries = readEntries(repository, body)
        // An empty JSON array is a valid repository. An HTML page or an object
        // with no index array is not; parse() cannot tell the two apart, and the
        // caller rejects non-JSON before this point.
        val validEmpty = body.trimStart().startsWith("[")
        val manifest = ExtensionIndex.pluginListUrls(body).isNotEmpty()
        return if (entries.isNotEmpty() || validEmpty || manifest) {
            RepositoryDiscovery(repository, entries)
        } else {
            null
        }
    }

    /** Direct index first; a CloudStream manifest's plugin lists second. */
    private suspend fun readEntries(
        repository: ExtensionRepository,
        body: String
    ): List<CatalogExtension> {
        val direct = ExtensionIndex.parse(
            body = body,
            repositoryId = repository.id,
            repositoryUrl = repository.url,
            kind = repository.kind,
            content = repository.content
        )
        if (direct.isNotEmpty()) return direct
        val lists = ExtensionIndex.pluginListUrls(body)
        if (lists.isEmpty()) return direct
        return coroutineScope {
            lists.take(MAX_PLUGIN_LISTS).map { listUrl ->
                async {
                    runCatching {
                        val resolved = ExtensionIndex.resolve(repository.url, listUrl)
                        ExtensionHttp.getText(resolved)
                    }.getOrNull()?.let { pluginBody ->
                        ExtensionIndex.parse(
                            body = pluginBody,
                            repositoryId = repository.id,
                            repositoryUrl = repository.url,
                            kind = ExtensionKind.CLOUDSTREAM,
                            content = repository.content
                        )
                    }.orEmpty()
                }
            }.awaitAll().flatten()
        }
    }

    private companion object {
        const val REPOSITORY_TIMEOUT_MS = 30_000L
        const val MAX_PLUGIN_LISTS = 8
    }
}
