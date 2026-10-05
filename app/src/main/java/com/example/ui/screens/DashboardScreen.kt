package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.data.model.MissedSequel
import com.example.data.model.RelationKind
import com.example.data.model.StatusFilter
import com.example.data.model.ViewerProfile
import com.example.ui.components.AniTopAppBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FilterSortSheet
import com.example.ui.components.QuickFilterBar
import com.example.ui.components.SequelCard
import com.example.ui.components.SequelDetailSheet
import com.example.ui.components.StatsBanner
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.screens.dashboard.DashboardDemoBanner
import com.example.ui.screens.dashboard.DashboardErrorView
import com.example.ui.screens.dashboard.DashboardLoadingView
import com.example.ui.screens.dashboard.DashboardSearchBar
import com.example.ui.screens.dashboard.DashboardSectionHeader
import com.example.ui.viewmodel.DashboardEvent
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.flow.collectLatest

private val MaxContentWidth = 640.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    dashboardViewModel: DashboardViewModel,
    onOpenSettings: (ViewerProfile?) -> Unit,
    onSignInAgain: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val filterCriteria by dashboardViewModel.filterCriteria.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedSequelId by remember { mutableStateOf<Int?>(null) }
    val loadingDetailIds by dashboardViewModel.loadingDetailIds.collectAsState()
    val hiddenSequels by dashboardViewModel.hiddenSequels.collectAsState()

    // The most recently hidden entry, so the snackbar's Undo can put it back.
    // Held here rather than inside the ViewModel because the snackbar is what
    // owns the gesture: the action belongs to the message that offered it.
    var lastHiddenSequel by remember { mutableStateOf<MissedSequel?>(null) }

    // Re-checks freshness every time the app comes back to the foreground.
    //
    // `LifecycleResumeEffect` is the documented way to react to ON_RESUME from a
    // composable, and it is tied to the Activity rather than to this
    // destination - which is what makes it the right signal. The bug was not
    // inside the dashboard, it was that Android *resumes* a process rather than
    // restarting it, so closing and reopening AniSequel used to answer from the
    // same cached list until an hour had passed. Nothing in the composition was
    // ever told the app had come back.
    //
    // Stopped on pause rather than left running: a timer that keeps re-fetching
    // a list nobody is looking at spends AniList's ~30 requests a minute for
    // nobody. The ViewModel owns the decision, and it survives this composable
    // being disposed on the way to Settings.
    LifecycleResumeEffect(dashboardViewModel) {
        dashboardViewModel.onForegrounded()
        onPauseOrDispose { dashboardViewModel.onBackgrounded() }
    }

    LaunchedEffect(Unit) {
        dashboardViewModel.eventFlow.collectLatest { event ->
            when (event) {
                is DashboardEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        lastHiddenSequel?.let(dashboardViewModel::restoreHiddenSequel)
                        lastHiddenSequel = null
                    }
                }
            }
        }
    }

    val successState = uiState as? DashboardUiState.Success
    val currentViewer = successState?.viewer
    val missedCount = successState?.totalMissedCount ?: 0

    val selectedSequel = selectedSequelId?.let { id ->
        successState?.missedSequels?.firstOrNull { it.sequelId == id }
    }

    val isDetailLoading = selectedSequelId != null && selectedSequelId in loadingDetailIds
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val hasActiveFilters by remember(filterCriteria) {
        androidx.compose.runtime.derivedStateOf {
            filterCriteria.statusFilter != StatusFilter.ALL ||
                filterCriteria.selectedFormat != null ||
                filterCriteria.searchQuery.isNotBlank() ||
                !filterCriteria.includeUnreleased ||
                !filterCriteria.hideAlreadyPlanned ||
                filterCriteria.includedRelations != setOf(RelationKind.SEQUEL)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("dashboard_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AniTopAppBar(
                viewer = currentViewer,
                missedCount = missedCount,
                onOpenFilter = { showFilterSheet = true },
                onRefresh = { dashboardViewModel.refresh() },
                onOpenSettings = { onOpenSettings(currentViewer) },
                isRefreshing = successState?.isRefreshing == true,
                hasActiveFilters = hasActiveFilters,
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    DashboardLoadingView(
                        message = state.message,
                        maxWidth = MaxContentWidth
                    )
                }

                is DashboardUiState.Error -> {
                    DashboardErrorView(
                        state = state,
                        onRetry = { dashboardViewModel.loadData() },
                        onSignInAgain = onSignInAgain
                    )
                }

                is DashboardUiState.Success -> {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { dashboardViewModel.refresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("sequels_list"),
                            contentPadding = PaddingValues(bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (state.isDemoMode) {
                                item(key = "demo_banner") {
                                    DashboardDemoBanner(modifier = Modifier.widthIn(max = MaxContentWidth))
                                }
                            }

                            item(key = "stats_banner") {
                                StatsBanner(
                                    totalWatched = state.totalWatchedCount,
                                    missedCount = state.totalMissedCount,
                                    modifier = Modifier
                                        .widthIn(max = MaxContentWidth)
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }

                            item(key = "search_bar") {
                                DashboardSearchBar(
                                    query = filterCriteria.searchQuery,
                                    onQueryChange = { dashboardViewModel.updateSearchQuery(it) },
                                    maxWidth = MaxContentWidth
                                )
                            }

                            item(key = "quick_filter_bar") {
                                QuickFilterBar(
                                    filterCriteria = filterCriteria,
                                    onStatusSelected = { dashboardViewModel.updateStatusFilter(it) },
                                    onFormatSelected = { dashboardViewModel.selectFormat(it) },
                                    onClearAll = { dashboardViewModel.resetFilters() }
                                )
                            }

                            item(key = "section_header") {
                                DashboardSectionHeader(
                                    count = state.missedSequels.size,
                                    filterCriteria = filterCriteria,
                                    maxWidth = MaxContentWidth
                                )
                            }

                            if (state.missedSequels.isEmpty()) {
                                item(key = "empty_state") {
                                    EmptyStateView(
                                        isSearching = filterCriteria.searchQuery.isNotBlank() ||
                                            filterCriteria.selectedFormat != null ||
                                            filterCriteria.statusFilter != StatusFilter.ALL ||
                                            !filterCriteria.includeUnreleased ||
                                            !filterCriteria.hideAlreadyPlanned ||
                                            filterCriteria.includedRelations != setOf(RelationKind.SEQUEL),
                                        onResetFilters = { dashboardViewModel.resetFilters() }
                                    )
                                }
                            } else {
                                items(
                                    items = state.missedSequels,
                                    key = { "${it.parentId}_${it.sequelId}" }
                                ) { item ->
                                    SequelCard(
                                        sequel = item,
                                        onClick = { selectedSequelId = item.sequelId },
                                        onAddToPlanning = { dashboardViewModel.addToPlanning(item) },
                                        onHide = {
                                            lastHiddenSequel = item
                                            dashboardViewModel.toggleHideSequel(item)
                                        },
                                        modifier = Modifier
                                            .widthIn(max = MaxContentWidth)
                                            .padding(horizontal = 16.dp)
                                            .animateItem(
                                                placementSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                fadeInSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                ),
                                                fadeOutSpec = ExpressiveMotion.FastEffects
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterSortSheet(
            sheetState = filterSheetState,
            filterCriteria = filterCriteria,
            onSortSelected = { dashboardViewModel.updateSortOption(it) },
            onStatusSelected = { dashboardViewModel.updateStatusFilter(it) },
            onToggleUnreleased = { dashboardViewModel.toggleIncludeUnreleased(it) },
            onToggleHidePlanned = { dashboardViewModel.toggleHideAlreadyPlanned(it) },
            onFormatSelected = { dashboardViewModel.selectFormat(it) },
            onToggleRelation = { dashboardViewModel.toggleRelation(it) },
            hiddenSequels = hiddenSequels,
            onRestoreHidden = { dashboardViewModel.restoreHiddenSequel(it) },
            onRestoreAllHidden = { dashboardViewModel.restoreAllHiddenSequels() },
            onDismiss = { showFilterSheet = false }
        )
    }

    if (selectedSequel != null) {
        SequelDetailSheet(
            sequel = selectedSequel,
            sheetState = detailSheetState,
            onDismiss = { selectedSequelId = null },
            onAddToPlanning = { dashboardViewModel.addToPlanning(it) },
            onLoadDetail = { dashboardViewModel.loadDetail(it) },
            isDetailLoading = isDetailLoading,
            canWriteToAniList = (uiState as? DashboardUiState.Success)?.canWriteToAniList != false
        )
    }
}
