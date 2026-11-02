package com.anisequel.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Uploads one image to imgbb.com and returns the URL it is served at.
 *
 * ## Why a third host exists
 *
 * catbox.moe paused anonymous uploads in November 2026 and pomf.lain.la is one
 * stranger's server; a chain that ends at the second host still ends at a single
 * point of failure.
 *
 * It is also the **second** attempt now, ahead of pomf, for a reason that was
 * measured rather than assumed: `pomf2.lain.la` resets any request carrying
 * AniList's own referer, so a file stored there is invisible to everybody reading
 * the comment on the site (`i.ibb.co` answers 200 to the same referer). Imgbb
 * therefore gets the file when catbox is paused, and pomf is what is left when both
 * of those are having a bad afternoon. See [ImageHost.showsOnAniList].
 *
 * ## Why the browser endpoint rather than the documented API
 *
 * imgbb's API v1 (`api.imgbb.com/1/upload`) requires an API key, and a key is the
 * one thing this app must not ship: it would be a credential in a public
 * repository, shared by every install and revocable for all of them at once. The
 * site's own anonymous uploader is the form behind `imgbb.com` itself - a session
 * token scraped from the homepage and one multipart POST to `/json` - so it needs
 * no account and no key, which is the same bar every other host here meets.
 *
 * The homepage request is best-effort: some responses have no token (a bot
 * challenge, a cut-down page), and the upload is still attempted with the cookies
 * that request set, because the endpoint has answered without a token for years.
 * A token that cannot be found is not by itself the reason a reader's photo failed.
 *
 * ## Never the AniList token
 *
 * The client is its own [OkHttpClient], for the reason [CatboxApi] records: the
 * shared one attaches the reader's bearer token, and a file host has no business
 * receiving a credential for their whole AniList account. The cookie jar below is
 * for imgbb's own anti-forgery cookie, and it is deliberately scoped to this
 * client so it never travels anywhere else.
 */
