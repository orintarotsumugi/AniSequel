package com.example.ui.components.expressive

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Standardized Material 3 Expressive motion tokens and physics specifications.
 * Defines consistent stiffness and damping ratios for spatial and effects motion.
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
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** Super bouncy spring with noticeable playful oscillation for hero moments. */
    val SuperBouncy: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.45f,
        stiffness = Spring.StiffnessLow
    )

    /** Fast effects spring for smooth color and opacity transitions without overshoot. */
    val FastEffects: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Default effects spring for state fades and container transitions. */
    val DefaultEffects: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

/**
 * Modifier that applies a tactile bouncy spring press animation in the Draw phase.
 * Does not trigger composition or layout passes for maximum 120fps smoothness.
 */
fun Modifier.bouncyPress(
    pressedScale: Float = 0.94f
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.52f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bouncy_press_scale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Modifier that adds an expressive spring-physics bounce scale and click listener.
 */
fun Modifier.expressiveBounceClick(
    pressedScale: Float = 0.93f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.50f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expressive_bounce_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
