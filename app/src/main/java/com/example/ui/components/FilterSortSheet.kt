package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FilterCriteria
import com.example.data.model.MissedSequel
import com.example.data.model.RelationKind
import com.example.data.model.SequelSortOption
import com.example.data.model.StatusFilter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSortSheet(
    sheetState: SheetState,
    filterCriteria: FilterCriteria,
    onSortSelected: (SequelSortOption) -> Unit,
    onStatusSelected: (StatusFilter) -> Unit,
    // The switches pass the value they are about to hold, not a bare toggle, so
    // assistive technology announcing the new state and the callback cannot
    // disagree.
    onToggleUnreleased: (Boolean) -> Unit,
    onToggleHidePlanned: (Boolean) -> Unit,
    onFormatSelected: (String?) -> Unit,
    onToggleRelation: (RelationKind) -> Unit,
    // The calendar-year axes, named for what they measure rather than for the
    // switch they drive: one is about the sequel's release date, the other
    // about the year the viewer finished its parent.
    onToggleSequelThisYear: (Boolean) -> Unit = {},
    onToggleParentCompletedThisYear: (Boolean) -> Unit = {},
    onToggleCurrentlyWatching: (Boolean) -> Unit = {},
    onToggleInList: (Boolean) -> Unit = {},
    /**
     * The entries the user chose to stop being reminded about, and the two ways
     * back out. Empty until the ViewModel has split its candidates, which is
     * why the section hides itself rather than rendering an empty list.
     */
    hiddenSequels: List<MissedSequel> = emptyList(),
    onRestoreHidden: (MissedSequel) -> Unit = {},
    onRestoreAllHidden: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("filter_sort_sheet"),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        // Read once rather than per recomposition; the sheet is not open long
        // enough for a New Year to matter, and the filter itself re-reads the
        // year when it runs.
        val currentYear = remember { java.time.LocalDate.now().year }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter & Sort Sequels",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("done_filter_button")
                ) {
                    Text("Done")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sort By Section
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SequelSortOption.entries.forEach { option ->
                    val isSelected = filterCriteria.sortOption == option
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSortSelected(option) },
                        label = { Text(option.displayName) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = AppVectorIcons.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Airing Status Section
            Text(
                text = "Airing status",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusFilter.entries.forEach { status ->
                    val isSelected = filterCriteria.statusFilter == status
                    FilterChip(
                        selected = isSelected,
                        onClick = { onStatusSelected(status) },
                        label = { Text(status.displayName) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = AppVectorIcons.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Media Format Section
            Text(
                text = "Media format",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            val formats = listOf(
                    "All" to null,
                    // Labelled the same way the quick filter bar labels it, so
                    // the same choice does not read as two different ones.
                    "TV series" to "TV",
                    "Movies" to "MOVIE",
                    "OVA" to "OVA",
                    "ONA" to "ONA",
                    "Special" to "SPECIAL"
                )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                formats.forEach { (label, formatKey) ->
                    val isSelected = filterCriteria.selectedFormat == formatKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFormatSelected(formatKey) },
                        label = { Text(label) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = AppVectorIcons.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Include Unreleased Sequels",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Show upcoming anime announced or not yet aired",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                Switch(
                    checked = filterCriteria.includeUnreleased,
                    onCheckedChange = onToggleUnreleased,
                    modifier = Modifier.testTag("toggle_unreleased_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hide Already in Planning",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Only show sequels not yet on your Planning list",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                Switch(
                    checked = filterCriteria.hideAlreadyPlanned,
                    onCheckedChange = onToggleHidePlanned,
                    modifier = Modifier.testTag("toggle_hide_planned_switch")
                )
            }

            // The two calendar-year axes. Off to the side of the one-tap
            // controls and reachable only from here: each one needs a sentence
            // to say what its year measures - the sequel's release date versus
            // the year the *viewer* finished its parent - and neither fits on
            // a segmented bar.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Releasing this year",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Only sequels coming out in $currentYear",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                Switch(
                    checked = filterCriteria.sequelReleasedThisYear,
                    onCheckedChange = onToggleSequelThisYear,
                    modifier = Modifier.testTag("toggle_sequel_this_year_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Completed this year",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Only sequels to shows you finished in $currentYear",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                Switch(
                    checked = filterCriteria.parentCompletedThisYear,
                    onCheckedChange = onToggleParentCompletedThisYear,
                    modifier = Modifier.testTag("toggle_parent_completed_this_year_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Include Currently Watching",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Find sequels you are currently watching or from anime in progress",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                Switch(
                    checked = filterCriteria.includeCurrentlyWatching,
                    onCheckedChange = onToggleCurrentlyWatching,
                    modifier = Modifier.testTag("toggle_currently_watching_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Include Sequels in My List",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Show sequels already tracked in your completed, paused, or dropped lists",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                Switch(
                    checked = filterCriteria.includeInList,
                    onCheckedChange = onToggleInList,
                    modifier = Modifier.testTag("toggle_in_list_switch")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // What counts as a gap worth surfacing. Sequels is the historical
            // behaviour and stays on by default; the rest widen the results
            // considerably, so they are opt-in rather than switched on silently.
            Text(
                text = "Include franchise gaps",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Prequels are earlier seasons you never watched; side stories and spin-offs are the entries the franchise branched into.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RelationKind.entries.forEach { relation ->
                    val selected = relation in filterCriteria.includedRelations
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleRelation(relation) },
                        label = { Text(relation.displayName) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = AppVectorIcons.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("relation_chip_${relation.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Last, and after every filter, because it is the one section that
            // is not a filter. The rest of this sheet changes what is on screen;
            // this is the list of decisions the user has already made, and the
            // only place in the app that can take one back.
            HiddenSequelsSection(
                hiddenSequels = hiddenSequels,
                onRestoreOne = onRestoreHidden,
                onRestoreAll = onRestoreAllHidden
            )
        }
    }
}
