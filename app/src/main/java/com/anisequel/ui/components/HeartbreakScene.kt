package com.anisequel.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.icons.AppCustomVectors
import com.anisequel.ui.theme.AniSequelTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The size the two heart halves are authored at.
 *
 * Big enough that the seam, the glue and the stitches survive at phone density;
 * small enough that the error screen still leads with its headline rather than
 * a graphic.
 */
private val HeartSize = 92.dp

/** The box is larger than the heart so the broken halves have room to part. */
private val HeartSceneSize = 128.dp

/** How far each half travels when it breaks, at full separation. */
private val HeartSplitTravel = 12.dp

/**
 * A heart that beats once, then cracks in two.
 *
 * ## Why the two halves are real vectors
 *
 * Drawing a heart and then clipping a gap through it leaves flat edges on the
 * rounded outline, and the halves cannot rotate away from each other. Two
 * authored halves can: [AppCustomVectors.HeartLeft] and
 * [AppCustomVectors.HeartRight] each own a closed path, so the split is a
 * transform rather than a mask.
 *
 * ## Why the motion is in `graphicsLayer`
 *
 * The split, the droop and the rotation are all read inside the layer block, so
 * the animation invalidates draw rather than composition. The first version of
 * this scene drove an `offset` and a `rotate` from a recomposed float, which
 * re-laid-out the row on every frame.
 */
@Composable
fun HeartbreakScene(modifier: Modifier = Modifier) {
    val scale = rememberAnimatable(0.7f)
    val split = rememberAnimatable(0f)
    val glue = rememberAnimatable(0f)
    val sparkle = rememberAnimatable(0f)

    LaunchedEffect(Unit) {
        // Pop in, beat once, then break. The beat is what makes the crack read
        // as a heart rather than as a shape being pulled apart.
        scale.animateTo(1f, ExpressiveMotion.SuperBouncy)
        scale.animateTo(1.08f, ExpressiveMotion.FastSpatial)
        scale.animateTo(1f, ExpressiveMotion.FastSpatial)
        split.animateTo(1f, ExpressiveMotion.BouncySpatial)
    }

    HeartPair(
        split = split,
        scale = scale,
        glue = glue,
        sparkle = sparkle,
        modifier = modifier
    )
}

/**
 * The heart coming back together, glued along the seam, with a small sparkle.
 *
 * Used in three places: the full-screen recovery overlay after a failed load
 * succeeds, the Settings connection dialog when a down check turns green, and
 * nowhere else - this is a moment, not a decoration.
 *
 * [onRepaired] fires once the halves have settled and the glue has reached the
 * bottom of the seam, so a caller can time a fade-out or a toast to the visual
 * rather than to a guessed delay.
 */
@Composable
fun HeartRepairScene(
    modifier: Modifier = Modifier,
    onRepaired: () -> Unit = {}
) {
    // Starts apart, unlike the break scene: the repair is always a continuation
    // of a heart that is already cracked.
    val scale = rememberAnimatable(0.92f)
    val split = rememberAnimatable(1f)
    val glue = rememberAnimatable(0f)
    val sparkle = rememberAnimatable(0f)

    LaunchedEffect(Unit) {
        // The join is the point of the scene, so it runs now; the scale is a
        // supporting pop that settles underneath it rather than a delay before
        // anything interesting happens.
        launch { scale.animateTo(1f, ExpressiveMotion.SuperBouncy) }
        split.animateTo(0f, ExpressiveMotion.SuperBouncy)
        // The glue starts just after the join so the seam reads as the thing
        // holding it together, not as a line drawn over an intact heart.
        delay(140)
        glue.animateTo(1f, ExpressiveMotion.DefaultSpatial)
        launch {
            delay(220)
            sparkle.animateTo(1f, ExpressiveMotion.SuperBouncy)
        }
        delay(650)
        onRepaired()
    }

    HeartPair(
        split = split,
        scale = scale,
        glue = glue,
        sparkle = sparkle,
        modifier = modifier
    )
}

@Composable
private fun rememberAnimatable(initial: Float): Animatable<Float, AnimationVector1D> =
    remember { Animatable(initial) }

