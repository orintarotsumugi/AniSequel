package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.cards.toCoverColorOrNull
import com.example.ui.components.expressive.ExpressiveShapes

/**
 * The anime the user chose to stop being reminded about, and the only way back.
 *
 * This exists because hiding was a one-way door. "Not interested" persisted an
 * AniList id and told the user to *"find it under Filters > Hidden"* - a place
 * that was never built. The gesture worked, the promise did not, and the only
 * route back to a hidden anime was clearing the app's data, which also threw
 * away their theme, AniList client id and everything else stored alongside it.
 *
 * The list is driven by the ViewModel's `hiddenSequels`, which is the hidden
 * half of the same candidate list the dashboard draws from. That matters: the
 * rows carry a real title, poster and parent rather than a bare media id, and
 * they stay populated regardless of what the search box and status chips are
 * doing, because none of those filters apply to a standing decision.
 *
 * Collapsed by default. It is a rarely-wanted list, and the section header
 * doubles as the count of how many entries it holds - which is the number
 * someone needs in order to decide whether to open it at all.
 */
@Composable
fun HiddenSequelsSection(
    hiddenSequels: List<MissedSequel>,
    onRestoreOne: (MissedSequel) -> Unit,
    onRestoreAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Guarded by the list being non-empty rather than checked at each call
    // site: restoring the last row from inside the expanded body would
    // otherwise leave an open, empty section behind.
    AnimatedVisibility(
        visible = hiddenSequels.isNotEmpty(),
        // `IntSize` explicitly, not left to inference: passing a Dp threshold
        // pins the spring to SpringSpec<Dp>, which is not the
        // FiniteAnimationSpec<IntSize> these two transitions take.
        enter = expandVertically(
            animationSpec = spring<IntSize>(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0
            )
        ) + fadeIn(tween(200)),
        exit = shrinkVertically(
            animationSpec = spring<IntSize>(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0
            )
        ) + fadeOut(tween(150))
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("hidden_sequels_section")
        ) {
            HiddenSectionHeader(
                count = hiddenSequels.size,
                expanded = expanded,
                onToggle = { expanded = !expanded }
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring<IntSize>(
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = 0
                    )
                ) + fadeIn(tween(180)),
                exit = shrinkVertically(
                    animationSpec = spring<IntSize>(
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = 0
                    )
                ) + fadeOut(tween(120))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "These are never offered again until you restore them. " +
                            "Hiding does not change anything on your AniList account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    hiddenSequels.forEach { sequel ->
                        HiddenSequelRow(
                            sequel = sequel,
                            onRestore = { onRestoreOne(sequel) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = onRestoreAll,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_all_hidden_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Show all ${hiddenSequels.size} again",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * The collapsible header: a count, the label, and a chevron that rotates.
 *
 * Rotates the chevron rather than swapping two icons so the affordance reads as
 * one control opening and closing, which is what it is.
 */
@Composable
private fun HiddenSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "hidden_chevron_rotation"
    )
    val container by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = spring(),
        label = "hidden_header_container"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.pillSoft)
            .background(container)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("hidden_section_header"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Hidden anime",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        SurfaceCount(count = count)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = AppVectorIcons.SequelJump,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(16.dp)
                .rotate(chevronRotation)
        )
    }
}

/** The badge showing how many entries are hidden. */
@Composable
private fun SurfaceCount(count: Int, modifier: Modifier = Modifier) {
    val container by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.tertiaryContainer,
        animationSpec = spring(),
        label = "hidden_count_container"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag("hidden_count_badge"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

/**
 * One hidden anime, with the single control that brings it back.
 *
 * No exit animation of its own: restoring removes the row from [hiddenSequels],
 * which disposes this composable outright, so an `AnimatedVisibility` here
 * would never get to play its exit - it would only ever add an enter animation
 * for a row that is already on screen. The whole section does animate open and
 * closed, which is where the motion actually reads.
 */
@Composable
private fun HiddenSequelRow(
    sequel: MissedSequel,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onRestore)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("hidden_row_${sequel.sequelId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A plain tinted placeholder rather than a network image: the poster
        // URLs are already in hand for these rows, but this section sits inside
        // a scrollable sheet that a user opens precisely to *find* something,
        // and a list of small thumbnails decoding while it scrolls is a worse
        // trade than a colour block keyed to the entry's own key visual.
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    sequel.coverColor.toCoverColorOrNull()
                        ?: MaterialTheme.colorScheme.surfaceContainerHighest
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sequel.sequelTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${sequel.relationLabel} ${sequel.parentTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Restore",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.Filled.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}
