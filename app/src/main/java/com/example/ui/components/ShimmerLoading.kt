package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * The one clock every shimmer on screen shares.
 *
 * ## Why this exists
 *
 * `DashboardLoadingView` renders five [ShimmerCard]s at once. Each card used to
 * own a `rememberInfiniteTransition` and read its animated value while building
 * a `Brush`, which put a per-frame read in the *composition*: five simultaneous
 * full recompositions of five composables, five `List<Color>` allocations, and
 * five new `LinearGradient` shaders per frame, for the length of a network scan
 * on a large account.
 *
 * Hoisting the transition means one driver for the whole list rather than one per
 * card, and the cards stay phase-locked to each other - which is what a single
 * sweeping highlight across a stack of skeletons looks like, instead of five
 * unrelated ones that happen to be running.
 *
 * Hoist it once where the list is built and pass it down. The parameter default
 * exists so a lone card still works, and it is a composable default expression
 * so that lone card still gets its own correctly-scoped driver.
 */
@Composable
fun rememberShimmerPhase(
    durationMillis: Int = SHIMMER_DURATION_MILLIS
): State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    return transition.animateFloat(
        initialValue = SHIMMER_START,
        targetValue = SHIMMER_END,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_animation"
    )
}

/**
 * Draws a shimmer sweep behind this box, driven by [phase].
 *
 * The animated value is read inside `drawBehind`, which is a *draw*-phase read,
 * so a frame of the shimmer invalidates only the draw pass. Reading it while
 * composing - which is what `.background(brush)` with a moving gradient forces,
 * because the brush has to be built before the modifier chain can be applied -
 * invalidates the whole composable instead.
 *
 * The colours are resolved and remembered *outside* the draw lambda on purpose.
 * `MaterialTheme.colorScheme` is a composable read, and a `DrawScope` is not a
 * composable scope, so it cannot be called from in here - and reading it per
 * frame would reintroduce the allocation this is here to remove.
 *
 * The gradient itself is still rebuilt each frame, because its endpoints
 * genuinely move. That is one shader allocation in the draw pass, in exchange
 * for no longer being a recomposition plus an allocation in composition.
 *
 * Pair with `Modifier.clip` *above* this one: the clip wraps the drawBehind
 * output, so the sweep is cut to the box's rounded corners exactly as
 * `.background(brush)` used to be.
 */
@Composable
private fun Modifier.shimmerBackground(phase: State<Float>): Modifier {
    val high = MaterialTheme.colorScheme.surfaceContainerHigh
    val lowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val colors = remember(high, lowest) {
        listOf(high, lowest.copy(alpha = 0.8f), high)
    }

    return drawBehind {
        val offset = phase.value
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(x = offset - SHIMMER_BLEED, y = offset - SHIMMER_BLEED),
                end = Offset(x = offset + SHIMMER_BLEED, y = offset + SHIMMER_BLEED)
            )
        )
    }
}

@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    phase: State<Float> = rememberShimmerPhase()
) {
    val boxShape = MaterialTheme.shapes.extraSmall

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
                    .clip(boxShape)
                    .shimmerBackground(phase)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                // Poster shimmer
                Box(
                    modifier = Modifier
                        .width(104.dp)
                        .height(148.dp)
                        .clip(MaterialTheme.shapes.small)
                        .shimmerBackground(phase)
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
                                .clip(boxShape)
                                .shimmerBackground(phase)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(14.dp)
                                .clip(boxShape)
                                .shimmerBackground(phase)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 20.dp)
                                    .clip(boxShape)
                                    .shimmerBackground(phase)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 20.dp)
                                    .clip(boxShape)
                                    .shimmerBackground(phase)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(boxShape)
                            .shimmerBackground(phase)
                    )
                }
            }
        }
    }
}

/**
 * How far the highlight's centre sits outside the box on each side.
 *
 * Held in a constant rather than written as `300f` at both ends of the gradient,
 * because the two numbers are one idea: the gradient's centre tracks
 * [SHIMMER_START]..[SHIMMER_END], and this is how much of it hangs off each end
 * so the sweep enters and leaves the box rather than popping at its edges.
 */
private const val SHIMMER_BLEED = 300f
private const val SHIMMER_START = -300f
private const val SHIMMER_END = 1200f
private const val SHIMMER_DURATION_MILLIS = 1100