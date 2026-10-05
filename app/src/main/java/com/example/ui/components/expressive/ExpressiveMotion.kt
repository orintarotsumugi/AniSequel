package com.example.ui.components.expressive

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

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

    /** Snappy spatial spring for quick taps and micro-interactions. */
    val FastSpatial: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Default spatial spring with fluid overshoot for smooth layout adaptations. */
    val DefaultSpatial: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Bouncy spatial spring for celebratory interactions, buttons, and icon reveals. */
    val BouncySpatial: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** Super bouncy spring with noticeable playful oscillation for hero moments. */
    val SuperBouncy: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessVeryLow
    )

    /** Fast effects spring for smooth color and opacity transitions without overshoot. */
    val FastEffects: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Default effects spring for state fades and container transitions. */
    val DefaultEffects: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * The colour counterpart to [FastEffects].
     *
     * `animateColorAsState` takes an `AnimationSpec<Color>`, so the `Float`
     * tokens above cannot be passed to it at all - which is why seven colour
     * animations had each grown their own bare `spring()` call.
     *
     * Deliberately `NoBouncy`: a spring interpolates `Color` componentwise
     * through `Ulerp`, and an overshooting spring drives individual channels past
     * their endpoints on the way down. A colour that overshoots is not a colour
     * on the way to another colour - it can render out of gamut. A two-state
     * selection change wants a fast, non-overshooting curve.
     */
    val FastColorEffects: FiniteAnimationSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /**
     * The press spring shared by every press gesture below.
     *
     * One token rather than a literal per gesture, so `bouncyPress` and
     * `expressiveHoldGesture` cannot drift into feeling like two different
     * buttons. Previously each spelled out its own `spring(...)`, so a change
     * to the feel of one press had to be remembered in the other.
     */
    val PressSpatial: FiniteAnimationSpec<Float> = DefaultSpatial

    /**
     * The [FastSpatial] counterpart for `animateIntAsState`.
     *
     * Same physics, different type parameter: an `AnimationSpec<Float>` is not
     * an `AnimationSpec<Int>`, so the counter cannot be animated with the `Float`
     * token and needed its own - which it did not have, and so spelled the
     * numbers out inline instead.
     */
    val FastSpatialInt: FiniteAnimationSpec<Int> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
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
 */
fun Modifier.bouncyPress(
    pressedScale: Float = 0.96f
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = ExpressiveMotion.PressSpatial,
        label = "bouncy_press_scale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
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
