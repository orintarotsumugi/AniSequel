package com.anisequel.ui.components.expressive

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The bar's pointer handling: the hold that starts a swipe, the travel that moves
 * the preview, and the decision that ends it.
 *
 * Split out of `ExpressiveNavigationBar.kt` because the two halves have different
 * reasons to change - this file is gesture timing and arithmetic, the other is a
 * capsule, its pill and its labels. The public surface is unchanged: the bar still
 * mounts one `holdThenSwipe` from its `pointerInput`, `navGestureDecision` stays
 * internal for `NavGestureDecisionTest`, and the constant lives here with the timer
 * that reads it.
 */

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
 * What a finished hold-and-swipe actually asked for: the destination to switch to,
 * or null for nothing.
 *
 * Extracted as a pure decision because the gesture itself cannot be unit tested
 * here: there is no `androidTest` source set and no `createComposeRule` in the JVM
 * suite, so the house pattern for pointer behaviour - `TierDragLogicTest`,
 * `MainTabsTest` - is to pull the arithmetic out and pin that.
 *
 * A drag belongs to the destination it ended on, and whether it *was* a drag is
 * answered by [travelledPx] - the distance the finger covered - and not by whether
 * the index changed on the way. The index only moves once a drag has passed a whole
 * destination width (95-126dp on a phone), so judging "did it move" by the index
 * classified every shorter drag as a still hold, and the ordinary hold-and-shove-left
 * on the profile ended up firing the inbox shortcut. An out-and-back drag travels
 * further than slop and settles where it started, so it is the no-op it always was:
 * nothing was asked for. A hold with no travel is the same no-op, and that is the
 * whole of what a hold does on its own - see the class documentation.
 */
internal fun navGestureDecision(
    startIndex: Int,
    settledIndex: Int,
    travelledPx: Float,
    touchSlopPx: Float
): Int? = if (travelledPx > touchSlopPx && settledIndex != startIndex) settledIndex else null

/**
 * Where a held drag ended, and how far the finger actually went.
 *
 * [travelledPx] is the sum of every move, which is not the same number as the
 * remainder `swipeSelection` banks against whole destination steps and zeroes at
 * either end of the bar: that one exists to do the tab arithmetic and cannot answer
 * whether the finger moved at all.
 */
private data class SwipeOutcome(val index: Int, val travelledPx: Float)

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
 *
 * A hold that never left the destination it began on commits nothing. It used to
 * fire a shortcut, and the main bar used that shortcut to jump to the inbox - which
 * made the gesture that *starts* a swipe also be a navigation, so a press held a
 * beat too long and released landed somewhere else. The lift and the haptic tick are
 * the whole acknowledgement now, and only travel can choose a destination.
 */
internal suspend fun AwaitPointerEventScope.holdThenSwipe(
    startIndex: () -> Int,
    lastIndex: Int,
    stepPx: Float,
    haptic: HapticFeedback,
    onPreview: (Int?) -> Unit,
    onSwitch: (Int) -> Unit
) {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (!awaitHold(down)) return

    // Read now, not before the hold: the bar can be switched by a tap while the
    // timer runs, and the swipe has to start from where the selection actually is.
    val start = startIndex()
    // The preview opens on the tab the hold began on, which is the route's own
    // selection: a hold with no movement must not switch anything.
    onPreview(start)
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

    // The gesture is decided once the finger lifts, in [navGestureDecision]: a drag
    // that travelled out and came back is the finger changing its mind rather than
    // asking for a destination, and a hold that never travelled asks for nothing.
    var settled: SwipeOutcome? = null
    try {
        settled = swipeSelection(
            down = down,
            startIndex = { start },
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
        val outcome = settled
        if (outcome != null) {
            navGestureDecision(
                startIndex = start,
                settledIndex = outcome.index,
                travelledPx = outcome.travelledPx,
                touchSlopPx = viewConfiguration.touchSlop
            )?.let(onSwitch)
        }
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
): SwipeOutcome? {
    var index = startIndex()
    var travel = 0f
    // Every move, either direction, counted once: see [SwipeOutcome].
    var travelled = 0f

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
        travelled += kotlin.math.abs(deltaX)
        if (!change.pressed) return SwipeOutcome(index, travelled)

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
