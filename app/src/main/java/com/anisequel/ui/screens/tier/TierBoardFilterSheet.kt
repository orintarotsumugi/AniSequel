package com.anisequel.ui.screens.tier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.anisequel.domain.tier.TierAiringStatusOptions
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierFacetOption
import com.anisequel.domain.tier.TierFilter
import com.anisequel.domain.tier.TierListStatusOptions
import com.anisequel.domain.tier.TierStatusOption
import com.anisequel.domain.tier.genreFacetOptions
import com.anisequel.domain.tier.studioFacetOptions
import com.anisequel.ui.components.expressive.ExpressiveStateChip

/**
 * Which list and airing statuses are on the board, and which genres and studios
 * narrow it further.
 *
 * A chip is *selected when the status is shown*, not when it is hidden. That is
 * the reading people arrive with - a row of lit chips means "these are in" - and
 * the alternative ("lit means hidden") makes the default state, with planning
 * unlit, look like the only thing the screen is doing is hiding something. The
 * sentence above the chips says it outright anyway, because a control whose
 * polarity has to be inferred is a control that gets used backwards.
 *
 * The genre section is always present; the studio section is off until switched
 * on. Studios are far more numerous than genres and putting a hundred chips over
 * the board for a filter most boards never need would bury the three sections
 * that are always in use.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TierFilterSheet(
    filter: TierFilter,
    candidates: List<TierCandidate>,
    onFilterChange: (TierFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val genres = remember(candidates) { genreFacetOptions(candidates) }
    val studios = remember(candidates) { studioFacetOptions(candidates) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A sheet with every genre and studio on a 300-title list is
                // taller than the screen. Bounding it and scrolling the content
                // keeps the drag-to-dismiss gesture on the sheet itself instead
                // of turning the bottom of the list into a dead strip.
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .testTag("tier_filter_sheet")
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Unselected statuses are left out of the board and the unranked " +
                    "shelf. Genres and studios narrow the board further. Nothing is " +
                    "removed from your list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))
            TierFilterGroup(
                title = "On your list",
                options = TierListStatusOptions,
                hidden = filter.hiddenListStatuses,
                onToggle = { value, hide ->
                    onFilterChange(filter.withListStatusHidden(value, hide))
                }
            )

            Spacer(modifier = Modifier.height(18.dp))
            TierFilterGroup(
                title = "Airing",
                options = TierAiringStatusOptions,
                hidden = filter.hiddenAiringStatuses,
                onToggle = { value, hide ->
                    onFilterChange(filter.withAiringStatusHidden(value, hide))
                }
            )

            Spacer(modifier = Modifier.height(18.dp))
            TierFacetGroup(
                title = "Genres",
                description = "Titles matching any selected genre are shown.",
                options = genres,
                selected = filter.genres,
                emptyText = "No genres were returned for this list.",
                onToggle = { value, selectedNow ->
                    onFilterChange(filter.withGenre(value, selectedNow))
                },
                onClear = {
                    onFilterChange(filter.copy(genres = emptySet()))
                }
            )

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Studios",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Off by default. Sorted by how many titles in your " +
                            "list each studio made.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = filter.studiosEnabled,
                    onCheckedChange = { enabled ->
                        onFilterChange(filter.withStudiosEnabled(enabled))
                    },
                    modifier = Modifier.testTag("tier_studio_switch")
                )
            }

            if (filter.studiosEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                TierFacetChips(
                    options = studios,
                    selected = filter.studios,
                    emptyText = "No animation studios were returned for this list.",
                    onToggle = { value, selectedNow ->
                        onFilterChange(filter.withStudio(value, selectedNow))
                    }
                )
                if (filter.studios.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onFilterChange(filter.copy(studios = emptySet()))
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Clear studios")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onFilterChange(TierFilter.Default) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Default")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { onFilterChange(TierFilter.ShowEverything) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Show everything", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * One facet section: its heading, a clear action when anything is selected, and
 * the chips themselves.
 *
 * [onToggle] receives the new selected state rather than the old one, so the
 * sheet does not have to reconstruct `selected` for every chip and the filter
 * object stays the only owner of the answer.
 */
@Composable
private fun TierFacetGroup(
    title: String,
    description: String,
    options: List<TierFacetOption>,
    selected: Set<String>,
    emptyText: String,
    onToggle: (value: String, selected: Boolean) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected.isNotEmpty()) {
            TextButton(
                onClick = onClear,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Clear")
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    TierFacetChips(
        options = options,
        selected = selected,
        emptyText = emptyText,
        onToggle = onToggle
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierFacetChips(
    options: List<TierFacetOption>,
    selected: Set<String>,
    emptyText: String,
    onToggle: (value: String, selected: Boolean) -> Unit
) {
    if (options.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option.value in selected
            // The count is in the label on purpose: at the top of the studio
            // list it answers "which studio have I watched most" in the same
            // glance as the name, and it is the sort key the section uses.
            ExpressiveStateChip(
                label = "${option.label} (${option.count})",
                selected = isSelected,
                onClick = { onToggle(option.value, !isSelected) },
                minTouchTargetHeight = 48.dp
            )
        }
    }
}

@Composable
private fun TierFilterGroup(
    title: String,
    options: List<TierStatusOption>,
    hidden: Set<String>,
    onToggle: (value: String, hide: Boolean) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Two columns built from `chunked` rather than `FlowRow`: the chips are
        // short and the set is fixed, so a flow layout's wrapping would make two
        // lists of different lengths look like different controls.
        options.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { option ->
                    val shown = option.value !in hidden
                    ExpressiveStateChip(
                        label = option.label,
                        selected = shown,
                        // Tapping a shown chip hides it, and the other way round.
                        // `shown` is the current state, which is exactly the new
                        // "hidden" value.
                        onClick = { onToggle(option.value, shown) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        minTouchTargetHeight = 48.dp
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
