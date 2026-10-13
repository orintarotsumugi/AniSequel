package com.example.ui.screens.tier

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.domain.tier.TierDefinition
import com.example.domain.tier.TierItemBox
import com.example.domain.tier.TierLayoutOptions
import com.example.domain.tier.TierRowBox

// ---------------------------------------------------------------------------
// Pure, testable helpers.
//
// Everything the board decides that is not "draw a thing" lives here, in
// functions `TierScreenLogicTest` can call without a device. Each one records
// the failure it prevents.
//
// This file exists because the tier list screen had grown past 1,500 lines in
// one file: the drag maths, the geometry, and the screen chrome have different
// reasons to change and were being reviewed as one thing. The *package* is
// unchanged, so the tests still reach these by name.
// ---------------------------------------------------------------------------

/**
 * The tier row's fill, from the packed `0xAARRGGBB` long the domain stores.
 *
 * The conversion is written out rather than left to a constructor because
 * Compose offers several and they do not agree about what a `Long` holds:
 *
 *  - `Color(Int)` expects `0x00AARRGGBB` and shifts it into place itself, so
 *    handed `0xFFE5484D` it produces a *transparent* colour rather than an
 *    error. Six rows quietly rendering as nothing is the failure this stops.
 *  - `Color(Long)` masks to 32 bits first, which is what makes a value that has
 *    come back from DataStore as a negative `Long` (any colour whose top byte has
 *    the sign bit set) still work.
 *
 * The value class's primary constructor takes a `ULong` already in Compose's own
 * `0xAARRGGBB` layout - exactly the layout the domain uses - so all that is left
 * to do is mask off anything above the alpha byte and shift the 32-bit ARGB up to
 * where the value class keeps it. Masking rather than trusting the input is what
 * turns `0xFFFFFFFFFFFFFFFF` - which `TierListSvg.Palette` really does hold, for
 * white - into white rather than an out-of-range colour.
 */
internal fun tierRowColor(colorArgb: Long): Color =
    Color((colorArgb.toULong() and 0xFFFFFFFFUL) shl 32)

/** Below this the captions stop being readable; above it a row holds one tile. */
internal const val TIER_MIN_ZOOM = 0.75f
internal const val TIER_MAX_ZOOM = 2.0f

/**
 * Constrains a pinch to the range the board can actually render at.
 *
 * NaN is not hypothetical: a pinch's zoom factor is a ratio of the distance
 * between two pointers to the distance they started at, and two pointers that
 * momentarily coincide divide by zero. `coerceIn` is built on `<` and `>`
 * comparisons, both false for NaN, so it hands NaN straight back - and every
 * `offset()` downstream of a NaN scale produces a layout that never draws.
 * Swallowing it here means one bad frame of gesture maths cannot take the
 * screen with it.
 */
internal fun clampTierZoom(value: Float): Float =
    if (value.isNaN()) TIER_MIN_ZOOM else value.coerceIn(TIER_MIN_ZOOM, TIER_MAX_ZOOM)

/**
 * How far outside a row a drop still counts as "on" that row.
 *
 * Measured from the row's *edge*, not its centre. Rows are ~128dp tall, so a
 * centre-referenced slop would have to be enormous to bridge an 8dp gap - large
 * enough that it would also swallow the empty canvas above the first row and turn
 * a drop on the heading into a placement.
 */
internal const val TIER_DROP_SNAP_SLOP = 16f

/**
 * On-screen geometry.
 *
 * The exporter's defaults (72-unit tiles, a 24-unit frame, a 480-unit floor) are
 * sized for a 1080-unit export canvas, not for a phone: at `itemWidth = 72` a
 * 360dp screen fits three tiles and the captions are unreadable. `headerHeight`
 * is 0 because this screen draws the heading itself, above the board; leaving the
 * layout's own header band set would reserve a band of space for a heading that
 * is not inside it.
 */
internal val ScreenTierOptions = TierLayoutOptions(
    width = 360f,
    labelGutter = 52f,
    padding = 10f,
    itemWidth = 64f,
    itemGap = 6f,
    rowGap = 8f,
    captionHeight = 32f,
    headerHeight = 0f,
    minRowHeight = 48f,
    minCanvasHeight = 200f
)

/**
 * Where a drop lands: which tier, and which tile the dragged one goes above.
 *
 * Two fields rather than one because they are two answers. The tier is the row
 * under the finger; [beforeMediaId] is the *position inside it*, which is the
 * difference between "put it in S" - which used to send the tile to wherever its
 * media id happened to sit in the map - and "put it here, between these two".
 *
 * A null [tierId] is not an error, it is the instruction to take the title out of
 * the list. A null [beforeMediaId] with a tier means the end of that row.
 */
internal data class TierDrop(val tierId: String?, val beforeMediaId: Int?) {
    val clears: Boolean get() = tierId == null
}

