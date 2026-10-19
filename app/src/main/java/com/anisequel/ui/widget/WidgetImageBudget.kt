package com.anisequel.ui.widget

import kotlin.math.sqrt

/**
 * How many pixels a widget cover may decode to, on this display.
 *
 * ## The failure this prevents
 *
 * [WidgetCover] used to decode at a fixed target size with a fixed pixel
 * backstop. That is fine for one tile on one phone. It is not fine for the case
 * widgets actually ship in: several instances stacked across a home screen,
 * each update decoding a poster while the launcher is also drawing the
 * wallpaper, and a low-memory device deciding which process to kill. The budget
 * is derived from the display and divided by the number of images the update
 * will decode, so the total stays a fraction of what the screen itself costs
 * rather than a number chosen on a developer's device.
 *
 * ## The constants
 *
 * They are deliberately conservative and are not display byte counts:
 *
 *  - [DISPLAY_MULTIPLIER] - a display allowance in **bytes**: six times the
 *    display's pixel count, which at [BYTES_PER_PIXEL] is 1.5x what the screen
 *    itself costs. A tile is small; this is a ceiling, not a target.
 *  - [SAFETY_FRACTION] - leave most of that ceiling to the rest of the process.
 *  - [BYTES_PER_PIXEL] - the byte-scaled allowance is divided by this to reach
 *    pixels, because that is the unit a decode budget is spent in.
 *  - [MAX_EDGE_PX] / [MIN_EDGE_PX] - no width is worth decoding past 480px for
 *    a home-screen tile, and no budget is ever allowed to round down to zero.
 *    A zero-sized decode is not a cheaper decode; it is a blank tile.
 *  - [COVER_ASPECT] - height / width for AniList cover art. Used to turn a pixel
 *    budget into two edges without asking the network for dimensions first.
 *
 * Pure Kotlin with no Android types so the arithmetic can be tested on the JVM,
 * which is where the degenerate cases (zero images, a zero-sized display) live.
 */
internal object WidgetImageBudget {

    const val DISPLAY_MULTIPLIER = 6
    const val SAFETY_FRACTION = 0.4f
    const val BYTES_PER_PIXEL = 4
    const val MAX_EDGE_PX = 480
    const val MIN_EDGE_PX = 48
    const val COVER_ASPECT = 1.4f

    /**
     * The per-image pixel ceiling for a display of [displayWidthPx] by
     * [displayHeightPx] showing [imageCount] covers.
     *
     * Never returns zero and never returns more than [MAX_EDGE_PX] squared. The
     * former matters because a caller hands this straight to a sample-size
     * calculation, and zero there would ask for an infinitely down-sampled
     * bitmap; the latter because the display term is unbounded on a large
     * tablet and a poster tile does not get better past 480px.
     */
    fun maxPixels(displayWidthPx: Int, displayHeightPx: Int, imageCount: Int): Int {
        val displayPixels = displayWidthPx.toLong().coerceAtLeast(1) *
            displayHeightPx.toLong().coerceAtLeast(1)
        val images = imageCount.coerceAtLeast(1)
        val perImage = (
            displayPixels.toDouble() * DISPLAY_MULTIPLIER * SAFETY_FRACTION /
                BYTES_PER_PIXEL / images
            ).toLong()

        val ceiling = MAX_EDGE_PX.toLong() * MAX_EDGE_PX
        val floor = MIN_EDGE_PX.toLong() * MIN_EDGE_PX
        return perImage.coerceIn(floor, ceiling).toInt()
    }

    /**
     * A width/height pair that fits [maxPixels] at [COVER_ASPECT], with both
     * edges clamped to the same floor and ceiling.
     *
     * Exposed for providers that want to size a placeholder before any bitmap
     * exists. The requested dp size is still the target; this is the cap.
     */
    fun targetSize(maxPixels: Int): Pair<Int, Int> {
        val pixels = maxPixels.coerceAtLeast(1).toDouble()
        val width = sqrt(pixels / COVER_ASPECT).toInt().coerceIn(MIN_EDGE_PX, MAX_EDGE_PX)
        // Only the width is capped: the height follows the cover aspect, and
        // clamping it independently would distort the poster rather than save
        // memory - the pixel budget already bounds the area.
        val height = (width * COVER_ASPECT).toInt().coerceAtLeast(MIN_EDGE_PX)
        return width to height
    }
}
