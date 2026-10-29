package com.anisequel.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
     * One background thread for every cover fetch in the app.
     *
     * A widget update used to spawn a fresh `Thread` per tile per update. With
     * four providers, a system tick plus an app-driven refresh could put several
     * downloads in flight at once, and each would compete with the foreground for
     * cores until it finished. A single low-priority executor serialises them: a
     * home-screen tile is a thirty-minute glance, not something worth delaying a
     * frame for.
     */
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "widget-cover").apply { priority = Thread.MIN_PRIORITY }
    }

    /**
     * A second thread for cache hits.
     *
     * A hit used to be posted to [executor], the same single thread every
     * download runs on, so its comment about answering "before any download is
     * queued" described the check but not the callback: a tile whose art was
     * already decoded still waited behind somebody else's fetch. Hits are
     * cheap, so one thread of their own is enough - and it keeps the callback
     * contract that every render happens off the caller's thread.
     */
    private val cacheExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "widget-cover-cache").apply { priority = Thread.MIN_PRIORITY }
    }

    /**
     * Hard ceiling on a decoded cover, in pixels (~400 KB at `RGB_565`).
     *
     * A `RemoteViews` bitmap is marshalled through a Binder transaction with a
     * 1 MB budget, and the sample-size rule below only guarantees that both
     * edges clear the requested size - an unusually tall or wide source could
     * still decode to a million pixels. This is the backstop that keeps every
     * cover inside the budget regardless of what the CDN returns.
     */
    private const val MAX_DECODED_PIXELS = 200_000

    /**
     * Cached placeholder rectangles, bounded by decoded byte count.
     *
     * A placeholder is a static shape - a rounded rectangle in one of a handful
     * of dominant colours - so drawing it per update per tile allocated the same
     * bitmap over and over, on the main thread, inside `onUpdate`. The key
     * carries the colour and the pixel size, so a density change or a different
     * entry still misses once and reuses after.
     */
    private val placeholders = object : LruCache<String, Bitmap>(PLACEHOLDER_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * Decoded covers, so a home-screen refresh of an unchanged tile costs no
     * network call - and, just as importantly, no decode.
     *
     * This is the "fast-cover" window: a hit is answered from here immediately,
     * before any download is queued, so text and art are painted together. A
     * miss goes on to the full download budget. The earlier implementation
     * always queued the fetch, which meant a refresh behind a slow in-flight
     * download could leave the tile on its placeholder long after the data it
     * already held was ready to draw.
     *
     * Bounded by decoded bytes. Four tiles' worth is the working set; a reader
     * with more copies of a widget than that gets a cache miss, not an OOM.
     */
    private val covers = object : LruCache<String, Bitmap>(COVER_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** ~2 MB of decoded covers. */
    private const val COVER_CACHE_BYTES = 2 * 1024 * 1024

    /** ~512 KB of cached placeholders: a few tiles' worth, never a memory story. */
    private const val PLACEHOLDER_CACHE_BYTES = 512 * 1024

    /**
     * Where saved widget cover bytes live, and how many are kept.
     *
     * The directory is under `noBackupFilesDir` for the same reason Coil's is:
     * a cover is kept until AniList changes it, and `cacheDir` is a directory
     * the system is entitled to empty. Eight tiles' worth of URLs is enough for
     * a reader with more than one widget plus the next season's artwork; older
     * files are unreachable by construction, because a changed cover gets a new
     * URL.
     */
    private const val SAVED_COVER_DIRECTORY = "widget_cover_cache"
    private const val SAVED_COVER_MAX_FILES = 8
    private const val HEX_DIGITS = "0123456789abcdef"

    /**
     * Fetches [url] and hands the decoded bitmap (or null) to [onLoaded].
     *
     * The callback always runs on a background thread owned by this loader -
     * the download thread for a miss, [cacheExecutor] for a hit - so a caller
     * using it to finish a `goAsync()` pending result can do so in a `finally`
     * without leaking it, and without ever running a render on the caller's
     * thread by surprise.
     */
    fun loadAsync(
        context: Context,
        url: String?,
        targetWidthPx: Int,
        targetHeightPx: Int,
        /** The per-image decode ceiling, normally from [WidgetImageBudget]. */
        maxPixels: Int = MAX_DECODED_PIXELS,
        onLoaded: (Bitmap?) -> Unit
    ) {
        // Cache-hit path, deliberately before the executor: a tile whose art is
        // already in hand should not queue behind someone else's download. This
        // is the fast window; a miss falls through to the full budget.
        // The key includes the decode size: the same poster at 48dp and at a
        // resized 96dp tile are two different bitmaps, and serving the smaller
        // one would upscale it into a blurry tile.
        val cacheKey = url?.let { "$it|${targetWidthPx}x${targetHeightPx}" }
        if (cacheKey != null) {
            covers.get(cacheKey)?.let { cached ->
                cacheExecutor.execute {
                    try {
                        onLoaded(cached)
                    } catch (t: Throwable) {
                        // Never let a widget update take the process down.
                    }
                }
                return
            }
        }

        val appContext = context.applicationContext
        executor.execute {
            val bitmap = if (url.isNullOrBlank()) {
                null
            } else {
                try {
                    // The saved bytes first, and the network only on a miss.
                    //
                    // A widget is refreshed on a timer and on every app start,
                    // and the decoded `covers` cache above lives only as long as
                    // the process. Without this every launcher refresh after the
                    // process was killed downloaded the same four posters again,
                    // which is exactly the "the cover keeps disappearing"
                    // behaviour the app cache no longer has. URL-keyed, like
                    // Coil's, so AniList replacing the artwork produces a new
                    // URL and therefore a miss - a cover is never served stale.
                    var bytes = readSavedCover(appContext, url)
                    var decoded = bytes?.let {
                        decodeScaled(it, targetWidthPx, targetHeightPx, maxPixels)
                    }
                    // A saved file that will not decode is not a cover. It is
                    // overwritten rather than left to fail on every refresh -
                    // the bytes may have been truncated by a device that lost
                    // power mid-write before the rename above existed.
                    if (decoded == null) {
                        bytes = downloadBytes(url)
                        if (bytes != null) {
                            saveCover(appContext, url, bytes)
                            decoded = decodeScaled(bytes, targetWidthPx, targetHeightPx, maxPixels)
                        }
                    }
                    decoded?.also { if (cacheKey != null) covers.put(cacheKey, it) }
                } catch (t: Throwable) {
                    null
                }
            }
            try {
                onLoaded(bitmap)
            } catch (t: Throwable) {
                // A throwing callback must not escape this thread: the default
                // handler would kill the app's process over a home-screen tile.
            }
        }
    }

    /**
     * The rounded rectangle a tile draws behind its cover, cached.
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
    fun placeholder(context: Context, colorHex: String?, widthPx: Int, heightPx: Int): Bitmap {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val key = "${colorHex.orEmpty()}|$width|$height"

        placeholders.get(key)?.let { return it }

        val color = colorHex?.let { parseColor(it) } ?: Color.TRANSPARENT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (color != Color.TRANSPARENT) {
            val radius = (8f * context.resources.displayMetrics.density)
                .coerceAtMost(min(width, height) / 2f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            Canvas(bitmap).drawRoundRect(
                0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint
            )
        }
        placeholders.put(key, bitmap)
        return bitmap
    }

    /** Density-independent pixels, for the decode target and the placeholder. */
    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun downloadBytes(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.bytes()
        }
    }

    /**
     * The saved copy of [url]'s bytes, or null when this cover has never been
     * fetched or the file is unreadable.
     *
     * Read failures are normal, not exceptional: `noBackupFilesDir` can be
     * absent on a wiped profile, a file can be truncated by a device that lost
     * power mid-write, and a hashed filename is not something a reader can fix.
     * The caller downloads in every one of those cases.
     */
    private fun readSavedCover(context: Context, url: String): ByteArray? = try {
        val file = savedCoverFile(context, url)
        if (file.isFile && file.length() > 0) file.readBytes() else null
    } catch (t: Throwable) {
        null
    }

    /**
     * Writes [bytes] for [url], best effort.
     *
     * The `.part` file and the rename are the whole point. A half-written cache
     * file that still has the cover's name would be served on the next refresh
     * and fail to decode - and because the URL has not changed, it would keep
     * failing forever. Writing beside the target and renaming means a reader
     * either gets the previous complete file or the new complete one.
     */
    private fun saveCover(context: Context, url: String, bytes: ByteArray) {
        try {
            val target = savedCoverFile(context, url)
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.part")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                temporary.delete()
                return
            }
            pruneSavedCovers(target.parentFile)
        } catch (t: Throwable) {
            // A full disk or a directory that cannot be created costs one
            // download on the next refresh, never a broken tile.
        }
    }

    /**
     * Keeps the newest [SAVED_COVER_MAX_FILES] covers.
     *
     * AniList gives changed artwork a new URL, so old files are not overwritten
     * - they become unreachable. Without a bound a reader who keeps up with new
     * seasons would leave one file per cover they had ever seen in app storage.
     * The working set is the handful of tiles on the home screen, so a small
     * ceiling costs nothing a widget can notice.
     */
    private fun pruneSavedCovers(directory: File?) {
        val files = directory
            ?.listFiles { file -> file.isFile && !file.name.endsWith(".part") }
            ?: return
        if (files.size <= SAVED_COVER_MAX_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - SAVED_COVER_MAX_FILES)
            .forEach { it.delete() }
    }

    /** A stable, filesystem-safe name for [url]: URL raw bytes are far too long. */
    private fun savedCoverFile(context: Context, url: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte ->
                val value = byte.toInt() and 0xFF
                "${HEX_DIGITS[value ushr 4]}${HEX_DIGITS[value and 0x0F]}"
            }
        return File(
            File(context.noBackupFilesDir, SAVED_COVER_DIRECTORY),
            digest
        )
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
    private fun decodeScaled(
        bytes: ByteArray,
        targetWidthPx: Int,
        targetHeightPx: Int,
        maxPixels: Int
    ): Bitmap? {
        if (targetWidthPx <= 0 || targetHeightPx <= 0) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(
                bounds.outWidth,
                bounds.outHeight,
                targetWidthPx,
                targetHeightPx,
                maxPixels
            )
            // Covers are opaque JPEG/PNG, and a widget bitmap travels through a
            // Binder transaction with a hard 1 MB budget. Halving the bytes here
            // is what keeps a 4-cell poster comfortably inside it.
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asSoftware()
    }

    /**
     * A bitmap that can cross the `RemoteViews` Binder boundary.
     *
     * A hardware bitmap is backed by the GPU and cannot be marshalled to the
     * launcher's process, so `setImageViewBitmap` with one is a blank tile at
     * best and a RemoteViews failure at worst - on exactly the devices whose
     * allocator decided to return `HARDWARE` for an `ARGB_8888` decode. The
     * decode asks for `RGB_565`, which is normally software already; this is the
     * explicit guarantee for every path that returns a bitmap (cache included).
     */
    private fun Bitmap.asSoftware(): Bitmap =
        if (config == Bitmap.Config.HARDWARE) {
            copy(Bitmap.Config.ARGB_8888, false) ?: this
        } else {
            this
        }

    /** The largest power of two that keeps both decoded edges at or above target. */
    private fun sampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
        maxPixels: Int
    ): Int {
        var sample = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / sample >= reqWidth && halfHeight / sample >= reqHeight) {
            sample *= 2
        }
        // Then the budget backstop: down-sample further while the decoded
        // bitmap would exceed the per-image ceiling computed from the display.
        val ceiling = maxPixels.coerceAtLeast(1).toLong()
        while ((width / sample).toLong() * (height / sample).toLong() > ceiling) {
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