/**
 * Which tier a drag released at ([x], [y]) belongs to, or null for "no tier".
 *
 * Null is not an error case, it is the *instruction*: the ViewModel's `moveTo`
 * takes a nullable tier id and files nothing when given one, so a release over
 * the heading or past the bottom of the board un-files the title. That is the
 * only way to empty a row by hand - deleting the row would delete the tier.
 *
 * The two-step resolution exists because the two ways of missing a row are not
 * the same mistake. Landing in the 8dp *gap* between two rows is a near miss with
 * one obvious answer, and refusing it would make dropping feel broken, so the gap
 * snaps to whichever edge is nearer. Landing on the heading, or a hundred dp
 * below the last row, is a deliberate miss: those coordinates are nowhere near
 * any row, and quietly picking the closest one would file a title under a
 * judgement the person never made - which is the exact thing this whole feature
 * refuses to do anywhere else.
 */
internal fun tierDropTarget(
    rows: List<TierRowBox>,
    x: Float,
    y: Float,
    snapSlop: Float = TIER_DROP_SNAP_SLOP
): String? {
    if (rows.isEmpty()) return null

    rows.firstOrNull { row -> x >= row.x && x <= row.right && y >= row.y && y <= row.bottom }
        ?.let { return it.tierId }

    val nearest = rows.minByOrNull { row -> edgeDistance(y, row) } ?: return null
    val closeEnough = edgeDistance(y, nearest) <= snapSlop
    val horizontallyInside = x >= nearest.x && x <= nearest.right
    return if (closeEnough && horizontallyInside) nearest.tierId else null
}

/** How far [y] is from the nearest edge of [row]; zero when it is inside. */
private fun edgeDistance(y: Float, row: TierRowBox): Float = when {
    y < row.y -> row.y - y
    y > row.bottom -> y - row.bottom
    else -> 0f
}

/**
 * The full drop: the row under the finger, and the tile the dragged one lands above.
 *
 * [skipMediaId] is the tile being dragged, and it is excluded from the anchor
 * candidates for a reason that shows up as a bug rather than a glitch: while the
 * finger is still over the tile's own slot, that slot is the tile whose centre is
 * nearest the pointer, so it would be named as the anchor, and the drop would
 * reorder the row under a finger that never moved.
 *
 * The anchor is chosen within the *line* the finger is on, so a two-line row can
 * be dropped into the middle of its second line rather than only at its ends. A
 * finger over the blank space to the right of a line names the first tile of the
 * next line, which is what "after this line" means when the only thing a placement
 * can say is "before that one".
 */
internal fun tierDropPlacement(
    rows: List<TierRowBox>,
    items: List<TierItemBox>,
    x: Float,
    y: Float,
    skipMediaId: Int? = null,
    snapSlop: Float = TIER_DROP_SNAP_SLOP
): TierDrop {
    val tierId = tierDropTarget(rows, x, y, snapSlop) ?: return TierDrop(null, null)
    val row = rows.firstOrNull { it.tierId == tierId } ?: return TierDrop(tierId, null)
    val inRow = items.filter { item ->
        item.mediaId != skipMediaId && item.y >= row.y && item.bottom <= row.bottom
    }
    if (inRow.isEmpty()) return TierDrop(tierId, null)

    val line = inRow.filter { y >= it.y && y <= it.bottom }
    if (line.isEmpty()) {
        // In the gap between two lines, or below the last one: the first tile of
        // the next line is the honest answer, and null - the end of the row -
        // when there is no next line.
        return TierDrop(tierId, inRow.firstOrNull { it.y > y }?.mediaId)
    }
    val nextLine = inRow.firstOrNull { it.y > line.last().y }?.mediaId
    val before = line.firstOrNull { x < (it.x + it.right) / 2f }?.mediaId ?: nextLine
    return TierDrop(tierId, before)
}

/** The width of the "it lands here" caret, in layout units. */
internal const val TIER_CARET_WIDTH = 3f

/** Where the caret that shows the landing position is drawn. */
internal data class TierCaret(val x: Float, val y: Float, val height: Float)

/**
 * The caret for a drop, or null when there is nothing to point at.
 *
 * A drop that clears the list has no position, and drawing a caret for it would
 * promise a slot the release is not going to use - the hint says "take it out of
 * the list" instead. An empty row needs a caret of its own, because "the first
 * poster in this row" has no tile to sit beside: it goes just inside the label
 * gutter, which is where the first tile will appear.
 */
internal fun tierDropCaret(
    drop: TierDrop,
    rows: List<TierRowBox>,
    items: List<TierItemBox>,
    options: TierLayoutOptions
): TierCaret? {
    val tierId = drop.tierId ?: return null
    val row = rows.firstOrNull { it.tierId == tierId } ?: return null
    val inRow = items.filter { it.y >= row.y && it.bottom <= row.bottom }
    if (inRow.isEmpty()) {
        return TierCaret(x = row.x + options.labelGutter, y = row.y, height = row.height)
    }

    val anchor = drop.beforeMediaId?.let { wanted -> inRow.firstOrNull { it.mediaId == wanted } }
    return if (anchor != null) {
        TierCaret(
            x = anchor.x - options.itemGap / 2f,
            y = anchor.y,
            height = anchor.height
        )
    } else {
        val last = inRow.last()
        TierCaret(
            x = last.right + options.itemGap / 2f,
            y = last.y,
            height = last.height
        )
    }
}

