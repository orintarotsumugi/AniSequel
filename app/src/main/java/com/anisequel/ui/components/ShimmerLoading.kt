package com.anisequel.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.ui.components.expressive.ExpressiveMotion

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
 *
 * The clock stops when the screen is not resumed. A shimmer is a decorative
 * loop, and one left running behind a stopped Activity kept the frame clock
 * awake for a screen nobody could see - the rule [rememberIsResumed] exists
 * for. While paused the phase is a still frame at the start of the sweep, so
 * the skeleton stays painted rather than disappearing to the background.
 *
 * `MotionStyle.INSTANT` gets the same still frame. Every other loop in the app
 * branches on that setting - the orb becomes [StaticEmptyOrb], the page springs
 * become `snap()` - and the shimmer used to inline its own tween and keep
 * sweeping regardless, so the one control the user has over motion did not
 * control the most constantly-drawn animation in the app.
 *
 * The value is a *fraction* of the sweep, not a pixel offset: a skeleton painted
 * at an absolute range could not tell a 100dp chip from a 900dp tablet card, and
 * the highlight only ever crossed part of the box. [shimmerBackground] scales it
 * to the box it is drawing.
 */
@Composable
fun rememberShimmerPhase(
    durationMillis: Int = SHIMMER_DURATION_MILLIS
): State<Float> {
    val still = ExpressiveMotion.speed == MotionStyle.INSTANT || !rememberIsResumed()
    if (still) {
        return remember { mutableFloatStateOf(0f) }
    }
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Linear, because the sweep now starts and ends *outside* the box:
            // the old ease-in-out decelerated into the two edges, which read as
            // the highlight stopping in the middle of the skeleton and starting
            // again.
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
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
        val bleed = maxOf(size.width, size.height) * SHIMMER_BLEED_FRACTION
        val centre = -bleed + phase.value * (size.width + 2f * bleed)
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(x = centre - bleed, y = centre - bleed),
                end = Offset(x = centre + bleed, y = centre + bleed)
            )
        )
    }
}

/**
 * One skeleton block: a clipped box painted with the shared shimmer.
 *
 * The building block the list-shaped waiting states are made of - the profile's
 * activity feed, a comment thread, a row of people - each of which is a handful
 * of these at the sizes of the rows that are coming. Kept beside
 * [shimmerBackground] so there is still one definition of what a skeleton looks
 * like, rather than one per screen.
 *
 * [phase] defaults to its own driver for a lone block and is meant to be hoisted
 * per list; see [rememberShimmerPhase].
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
    phase: State<Float> = rememberShimmerPhase()
) {
    Box(modifier = modifier.clip(shape).shimmerBackground(phase))
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
 * How far the highlight's centre sits outside the box on each side, as a
 * fraction of the box's longest edge.
 *
 * Relative rather than a fixed pixel offset: a 300px bleed crosses a 100dp chip
 * in a tenth of the sweep and never reaches the far edge of a tablet card at
 * all, so the same skeleton looked half-painted at one width and strobed at
 * another. Measured off the box, every skeleton enters and leaves its own edges.
 */
private const val SHIMMER_BLEED_FRACTION = 0.35f
private const val SHIMMER_DURATION_MILLIS = 1100
