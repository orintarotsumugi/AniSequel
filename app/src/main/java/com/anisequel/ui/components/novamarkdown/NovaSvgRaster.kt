package com.anisequel.ui.components.novamarkdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

/**
 * How many samples a raster is inspected at, per axis, before it counts as blank.
 *
 * A 32x32 grid is 1024 `getPixel` calls - microseconds - and it is dense enough
 * that no real card, whose content spans its width, can slip between the samples.
 */
private const val INK_SAMPLES = 32

/** A sampled pixel counts as content only once it is more than a rounding error. */
private const val INK_MIN_ALPHA = 24

/**
 * Samples that must carry content before the raster is trusted.
 *
 * The interesting failure is one-sided: a card that came back blank and is *shown*
 * as a bitmap deletes the picture the bio was written around, while a card wrongly
 * judged blank just keeps its WebView - which is what the app did before any of
 * this. So the bar is set high enough that stray antialiasing cannot clear it and
 * low enough that a couple of lines of text in a corner do.
 */
private const val INK_MIN_PIXELS = 8

/**
 * One card's WebView drawn into a bitmap.
 *
 * ## Why a raster at all
 *
 * A `WebView` in a `LazyColumn` is the most expensive child Compose can be asked
 * to carry. Each one is a Chromium renderer with its own surface, it redraws on
 * every animated frame of the SVG it holds, and a lazy list disposes and rebuilds
 * it when the bio comes back into view - so scrolling a profile with three stat
 * cards paid for three page loads per pass, on the main thread. Scrolling a
 * picture costs a blit.
 *
 * The card is therefore drawn once and released: this returns the pixels, the
 * caller swaps the `AndroidView` for an `Image`, and `AndroidView`'s `onRelease`
 * destroys the WebView. What the reader loses is an animated banner (it becomes
 * the frame it animated from); what they gain is a bio that scrolls.
 *
 * ## Why the layer type is switched first
 *
 * Chromium draws into its own surface when the view is hardware accelerated, and
 * `draw` into a software `Canvas` then comes back empty. Software is the layer
 * type that makes the page render into the canvas it is given. It is restored
 * afterwards, because a failed capture leaves the WebView on screen and it should
 * not stay on the slow path.
 */
internal fun captureNovaSvg(view: View): Bitmap? {
    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0) return null

    val (rasterWidth, rasterHeight) = novaSvgRasterSize(width, height)
    val bitmap = Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888)
    val previousLayer = view.layerType
    return try {
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val canvas = Canvas(bitmap)
        canvas.scale(rasterWidth / width.toFloat(), rasterHeight / height.toFloat())
        view.draw(canvas)
        if (novaSvgBitmapHasInk(bitmap)) {
            bitmap
        } else {
            bitmap.recycle()
            null
        }
    } catch (_: Throwable) {
        // A WebView destroyed underneath the capture, or a device whose software
        // path refuses the page, lands here: the card keeps its WebView.
        if (!bitmap.isRecycled) bitmap.recycle()
        null
    } finally {
        runCatching { view.setLayerType(previousLayer, null) }
    }
}

/**
 * Whether a raster holds anything the reader would see.
 *
 * Alpha only, and by sampling: the question is "did the page paint", not "what did
 * it paint". A card that is legitimately transparent in a corner still passes,
 * because the grid reaches the parts that are not.
 */
internal fun novaSvgBitmapHasInk(bitmap: Bitmap): Boolean {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
    val stepX = maxOf(1, bitmap.width / INK_SAMPLES)
    val stepY = maxOf(1, bitmap.height / INK_SAMPLES)
    var inked = 0
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            // The alpha byte of an ARGB pixel, named rather than parenthesised:
            // `ushr` binds tighter than `>=`, so a reader should not have to know
            // that to be sure this compares the alpha and not the colour.
            val alpha = bitmap.getPixel(x, y) ushr 24
            if (alpha >= INK_MIN_ALPHA) {
                inked++
                if (inked >= INK_MIN_PIXELS) return true
            }
            x += stepX
        }
        y += stepY
    }
    return false
}

/**
 * The attempts one WebView gets at being captured.
 *
 * `onPageFinished` fires before Chromium has committed the first frame it drew -
 * fonts arrive late in these cards, embedded ones especially - so the first draw
 * can still be empty. Three attempts over about a quarter of a second either finds
 * the painting or accepts that this card will stay a WebView.
 */
internal class NovaSvgCaptureSession {
    private var attempts = 0

    /** Set by `onRelease`, which runs before the view is destroyed. */
    var released = false

    fun schedule(
        view: View,
        onCaptured: (Bitmap) -> Unit,
        onExhausted: () -> Unit
    ) {
        if (released || attempts >= CAPTURE_ATTEMPTS) return
        val delay = if (attempts == 0) 0L else CAPTURE_RETRY_DELAY_MS
        view.postDelayed(
            {
                if (released) return@postDelayed
                attempts++
                val bitmap = captureNovaSvg(view)
                if (bitmap != null) {
                    onCaptured(bitmap)
                } else if (attempts < CAPTURE_ATTEMPTS) {
                    schedule(view, onCaptured, onExhausted)
                } else {
                    onExhausted()
                }
            },
            delay
        )
    }

    private companion object {
        const val CAPTURE_ATTEMPTS = 3
        const val CAPTURE_RETRY_DELAY_MS = 120L
    }
}
