package com.anisequel.data.network

import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

/**
 * Uploads one image to pomf.lain.la and returns the URL it is served at.
 *
 * ## Why a second host exists at all
 *
 * `catbox.moe` is the host this app was built around, and in November 2026 it
 * stopped accepting anonymous uploads - the API answers HTTP 200 with
 * "Uploads paused until I can resolve storage issues. Sorry!" for every file. An
 * attachment button that cannot attach anything is not a degraded feature, it is a
 * broken one, so [ImageUploader] falls back here before it tries imgbb.com.
 *
 * ## Why this host
 *
 * It is a pomf clone with the two properties the app needs and most anonymous
 * hosts do not have together: no account or key, and files are kept permanently
 * rather than for hours. (The candidates that were tried and rejected: catbox's own
 * Litterbox answers 403, `0x0.st` has uploads disabled, uguu.se deletes after three
 * hours, and tmpfiles.org after one - an hour-long URL in a comment is a broken
 * image by the time anyone reads it.)
 *
 * The price is the same as catbox's and is stated in Settings: files are public and
 * served by a stranger's server. This class is its own `OkHttpClient` for the
 * reason every third-party client here is - the shared one attaches the reader's
 * AniList bearer token.
 */
class PomfApi(
    private val client: OkHttpClient = CatboxApi.defaultClient(),
    moshi: Moshi = NetworkClient.json
) {

    private val adapter = moshi.adapter(PomfUploadResponse::class.java)

    /**
     * Uploads [bytes] as [fileName] and returns the served URL.
     *
     * The request shape is pomf's, not catbox's: one multipart part named `files[]`
     * and a JSON answer listing what was stored. The body is parsed rather than
     * pattern-matched because the host answers `success: false` with the same 200.
     */
    suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> {
        if (bytes.isEmpty()) return Result.failure(UploadFailure("There was nothing to upload."))

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "files[]",
                fileName,
                bytes.toRequestBody(mimeType.toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url(UPLOAD_URL)
            .header("User-Agent", USER_AGENT)
            .post(body)
            .build()

        // Cancellable for the same reason catbox's is: a reader can close the sheet
        // in the middle of a large transfer, and a blocking `execute` would run to
        // its call timeout with nobody left to answer.
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isActive) return
                        continuation.resume(
                            Result.failure(
                                UploadFailure("Could not reach the image host - check the connection.")
                            )
                        )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!continuation.isActive) return
                            val text = runCatching { it.body?.string().orEmpty() }.getOrDefault("")
                            continuation.resume(
                                if (!it.isSuccessful) {
                                    Result.failure(UploadFailure("The image host refused the upload (HTTP ${it.code})."))
                                } else {
                                    parseResponse(text)
                                }
                            )
                        }
                    }
                }
            )
        }
    }

    /**
     * The URL in a pomf answer, or the reason there is not one.
     *
     * Only an `https` address on this host's own file domain is accepted, for the
     * same reason [CatboxApi.isServedUrl] is strict: whatever comes back is written
     * into a public comment as an image, and a host that answers with an HTML
     * page - a CDN interstitial, a captcha - must not become the embed.
     */
    internal fun parseResponse(text: String): Result<String> {
        val parsed = runCatching { adapter.fromJson(text) }.getOrNull()
            ?: return Result.failure(
                UploadFailure(prose(text) ?: "The image host returned no URL.")
            )
        val url = parsed.files?.firstOrNull()?.url
        if (parsed.success != true || url == null || !isServedUrl(url)) {
            return Result.failure(
                UploadFailure(
                    parsed.error?.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull()
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
     * [CatboxApi.readableProse] takes the first non-blank line of any body, which
     * is right for catbox (it answers errors as a sentence) and wrong here: pomf
     * answers `"success": false` as a JSON document, and that document's first
     * line is a brace. Handing `{"success":false}` to the composer put a line of
     * JSON where the reader was owed a reason.
     */
    private fun prose(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return null
        return CatboxApi.readableProse(text)
    }

    companion object {
        private const val UPLOAD_URL = "https://pomf.lain.la/upload.php"
        private const val USER_AGENT = "AniSequel (Android; comment attachments)"

        /**
         * The host's own file domain.
         *
         * `pomf.lain.la` is the uploader; the file is served from `pomf2.lain.la`,
         * and both are under `lain.la`. The check is on the parsed host, not a
         * prefix of the string, so `https://pomf2.lain.la.evil.example/x.png`
         * cannot pass.
         */
        private val SERVED_HOST_SUFFIX = ".lain.la"

        /** Whether [text] is a URL this host serves a stored file at. */
        internal fun isServedUrl(text: String): Boolean {
            if (text.any { it.isWhitespace() }) return false
            val url = runCatching { text.toHttpUrlOrNull() }.getOrNull() ?: return false
            if (url.scheme != "https") return false
            val host = url.host
            return host.endsWith(SERVED_HOST_SUFFIX) && host.length > SERVED_HOST_SUFFIX.length &&
                url.pathSegments.any { it.isNotBlank() }
        }
    }
}

/** pomf's answer to an upload: whether it succeeded, and the stored files. */
@JsonClass(generateAdapter = true)
data class PomfUploadResponse(
    val success: Boolean? = null,
    /** Present on a refusal; the one field a reader can actually act on. */
    val error: String? = null,
    val files: List<PomfFile>? = null
)

@JsonClass(generateAdapter = true)
data class PomfFile(val url: String? = null)
