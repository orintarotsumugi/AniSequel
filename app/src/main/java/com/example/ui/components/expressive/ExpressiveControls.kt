package com.example.ui.components.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val EXPRESSIVE_TAG = "expressive_"

/** One segment item of [ExpressivePolygonSegmentedBar]. */
data class SegmentedOption(
    val label: String,
    val icon: ImageVector? = null
)

/**
 * Material 3 Expressive tab bar with spring physics and morphing pill shapes.
 */
@Composable
fun ExpressiveTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("${EXPRESSIVE_TAG}tab_bar"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex

            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.94f,
                animationSpec = spring(
                    dampingRatio = 0.65f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "tab_scale_$label"
            )
            val container by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = spring(),
                label = "tab_container_$label"
            )
            val content by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = spring(),
                label = "tab_content_$label"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(expressiveShape(ExpressiveShapes.pill))
                    .background(container)
                    .clickable { onSelect(index) }
                    .semantics {
                        this.selected = selected
                        role = Role.Tab
                    }
                    .testTag("${EXPRESSIVE_TAG}tab_$label"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * A segmented bar styled with MaterialShapes polygons and vector icons.
 */
@Composable
fun ExpressivePolygonSegmentedBar(
    options: List<SegmentedOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(expressiveShape(ExpressiveShapes.pill))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp)
            .testTag("${EXPRESSIVE_TAG}polygon_segmented_bar"),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex

            val container by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                animationSpec = spring(dampingRatio = 0.7f),
                label = "segment_container_${option.label}"
            )
            val content by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = spring(),
                label = "segment_content_${option.label}"
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(expressiveShape(ExpressiveShapes.pillSoft))
                    .background(container)
                    .clickable { onSelect(index) }
                    .semantics {
                        this.selected = selected
                        role = Role.RadioButton
                    }
                    .testTag("${EXPRESSIVE_TAG}polygon_segment_${option.label}"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (option.icon != null) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Segmented control with outer rounded edges and inner joint shapes.
 */
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
                    topStart = 14.dp, bottomStart = 14.dp, topEnd = 6.dp, bottomEnd = 6.dp
                )
                index == options.lastIndex && options.size > 1 -> RoundedCornerShape(
                    topStart = 6.dp, bottomStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp
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
                    .clickable { onSelect(index) }
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

/**
 * Filter chip whose corners and padding smoothly animate on selection.
 */
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
            .clickable(onClick = onClick)
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
 * Connected group of buttons sharing an outline and animated states.
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
            val radius = 12.dp
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
                    .clickable { onSelect(index) }
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
