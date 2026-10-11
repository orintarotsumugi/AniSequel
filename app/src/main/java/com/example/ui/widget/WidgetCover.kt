package com.example.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Loads a widget's cover art off the main thread, and draws the placeholder when
 * there is none.
 *
 * ## Why this is hand-rolled
 *
 * A `RemoteViews` widget needs a plain [Bitmap] - it cannot host a Compose image
 * and the app's `ImageVector` pipeline is unreachable from the launcher's
 * process. The obvious library answer, Coil, only reaches widgets through Glance,
 * and Glance would replace the classic `AppWidgetProvider` + `RemoteViews`
 * widgets this app already ships (and add a dependency on top of it). A short
 * OkHttp fetch and a `BitmapFactory` decode is the whole job.
 *
 * ## Why every failure is a null instead of a throw
 *
 * Widgets are updated outside the app's lifecycle and, since the fetch runs on
 * its own thread, an exception escaping that thread would take the whole process
 * with it. Offline, a 404, a dead CDN and an `OutOfMemoryError` on a poster the
 * device cannot hold are all normal outcomes here; the caller falls back to the
 * dominant-colour placeholder and the tile still looks deliberate.
 */
object WidgetCover {

    /**
     * Its own client, deliberately not the shared API one.
     *
     * The shared client carries the AniList auth interceptor, and cover art is a
     * public CDN image that must never be requested with the user's bearer token
     * attached - the same reasoning `UpdateManager` records for its manifest
     * fetch.
     */
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Hard ceiling on a decoded cover, in pixels (~400 KB at `RGB_565`).
     *
     * A `RemoteViews` bitmap is marshalled through a Binder transaction with a
     * 1 MB budget, and the sample-size rule below only guarantees that both
     * edges clear the requested size - an unusually tall or wide source could
     * still decode to a million pixels. This is the backstop that keeps every
     * cover inside the budget regardless of what the CDN returns.
     */
    private const val MAX_DECODED_PIXELS = 200_000L

    /**
     * Fetches [url] and hands the decoded bitmap (or null) to [onLoaded].
     *
     * The callback runs on a background thread; it is always invoked exactly
     * once, including for a null or blank [url], so a caller using it to finish a
     * `goAsync()` pending result can do so in a `finally` without leaking it.
     */
    fun loadAsync(
        url: String?,
        targetWidthPx: Int,
        targetHeightPx: Int,
        onLoaded: (Bitmap?) -> Unit
    ) {
        if (url.isNullOrBlank()) {
            onLoaded(null)
            return
        }

        Thread {
            val bitmap = try {
                download(url, targetWidthPx, targetHeightPx)
            } catch (t: Throwable) {
                null
            }
            try {
                onLoaded(bitmap)
            } catch (t: Throwable) {
                // A throwing callback must not escape this Thread: the default
                // handler would kill the app's process over a home-screen tile.
            }
        }.apply {
            name = "widget-cover"
            // The tile is never worth competing with the foreground for cores.
            priority = Thread.MIN_PRIORITY
        }.start()
    }

    /**
     * A rounded rectangle filled with [colorHex], sized for the ImageView.
     *
     * Never null, and that is the point. A malformed AniList colour - or none at
     * all - draws a transparent rectangle instead, so a caller can hand the
     * result straight to `RemoteViews.setImageViewBitmap` and leave the layout's
     * own placeholder visible behind it. The earlier version returned null for an
     * unparseable colour, which put a null bitmap through `setImageViewBitmap`
     * from `onUpdate` on the main thread - a widget update is not worth betting
     * the app process on how the platform handles that. Drawn rather than set as a
     * background so the corners stay rounded while the colour comes from data:
     * `RemoteViews` can set a view's background colour, but that replaces the
     * shape drawable with a square.
     */
    fun roundedPlaceholder(context: Context, colorHex: String?, widthPx: Int, heightPx: Int): Bitmap {
        val color = colorHex?.let { parseColor(it) } ?: Color.TRANSPARENT
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)

        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            if (color != Color.TRANSPARENT) {
                val radius = (8f * context.resources.displayMetrics.density)
                    .coerceAtMost(min(width, height) / 2f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                Canvas(bitmap).drawRoundRect(
                    0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint
                )
            }
            bitmap
        } catch (t: Throwable) {
            // A 1x1 transparent bitmap still beats a null one at the call site.
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    /** Density-independent pixels, for the decode target and the placeholder. */
    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun download(url: String, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            return decodeScaled(bytes, targetWidthPx, targetHeightPx)
        }
    }

    /**
     * Two-pass decode with an `inSampleSize`.
     *
     * A cover is at most a couple of megabytes of bytes but decodes to far more
     * pixels than a 48dp tile can show, and decoding at full size was how a
     * widget could push a low-memory device into an OOM during an update. The
     * first pass reads only the dimensions, so the second never allocates more
     * than roughly the requested size.
     */
    private fun decodeScaled(bytes: ByteArray, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
        if (targetWidthPx <= 0 || targetHeightPx <= 0) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetWidthPx, targetHeightPx)
            // Covers are opaque JPEG/PNG, and a widget bitmap travels through a
            // Binder transaction with a hard 1 MB budget. Halving the bytes here
            // is what keeps a 4-cell poster comfortably inside it.
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** The largest power of two that keeps both decoded edges at or above target. */
    private fun sampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / sample >= reqWidth && halfHeight / sample >= reqHeight) {
            sample *= 2
        }
        // Then the Binder-budget backstop: down-sample further while the decoded
        // bitmap would exceed the pixel ceiling.
        while ((width / sample).toLong() * (height / sample).toLong() > MAX_DECODED_PIXELS) {
            sample *= 2
        }
        return sample
    }

    private fun parseColor(hex: String): Int? = try {
        Color.parseColor(hex)
    } catch (e: IllegalArgumentException) {
        null
    }
}
