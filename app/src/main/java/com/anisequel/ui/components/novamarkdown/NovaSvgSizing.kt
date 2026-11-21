package com.anisequel.ui.components.novamarkdown

import kotlin.math.max
import kotlin.math.sqrt

/**
 * The pixels one card's raster may hold.
 *
 * A profile bio card is drawn at the column width, and at a 3x density a wide
 * banner is already 1080x540. The cap only bites on the pathological ones - a
 * "currently watching" strip authored at 0.35 aspect is 1080x3085, which is 13 MiB
 * of ARGB_8888 for one decorative picture. 1.5 MP is about 6 MiB, keeps text in a
 * card legible at phone width and bounds a whole bio to a number the heap can
 * carry.
 */
internal const val NOVA_SVG_RASTER_MAX_PIXELS = 1_500_000

/**
 * The size a raster of a `widthPx x heightPx` slot is drawn at.
 *
 * Scales the slot down uniformly so the aspect ratio survives - the caller draws
 * the bitmap into the same slot, so a scaled raster that kept its shape is exactly
 * what a slightly softer picture needs - and never returns a zero dimension, which
 * `Bitmap.createBitmap` rejects.
 */
internal fun novaSvgRasterSize(
    widthPx: Int,
    heightPx: Int,
    maxPixels: Int = NOVA_SVG_RASTER_MAX_PIXELS
): Pair<Int, Int> {
    if (widthPx <= 0 || heightPx <= 0) return 1 to 1
    val pixels = widthPx.toLong() * heightPx.toLong()
    if (pixels <= maxPixels.toLong()) return widthPx to heightPx

    val scale = sqrt(maxPixels.toDouble() / pixels.toDouble())
    val width = max(1, (widthPx * scale).toInt())
    var height = max(1, (heightPx * scale).toInt())
    // Truncating both dimensions rounds *up* by up to a pixel each, which is
    // enough to land a pixel row over the budget. One correction is all it takes,
    // and the aspect ratio does not survive losing a row any less than it would
    // survive losing a column.
    while (width.toLong() * height > maxPixels.toLong() && height > 1) height--
    return width to height
}

/**
 * The cache key for one card's raster.
 *
 * The slot size is part of it because the raster is drawn back at the exact size it
 * was captured at: a rotation, a fold or a split-screen resize changes the column
 * width, and a bitmap captured for the old width is the wrong one. Keying on the
 * URL alone would stretch it.
 */
internal fun novaSvgRasterKey(url: String, widthPx: Int, heightPx: Int): String =
    "$url#$widthPx×$heightPx"
