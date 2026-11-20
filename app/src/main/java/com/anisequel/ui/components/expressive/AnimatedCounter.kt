package com.anisequel.ui.components.expressive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Animated number counter that rolls smoothly with spring physics when the count changes.
 *
 * ## Why `AnimatedContent` is keyed on the target, not the animated value
 *
 * This used to be `AnimatedContent(targetState = animatedCount)`, where
 * `animatedCount` is the *spring-interpolated* value. `animateIntAsState` emits a
 * new value on every frame while the spring is settling, so `targetState` changed
 * every frame and every one of those frames started a fresh enter+exit
 * transition: two `Text` nodes composed and disposed per frame, plus the implicit
 * `SizeTransform` driving a measure and layout pass each time. A counter that is
 * supposed to be cheaper than static text was doing more work per frame than the
 * list row it sits above.
 *
 * Keying on [count] - the settled target - means one transition per actual change.
 * The digits still interpolate on the *incoming* side, which reads `animatedCount`;
 * the outgoing side renders the count it is leaving, taken from the content
 * lambda's own parameter. Both sides reading `animatedCount` was the bug this
 * fixes: they then showed the same number on every frame, so the old count never
 * appeared at all and the roll read as a flicker.
 *
 * The spring used to be spelled out inline with exactly the parameters
 * [ExpressiveMotion.FastSpatialInt] already declares; it is now
 * [ExpressiveMotion.FastEffectsInt], because a count must not overshoot the
 * number it is counting to.
 *
 * The default `SizeTransform` is dropped with `using null` as well: the digits
 * are tabular and therefore the same width, so there is no size to animate and
 * the default one measured both numbers on every frame of every roll.
 */
@Composable
fun AnimatedCounterText(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val animatedCount by animateIntAsState(
        targetValue = count,
        // `FastEffectsInt`, not `FastSpatialInt`: a spatial spring on an Int
        // overshoots the number itself on the expressive styles, so the counter
        // showed counts that never existed. See the token's KDoc.
        animationSpec = ExpressiveMotion.FastEffectsInt,
        label = "animated_counter_val"
    )

    AnimatedContent(
        targetState = count,
        transitionSpec = {
            if (targetState > initialState) {
                // The shared offset token, like every other slide in the app. These
                // four used to pass no spec at all, which is Compose's default spring:
                // a counter that rolled at its own speed and ignored MotionStyle, so
                // the digits moved while nothing else on the screen did.
                slideInVertically(animationSpec = ExpressiveMotion.FastSpatialOffset) { height ->
                    height / 2
                } togetherWith
                    slideOutVertically(animationSpec = ExpressiveMotion.FastSpatialOffset) { height ->
                        -height / 2
                    } using null
            } else {
                slideInVertically(animationSpec = ExpressiveMotion.FastSpatialOffset) { height ->
                    -height / 2
                } togetherWith
                    slideOutVertically(animationSpec = ExpressiveMotion.FastSpatialOffset) { height ->
                        height / 2
                    } using null
            }
        },
        label = "animated_counter_content",
        modifier = modifier
    ) { value ->
        // The incoming side rolls its digits (`animatedCount`), the outgoing side
        // shows the number it is leaving (`value`).
        //
        // Both sides used to read `animatedCount` - the single animating value -
        // so at every frame of the transition the departing and arriving `Text`
        // rendered the *same* number. The old count was never on screen: the roll
        // looked like one number sliding out and the same number sliding back in,
        // which is why the counter read as a flicker rather than as a roll. The
        // content lambda's parameter is the side's own settled count, so it is the
        // only thing that can freeze the outgoing digits at the value they had.
        Text(
            text = if (value == count) "$animatedCount" else "$value",
            // Tabular figures. The digits interpolate every frame of the spring,
            // and with proportional figures the text's width changes with them -
            // "111" measures narrower than "888" - so the counter's own box, and
            // everything laid out beside it, was re-measured on every one of
            // those frames. Equal-width digits make the roll cost what settled
            // text costs.
            style = style.copy(fontFeatureSettings = "tnum"),
            color = color,
            fontWeight = fontWeight
        )
    }
}
