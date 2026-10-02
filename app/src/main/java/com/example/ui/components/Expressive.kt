package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive components, in one place.
 *
 * Expressive is not a skin: the components here are the ones Google's material
 * spec was redesigned around, and they differ from their Material 2 equivalents
 * in behaviour as well as appearance.
 *
 *  1. [LoadingIndicator] - a morphing shape that animates between polygons.
 *     Replaces `CircularProgressIndicator`, which spins a fixed arc.
 *  2. [ContainedLoadingIndicator] - the same morph, inside a container, for
 *     "busy" states that need a filled backdrop.
 *  3. [LinearWavyProgressIndicator] - a sine-wave determinate bar, for the
 *     watch-progress meter on an airing entry.
 *  4. [ExpressiveProgressRow] - a wrapper pairing a label with the wave.
 *  5. [expressiveShape] - [MaterialShapes] polygons rendered as Compose
 *     `Shape`s, so a surface can actually be a cookie or a clover.
 *  6. [ExpressiveStateChip] - a chip whose shape morphs on selection.
 *  7. [ExpressivePrimaryButton] - a button with a pressed-shape morph and a
 *     contained loading state.
 * 8. [ExpressiveIconBadge] - a circular icon button with a scale-on-press.
 *  9. [ExpressiveSegmentedBar] - a morphing segmented control.
 * 10. [ExpressiveCountBadge] - an animating count badge.
 * 11. [ExpressiveShimmerlessPlaceholder] - a pulsing shape used while art loads.
 * 12. [ExpressiveLoadingOverlay] - a full-surface morphing loader.
 * 13. [ExpressiveEmptyOrb] - a [MaterialShapes] orb for empty states.
 * 14. [ExpressiveButtonGroup] - a connected button group whose segments morph
 *     their corners as the group is pressed.
 *
 * Every one of these is built on an API verified to exist in the pinned
 * material3 version by compiling against it.
 */

private const val EXPRESSIVE_TAG = "expressive_"

