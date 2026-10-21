package com.anisequel.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.anisequel.domain.color.CoverColorQuantizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a theme seed out of a title's cover art.
 *
 * ## Why the cover and not AniList's `coverImage.color`
 *
 * AniList returns one dominant colour per cover, and it is frequently not the
 * colour a reader sees. It is computed server-side over the whole image - title
 * lettering, borders, studio logos and all - and it is sometimes simply the
 * background wash. The palette built from it therefore matched the *file* rather
 * than the artwork. Material You does not read a wallpaper's average colour
 * either: it quantizes the pixels and scores the resulting regions, which is what
 * [CoverColorQuantizer] now does.
 *
 * The AniList value is still the fallback. It costs nothing, it is already in the
 * response, and a muted but correct colour beats a failed decode on a phone with
 * no network.
 *
 * ## Why Coil rather than a second HTTP client
 *
 * The hero is already loading this exact URL through Coil. Asking Coil again for
 * a 128px decode hits its memory or disk cache, so the colour costs one small
 * bitmap instead of a second download, and it inherits the app's cache policy,
 * headers and GIF handling for free.
 *
 * The decode is deliberately software and deliberately small:
 *
 *  - `allowHardware(false)` because a hardware bitmap cannot be read with
 *    `getPixels`, and Coil will happily return one on API 31+.
 *  - `size(128)` because a palette needs the broad regions of an image, not its
 *    detail. Downscaling is also what stops a 4000px cover from being a 16MB
 *    allocation per open.
 *
 * ## The cache
 *
 * Keyed by URL, so scrolling a list and opening the same title twice does not
 * decode twice. A handful of entries is enough - only the open sheet's URL is
 * ever read - and the cap keeps a long session from retaining every cover the
 * reader has visited.
 */
object CoverColorRepository {

    private const val SAMPLE_SIZE = 128
    private const val CACHE_ENTRIES = 64

    private val seeds = object : LruCache<String, Int>(CACHE_ENTRIES) {}

    /** A seed already extracted for [url], if this session has seen it. */
    fun cached(url: String?): Int? = url?.let(seeds::get)

    /**
     * Extracts a seed for [url], or null when the image cannot be read.
     *
     * Null is a normal result, not an error: the caller falls back to AniList's
     * colour and then to the brand. The only failure worth catching is an image
     * that will not decode; a colour is never worth crashing a detail sheet for.
     */
    suspend fun extract(context: Context, url: String?): Int? {
        if (url.isNullOrBlank()) return null
        cached(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(SAMPLE_SIZE)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()

                val result = context.imageLoader.execute(request)
                if (result !is SuccessResult) return@withContext null

                val bitmap = result.drawable.toSoftwareBitmap() ?: return@withContext null
                val width = bitmap.width
                val height = bitmap.height
                if (width <= 0 || height <= 0) return@withContext null

                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val seed = CoverColorQuantizer.seedFrom(pixels)
                if (seed != null) seeds.put(url, seed)
                seed
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                // Coil throws this when the sheet closes mid-decode. Swallowing it
                // would make the cancellation look like a failed image, and the
                // caller would apply a fallback colour to a sheet that is gone.
                throw cancellation
            } catch (_: Exception) {
                // A cover that fails to decode, a URL that 404s, a cancelled
                // request: all mean "no seed", which the caller already handles.
                null
            }
        }
    }

    private fun android.graphics.drawable.Drawable?.toSoftwareBitmap(): Bitmap? {
        if (this == null) return null
        val bitmap = if (this is BitmapDrawable) {
            this.bitmap
        } else {
            // `toBitmap` is the one conversion that works for a vector or a
            // custom drawable; a cover is normally a BitmapDrawable, but a GIF
            // cover is not.
            this.toBitmap()
        } ?: return null
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    }
}
