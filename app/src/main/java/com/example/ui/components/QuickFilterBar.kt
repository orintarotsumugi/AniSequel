package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FilterCriteria
import com.example.data.model.StatusFilter

@Composable
fun QuickFilterBar(
    filterCriteria: FilterCriteria,
    onStatusSelected: (StatusFilter) -> Unit,
    onFormatSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("quick_filter_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // All
        val isAll = filterCriteria.statusFilter == StatusFilter.ALL && filterCriteria.selectedFormat == null
        FilterChip(
            selected = isAll,
            onClick = {
                onStatusSelected(StatusFilter.ALL)
                onFormatSelected(null)
            },
            label = { Text("All", fontSize = 13.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        // Currently Airing
        val isAiring = filterCriteria.statusFilter == StatusFilter.RELEASING
        FilterChip(
            selected = isAiring,
            onClick = {
                if (isAiring) onStatusSelected(StatusFilter.ALL) else onStatusSelected(StatusFilter.RELEASING)
            },
            label = { Text("Airing Now", fontSize = 13.sp) },
            leadingIcon = if (isAiring) {
                { Icon(imageVector = AppVectorIcons.Done, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else null
        )

        // Finished
        val isFinished = filterCriteria.statusFilter == StatusFilter.FINISHED
        FilterChip(
            selected = isFinished,
            onClick = {
                if (isFinished) onStatusSelected(StatusFilter.ALL) else onStatusSelected(StatusFilter.FINISHED)
            },
            label = { Text("Finished", fontSize = 13.sp) },
            leadingIcon = if (isFinished) {
                { Icon(imageVector = AppVectorIcons.Done, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else null
        )

        // Upcoming / Unreleased
        val isUpcoming = filterCriteria.statusFilter == StatusFilter.NOT_YET_RELEASED
        FilterChip(
            selected = isUpcoming,
            onClick = {
                if (isUpcoming) onStatusSelected(StatusFilter.ALL) else onStatusSelected(StatusFilter.NOT_YET_RELEASED)
            },
            label = { Text("Upcoming", fontSize = 13.sp) },
            leadingIcon = if (isUpcoming) {
                { Icon(imageVector = AppVectorIcons.Done, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else null
        )

        // TV Format
        val isTv = filterCriteria.selectedFormat == "TV"
        FilterChip(
            selected = isTv,
            onClick = {
                if (isTv) onFormatSelected(null) else onFormatSelected("TV")
            },
            label = { Text("TV Series", fontSize = 13.sp) },
            leadingIcon = if (isTv) {
                { Icon(imageVector = AppVectorIcons.Done, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else null
        )

        // Movie Format
        val isMovie = filterCriteria.selectedFormat == "MOVIE"
        FilterChip(
            selected = isMovie,
            onClick = {
                if (isMovie) onFormatSelected(null) else onFormatSelected("MOVIE")
            },
            label = { Text("Movies", fontSize = 13.sp) },
            leadingIcon = if (isMovie) {
                { Icon(imageVector = AppVectorIcons.Done, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else null
        )
    }
}
