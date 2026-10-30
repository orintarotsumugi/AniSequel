package com.anisequel.ui.components.detail

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Saves a title's cover artwork to `Pictures/AniSequel`.
 *
 * ## Why the bytes are fetched rather than the decoded bitmap re-encoded
 *
 * The viewer already holds a decoded bitmap, and `Bitmap.compress` would avoid a
 * second request - but it would also re-encode a JPEG the CDN already tuned, at
 * full resolution, and a 2:3 poster saved at quality 95 comes back *larger* than
 * the original. Copying the response bytes preserves the artwork exactly and
 * keeps whatever format AniList served (the CDN answers WebP for some covers).
 *
 * ## Why there is no storage permission
 *
 * `minSdk` is 31, so the app can only install where scoped storage is the law:
 * `MediaStore` with `RELATIVE_PATH` writes into the shared Pictures collection
 * without `WRITE_EXTERNAL_STORAGE`, which no longer exists as a runtime grant.
 * The row is `IS_PENDING` while the bytes are in flight, so a gallery app scanning
 * mid-write never sees half a cover; a failed write deletes the pending row rather
 * than leaving an empty entry behind.
 */
internal suspend fun saveCoverToGallery(
    context: Context,
    coverUrl: String,
    title: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        val response = imageClient.newCall(Request.Builder().url(coverUrl).build()).execute()
        response.use { result ->
            if (!result.isSuccessful) return@withContext false
            val body = result.body ?: return@withContext false
            val mime = body.contentType()?.toString()?.substringBefore(';') ?: "image/jpeg"
            val extension = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${coverFileName(title)}.$extension")
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/AniSequel"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false

            val written = resolver.openOutputStream(uri)?.use { output ->
                body.byteStream().copyTo(output)
                true
            } ?: false
            if (!written) {
                resolver.delete(uri, null, null)
                return@withContext false
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }
}

/**
 * Its own client, deliberately not the shared API one.
 *
 * The shared client carries the AniList auth interceptor, and cover art is a
 * public CDN image that must never be requested with the user's bearer token
 * attached - the same rule `WidgetCover` and `UpdateManager` record. The timeouts
 * are shorter than a default client's: this runs behind a tap with a spinner on
 * screen, so a dead CDN has to fail into the error state instead of hanging.
 */
private val imageClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

/**
 * A file name that survives every filesystem without becoming a different name.
 *
 * The title is user-visible text that can contain `/`, a colon, or a leading dot;
 * everything outside a conservative set becomes a dash, and the result is bounded
 * so the gallery name stays readable.
 */
private fun coverFileName(title: String): String =
    title.trim()
        .replace(Regex("[^A-Za-z0-9._ -]+"), "-")
        .trim('-', '.', ' ')
        .take(60)
        .ifBlank { "cover" }
