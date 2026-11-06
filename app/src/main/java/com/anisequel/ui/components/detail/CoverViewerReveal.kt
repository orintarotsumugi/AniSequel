package com.anisequel.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.anisequel.ui.components.cards.unseamedCover
import kotlin.math.roundToInt

/**
 * The artwork's journey between the poster and the pager frame.
 *
 * ## Why this sits at the root of the dialog
 *
 * The pager clips its pages, so a picture scaled beyond its own page would be
 * cut off by the pager's bounds - the travelling copy has to be a sibling of the
 * pager, laid out against the window. It is drawn at the *target* size and
 * position, then scaled and translated back toward [origin] by the animation:
 * one Box whose transform starts where the reader's finger was and ends at 1:1,
 * which is why the hand-off to the pager is invisible.
 *
 * Split from `FullScreenCoverViewer` because it is one geometry problem with one
 * input: two rectangles and a fraction. The viewer decides *when* to run it; this
 * file only knows how to draw a frame of it.
 *
 * [progress] is a lambda so the value is read inside `graphicsLayer`. Passing the
 * `Float` would recompose this composable - and ask Coil for a painter again - on
 * every frame of a full-screen spring.
 */
@Composable
internal fun CoverRevealOverlay(
    url: String,
    coverBg: Color,
    origin: Rect,
    target: Rect,
    progress: () -> Float
) {
    if (target.width <= 0f || target.height <= 0f) return
    val density = LocalDensity.current
    val width = with(density) { target.width.toDp() }
    val height = with(density) { target.height.toDp() }
    // A poster is 2:3 and so is the frame, so the two scales are nearly equal;
    // keeping both axes means a rare wide image still starts from the rectangle
    // the reader tapped rather than from a letterboxed guess.
    val startScaleX = origin.width / target.width
    val startScaleY = origin.height / target.height

    Box(
        modifier = Modifier
            .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
            .size(width, height)
            .graphicsLayer {
                // Top-left origin: the rectangle's *corner* is what travels, not
                // its centre. Scaling around the centre would swing the poster
                // sideways on the way in.
                transformOrigin = TransformOrigin(0f, 0f)
                val p = progress().coerceIn(0f, 1f)
                scaleX = lerp(startScaleX, 1f, p)
                scaleY = lerp(startScaleY, 1f, p)
                translationX = lerp(origin.left - target.left, 0f, p)
                translationY = lerp(origin.top - target.top, 0f, p)
            }
            .clip(MaterialTheme.shapes.large)
            .background(coverBg)
            .testTag("cover_reveal_artwork")
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .unseamedCover()
        )
    }
}
