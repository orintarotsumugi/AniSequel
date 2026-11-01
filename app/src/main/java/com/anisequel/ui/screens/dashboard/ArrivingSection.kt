package com.anisequel.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.ArrivingEntry
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveCountBadge
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.rememberIsResumed
import kotlinx.coroutines.delay

/** A minute, because the countdown's smallest unit is a minute. */
private const val ARRIVING_COUNTDOWN_TICK_MILLIS = 60_000L

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
 * The shipped layout is **one row**, airing first. The two-group layout that
 * preceded it spent a second heading and a second strip of vertical space above
 * the recommendations, and the cards already carry the Airing / Upcoming
 * distinction in their own status chip and container colour. Splitting the
 * groups is still offered in Settings for readers who want the headings.
 *
 * Includes an interactive collapse/expand toggle with a spring-driven chevron
 * so the section can be tucked away when the reader wants the recommendations
 * immediately.
 */
@Composable
fun ArrivingSection(
    entries: List<ArrivingEntry>,
    maxWidth: Dp,
    onEntryClick: (ArrivingEntry) -> Unit,
    compact: Boolean = false,
    separateGroups: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    var isExpanded by rememberSaveable { mutableStateOf(true) }
    val rows = remember(entries, separateGroups) { arrivingRows(entries, separateGroups) }

    Column(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .testTag("arriving_section")
    ) {
        ArrivingSectionHeader(
            expanded = isExpanded,
            count = entries.size,
            onToggle = { isExpanded = !isExpanded }
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = ExpressiveMotion.FastSpatialSize
            ) + fadeIn(animationSpec = ExpressiveMotion.FastEffects),
            exit = shrinkVertically(
                animationSpec = ExpressiveMotion.FastSpatialSize
            ) + fadeOut(animationSpec = ExpressiveMotion.FastEffects)
        ) {
            if (compact) {
                // Compact rows draw no artwork and carry no live countdown: the
                // poster fetch is the whole data and battery cost of these cards,
                // and the countdown pill is the one number that goes stale while
                // the screen is up. A compact section therefore loads nothing and
                // recomposes nothing on a schedule.
                CompactArrivingList(
                    rows = rows,
                    onEntryClick = onEntryClick
                )
            } else {
                // One clock for the whole section, started only when an airing
                // entry actually has something to count down to and stopped when
                // the section is collapsed or the app is not resumed.
                val nowMillis = rememberArrivingNow(
                    hasCountdown = rows.any { row ->
                        row.entries.any { it.isAiring && it.nextAiringAt != null }
                    }
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    rows.forEach { row ->
                        ArrivingStrip(
                            row = row,
                            nowMillis = nowMillis,
                            onEntryClick = onEntryClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * The collapsible header: label, count, and a chevron that rotates.
 *
 * The chevron rotates rather than swapping two vectors so the affordance reads
 * as one control opening and closing. Rotation is applied in the draw phase; a
 * `Modifier.rotate(angle)` would read the animation during composition and
 * re-run layout on every frame of the turn.
 *
 * The whole row is the only click target. It used to contain a separate
 * `IconButton` with the same action, which gave TalkBack two buttons for one
 * toggle and put two overlapping semantics nodes on the same strip.
 */
@Composable
private fun ArrivingSectionHeader(
    expanded: Boolean,
    count: Int,
    onToggle: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "arriving_chevron_rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) {
                    "Collapse Currently arriving"
                } else {
                    "Expand Currently arriving"
                },
                onClick = onToggle
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
            ExpressiveCountBadge(count = count)
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
            Icon(
                imageVector = AppVectorIcons.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }
    }
}

/**
 * One strip: an optional group label and its horizontal row of cards.
 *
 * In the combined layout [ArrivingRow.label] is null and no heading is drawn.
 * The cards themselves are the same in both layouts, which is what lets the
 * reader switch the grouping without relearning the card.
 */
@Composable
private fun ArrivingStrip(
    row: ArrivingRow,
    nowMillis: Long,
    onEntryClick: (ArrivingEntry) -> Unit
) {
    if (row.entries.isEmpty()) return

    if (row.label != null) {
        ArrivingGroupLabel(
            label = row.label,
            airing = row.airing,
            count = row.entries.size
        )
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                when {
                    row.label == null -> "arriving_lazy_row_combined"
                    row.airing -> "arriving_lazy_row_airing"
                    else -> "arriving_lazy_row_upcoming"
                }
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(items = row.entries, key = { it.mediaId }) { entry ->
            ArrivingCard(
                entry = entry,
                nowMillis = nowMillis,
                onClick = { onEntryClick(entry) },
                modifier = Modifier.animateItem(
                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                )
            )
        }
    }
}

/**
 * The capsule naming a separated group.
 *
 * Keyed to the group's own colour rather than to its first entry, so an empty or
 * mixed list can never colour the heading with the wrong half's role.
 */
@Composable
internal fun ArrivingGroupLabel(
    label: String,
    airing: Boolean,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = ExpressiveShapes.pill,
            color = if (airing) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (airing) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The section's clock, shared by every countdown pill.
 *
 * One `produceState` for the row rather than one per card: a reader with eight
 * airing shows would otherwise keep eight independent minute timers, and two
 * cards could disagree about the same minute. It is gated on `rememberIsResumed`
 * for the same reason every other clock in the app is - a screen behind another
 * app has nobody reading its countdown.
 */
@Composable
private fun rememberArrivingNow(hasCountdown: Boolean): Long {
    val resumed = rememberIsResumed()
    val now by produceState(
        initialValue = System.currentTimeMillis(),
        hasCountdown,
        resumed
    ) {
        if (!resumed || !hasCountdown) return@produceState
        while (true) {
            value = System.currentTimeMillis()
            delay(ARRIVING_COUNTDOWN_TICK_MILLIS)
        }
    }
    return now
}
