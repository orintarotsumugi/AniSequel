package com.anisequel.ui.components.expressive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.anisequel.data.repository.MotionStyle
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.rememberIsResumed
import kotlin.math.PI
import kotlin.math.sin

private const val EXPRESSIVE_TAG = "expressive_"

/**
 * The morphing silhouette's share of the orb's 80dp box.
 *
 * The remaining 6% is the margin the aura behind the fill scales into. Without
 * it the aura's outer edge is clipped by the box, and every lobe that reaches
 * the edge is cut flat exactly where it should curve - which on this silhouette
 * is most of them.
 *
 * That margin is also a ceiling: the aura's largest scale must stay below
 * `1 / ORB_FILL_FRACTION` (1.0638). It peaks at 1.06 today, so a lobe of the aura
 * lands 0.4% inside the box rather than on it.
 */
private const val ORB_FILL_FRACTION = 0.94f

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

    // The container and the glyph change meaning with the empty state - a
    // filtered list is a grey search orb, the caught-up dashboard is a green
    // sparkle - and swapping them in one frame made the orb flash rather than
    // settle. `FastColorEffects` is the app's non-overshooting colour spring;
    // under INSTANT it is `snap()`, so the static path still changes in one
    // frame for a reader who asked for no motion.
    val resolvedContainer by animateColorAsState(
        targetValue = containerColor,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "empty_orb_container"
    )
    val resolvedTint by animateColorAsState(
        targetValue = iconTint,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "empty_orb_tint"
    )

    if (ExpressiveMotion.speed == MotionStyle.INSTANT || !resumed) {
        StaticEmptyOrb(
            icon = icon,
            modifier = modifier,
            containerColor = resolvedContainer,
            iconTint = resolvedTint
        )
    } else {
        MorphingEmptyOrb(
            icon = icon,
            modifier = modifier,
            containerColor = resolvedContainer,
            iconTint = resolvedTint
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
 * The expressive orb: the nine-sided cookie deforming into a soft burst while
 * the icon breathes with it.
 *
 * ## Why this pair, and not Cookie -> Flower
 *
 * Both of the old endpoints are attractive silhouettes, but they are not a
 * morph. Nine shallow lobes and eight deep petals have no stable curve-to-curve
 * correspondence, so `Morph`'s feature matcher produced a shape that folded
 * through itself around the middle of the tween. That is the same "torn clover"
 * the frozen outline used to show - it was just moving this time. The pair is
 * now the adjacent pair from Material's own loading-indicator sequence (the
 * soft burst and the nine-sided cookie), whose curves are authored to
 * interpolate.
 *
 * ## One clock, and a rest at each end
 *
 * The old version ran the morph on a 2.4s transition and the icon scale on an
 * independent 1.6s one, so the two never agreed about when the orb was full.
 * Both read [morphProgress] now: the icon is smallest while the silhouette
 * rests, grows through the deformation, and settles again. The cycle itself has
 * a hold at both ends (see [ExpressiveMotion.OrbMorph]) so this reads as
 * breathing artwork rather than as a loading spinner that never lands.
 *
 * ## The silhouette is drawn, not clipped
 *
 * A clipped layer asks its `Shape` for an outline when the layer is configured,
 * not once per frame, so a shape whose only animated value is read inside
 * `createOutline` gives the layer one outline and keeps it. The orb froze on a
 * single frame of the morph. `Modifier.drawBehind` reads [morphProgress] in the
 * draw pass - the pass that is supposed to depend on it - so the outline is
 * rebuilt every frame.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingEmptyOrb(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color,
    iconTint: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_morph_transition")
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = ExpressiveMotion.OrbMorph,
        label = "orb_morph_progress"
    )

    // Cookie9Sided -> SoftBurst: the two adjacent entries in
    // `LoadingIndicatorDefaults.IndeterminateIndicatorPolygons`. Material runs
    // them the other way round (SoftBurst, then Cookie9Sided); a `Morph` is
    // symmetric in what it can interpolate, so the pair is what matters, not the
    // direction. `Morph` builds its match table in the constructor, so it is
    // remembered for the life of the orb rather than rebuilt per frame.
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    // One path, one matrix and one bounds buffer for the life of the orb;
    // `writeSilhouette` rewinds and rewrites them in the draw pass instead of
    // allocating three objects per frame.
    val silhouette = remember { Path() }
    val matrix = remember { Matrix() }
    val bounds = remember { FloatArray(4) }

    Box(
        modifier = modifier
            .size(80.dp)
            // `morphProgress` is read *inside* the draw lambda, so a frame of
            // the morph invalidates the draw pass alone - not this composable,
            // and not a graphics layer that would then cache the outline.
            .drawBehind {
                val path = morph.writeSilhouette(
                    progress = morphProgress,
                    size = size,
                    target = silhouette,
                    matrix = matrix,
                    bounds = bounds,
                    scaleFraction = ORB_FILL_FRACTION
                )

                // A larger, softer copy of the same silhouette: the one thing
                // that gives a flat container colour some depth without a
                // second clock. The fill is held back to 94% so the aura fits
                // inside the box instead of being clipped by it, and the aura
                // both grows and brightens with `breath` so the orb blooms
                // rather than just changing outline.
                val breath = sin(morphProgress * PI).toFloat()
                // 1.04 -> 1.06, and it must never pass 1 / ORB_FILL_FRACTION
                // (1.0638): past that the aura's outer edge reaches the box and
                // the lobes are cut flat by it, which is the clipping the 94%
                // fill exists to avoid.
                val auraScale = 1.04f + 0.02f * breath
                val pivot = center
                withTransform({
                    scale(auraScale, auraScale, pivot)
                }) {
                    drawPath(
                        path,
                        containerColor.copy(
                            alpha = containerColor.alpha * (0.14f + 0.10f * breath)
                        )
                    )
                }
                drawPath(path, containerColor)
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
                    // Read inside the layer block so the icon breathes in the
                    // draw pass, alongside the silhouette, without recomposing
                    // the empty state on every frame.
                    val iconScale = 0.92f + 0.10f * sin(morphProgress * PI).toFloat()
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}
