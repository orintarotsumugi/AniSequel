package com.anisequel.ui.components.detail

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anisequel.ui.components.cards.unseamedCover
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The poster rectangle a press started from, remembered across the window gap.
 *
 * The title page is a `Dialog`, and a dialog is a separate window: the list's
 * poster and the page's poster are laid out by two different compositions, so
 * there is no shared bounds, no `SharedTransitionLayout`, and no target the list
 * can animate toward. What both windows do share is the screen, and
 * [boundsInWindow] is measured against it, so the only thing that has to cross
 * the gap is two rectangles.
 *
 * The list writes its poster's rectangle on the press that opens the page, and
 * the page takes it back on its first composition. A press, not the click, for
 * the same reason [SequelPoster] records bounds at all: the rect is where the
 * finger was, and by the time the click fires the sheet is already composing.
 *
 * Entries are short-lived and capped. A deep link or a notification opens the
 * page with no press behind it, and a stale rectangle from a poster the reader
 * tapped a minute ago is worse than no flight at all - it would fly in from
 * wherever that poster used to be.
 */
internal object DetailCoverFlight {
    private const val FRESH_MILLIS = 2_000L
    // Narrower than [FRESH_MILLIS]: this window has to contain the press that
    // opened the page and nothing else, because it is used when no URL matches.
    private const val RECENT_MILLIS = 750L
    private const val CAPACITY = 24

    private class Origin(val rect: Rect, val atMillis: Long)

    private val origins = LinkedHashMap<String, Origin>()

    fun record(url: String?, rect: Rect) {
        if (url.isNullOrBlank() || rect.width <= 0f || rect.height <= 0f) return
        val now = SystemClock.uptimeMillis()
        synchronized(origins) {
            origins.entries.removeAll { now - it.value.atMillis > FRESH_MILLIS }
            origins[url] = Origin(rect, now)
            while (origins.size > CAPACITY) {
                val oldest = origins.entries.minByOrNull { it.value.atMillis } ?: break
                origins.remove(oldest.key)
            }
        }
    }

    /**
     * The first fresh rectangle among [urls], consuming every key tried.
     *
     * More than one key because a card and the page can disagree on which URL
     * they show: a sequel row draws the AniList cover while the page prefers
     * btttr's rendering of the same poster. Either rectangle is the right place
     * to fly from - the picture's composition is unchanged.
     */
    fun take(urls: List<String?>): Rect? = synchronized(origins) {
        val now = SystemClock.uptimeMillis()
        val keys = urls.filterNotNull()
        val exact = keys.firstNotNullOfOrNull { key ->
            origins[key]?.takeIf { now - it.atMillis <= FRESH_MILLIS }
        }
        keys.forEach { origins.remove(it) }
        if (exact != null) return@synchronized exact.rect

        // A btttr cover is resolved a frame or two after the list draws, so the
        // page can still be holding the AniList URL the card was *not* keyed on
        // when it composes. Both URLs are the same picture; the press in the last
        // few frames is the one that opened this page, and it is the only origin
        // that can be right. Without this, every btttr poster arrived by fading
        // in from nothing instead of flying.
        val recent = origins.entries
            .filter { now - it.value.atMillis <= RECENT_MILLIS }
            .maxByOrNull { it.value.atMillis }
            ?: return@synchronized null
        origins.remove(recent.key)
        recent.value.rect
    }
}

/**
 * The page's poster place, written by whichever hero is on screen.
 *
 * Plain fields rather than `mutableStateOf` on purpose: [record] is called from
 * `onGloballyPositioned`, which runs on every scroll frame while the hero is on
 * screen, and a state write there would recompose the page for a rectangle it
 * does not draw. The sheet waits for the first frame and then reads it once.
 */
internal class DetailCoverTarget {
    var filled = false
        private set
    var rect: Rect = Rect.Zero
        private set
    var shape: Shape = RectangleShape
        private set
    var tint: Color = Color.Transparent
        private set

