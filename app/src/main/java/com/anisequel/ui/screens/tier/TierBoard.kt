package com.anisequel.ui.screens.tier

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierDisplay
import com.anisequel.domain.tier.TierLayoutOptions
import com.anisequel.domain.tier.TierListLayout
import com.anisequel.domain.tier.layoutTierList
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Measures the available width, then hands it to [TierBoard].
 *
 * Width has to be known *before* composition emits the children, because the
 * children are positioned by the layout that the width itself determines.
 * `onSizeChanged` rather than `BoxWithConstraints`: the latter measures its
 * content against `BoxScope` constraints, and its `maxWidth`/`minWidth` accessors
 * are on their way out in favour of `constraints`, so a screen written against
 * them would be standing on a deprecated API to save one frame.
 */
@Composable
internal fun TierBoardHost(
    listName: String,
    tiers: List<TierDefinition>,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    display: TierDisplay,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    viewport: TierBoardViewport,
    scrollState: ScrollState,
    dragEnabled: Boolean,
    onMove: (mediaId: Int, tierId: String?, beforeMediaId: Int?) -> Unit,
    onItemTap: (Int) -> Unit,
    onDragHintChanged: (String?) -> Unit
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width }
            .testTag("tier_board_host")
    ) {
        // Nothing is drawn until the first measurement lands. A layout built at
        // width 0 packs one item per row and gives every row its minimum height,
        // so rendering that frame would flash six full-width bars.
        if (widthPx > 0) {
            TierBoard(
                widthDp = with(density) { widthPx.toDp() }.value,
                listName = listName,
                tiers = tiers,
                assignment = assignment,
                candidates = candidates,
                display = display,
                zoom = zoom,
                onZoomChange = onZoomChange,
                viewport = viewport,
                scrollState = scrollState,
                dragEnabled = dragEnabled,
                onMove = onMove,
                onItemTap = onItemTap,
                onDragHintChanged = onDragHintChanged
            )
        }
    }
}

/**
 * The board itself: rows, posters, drag and pinch.
 *
 * ## The geometry is not computed here
 *
 * Every row and every tile is placed by [layoutTierList], in the *same* units the
 * SVG exporter reads. This file holds no second opinion about where anything goes:
 * it builds a [TierLayoutOptions] whose `width` is the measured screen width in dp,
 * asks the domain for the arrangement, and places children at the boxes it hands
 * back. A screen that arranged its own rows would agree with the export right up
 * until the day somebody changed a gap in one of them - and the symptom would be an
 * exported file whose labels are in a different order from the screen it came from.
 *
 * ## Zoom divides the layout width rather than scaling the canvas
 *
 * Pinching sets `options.width = screenWidthDp / zoom`, and every box is placed at
 * `x * zoom`. There is therefore still one arrangement - the same numbers an export
 * would produce at zoom 1 - measured against a narrower or wider coordinate space.
 * Scaling a finished canvas with a `graphicsLayer` instead would leave `itemWidth`
 * meaning two different things depending on whether the caller had zoomed, which is
 * precisely the disagreement the shared layout exists to prevent.
 *
 * ## A drag outlives the screen it started on
 *
 * The board is taller than the viewport as soon as a list is worth having, so a
 * drag held near the top or bottom edge scrolls the screen underneath it
 * ([tierDragScrollStrength]) and the ghost is translated by how far the board has
 * moved since the tile was picked up. Without the second half the tile slides out
 * from under the finger exactly when the person is dragging furthest, which is the
 * moment they are least able to see what they are doing.
 */
