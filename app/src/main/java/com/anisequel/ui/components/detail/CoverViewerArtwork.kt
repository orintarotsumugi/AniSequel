package com.anisequel.ui.components.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.ui.components.cards.unseamedCover
import com.anisequel.ui.components.expressive.ExpressiveMotion

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
 * How far the full-bleed copy of the artwork is blurred.
 *
 * Enough that the picture reads as colour and shape rather than as a second copy of
 * the poster competing with the sharp one, and low enough that it still costs one
 * offscreen pass rather than a blur chain per frame. The Info page's backdrop uses
 * the same order of magnitude for the same reason.
 */
private val BACKDROP_BLUR = 26.dp

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
 * The picture behind everything: the image on screen, cropped to the display.
 *
 * This is what makes the viewer full screen rather than a poster on a dark page.
 * A 2:3 cover that fits a 19.5:9 phone leaves a wide band on each side; drawing the
 * *same* image at full bleed behind it means those bands are the title's own colour
 * and art instead of empty space, and the sharp copy in the middle reads as the
 * subject of a lit room rather than as an object on a table.
 *
 * Blurred, because an unblurred copy at 2x scale is a second poster competing with
 * the one the reader is looking at - the eye cannot tell which is the image and
 * which is the decoration.
 *
 * [presence] is a lambda so the fade is read in the draw phase: this covers the
 * whole window, and recomposing it per fade frame would re-ask Coil for a painter
 * on every one of them. [pageOffset] is read the same way and for the same reason:
 * it is the pager's fractional offset, and it moves the backdrop a few pixels
 * against the swipe so the blur reads as depth behind the sharp page rather than
 * as a second copy pasted under it.
 *
 * The poster's extracted colour is not only the fallback fill: a soft radial wash
 * of it sits over the blur and under the scrim, which is what gives the viewer its
 * own colour on a pale banner and keeps a dark poster from going flat grey-black.
 */
@Composable
internal fun CoverViewerBackdrop(
    url: String?,
    coverBg: Color,
    presence: () -> Float,
    pageOffset: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { alpha = presence() }
            .background(coverBg)
            .testTag("cover_viewer_backdrop")
    ) {
        if (url != null) {
            AnimatedContent(
                targetState = url,
                transitionSpec = {
                    fadeIn(ExpressiveMotion.FastEffects)
                        .togetherWith(fadeOut(ExpressiveMotion.FastEffects)) using null
                },
                contentKey = { it },
                label = "cover_viewer_backdrop_art",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // A little larger than the window so the parallax never
                        // exposes an edge, and a small fraction of the pager's
                        // offset in the opposite direction. Not the pager's full
                        // travel: the backdrop is decoration, and matching the
                        // page exactly made it read as a second pager behind the
                        // first.
                        val drift = pageOffset() * 30.dp.toPx()
                        scaleX = 1.08f
                        scaleY = 1.08f
                        translationX = -drift
                    }
            ) { activeUrl ->
                AsyncImage(
                    model = activeUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(BACKDROP_BLUR)
                        .unseamedCover()
                )
            }

            // The title's own colour, as light in the room. Over the blur and
            // under the scrim, so it tints the stage without fighting the picture:
            // a restrained 0.32 alpha keeps the effect a glow rather than a wash.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                coverBg.copy(alpha = 0.32f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // The legibility layer. Black rather than the scheme's surface: this is
            // the inside of a lightbox, and a `surface` wash went pale-grey in the
            // light theme where the chrome's `onSurface` text needed a dark ground.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.35f to Color.Black.copy(alpha = 0.30f),
                            1f to Color.Black.copy(alpha = 0.70f)
                        )
                    )
            )
        }
    }
}

/**
 * The artwork between the header and the details sheet, at its own ratio.
 *
 * [BoxWithConstraints] gives it the space the chrome leaves, and the cover takes
 * the largest box that fits it at its own ratio - the 340x440 frame this replaced
 * letterboxed a 2:3 cover on one axis, so the "full screen" viewer drew a poster
 * smaller than the hero it was opened from.
 *
 * No rounded corners and no shadow: this is now the picture on a screen rather than
 * a card floating over one, and the travelling copy in `CoverViewerReveal` has to
 * land on exactly this shape, so both are square.
 */
@Composable
internal fun CoverArtwork(
    media: MediaNode,
    painter: Painter,
    coverAspect: Float,
    coverBg: Color,
    zoom: CoverZoomState,
    /** A single tap: the viewer resets a zoom or toggles its chrome. */
    onTap: () -> Unit,
    /** False for the copy that is only on screen to fade out. See the call site. */
    reportsFrame: Boolean = true,
    /**
     * The measured frame's window bounds, for the entrance reveal.
     *
     * Only the copy that reports its size calls this, for the same reason: the
     * fading-out copy is not where the artwork will settle, and an entrance that
     * aimed at it would land in the wrong place for one frame.
     */
    onFrameBounds: ((Rect) -> Unit)? = null,
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
                .background(coverBg)
                .onSizeChanged { if (reportsFrame) zoom.frameSize = it }
                .onGloballyPositioned {
                    if (reportsFrame) onFrameBounds?.invoke(it.boundsInWindow())
                }
                // Single tap and double tap on one detector: the artwork covers most
                // of the screen now, and "tap anywhere to close" (which the sheet
                // still promises) cannot be left to the dialog's background when the
                // picture is in the way. `detectTapGestures` holds a single tap for
                // the double-tap window, so opening the zoom never also closes the
                // viewer.
                .pointerInput(zoom, onTap) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { zoom.toggleDoubleTap() }
                    )
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
                // The frame above is sized to the image's *own* ratio, so Crop
                // draws the same pixels Fit drew - except never one short. Fit
                // rounds its destination, and the missing row landed at the top
                // edge as a line of `coverBg`, the cover's own colour, which
                // grew with every pinch. See [unseamedCover].
                modifier = Modifier
                    .matchParentSize()
                    .unseamedCover(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
