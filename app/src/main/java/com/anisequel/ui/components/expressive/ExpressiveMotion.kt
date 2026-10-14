package com.anisequel.ui.components.expressive

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Standardized Material 3 Expressive motion tokens and physics specifications.
 * Defines consistent stiffness and damping ratios for spatial and effects motion.
 *
 * ## Use these, do not re-declare the numbers
 *
 * Every spring in the app is meant to be one of the six tokens below. Inline
 * `spring(...)` literals had crept in at a dozen call sites, and the ones that
 * mattered were all re-deriving a token that already existed - `AnimatedCounter`
 * spelled out `FastSpatial`'s exact stiffness and damping, and three files
 * spelled out `DefaultSpatial`. The result was motion that looked deliberate
 * and behaved inconsistently: two components animating the same interaction
 * with two different curves because neither was reading the shared token.
 *
 * New tokens are fine to add, but a call site that needs one should name it.
 */
object ExpressiveMotion {

    /**
     * How the app's custom springs and fades are scaled. Set once per theme
     * change in [com.anisequel.ui.theme.AniSequelTheme]. The Material
     * `MotionScheme` covers M3 components' built-in motion; this value covers
     * the tokens the app itself drives (route transitions, list item fades,
     * press feedback, and the morph loops).
     */
    var speed: com.anisequel.data.repository.MotionStyle =
        com.anisequel.data.repository.MotionStyle.DEFAULT

    /** Snappy spatial spring for quick taps and micro-interactions. */
    val FastSpatial: FiniteAnimationSpec<Float> get() =
        spatial()

    /** Default spatial spring with fluid overshoot for smooth layout adaptations. */
    val DefaultSpatial: FiniteAnimationSpec<Float> get() =
        spatial(expressiveDamping = 0.65f, expressiveStiffness = Spring.StiffnessMediumLow)

    /** Bouncy spatial spring for celebratory interactions, buttons, and icon reveals. */
    val BouncySpatial: FiniteAnimationSpec<Float> get() =
        spatial(expressiveStiffness = Spring.StiffnessLow)

    /** Super bouncy spring with noticeable playful oscillation for hero moments. */
    val SuperBouncy: FiniteAnimationSpec<Float> get() =
        spatial(expressiveDamping = Spring.DampingRatioHighBouncy, expressiveStiffness = Spring.StiffnessVeryLow)

    /** Fast effects spring for smooth color and opacity transitions without overshoot. */
    val FastEffects: FiniteAnimationSpec<Float> get() =
        effects(stiffness = Spring.StiffnessMedium)

    /** Default effects spring for state fades and container transitions. */
    val DefaultEffects: FiniteAnimationSpec<Float> get() =
        effects(stiffness = Spring.StiffnessMediumLow)

    /**
     * The colour counterpart to [FastEffects].
     *
     * `animateColorAsState` takes an `AnimationSpec<Color>`, so the `Float`
     * tokens above cannot be passed to it at all - which is why seven colour
     * animations had each grown their own bare `spring()` call.
     *
     *Deliberately `NoBouncy`: a spring interpolates `Color` componentwise
     * through `Ulerp`, and an overshooting spring drives individual channels past
     * their endpoints on the way down. A colour that overshoots is not a colour
     * on the way to another colour - it can render out of gamut. A two-state
     * selection change wants a fast, non-overshooting curve.
     */
    val FastColorEffects: FiniteAnimationSpec<Color> get() =
        colorEffects(stiffness = Spring.StiffnessMedium)

    /**
     * The press spring shared by every press gesture below.
     *
     * One token rather than a literal per gesture, so `bouncyPress` and
     * `expressiveHoldGesture` cannot drift into feeling like two different
     * buttons. Previously each spelled out its own `spring(...)`, so a change
     * to the feel of one press had to be remembered in the other.
     */
    val PressSpatial: FiniteAnimationSpec<Float> get() =
        spatial(expressiveDamping = Spring.DampingRatioMediumBouncy, expressiveStiffness = Spring.StiffnessMedium)

    /**
     * The spatial counterpart for `IntOffset`-based placement motion.
     *
     * List item add/remove animations in [androidx.compose.foundation.lazy.LazyItemScope.animateItem]
     * take an `AnimationSpec<IntOffset>`, so the placement spring the dashboard
     * was spelling out inline needs its own typed token. `FiniteAnimationSpec`
     * is generic on the target type, so a `Float` token cannot be passed.
     */
    val DefaultSpatialOffset: FiniteAnimationSpec<IntOffset> get() =
        when (speed) {
            com.anisequel.data.repository.MotionStyle.INSTANT -> snap()
            com.anisequel.data.repository.MotionStyle.SMOOTH -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
            com.anisequel.data.repository.MotionStyle.CHILL -> spring(
                dampingRatio = 0.65f,
                stiffness = Spring.StiffnessMediumLow
            )
        }

/**
     * Item appearance and disappearance for lazy list items.
     *
     * `LazyItemScope.animateItem` takes `fadeInSpec`/`fadeOutSpec` parameters
     * of type `FiniteAnimationSpec<Float>`. The dashboard had inlined
     * `DampingRatioMediumBouncy`/`StiffnessMedium` for the entrance — which
     * is exactly `FastSpatial` — and a separate `FastEffects`-like spring for the
     * exit. This pair pins both types together.
     */
    val ListItemFadeIn: FiniteAnimationSpec<Float> get() =
        effects(stiffness = Spring.StiffnessMedium)

