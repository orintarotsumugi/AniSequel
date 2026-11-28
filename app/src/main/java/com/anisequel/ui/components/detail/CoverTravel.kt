package com.anisequel.ui.components.detail

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * The rectangle a travelling cover occupies at [progress]: the card's at 0, the
 * page's or the viewer's frame at 1, and the rectangle between them in between.
 *
 * A rectangle rather than a scale factor because the two ends are not the same
 * shape. A grid card can be square, a sequel card wide, the page's hero 2:3 and the
 * viewer's frame whatever the artwork is; a scale is a statement about the *size*
 * of a shape that is not changing, so any flight between two different shapes has
 * to either stretch the picture (one factor per axis) or land on a rectangle the
 * reader's finger was not on (one shared factor). The copy is measured at this
 * rectangle instead and cropped into it, which is exact at both ends and never
 * stretched in between.
 */
internal fun coverTravelRect(origin: Rect, target: Rect, progress: Float): Rect {
    val p = progress.coerceIn(0f, 1f)
    return Rect(
        left = lerp(origin.left, target.left, p),
        top = lerp(origin.top, target.top, p),
        right = lerp(origin.right, target.right, p),
        bottom = lerp(origin.bottom, target.bottom, p)
    )
}

/**
 * Lays its content out at [coverTravelRect] and places it there, every frame.
 *
 * The progress is read inside the layout block, so the copy is not recomposed (and
 * does not ask Coil for its painter again) for a whole spring - the same reason the
 * old layer transform read it in the draw block.
 *
 * This is the one place the app animates a layout value on purpose, and it has to: a
 * layer scales the picture it was already given, so it cannot re-crop it, and
 * `ContentScale.Crop` only re-cuts the artwork for the shape it is being drawn into
 * when the box is *measured* at that shape. A layer scaling one fixed aspect carries
 * the crop it was composed with to both ends of the flight, which is what made a close
 * swap the page's crop for the card's in one frame. The cost is one relayout of one
 * image per frame, and the page is not in that subtree.
 *
 * The node still measures to the constraints it was given, and only the child moves
 * inside it. That is not cosmetic: a copy that reported the *travelling* size would be
 * placed by its parent a second time, so a parent that centres its children - the
 * viewer's root Box does - would pull the flight toward the middle of the screen on
 * the frames where the copy is small, instead of landing it on the card.
 */
internal fun Modifier.coverTravelBounds(
    origin: Rect,
    target: Rect,
    progress: () -> Float
): Modifier = layout { measurable, constraints ->
    val rect = coverTravelRect(origin, target, progress())
    val width = rect.width.roundToInt().coerceAtLeast(1)
    val height = rect.height.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    val nodeWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else width
    val nodeHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else height
    layout(nodeWidth, nodeHeight) {
        placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
    }
}
