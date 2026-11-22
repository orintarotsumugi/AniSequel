package com.anisequel.ui.components.expressive

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A colour that eases between two roles without chasing the theme.
 *
 * ## The bug this exists for
 *
 * The theme's whole scheme is animated - `rememberAnimatedColorScheme` eases
 * every one of the 36 roles - so `MaterialTheme.colorScheme.primaryContainer` is
 * a *different value on every frame* of a theme change. A control that then
 * called `animateColorAsState(targetValue = MaterialTheme.colorScheme.X)` was
 * retargeting 36 times a frame: its own spring never got a settled target, so the
 * control arrived a beat after the theme it lives in, and on a bouncy token it
 * overshot a colour the page had already left.
 *
 * The fix is to animate the *decision* rather than the colour. The endpoints are
 * read from the current scheme on every frame - so a theme change moves them, not
 * the target - and only the 0..1 progress is animated, on a scalar spring that
 * settles in a few frames no matter what the theme is doing.
 *
 * ## What this keeps
 *
 * [lerp] is a straight line in sRGB, which is the same interpolation
 * `animateColorAsState` performs internally (`Color.VectorConverter` ulerps the
 * four channels), so this looks identical to the call it replaces - it just stops
 * competing with the theme for the same frames.
 *
 * Both endpoints must be colours of the *same* scheme, and the state must be a
 * decision (selected, enabled, healthy) rather than a value that itself animates.
 * Passing an animated colour here puts the chase back.
 */
@Composable
internal fun rememberStateColor(
    active: Boolean,
    whenInactive: Color,
    whenActive: Color,
    animationSpec: FiniteAnimationSpec<Float> = ExpressiveMotion.FastEffects,
    label: String = "state_colour"
): Color {
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = animationSpec,
        label = label
    )
    return lerp(whenInactive, whenActive, progress)
}

/**
 * Three-or-more state colour, as one progress plus a resolved endpoint pair.
 *
 * The two-state helper above cannot express "neutral, or one of two verdicts"
 * without animating two independent colours, which is the failure it exists to
 * remove: a container and its content easing on separate springs spend the middle
 * of the transition as a pair the scheme never designed, and contrast dips there.
 *
 * Feeding both roles from these two scalars keeps every intermediate frame a
 * blend of two *designed* pairs in the same proportion, so the quietest contrast
 * in the transition is the quietest of the two endpoints.
 */
@Composable
internal fun rememberVerdictColor(
    resolved: Boolean,
    failed: Boolean,
    whenUnresolved: Color,
    whenPassed: Color,
    whenFailed: Color,
    animationSpec: FiniteAnimationSpec<Float> = ExpressiveMotion.FastEffects,
    label: String = "verdict_colour"
): Color {
    val shown by animateFloatAsState(
        targetValue = if (resolved) 1f else 0f,
        animationSpec = animationSpec,
        label = "${label}_shown"
    )
    val failure by animateFloatAsState(
        targetValue = if (failed) 1f else 0f,
        animationSpec = animationSpec,
        label = "${label}_failed"
    )
    return lerp(whenUnresolved, lerp(whenPassed, whenFailed, failure), shown)
}
