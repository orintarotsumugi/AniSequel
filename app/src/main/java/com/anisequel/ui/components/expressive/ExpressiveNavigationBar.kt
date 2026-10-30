package com.anisequel.ui.components.expressive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.withTimeoutOrNull

/** One destination of [ExpressiveNavigationBar]. */
data class NavigationDestination(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    /**
     * The destination's picture, when its identity is an image rather than a
     * glyph.
     *
     * Only the main bar's Profile tab sets this, to the signed-in viewer's own
     * avatar: the tab is "you", and the picture is the recognisable half of
     * that. Null everywhere else - while the avatar is still loading, and in the
     * demo, which has no session - and [icon] carries the destination instead.
     *
     * The picture takes exactly the icon's slot rather than a larger one,
     * because the bar's width arithmetic (`SELECTED_WEIGHT`) is computed against
     * a 22dp icon, a 9dp gap and the label: a bigger image would come straight
     * out of the selected label's fit, which is the width that arithmetic exists
     * to protect.
     */
    val iconUrl: String? = null
)

/**
 * A floating capsule navigation bar where the selected destination grows a label.
 *
 * ## Why not `NavigationBar`
 *
 * The stock component gives every destination an equal slot with an icon over a
 * fixed label. This screen already has an [ExpressiveTabBar] in its top app bar,
 * and putting a `NavigationBar` underneath it would draw the same control twice
 * at two sizes and make the two read as the same thing. The distinction is
 * deliberate: the top bar switches between tabs of one page, the floating
 * capsule switches between pages, and the growing label is what says so at a
 * glance - the unselected destinations are icons only, so there is exactly one
 * place on the bar where the current page is spelled out.
 *
 * ## Why the label animates rather than switching
 *
 * `AnimatedVisibility` over the label makes the capsule's width spring open and
 * shut, because a `Row` is measured from its content and the label's own width
 * is what is animating. Animating a stored width state instead would have needed
 * the width of the text before the text was laid out - one frame of the wrong
 * width, or a measured-width side channel through a subcomposition.
 *
 * The selection *scale* and press *scale* are multiplied together in one
 * `graphicsLayer`, so both are draw-phase only and neither recomposes this bar -
 * it sits over a list that is being scrolled underneath it.
 */
/**
 * How much of the bar's width the selected destination may claim.
 *
 * Picked so the **whole** label fits at the default font scale, which is the only
 * version of this that is worth having - a selected pill reading "Activ..." is
 * worse than no label at all, because it costs the width and still does not say
 * what the tab is.
 *
 * ## The arithmetic, because it is not a taste value
 *
 * Tuned on the five-slot bar the arithmetic below is worked out for - the
 * profile's tab row, before the Library left it for the main bar. Both bars this
 * component now draws are narrower (the profile's four, the main bar's three),
 * which only gives the selected label more room, so the weight stands.
 *
 * On the narrowest phone worth supporting (360dp) the bar's inner width is
 * 360 - 32 (this Row's own padding) - 10 (the pill's inner padding) - 8 (four 2dp
 * gaps) = 310dp to share. At a weight of 2.3 against four unselected items at 1.0:
 *
 * - selected slot = 310 x 2.3/6.3 = **113dp**, leaving 113 - 22 (item padding)
 *   - 22 (icon) - 9 (gap) = **60dp** of label, against the ~55dp "Activity"
 *   needs at `labelLarge`;
 * - each unselected item = 310/6.3 = **49dp**, which clears the 48dp minimum tap
 *   target with a dp to spare.
 *
 * At the old 1.6 the same phone gave the label 29dp, so every destination
 * ellipsized - "Activity" was the worst case but none of them were legible. The
 * weight was tuned against the label's *clipping* rather than its fitting.
 *
 * Past ~2.4 the unselected items drop under the 48dp minimum, and `defaultMinSize`
 * starts fighting the weight rather than helping it, so this is the ceiling as
 * well as the floor. At extreme font scales no fixed bar of this width can spell
 * every label; the `TextOverflow.Ellipsis` is the graceful degradation for that,
 * and the semantics still carry the full name to a screen reader.
 */
private const val SELECTED_WEIGHT = 2.3f

/**
 * How long the bar has to be held before a drag starts switching tabs.
 *
 * Short on purpose: the hold is an acknowledgement, not a decision - the finger
 * has already landed on the bar, and half a second of nothing reads as a missed
 * tap. 220ms is long enough that a tap (well under the 100ms a deliberate press
 * lasts) never turns into a swipe, and short enough that the bar lifts while the
 * finger is still on it. Not a `ViewConfiguration` value because the platform's
 * long-press timeout is the one this gesture exists to be quicker than.
 */
