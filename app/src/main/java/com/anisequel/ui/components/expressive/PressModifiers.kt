package com.anisequel.ui.components.expressive

/**
 * The two press gestures that read [ExpressiveMotion]'s springs.
 *
 * Split out of `ExpressiveMotion.kt` so that file stays under the project's
 * 700-line ceiling after the sixth motion style landed; the tokens themselves are
 * still there, and nothing here changed behaviour. Staying in the same package is
 * deliberate - every call site imports `...expressive.bouncyPress` by name, so a
 * different package would have meant editing two dozen files to move one function.
 */

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Modifier that applies a tactile bouncy spring press animation in the Draw phase.
 * Does not trigger composition or layout passes for maximum 120fps smoothness.
 *
 * ## Install it before the clip and the background
 *
 * A `graphicsLayer` transforms only what is drawn inside it. Installed after
 * `.clip(...).background(...)` - which a score of call sites had done - the press
 * layer contains the label but not the pill, so a tap squashed the text inside a
 * surface that did not move. It belongs immediately after the size, before the
 * shape and the fill, so the whole control answers the finger.
 *
 * Applied at two dozen call sites, so this is the most widely instantiated
 * modifier in the app - which is why the animated value is read inside the
 * [graphicsLayer] block rather than passed to it. Reading a `State` inside the
 * layer block invalidates only the draw phase, so a press animates without
 * recomposing the composable it is attached to. That matters most on
 * `SequelCard`, where one of these sits on every row of a `LazyColumn`.
 *
 * The `composed {}` wrapper is what costs this its remaining recomposition: a
 * composable factory modifier is never skipped, so each call site gets a scope
 * that re-runs when its own state changes. Converting this to a
 * `ModifierNodeElement` would remove it, and is the single biggest remaining
 * animation win in the app - see TO-DO.md.
 *
 * ## Pass the `clickable`'s own `InteractionSource`, and this stops listening
 *
 * Two press sources were running at once. The `MutableInteractionSource` a
 * caller passes in is read through `collectIsPressedAsState`, but when no source
 * is passed the fallback was a source nothing ever emitted into - so
 * `isInteractionPressed` was permanently `false` and *all* of the press feedback
 * came from the raw `pointerInput` loop underneath. That loop tracked "is a
 * pointer down" and nothing else: it never asked whether the gesture had turned
 * into a scroll, so flicking a `LazyColumn` with a finger resting on a card left
 * that card squashed for the whole fling, and released only once every pointer
 * had lifted. It also cost a second pointer-input node per element - on
 * `ActivityCard`, one extra coroutine per row of a scrolling feed.
 *
 * So when a caller supplies its source, the loop is not installed at all: the
 * `clickable` that shares that source emits press on down and *cancels* it the
 * moment the gesture is claimed as a drag, which is the behaviour the loop was
 * approximating badly. The loop stays for the call sites that have no source to
 * share - a bare `Button` does not expose one - so their press feedback is
 * unchanged rather than silently lost.
 *
 * ## The loop's remaining flaw, and what it now does about it
 *
 * Without a source to read, the loop has to decide for itself whether a gesture
 * is still a press, and "a pointer is down" is the wrong answer. A fling into
 * the end of a list holds the pointer down for its whole length, so anything
 * under the finger stayed depressed and then sprang back - which reads as the
 * row reacting twice, once on the way in and once on the rebound. The loop now
 * releases the press the moment the pointer travels further than touch slop,
 * because past slop the gesture has become a drag and belongs to the scrollable.
 *
 * Touch slop rather than `isConsumed` on purpose: a `clickable` sitting on the
 * same element consumes its own events, so consumption cannot distinguish "this
 * became a scroll" from "this control handled it". A long press - the heart's
 * press-and-hold for the likers sheet - does not move, so it survives.
 */
