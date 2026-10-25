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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import kotlin.math.max
import kotlin.math.min

/**
 * How the picture is fitted into its box before any pinch is applied.
 *
 * [COVER] scales by the *larger* axis ratio: the box is filled edge to edge and
 * whichever axis has slack is cropped off. [FIT] scales by the *smaller* ratio:
 * the whole picture is inside the box, and the axis with slack letterboxes
 * against the caller's background.
 *
 * The detail sheet's hero used to be COVER unconditionally, which reads as
 * "zoomed in" on a wide AniList banner: the left and right thirds of the artwork
 * are cropped by definition, and the reader had to pinch out to see the banner
 * whole. FIT is what "show the full image" means, and COVER stays the default
 * because a profile banner letterboxed against the surface would look unfinished.
 *
 * ## Nothing passes FIT yet, and that is deliberate - say so before you hunt
 *
 * The hero this was written for stopped using [PannableBackdrop] in the same
 * release: the title page redraws its own backdrop so the parallax can be driven
 * by the page's scroll in the draw phase, and that drawing has to keep covering
 * its box as it moves - a fitted hero would expose its letterbox the moment the
 * page scrolls. The whole picture is still one tap away in the full-screen cover
 * viewer. So FIT is reachable only through its parameters and its tests today;
 * it is not dead weight to delete, it is the half of this component a caller who
 * *does* want "show the whole image" (rather than fill the box) reaches for, and
 * [BackdropTransformTest] is what currently holds the arithmetic honest.
 *
 * Public because [PannableBackdrop] is: a public function's parameter type cannot
 * be `internal` (`'public' function exposes its 'internal' parameter type`), and
 * widening the parameter's caller set is what this component is for.
 */
enum class BackdropFit {
    COVER,
    FIT
}

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
    fit: BackdropFit = BackdropFit.COVER,
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
        offsetY = offsetY,
        fit = fit
    )

    // `transformable` rather than a `pointerInput` with
    // `detectTransformGestures`, for one reason: the gesture detector consumes
    // single-pointer drags unconditionally, so a finger dragged on the hero panned
    // the picture instead of scrolling the sheet it sits in. Every media detail is
    // a `ModalBottomSheet` over a scrolling column, and the hero is the first thing
    // under the finger.
    //
    // `canPan` is what settles it, and it is a blunt instrument - one boolean for
    // the whole gesture, because [Modifier.transformable] cannot ask which way the
    // finger is going before it has already consumed the drag. So it is shut at the
    // cover fit, which is the state the sheet opens in, and the sheet's own
    // vertical scroll takes the drag. It opens once the picture is zoomed, where
    // panning is what the user asked for and both axes have slack. A pinch is
    // always consumed, because two fingers on a hero mean zoom and nothing else.
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
            offsetY = offsetY + panChange.y,
            fit = fit
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
                        // Opens once the picture is zoomed, not once it merely
                        // overflows. See [hasOverflow]: at the cover fit the hero
                        // declines single-pointer drags outright, because a gate
                        // that asked "is there slack?" was always open, and an
                        // open gate eats the sheet's own vertical scroll while
                        // the transform clamps the pan back to zero.
                        canPan = { hasOverflow(containerWidth, containerHeight, imageSize, zoom, fit) },
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
 * Whether the picture may take a single-pointer drag at all right now.
 *
 * ## It used to ask the wrong question, and the answer was always yes
 *
 * This was "is there anywhere to drag to", tested as
 * `width overflows || height overflows`. That is a near-tautology, because
 * [backdropCoverScale] is a `max` of the two axis ratios and the cover fit
 * over-covers the other axis *by definition* - its own doc comment says so. So
 * for essentially any image at least one axis overflows, `hasOverflow` returned
 * true, and the `canPan` gate never once declined.
 *
 * That mattered because `Modifier.transformable` consumes a single-pointer drag in
 * **both** axes when `canPan` is true, and its `canPan` is one boolean for the
 * whole gesture rather than a per-axis decision. So a hero at the top of the
 * detail sheet ate every vertical drag aimed at it - including the ones that
 * should have scrolled the sheet - and then, because the vertical pan range at
 * the fit is zero, [backdropTransform] clamped the offset straight back to 0. The
 * picture did not move and the sheet did not scroll: the gesture was consumed and
 * thrown away. On release the spring settled it, which is what read as the hero
 * bouncing twice.
 *
 * ## Why the answer is now "only once zoomed"
 *
 * `canPan` cannot be made axis-aware - the API has no way to ask which way the
 * finger is going before it has already consumed the drag. So the choice is
 * between a gate that is always open and one that is shut until the user has
 * asked for the image to be manipulated. It is shut at the fit, which is the
 * state the sheet opens in and the state a finger meets first.
 *
 * The cost is real and deliberate: at zoom 1 the banner can no longer be panned
 * sideways to reach the left and right thirds the cover fit crops off. Pinching
 * still zooms (a pinch is consumed separately, and does not consult this), and
 * panning returns the moment the picture is zoomed, where both axes genuinely
 * have slack. Losing a cosmetic pan is a better trade than a sheet that cannot be
 * scrolled from its own first row.
 */