private const val HOLD_TO_SWIPE_MILLIS = 220L

/**
 * ## Holding the bar and swiping
 *
 * A hold on the bar lifts it, and from then on the finger drags the selection: one
 * destination per destination-width of travel, with a tick each time it moves.
 * Tapping is untouched - the drag only starts after [HOLD_TO_SWIPE_MILLIS], and
 * while it is running the items' clicks are disabled so releasing the finger after
 * a swipe cannot also fire the tap that was under it.
 *
 * The drag moves a **preview**, and the page changes once, when the finger lifts.
 * It used to call the route change on every step, which meant a swipe across three
 * tabs popped and pushed the graph three times: three page transitions played under
 * one finger, and the two tabs that were only passed through still composed and
 * started loading their data before being replaced. The bar is the thing the finger
 * is touching, so the bar is what follows it; the page underneath waits for the
 * destination to be chosen. Releasing on the tab the swipe started from is not a
 * route change at all.
 *
 * The hold is hand-rolled rather than `detectDragGesturesAfterLongPress`, which
 * waits out the platform's `longPressTimeoutMillis` - 500ms on every device this
 * app supports. On a bar 52dp tall that is long enough for the finger to feel like
 * the gesture did not register, and it was reported as exactly that. The platform
 * timeout is not ours to change, so the timer here is.
 *
 * The direction is the one a strip of pages has rather than the one a list has:
 * dragging to the right pulls the pages right, so the selection moves *left* to the
 * previous tab. That is the same direction as a horizontal pager, which is the
 * gesture people will have used before this one.
 *
 * The travel is measured in pixels against one destination's share of the measured
 * bar, not against a fixed dp: the selected pill is wider than the others, so a
 * fixed threshold would move two tabs in one gesture on a phone and feel stuck on a
 * tablet. The accumulator and the previewed index both live inside the pointer
 * handler. The preview is written back so the pill can follow the finger, but the
 * handler must not read it back: two steps inside one frame would then both start
 * from the same index and move one destination instead of two.
 */
@Composable
fun ExpressiveNavigationBar(
    destinations: List<NavigationDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // The bar's measured width, which the swipe's step is derived from. Zero until
    // the first layout; a gesture cannot start before then anyway.
    var barWidth by remember { mutableStateOf(0) }

    // The destination the held finger is over, or null when no gesture is running.
    // Null rather than a sentinel index so "holding" and "the previewed tab" cannot
    // disagree: the bar is holding exactly when there is a preview.
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val holding = previewIndex != null
    // While the finger is down the bar shows where it would go; the page underneath
    // does not move until it lifts. See the hold-and-swipe note above.
    val shownIndex = previewIndex ?: selectedIndex

    // Read inside the pointer handler rather than captured: the handler is keyed on
    // the destination count, and a key change mid-gesture would cancel it. These
    // are the values that legitimately change while a finger is down.
    val currentIndex by rememberUpdatedState(selectedIndex)
    val selectTab by rememberUpdatedState(onSelect)
    val stepPx by rememberUpdatedState(
        // Never zero: the step is the divisor of the travel accumulator, and a
        // bar measured at zero width (before its first layout) would make the
        // swipe loop below spin forever.
        if (destinations.isEmpty()) 1f
        else (barWidth / destinations.size).toFloat().coerceAtLeast(1f)
    )
    val lastIndex = destinations.lastIndex

    // Lifting the bar is the hold's whole visual: the finger is on the bar and the
    // bar acknowledges it before anything has moved. It is a draw-phase scale, so
    // holding does not relayout the page underneath.
    val holdScale by animateFloatAsState(
        targetValue = if (holding) 1.035f else 1f,
        animationSpec = if (holding) ExpressiveMotion.PressSpatialOut
        else ExpressiveMotion.PressSpatialIn,
        label = "nav_bar_hold_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("expressive_navigation_bar"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 560.dp)
                // 16 rather than 20: the four dp are handed straight to the
                // selected pill, and the selected pill's whole job is to fit a
                // word. See SELECTED_WEIGHT for what that buys.
                .padding(horizontal = 16.dp, vertical = 12.dp)
                // Elevated rather than outlined: it floats over scrolling
                // content, and a hairline border on a shape with a 50% radius
                // sits exactly on the curve's own edge and reads as a smudge.
                .shadow(elevation = 8.dp, shape = ExpressiveShapes.pill, clip = false)
                .clip(ExpressiveShapes.pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(5.dp)
                .onSizeChanged { barWidth = it.width }
                .graphicsLayer {
                    scaleX = holdScale
                    scaleY = holdScale
                }
                .pointerInput(destinations.size) {
                    awaitEachGesture {
                        holdThenSwipe(
                            startIndex = { currentIndex },
                            lastIndex = lastIndex,
                            stepPx = stepPx,
                            haptic = haptic,
                            onPreview = { previewIndex = it },
                            onSwitch = selectTab
                        )
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = index == shownIndex
                NavigationItem(
                    destination = destination,
                    selected = selected,
                    // A tap is ignored while the bar is being held: the long press
                    // and the tap end on the same finger-up, so without this a
                    // swipe that ends on a destination would also tap it and put
                    // the selection back where the gesture started.
                    enabled = !holding,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    },
                    // The selected item takes the extra width rather than every
                    // item an equal share.
                    //
                    // Equal shares look deliberate and are wrong the moment one
                    // item has to spell its label: five destinations split evenly
                    // leaves no room for a 22dp icon, a 9dp gap and "Activity" side
                    // by side, and the label was clipped to nothing. Since only one
                    // item is ever labelled, giving that item the extra width keeps
                    // the bar's overall width stable - the Row fills its parent, so
                    // this redistributes within a fixed bar rather than resizing it
                    // - while leaving the four icon-only items their tap target.
                    //
                    // [animatedItemWeight], not the constant: the redistribution
                    // itself was one frame, so the four unselected items jumped to
                    // their new places in the same frame the tap landed and the label
                    // then unfurled into a slot that had already finished growing.
                    // The width is the pill's only morph, so it moves.
                    modifier = Modifier.weight(animatedItemWeight(selected))
                )
            }
        }
    }
}

