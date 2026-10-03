package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import com.example.ui.components.expressive.ExpressiveArtworkPlaceholder
import com.example.ui.components.expressive.ExpressiveContainedLoadingIndicator
import com.example.ui.components.expressive.ExpressiveCountBadge
import com.example.ui.components.expressive.ExpressiveEmptyOrb
import com.example.ui.components.expressive.ExpressiveIconBadge
import com.example.ui.components.expressive.ExpressiveLoadingIndicator
import com.example.ui.components.expressive.ExpressiveLoadingOverlay
import com.example.ui.components.expressive.ExpressivePolygonSegmentedBar
import com.example.ui.components.expressive.ExpressivePrimaryButton
import com.example.ui.components.expressive.ExpressiveProgressRow
import com.example.ui.components.expressive.ExpressiveSegmentedBar
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.components.expressive.ExpressiveStateChip
import com.example.ui.components.expressive.ExpressiveTabBar
import com.example.ui.components.expressive.SegmentedOption
import com.example.ui.components.expressive.WavyProgressBar
import com.example.ui.components.expressive.expressiveShape

// Re-export typealias and symbols for backward compatibility
typealias SegmentedOption = com.example.ui.components.expressive.SegmentedOption
typealias ExpressiveShapes = com.example.ui.components.expressive.ExpressiveShapes

@Composable
fun ExpressiveLoadingIndicator(modifier: Modifier = Modifier, color: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary) =
    com.example.ui.components.expressive.ExpressiveLoadingIndicator(modifier, color)

@Composable
fun ExpressiveContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
    indicatorColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary
) = com.example.ui.components.expressive.ExpressiveContainedLoadingIndicator(modifier, containerColor, indicatorColor)

@Composable
fun WavyProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    trackColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
) = com.example.ui.components.expressive.WavyProgressBar(progress, modifier, color, trackColor)

@Composable
fun ExpressiveProgressRow(label: String, detail: String, progress: Float, modifier: Modifier = Modifier) =
    com.example.ui.components.expressive.ExpressiveProgressRow(label, detail, progress, modifier)

@Composable
fun expressiveShape(polygon: RoundedPolygon) = com.example.ui.components.expressive.expressiveShape(polygon)

@Composable
fun ExpressiveTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = List(tabs.size) { null }
) =
    com.example.ui.components.expressive.ExpressiveTabBar(tabs, selectedIndex, onSelect, modifier, icons)

@Composable
fun ExpressivePolygonSegmentedBar(options: List<SegmentedOption>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    com.example.ui.components.expressive.ExpressivePolygonSegmentedBar(options, selectedIndex, onSelect, modifier)

@Composable
fun ExpressiveStateChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContentColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
) = com.example.ui.components.expressive.ExpressiveStateChip(
    label, selected, onClick, modifier, containerColor, selectedContainerColor, contentColor, selectedContentColor
)

@Composable
fun ExpressivePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true
) = com.example.ui.components.expressive.ExpressivePrimaryButton(text, onClick, modifier, icon, loading, enabled)

@Composable
fun ExpressiveIconBadge(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) = com.example.ui.components.expressive.ExpressiveIconBadge(icon, contentDescription, onClick, modifier, badge)

@Composable
fun ExpressiveSegmentedBar(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    com.example.ui.components.expressive.ExpressiveSegmentedBar(options, selectedIndex, onSelect, modifier)

@Composable
fun ExpressiveCountBadge(count: Int, modifier: Modifier = Modifier) =
    com.example.ui.components.expressive.ExpressiveCountBadge(count, modifier)

@Composable
fun ExpressiveArtworkPlaceholder(modifier: Modifier = Modifier) =
    com.example.ui.components.expressive.ExpressiveArtworkPlaceholder(modifier)

@Composable
fun ExpressiveLoadingOverlay(message: String, modifier: Modifier = Modifier) =
    com.example.ui.components.expressive.ExpressiveLoadingOverlay(message, modifier)

@Composable
fun ExpressiveEmptyOrb(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
    iconTint: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
) = com.example.ui.components.expressive.ExpressiveEmptyOrb(icon, modifier, containerColor, iconTint)

@Composable
fun ExpressiveButtonGroup(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    com.example.ui.components.expressive.ExpressiveSegmentedBar(labels, selectedIndex, onSelect, modifier)
