package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.FilterCriteria
import com.example.data.model.StatusFilter
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveSegmentedBar

/**
 * The one-tap filters.
 *
 * This was a horizontally scrolling row of `FilterChip`s, which had two
 * problems: the row could be scrolled half off-screen so the format filters
 * ("TV series", "Movies") were unreachable without discovering the swipe, and
 * nothing on screen said which filters were currently narrowing the results.
 *
 * It is now a connected segmented control - one shape, not a strip of separate
 * pills - with the active filter restated underneath it, plus an explicit
 * control to clear everything.
 */
@Composable
fun QuickFilterBar(
    filterCriteria: FilterCriteria,
    onStatusSelected: (StatusFilter) -> Unit,
    onFormatSelected: (String?) -> Unit,
    onClearAll: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Four segments, not six. This is a fixed-width segmented control, so the two
    // rarer states - On Hiatus and Cancelled - live in the filter sheet, which
    // draws every value in `StatusFilter`. Crowding them in here would leave six
    // labels sharing one capsule, and "Cancelled" is the one that would truncate.
    val statuses = listOf(StatusFilter.ALL, StatusFilter.RELEASING, StatusFilter.FINISHED, StatusFilter.NOT_YET_RELEASED)
    val statusLabels = listOf("All", "Airing", "Finished", "Upcoming")

    // The selected index follows the criteria rather than being stored
    // separately, so the control cannot drift out of sync with the filter it
    // claims to represent.
    //
    // -1 when the active filter is one this bar does not offer. The old
    // `coerceAtLeast(0)` turned that into 0, so a list narrowed to "On Hiatus"
    // showed **All** highlighted - the bar claiming nothing was filtered while
    // the sheet was filtering by a status the bar has no segment for. A `-1`
    // selects nothing, which is the honest picture; the row underneath says
    // which status it is.
    val selectedIndex = statuses.indexOf(filterCriteria.statusFilter)

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("quick_filter_bar")
    ) {
        ExpressiveSegmentedBar(
            options = statusLabels,
            selectedIndex = selectedIndex,
            onSelect = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onStatusSelected(statuses[it])
            }
        )

        val activeFormats = listOf("TV" to "TV series", "MOVIE" to "Movies")
            .filter { (key, _) -> filterCriteria.selectedFormat == key }
            .map { (_, label) -> label }

        // The active status is restated only when the bar above cannot show it.
        // A status the segmented control already highlights would otherwise be
        // printed twice on the same line.
        val activeStatus = filterCriteria.statusFilter
            .takeIf { it != StatusFilter.ALL && selectedIndex < 0 }
            ?.displayName
        val activeFilters = listOfNotNull(activeStatus) + activeFormats

        // The shared predicate rather than a local copy. This row's own
        // version used to look at status, search and "include unreleased" only,
        // so a viewer who hid already-planned entries - or widened the relation
        // set - saw no "Filtered" row and no clear button, with no way to tell
        // why the list was shorter than they expected.
        val hasNarrowing = filterCriteria.isNarrowing

        if (hasNarrowing) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (activeFilters.isEmpty()) {
                            "Filtered"
                        } else {
                            "Filtered · ${activeFilters.joinToString(", ")}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onClearAll()
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("clear_filters_button")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Clear all filters",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}