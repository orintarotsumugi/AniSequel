package com.anisequel.ui.screens.tier
import com.anisequel.ui.components.expressive.ExpressiveShapes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress

// ---------------------------------------------------------------------------
// The pool, drawn as a tray under the board.
//
// ## Why this is not part of `layoutTierList`
//
// The board's geometry is computed once in the domain and drawn twice - by Compose
// and by the SVG exporter - which is what stops an exported file disagreeing with
// the screen it came from. The tray is deliberately **not** in that layout, and the
// reason is the same one seen from the other side: the tray is not part of the
// artifact.
//
// A tier list is its rows. The pool of unranked titles is the workspace you rank
// them *from*, and it has no business in a file somebody posts to the internet.
// Putting it in the shared layout would have bought nothing and cost the export a
// row of titles nobody asked to publish.
//
// ## Why a drag starts here rather than on the board
//
// Filing a title used to need three taps: tap the poster, a sheet opens, pick a
// tier. Ranking forty of them is a hundred and twenty taps. Long-pressing a tray
// poster and dropping it on a row is one gesture, and it is the gesture a tier list
// is actually made of.
//
// ## Why the drop is resolved in window space
//
// The tray sits below a scrolling column and the board scrolls inside it, so the
// two have different origins and neither is the one a drop should be resolved
// against. The board publishes where its rows *are* - in window pixels - and the
// tray tests against that. Nothing here converts between coordinate spaces, and a
// drop cannot land in the wrong place because two origins disagreed about zero.
// ---------------------------------------------------------------------------

/** How close to a row's edge still counts as "on" it, in window pixels. */
internal const val TIER_TRAY_SNAP_SLOP = 24f

/** A tier row's top and bottom in window pixels, published by the board. */
internal data class TierRowBand(val tierId: String, val top: Float, val bottom: Float)

/**
 * The in-flight tray drag, hoisted so the ghost can be drawn over the board.
 *
 * A holder rather than three `remember`s in the screen because the tray writes it
 * and the screen's overlay reads it, and those are different composables: the
 * ghost has to be a *sibling* of the board, not a child of the tray, or the tray's
 * `Surface` would clip it away for the entire part of the gesture that matters.
 *
 * [windowX] and [windowY] are the finger; [liftedY] is the poster's top edge. The
 * two Y values are separate because a poster is carried by its middle - offsetting
 * by half its height is what stops the ghost appearing to jump upwards the instant
 * a drag begins.
 */
@Stable
internal class TierTrayDrag {
    var mediaId by mutableStateOf<Int?>(null)
    var windowX by mutableFloatStateOf(0f)
    var windowY by mutableFloatStateOf(0f)
    var liftedY by mutableFloatStateOf(0f)

    /**
     * Where in the tile the finger landed, in px, horizontally.
     *
     * Held so the ghost keeps the same grip the finger has rather than snapping to
     * the tile's left edge. The poster used to be tracked on Y alone, so a drag
     * pinned the ghost to the screen's left edge and the thing under the finger and
     * the thing being dragged were two hundred dp apart.
     */
    var grabOffsetX by mutableFloatStateOf(0f)

    /** The poster's left edge, in window px. */
    val posterX: Float get() = windowX - grabOffsetX
}

/**
 * Which tier row a release at this window position lands in, or null for "none".
 *
 * The same two-step resolution as the board's own `tierDropTarget`, for the same
 * reason and with the same consequence: a null answer means *un-file the title*,
 * which is a real instruction here rather than an error - dropping a poster back
 * into the tray is how a mis-drop is undone without opening anything.
 *
 * Written against the published bands rather than reusing the board's layout-space
 * function because this one is handed a pointer the board has never seen.
 * Converting the tray's pointer into the board's coordinate space to reuse that
 * function would mean trusting two origins to agree about a scroll offset; testing
 * the bands directly cannot be wrong in that way.
 */
internal fun tierRowAt(
    bands: List<TierRowBand>,
    windowX: Float,
    windowY: Float,
    leftEdge: Float,
    rightEdge: Float,
    slop: Float = TIER_TRAY_SNAP_SLOP
): String? {
    if (bands.isEmpty()) return null
    if (windowX < leftEdge || windowX > rightEdge) return null

    bands.firstOrNull { windowY >= it.top && windowY <= it.bottom }?.let { return it.tierId }

    // In a gap between two rows the nearest edge wins, because a near miss has one
    // obvious answer and refusing it would make the drag feel broken. Far from every
    // row is a deliberate miss - over the tray itself, or over the heading - and
    // guessing there would file a title under a judgement nobody made.
    val nearest = bands.minByOrNull { edgeDistance(windowY, it) } ?: return null
    return if (edgeDistance(windowY, nearest) <= slop) nearest.tierId else null
}

