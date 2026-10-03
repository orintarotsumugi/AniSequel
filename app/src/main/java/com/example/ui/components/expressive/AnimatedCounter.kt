package com.example.ui.components.expressive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "animated_counter_val"
    )

    AnimatedContent(
        targetState = animatedCount,
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
    ) { current ->
        Text(
            text = "$current",
            style = style,
            color = color,
            fontWeight = fontWeight
        )
    }
}
