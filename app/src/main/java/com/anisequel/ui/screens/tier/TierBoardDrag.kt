package com.anisequel.ui.screens.tier

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.anisequel.domain.tier.TierLayoutOptions
import com.anisequel.domain.tier.TierListLayout
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
     */
    fun drop(arrangement: TierListLayout, zoom: Float, density: Float): TierDrop {
        val mediaId = mediaId ?: return TierDrop(tierId = null, beforeMediaId = null)
        val position = origin + travel.value
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
 * Auto-scroll while a drag is held near an edge.
 *
 * One frame at a time, paced by the display rather than by a fixed delay, so the
 * speed is the same on a 60Hz and a 120Hz screen and the scroll stops the
 * instant the finger leaves the band. A `while (isActive)` around
 * `withFrameNanos` is the whole scheduler: this is a foreground gesture, and a
 * job that outlived it would scroll a board nobody is touching.
 */
@Composable
internal fun TierDragAutoScroll(
    drag: TierDragState,
    viewport: TierBoardViewport,
    scrollState: ScrollState,
    density: Float
) {
    LaunchedEffect(drag.mediaId) {
        if (drag.mediaId == null) return@LaunchedEffect
        val edgePx = TIER_DRAG_EDGE_DP * density
        val perFramePx = TIER_DRAG_MAX_SCROLL_DP * density
        while (isActive) {
            withFrameNanos { }
            val strength = tierDragScrollStrength(
                pointerY = viewport.boardTop + drag.origin.y + drag.travel.value.y,
                viewportTop = viewport.viewportTop,
                viewportBottom = viewport.viewportBottom,
                edge = edgePx
            )
            if (strength != 0f) {
                // The raw delta rather than `scrollBy`: this is a gesture moving a
                // list, not an animation, and the raw path cannot fail with a
                // mutation-priority conflict - which would kill this loop silently
                // and stop the scroll exactly while it is holding the drag up.
                scrollState.dispatchRawDelta(strength * perFramePx)
            }
        }
    }
}

/** Draws the "release to file under..." pill, and clears it when the drag ends. */
@Composable
internal fun TierDragHint(
    draggingId: Int?,
    hoveredTierId: String?,
    tiers: List<TierDefinition>,
    onDragHintChanged: (String?) -> Unit
) {
    val hint = if (draggingId == null) null else tierDragHint(tiers, hoveredTierId)
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
                val target = drag.drop(arrangement, zoom, density)
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
        travel = drag.travel,
        // How far the board has scrolled since the tile was picked up, read in
        // the draw phase for the same reason the travel is.
        scrollShift = { drag.boardTop - viewport.boardTop }
    )
}