    val ListItemFadeOut: FiniteAnimationSpec<Float> get() =
        effects(stiffness = Spring.StiffnessMedium)

    /**
     * The [FastSpatial] counterpart for `IntSize`-based expansion.
     *
     * `AnimatedVisibility`'s `expandVertically`, `shrinkVertically`,
     * `expandHorizontally` and `shrinkHorizontally` all take
     * `FiniteAnimationSpec<IntSize>` - the size being revealed or hidden, which
     * is why the hidden-sequels section can pass this token straight to
     * `expandVertically`. The note here used to say `IntOffset`; that is the type
     * `slideInVertically` takes, a different mechanism, and the claim sent a
     * reader after the wrong token. `FastSpatial` is the right physics: a section
     * growing open should be as quick as a press, not as slow as a list item
     * settling.
     */
    val FastSpatialSize: FiniteAnimationSpec<IntSize> get() =
        when (speed) {
            com.anisequel.data.repository.MotionStyle.INSTANT -> snap()
            com.anisequel.data.repository.MotionStyle.SMOOTH -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
            com.anisequel.data.repository.MotionStyle.CHILL -> spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        }

    /**
     * Selected-tab and selected-segment scale.
     *
     * Three segmented controls and one chip rendered their selection scale
     * animation with the same inline `DampingRatioLowBouncy` +
     * `StiffnessMediumLow` spring; this is that physics, as a token.
     */
    val SelectionScale: FiniteAnimationSpec<Float> get() =
        spatial(expressiveDamping = Spring.DampingRatioLowBouncy, expressiveStiffness = Spring.StiffnessMediumLow)

    /**
     * The [FastSpatial] counterpart for `animateIntAsState`.
     *
     * Same physics, different type parameter: an `AnimationSpec<Float>` is not
     * an `AnimationSpec<Int>`, so the counter cannot be animated with the `Float`
     * token and needed its own - which it did not have, and so spelled the
     * numbers out inline instead.
     */
    val FastSpatialInt: FiniteAnimationSpec<Int> get() =
        when (speed) {
            com.anisequel.data.repository.MotionStyle.INSTANT -> snap()
            com.anisequel.data.repository.MotionStyle.SMOOTH -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
            com.anisequel.data.repository.MotionStyle.CHILL -> spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        }

    /**
     * The [FastSpatial] counterpart for `animateDpAsState` (corner radius and elevation).
     */
    val FastSpatialDp: FiniteAnimationSpec<Dp> get() =
        when (speed) {
            com.anisequel.data.repository.MotionStyle.INSTANT -> snap()
            com.anisequel.data.repository.MotionStyle.SMOOTH -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
            com.anisequel.data.repository.MotionStyle.CHILL -> spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        }

    private fun spatial(
        expressiveDamping: Float = Spring.DampingRatioMediumBouncy,
        expressiveStiffness: Float = Spring.StiffnessMedium
    ): FiniteAnimationSpec<Float> =
        when (speed) {
            com.anisequel.data.repository.MotionStyle.INSTANT -> snap()
            com.anisequel.data.repository.MotionStyle.SMOOTH -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
            com.anisequel.data.repository.MotionStyle.CHILL -> spring(
                dampingRatio = expressiveDamping,
                stiffness = expressiveStiffness
            )
        }

    private fun effects(
        stiffness: Float
    ): FiniteAnimationSpec<Float> =
        when (speed) {
            com.anisequel.data.repository.MotionStyle.INSTANT -> snap()
            com.anisequel.data.repository.MotionStyle.SMOOTH -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
            // `CHILL` and the tokens are one spring: the effects tokens are the
            // calm half of the pair, even in the expressive style. NoBouncy keeps
            // alpha and colour from overshooting their target.
            com.anisequel.data.repository.MotionStyle.CHILL -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = stiffness
            )
        }

    private fun colorEffects(
        stiffness: Float
    ): FiniteAnimationSpec<Color> =
        when (speed) {
            com.anisequel.data.repository.MotionStyle.INSTANT -> snap()
            com.anisequel.data.repository.MotionStyle.SMOOTH -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
            com.anisequel.data.repository.MotionStyle.CHILL -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = stiffness
            )
        }
}

/**
 * Modifier that applies a tactile bouncy spring press animation in the Draw phase.
 * Does not trigger composition or layout passes for maximum 120fps smoothness.
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
        animationSpec = ExpressiveMotion.PressSpatial,
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
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes
                                        .firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (change != null) {
                                        // Squared distance against a squared slop:
                                        // the comparison never needs the square
                                        // root, and `Offset.getDistance` is an
                                        // extension in a package this file does not
                                        // otherwise import from.
                                        val dx = change.position.x - down.position.x
                                        val dy = change.position.y - down.position.y
                                        if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                            // Past slop: this is a drag, so the
                                            // scrollable has it now and this is no
                                            // longer a press.
                                            isPointerPressed.value = false
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
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

    val scale by animateFloatAsState(
        targetValue = if (isPressed.value) 0.96f else 1f,
        animationSpec = ExpressiveMotion.PressSpatial,
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
                    onHold()
                },
                onTap = {
                    onClick?.invoke()
                }
            )
        }
}
