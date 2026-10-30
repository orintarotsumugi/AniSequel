package com.anisequel.ui.components

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.FilterCriteria
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.RelationKind
import com.anisequel.data.model.SequelSortOption
import com.anisequel.data.model.StatusFilter
import com.anisequel.data.model.animeGenreOptions
import com.anisequel.data.model.filterEpisodeOptions
import com.anisequel.data.model.filterFormatOptions
import com.anisequel.data.model.filterScoreOptions
import com.anisequel.ui.components.expressive.ExpressiveMotion

/**
 * How many genres the sheet shows before the reader asks for the rest.
 *
 * Eight fills a little over two rows of chips on a phone. The remaining ten used
 * to push every filter below the genre section - minimum rating, the toggles,
 * the hidden list - a long scroll away from the top of the sheet, for chips most
 * readers never touch. A genre the reader has *selected* is never folded away:
 * a filter that is in effect must be visible, or the sheet hides why its results
 * are narrow.
 */
private const val GENRE_PREVIEW_COUNT = 8

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
    onToggleGenre: (String) -> Unit = {},
    onSelectMinScore: (Int?) -> Unit = {},
    /** The episode-count floor; null is "Any". See `filterEpisodeOptions`. */
    onSelectMinEpisodes: (Int?) -> Unit = {},
    /**
     * Puts every filter back to its default in place, without closing the
     * sheet. The quick bar's clear button is behind the sheet while it is open,
     * so the one action that undoes a stack of toggles used to require closing
     * the sheet, finding the clear button, and reopening the sheet to see the
     * result.
     */
    onResetFilters: () -> Unit = {},
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Only when there is something to reset: a Reset button on
                    // a sheet that already matches the defaults is a control
                    // promising to do nothing.
                    if (filterCriteria.isNarrowing) {
                        TextButton(
                            onClick = onResetFilters,
                            modifier = Modifier.testTag("reset_filters_in_sheet")
                        ) {
                            Text("Reset")
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("done_filter_button")
                    ) {
                        Text("Done")
                    }
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
                    FilterOptionChip(
                        label = option.displayName,
                        selected = filterCriteria.sortOption == option,
                        onClick = { onSortSelected(option) }
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
                    FilterOptionChip(
                        label = status.displayName,
                        selected = filterCriteria.statusFilter == status,
                        onClick = { onStatusSelected(status) }
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
            // The shared list, so the quick filter bar can name every choice
            // this row offers - see [filterFormatOptions].
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                filterFormatOptions.forEach { (formatKey, label) ->
                    FilterOptionChip(
                        label = label,
                        selected = filterCriteria.selectedFormat == formatKey,
                        onClick = { onFormatSelected(formatKey) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Genre, multi-select. OR semantics, stated in the subtitle
            // because "Action + Comedy" is ambiguous otherwise.
            Text(
                text = "Genre",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Show sequels in any of the genres you pick.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Folded by default, but a selected genre on the far side of the fold
            // is pulled up into the visible set: the chips are the only place the
            // sheet can show that a genre filter is narrowing the results.
            var genresExpanded by rememberSaveable { mutableStateOf(false) }
            val selectedFoldedGenres = remember(filterCriteria.selectedGenres) {
                animeGenreOptions
                    .drop(GENRE_PREVIEW_COUNT)
                    .filter { genre ->
                        filterCriteria.selectedGenres.any { it.equals(genre, ignoreCase = true) }
                    }
            }
            val visibleGenres = if (genresExpanded) {
                animeGenreOptions
            } else {
                animeGenreOptions.take(GENRE_PREVIEW_COUNT) + selectedFoldedGenres
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
            ) {
                visibleGenres.forEach { genre ->
                    FilterOptionChip(
                        label = genre,
                        selected = filterCriteria.selectedGenres.any {
                            it.equals(genre, ignoreCase = true)
                        },
                        onClick = { onToggleGenre(genre) },
                        testTag = "genre_chip_${genre.lowercase().replace(' ', '_')}"
                    )
                }
            }
            if (animeGenreOptions.size > GENRE_PREVIEW_COUNT) {
                TextButton(
                    onClick = { genresExpanded = !genresExpanded },
                    modifier = Modifier.testTag("genre_expand_toggle")
                ) {
                    Text(
                        if (genresExpanded) {
                            "Show fewer genres"
                        } else {
                            "Show all ${animeGenreOptions.size} genres"
                        }
                    )
                    Icon(
                        imageVector = if (genresExpanded) {
                            AppVectorIcons.ExpandLess
                        } else {
                            AppVectorIcons.ExpandMore
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Minimum rating. A lower bound rather than a band, and unrated
            // titles drop out - both stated, because "80+" could plausibly
            // mean either "80 to 100" or "at least 80", and a reader whose
            // one unrated favourite vanished deserves to know why.
            Text(
                text = "Minimum rating",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Only sequels AniList rates at least this high. Unrated titles drop out while a minimum is set.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                filterScoreOptions.forEach { (score, label) ->
                    FilterOptionChip(
                        label = label,
                        selected = filterCriteria.minScore == score,
                        onClick = { onSelectMinScore(score) },
                        testTag = "score_chip_${score ?: "any"}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Episode floor. A separate scalar from the score floor because the
            // two answer different questions - "is it good" and "is it long" -
            // and a reader looking for a full cour should not have to hope the
            // short entries happen to score lower.
            Text(
                text = "Minimum episodes",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Only sequels AniList lists at least this many episodes. \"12+\" " +
                    "is one cour or more; titles with no announced count drop out.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                filterEpisodeOptions.forEach { (episodes, label) ->
                    FilterOptionChip(
                        label = label,
                        selected = filterCriteria.minEpisodes == episodes,
                        onClick = { onSelectMinEpisodes(episodes) },
                        testTag = "episode_chip_${episodes ?: "any"}"
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
                    FilterOptionChip(
                        label = relation.displayName,
                        selected = relation in filterCriteria.includedRelations,
                        onClick = { onToggleRelation(relation) },
                        testTag = "relation_chip_${relation.name}"
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

/**
 * One selectable choice in the sheet.
 *
 * Built once because the sheet draws six sections of them, and six copies of
 * the same `leadingIcon` lambda is how one section ends up without a checkmark.
 * The check appears only when selected - the M3 filter-chip convention - and
 * the chip animates its own container itself, so no extra motion is layered on
 * top.
 */
@Composable
private fun FilterOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = AppVectorIcons.Done,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        modifier = if (testTag == null) Modifier else Modifier.testTag(testTag)
    )
}
