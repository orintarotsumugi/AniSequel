package com.anisequel.ui.screens.tier

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
    editable: Boolean,
    onMove: (mediaId: Int, tierId: String?, beforeMediaId: Int?) -> Unit,
    onItemTap: (Int) -> Unit,
    onDragHintChanged: (String?) -> Unit,
    /**
     * Whether the tray holds a drag, and where that drag currently points.
     *
     * A tray drop is the board's drop as much as its own is: the row it will land
     * in is highlighted, the caret shows the seam, and the hint pill names the
     * tier - all computed from the same resolver the release uses. The provider is
     * a lambda rather than a value so only the board's own composition scope is
     * invalidated when the drop moves between rows; the screen above it is not
     * rebuilt on every seam the finger crosses.
     */
    externalDragging: Boolean = false,
    externalDrop: (() -> TierDrop?)? = null
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
                editable = editable,
                onMove = onMove,
                onItemTap = onItemTap,
                onDragHintChanged = onDragHintChanged,
                externalDragging = externalDragging,
                externalDrop = externalDrop
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
 *
 * That drag's state, its pointer handler, the auto-scroll loop and the ghost all
 * live in `TierBoardDrag.kt`; this file owns the geometry they resolve against.
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
    editable: Boolean,
    onMove: (mediaId: Int, tierId: String?, beforeMediaId: Int?) -> Unit,
    onItemTap: (Int) -> Unit,
    onDragHintChanged: (String?) -> Unit,
    externalDragging: Boolean,
    externalDrop: (() -> TierDrop?)?
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    // The gesture's state lives in one object, so the pointer handler, the
    // auto-scroll loop and the ghost can all be handed the same thing instead of
    // five separate `remember`ed values threaded through every call.
    val drag = remember { TierDragState() }

    val tileTitles = display.showTitles && zoom >= TIER_CAPTION_MIN_ZOOM
    val options = remember(widthDp, zoom, display) {
        tierBoardOptions(widthDp = widthDp, zoom = zoom, display = display)
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

    // Any of these keys changing replaces the drag pointer handler, and a replaced
    // handler never delivers `onDragCancel`. Locking the board was the case the reset
    // was written for, but a list rebuild, a stream of incoming candidates, an undo or
    // a zoom step restart the handler too - and without a reset the ghost and row
    // highlight froze under the finger while `TierEdgeAutoScroll` (keyed on
    // `drag.mediaId != null`) kept ticking until the next lift. Unconditional on
    // purpose: this effect *is* the handler-replaced signal, and clearing an empty
    // drag costs nothing.
    LaunchedEffect(dragEnabled, arrangement, zoom) {
        drag.reset()
    }
    val candidateById = remember(candidates) { candidates.associateBy { it.mediaId } }

    // Row positions for the tray's drag, the auto-scroll while dragging and the
    // hint pill are three side effects with three lifetimes; they live in the
    // helper composables below so this one stays about composition.
    //
    // The resolver is the board's own window-space answer to "where would a tray
    // drop land". It replaces a published list of row bands - a second, tray-side
    // hit test that knew which row a pointer was over but not where inside it, so
    // every tray drop appended to the row while a board drag could place between
    // two posters. Both drags now resolve against one layout. It is cleared on
    // dispose so a tray drop made while the board is replaced by an empty state
    // resolves to no tier rather than to bands that are no longer drawn.
    DisposableEffect(viewport, arrangement, zoom, density) {
        viewport.dropResolver = { windowX, windowY ->
            tierDropAtWindow(
                rows = arrangement.rows,
                items = arrangement.items,
                windowX = windowX,
                windowY = windowY,
                boardLeft = viewport.boardLeft,
                boardTop = viewport.boardTop,
                density = density.density,
                zoom = zoom,
                bottomLimitY = viewport.visibleBottom
            )
        }
        onDispose { viewport.dropResolver = null }
    }
    TierEdgeAutoScroll(
        active = drag.mediaId != null,
        // The board's position *at lift* plus the travel: this is the finger's
        // window position. It used to read the live `viewport.boardTop`, which
        // moves as the board auto-scrolls under the finger, so the computed
        // pointer climbed out of the bottom band while the finger still held it
        // there and the scroll stopped short.
        pointerY = { drag.boardTop + drag.origin.y + drag.travel.value.y },
        viewport = viewport,
        scrollState = scrollState,
        density = density.density
    )

    // Derived rather than recomputed on every pointer move. Reading the drag's
    // travel straight into composition made the whole board recompose each frame
    // of a drag, only to redraw the same hint and caret; a `derivedStateOf`
    // notifies its readers only when the drop *changes*, so dragging across one
    // row is a draw invalidation (the ghost's `graphicsLayer` already reads the
    // travel in the draw phase) instead of a full recomposition of every row and
    // poster.
    //
    // The shift is read here as well as in the ghost's draw phase, because a
    // board that auto-scrolled under a stationary finger has moved its rows past
    // the pointer without a single pointer event to say so. The ghost used to
    // compensate and the drop did not, so the marker and the release stayed on
    // the row the tile was picked up from while the poster hung over another -
    // visible as a drop that files a title into the wrong tier after a scroll.
    val drop = remember(drag.mediaId, arrangement, zoom, density) {
        derivedStateOf {
            drag.releaseDrop(
                arrangement = arrangement,
                zoom = zoom,
                density = density.density,
                viewport = viewport
            )
        }
    }
    val boardDragging = drag.mediaId != null
    // The tray's drag points through the same resolver the release uses, so the
    // highlight, the caret and the drop cannot disagree by a pixel.
    val external = if (boardDragging) null else externalDrop?.invoke()
    val activeDrop = if (boardDragging) drop.value else external
    val hoveredTierId = activeDrop?.tierId
    val caret = remember(activeDrop, arrangement, options, drag.mediaId) {
        tierDropCaret(
            drop = activeDrop ?: TierDrop(tierId = null, beforeMediaId = null),
            rows = arrangement.rows,
            items = arrangement.items,
            options = options,
            // The same exclusion the placement uses, so the mark and the answer are
            // computed from one list of "tiles that will still be here".
            skipMediaId = drag.mediaId
        )
    }

    // A light tick when the carried poster crosses into another row. The
    // highlight sits under the finger that is covering it, so without this the
    // only confirmation that the target changed is a colour the hand cannot see.
    var lastHoveredTierId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hoveredTierId) {
        if (hoveredTierId != null && lastHoveredTierId != null &&
            hoveredTierId != lastHoveredTierId
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastHoveredTierId = hoveredTierId
    }

    // The hint lives on the screen above the board, not inside it: the board
    // scrolls during a drag, and a pill drawn at the board's top edge scrolls away
    // with it - precisely while auto-scrolling, which is when it is being read.
    TierDragHint(
        dragging = boardDragging || externalDragging,
        hoveredTierId = hoveredTierId,
        tiers = tiers,
        onDragHintChanged = onDragHintChanged,
        // Only the tray's drag is "from the pool"; a board drag that has been
        // carried over the shelf is still taking a title out of the list.
        fromTray = !boardDragging
    )

    // ## Pinch is two fingers only; one finger belongs to the scroll
    //
    // This used to be `Modifier.transformable`. That modifier reports a *pan* as
    // well as a zoom, and consumes the movement it reports - including a
    // one-finger pan, which is exactly what the screen's `verticalScroll` needs.
    // The result was a board that refused to scroll under a single finger no
    // matter what the lock said: the gesture started, `transformable` consumed
    // every move while its callback threw the pan away, and the column above and
    // below the board scrolled normally while the board itself felt frozen.
    //
    // The detector below only acts while at least two pointers are down, so a
    // one-finger drag reaches the scrollable untouched and a pinch still zooms.
    // The value is kept locally for the gesture because the composition's `zoom`
    // is a frame behind by the time the next pointer event arrives; the callback
    // is read through `rememberUpdatedState` for the same reason (a
    // `pointerInput(Unit)` block keeps the lambda from the composition that
    // installed it).
    val liveZoom by rememberUpdatedState(zoom)
    val applyZoom by rememberUpdatedState(onZoomChange)
    val pinchZoom = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var gestureZoom = liveZoom
            var zooming = false
            // `while (true)` rather than a `do/while`: a value declared in the body
            // of a `do` block is not in scope in its condition, and the pointer
            // event is exactly what the condition needs. The `break` is the same
            // "every pointer is up" test `awaitEachGesture` uses between gestures.
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    val factor = event.calculateZoom()
                    if (!factor.isNaN() && factor != 1f) {
                        gestureZoom = clampTierZoom(gestureZoom * factor)
                        applyZoom(gestureZoom)
                        zooming = true
                    }
                    event.changes.forEach { if (it.pressed) it.consume() }
                } else if (zooming) {
                    // One finger lifted mid-pinch. Consume whatever is left of the
                    // gesture so the scrollable cannot start from the second
                    // finger and jump the board by half a screen.
                    event.changes.forEach { if (it.pressed) it.consume() }
                }
                if (event.changes.none { it.pressed }) break
            }
        }
    }

    // The drag handler is *removed* rather than merely disabled while the board
    // is locked. Leaving a pointer input installed that ignores its drags still
    // claims the first pointer for a long-press it will never finish, which is
    // the bug the lock exists to avoid: a pinch that starts on a poster was
    // being eaten by the drag detector before the second finger arrived - and a
    // one-finger scroll that starts there can feel stuck for the same reason.
    val dragHandler = if (dragEnabled) {
        Modifier.tierDragHandler(
            drag = drag,
            arrangement = arrangement,
            zoom = zoom,
            density = density.density,
            haptics = haptics,
            viewport = viewport,
            onMove = onMove
        )
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
            .then(pinchZoom)
            .then(dragHandler)
    ) {
        TierGeometry(
            arrangement = arrangement,
            options = options,
            zoom = zoom,
            display = display,
            showTitles = tileTitles,
            candidateById = candidateById,
            hoveredTierId = hoveredTierId,
            dropCaret = caret,
            skipMediaId = drag.mediaId,
            editable = editable,
            onItemTap = onItemTap
        )

        TierDragGhostOverlay(
            drag = drag,
            arrangement = arrangement,
            candidateById = candidateById,
            options = options,
            zoom = zoom,
            showTitles = tileTitles,
            display = display,
            viewport = viewport
        )
    }
}

/** The layout options for a board at [zoom], given its measured width. */
private fun tierBoardOptions(
    widthDp: Float,
    zoom: Float,
    display: TierDisplay
): TierLayoutOptions = ScreenTierOptions.copy(
    width = widthDp / zoom,
    // No caption band when the name is on the cover, hidden, or too small to
    // read. Keeping the band at 35% would leave a 12dp strip of empty
    // background under every 25dp poster.
    captionHeight = if (display.hasCaptionBand && zoom >= TIER_CAPTION_MIN_ZOOM) {
        ScreenTierOptions.captionHeight
    } else {
        0f
    }
)

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
    editable: Boolean,
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
                    titleLength = display.titleLength,
                    enabled = editable,
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
