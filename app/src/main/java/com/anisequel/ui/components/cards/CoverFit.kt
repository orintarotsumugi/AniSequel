package com.anisequel.ui.components.cards

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Draws a cover-fitted image a hair larger than its box, from the top edge down.
 *
 * `ContentScale.Crop` rounds its destination to whole pixels, and that rounding
 * can come out a pixel *short* of the box it fills. The offset that centres a
 * too-short image puts the missing row at the **top**, and what paints there is
 * the box's own background - which for every cover in this app is the cover's
 * extracted colour. On a saturated cover that reads as a bright line across the
 * top of the artwork, and it thickens while the image is stretched or pinched
 * (the reported "weird yellow line on the top of the cover").
 *
 * One percent of overdraw cannot be out-caled by any rounding, costs a pixel or
 * two of crop the cover fit was trimming anyway, and is a draw-phase scale - no
 * second measure pass on a scrolling list of covers. The origin sits on the top
 * edge so the extra height goes *down* and the extra width stays centred: the
 * top - the edge that had the line - stays pixel-exact.
 */
private const val COVER_OVERDRAW = 1.01f

fun Modifier.unseamedCover(): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(0.5f, 0f)
    scaleX = COVER_OVERDRAW
    scaleY = COVER_OVERDRAW
}
