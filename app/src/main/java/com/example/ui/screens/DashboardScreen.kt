package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.sp
import com.example.data.model.MissedSequel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    dashboardViewModel: DashboardViewModel,
    onOpenSettings: (ViewerProfile?) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val filterCriteria by dashboardViewModel.filterCriteria.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedSequelForDetail by remember { mutableStateOf<MissedSequel?>(null) }

    LaunchedEffect(Unit) {
        dashboardViewModel.eventFlow.collectLatest { event ->
            when (event) {
                is DashboardEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val currentViewer = (uiState as? DashboardUiState.Success)?.viewer
    val missedCount = (uiState as? DashboardUiState.Success)?.totalMissedCount ?: 0

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("dashboard_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AniTopAppBar(
                viewer = currentViewer,
                missedCount = missedCount,
                onOpenFilter = { showFilterSheet = true },
                onRefresh = { dashboardViewModel.refresh() },
                onOpenSettings = { onOpenSettings(currentViewer) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .testTag("dashboard_loading"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = AppVectorIcons.Tv,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }

                        items(5) {
                            ShimmerCard()
                        }
                    }
                }

                is DashboardUiState.Error -> {
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
                            text = "AniList Connection Error",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            textAlign = TextAlign.Center
                        )
                        if (state.canRetry) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { dashboardViewModel.loadData() },
                                modifier = Modifier.testTag("retry_button")
                            ) {
                                Text("Retry Connection")
                            }
                        }
                    }
                }

                is DashboardUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("sequels_list"),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Demo Mode notice if applicable
                        if (state.isDemoMode) {
                            item(key = "demo_banner") {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    shape = RoundedCornerShape(0.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = AppVectorIcons.Visibility,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(
                                            text = "Sample Demo Mode • Connect account anytime in Settings",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Stats Overview Banner
                        item(key = "stats_banner") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                StatsBanner(
                                    totalWatched = state.totalWatchedCount,
                                    missedCount = state.totalMissedCount
                                )
                            }
                        }

                        // Search Bar
                        item(key = "search_bar") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                OutlinedTextField(
                                    value = filterCriteria.searchQuery,
                                    onValueChange = { dashboardViewModel.updateSearchQuery(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("anime_search_bar"),
                                    placeholder = { Text("Search franchise or sequel...") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = AppVectorIcons.Search,
                                            contentDescription = "Search"
                                        )
                                    },
                                    trailingIcon = {
                                        if (filterCriteria.searchQuery.isNotBlank()) {
                                            IconButton(onClick = { dashboardViewModel.updateSearchQuery("") }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear search"
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            }
                        }

                        // Quick Filter Bar
                        item(key = "quick_filter_bar") {
                            QuickFilterBar(
                                filterCriteria = filterCriteria,
                                onStatusSelected = { dashboardViewModel.updateStatusFilter(it) },
                                onFormatSelected = { dashboardViewModel.selectFormat(it) }
                            )
                        }

                        // Section Header Title
                        item(key = "section_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Missed Sequels (${state.missedSequels.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )

                                Text(
                                    text = filterCriteria.sortOption.displayName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        // List of Missed Sequels
                        if (state.missedSequels.isEmpty()) {
                            item(key = "empty_state") {
                                EmptyStateView(
                                    isSearching = filterCriteria.searchQuery.isNotBlank() ||
                                            filterCriteria.selectedFormat != null ||
                                            filterCriteria.statusFilter != com.example.data.model.StatusFilter.ALL,
                                    onResetFilters = {
                                        dashboardViewModel.updateSearchQuery("")
                                        dashboardViewModel.selectFormat(null)
                                        dashboardViewModel.updateStatusFilter(com.example.data.model.StatusFilter.ALL)
                                    }
                                )
                            }
                        } else {
                            items(
                                items = state.missedSequels,
                                key = { "${it.parentId}_${it.sequelId}" }
                            ) { item ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    SequelCard(
                                        sequel = item,
                                        onClick = { selectedSequelForDetail = item },
                                        onAddToPlanning = { dashboardViewModel.addToPlanning(item) }
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
            onToggleUnreleased = { dashboardViewModel.toggleIncludeUnreleased() },
            onToggleHidePlanned = { dashboardViewModel.toggleHideAlreadyPlanned() },
            onFormatSelected = { dashboardViewModel.selectFormat(it) },
            onDismiss = { showFilterSheet = false }
        )
    }

    if (selectedSequelForDetail != null) {
        SequelDetailSheet(
            sequel = selectedSequelForDetail,
            sheetState = detailSheetState,
            onDismiss = { selectedSequelForDetail = null },
            onAddToPlanning = {
                dashboardViewModel.addToPlanning(it)
                selectedSequelForDetail = selectedSequelForDetail?.copy(isAddingToPlanning = true)
            }
        )
    }
}