class ImgbbApi(
    private val client: OkHttpClient = defaultClient(),
    moshi: Moshi = NetworkClient.json
) : ImageHost {

    private val adapter = moshi.adapter(ImgbbUploadResponse::class.java)

    /**
     * Uploads [bytes] as [fileName] and returns the served URL.
     *
     * The request is the site's own: one multipart part named `source`, the
     * `action=upload` discriminator, and the session token when the homepage
     * gave one. The body is parsed rather than pattern-matched because the
     * endpoint answers HTTP 200 for a refusal too.
     */
    override suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> {
        if (bytes.isEmpty()) return Result.failure(UploadFailure("There was nothing to upload."))

        // Best-effort: the upload below is still worth trying without it. Only an
        // IOException is swallowed - a cancelled coroutine must stay cancelled
        // rather than carry on to a second request nobody is waiting for.
        val token = try {
            fetchToken()
        } catch (failure: IOException) {
            null
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "source",
                fileName,
                bytes.toRequestBody(mimeType.toMediaType())
            )
            .addFormDataPart("type", "file")
            .addFormDataPart("action", "upload")
            .addFormDataPart("name", fileName)
            .addFormDataPart("timestamp", System.currentTimeMillis().toString())
            .apply { token?.let { addFormDataPart("auth_token", it) } }
            .build()
        val request = Request.Builder()
            .url(UPLOAD_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            // The endpoint is the one the site's own JavaScript calls, and it
            // checks where the request claims to come from.
            .header("Origin", SITE_URL)
            .header("Referer", "$SITE_URL/")
            .header("X-Requested-With", "XMLHttpRequest")
            .post(body)
            .build()

        val response = try {
            execute(request)
        } catch (failure: IOException) {
            return Result.failure(
                UploadFailure("Could not reach imgbb.com - check the connection.")
            )
        }

        return response.use {
            val text = runCatching { it.body?.string().orEmpty() }.getOrDefault("")
            if (!it.isSuccessful) {
                Result.failure(UploadFailure(describe(it.code, text)))
            } else {
                parseResponse(text)
            }
        }
    }

    /**
     * The anonymous upload token embedded in imgbb's homepage, or null.
     *
     * The page writes it as `auth_token = "..."`. It is kept internal so the
     * scrape is pinned by a test: the day the markup changes, the failure should
     * be a failed assertion rather than a support thread about photos that only
     * upload when catbox is up.
     */
    private suspend fun fetchToken(): String? {
        val request = Request.Builder()
            .url("$SITE_URL/")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val response = execute(request)
        response.use {
            if (!it.isSuccessful) return null
            val html = runCatching { it.body?.string().orEmpty() }.getOrDefault("")
            return authToken(html)
        }
    }

    /**
     * The URL in an imgbb answer, or the reason there is not one.
     *
     * A successful answer is `status_code: 200` with an `image.url` on the host
     * that serves the files. Everything else - an error object, a status the site
     * uses for a refusal, an HTML page - is a sentence for the composer.
     */
    internal fun parseResponse(text: String): Result<String> {
        val parsed = runCatching { adapter.fromJson(text) }.getOrNull()
            ?: return Result.failure(
                UploadFailure(prose(text) ?: "The image host returned no URL.")
            )
        val url = parsed.image?.url
        if (parsed.statusCode != 200 || url == null || !isServedUrl(url)) {
            return Result.failure(
                UploadFailure(
                    oneLine(parsed.error?.message)
                        ?: oneLine(parsed.statusText)
                        ?: prose(text)
                        ?: "The image host did not return a usable URL."
                )
            )
        }
        return Result.success(url)
    }

    /**
     * The host's refusal as prose - or null, when it answered in JSON.
     *
     * imgbb answers errors as a JSON document, and that document's first line is
     * a brace; handing it to the composer would put JSON where the reader is owed
     * a reason. The same rule [PomfApi.prose] records.
     */
    private fun prose(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return null
        return CatboxApi.readableProse(text)
    }

    private fun describe(status: Int, text: String): String = when (status) {
        429 -> "imgbb.com is rate limiting uploads. Try again in a minute."
        in 500..599 -> "imgbb.com is not taking uploads right now (HTTP $status)."
        else -> prose(text) ?: "imgbb.com refused the upload (HTTP $status)."
    }

    /** One line, capped like every other host reason, or null. */
    private fun oneLine(reason: String?): String? {
        val line = reason?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
        if (line.isNullOrEmpty()) return null
        return if (line.length <= MAX_REASON_CHARS) line else line.take(MAX_REASON_CHARS) + "…"
    }

    /**
     * One call, cancellable the way catbox's and pomf's are.
     *
     * A reader can close the sheet in the middle of a large upload, and a
     * blocking `execute` would run to the call timeout with nobody left to
     * answer.
     */
    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isActive) return
                        continuation.resumeWith(Result.failure(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!continuation.isActive) {
                            response.close()
                            return
                        }
                        continuation.resumeWith(Result.success(response))
                    }
                }
            )
        }

    companion object {
        private const val SITE_URL = "https://imgbb.com"
        private const val UPLOAD_URL = "$SITE_URL/json"
        private const val USER_AGENT = "AniSequel (Android; comment attachments)"
        private const val MAX_REASON_CHARS = 160

        /**
         * The token on the site's homepage.
         *
         * The markup is `auth_token = "hex"` today; the pattern tolerates the
         * whitespace around `=` that the page has changed once already and the
         * hex case it actually uses. It is a scrape, so it is pinned by a test.
         */
        internal fun authToken(html: String): String? {
            val match = TOKEN_PATTERN.find(html) ?: return null
            return match.groupValues[1].takeIf { it.isNotEmpty() }
        }

        private val TOKEN_PATTERN = Regex("""auth_token\s*=\s*"([0-9a-fA-F]+)"""")

        /**
         * The host's own file domain.
         *
         * Uploads are served from `i.ibb.co`; `imgbb.com` itself serves the pages,
         * not the files. The check is on the parsed host rather than a prefix, so
         * `https://i.ibb.co.evil.example/x.png` cannot pass.
         */
        private const val SERVED_HOST = "i.ibb.co"

        /** Whether [text] is a URL this host serves a stored file at. */
        internal fun isServedUrl(text: String): Boolean {
            if (text.any { it.isWhitespace() }) return false
            val url = runCatching { text.toHttpUrlOrNull() }.getOrNull() ?: return false
            if (url.scheme != "https") return false
            return url.host == SERVED_HOST && url.pathSegments.any { it.isNotBlank() }
        }

        /**
         * An upload client with a cookie jar.
         *
         * The homepage sets a session cookie and the upload endpoint is the form
         * behind that page, so the two requests have to look like one browser.
         * Everything else - the generous timeouts, the connection retry - is
         * [CatboxApi.defaultClient]'s, for the same reasons recorded there.
         */
        internal fun defaultClient(): OkHttpClient =
            CatboxApi.defaultClient().newBuilder()
                .cookieJar(SessionCookies())
                .build()
    }
}

/**
 * Cookies for imgbb's own two requests, and nothing else.
 *
 * A process-wide jar would send this site's session cookie to every other host
 * the app talks to; a no-op jar would make the upload look like a stranger's POST
 * to a form that checks. Scoped to the one client that needs it.
 */
private class SessionCookies : CookieJar {
    private val byHost = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(byHost) {
            // Newest first so a refreshed cookie replaces the old one, and expired
            // cookies (including deletions the server sends) are dropped.
            val now = System.currentTimeMillis()
            byHost[url.host] = (cookies + byHost[url.host].orEmpty())
                .distinctBy { it.name }
                .filter { it.expiresAt > now }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        synchronized(byHost) { byHost[url.host].orEmpty().filter { it.matches(url) } }
}

/** imgbb's answer to an upload. */
@JsonClass(generateAdapter = true)
internal data class ImgbbUploadResponse(
    @Json(name = "status_code") val statusCode: Int? = null,
    val success: Boolean? = null,
    val image: ImgbbImage? = null,
    /** Present on a refusal; the one field a reader can act on. */
    val error: ImgbbError? = null,
    @Json(name = "status_txt") val statusText: String? = null
)

@JsonClass(generateAdapter = true)
internal data class ImgbbImage(val url: String? = null)

@JsonClass(generateAdapter = true)
internal data class ImgbbError(
    val message: String? = null,
    val code: Int? = null
)