internal fun hasOverflow(
    containerWidth: Float,
    containerHeight: Float,
    imageSize: Size,
    zoom: Float,
    fit: BackdropFit = BackdropFit.COVER
): Boolean {
    if (containerWidth <= 0f || containerHeight <= 0f) return false
    if (imageSize.width <= 0f || imageSize.height <= 0f) return false
    val clampedZoom = zoom.coerceIn(BACKDROP_MIN_ZOOM, BACKDROP_MAX_ZOOM)
    // At the fit the answer is no regardless of the geometry. See the KDoc: the
    // per-axis question below cannot be expressed through `canPan`, and asking it
    // anyway is what consumed the sheet's own scroll.
    if (clampedZoom <= BACKDROP_MIN_ZOOM) return false
    val scale = backdropBaseScale(fit, containerWidth, containerHeight, imageSize.width, imageSize.height) *
        clampedZoom
    return imageSize.width * scale > containerWidth + 0.5f ||
        imageSize.height * scale > containerHeight + 0.5f
}

/** Which axis decides the base scale: the larger ratio [COVER], the smaller [FIT]. */
private fun backdropBaseScale(
    fit: BackdropFit,
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float
): Float = when (fit) {
    BackdropFit.COVER -> backdropCoverScale(containerWidth, containerHeight, imageWidth, imageHeight)
    BackdropFit.FIT -> backdropFitScale(containerWidth, containerHeight, imageWidth, imageHeight)
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
 * The scale that makes the picture *fit inside* its box: the whole image, with
 * the slack on the longer axis left as letterbox.
 *
 * The smaller of the two ratios - [backdropCoverScale]'s twin, and the reason the
 * two live side by side: a wide banner at the cover fit is zoomed by definition,
 * because covering a 280dp-tall box with a 16:9 image means cropping its sides.
 */
internal fun backdropFitScale(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float
): Float {
    if (containerWidth <= 0f || containerHeight <= 0f) return 1f
    if (imageWidth <= 0f || imageHeight <= 0f) return 1f
    return min(containerWidth / imageWidth, containerHeight / imageHeight)
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
    offsetY: Float,
    fit: BackdropFit = BackdropFit.COVER
): BackdropTransform {
    // Nothing measured, nothing to place. Without this the cover scale reads 1 and
    // the *zoom* is still applied on top, so a caller asking about an unmeasured
    // box got `scale = zoom` rather than an identity - a number that reads like a
    // real answer and is not one. It also means a gesture arriving before the
    // image has loaded cannot scale an invisible picture.
    if (containerWidth <= 0f || containerHeight <= 0f) return BackdropTransform(1f, 0f, 0f)
    if (imageWidth <= 0f || imageHeight <= 0f) return BackdropTransform(1f, 0f, 0f)

    val base = backdropBaseScale(fit, containerWidth, containerHeight, imageWidth, imageHeight)
    val scale = base * zoom.coerceIn(BACKDROP_MIN_ZOOM, BACKDROP_MAX_ZOOM)

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