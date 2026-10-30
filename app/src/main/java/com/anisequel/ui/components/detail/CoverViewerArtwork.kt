package com.anisequel.ui.components.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName

/**
 * The artwork and its gestures, split out of [FullScreenCoverViewer].
 *
 * The viewer is the dialog, the pager, the header and the save state. This file
 * is the geometry: the frame that fits the image, the pinch that scales it, and
 * the pan limits measured from that frame. The split is by reason to change -
 * adding a source is a viewer change, retuning a spring or a zoom limit is this
 * file - and it also keeps both under the repo's file ceiling now that LiveChart
 * made the viewer a gallery.
 */

/** A pinch that scales past this counts as zoomed for the background tap. */
private const val ZOOMED_THRESHOLD = 1.05f

/** Past this, a double-tap puts the artwork back instead of opening it further. */
private const val DOUBLE_TAP_RESET_THRESHOLD = 1.2f

private const val DOUBLE_TAP_SCALE = 2.4f
private const val MAX_ZOOM = 4.5f

/** AniList's cover shape, used until the image's own dimensions arrive. */
internal const val DEFAULT_COVER_ASPECT = 2f / 3f

/**
 * The viewer's zoom and pan.
 *
 * Held outside the composable so the three gestures that move it - pinch,
 * double-tap and the background tap - are methods with names rather than more
 * branches inside the viewer's body. [frameSize] is measured by the artwork,
 * because the pan limits are half the scaled overflow on each axis; the
 * constants this replaced (300f by 400f) were wrong on both a narrow phone and
 * a tablet.
 *
 * `internal` rather than file-private because the details card reads
 * [isZoomed] to word its gesture hint, and recomposing that hint on a pinch is
 * the card's job rather than the whole overlay's.
 */
internal class CoverZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set
    var frameSize by mutableStateOf(IntSize.Zero)

    val isZoomed: Boolean get() = scale > ZOOMED_THRESHOLD

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    /** The double-tap: open the artwork, or put a zoomed one back. */
    fun toggleDoubleTap() {
        if (scale > DOUBLE_TAP_RESET_THRESHOLD) reset() else scale = DOUBLE_TAP_SCALE
    }

    /**
     * One pinch frame.
     *
     * Half the overflow on each side: the image scales around its centre, so
     * that is exactly how far it can move before an edge shows.
     */
    fun applyGesture(pan: Offset, zoomChange: Float) {
        val newScale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        scale = newScale
        if (newScale > 1f) {
            val maxX = frameSize.width * (newScale - 1f) / 2f
            val maxY = frameSize.height * (newScale - 1f) / 2f
            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }
}

/** The artwork's own ratio, falling back to what the source is expected to be. */
internal fun coverAspectOf(intrinsic: Size, source: CoverSource?): Float =
    if (intrinsic.width > 0f && intrinsic.height > 0f) {
        intrinsic.width / intrinsic.height
    } else {
        source?.fallbackAspect ?: DEFAULT_COVER_ASPECT
    }

/**
 * The artwork between the header and the details card.
 *
 * [BoxWithConstraints] gives it the space the header and footer leave, and the
 * cover takes the largest box that fits it at its own ratio - the 340x440 frame
 * this replaced letterboxed a 2:3 cover on one axis, so the "full screen" viewer
 * drew a poster smaller than the hero it was opened from.
 */
@Composable
internal fun CoverArtwork(
    media: MediaNode,
    painter: Painter,
    coverAspect: Float,
    coverBg: Color,
    zoom: CoverZoomState,
    /** False for the copy that is only on screen to fade out. See the call site. */
    reportsFrame: Boolean = true,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val frameWidth = minOf(maxWidth, maxHeight * coverAspect)
        val frameHeight = frameWidth / coverAspect
        Box(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
                .shadow(
                    elevation = 24.dp,
                    shape = MaterialTheme.shapes.large,
                    ambientColor = coverBg,
                    spotColor = Color.Black
                )
                .clip(MaterialTheme.shapes.large)
                .background(coverBg)
                .onSizeChanged { if (reportsFrame) zoom.frameSize = it }
                .pointerInput(zoom) {
                    detectTapGestures(onDoubleTap = { zoom.toggleDoubleTap() })
                }
                // Only a pinch, or a drag while already zoomed, claims the
                // pointer. The old `detectTransformGestures` consumed every
                // single-finger drag as a pan too, so the pager underneath could
                // never see a swipe: a gallery whose images could not be swiped.
                // Letting the pager own one-finger movement at 1x is also what
                // keeps the zoom gesture and the source pager from fighting.
                .pointerInput(zoom) {
                    awaitEachGesture {
                        var event: PointerEvent
                        do {
                            event = awaitPointerEvent()
                            val multiTouch = event.changes.count { it.pressed } > 1
                            if (multiTouch || zoom.isZoomed) {
                                val zoomChange = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoomChange != 1f || pan != Offset.Zero) {
                                    zoom.applyGesture(pan, zoomChange)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offsetX
                    translationY = zoom.offsetY
                }
        ) {
            Image(
                painter = painter,
                contentDescription = media.displayName,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
