package com.anisequel.ui.components

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import kotlin.math.max

/**
 * A hero image that covers its box, can be pinched, and can be dragged once there
 * is somewhere to drag to.
 *
 * ## Why this computes the scale itself
 *
 * It used to hand `ContentScale.FillWidth` to Coil. That scales to the *width*, so
 * a 1920x1080 banner in a 360x280dp hero draws 360x202 - and the missing 78dp is
 * not padding, it is the hero showing through as bare background under a dark
 * scrim. Nearly a third of the top of every media sheet was an empty rectangle,
 * which reads exactly as "the top of this screen has no image on it".
 *
 * `ContentScale.Crop` covers properly but cannot be panned: it is a single static
 * fit, so the left and right thirds of the artwork were gone for good.
 *
 * So the fit is computed here - `max(w/imgW, h/imgH)`, the cover scale - and applied
 * as a transform. That covers with no gaps *and* leaves the overflow as a known
 * distance the picture can be dragged across.
 *
 * ## Why the transform is pure and separate
 *
 * [backdropTransform] has no Compose in it, so the arithmetic that decides where
 * the picture sits - and, crucially, that it can never leave a gap - is pinned by
 * unit tests. The failures it prevents are all silent: an image that slides off
 * its own edge, a zoom that outruns the pan bounds, a reset that strands the
 * picture off-centre.
 */
@Composable
fun PannableBackdrop(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var imageSize by remember { mutableStateOf(Size.Zero) }

    // Not `rememberSaveable`: zoom and pan are meaningless against a box that has
    // been re-measured since, and restoring a stale offset would strand the picture
    // off-centre on the first frame after a rotation.
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Re-clamped every recomposition, and *written* rather than assigned: the
    // previous version did `translation = clamped` during composition, which is a
    // state write in the composition phase. Here the clamp only ever runs when a
    // gesture or a measurement actually changes something.
    val transform = backdropTransform(
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        imageWidth = imageSize.width,
        imageHeight = imageSize.height,
        zoom = zoom,
        offsetX = offsetX,
        offsetY = offsetY
    )

    // `transformable` rather than a `pointerInput` with
    // `detectTransformGestures`, for one reason: the gesture detector consumes
    // single-pointer drags unconditionally, so a finger dragged on the hero panned
    // the picture instead of scrolling the sheet it sits in. Every media detail is
    // a `ModalBottomSheet` over a scrolling column, and the hero is the first thing
    // under the finger.
    //
    // `canPan` is what settles it: the gesture declines to take a pan when there is
    // nowhere to pan to, so at zoom-out the sheet scrolls normally, and only a
    // picture that is genuinely off-frame takes the drag. A pinch is always
    // consumed, because two fingers on a hero mean zoom and nothing else.
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextZoom = (zoom * zoomChange).coerceIn(BACKDROP_MIN_ZOOM, BACKDROP_MAX_ZOOM)
        zoom = nextZoom

        // Applied raw, then clamped against the bounds the *new* zoom implies.
        // Clamping first would use the old scale's bounds and strand the picture
        // when zooming back out to fit.
        val next = backdropTransform(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            imageWidth = imageSize.width,
            imageHeight = imageSize.height,
            zoom = nextZoom,
            offsetX = offsetX + panChange.x,
            offsetY = offsetY + panChange.y
        )
        offsetX = next.offsetX
        offsetY = next.offsetY
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                val width = it.width.toFloat()
                val height = it.height.toFloat()
                // A resize invalidates the old pan, so centre it rather than
                // keeping an offset measured against a box that no longer exists.
                if (width != containerWidth || height != containerHeight) {
                    offsetX = 0f
                    offsetY = 0f
                    zoom = 1f
                }
                containerWidth = width
                containerHeight = height
            }
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                onSuccess = { state ->
                    // The source image's own pixels - the only measurement available
                    // before the image lands, and the reason the fit can only be
                    // computed once it has.
                    imageSize = state.painter.intrinsicSize
                },
                modifier = Modifier
                    .fillMaxSize()
                    // Centre origin, so a scale grows the picture about its middle -
                    // the behaviour a pinch expects - and the overflow is symmetric,
                    // which is what makes the pan bounds below simple.
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offsetX
                        translationY = transform.offsetY
                    }
                    .transformable(
                        state = transformState,
                        canPan = { hasOverflow(containerWidth, containerHeight, imageSize, zoom) },
                        lockRotationOnZoomPan = true
                    ),
                // `FitBounds` because the transform above owns the fit. Crop here
                // would fight the scale and the two would disagree about what is on
                // screen the moment the picture was panned or zoomed.
                contentScale = ContentScale.FillBounds,
                colorFilter = colorFilter
            )
        }

        overlay()
    }
}

