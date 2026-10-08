package com.example.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.ArrivingEntry
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveCountBadge
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.bouncyPress

/**
 * The viewer's own list, split out into what is airing now and what has not
 * started yet.
 *
 * Deliberately its own section rather than a group at the top of the missed
 * list: the two answer different questions. Missed sequels are gaps the app
 * found by walking relation edges and every filter in the sheet applies to
 * them; these rows are things the viewer already tracks, and filtering them
 * would be answering "what am I missing?" with "here is something you are not
 * missing". So this section ignores the filter criteria entirely and hides
 * itself when there is nothing to report.
 *
 * Includes an interactive collapse/expand toggle button with smooth spring
 * animation so users can tuck it away if they want to focus immediately on
 * missed sequels.
 */
@Composable
fun ArrivingSection(
    entries: List<ArrivingEntry>,
    maxWidth: Dp,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    var isExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .testTag("arriving_section")
    ) {
        val interactionSource = remember { MutableInteractionSource() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (isExpanded) "Collapse Currently arriving" else "Expand Currently arriving",
                    onClick = { isExpanded = !isExpanded }
                )
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("arriving_section_header"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = "Currently arriving",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                ExpressiveCountBadge(count = entries.size)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "From your list",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .size(36.dp)
                        .bouncyPress(pressedScale = 0.9f)
                        .testTag("arriving_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) AppVectorIcons.ExpandLess else AppVectorIcons.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse Currently arriving" else "Expand Currently arriving",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = ExpressiveMotion.FastSpatialSize
            ) + fadeIn(animationSpec = ExpressiveMotion.FastEffects),
            exit = shrinkVertically(
                animationSpec = ExpressiveMotion.FastSpatialSize
            ) + fadeOut(animationSpec = ExpressiveMotion.FastEffects)
        ) {
            val context = LocalContext.current
            if (compact) {
                // Compact rows draw no artwork and carry no live countdown: the
                // poster fetch is the whole data and battery cost of these cards,
                // and the countdown pill is the one number that goes stale while
                // the screen is up. A compact section therefore loads nothing and
                // recomposes nothing on a schedule.
                CompactArrivingList(
                    entries = entries,
                    onEntryClick = { openOnAniList(context, it) }
                )
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("arriving_lazy_row"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(items = entries, key = { it.mediaId }) { entry ->
                        ArrivingCard(
                            entry = entry,
                            onClick = { openOnAniList(context, entry) },
                            modifier = Modifier.animateItem(
                                placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                                fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                                fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                            )
                        )
                    }
                }
            }
        }
    }
}