/**
 * The item under a press, or null for "not a poster".
 *
 * Scans in layout order and keeps the *last* hit, so a press on the seam between
 * two rows takes the lower one - which is what the finger was over, and the order
 * the tiles are stacked in.
 */
internal fun tierItemAt(items: List<TierItemBox>, x: Float, y: Float): Int? =
    items.lastOrNull {
        x >= it.x && x <= it.right && y >= it.y && y <= it.bottom
    }?.mediaId

/**
 * The tiles the board draws, given the one currently lifted under a finger.
 *
 * The dragged tile is drawn as a ghost that follows the pointer, not in the place
 * it started, so it must not be emitted as a child of the layout. Leaving it in
 * its old slot would draw a second copy underneath the ghost - the release would
 * then read as landing on a tile that never moved.
 *
 * A named function rather than an inline `filterNot` because **both** the content
 * lambda and the slot list have to apply the exact same rule. The measure policy
 * asserts the two stayed in step, so applying the skip to one and not the other is
 * not a cosmetic glitch: it is an exception that takes the screen down. It was
 * precisely that - the skip lived only in the content lambda, so the first
 * long-press threw `TierGeometry emitted N children for N+1 slots` and killed the
 * tier list the moment a drag began.
 */
internal fun visibleTierItems(
    items: List<TierItemBox>,
    skipMediaId: Int?
): List<TierItemBox> =
    if (skipMediaId == null) items else items.filterNot { it.mediaId == skipMediaId }

// ---------------------------------------------------------------------------
// Dragging near the edge of the screen
// ---------------------------------------------------------------------------

/**
 * How close to the top or bottom of the visible area a drag starts scrolling.
 *
 * A board is taller than the screen the moment somebody ranks fifty titles, which
 * is the whole point of ranking them, and the row they want is usually not the row
 * they picked the tile up from. Without this, reaching it means letting go,
 * scrolling, and picking the tile up again - and the tile is then filed by a drag
 * that started from the wrong row, which is how a list ends up subtly wrong rather
 * than obviously unfinished.
 */
internal const val TIER_DRAG_EDGE_DP = 72f

/** The fastest auto-scroll, in dp per frame, reached at the very edge. */
internal const val TIER_DRAG_MAX_SCROLL_DP = 9f

/**
 * How fast to scroll while a drag is held near an edge, signed: negative up.
 *
 * A *strength* rather than a distance, so the caller owns the frame rate and a
 * device at 120Hz scrolls at the same speed as one at 60Hz instead of twice as
 * fast. Zero in the middle of the screen and past the far edge band, and clamped to
 * full speed when the finger is dragged outside the visible area entirely - which
 * happens constantly, because a finger near the edge is usually over the app bar or
 * the navigation bar.
 */
internal fun tierDragScrollStrength(
    pointerY: Float,
    viewportTop: Float,
    viewportBottom: Float,
    edge: Float
): Float {
    if (edge <= 0f || viewportBottom <= viewportTop) return 0f
    val fromTop = pointerY - viewportTop
    val fromBottom = viewportBottom - pointerY
    return when {
        fromTop < edge -> -((edge - fromTop) / edge).coerceIn(0f, 1f)
        fromBottom < edge -> ((edge - fromBottom) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}

/**
 * Where the visible part of the screen is, and where the board sits inside it.
 *
 * Both halves are needed and neither can be derived from the other: the board
 * scrolls, the finger does not. A pointer's position *in the window* is what the
 * auto-scroll edge test is about, and it is `boardTop + the drag's own offset` -
 * so a board that has scrolled up by a hundred dp has moved the pointer a hundred
 * dp down the board without the finger moving at all.
 *
 * A plain holder of mutable state rather than three `rememberSaveable` values in
 * the screen, because these are read once per frame by a coroutine that must see
 * the *current* value and never re-subscribe to it.
 */
@Stable
internal class TierBoardViewport {
    /** Window-space top of the scrolling viewport, in px. */
    var viewportTop by mutableFloatStateOf(0f)
    var viewportHeight by mutableFloatStateOf(0f)

    /** Window-space top of the board, in px. Moves as the board scrolls. */
    var boardTop by mutableFloatStateOf(0f)

    val viewportBottom: Float get() = viewportTop + viewportHeight
}

/** The hint pill's text for the row under the finger, or null when nothing is picked up. */
internal fun tierDragHint(tiers: List<TierDefinition>, tierId: String?): String =
    if (tierId == null) {
        "Release here to take it out of the list"
    } else {
        val label = tiers.firstOrNull { it.id == tierId }?.label ?: tierId
        "Release to file under $label"
    }