/**
 * One hold-then-swipe gesture, start to finish.
 *
 * Split out of the bar's `pointerInput` because the whole gesture inline was one
 * function doing four jobs - timing the hold, cancelling it, accumulating travel
 * and switching tabs - and CodeFactor read it as a complex method. The pieces are
 * separate for the same reason the code reads better in pieces: each one has a
 * single thing that can be wrong with it.
 *
 * The two cancels are the reason this is not `detectDragGesturesAfterLongPress`:
 * the finger lifting is a tap and travel past touch slop is a drag that never
 * asked to swipe, and both have to abandon the hold *before* the timer expires.
 *
 * [onSwitch] is called once, on release, with the previewed destination - not once
 * per step. A cancellation (the tier tab being switched off while the bar is held,
 * or the screen leaving) commits nothing and only clears the preview, so an
 * interrupted gesture cannot navigate somewhere the finger never settled on.
 */
private suspend fun AwaitPointerEventScope.holdThenSwipe(
    startIndex: () -> Int,
    lastIndex: Int,
    stepPx: Float,
    haptic: HapticFeedback,
    onPreview: (Int?) -> Unit,
    onSwitch: (Int) -> Unit
) {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (!awaitHold(down)) return

    // The preview opens on the tab the hold began on, which is the route's own
    // selection: a hold with no movement must not change anything.
    onPreview(startIndex())
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    var settled: Int? = null
    try {
        settled = swipeSelection(
            down = down,
            // Read now, not before the hold: the bar can be switched by a tap
            // while the timer runs, and the swipe has to start from where the
            // selection actually is.
            startIndex = startIndex,
            lastIndex = lastIndex,
            stepPx = stepPx,
            haptic = haptic,
            onPreview = onPreview
        )
    } finally {
        // `finally`, not after the call: a key change (the tier tab being switched
        // off while the bar is held) cancels this coroutine, and a preview left
        // open would leave every item disabled and the pill on a page that is not
        // showing.
        //
        // The commit happens *before* the preview closes so the bar never flashes
        // the tab the swipe started from between the two writes.
        val destination = settled
        if (destination != null) onSwitch(destination)
        onPreview(null)
    }
}

/**
 * Waits out [HOLD_TO_SWIPE_MILLIS] and says whether the hold survived it.
 *
 * Returns `false` for both things that cancel a hold - the finger lifting, and
 * travel past touch slop - so the caller has one branch rather than two. The
 * timeout winning is `withTimeoutOrNull` returning `null`, which is the only
 * `null` this returns.
 */
