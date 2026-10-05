package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f),
        MaterialTheme.colorScheme.surfaceContainerHigh
    )

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim = transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_animation"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim.value - 300f, y = translateAnim.value - 300f),
        end = Offset(x = translateAnim.value + 300f, y = translateAnim.value + 300f)
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header bar shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(20.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(brush)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                // Poster shimmer
                Box(
                    modifier = Modifier
                        .width(104.dp)
                        .height(148.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(brush)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(148.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(18.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(brush)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(14.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(brush)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 20.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(brush)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 20.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(brush)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(brush)
                    )
                }
            }
        }
    }
}
