package com.example.ui.components.expressive

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
 * The digits themselves still interpolate, because `animatedCount` is what gets
 * rendered inside both the outgoing and incoming content.
 *
 * The spring used to be spelled out inline with exactly the parameters
 * [ExpressiveMotion.FastSpatialInt] already declares; it is now the token.
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
        animationSpec = ExpressiveMotion.FastSpatialInt,
        label = "animated_counter_val"
    )

    AnimatedContent(
        targetState = count,
        transitionSpec = {
            if (targetState > initialState) {
                slideInVertically { height -> height / 2 } togetherWith
                    slideOutVertically { height -> -height / 2 }
            } else {
                slideInVertically { height -> -height / 2 } togetherWith
                    slideOutVertically { height -> height / 2 }
            }
        },
        label = "animated_counter_content",
        modifier = modifier
    ) { _ ->
        // `animatedCount`, not the content lambda's own `targetState`: the digits
        // are what should roll, and rolling them is the whole point of the
        // spring. Reading the content parameter here would render the settled
        // number and animate nothing but the slide.
        Text(
            text = "$animatedCount",
            style = style,
            color = color,
            fontWeight = fontWeight
        )
    }
}