    fun record(rect: Rect, shape: Shape, tint: Color) {
        if (rect.width <= 0f || rect.height <= 0f) return
        this.rect = rect
        this.shape = shape
        this.tint = tint
        filled = true
    }
}

/**
 * How visible the page's own poster is while the flying copy is in charge.
 *
 * The page has drawn its poster since its first frame - it is the placeholder
 * the flight lands on, and the thing the entry editor collapses around - so the
 * copy would otherwise arrive on top of an identical picture and the hand-off
 * would show a seam. Hiding it until the copy lands is the whole trick: at rest
 * the two are pixel-identical, so the swap is invisible, and the page's poster
 * (with its shadow and border, which the copy does not carry) is the one left
 * on screen.
 */
internal val LocalDetailCoverReveal = staticCompositionLocalOf<() -> Float> { { 1f } }

/** Null outside the sheet, or when the page has no poster flight to land. */
internal val LocalDetailCoverTarget = staticCompositionLocalOf<DetailCoverTarget?> { null }

private class PressBounds {
    var rect: Rect? = null
}

/**
 * Records this poster's window rectangle when a finger lands on it.
 *
 * The `Initial` pass runs before children and before the scrollable, and this
 * listener consumes nothing, so the card's click, its hold gesture and the drag
 * that starts on it all still see every event. Recording at press rather than
 * at click is what makes the rectangle still be here when the dialog - a window
 * added a frame later - asks for it.
 */
internal fun Modifier.detailCoverPressOrigin(url: String?): Modifier {
    if (url.isNullOrBlank()) return this
    return composed {
        val bounds = remember(url) { PressBounds() }
        onGloballyPositioned { bounds.rect = it.boundsInWindow() }
            .pointerInput(url) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            bounds.rect?.let { DetailCoverFlight.record(url, it) }
                        }
                    }
                }
            }
    }
}

/**
 * The poster in flight, drawn above the page for as long as the flight runs.
 *
 * Laid out at the page's poster rectangle and scaled and translated back toward
 * the list's, the same geometry [CoverRevealOverlay] uses: one Box whose
 * transform starts where the reader's finger was and ends at 1:1. It reads the
 * progress inside the layer block, so a flight is a draw-phase change and the
 * page behind it never recomposes.
 *
 * One scale factor for both axes, unlike [CoverRevealOverlay]: the viewer's
 * bounds and the page's poster are the same crop, but a list card can be square
 * or wide, and two factors would stretch the poster until it landed.
 *
 * The rounded corner is the page's shape, and the clip sits *inside* the scale,
 * so the corners grow with the artwork instead of staying at the page's radius
 * while the picture is still small.
 */
@Composable
internal fun SheetCoverFlight(
    url: String,
    origin: Rect,
    target: DetailCoverTarget,
    progress: () -> Float
) {
    if (!target.filled || target.rect.width <= 0f || target.rect.height <= 0f) return
    val density = LocalDensity.current
    val width = with(density) { target.rect.width.toDp() }
    val height = with(density) { target.rect.height.toDp() }
    val left = target.rect.left
    val top = target.rect.top
    val targetWidth = target.rect.width
    val targetHeight = target.rect.height
    // One scale factor, not one per axis: the card's artwork and the page's poster
    // are the same picture but not always the same crop - a square list thumbnail,
    // a wide sequel card - and scaling each axis to its own rectangle stretches the
    // poster for the whole flight. The smaller factor keeps it undistorted and only
    // costs a margin on the first frames, which the page's scrim is still covering.
    val startScale = min(origin.width / targetWidth, origin.height / targetHeight)
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(width, height)
            .graphicsLayer {
                // Top-left origin: the transform is a scale around the layout's
                // corner, and the translation below puts that corner where the
                // scaled picture has to start.
                transformOrigin = TransformOrigin(0f, 0f)
                val p = progress().coerceIn(0f, 1f)
                val scale = lerp(startScale, 1f, p)
                scaleX = scale
                scaleY = scale
                // Centred on the card's rectangle rather than aligned to its
                // corner: with one scale factor the copy is smaller than the card
                // on one axis, and a top-left anchor would hang it off the corner.
                translationX = lerp(origin.center.x - (targetWidth * startScale / 2f) - left, 0f, p)
                translationY = lerp(origin.center.y - (targetHeight * startScale / 2f) - top, 0f, p)
            }
            .clip(target.shape)
            .background(target.tint)
            .testTag("detail_cover_flight")
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = remember(url, context) {
                    ImageRequest.Builder(context)
                        .data(url)
                        // The flight is the transition; a crossfade here would
                        // arrive with a second, half-faded copy of the poster.
                        .crossfade(false)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().unseamedCover()
            )
        }
    }
}

