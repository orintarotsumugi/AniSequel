package com.anisequel.data.network

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Uploads one image to catbox.moe and returns the URL it is served at.
 *
 * ## Why the app needs a host at all
 *
 * AniList has no upload endpoint. Its own composer asks for an image *URL*, which
 * is fine on a desktop with ten tabs open and useless on a phone holding the photo
 * they want to post. This class is the missing half: the reader picks a file, it
 * goes to a host, and the URL that comes back is written into the comment as
 * `img(...)`. Nothing about it is AniList-specific - it is the same shape as
 * [AnimeThemesApi] and [LiveChartPosterApi], one third party, one small surface.
 *
 * ## Why catbox, and what that costs
 *
 * It takes an anonymous upload with no account, no key and no OAuth, over HTTPS,
 * and serves the file back at `files.catbox.moe` unchanged - which is the only
 * reason an app like this can offer "attach a photo" without asking anybody to
 * register for an API key first. The price is retention: files uploaded without an
 * account are kept until they have two years of no hits, so a comment's image can
 * go away on a thread nobody visits. That is stated in Settings rather than hidden,
 * because a reader who finds a dead embed six months later should be able to
 * remember what they chose.
 *
 * ## Never the AniList token
 *
 * The client below is its own [OkHttpClient] with no interceptors, for the same
 * reason [AnimeThemesApi], [LiveChartPosterApi], the widget's cover loader and the
 * update client keep theirs: the shared client attaches the reader's bearer token
 * to every request, and a file host has no business receiving a credential for
 * their whole AniList account.
 */
class CatboxApi(
    private val client: OkHttpClient = defaultClient()
) : ImageHost {

    /**
     * Uploads [bytes] as [fileName] and returns the served URL.
     *
     * Fails with an [UploadFailure] whose message is safe to show: the host answers
     * errors as plain prose ("Invalid file type.", "The file ... is too large"),
     * which is more useful to the reader than "HTTP 200", and it is the whole
     * response body when that body is not a URL - so a Cloudflare page or a rate
     * limit notice reaches the composer as something they can read instead of a
     * silent spinner that stops.
     */
    override suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> {
        if (bytes.isEmpty()) return Result.failure(UploadFailure("There was nothing to upload."))

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart(
                "fileToUpload",
                fileName,
                bytes.toRequestBody(mimeType.toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url(UPLOAD_URL)
            // The host is a small site behind a CDN that has been known to block
            // requests with no client identity at all; a plain descriptive UA is
            // what every other catbox client sends.
            .header("User-Agent", USER_AGENT)
            .post(body)
            .build()

        // `enqueue`, not `execute`: the reader can close the sheet in the middle of a
        // two-megabyte upload over a phone connection, and a blocking call cannot be
        // interrupted - it runs to the 150-second call timeout with nobody left to
        // answer, holding a thread and the file's bytes with it. Cancelled from the
        // coroutine, the transfer stops on the next chunk.
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        // A call the coroutine cancelled answers here too, with an
                        // IOException that says "canceled". Reporting that would blame
                        // the reader's connection for pressing Back, and the
                        // continuation cannot take a result once it is resumed by
                        // cancellation anyway.
                        if (!continuation.isActive) return
                        continuation.resume(
                            Result.failure(
                                UploadFailure("Could not reach catbox.moe - check the connection.")
                            )
                        )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!continuation.isActive) return
                            val text = runCatching { it.body?.string().orEmpty().trim() }.getOrDefault("")
                            continuation.resume(
                                when {
                                    !it.isSuccessful ->
                                        Result.failure(UploadFailure(describe(it.code, text)))
                                    isServedUrl(text) -> Result.success(text)
                                    // HTTP 200 with a refusal in the body is this API's
                                    // normal error shape - there is no status code to
                                    // branch on, only the text.
                                    else -> Result.failure(
                                        UploadFailure(readableProse(text) ?: "The host returned no URL.")
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
    }

    private fun describe(status: Int, text: String): String = when (status) {
        429 -> "catbox.moe is rate limiting uploads. Try again in a minute."
        in 500..599 -> "catbox.moe is not taking uploads right now (HTTP $status)."
        else -> readableProse(text)
            ?: "catbox.moe refused the upload (HTTP $status)."
    }



    companion object {
        private const val UPLOAD_URL = "https://catbox.moe/user/api.php"
        private const val SERVED_HOST = "https://files.catbox.moe/"
        private const val MAX_REASON_CHARS = 160
        private const val USER_AGENT = "AniSequel (Android; comment attachments)"

        /**
         * The host's refusal, as one line a reader can act on - or null.
         *
         * catbox answers upload errors as plain prose ("Invalid file type.", "The
         * file ... is too large"), which says more than "HTTP 200" ever could. The
         * same position is also what a CDN interstitial page fills when the request
         * never reached the uploader, and that page's first line is
         * `<!DOCTYPE html>` - which used to be handed to the reader as the reason
         * their photo did not upload. Markup is not prose, so it is refused here and
         * the caller says the plain thing it knows instead.
         */
        internal fun readableProse(text: String): String? {
            val line = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
            if (line.isEmpty() || line.startsWith("<")) return null
            return if (line.length <= MAX_REASON_CHARS) line else line.take(MAX_REASON_CHARS) + "…"
        }

        /** Whether a response body is the URL of the file that was just stored. */
        fun isServedUrl(text: String): Boolean =
            text.startsWith(SERVED_HOST, ignoreCase = true) &&
                text.length > SERVED_HOST.length &&
                !text.any { it.isWhitespace() }

        internal fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // An upload is the slowest thing this app does on a phone connection:
            // a megabyte of photo over LTE takes longer than any JSON read, and a
            // timeout sized for the AniList API would abort it mid-transfer and
            // blame the reader's file.
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            // True, and it is the reachability that matters rather than the retry
            // semantics: catbox sits behind a CDN whose DNS answers both families,
            // and on an LTE bearer with broken IPv6 the first route fails while a
            // working address is still in the list. With this off OkHttp surfaces
            // that first failure as "Could not reach catbox.moe" and never tries the
            // other. A file upload that has begun is not replayed by OkHttp, so the
            // duplicate-hosting cost this guard was protecting against is not what
            // it prevents here.
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * An upload that did not happen, with a message written for the composer rather
 * than for a log: the reader is holding a file they chose and a reason is the only
 * thing that tells them what to do next.
 */
class UploadFailure(message: String) : IOException(message)
