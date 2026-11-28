package com.anisequel.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anisequel.ui.components.cards.unseamedCover

/**
 * The artwork's journey between the poster and the pager frame.
 *
 * ## Why this sits at the root of the dialog
 *
 * The pager clips its pages, so a picture scaled beyond its own page would be
 * cut off by the pager's bounds - the travelling copy has to be a sibling of the
 * pager, laid out against the window. It is drawn at the *target* rectangle and
 * measured at the travelling one, so the copy is the reader's finger's rectangle on
 * the first frame and the pager's frame on the last, which is why the hand-off to
 * the pager is invisible.
 *
 * Split from `FullScreenCoverViewer` because it is one geometry problem with one
 * input: two rectangles and a fraction. The viewer decides *when* to run it; this
 * file only knows how to draw a frame of it.
 *
 * ## Why the box is measured, not scaled
 *
 * It used to be laid out at the target size and scaled there by one factor per
 * axis. Two factors land exactly on the poster's rectangle but stretch the picture
 * for the whole flight whenever the frame and the poster are different shapes - a
 * wide banner, a square btttr render - and one shared factor does not stretch it but
 * lands on a letterboxed guess instead of the poster the reader pressed. Both were
 * visible on the way out, where the last frames sit over the poster itself.
 * [coverTravelBounds] measures the copy at the rectangle instead, so
 * `ContentScale.Crop` re-cuts the artwork for whatever shape it is being drawn
 * into: exact at both ends, never stretched, and the crop morphs with the box.
 *
 * ## Square, like the frame it lands on
 *
 * It used to clip to `shapes.large` while the pager's copy was a rounded card, so
 * the corners matched. The pager's artwork is now square and full bleed (see
 * [CoverArtwork]), and a rounded flying copy would visibly round its corners off at
 * the exact frame the hand-off happens - the one frame where the illusion has to be
 * perfect. No clip at all: both are rectangles of the same size at the same place.
 *
 * [progress] is a lambda so the value is read inside the layout block. Passing the
 * `Float` would recompose this composable - and ask Coil for a painter again -
 * on every frame of a full-screen spring.
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
    Box(
        modifier = Modifier
            .coverTravelBounds(origin = origin, target = target, progress = progress)
            .background(coverBg)
            .testTag("cover_reveal_artwork")
    ) {
        val imageUrl = url.takeIf { it.isNotBlank() }
        if (imageUrl != null) {
            val context = LocalContext.current
            AsyncImage(
                model = remember(imageUrl, context) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        // The overlay is the transition itself; crossfading here would
                        // fight the reveal spring and can show a brief double image.
                        .crossfade(false)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .unseamedCover()
            )
        }
    }
}