/**
 * One page's flight: the list's rectangle, the page's poster, and the progress
 * between them.
 *
 * The page needs the same pieces in four places - the dialog's locals, the
 * close spring, the entrance modifier and the overlay - so they live in one
 * object. Passed around separately, one of them ends up wired to a different
 * flight than the others.
 */
internal class DetailCoverFlightState(
    val origin: Rect?,
    private val target: DetailCoverTarget,
    private val progress: Animatable<Float, AnimationVector1D>
) {
    private val onStage = mutableStateOf(false)

    /** No rectangle means nothing to fly, and the page keeps its own entrance. */
    val travels: Boolean get() = origin != null

    /** The page's poster place, or null when this open has no copy to land. */
    val pageTarget: DetailCoverTarget? get() = target.takeIf { travels }

    /** The progress spring, or null when this open has no flight to reverse. */
    val travel: Animatable<Float, AnimationVector1D>? get() = progress.takeIf { travels }

    /**
     * 1 once the copy has landed, 0 while it is still in charge.
     *
     * Read in the draw phase by the hero, so this changes no layout. The page
     * draws its poster from its first frame because that is what the copy lands
     * on; hiding it until then is what makes the hand-off seamless, since both
     * sides show the same picture over the same rectangle.
     */
    val reveal: () -> Float = { if (progress.value >= 1f) 1f else 0f }

    /** Puts the copy on stage before either side starts moving. */
    fun begin() {
        if (origin != null && target.filled) onStage.value = true
    }

    /**
     * The copy, drawn above the page while it travels.
     *
     * It lives in the dialog's root Box rather than in the page, because the page
     * clips its content and would cut the copy off on the first half of the way
     * in.
     */
    @Composable
    fun Overlay(url: String) {
        if (origin != null && onStage.value) {
            SheetCoverFlight(
                url = url,
                origin = origin,
                target = target,
                progress = { progress.value }
            )
        }
    }

    fun land() {
        onStage.value = false
    }
}

/**
 * Remembers this open's flight, and runs the copy in once the page has laid out.
 *
 * The hero reports its rectangle during layout, so the spring waits for it - a
 * bounded number of frames, because a page that never draws a poster (a text
 * page, a cover that fails to decode) must not keep the copy off stage forever.
 */
@Composable
internal fun rememberDetailCoverFlight(urls: List<String?>): DetailCoverFlightState {
    val origin = remember { DetailCoverFlight.take(urls) }
    val target = remember { DetailCoverTarget() }
    // 1 is "landed": a page with no origin starts settled, so every reader below
    // sees the poster on its first frame instead of a hidden one.
    val progress = remember { Animatable(if (origin == null) 1f else 0f) }
    val state = remember(origin) { DetailCoverFlightState(origin, target, progress) }
    LaunchedEffect(state) {
        if (origin == null) return@LaunchedEffect
        var waited = 0
        while (!target.filled && waited < 4) {
            withFrameNanos { }
            waited++
        }
        if (!target.filled) return@LaunchedEffect
        state.begin()
        progress.animateTo(1f, ExpressiveMotion.SettleSpatial)
        state.land()
    }
    return state
}