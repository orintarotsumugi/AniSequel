package com.anisequel.ui.screens.tier

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierDisplay
import com.anisequel.domain.tier.TierItemBox
import com.anisequel.domain.tier.TierLayoutOptions
import com.anisequel.domain.tier.TierListLayout
import com.anisequel.domain.tier.TierRowBox
import kotlinx.coroutines.isActive

/**
 * The board's drag: the state a long press builds up, the gesture that fills it,
 * the auto-scroll that keeps it reachable, and the ghost that follows the finger.
 *
 * Split out of `TierBoard.kt` because that file is composition and geometry while
 * this one is one gesture's whole lifetime. They change for different reasons: a
 * zoom or caption change never touches this file, and a change to how a drop
 * resolves should not have to be read between two `Layout` blocks.
 */

/**
 * Everything one long-press drag knows: which tile is lifted, where the finger
 * started, how far it has travelled, and where the board sat when it began.
 *
 * One object rather than five `remember`s in the board, because the pointer
 * handler, the auto-scroll loop and the ghost all read the same values and a
 * mistyped `remember` key between them is a drag that resolves against a stale
 * origin.
 */
internal class TierDragState {
    var mediaId by mutableStateOf<Int?>(null)
        private set

    var origin by mutableStateOf(Offset.Zero)
        private set

    // Held as the state object rather than read with `by`, because the dragged
    // ghost's translation has to be read inside its `graphicsLayer` block. A
    // `by` here would read it during composition and rebuild the modifier chain
    // on every pointer move, which is a recomposition per frame for a drag.
    val travel = mutableStateOf(Offset.Zero)

    // Where the board sat in the window when the tile was picked up.
    var boardTop by mutableFloatStateOf(0f)
        private set

    fun lift(mediaId: Int, origin: Offset, boardTop: Float) {
        this.mediaId = mediaId
        this.origin = origin
        this.boardTop = boardTop
        travel.value = Offset.Zero
    }

    fun moveBy(amount: Offset) {
        travel.value += amount
    }

    fun reset() {
        mediaId = null
        travel.value = Offset.Zero
    }

    /**
     * Where a release *right now* would land, in the layout's own coordinates.
     *
     * Read at release rather than taken from the last composition: the final
     * pointer move is not required to have triggered a recomposition, and a drop
     * that lands one frame behind the finger is a drop into the wrong row.
     *
     * [scrollShift] is how far the board has scrolled since the tile was picked
     * up, signed the same way the ghost's own shift is. It used to be missing
     * here while the ghost applied it, so the poster followed the finger
     * correctly and the *drop* did not: auto-scrolling to reach an off-screen
     * row moved every row past the finger, and the highlight, the caret and the
     * release all kept resolving against the content position the tile was
     * picked up from. The one gesture the auto-scroll exists for - carrying a
     * title to a row that was not on screen - filed it into the row that *was*.
     */
    fun drop(
        arrangement: TierListLayout,
        zoom: Float,
        density: Float,
        scrollShift: Float = 0f
    ): TierDrop {
        val mediaId = mediaId ?: return TierDrop(tierId = null, beforeMediaId = null)
        val position = origin + travel.value + Offset(0f, scrollShift)
        return tierDropPlacement(
            rows = arrangement.rows,
            items = arrangement.items,
            x = position.x / density / zoom,
            y = position.y / density / zoom,
            skipMediaId = mediaId
        )
    }
}

/**
 * The drop a release at a *window* position would make, or no tier off the board.
 *
 * The board's drag works in its own layout coordinates; the tray's drag is handed
 * a pointer in window pixels, below a scrolling column, with the board zoomed. This
 * is the single conversion between the two, and the board publishes it through
 * [TierBoardViewport.dropResolver] so the tray, the row highlight and the drop caret
 * all read one answer.
 *
 * It used to be a second hit test over published row bands - a tray-local opinion
 * about where the rows were - which could only ever be approximately right: it knew
 * a row's vertical extent but not its tiles, so a tray drop always appended to the
 * end of a row and could not show where inside it the poster would land. A tray
 * that asks the board cannot disagree with the board about the seam between two
 * posters.
 */
internal fun tierDropAtWindow(
    rows: List<TierRowBox>,
    items: List<TierItemBox>,
    windowX: Float,
    windowY: Float,
    boardLeft: Float,
    boardTop: Float,
    density: Float,
    zoom: Float,
    skipMediaId: Int? = null
): TierDrop = tierDropPlacement(
    rows = rows,
    items = items,
    x = (windowX - boardLeft) / density / zoom,
    y = (windowY - boardTop) / density / zoom,
    skipMediaId = skipMediaId
)

/**
 * Auto-scroll while a drag is held near an edge.
 *
 * One frame at a time, paced by the display, so the speed is the same on a 60Hz
 * and a 120Hz screen and the scroll stops the instant the finger leaves the band.
 * A `while (isActive)` around `withFrameNanos` is the whole scheduler: this is a
 * foreground gesture, and a job that outlived it would scroll a board nobody is
 * touching.
 *
 * The distance per frame is scaled by the frame's own duration, and used not to
 * be: a fixed dp *per frame* is twice the speed at 120Hz, so the same drag that
 * was controllable on one phone overshot every row on another. [pointerY] is read
 * every frame rather than captured, because the finger belongs to a gesture that
 * is moving the board under itself - and the board's drag and the tray's drag each
 * pass their own.
 */
