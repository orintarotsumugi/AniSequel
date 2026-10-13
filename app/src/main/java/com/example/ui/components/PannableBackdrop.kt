package com.example.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * A hero image that fills its box, and can be dragged sideways when it is too big
 * to see all at once.
 *
 * ## Why this exists
 *
 * Both hero images in the app - a profile banner and a media backdrop - are wide
 * artwork, and both were drawn with `ContentScale.Crop` into a fixed-height box.
 * Cropping a wide image to a short box throws away its left and right edges
 * permanently: the composition is authored for the whole frame, so whoever put a
 * face at the left third of the image was never going to be seen on a phone.
 *
 * Rather than crop, this scales to cover and lets the reader drag to the rest of it.
 * It is the same gesture as an image viewer and it costs one offset.
 *
 * ## Why the offset lives in a `graphicsLayer`
 *
 * Read in the draw phase, so a drag costs a repaint rather than a recomposition -
 * the rule `AGENTS.md` section 3 sets for transforms. It is `rememberSaveable`, so
 * rotating the device does not throw the reader back to the left edge of a picture
 * they had panned halfway across.
 */
@Composable
fun PannableBackdrop(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var imageSize by remember { mutableStateOf(Size.Zero) }
    var translation by rememberSaveable { mutableFloatStateOf(0f) }

    // Recomputed whenever the image or the box changes, and again whenever the
    // reader drags: `pannedBackdropTranslation` clamps, so re-running it on the new
    // offset cannot push the picture past either edge.
    val clamped = pannedBackdropTranslation(
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        imageWidth = imageSize.width,
        imageHeight = imageSize.height,
        current = translation
    )
    translation = clamped

    val draggableState = rememberDraggableState { delta ->
        translation = (translation + delta).coerceIn(
            minimumValue = -maxPan(containerWidth, containerHeight, imageSize),
            maximumValue = 0f
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                containerWidth = it.width.toFloat()
                containerHeight = it.height.toFloat()
            }
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                onSuccess = { state ->
                    // The intrinsic size is in the source image's own pixels, and it
                    // is the only measurement available before the image lands -
                    // which is why the pan only becomes possible on success rather
                    // than at layout time.
                    imageSize = state.painter.intrinsicSize
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = translation
                    }
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        // Only intercept the gesture when there is somewhere to pan
                        // to. Without this the reader would have to fight the
                        // vertical scroll to get past a poster that fit anyway.
                        enabled = maxPan(containerWidth, containerHeight, imageSize) > 0f
                    ),
                contentScale = contentScale,
                colorFilter = colorFilter,
                placeholder = null,
                error = null,
                fallback = null
            )
        }

        overlay()
    }
}

/** How far the picture can be dragged left before its right edge shows, in px. */
private fun maxPan(containerWidth: Float, containerHeight: Float, imageSize: Size): Float {
    if (containerWidth <= 0f || containerHeight <= 0f) return 0f
    if (imageSize.width <= 0f || imageSize.height <= 0f) return 0f
    // Cover the height, then see how much wider that leaves the picture than the box.
    val scale = containerHeight / imageSize.height
    val displayedWidth = imageSize.width * scale
    return (displayedWidth - containerWidth).coerceAtLeast(0f)
}

/**
 * The horizontal offset that keeps the picture covering its box, clamped to the
 * range it can actually be dragged through.
 *
 * Pure so the arithmetic can be pinned without a device: the failure this guards
 * is a picture that slides off its own edge and leaves a gap where the background
 * should be, which is the one thing a "fill the box" image must never do.
 *
 * An image narrower than the box, or one not measured yet, reads as zero - there
 * is nothing to reveal, so there is nothing to drag.
 */
internal fun pannedBackdropTranslation(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    current: Float
): Float {
    val pan = maxPan(containerWidth, containerHeight, Size(imageWidth, imageHeight))
    if (pan <= 0f) return 0f
    return current.coerceIn(-pan, 0f)
}