package com.anisequel.ui.components.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Tap to plan, hold to watch, swipe sideways for watching too.
 *
 * ## Why this is its own file
 *
 * It is the gesture half of `QuickAddPlanningButton`, and the two halves change for
 * different reasons: the button's face is layout and colour, this is pointer handling
 * and the hold's clock. The button file was over the project's size ceiling with both
 * in it, and every fix to the gestures landed in a 700-line file whose other subject
 * was what a card looks like.
 *
 * ## The state that is not composition state
 *
 * `holdJob` and the "a hold already committed" flag live here, inside the pointer
 * coroutine, rather than in composition: no frame reads either one. The flag is not
 * cosmetic - the hold handler snaps the fill back to 0f when it completes, so without
 * it the release after a completed hold looks like a quick tap and the same title gets
 * written to Planning immediately after Watching.
 *
 * ## Keyed on the title
 *
 * Both pointer loops are keyed on `mediaId` as well as on their own settings. The
 * callbacks arrive through `rememberUpdatedState` at the call site, but a block that
 * survives a different title also keeps its drag and hold state; restarting here is
 * what makes a recycled row start the gesture it is showing rather than the one it
 * used to show.
 */
internal fun Modifier.quickAddGestures(
    mediaId: Int,
    holdEnabled: Boolean,
    swipeEnabled: Boolean,
    holdDurationMs: Long,
    swipeThresholdPx: Float,
    holdProgress: Animatable<Float, AnimationVector1D>,
    dragOffset: Animatable<Float, AnimationVector1D>,
    haptic: HapticFeedback,
    coroutineScope: CoroutineScope,
    onHoldingChange: (Boolean) -> Unit,
    onAddToPlanning: () -> Unit,
    onAddToWatching: () -> Unit
): Modifier {
    if (!holdEnabled) return this

    val tapAndHold = pointerInput(mediaId, holdDurationMs) {
        var holdJob: Job? = null
        var holdCommitted = false

        detectTapGestures(
            onPress = {
                onHoldingChange(true)
                holdCommitted = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                holdJob?.cancel()
                holdJob = coroutineScope.launch {
                    holdProgress.snapTo(0f)
                    holdProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = holdDurationMs.toInt(),
                            easing = LinearEasing
                        )
                    )
                    // Hold reached 100% -> Trigger watching!
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHoldingChange(false)
                    holdCommitted = true
                    onAddToWatching()
                    holdProgress.snapTo(0f)
                }

                val released = tryAwaitRelease()
                onHoldingChange(false)

                if (released) {
                    // User released before hold finished
                    val elapsedFraction = holdProgress.value
                    holdJob.cancel()
                    coroutineScope.launch {
                        holdProgress.animateTo(0f, ExpressiveMotion.FastEffects)
                    }

                    // Quick tap -> Add to Planning. Only when the hold did not
                    // already commit: see `holdCommitted` above.
                    if (elapsedFraction < 0.25f && !holdCommitted) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAddToPlanning()
                    }
                } else {
                    holdJob.cancel()
                    coroutineScope.launch { holdProgress.snapTo(0f) }
                }
            }
        )
    }

    val swipe = if (swipeEnabled) {
        Modifier.pointerInput(mediaId) {
            detectDragGestures(
                onDragEnd = {
                    coroutineScope.launch {
                        if (dragOffset.value >= swipeThresholdPx) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddToWatching()
                        }
                        dragOffset.animateTo(0f, ExpressiveMotion.PressSpatial)
                    }
                },
                onDragCancel = {
                    coroutineScope.launch {
                        dragOffset.animateTo(0f, ExpressiveMotion.PressSpatial)
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val newOffset = (dragOffset.value + dragAmount.x)
                        .coerceIn(0f, swipeThresholdPx * 1.5f)
                    coroutineScope.launch { dragOffset.snapTo(newOffset) }
                }
            )
        }
    } else {
        Modifier
    }

    return this.then(tapAndHold).then(swipe)
}
