package com.example.ui.components.expressive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One destination of [ExpressiveNavigationBar]. */
data class NavigationDestination(
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * A floating capsule navigation bar where the selected destination grows a label.
 *
 * ## Why not `NavigationBar`
 *
 * The stock component gives every destination an equal slot with an icon over a
 * fixed label. This screen already has an [ExpressiveTabBar] in its top app bar,
 * and putting a `NavigationBar` underneath it would draw the same control twice
 * at two sizes and make the two read as the same thing. The distinction is
 * deliberate: the top bar switches between tabs of one page, the floating
 * capsule switches between pages, and the growing label is what says so at a
 * glance - the unselected destinations are icons only, so there is exactly one
 * place on the bar where the current page is spelled out.
 *
 * ## Why the label animates rather than switching
 *
 * `AnimatedVisibility` over the label makes the capsule's width spring open and
 * shut, because a `Row` is measured from its content and the label's own width
 * is what is animating. Animating a stored width state instead would have needed
 * the width of the text before the text was laid out - one frame of the wrong
 * width, or a measured-width side channel through a subcomposition.
 *
 * The selection *scale* and press *scale* are multiplied together in one
 * `graphicsLayer`, so both are draw-phase only and neither recomposes this bar -
 * it sits over a list that is being scrolled underneath it.
 */
@Composable
fun ExpressiveNavigationBar(
    destinations: List<NavigationDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("expressive_navigation_bar"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                // Elevated rather than outlined: it floats over scrolling
                // content, and a hairline border on a shape with a 50% radius
                // sits exactly on the curve's own edge and reads as a smudge.
                .shadow(elevation = 8.dp, shape = ExpressiveShapes.pill, clip = false)
                .clip(ExpressiveShapes.pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { index, destination ->
                NavigationItem(
                    destination = destination,
                    selected = index == selectedIndex,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    destination: NavigationDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // One shared interaction source with the ripple suppressed: a ripple on a
    // capsule that is simultaneously springing wider and changing colour fights
    // the spring, and the capsule's own scale is the press feedback.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.PressSpatial,
        label = "nav_item_press_scale"
    )
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = ExpressiveMotion.SelectionScale,
        label = "nav_item_selection_scale"
    )
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nav_item_container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nav_item_content"
    )

    Row(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                val combined = pressScale * selectionScale
                scaleX = combined
                scaleY = combined
            }
            .clip(ExpressiveShapes.pill)
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            // An icon-only item is otherwise 22dp wide plus 10dp of padding, which
            // is under the 48dp minimum tap target and gets found by a fingertip
            // rather than by the user aiming at it.
            .defaultMinSize(minWidth = 48.dp)
            .padding(horizontal = 13.dp)
            .semantics {
                this.selected = selected
                role = Role.Tab
            }
            .testTag(destination.testTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp)
        )

        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(animationSpec = ExpressiveMotion.FastSpatialSize) +
                fadeIn(ExpressiveMotion.FastEffects),
            exit = shrinkHorizontally(animationSpec = ExpressiveMotion.FastSpatialSize) +
                fadeOut(ExpressiveMotion.FastEffects)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}