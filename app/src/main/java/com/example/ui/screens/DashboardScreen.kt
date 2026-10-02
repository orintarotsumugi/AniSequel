package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.StatusFilter
import com.example.data.model.ViewerProfile
import com.example.ui.components.AniTopAppBar
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FilterSortSheet
import com.example.ui.components.QuickFilterBar
import com.example.ui.components.SequelCard
import com.example.ui.components.SequelDetailSheet
import com.example.ui.components.ShimmerCard
import com.example.ui.components.StatsBanner
import com.example.ui.viewmodel.DashboardEvent
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.flow.collectLatest

// Cards stay legible on a tablet and in landscape instead of stretching the
// poster and the text to opposite edges of a 1200dp-wide row.
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

    LaunchedEffect(Unit) {
        dashboardViewModel.eventFlow.collectLatest { event ->
            when (event) {
                is DashboardEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val successState = uiState as? DashboardUiState.Success
    val currentViewer = successState?.viewer
    val missedCount = successState?.totalMissedCount ?: 0

    // The sheet is looked up by id on every recomposition rather than being
    // handed a snapshot. It used to receive the MissedSequel captured when the
    // card was tapped, so after "Add to Planning" the sheet kept showing that
    // stale copy: spinner on the button, forever, with no way out but dismissal.
    val selectedSequel = selectedSequelId?.let { id ->
        successState?.missedSequels?.firstOrNull { it.sequelId == id }
    }

    val isDetailLoading = selectedSequelId != null &&
        selectedSequelId in loadingDetailIds

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("dashboard_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AniTopAppBar(
                viewer = currentViewer,
                missedCount = missedCount,
                onOpenFilter = { showFilterSheet = true },
                onRefresh = { dashboardViewModel.refresh() },
                onOpenSettings = { onOpenSettings(currentViewer) },
                isRefreshing = successState?.isRefreshing == true
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
                    LoadingList(message = state.message)
                }

                is DashboardUiState.Error -> {
                    ErrorState(
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
                                    DemoBanner(modifier = Modifier.widthIn(max = MaxContentWidth))
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
                                OutlinedTextField(
                                    value = filterCriteria.searchQuery,
                                    onValueChange = { dashboardViewModel.updateSearchQuery(it) },
                                    modifier = Modifier
                                        .widthIn(max = MaxContentWidth)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .testTag("anime_search_bar"),
                                    placeholder = { Text("Search franchise or sequel") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.small,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = AppVectorIcons.Search,
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = {
                                        if (filterCriteria.searchQuery.isNotBlank()) {
                                            IconButton(
                                                onClick = { dashboardViewModel.updateSearchQuery("") }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear search"
                                                )
                                            }
                                        }
                                    }
                                )
                            }

                            item(key = "quick_filter_bar") {
                                QuickFilterBar(
                                    filterCriteria = filterCriteria,
                                    onStatusSelected = { dashboardViewModel.updateStatusFilter(it) },
                                    onFormatSelected = { dashboardViewModel.selectFormat(it) }
                                )
                            }

                            item(key = "section_header") {
                                Row(
                                    modifier = Modifier
                                        .widthIn(max = MaxContentWidth)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Missed Sequels (${state.missedSequels.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = filterCriteria.sortOption.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (state.missedSequels.isEmpty()) {
                                item(key = "empty_state") {
                                    EmptyStateView(
                                        isSearching = filterCriteria.searchQuery.isNotBlank() ||
                                                filterCriteria.selectedFormat != null ||
                                                filterCriteria.statusFilter != StatusFilter.ALL ||
                                                !filterCriteria.includeUnreleased,
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
                                        modifier = Modifier
                                            .widthIn(max = MaxContentWidth)
                                            .padding(horizontal = 16.dp)
                                            // Cards fade and slide into place as
                                            // filters change the list, instead of
                                            // the whole screen snapping.
                                            .animateItem(
                                                placementSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                fadeInSpec = tween(220),
                                                fadeOutSpec = tween(120)
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

@Composable
private fun LoadingList(message: String) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("dashboard_loading"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        items(5) {
            ShimmerCard(modifier = Modifier.widthIn(max = MaxContentWidth))
        }
    }
}

@Composable
private fun DemoBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Sample data • sign in any time to scan your own list",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ErrorState(
    state: DashboardUiState.Error,
    onRetry: () -> Unit,
    onSignInAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("dashboard_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = AppVectorIcons.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (state.isAuthError) "Session expired" else "AniList connection error",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // An expired token cannot be fixed by retrying. Before, this screen only
        // offered Retry, which re-sent the same rejected request forever and left
        // no way back to the login screen.
        if (state.isAuthError) {
            Button(
                onClick = onSignInAgain,
                modifier = Modifier.testTag("sign_in_again_button")
            ) {
                Icon(
                    imageVector = AppVectorIcons.Login,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Sign in again")
            }
        } else if (state.canRetry) {
            Button(
                onClick = onRetry,
                modifier = Modifier.testTag("retry_button")
            ) {
                Text("Try again")
            }
        }
    }
}