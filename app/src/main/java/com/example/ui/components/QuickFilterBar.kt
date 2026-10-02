package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
    val statuses = listOf(StatusFilter.ALL, StatusFilter.RELEASING, StatusFilter.FINISHED, StatusFilter.NOT_YET_RELEASED)
    val statusLabels = listOf("All", "Airing", "Finished", "Upcoming")

    // The selected index follows the criteria rather than being stored
    // separately, so the control cannot drift out of sync with the filter it
    // claims to represent.
    val selectedIndex = statuses.indexOf(filterCriteria.statusFilter).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("quick_filter_bar")
    ) {
        ExpressiveSegmentedBar(
            options = statusLabels,
            selectedIndex = selectedIndex,
            onSelect = { onStatusSelected(statuses[it]) }
        )

        val activeFormats = listOf("TV" to "TV series", "MOVIE" to "Movies")
            .filter { (key, _) -> filterCriteria.selectedFormat == key }
            .map { (_, label) -> label }

        val hasNarrowing = activeFormats.isNotEmpty() ||
            filterCriteria.statusFilter != StatusFilter.ALL ||
            filterCriteria.searchQuery.isNotBlank() ||
            !filterCriteria.includeUnreleased

        if (hasNarrowing) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (activeFormats.isEmpty()) {
                            "Filtered"
                        } else {
                            "Filtered · ${activeFormats.joinToString(", ")}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }

                // Without this, picking a filter was a one-way door: the chips
                // could be changed back one at a time but there was no single
                // way to return to "everything".
                IconButton(
                    onClick = onClearAll,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("clear_filters_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear all filters",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}