package com.anisequel.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.NavigationDestination
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The new look's bottom bar.
 *
 * The classic bar is an expressive capsule whose selected tab grows into a
 * labelled pill. This one keeps that idea and changes the composition: the
 * selected destination is a filled capsule with its label, the rest are quiet
 * icons, and the whole thing is a translucent shelf rather than a solid one. The
 * destinations, the tap handler and the selected index are still [MainTab]'s -
 * only the drawing is new - so a tab cannot mean one page in one look and a
 * different page in the other.
 *
 * The label expands rather than being cross-faded in place. `AnimatedVisibility`
 * with `expandHorizontally` lets the bar's own weight arithmetic do the
 * animation, so the neighbouring icons slide rather than jump when a tab is
 * chosen - and it is why no width is measured or animated by hand here.
 */
@Composable
internal fun NovaMainBar(
    destinations: List<NavigationDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
        ),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = index == selectedIndex
                val container by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0f)
                    },
                    animationSpec = ExpressiveMotion.FastColorEffects,
                    label = "nova_tab_container_$index"
                )
                val content = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val interactionSource = remember { MutableInteractionSource() }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(ExpressiveShapes.pill)
                        .background(container)
                        .bouncyPress(
                            pressedScale = 0.94f,
                            interactionSource = interactionSource
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClickLabel = "Open ${destination.label}"
                        ) { onSelect(index) }
                        .semantics { role = Role.Tab }
                        .testTag(destination.testTag)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (destination.iconUrl != null) {
                        AsyncImage(
                            model = destination.iconUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = selected,
                        enter = expandHorizontally(
                            animationSpec = ExpressiveMotion.FastSpatialSize
                        ) + fadeIn(animationSpec = ExpressiveMotion.FastEffects),
                        exit = shrinkHorizontally(
                            animationSpec = ExpressiveMotion.FastSpatialSize
                        ) + fadeOut(animationSpec = ExpressiveMotion.FastEffects)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = content,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
