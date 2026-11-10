package com.anisequel.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.domain.library.LibraryAdvancedFilters
import com.anisequel.domain.library.libraryFormatOptions
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.screens.profile.LibrarySort
import com.anisequel.ui.screens.profile.selectSort

/**
 * The advanced filter sheet behind the new Library's filter button.
 *
 * The screenshot the reader asked for has two jobs in one sheet: choose an order,
 * then narrow the list. Keeping them together means a search that returns "Jujutsu
 * Kaisen" eleven times can be reduced to the season they meant without closing one
 * sheet to open another, and Reset can undo both in one tap.
 *
 * Drafts are local. A sheet that writes every chip straight through repaints a
 * 300-entry grid on each tap and has no cancel; Apply is what makes the draft
 * cheap, and Reset becomes a real control rather than a button that has already
 * happened by the time it is drawn.
 *
 * Each section is its own composable for the reason the Library split its chrome
 * from its cards: a section is one question with one answer shape, and the sheet
 * itself is only the draft, the scroll and the two buttons.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterSheet(
    sheetState: SheetState,
    filters: LibraryAdvancedFilters,
    sort: LibrarySort,
    ascending: Boolean,
    years: List<Int>,
    genres: List<String>,
    onApply: (LibraryAdvancedFilters, LibrarySort, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    var draftSort by remember(sort) { mutableStateOf(sort) }
    var draftAscending by remember(ascending) { mutableStateOf(ascending) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Filters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sort, then narrow this list",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (draft.isActive) {
                    Text(
                        text = "${draft.activeCount} active",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                SortSection(
                    sort = draftSort,
                    ascending = draftAscending,
                    onSortChange = { nextSort, nextAscending ->
                        draftSort = nextSort
                        draftAscending = nextAscending
                    }
                )

                FilterDivider()
                FormatSection(draft = draft, onDraftChange = { draft = it })

                if (years.isNotEmpty()) {
                    FilterDivider()
                    YearSection(
                        years = years,
                        selected = draft.releaseYear,
                        onSelect = { year -> draft = draft.copy(releaseYear = year) }
                    )
                }

                FilterDivider()
                ScoreSection(
                    selected = draft.minScore,
                    onSelect = { score -> draft = draft.copy(minScore = score) }
                )

                if (genres.isNotEmpty()) {
                    FilterDivider()
                    GenreSection(genres = genres, draft = draft, onDraftChange = { draft = it })
                }

                FilterDivider()
                FavouritesRow(
                    checked = draft.onlyFavourites,
                    onCheckedChange = { draft = draft.copy(onlyFavourites = it) }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        draft = LibraryAdvancedFilters()
                        draftSort = LibrarySort.UPDATED_AT
                        draftAscending = false
                    },
                    shape = ExpressiveShapes.pill,
                    modifier = Modifier.weight(0.42f)
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = { onApply(draft, draftSort, draftAscending) },
                    shape = ExpressiveShapes.pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(0.58f)
                ) {
                    Text(
                        text = "Apply filters",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortSection(
    sort: LibrarySort,
    ascending: Boolean,
    onSortChange: (LibrarySort, Boolean) -> Unit
) {
    FilterSectionLabel("Sort")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LibrarySort.entries.forEach { option ->
            ExpressiveStateChip(
                label = option.label,
                selected = sort == option,
                onClick = {
                    val next = selectSort(option)
                    onSortChange(next.first, next.second)
                },
                minTouchTargetHeight = 38.dp
            )
        }
    }
    if (sort.isDirectional) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveStateChip(
                label = "Ascending ↑",
                selected = ascending,
                onClick = { onSortChange(sort, true) },
                minTouchTargetHeight = 38.dp
            )
            ExpressiveStateChip(
                label = "Descending ↓",
                selected = !ascending,
                onClick = { onSortChange(sort, false) },
                minTouchTargetHeight = 38.dp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatSection(
    draft: LibraryAdvancedFilters,
    onDraftChange: (LibraryAdvancedFilters) -> Unit
) {
    FilterSectionLabel("Format")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        libraryFormatOptions.forEach { (key, label) ->
            val selected = key in draft.formats
            ExpressiveStateChip(
                label = label,
                selected = selected,
                onClick = {
                    onDraftChange(
                        draft.copy(
                            formats = if (selected) draft.formats - key else draft.formats + key
                        )
                    )
                },
                minTouchTargetHeight = 38.dp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearSection(
    years: List<Int>,
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    FilterSectionLabel("Release year")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        years.forEach { year ->
            ExpressiveStateChip(
                label = year.toString(),
                selected = selected == year,
                onClick = { onSelect(if (selected == year) null else year) },
                minTouchTargetHeight = 38.dp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreSection(
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    FilterSectionLabel("Minimum score")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(null to "Any", 60 to "60+", 70 to "70+", 80 to "80+", 90 to "90+")
            .forEach { (score, label) ->
                ExpressiveStateChip(
                    label = label,
                    selected = selected == score,
                    onClick = { onSelect(score) },
                    minTouchTargetHeight = 38.dp
                )
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreSection(
    genres: List<String>,
    draft: LibraryAdvancedFilters,
    onDraftChange: (LibraryAdvancedFilters) -> Unit
) {
    FilterSectionLabel("Genres")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        genres.forEach { genre ->
            val selected = genre in draft.genres
            ExpressiveStateChip(
                label = genre,
                selected = selected,
                onClick = {
                    onDraftChange(
                        draft.copy(
                            genres = if (selected) draft.genres - genre else draft.genres + genre
                        )
                    )
                },
                minTouchTargetHeight = 38.dp
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Titles carrying any of the genres you pick.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FavouritesRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Favourites only",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Keep the titles you pinned on AniList.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun FilterDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