/** How far [y] is from the nearest edge of [band]; zero when it is inside. */
private fun edgeDistance(y: Float, band: TierRowBand): Float = when {
    y < band.top -> band.top - y
    y > band.bottom -> y - band.bottom
    else -> 0f
}

/** Height the tray reserves at the bottom of the screen, as the board's bottom padding. */
internal val TIER_TRAY_HEIGHT: Dp = 116.dp

/** Tray poster width. AniList covers are 2:3, so a tile is 1.5x this tall. */
internal val TIER_TRAY_ITEM_WIDTH: Dp = 56.dp

/** The poster's height, a constant so the hit test and the tile cannot disagree. */
internal val TIER_TRAY_ITEM_HEIGHT: Dp = TIER_TRAY_ITEM_WIDTH * 1.5f

/** Height of the tray's own label row, above the posters. */
private val TRAY_HEADER_HEIGHT: Dp = 18.dp

private val TRAY_ITEM_GAP = 6.dp
private val TRAY_PADDING = 12.dp

/**
 * The pool of unranked titles, under the board, each one draggable into a tier.
 *
 * Pinned to the bottom of the screen rather than placed after the last row, for two
 * reasons. It has to be reachable the whole time - a tray that scrolls out of view
 * is a tray you cannot drag *from* - and a fixed height is what lets the screen
 * reserve exactly that much padding under the board, so the last tier row can
 * always be scrolled clear of it instead of being covered by it.
 *
 * [resolveTier] is handed window coordinates and answers the tier a release there
 * would file into. It is a callback rather than the bands themselves because the
 * board is what knows where its rows ended up; this file has no business knowing
 * that, and a second copy of the answer here is how the two would drift.
 */
@Composable
internal fun TierTray(
    candidates: List<TierCandidate>,
    drag: TierTrayDrag,
    resolveTier: (windowX: Float, windowY: Float) -> String?,
    onDrop: (mediaId: Int, tierId: String?) -> Unit,
    onItemTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (candidates.isEmpty()) return

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    var trayWindowX by remember { mutableFloatStateOf(0f) }
    var trayWindowY by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .height(TIER_TRAY_HEIGHT)
            .testTag("tier_tray")
            .onGloballyPositioned { coordinates ->
                val window = coordinates.localToWindow(Offset.Zero)
                trayWindowX = window.x
                trayWindowY = window.y
            }
            .pointerInput(candidates, listState) {
                // Read outside the block and closed over by value: inside it the
                // receiver is the pointer-input scope, which carries no Density.
                val pxPerDp = density.density
                val tileWidthPx = TIER_TRAY_ITEM_WIDTH.value * pxPerDp
                val tileHeightPx = TIER_TRAY_ITEM_HEIGHT.value * pxPerDp
                val gapPx = TRAY_ITEM_GAP.value * pxPerDp
                val paddingPx = TRAY_PADDING.value * pxPerDp
                val postersTopPx = TRAY_HEADER_HEIGHT.value * pxPerDp

                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        // The LazyRow's own scroll is part of where a poster is.
                        // It used to be ignored, so after scrolling the pool
                        // sideways a long-press picked up whichever poster was that
                        // many slots from the *unscrolled* position - or nothing at
                        // all, past the end of the unscrolled row - and then filed
                        // that wrong title into the tier the drop landed on.
                        val scroll = listState.firstVisibleItemIndex * (tileWidthPx + gapPx) +
                            listState.firstVisibleItemScrollOffset
                        val slot = trayItemAt(
                            count = candidates.size,
                            pointerX = offset.x + scroll,
                            pointerY = offset.y - postersTopPx,
                            tileWidthPx = tileWidthPx,
                            tileHeightPx = tileHeightPx,
                            gapPx = gapPx,
                            paddingPx = paddingPx
                        )
                        if (slot == null) return@detectDragGesturesAfterLongPress

                        drag.mediaId = candidates[slot].mediaId
                        drag.windowX = trayWindowX + offset.x
                        drag.windowY = trayWindowY + offset.y
                        // Carried by its middle, not its top edge.
                        drag.liftedY = drag.windowY - tileHeightPx / 2f
                        drag.grabOffsetX = offset.x -
                            (paddingPx + slot * (tileWidthPx + gapPx) - scroll)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        // Both axes. `amount.x` used to be discarded, which is what
                        // pinned the ghost to the left edge for the whole gesture.
                        drag.windowX += amount.x
                        drag.windowY += amount.y
                        drag.liftedY += amount.y
                    },
                    onDragEnd = {
                        val mediaId = drag.mediaId
                        if (mediaId != null) {
                            // Read the live drag position rather than whatever the
                            // last composition saw: the final pointer move is not
                            // required to have recomposed anything, and a drop that
                            // lands a frame behind the finger is a drop into the
                            // wrong row.
                            onDrop(mediaId, resolveTier(drag.windowX, drag.windowY))
                        }
                        drag.mediaId = null
                    },
                    onDragCancel = { drag.mediaId = null }
                )
            }
    ) {
        Column {
            Text(
                text = "Unranked - hold and drag onto a tier",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .height(TRAY_HEADER_HEIGHT)
                    .padding(start = 12.dp, end = 12.dp)
            )
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(TRAY_ITEM_GAP),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TIER_TRAY_ITEM_HEIGHT)
            ) {
                items(candidates, key = { it.mediaId }) { candidate ->
                    TierTrayTile(candidate = candidate, onTap = { onItemTap(candidate.mediaId) })
                }
            }
        }
    }
}