fun Modifier.bouncyPress(
    pressedScale: Float = 0.94f,
    interactionSource: InteractionSource? = null
): Modifier = composed {
    val ownedSource = remember { MutableInteractionSource() }
    val effectiveSource = interactionSource ?: ownedSource
    val isInteractionPressed by effectiveSource.collectIsPressedAsState()
    val isPointerPressed = remember { mutableStateOf(false) }

    // The fallback is only consulted on the no-source path. Reading a state that
    // nothing writes is harmless, and keeping the allocation unconditional keeps
    // this composable's `remember` calls in a fixed order.
    val isPressed = isInteractionPressed || (interactionSource == null && isPointerPressed.value)

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        // Two specs chosen by direction, not one.
        //
        // A single spring has to treat the press and the release identically, so
        // the element squashed and then merely returned - which reads as a fade
        // rather than as a button with weight. The asymmetry has to come from two
        // specs, because a shared `dampingRatio` cannot express "no overshoot
        // down, a little overshoot back", and the `bounce` parameter that would
        // have said it does not exist in the Compose version this project pins
        // (see `PressSpatialOut`). So the way down is tight and cannot flicker
        // below the pressed scale, and the way back overshoots slightly past rest
        // and settles.
        animationSpec = if (isPressed) {
            ExpressiveMotion.PressSpatialIn
        } else {
            ExpressiveMotion.PressSpatialOut
        },
        label = "bouncy_press_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (interactionSource == null) {
                Modifier.pointerInput(Unit) {
                    // A tap does not travel; a scroll starts by travelling. Watching
                    // the pointer leave touch slop is therefore the cheapest honest
                    // signal that the gesture stopped being a press, and it is what
                    // stops the loop holding an element down for a whole fling.
                    val touchSlop = viewConfiguration.touchSlop
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPointerPressed.value = true
                            try {
                                // The loop follows the finger that started the press,
                                // not "any pointer on screen". A second finger placed
                                // elsewhere used to keep this loop alive after the
                                // first had lifted, so the element stayed squashed
                                // under a hand that was no longer touching it - and
                                // the press only released once every finger was up.
                                var trackedPressed = true
                                while (trackedPressed) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes
                                        .firstOrNull { it.id == down.id }
                                        // The tracked pointer is gone from the event:
                                        // treat it as lifted rather than waiting for a
                                        // finger that will never report again.
                                        ?: break
                                    trackedPressed = change.pressed
                                    // Squared distance against a squared slop: the
                                    // comparison never needs the square root, and
                                    // `Offset.getDistance` is an extension in a
                                    // package this file does not otherwise import
                                    // from.
                                    val dx = change.position.x - down.position.x
                                    val dy = change.position.y - down.position.y
                                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                        // Past slop: this is a drag, so the
                                        // scrollable has it now and this is no longer
                                        // a press.
                                        isPointerPressed.value = false
                                    }
                                }
                            } finally {
                                isPointerPressed.value = false
                            }
                        }
                    }
                }
            } else {
                Modifier
            }
        )
}

/**
 * Modifier that adds an expressive spring-physics press animation and triggers onHold on long press.
 *
 * Built on [bouncyPress] rather than repeating it, so the two press gestures
 * share one animation body and one spring token.
 */
fun Modifier.expressiveHoldGesture(
    onHold: () -> Unit,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val isPressed = remember { mutableStateOf(false) }

    // Read through state rather than captured by `pointerInput(Unit)`, which never
    // restarts. A lazy slot reused for another title kept the lambda from its first
    // composition, so holding a recycled card opened the media it used to show - the
    // same hazard `InfoHero` documents for its title.
    val currentOnHold by rememberUpdatedState(onHold)
    val currentOnClick by rememberUpdatedState(onClick)

    val scale by animateFloatAsState(
        targetValue = if (isPressed.value) 0.96f else 1f,
        animationSpec = if (isPressed.value) {
            ExpressiveMotion.PressSpatialIn
        } else {
            ExpressiveMotion.PressSpatialOut
        },
        label = "expressive_hold_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed.value = true
                    tryAwaitRelease()
                    isPressed.value = false
                },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnHold()
                },
                onTap = {
                    currentOnClick?.invoke()
                }
            )
        }
}
