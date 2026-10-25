package com.anisequel.ui.components.expressive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.anisequel.data.repository.MotionStyle
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.rememberIsResumed

private const val EXPRESSIVE_TAG = "expressive_"

/**
 * Animated count pill badge for section headers.
 */
@Composable
fun ExpressiveCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 9.dp, vertical = 3.dp)
            .testTag("${EXPRESSIVE_TAG}count_badge")
    ) {
        AnimatedContent(
            targetState = if (count > 99) "99+" else "$count",
            transitionSpec = {
                fadeIn(ExpressiveMotion.FastEffects) togetherWith fadeOut(ExpressiveMotion.FastEffects)
            },
            label = "count_badge_text"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Visual artwork placeholder shown while posters load.
 */
@Composable
fun ExpressiveArtworkPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .testTag("${EXPRESSIVE_TAG}artwork_placeholder"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppVectorIcons.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * Full surface loading screen with expressive morphing animation.
 */
@Composable
fun ExpressiveLoadingOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("${EXPRESSIVE_TAG}loading_overlay"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(52.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Expressive shape orb for empty state graphics with subtle breathing motion.
 *
 * The orb's silhouette morphs between two Material shape-library polygons on a
 * loop, rather than just pulsing on a fixed path. That keeps the empty state
 * looking hand-built: a static 9-sided cookie with a scaling icon reads as the
 * icon on a placeholder; a polygon that is actively *between* two shapes reads
 * as a loading figure, which is the point of the empty state.
 *
 * ## Instant motion is actually instant now
 *
 * This used to be one composable that always built the `rememberInfiniteTransition`
 * and always read both of its values - into `clip` and into `graphicsLayer`. The
 * instant motion style only changed *which* shape got clipped, so behind a static
 * box two tweens went on ticking on every frame and the icon went on breathing.
 * That is the opposite of what the comment on the branch claimed, which is why it
 * read as handled.
 *
 * The branch is between two composables rather than inside one because an
 * infinite transition cannot be skipped conditionally: a composable that
 * sometimes owns a clock would change the shape of the composition every time the
 * motion style changed. The static path allocates no animation at all.
 *
 * ## The clock also stops when the app is not on screen
 *
 * The cost the Instant branch could not cover was this one: the transition kept
 * ticking behind an orb on a stopped Activity, because a
 * `rememberInfiniteTransition` runs for as long as it is composed and an empty
 * state is composed inside a plain `Column` - scrolling it away does not dispose
 * it, and neither does leaving the app. It is gated on `rememberIsResumed` now,
 * which is the same signal the joke toast's timer uses.
 *
 * The branch joins the Instant one rather than wrapping the animation, so the
 * static composable is the *only* thing composed while the app is in the
 * background: no clock is allocated to be paused, which is the point. Because
 * both branches are the same pair of composables, resuming swaps one for the
 * other and the orb's silhouette restarts from the cookie - a shape change the
 * reader cannot see, since they were not looking at the screen a frame earlier.
 */
@Composable
fun ExpressiveEmptyOrb(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val resumed = rememberIsResumed()

    if (ExpressiveMotion.speed == MotionStyle.INSTANT || !resumed) {
        StaticEmptyOrb(
            icon = icon,
            modifier = modifier,
            containerColor = containerColor,
            iconTint = iconTint
        )
    } else {
        MorphingEmptyOrb(
            icon = icon,
            modifier = modifier,
            containerColor = containerColor,
            iconTint = iconTint
        )
    }
}

/**
 * The instant-motion orb: one static shape, one static icon, no clock at all.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StaticEmptyOrb(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color,
    iconTint: Color
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(expressiveShape(ExpressiveShapes.orb))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(36.dp)
        )
    }
}

/**
 * The expressive orb: Cookie -> Flower while the icon breathes.
 *
 * The silhouette is drawn here rather than applied as a `Modifier.clip(shape)`.
 * That is the fix for the "torn clover" the released orb showed: a clipped layer
 * asks its `Shape` for an outline when the layer is configured, not once per
 * frame, so a shape whose only animated value is read inside `createOutline`
 * gives the layer one outline and keeps it. The orb froze on a single frame of
 * the morph. `Modifier.drawBehind` reads [morphProgress] in the draw pass - the
 * pass that is supposed to depend on it - so the outline is rebuilt every frame.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingEmptyOrb(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color,
    iconTint: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse_transition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse_scale"
    )

    // Cookie -> Flower: two familiar silhouettes, so the morph is a deformation
    // the eye can track frame to frame rather than a morph into a polygon it
    // has to decode first. Held at each end for nothing - the tween never rests,
    // which is what makes the box look like it is turning.
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_morph_progress"
    )

    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Flower) }
    // One path, one matrix and one bounds buffer for the life of the orb;
    // `writeSilhouette` rewinds and rewrites them in the draw pass instead of
    // allocating three objects per frame.
    val silhouette = remember { Path() }
    val matrix = remember { Matrix() }
    val bounds = remember { FloatArray(4) }

    Box(
        modifier = modifier
            .size(80.dp)
            // `morphProgress` is read *inside* the draw lambda, so a frame of the
            // morph invalidates the draw pass alone - not this composable, and
            // not a graphics layer that would then cache the outline.
            .drawBehind {
                morph.writeSilhouette(morphProgress, size, silhouette, matrix, bounds)
                drawPath(silhouette, containerColor)
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}