private suspend fun AwaitPointerEventScope.awaitHold(down: PointerInputChange): Boolean {
    val cancelled = withTimeoutOrNull(HOLD_TO_SWIPE_MILLIS) {
        var change = down
        while (
            change.pressed &&
            (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
        ) {
            change = awaitPointerEvent()
                .changes
                .firstOrNull { it.id == down.id }
                ?: return@withTimeoutOrNull true
        }
        true
    }
    return cancelled == null
}

/**
 * Drags the previewed destination while the held finger moves, one per [stepPx].
 *
 * Returns the destination under the finger when it lifts, or `null` when the
 * pointer disappeared without a release - which is a cancelled gesture, and one
 * that must not navigate.
 *
 * The release is consumed as well as the moves: the item under the finger was
 * pressed when the hold began, and a release it is allowed to see would fire the
 * tap the swipe replaced.
 */
private suspend fun AwaitPointerEventScope.swipeSelection(
    down: PointerInputChange,
    startIndex: () -> Int,
    lastIndex: Int,
    stepPx: Float,
    haptic: HapticFeedback,
    onPreview: (Int) -> Unit
): Int? {
    var index = startIndex()
    var travel = 0f

    fun moveTo(next: Int) {
        if (next == index) return
        index = next
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onPreview(next)
    }

    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return null
        // Read the delta *before* consuming. `consume()` marks the change as
        // position-consumed, and from that moment `positionChange()` is defined
        // to return `Offset.Zero` - so the old order here (consume, then read)
        // accumulated exactly zero travel for the whole gesture. That is the bug
        // that made the bar lift on a hold and then ignore the drag: every
        // release returned the starting tab and the swipe cancelled itself.
        val deltaX = change.positionChange().x
        change.consume()
        if (!change.pressed) return index

        travel += deltaX

        // Positive travel is a drag to the right, which pulls the previous
        // destination in from the left.
        while (travel >= stepPx && index > 0) {
            travel -= stepPx
            moveTo(index - 1)
        }
        while (travel <= -stepPx && index < lastIndex) {
            travel += stepPx
            moveTo(index + 1)
        }
        // Do not bank travel against an end that cannot move: a long drag at the
        // first tab would otherwise turn around and jump two tabs back on the way
        // home.
        if (index == 0 && travel > 0f) travel = 0f
        if (index == lastIndex && travel < 0f) travel = 0f
    }
}

/**
 * The selected item's share of the bar, sprung rather than stepped.
 *
 * This is the pill's half of the selection morph - `Modifier.weight` is the only
 * width the item has, so the container does not resize, it redistributes - and it
 * uses the same physics as the label's own unfurl: [ExpressiveMotion.FastSpatialSize]
 * is this spring against an `IntSize`, this is it against the `Float` a weight
 * takes. Both follow the Motion style setting, so `INSTANT` still snaps.
 *
 * The layout cost is per-frame while it runs, which is inherent to animating a
 * weight, and it is the one part of the bar that has nothing else to draw.
 */
@Composable
private fun animatedItemWeight(selected: Boolean): Float {
    val weight by animateFloatAsState(
        targetValue = if (selected) SELECTED_WEIGHT else 1f,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "nav_item_weight"
    )
    return weight
}

@Composable
private fun NavigationItem(
    destination: NavigationDestination,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // One shared interaction source with the ripple suppressed: a ripple on a
    // capsule that is simultaneously springing wider and changing colour fights
    // the spring, and the capsule's own scale is the press feedback.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.PressSpatial,
        label = "nav_item_press_scale"
    )
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = ExpressiveMotion.SelectionScale,
        label = "nav_item_selection_scale"
    )
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nav_item_container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nav_item_content"
    )

    Row(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                val combined = pressScale * selectionScale
                scaleX = combined
                scaleY = combined
            }
            .clip(ExpressiveShapes.pill)
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            // An icon-only item is otherwise 22dp wide plus 10dp of padding, which
            // is under the 48dp minimum tap target and gets found by a fingertip
            // rather than by the user aiming at it.
            .defaultMinSize(minWidth = 48.dp)
            // 11 rather than 13, for the same reason as the bar's own padding:
            // every dp here is a dp the selected item's label does not get.
            .padding(horizontal = 11.dp)
            .semantics {
                this.selected = selected
                role = Role.Tab
            }
            .testTag(destination.testTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A destination whose identity is a picture - the viewer's own avatar on
        // the main bar's Profile tab - draws it in the icon's slot. Not tinted by
        // the item's content colour, because it is a photograph rather than a
        // glyph; circle-clipped, because the bar's own shapes are pills and a
        // square picture inside one reads as a rendering mistake.
        if (destination.iconUrl != null) {
            AsyncImage(
                model = destination.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        } else {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(22.dp)
            )
        }

        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(animationSpec = ExpressiveMotion.FastSpatialSize) +
                fadeIn(ExpressiveMotion.FastEffects),
            exit = shrinkHorizontally(animationSpec = ExpressiveMotion.FastSpatialSize) +
                fadeOut(ExpressiveMotion.FastEffects)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}