@Composable
private fun TierBoard(
    widthDp: Float,
    listName: String,
    tiers: List<TierDefinition>,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    display: TierDisplay,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    viewport: TierBoardViewport,
    scrollState: ScrollState,
    dragEnabled: Boolean,
    onMove: (mediaId: Int, tierId: String?, beforeMediaId: Int?) -> Unit,
    onItemTap: (Int) -> Unit,
    onDragHintChanged: (String?) -> Unit
) {
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    // Held as the state object rather than read with `by`, because the dragged
    // ghost's translation has to be read inside its `graphicsLayer` block. A
    // `by` here would read it during composition and rebuild the modifier chain
    // on every pointer move, which is a recomposition per frame for a drag.
    val dragTravel = remember { mutableStateOf(Offset.Zero) }
    // Where the board sat in the window when the tile was picked up.
    var dragBoardTop by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Locking the board while a tile is under the finger must not leave the
    // ghost behind: the drag pointer handler is about to be removed, so its
    // `onDragCancel` will never run.
    LaunchedEffect(dragEnabled) {
        if (!dragEnabled) {
            draggingId = null
            dragTravel.value = Offset.Zero
        }
    }

    val tileTitles = display.showTitles && zoom >= TIER_CAPTION_MIN_ZOOM
    val options = remember(widthDp, zoom, display) {
        ScreenTierOptions.copy(
            width = widthDp / zoom,
            // No caption band when the name is on the cover, hidden, or too
            // small to read. Keeping the band at 35% would leave a 12dp strip
            // of empty background under every 25dp poster.
            captionHeight = if (display.hasCaptionBand && zoom >= TIER_CAPTION_MIN_ZOOM) {
                ScreenTierOptions.captionHeight
            } else {
                0f
            }
        )
    }
    val arrangement = remember(options, tiers, assignment, candidates, listName) {
        layoutTierList(
            tiers = tiers,
            assignment = assignment,
            candidates = candidates,
            title = listName,
            options = options
        )
    }
    val candidateById = remember(candidates) { candidates.associateBy { it.mediaId } }

    // Where the rows actually are, in window px, for the tray's drag to resolve
    // against.
    //
    // `boardTop` is read inside `snapshotFlow`, never as a composition key.
    //
    // It used to be a `LaunchedEffect` key, and `boardTop` is a
    // `mutableFloatStateOf` - so every frame the board moved, *reading* it
    // invalidated the whole `TierBoard` recompose scope: a coroutine relaunch, a
    // fresh band list, and every `TierRowStrip` and `TierPosterTile` re-executing,
    // each with its own `animateColorAsState` and `AsyncImage`. On a fling, and on
    // every frame of the drag auto-scroll loop, which is precisely the
    // frame-rate-sensitive path.
    //
    // `snapshotFlow` observes the same state from a coroutine, which is not a
    // composition, so the board keeps moving without recomposing anything.
    LaunchedEffect(arrangement, zoom, density.density, scrollState) {
        val pxPerDp = density.density
        snapshotFlow { scrollState.value to viewport.boardTop }.collect {
            val boardTop = viewport.boardTop
            viewport.rowBands = arrangement.rows.map { row ->
                TierRowBand(
                    tierId = row.tierId,
                    top = boardTop + row.y * zoom * pxPerDp,
                    bottom = boardTop + row.bottom * zoom * pxPerDp
                )
            }
        }
    }

    // ...and un-published when this board leaves the composition.
    //
    // `viewport` is remembered by the *screen*, so it outlives the board, and the
    // board is not always composed - "Empty every tier" replaces it with an empty
    // state while the pool stays. The bands were the one piece of state with no
    // writer on that path, so a tray drop resolved against rows that were no
    // longer drawn, at window positions that no longer lined up with anything: the
    // title was filed under a tier id nobody could see and the board came back with
    // a poster in it, placed by a phantom.
    DisposableEffect(Unit) {
        onDispose { viewport.rowBands = emptyList() }
    }

    // Derived rather than recomputed on every pointer move. Reading `dragTravel`
    // straight into composition made the whole board recompose each frame of a
    // drag, only to redraw the same hint and caret; a `derivedStateOf` notifies its
    // readers only when the drop *changes*, so dragging across one row is a draw
    // invalidation (the ghost's `graphicsLayer` already reads the travel in the
    // draw phase) instead of a full recomposition of every row and poster.
    val drop = remember(draggingId, arrangement, zoom, density) {
        derivedStateOf {
            val mediaId = draggingId
            if (mediaId == null) {
                TierDrop(tierId = null, beforeMediaId = null)
            } else {
                val position = dragOrigin + dragTravel.value
                tierDropPlacement(
                    rows = arrangement.rows,
                    items = arrangement.items,
                    x = position.x / density.density / zoom,
                    y = position.y / density.density / zoom,
                    skipMediaId = mediaId
                )
            }
        }
    }
    val dropTarget = drop.value
    val caret = remember(dropTarget, arrangement, options, draggingId) {
        tierDropCaret(
            drop = dropTarget,
            rows = arrangement.rows,
            items = arrangement.items,
            options = options,
            // The same exclusion the placement uses, so the mark and the answer are
            // computed from one list of "tiles that will still be here".
            skipMediaId = draggingId
        )
    }

    // The hint lives on the screen above the board, not inside it: the board
    // scrolls during a drag, and a pill drawn at the board's top edge scrolls away
    // with it - precisely while auto-scrolling, which is when it is being read.
    val hint = if (draggingId == null) null else tierDragHint(tiers, dropTarget.tierId)
    LaunchedEffect(hint) { onDragHintChanged(hint) }
    DisposableEffect(Unit) {
        onDispose { onDragHintChanged(null) }
    }

    // Auto-scroll while a drag is held near an edge. One frame at a time, paced by
    // the display rather than by a fixed delay, so the speed is the same on a 60Hz
    // and a 120Hz screen and the scroll stops the instant the finger leaves the
    // band. A `while (isActive)` around `withFrameNanos` is the whole scheduler:
    // this is a foreground gesture, and a job that outlived it would scroll a board
    // nobody is touching.
    LaunchedEffect(draggingId) {
        if (draggingId == null) return@LaunchedEffect
        val edgePx = TIER_DRAG_EDGE_DP * density.density
        val perFramePx = TIER_DRAG_MAX_SCROLL_DP * density.density
        while (isActive) {
            withFrameNanos { }
            val strength = tierDragScrollStrength(
                pointerY = viewport.boardTop + dragOrigin.y + dragTravel.value.y,
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

    // No trailing return value: `rememberTransformableState`'s callback returns
    // `Unit` in this Compose version, so the `true` that used to end this lambda
    // was an unused expression - the compiler said so on every release build.
    // (The comment above it claimed the callback "is `suspend` and returns a
    // Boolean", which describes an API shape this project has never compiled
    // against.)
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        onZoomChange(clampTierZoom(zoom * zoomChange))
    }

    val dragBox = draggingId?.let { arrangement.boxByMediaId[it] }

    // The drag handler is *removed* rather than merely disabled while the board
    // is locked. Leaving a pointer input installed that ignores its drags still
    // competes with `transformable` for the first pointer, which is the bug the
    // lock exists to avoid: a pinch that starts on a poster was being eaten by
    // the long-press drag detector before the second finger arrived.
    val dragHandler = if (dragEnabled) {
        Modifier.pointerInput(arrangement, zoom) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val mediaId = tierItemAt(
                        items = arrangement.items,
                        x = offset.x / density.density / zoom,
                        y = offset.y / density.density / zoom
                    )
                    if (mediaId == null) return@detectDragGesturesAfterLongPress
                    draggingId = mediaId
                    dragOrigin = offset
                    dragTravel.value = Offset.Zero
                    dragBoardTop = viewport.boardTop
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDrag = { change, amount ->
                    change.consume()
                    dragTravel.value += amount
                },
                onDragEnd = {
                    val mediaId = draggingId
                    if (mediaId != null) {
                        // Read the live value rather than the one the last
                        // composition saw: the final pointer move is not
                        // required to have triggered a recomposition, and a
                        // drop that lands one frame behind the finger is a
                        // drop into the wrong row.
                        val target = drop.value
                        // A null tier id means the release landed off every
                        // row, and a null tier id is exactly how `moveTo`
                        // un-files a title. This is the only route to an empty
                        // row that does not delete the row too. The second id
                        // is where inside that row it goes.
                        onMove(mediaId, target.tierId, target.beforeMediaId)
                    }
                    draggingId = null
                    dragTravel.value = Offset.Zero
                },
                onDragCancel = {
                    draggingId = null
                    dragTravel.value = Offset.Zero
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Where this board sits in the window, refreshed on every frame it
            // moves. The pointer's position *on screen* is this plus the drag's own
            // offset, and that is what the edge test is about.
            // `localToWindow(Offset.Zero)`, not `positionInWindow()`: the latter was
            // deprecated and has now been removed from Compose UI entirely, so it
            // compiles against nothing. This is its documented equivalent - the
            // window position of this node's top-left corner.
            .onGloballyPositioned { coordinates ->
                val window = coordinates.localToWindow(Offset.Zero)
                viewport.boardTop = window.y
                viewport.boardLeft = window.x
                viewport.boardWidth = coordinates.size.width.toFloat()
            }
            .transformable(state = transformState)
            .then(dragHandler)
    ) {
        TierGeometry(
            arrangement = arrangement,
            options = options,
            zoom = zoom,
            display = display,
            showTitles = tileTitles,
            candidateById = candidateById,
            hoveredTierId = dropTarget.tierId,
            dropCaret = caret,
            skipMediaId = draggingId,
            onItemTap = onItemTap
        )

        if (dragBox != null) {
            TierDragGhost(
                box = dragBox,
                candidate = candidateById[dragBox.mediaId],
                zoom = zoom,
                posterHeight = options.posterHeight * zoom,
                captionHeight = options.captionHeight * zoom,
                showTitle = tileTitles,
                titlePosition = display.titlePosition,
                travel = dragTravel,
                // How far the board has scrolled since the tile was picked up,
                // read in the draw phase for the same reason the travel is.
                scrollShift = { dragBoardTop - viewport.boardTop }
            )
        }
    }
}

/**
 * Places the rows, the tiles and the drop caret at the boxes [layoutTierList]
 * produced.
 *
 * A `Layout` rather than a `Canvas` so the posters are real `AsyncImage`s that
 * Coil can cancel, recycle and cross-fade. The measure policy is the only place
 * in the app that knows about positions, and it knows about nothing else.
 *
 * The content lambda and [slots] are two views of the same ordered list - rows,
 * tiles, then the caret - and the `check` is what keeps them that way. Compose hands
 * the measure policy its children in emission order and offers no way to ask which
 * is which, so a row added to one list and not the other would otherwise place every
 * tile one row too high, and it would read as a layout bug for ever.
 *
 * Both halves read the same [visibleTierItems] list rather than filtering for
 * themselves. They used to disagree - the dragged tile was skipped from the
 * emitted children only - so starting a drag made the two counts differ by one and
 * the `check` below threw. Sharing the list is what makes that impossible rather
 * than merely fixed, and the caret is added to both halves in one `remember` for
 * the same reason.
 *
 * Named `arrangement` rather than `layout` because a parameter called `layout`
 * would shadow `MeasureScope.layout` at the `layout(width, height)` call at the
 * bottom of this function.
 */
@Composable
private fun TierGeometry(
    arrangement: TierListLayout,
    options: TierLayoutOptions,
    zoom: Float,
    display: TierDisplay,
    showTitles: Boolean,
    candidateById: Map<Int, TierCandidate>,
    hoveredTierId: String?,
    dropCaret: TierCaret?,
    skipMediaId: Int?,
    onItemTap: (Int) -> Unit
) {
    // The one tile list the whole layout is built from, so the emitted children
    // and the positions they are measured against cannot drift apart.
    val drawnItems = remember(arrangement, skipMediaId) {
        visibleTierItems(arrangement.items, skipMediaId)
    }
    // How many posters each row holds, for the count under its label. Counted
    // from `arrangement.items` rather than `drawnItems` so the number does not
    // drop by one while its poster is being carried - the row still holds it.
    val memberCounts = remember(arrangement) {
        arrangement.rows.associate { row ->
            row.tierId to arrangement.items.count { it.y >= row.y && it.bottom <= row.bottom }
        }
    }
    val slots = remember(arrangement, drawnItems, dropCaret) {
        buildList {
            arrangement.rows.forEach { add(Slot(it.x, it.y, it.width, it.height)) }
            drawnItems.forEach { add(Slot(it.x, it.y, it.width, it.height)) }
            dropCaret?.let {
                add(Slot(it.x - TIER_CARET_WIDTH / 2f, it.y, TIER_CARET_WIDTH, it.height))
            }
        }
    }

    Layout(
        content = {
            arrangement.rows.forEach { row ->
                TierRowStrip(
                    row = row,
                    // Scaled with the row it fills, for the same reason the tiles
                    // are: the layout places the first poster at
                    // `(padding + labelGutter) * zoom`, so a fixed gutter would sit
                    // inside the poster area at 75% and leave a band of empty row at
                    // 200%, with the label centred in the wrong strip either way.
                    labelGutter = options.labelGutter * zoom,
                    memberCount = memberCounts[row.tierId] ?: 0,
                    highlighted = row.tierId == hoveredTierId
                )
            }
            drawnItems.forEach { box ->
                // The *scaled* numbers, not the layout's own. The slot below is
                // measured at `slot.height * zoom`, and the tile's content was
                // asking for unscaled heights - so the box grew with the rows while
                // the artwork inside it stayed the size it started. At maximum zoom
                // every poster rendered as a wide 4:3 crop with a band of dead space
                // under its caption; at `TIER_MIN_ZOOM` the caption was handed
                // `maxHeight = 96 - 96 = 0` and every title vanished.
                //
                // `TierDragGhost` was already given the scaled pair, which is why
                // picking a poster up visibly resized it - the ghost is the correct
                // version of a tile and the tile was not.
                TierPosterTile(
                    candidate = candidateById[box.mediaId],
                    // All three scaled: the slot is measured at `box.width * zoom`
                    // and the tile sizes its own artwork from these numbers. Passing
                    // the unscaled width left a 72-unit-wide column inside a
                    // 144-unit slot at 200%, so the poster rendered as a tall strip
                    // and the ghost that is picked up from it was suddenly wider
                    // than the tile it replaced.
                    width = box.width * zoom,
                    posterHeight = options.posterHeight * zoom,
                    captionHeight = options.captionHeight * zoom,
                    showTitle = showTitles,
                    titlePosition = display.titlePosition,
                    onTap = { onItemTap(box.mediaId) }
                )
            }
            if (dropCaret != null) {
                TierDropCaret()
            }
        },
        measurePolicy = { measurables, _ ->
            check(measurables.size == slots.size) {
                "TierGeometry emitted ${measurables.size} children for ${slots.size} " +
                    "slots; the content lambda and the slot list must stay in step."
            }
            val placeables = measurables.mapIndexed { index, measurable ->
                val slot = slots[index]
                measurable.measure(
                    Constraints.fixed(
                        width = (slot.width * zoom).dp.toPx().roundToInt(),
                        height = (slot.height * zoom).dp.toPx().roundToInt()
                    )
                )
            }
            layout(
                width = (arrangement.width * zoom).dp.toPx().roundToInt(),
                height = (arrangement.height * zoom).dp.toPx().roundToInt()
            ) {
                placeables.forEachIndexed { index, placeable ->
                    val slot = slots[index]
                    placeable.place(
                        x = (slot.x * zoom).dp.toPx().roundToInt(),
                        y = (slot.y * zoom).dp.toPx().roundToInt()
                    )
                }
            }
        },
        modifier = Modifier.testTag("tier_geometry")
    )
}

/** One position, in the layout's own dp units, before zoom is applied. */
private data class Slot(val x: Float, val y: Float, val width: Float, val height: Float)