/**
 * Which poster slot a long-press landed on, or null for the tray's empty space.
 *
 * An *index* rather than a media id, because the caller already holds the ordered
 * candidate list: an index into it cannot go stale, where a media id captured in a
 * gesture closure outlives the list that referred to it.
 *
 * Arithmetic rather than a scan, because a horizontal run of equal slots can only
 * be hit by at most one - there is no overlap to resolve and so no reason to walk.
 * A miss means "start no drag", the same answer the board gives for the same
 * reason: the tray is wider than its contents on most screens, and the space to
 * the right of the last poster is not a poster.
 */
private fun trayItemAt(
    count: Int,
    pointerX: Float,
    pointerY: Float,
    tileWidthPx: Float,
    tileHeightPx: Float,
    gapPx: Float,
    paddingPx: Float
): Int? {
    if (pointerY < 0f || pointerY > tileHeightPx) return null
    val index = ((pointerX - paddingPx) / (tileWidthPx + gapPx)).toInt()
    if (index < 0 || index >= count) return null
    val left = paddingPx + index * (tileWidthPx + gapPx)
    return if (pointerX >= left && pointerX <= left + tileWidthPx) index else null
}

/** One unranked poster in the tray. */
@Composable
private fun TierTrayTile(candidate: TierCandidate, onTap: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val placeholder = rememberVectorPainter(AppVectorIcons.List)

    Column(
        modifier = Modifier
            .width(TIER_TRAY_ITEM_WIDTH)
            // One interaction source, shared with the clickable below it. Sharing
            // is what cancels the press spring the moment the gesture becomes a
            // drag instead of leaving the tile squashed under a finger that has
            // already picked it up.
            .bouncyPress(pressedScale = 0.94f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onTap)
            .testTag("tier_tray_tile")
    ) {
        AsyncImage(
            model = candidate.coverUrl,
            contentDescription = candidate.title,
            modifier = Modifier
                .width(TIER_TRAY_ITEM_WIDTH)
                .height(TIER_TRAY_ITEM_HEIGHT)
                .clip(RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder
        )
    }
}

/**
 * The poster being dragged out of the tray, drawn over everything else.
 *
 * A sibling of the board rather than a child of the tray: the tray's `Surface`
 * clips its content, so a ghost drawn inside it would vanish the instant it left
 * the tray - which is the whole gesture.
 *
 * No caption and no score badge, matching the board's own ghost: this is a shape
 * being carried across a screen, not a card somebody is reading, and a caption
 * under a moving poster is unreadable at every speed it moves at.
 */
@Composable
internal fun TierTrayGhost(candidate: TierCandidate, modifier: Modifier = Modifier) {
    val placeholder = rememberVectorPainter(AppVectorIcons.List)

    Box(
        modifier = modifier
            .width(TIER_TRAY_ITEM_WIDTH)
            .height(TIER_TRAY_ITEM_HEIGHT)
            // Transformed in the draw phase, so the lift costs a repaint rather
            // than a recomposition - the rule AGENTS.md section 3 asks for.
            .graphicsLayer {
                scaleX = 1.15f
                scaleY = 1.15f
            }
            .clip(RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .testTag("tier_tray_ghost")
    ) {
        AsyncImage(
            model = candidate.coverUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder
        )
    }
}