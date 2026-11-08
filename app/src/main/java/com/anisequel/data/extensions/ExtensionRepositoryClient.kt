package com.anisequel.data.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The result of adding a repository URL, before it is persisted. */
data class RepositoryDiscovery(
    val repository: ExtensionRepository,
    val entries: List<CatalogExtension>
)

/**
 * The one HTTP client extension code uses.
 *
 * It is deliberately not `NetworkClient`'s AniList client. That client attaches
 * the viewer's bearer token through `AuthInterceptor`; sending an AniList token to
 * a random GitHub index or an Archive.org file would leak the session to a third
 * party. This client sends a browser-shaped User-Agent and nothing else.
 */
internal object ExtensionHttp {
    private const val MAX_BYTES = 16 * 1024 * 1024

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 AniSequel"
            )
            .header("Accept", "application/json, text/plain, */*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from ${request.url.host}")
            }
            val body = response.body ?: throw IOException("Empty answer from ${request.url.host}")
            val bytes = body.bytes()
            if (bytes.size > MAX_BYTES) throw IOException("Repository index is too large")
            bytes.toString(Charsets.UTF_8)
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
 */
class ExtensionRepositoryClient {

    suspend fun discover(input: String): RepositoryDiscovery {
        val candidates = ExtensionIndex.candidateUrls(input)
        if (candidates.isEmpty()) throw IOException("Paste a repository URL first")

        val failures = mutableListOf<String>()
        for (candidate in candidates) {
            val body = runCatching { ExtensionHttp.getText(candidate) }
                .getOrElse { error ->
                    failures += "${candidate.substringAfter("://").take(48)}: ${error.message}"
                    continue
                }
            if (!ExtensionIndex.looksLikeJson(body)) {
                failures += "${candidate.substringAfter("://").take(48)}: not JSON"
                continue
            }
            val kind = ExtensionIndex.kindFor(body, candidate)
            val content = ExtensionIndex.contentForUrl(candidate, kind)
            val repository = ExtensionRepository(
                id = candidate,
                url = candidate,
                kind = kind,
                content = content
            )
            val entries = ExtensionIndex.parse(
                body = body,
                repositoryId = repository.id,
                repositoryUrl = repository.url,
                kind = kind,
                content = content
            )
            // An empty JSON array is a valid repository. An HTML page or an object
            // with no index array is not; parse() cannot tell the two apart, and
            // those were rejected above by looksLikeJson.
            if (entries.isNotEmpty() || body.trimStart().startsWith("[")) {
                return RepositoryDiscovery(repository, entries)
            }
            failures += "${candidate.substringAfter("://").take(48)}: no extension list"
        }

        throw IOException(
            failures.firstOrNull()?.let { "No extension index answered. $it" }
                ?: "No extension index answered at this URL"
        )
    }

    suspend fun fetch(repository: ExtensionRepository): List<CatalogExtension> {
        val body = ExtensionHttp.getText(repository.url)
        if (!ExtensionIndex.looksLikeJson(body)) {
            throw IOException("${repository.displayHost} did not answer with an index")
        }
        return ExtensionIndex.parse(
            body = body,
            repositoryId = repository.id,
            repositoryUrl = repository.url,
            kind = repository.kind,
            content = repository.content
        )
    }
}