/**
 * The shared drawing: two halves whose transforms, seam and sparkles are all
 * read in the draw phase.
 *
 * [split] runs from 0 (joined) to 1 (fully apart). [glue] runs from 0 (no seam)
 * to 1 (seam drawn, stitches visible). [sparkle] is the little mended-heart
 * glint at the end, and is ignored at 0.
 */
@Composable
private fun HeartPair(
    split: Animatable<Float, AnimationVector1D>,
    scale: Animatable<Float, AnimationVector1D>,
    glue: Animatable<Float, AnimationVector1D>,
    sparkle: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier
) {
    // The colour change is a settled value, not a per-frame lerp: the heart is
    // either broken-red or mended-green, and `animateColorAsState` moves between
    // those two constants once the glue completes. `derivedStateOf` is what keeps
    // that from recomposing on every frame of the glue: it only invalidates when
    // the *boolean* flips, not when the Animatable's float moves.
    val healed by remember { derivedStateOf { glue.value >= 1f } }
    val mendedColor = AniSequelTheme.statusColors.success
    val tint by animateColorAsState(
        targetValue = if (healed) {
            mendedColor
        } else {
            MaterialTheme.colorScheme.error
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "heartTint"
    )

    Box(
        modifier = modifier.size(HeartSceneSize),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppCustomVectors.HeartLeft,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(HeartSize)
                .graphicsLayer {
                    translationX = -split.value * HeartSplitTravel.toPx()
                    translationY = split.value * 4.dp.toPx()
                    rotationZ = -split.value * 15f
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )
        Icon(
            imageVector = AppCustomVectors.HeartRight,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(HeartSize)
                .graphicsLayer {
                    translationX = split.value * HeartSplitTravel.toPx()
                    translationY = split.value * 4.dp.toPx()
                    rotationZ = split.value * 15f
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )

        // The glue seam. A straight line down the centre, drawn from the top dip
        // to the bottom tip as the repair progresses, then left in place as a
        // stitch. Its colour is the *mended* green from the first frame: the
        // point is that this line is the fix, not part of the break.
        Canvas(modifier = Modifier.size(HeartSize)) {
            val progress = glue.value
            if (progress <= 0f) return@Canvas

            val mended = mendedColor
            val seamTop = size.height * 0.27f
            val seamBottom = size.height * 0.88f
            val seamX = size.width / 2f
            val endY = seamTop + (seamBottom - seamTop) * progress

            drawLine(
                color = mended,
                start = Offset(seamX, seamTop),
                end = Offset(seamX, endY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Stitches only once the seam is complete, so the middle of the
            // animation is a neat line rather than three floating ticks.
            val stitchProgress = sparkle.value
            if (stitchProgress > 0f) {
                val halfTick = 5.dp.toPx()
                val tickAlpha = stitchProgress.coerceIn(0f, 1f)
                listOf(0.38f, 0.52f, 0.66f).forEach { fraction ->
                    val y = size.height * fraction
                    drawLine(
                        color = mended.copy(alpha = tickAlpha),
                        start = Offset(seamX - halfTick, y),
                        end = Offset(seamX + halfTick, y),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Sparkle(
            sparkle = sparkle,
            alignment = Alignment.TopStart,
            modifier = Modifier.offset(x = 18.dp, y = 20.dp)
        )
        Sparkle(
            sparkle = sparkle,
            alignment = Alignment.BottomEnd,
            modifier = Modifier.offset(x = (-22).dp, y = (-26).dp)
        )
    }
}

@Composable
private fun BoxScope.Sparkle(
    sparkle: Animatable<Float, AnimationVector1D>,
    alignment: Alignment,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = AppCustomVectors.AnimeSparkle,
        contentDescription = null,
        tint = AniSequelTheme.statusColors.success,
        modifier = modifier
            .align(alignment)
            .size(18.dp)
            .graphicsLayer {
                val progress = sparkle.value
                // A bouncy spring overshoots past 1; alpha is not a place to let
                // that through, even though the scale is.
                alpha = progress.coerceIn(0f, 1f)
                val pop = 0.4f + 0.6f * progress
                scaleX = pop
                scaleY = pop
                rotationZ = progress * 35f
            }
    )
}