/** Zoomed-out is the cover fit; anything less would leave a gap. */
private const val BACKDROP_MIN_ZOOM = 1f

/** Far enough in to read a face, near enough that nothing is a memory test. */
private const val BACKDROP_MAX_ZOOM = 4f

/** Where the picture sits once the container, the image and the gesture are known. */
internal data class BackdropTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

/**
 * Whether the picture has anywhere to be dragged to right now.
 *
 * The gate on the pan gesture. False at zoom-out on an axis the cover fit already
 * fills, and the sheet's own vertical scroll takes the drag instead - which is what
 * a hero in a scrolling sheet has to do, or the sheet cannot be scrolled by
 * dragging the one thing at the top of it.
 */
internal fun hasOverflow(
    containerWidth: Float,
    containerHeight: Float,
    imageSize: Size,
    zoom: Float
): Boolean {
    if (containerWidth <= 0f || containerHeight <= 0f) return false
    if (imageSize.width <= 0f || imageSize.height <= 0f) return false
    val scale = backdropCoverScale(containerWidth, containerHeight, imageSize.width, imageSize.height) *
        zoom.coerceIn(BACKDROP_MIN_ZOOM, BACKDROP_MAX_ZOOM)
    return imageSize.width * scale > containerWidth + 0.5f ||
        imageSize.height * scale > containerHeight + 0.5f
}

/**
 * The scale that makes the picture cover its box on both axes.
 *
 * The larger of the two ratios, which is what "cover" means: whichever axis runs
 * out first decides the scale, and the other is then over-covered - which is
 * precisely the slack the pan consumes.
 */
internal fun backdropCoverScale(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float
): Float {
    if (containerWidth <= 0f || containerHeight <= 0f) return 1f
    if (imageWidth <= 0f || imageHeight <= 0f) return 1f
    return max(containerWidth / imageWidth, containerHeight / imageHeight)
}

/**
 * Where the picture sits: the cover scale times the pinch, and an offset clamped so
 * no edge of the picture can come inside the box.
 *
 * ## Why clamping rather than trusting the gesture
 *
 * Because the offset is cumulative, and a pinch about an arbitrary point moves the
 * picture by an arbitrary amount. Without a bound, dragging hard towards one edge
 * walks the artwork out of frame and leaves bare background where the image
 * should be - the one failure a hero image cannot have, because it looks like the
 * screen simply has no image.
 *
 * ## Why the bounds are symmetric
 *
 * The transform origin is the centre, so the overflow is equal on both sides and
 * the reachable range is `[-overflow/2, +overflow/2]`. A non-symmetric bound is what
 * would let the picture slide off on one side and stick out on the other.
 *
 * Unmeasured anything reads as an identity transform, so the first frame - before
 * the image has landed and before the box has been measured - draws nothing
 * scaled or moved rather than dividing by a zero.
 */
internal fun backdropTransform(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float
): BackdropTransform {
    // Nothing measured, nothing to place. Without this the cover scale reads 1 and
    // the *zoom* is still applied on top, so a caller asking about an unmeasured
    // box got `scale = zoom` rather than an identity - a number that reads like a
    // real answer and is not one. It also means a gesture arriving before the
    // image has loaded cannot scale an invisible picture.
    if (containerWidth <= 0f || containerHeight <= 0f) return BackdropTransform(1f, 0f, 0f)
    if (imageWidth <= 0f || imageHeight <= 0f) return BackdropTransform(1f, 0f, 0f)

    val cover = backdropCoverScale(containerWidth, containerHeight, imageWidth, imageHeight)
    val scale = cover * zoom.coerceIn(BACKDROP_MIN_ZOOM, BACKDROP_MAX_ZOOM)

    val overflowX = imageWidth * scale - containerWidth
    val overflowY = imageHeight * scale - containerHeight

    // Zero when the picture does not overflow, which is the zoomed-out case on the
    // axis that was already over-covered. Clamping to zero *centres* the picture
    // on that axis, which is what dropping back to fit should do.
    val limitX = if (overflowX > 0f) overflowX / 2f else 0f
    val limitY = if (overflowY > 0f) overflowY / 2f else 0f

    return BackdropTransform(
        scale = scale,
        offsetX = offsetX.coerceIn(-limitX, limitX),
        offsetY = offsetY.coerceIn(-limitY, limitY)
    )
}