@Composable
internal fun TierEdgeAutoScroll(
    active: Boolean,
    pointerY: () -> Float,
    viewport: TierBoardViewport,
    scrollState: ScrollState,
    density: Float
) {
    val currentPointerY by rememberUpdatedState(pointerY)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val edgePx = TIER_DRAG_EDGE_DP * density
        val perFramePx = TIER_DRAG_MAX_SCROLL_DP * density
        var lastFrame = 0L
        while (isActive) {
            val frame = withFrameNanos { it }
            val elapsed = if (lastFrame == 0L) 0L else frame - lastFrame
            lastFrame = frame
            val strength = tierDragScrollStrength(
                pointerY = currentPointerY(),
                viewportTop = viewport.viewportTop,
                viewportBottom = viewport.viewportBottom,
                edge = edgePx
            )
            if (strength != 0f) {
                // The raw delta rather than `scrollBy`: this is a gesture moving a
                // list, not an animation, and the raw path cannot fail with a
                // mutation-priority conflict - which would kill this loop silently
                // and stop the scroll exactly while it is holding the drag up.
                scrollState.dispatchRawDelta(
                    tierAutoScrollDeltaPx(strength, perFramePx, elapsed)
                )
            }
        }
    }
}

/** How many 60Hz frames one scroll step's distance is paced against. */
private const val NANOS_PER_FRAME = 16_666_667f

/**
 * A frame's worth of auto-scroll, in pixels, at any refresh rate.
 *
 * A dropped frame must not teleport the board either, so the elapsed time is
 * capped at [MAX_FRAME_SPAN] frames - a stall becomes a pause rather than a jump
 * of a row's height. The first frame after lift has no elapsed time and moves
 * nothing, which keeps the pick-up frame stable.
 */
internal fun tierAutoScrollDeltaPx(
    strength: Float,
    perFramePx: Float,
    elapsedNanos: Long
): Float {
    if (elapsedNanos <= 0L) return 0f
    val frames = (elapsedNanos.toFloat() / NANOS_PER_FRAME)
        .coerceIn(0f, MAX_FRAME_SPAN)
    return strength * perFramePx * frames
}

/** The longest frame a single scroll step may be paced against. */
private const val MAX_FRAME_SPAN = 4f

/** Draws the "release to file under..." pill, and clears it when the drag ends. */
@Composable
internal fun TierDragHint(
    dragging: Boolean,
    hoveredTierId: String?,
    tiers: List<TierDefinition>,
    onDragHintChanged: (String?) -> Unit
) {
    val hint = if (!dragging) null else tierDragHint(tiers, hoveredTierId)
    LaunchedEffect(hint) { onDragHintChanged(hint) }
    DisposableEffect(Unit) {
        onDispose { onDragHintChanged(null) }
    }
}

/**
 * The long-press drag gesture, as a modifier.
 *
 * `pointerInput(arrangement, zoom)` and not `pointerInput(Unit)`: the handler
 * closes over the boxes it hit-tests, so a list that changed under an installed
 * handler would resolve presses against geometry that is no longer on screen.
 */
internal fun Modifier.tierDragHandler(
    drag: TierDragState,
    arrangement: TierListLayout,
    zoom: Float,
    density: Float,
    haptics: HapticFeedback,
    viewport: TierBoardViewport,
    onMove: (mediaId: Int, tierId: String?, beforeMediaId: Int?) -> Unit
): Modifier = pointerInput(arrangement, zoom) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            tierItemAt(
                items = arrangement.items,
                x = offset.x / density / zoom,
                y = offset.y / density / zoom
            )?.let { mediaId ->
                drag.lift(mediaId = mediaId, origin = offset, boardTop = viewport.boardTop)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        },
        onDrag = { change, amount ->
            change.consume()
            drag.moveBy(amount)
        },
        onDragEnd = {
            drag.mediaId?.let { mediaId ->
                // A null tier id means the release landed off every row, and a
                // null tier id is exactly how `moveTo` un-files a title. This is
                // the only route to an empty row that does not delete the row
                // too. The second id is where inside that row it goes.
                //
                // The scroll shift is read live, like the travel: the board may
                // have auto-scrolled since the lift, and the finger did not move
                // with it.
                val target = drag.drop(
                    arrangement = arrangement,
                    zoom = zoom,
                    density = density,
                    scrollShift = drag.boardTop - viewport.boardTop
                )
                onMove(mediaId, target.tierId, target.beforeMediaId)
            }
            drag.reset()
        },
        onDragCancel = { drag.reset() }
    )
}

/** The lifted poster, drawn above the geometry while a drag is in flight. */
@Composable
internal fun TierDragGhostOverlay(
    drag: TierDragState,
    arrangement: TierListLayout,
    candidateById: Map<Int, TierCandidate>,
    options: TierLayoutOptions,
    zoom: Float,
    showTitles: Boolean,
    display: TierDisplay,
    viewport: TierBoardViewport
) {
    val box = drag.mediaId?.let { arrangement.boxByMediaId[it] } ?: return
    TierDragGhost(
        box = box,
        candidate = candidateById[box.mediaId],
        zoom = zoom,
        posterHeight = options.posterHeight * zoom,
        captionHeight = options.captionHeight * zoom,
        showTitle = showTitles,
        titlePosition = display.titlePosition,
        titleLength = display.titleLength,
        travel = drag.travel,
        // How far the board has scrolled since the tile was picked up, read in
        // the draw phase for the same reason the travel is.
        scrollShift = { drag.boardTop - viewport.boardTop }
    )
}