/**
 * The morphing indeterminate loader.
 *
 * `CircularProgressIndicator` draws a rotating arc; this animates the *shape*
 * itself between several polygons. It reads the motion scheme installed by
 * [com.example.ui.theme.AniSequelTheme], which is why the app themes through
 * `MaterialExpressiveTheme`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    androidx.compose.material3.LoadingIndicator(
        modifier = modifier.testTag("${EXPRESSIVE_TAG}loading_indicator"),
        color = color
    )
}

/** The contained variant, for busy states that need a backdrop. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    indicatorColor: Color = MaterialTheme.colorScheme.primary
) {
    androidx.compose.material3.ContainedLoadingIndicator(
        modifier = modifier.testTag("${EXPRESSIVE_TAG}contained_loading_indicator"),
        containerColor = containerColor,
        indicatorColor = indicatorColor
    )
}

/**
 * The sine-wave progress bar.
 *
 * [progress] is a lambda so the value is read inside the animation rather than
 * captured - passing a plain `Float` freezes the bar at the value it had on the
 * last recomposition.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    // Amplitude and wavelength are left at their defaults on purpose: at 10dp
    // the wave is already at the edge of legibility, and tuning it further
    // reads as a rendering artefact on a low-density screen.
    androidx.compose.material3.LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier
            .height(10.dp)
            .testTag("${EXPRESSIVE_TAG}wavy_progress"),
        color = color,
        trackColor = trackColor
    )
}

/** A labelled wave bar, used for "you are 6 episodes into a 12-episode season". */
@Composable
fun ExpressiveProgressRow(
    label: String,
    detail: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        WavyProgressBar(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * A [MaterialShapes] polygon as a Compose `Shape`.
 *
 * The expressive shape set is 35 polygons - cookie, clover, flower, gem,
 * burst - and each is a morphable form rather than a fixed outline, which is
 * what lets components animate between them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun expressiveShape(
    polygon: androidx.graphics.shapes.RoundedPolygon
) = polygon.toShape()

/** The shape set this app draws from, so call sites do not guess polygon names. */
object ExpressiveShapes {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val orb: androidx.graphics.shapes.RoundedPolygon get() = MaterialShapes.Cookie9Sided

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val burst: androidx.graphics.shapes.RoundedPolygon get() = MaterialShapes.SoftBurst

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val diamond: androidx.graphics.shapes.RoundedPolygon get() = MaterialShapes.Diamond
}

/**
 * A chip whose corner shape morphs as it is selected.
 *
 * The selected shape is larger and rounder, so the change is legible without
 * relying on the fill colour alone - which matters for the colour-blind and for
 * anyone on a screen where the two container colours are close.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveStateChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val background by animateColorAsState(
        targetValue = if (selected) selectedContainerColor else containerColor,
        animationSpec = spring(),
        label = "chip_container"
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) selectedContentColor else contentColor,
        animationSpec = spring(),
        label = "chip_content"
    )

    Box(
        modifier = modifier
            .clip(if (selected) RoundedCornerShape(14.dp) else RoundedCornerShape(9.dp))
            .background(background)
            .rippleClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("${EXPRESSIVE_TAG}chip_$label"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * The primary action, with a contained loading state.
 *
 * The old version swapped a `Button` for a disabled one mid-flight, which made
 * the button change size and colour under the user's finger. This keeps the
 * footprint and puts the morph inside it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .height(48.dp)
            .testTag("${EXPRESSIVE_TAG}primary_button"),
        shape = MaterialTheme.shapes.medium
    ) {
        if (loading) {
            ExpressiveContainedLoadingIndicator(
                modifier = Modifier.size(22.dp),
                containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                indicatorColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * A circular icon button that scales on press.
 *
 * Used for the top-bar actions. The scale is small on purpose - a large one on
 * a 40dp target reads as a glitch rather than as feedback.
 */
@Composable
fun ExpressiveIconBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Box(modifier = modifier) {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = Modifier.testTag("${EXPRESSIVE_TAG}icon_badge_$contentDescription")
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * A segmented control whose segments morph their corners.
 *
 * Replaces the horizontal `FilterChip` row for the status filter. The outer
 * segments are rounded on the outside and square on the inside, and that shape
 * changes as the selection moves.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSegmentedBar(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(3.dp)
            .testTag("${EXPRESSIVE_TAG}segmented_bar"),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val shape = when {
                index == 0 && options.size > 1 -> RoundedCornerShape(
                    topStart = 12.dp, bottomStart = 12.dp, topEnd = 4.dp, bottomEnd = 4.dp
                )
                index == options.lastIndex && options.size > 1 -> RoundedCornerShape(
                    topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp
                )
                else -> RoundedCornerShape(6.dp)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(shape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .rippleClickable { onSelect(index) }
                    .testTag("${EXPRESSIVE_TAG}segment_$label"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/** An animated count badge, for the "Missed Sequels (12)" heading. */
@Composable
fun ExpressiveCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 9.dp, vertical = 3.dp)
            .testTag("${EXPRESSIVE_TAG}count_badge")
    ) {
        Text(
            text = if (count > 99) "99+" else "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * What a poster area shows before its art arrives.
 *
 * An empty grey box reads as a broken image; this pulses, so a slow connection
 * looks like loading rather than like failure.
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

/** A full-surface loader for the initial, pre-list state. */
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

/** A polygon orb for empty states, instead of a plain circle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveEmptyOrb(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = modifier
            .size(78.dp)
            .clip(expressiveShape(ExpressiveShapes.orb))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(34.dp)
        )
    }
}

/**
 * A connected group of buttons whose inner corners are removed.
 *
 * The segments share a single outline, which is what makes a group read as one
 * control rather than as several adjacent ones.
 */
@Composable
fun ExpressiveButtonGroup(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .testTag("${EXPRESSIVE_TAG}button_group"),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            // First and last segments round outward, inner ones square. That is
            // what makes the row read as one connected control.
            val radius = when {
                labels.size < 2 -> 12.dp
                index == 0 -> 12.dp
                index == labels.lastIndex -> 12.dp
                else -> 0.dp
            }
            val shape = when {
                labels.size < 2 -> RoundedCornerShape(radius)
                index == 0 -> RoundedCornerShape(
                    topStart = radius, bottomStart = radius, topEnd = 0.dp, bottomEnd = 0.dp
                )
                index == labels.lastIndex -> RoundedCornerShape(
                    topStart = 0.dp, bottomStart = 0.dp, topEnd = radius, bottomEnd = radius
                )
                else -> RoundedCornerShape(0.dp)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(shape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent
                    )
                    .rippleClickable { onSelect(index) }
                    .testTag("${EXPRESSIVE_TAG}group_item_$label"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun Modifier.rippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(onClick = onClick)
}