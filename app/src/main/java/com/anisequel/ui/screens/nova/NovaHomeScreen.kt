package com.anisequel.ui.screens.nova

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.FilterCriteria
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.StatusFilter
import com.anisequel.data.model.withListStatus
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.QuickFilterBar
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.nova.NovaSectionHeading
import com.anisequel.ui.components.seasonal.SeasonalWind
import com.anisequel.ui.navigation.mainBarClearance
import com.anisequel.ui.screens.dashboard.ArrivingSection
import com.anisequel.ui.screens.dashboard.DashboardDemoBanner
import com.anisequel.ui.theme.LocalShowContinueRail
import com.anisequel.ui.viewmodel.DashboardUiState

/**
 * The new Home.
 *
 * ## What is different from the classic dashboard
 *
 * The classic page is a sequel feed with a top bar; this page is a *shelf*: what
 * you have, what you are in the middle of, and then the recommendations. The
 * order is the whole redesign - the list counts and the two Continue rails are
 * above the sequels, so returning to the app answers "where was I?" before it
 * asks "what next?".
 *
 * Everything is still the same state and the same writes. The page reads
 * [DashboardUiState.Success] and calls the dashboard's own callbacks, which is
 * what makes the two layouts a setting rather than two apps: a planning add from
 * either one lands in the same cache and the same snackbar.
 *
 * ## Performance
 *
 * One `LazyColumn`, keys on every row, and the rails are already-computed lists
 * carried in the state. Nothing here filters, sorts or derives a list during
 * composition except the three display strings a card needs; the expensive walks
 * stay in `DashboardViewModel` on `Dispatchers.Default`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaHomeScreen(
    state: DashboardUiState.Success,
    filterCriteria: FilterCriteria,
    quickAddSettings: QuickAddPreferences.QuickAddSettings,
    arrivingEnabled: Boolean,
    arrivingCompact: Boolean,
    arrivingSeparateGroups: Boolean,
    onRefresh: () -> Unit,
    onOpenOther: () -> Unit,
    onOpenAnimeList: () -> Unit,
    onOpenMangaList: () -> Unit,
    onOpenSequel: (MissedSequel) -> Unit,
    onOpenArriving: (Int) -> Unit,
    onOpenMedia: (MediaNode) -> Unit,
    onAddToPlanning: (MissedSequel) -> Unit,
    onAddToWatching: (MissedSequel) -> Unit,
    onHide: (MissedSequel) -> Unit,
    onSearch: (String) -> Unit,
    onOpenFilter: () -> Unit,
    onStatusSelected: (StatusFilter) -> Unit,
    onFormatSelected: (String?) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showContinueRail = LocalShowContinueRail.current
    val continueEntries = if (showContinueRail) state.continueWatching else emptyList()
    val viewer = state.viewer

    // The whole list's size, not the completed tally: the shortcut says "your
    // list", and the statistics are the only count the viewer query already
    // carries for both media types. The dashboard's watched count remains the
    // fallback for a demo fixture, whose stats object is empty.
    val animeCount = viewer.statistics?.anime?.count ?: state.totalWatchedCount
    val mangaCount = viewer.statistics?.manga?.count ?: 0
    val animeCovers = state.cardPreview.mapNotNull { it.media.posterUrl }

    val maxWidth = 640.dp

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("nova_home_list"),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = mainBarClearance() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.isDemoMode) {
                item(key = "demo_banner", contentType = "banner") {
                    DashboardDemoBanner(
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .padding(horizontal = 20.dp)
                    )
                }
            }

            item(key = "greeting", contentType = "header") {
                NovaGreeting(
                    viewerName = viewer.name,
                    modifier = Modifier.widthIn(max = maxWidth)
                )
            }

            item(key = "shortcuts", contentType = "shortcuts") {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NovaShortcutCard(
                            title = "ANIME LIST",
                            meta = "$animeCount items",
                            icon = AppVectorIcons.Tv,
                            coverUrls = animeCovers,
                            onClick = onOpenAnimeList,
                            modifier = Modifier.weight(1f)
                        )
                        NovaShortcutCard(
                            title = "MANGA LIST",
                            meta = "$mangaCount items",
                            icon = AppVectorIcons.SourceBook,
                            coverUrls = emptyList(),
                            onClick = onOpenMangaList,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    NovaOtherShortcut(onClick = onOpenOther)
                }
            }

            if (continueEntries.isNotEmpty()) {
                item(key = "continue_watching", contentType = "rail") {
                    NovaContinueRail(
                        title = "Continue Watching",
                        entries = continueEntries,
                        onOpen = { entry ->
                            SeasonalWind.stir()
                            onOpenMedia(entry.media.withListStatus(entry.status))
                        },
                        modifier = Modifier.widthIn(max = maxWidth)
                    )
                }
            }

            if (arrivingEnabled && state.arriving.isNotEmpty()) {
                // Arriving rows are their own section on both layouts, with the
                // reader's compact/separate settings honoured by the same
                // composable - see `ArrivingSection`.
                item(key = "arriving_section", contentType = "arriving") {
                    ArrivingSection(
                        entries = state.arriving,
                        maxWidth = maxWidth,
                        onEntryClick = { onOpenArriving(it.mediaId) },
                        compact = arrivingCompact,
                        separateGroups = arrivingSeparateGroups
                    )
                }
            }

            item(key = "sequels_header", contentType = "header") {
                NovaSectionHeading(
                    title = "Missed Sequels",
                    count = state.missedSequels.size,
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .padding(horizontal = 20.dp)
                )
            }

            item(key = "sequels_search", contentType = "controls") {
                NovaSearchRow(
                    query = filterCriteria.searchQuery,
                    onQueryChange = onSearch,
                    onOpenFilter = onOpenFilter,
                    modifier = Modifier.widthIn(max = maxWidth)
                )
            }

            item(key = "sequels_filters", contentType = "controls") {
                QuickFilterBar(
                    filterCriteria = filterCriteria,
                    onStatusSelected = onStatusSelected,
                    onFormatSelected = onFormatSelected,
                    onClearAll = onClearFilters,
                    modifier = Modifier.widthIn(max = maxWidth)
                )
            }

            if (state.missedSequels.isEmpty()) {
                item(key = "sequels_empty", contentType = "state") {
                    NovaEmptyNote(
                        text = if (filterCriteria.isNarrowing) {
                            "Nothing matches these filters. Clear them to see everything again."
                        } else {
                            "No missed sequels right now - your franchises are all caught up."
                        },
                        modifier = Modifier.widthIn(max = maxWidth)
                    )
                }
            } else {
                items(
                    items = state.missedSequels,
                    key = { "${it.parentId}_${it.sequelId}" },
                    contentType = { "sequel_row" }
                ) { sequel ->
                    NovaSequelRow(
                        sequel = sequel,
                        onOpen = { onOpenSequel(sequel) },
                        onAddToPlanning = { onAddToPlanning(sequel) },
                        onAddToWatching = { onAddToWatching(sequel) },
                        onHide = { onHide(sequel) },
                        holdDurationSeconds = quickAddSettings.holdDurationSeconds,
                        swipeEnabled = quickAddSettings.swipeEnabled,
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .padding(horizontal = 20.dp)
                            .animateItem(
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
