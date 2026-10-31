package com.anisequel.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.model.detailFor
import com.anisequel.data.model.withSummaryProgress
import com.anisequel.domain.jokes.JokeMoment
import com.anisequel.ui.components.AniTopAppBar
import com.anisequel.ui.components.EmptyStateView
import com.anisequel.ui.components.FilterSortSheet
import com.anisequel.ui.components.JokeMomentBus
import com.anisequel.ui.components.QuickFilterBar
import com.anisequel.ui.components.StateCrossfade
import com.anisequel.ui.components.cards.SequelCard
import com.anisequel.ui.components.detail.SequelDetailSheet
import com.anisequel.ui.viewmodel.rememberPersonProfileHost
import com.anisequel.ui.viewmodel.rememberPlanningAddHost
import com.anisequel.ui.viewmodel.toMediaNode
import com.anisequel.ui.components.StatsBanner
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.toDetailMedia
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.navigation.MainBarClearance
import com.anisequel.ui.navigation.mainBarClearance
import com.anisequel.ui.screens.dashboard.ArrivingSection
import com.anisequel.ui.screens.dashboard.DashboardDemoBanner
import com.anisequel.ui.screens.dashboard.DashboardErrorView
import com.anisequel.ui.screens.dashboard.DashboardLoadingView
import com.anisequel.ui.screens.dashboard.DashboardSearchBar
import com.anisequel.ui.screens.dashboard.DashboardSectionHeader
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.ApiStatusSnapshot
import com.anisequel.data.network.isConnectionTrouble
import com.anisequel.ui.components.ApiRecoveryOverlay
import com.anisequel.ui.viewmodel.DashboardEvent
import com.anisequel.ui.viewmodel.DashboardUiState
import com.anisequel.ui.viewmodel.DashboardViewModel
import com.anisequel.ui.viewmodel.rememberApiStatusProbe
import kotlinx.coroutines.flow.collectLatest

private val MaxContentWidth = 640.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    dashboardViewModel: DashboardViewModel,
    onOpenSettings: (ViewerProfile?) -> Unit,
    onSignInAgain: () -> Unit = {},
    onOpenProfile: (ViewerProfile?) -> Unit = {},
    /**
     * Opens a studio's page from the credits on a title the sheet is showing.
     *
     * The host owns this rather than the sheet: a studio is a route, and the sheet
     * is a window over the dashboard - so this *closes* the sheet and navigates. A
     * `ModalBottomSheet` is its own window and would otherwise keep floating over
     * the page it navigated to, with the title the reader came from underneath it.
     */
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
    quickAddPreferences: com.anisequel.data.repository.QuickAddPreferences,
    /**
     * The connection the cast and crew rows open a profile with.
     *
     * Null on the demo route, and deliberately so: a host with no repository draws
     * the cards as captions rather than as buttons that open a sheet which can only
     * report a failure. The demo has no characters to look up, and a button that
     * always fails is the dead control this feature replaced.
     */
    repository: com.anisequel.data.repository.AniListRepository? = null,
    modifier: Modifier = Modifier
) {
    val uiState by dashboardViewModel.uiState.collectAsState()

    // The connection status strip. The probe reads through the repository so it
    // asks the same client, with the same auth and the same classifier, as every
    // real request; the demo route has no repository and reports UNKNOWN rather
    // than pretending to have checked.
    val apiProbe = rememberApiStatusProbe {
        repository?.checkApiReachability() ?: ApiStatusSnapshot(ApiReachability.UNKNOWN)
    }
    val apiStatus by apiProbe.ui.collectAsState()

    // The person whose profile is open, and the callbacks the cast and crew rows
    // open it with. Null repository - the demo route - draws captions, not buttons.
    val profiles = rememberPersonProfileHost(repository)
    val filterCriteria by dashboardViewModel.filterCriteria.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Collected unconditionally. The old form short-circuited on a nullable
    // parameter (`prefs?.settings?.collectAsState(...) ?: remember { ... }`),
    // which changes the composable calls between recompositions - a Rules of
    // Compose violation, not just a wasted allocation, that would drop or
    // scramble state the moment the parameter flipped.
    val quickAddSettings by quickAddPreferences.settings.collectAsState(
        initial = com.anisequel.data.repository.QuickAddPreferences.QuickAddSettings()
    )

    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedSequelId by remember { mutableStateOf<Int?>(null) }
    // An arriving row is not a sequel, so it is tracked by its own media id and
    // resolved to a plain media subject below. One nullable id per source keeps
    // "what is open" unambiguous without a second sheet.
    var selectedArrivingId by remember { mutableStateOf<Int?>(null) }
    val loadingDetailIds by dashboardViewModel.loadingDetailIds.collectAsState()
    val detailNodes by dashboardViewModel.detailNodes.collectAsState()
    val detailFriends by dashboardViewModel.friends.collectAsState()
    val hiddenSequels by dashboardViewModel.hiddenSequels.collectAsState()
    val arrivingEnabled by dashboardViewModel.arrivingEnabled.collectAsState()
    val arrivingCompact by dashboardViewModel.arrivingCompact.collectAsState()

    // The most recently hidden entry used to be mirrored here for the snackbar's
    // Undo. It lives on the event now: one slot could not tell two quick hides apart,
    // so the first message's Undo could restore the second entry.

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
                        // The entry this message was about, not whatever was hidden
                        // last: see [DashboardEvent.ShowSnackbar.undoSequel].
                        event.undoSequel?.let(dashboardViewModel::restoreHiddenSequel)
                    }
                }
            }
        }
    }

    val successState = uiState as? DashboardUiState.Success
    val currentViewer = successState?.viewer
    val missedCount = successState?.totalMissedCount ?: 0

    // One answer for "this screen may write to AniList", used by the sheet's
    // editor, its quick add and the recommendation cards' own button. `!= false`
    // rather than `== true` because a refresh briefly leaves the state outside
    // `Success` - a demo session is the one case that is genuinely read-only, and
    // it says so from inside `Success`.
    val canWrite = successState?.canWriteToAniList != false

    // The joke toast's "after you refresh" moment. Announced here rather than in
    // the ViewModel because the interval refresh also goes through the
    // ViewModel, and an automatic poll is not something the reader asked for.
    // The host owns whether the switch is on, whether a line is already up and
    // whether a quiet gap has passed.
    val requestRefresh = {
        dashboardViewModel.refresh()
        JokeMomentBus.announce(JokeMoment.REFRESH)
    }

    // One-tap Planning for a title the sheet is showing that is not one of the
    // recommendation cards: an arriving row, or a relation walked to from either.
    val planningAdd = rememberPlanningAddHost(
        repository = repository,
        canWrite = canWrite
    )
    val quickAddStatuses by planningAdd.statuses.collectAsState()

    // One subject for the sheet, resolved from whichever source was tapped.
    //
    // Memoised on the state instance and the open ids: an uncached linear scan
    // ran on every recomposition, including the ones driven by unrelated state
    // such as the snackbar or the detail spinner. `detailNodes` is a key as well
    // so an arriving row swaps to its fetched detail the moment it lands.
    //
    // A sequel keeps its parent narrative; an arriving row or a relation is a
    // summary the detail query fills in, so its subject prefers the fetched node
    // and keeps the summary's progress either way.
    //
    // A title reached by tapping a relation card, which replaces the subject rather
    // than stacking a sheet on one. Cleared when a list row is tapped instead, so
    // dismissing and re-opening from the list does not land the reader back in
    // whatever franchise they had wandered into.
    //
    // What is held here is the *relation* node the card carried, and that node is a
    // summary rather than a record: the relations row is fetched with a narrow
    // field set (`RELATED_NODE_FIELDS` - no synopsis, cast, staff or relations of
    // its own). Painting it directly is what made a followed relation open a
    // visibly emptier page than the same title opened from the list - no synopsis,
    // no credits, and the relations row gone after a single hop - because the
    // detail the sheet fetched was never read by the subject it drew from. The
    // resolution below is what turns the summary into the same full page.
    var navigatedRelation by remember { mutableStateOf<MediaNode?>(null) }

    val selectedSubject = remember(
        selectedSequelId,
        selectedArrivingId,
        navigatedRelation,
        successState,
        detailNodes
    ) {
        when {
            // The same resolution an arriving row gets: the tapped node is the
            // summary the sheet can draw on the first frame, and the fetched
            // detail supersedes it the moment it lands. Without this, a relation
            // was the one place a subject was built straight from its summary -
            // the detail request went out and its answer was thrown away.
            navigatedRelation != null -> navigatedRelation?.let { summary ->
                AnimeDetailSubject.Media(
                    detailNodes.detailFor(summary)?.withSummaryProgress(summary) ?: summary
                )
            }
            selectedSequelId != null -> successState?.missedSequels
                ?.firstOrNull { it.sequelId == selectedSequelId }
                ?.let { AnimeDetailSubject.Sequel(it) }
            selectedArrivingId != null -> successState?.arriving
                ?.firstOrNull { it.mediaId == selectedArrivingId }
                ?.let { entry ->
                    val summary = entry.toDetailMedia()
                    AnimeDetailSubject.Media(
                        detailNodes.detailFor(summary)?.withSummaryProgress(summary) ?: summary
                    )
                }
            else -> null
        }
    }

    val selectedDetailId = selectedSubject?.mediaId
    val isDetailLoading = selectedDetailId != null && selectedDetailId in loadingDetailIds
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // One definition of "the filters are narrowing this list", owned by
    // [FilterCriteria] so the top bar's badge, the quick filter row and the
    // empty state cannot drift apart again.
    val hasActiveFilters = filterCriteria.isNarrowing

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("dashboard_screen"),
        // Lifted clear of the main bar: the Scaffold sits a snackbar on the system
        // inset, which is exactly where the floating bar begins, so an "Added to
        // Planning" confirmation was drawn behind the bar that had just been tapped
        // to come back from. `MainBarClearance` and not `mainBarClearance()`: the
        // Scaffold has already paid the inset, and this is only the bar's own height.
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = MainBarClearance)
            )
        },
        topBar = {
            AniTopAppBar(
                viewer = currentViewer,
                missedCount = missedCount,
                onOpenFilter = { showFilterSheet = true },
                onRefresh = requestRefresh,
                onOpenSettings = { onOpenSettings(currentViewer) },
                onOpenProfile = { onOpenProfile(currentViewer) },
                isRefreshing = successState?.isRefreshing == true,
                hasActiveFilters = hasActiveFilters,
                // The snapshot is the first frame of a cold start; the bar says
                // so without moving the list underneath it. See [AniTopAppBar].
                showingSavedCopy = successState?.isCached == true,
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Start/end insets are applied; the bottom deliberately is not,
                // because the main bar floats over the content rather than pushing
                // it. Its clearance is what the list ends with instead - see the
                // LazyColumn's `contentPadding` below.
                .padding(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding()
                )
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            StateCrossfade(
                targetState = uiState,
                modifier = Modifier.fillMaxSize(),
                label = "dashboard_state",
            ) { state ->
                when (state) {
                    is DashboardUiState.Loading -> {
                        DashboardLoadingView(
                            message = state.message,
                            maxWidth = MaxContentWidth
                        )
                    }

                    is DashboardUiState.Error -> {
                        // Seed the strip from the failure, and probe only when the
                        // failure left reachability genuinely unknown. Keyed on the
                        // failure so a *new* error re-diagnoses instead of showing
                        // the previous one's verdict.
                        LaunchedEffect(state.kind, state.message) {
                            apiProbe.onFailure(state.kind)
                        }
                        DashboardErrorView(
                            state = state,
                            apiStatus = apiStatus,
                            onCheckApiStatus = { apiProbe.probe() },
                            onRetry = { dashboardViewModel.loadData() },
                            onSignInAgain = onSignInAgain
                        )
                    }

                    is DashboardUiState.Success -> {
                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = requestRefresh,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("sequels_list"),
                                // The bar floats over the list, so the Scaffold's bottom
                                // inset is *replaced* by the bar's clearance rather than
                                // added to it - the two describe the same strip of screen.
                                // The 32dp of breathing room stays on top of it.
                                contentPadding = PaddingValues(bottom = 32.dp + mainBarClearance()),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (state.isDemoMode) {
                                    item(key = "demo_banner") {
                                        DashboardDemoBanner(modifier = Modifier.widthIn(max = MaxContentWidth))
                                    }
                                }

                                // The saved-copy notice moved to the top app bar. It
                                // used to be an item here, so it appeared and
                                // disappeared above the stats card as the network
                                // answered and moved every row below it by its
                                // height. See [AniTopAppBar.showingSavedCopy].

                                item(key = "stats_banner") {
                                    StatsBanner(
                                        totalWatched = state.totalWatchedCount,
                                        missedCount = state.totalMissedCount,
                                        modifier = Modifier
                                            .widthIn(max = MaxContentWidth)
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }

                                // Above the search bar and the missed-sequel list
                                // because it is a different question from either,
                                // and hidden entirely when the viewer has nothing
                                // arriving rather than rendering an empty header.
                                // The same emptiness check gates the switch in
                                // Settings: disabled means the section - and every
                                // poster load it draws - is not composed at all.
                                if (state.arriving.isNotEmpty() && arrivingEnabled) {
                                    item(key = "arriving_section") {
                                        ArrivingSection(
                                            entries = state.arriving,
                                            compact = arrivingCompact,
                                            maxWidth = MaxContentWidth,
                                            onEntryClick = {
                                                selectedArrivingId = it.mediaId
                                                navigatedRelation = null
                                            }
                                        )
                                    }
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
                                            // The same predicate as the badge and
                                            // the quick filter row: an empty list
                                            // with no filter active has nothing to
                                            // reset, and an empty list with one
                                            // active has a way out.
                                            filtersActive = filterCriteria.isNarrowing,
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
                                            onClick = {
                                                selectedSequelId = item.sequelId
                                                navigatedRelation = null
                                            },
                                            onAddToPlanning = { dashboardViewModel.addToPlanning(item) },
                                            onAddToWatching = { dashboardViewModel.addToWatching(item) },
                                            holdDurationSeconds = quickAddSettings.holdDurationSeconds,
                                            swipeEnabled = quickAddSettings.swipeEnabled,
                                            onHide = {
                                                dashboardViewModel.toggleHideSequel(item)
                                            },
                                            modifier = Modifier
                                                .widthIn(max = MaxContentWidth)
                                                .padding(horizontal = 16.dp)
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
                }
            }

            DashboardRecovery(uiState = uiState)
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
            onToggleSequelThisYear = { dashboardViewModel.toggleSequelReleasedThisYear(it) },
            onToggleParentCompletedThisYear = { dashboardViewModel.toggleParentCompletedThisYear(it) },
            onToggleCurrentlyWatching = { dashboardViewModel.toggleIncludeCurrentlyWatching(it) },
            onToggleInList = { dashboardViewModel.toggleIncludeInList(it) },
            onToggleGenre = { dashboardViewModel.toggleGenre(it) },
            onSelectMinScore = { dashboardViewModel.selectMinScore(it) },
            onSelectMinEpisodes = { dashboardViewModel.selectMinEpisodes(it) },
            onResetFilters = { dashboardViewModel.resetFilters() },
            hiddenSequels = hiddenSequels,
            onRestoreHidden = { dashboardViewModel.restoreHiddenSequel(it) },
            onRestoreAllHidden = { dashboardViewModel.restoreAllHiddenSequels() },
            onDismiss = { showFilterSheet = false }
        )
    }

    if (selectedSubject != null) {
        // `canWrite` is hoisted above, next to `successState`, so the write flag,
        // the editor and the quick-add host above cannot be told different things:
        // writes unavailable while still handed the tab that writes - or the
        // reverse, a live editor hidden behind a false flag.
        //
        // One definition of "the sheet is closed", used by the dismiss gesture and by
        // a tap on a studio credit. Written twice, the studio hop would have to
        // remember all three of the ids the sheet keys off - and forgetting one
        // re-opens the title it was showing the next time the sheet is dismissed.
        val closeSheet: () -> Unit = {
            selectedSequelId = null
            selectedArrivingId = null
            navigatedRelation = null
        }
        SequelDetailSheet(
            subject = selectedSubject,
            sheetState = detailSheetState,
            onDismiss = closeSheet,
            onAddToPlanning = { dashboardViewModel.addToPlanning(it) },
            onAddToWatching = { dashboardViewModel.addToWatching(it) },
            quickAddStatuses = quickAddStatuses,
            onAddMediaToPlanning = planningAdd.onAddToPlanning,
            onAddMediaToWatching = planningAdd.onAddToWatching,
            holdDurationSeconds = quickAddSettings.holdDurationSeconds,
            swipeEnabled = quickAddSettings.swipeEnabled,
            onLoadDetail = { id, type -> dashboardViewModel.loadDetail(id, type) },
            onLoadFriends = { id, type -> dashboardViewModel.loadFriends(id, type) },
            friends = detailFriends[selectedSubject.mediaId].orEmpty(),
            onOpenMedia = { media -> navigatedRelation = media },
            isDetailLoading = isDetailLoading,
            canWriteToAniList = canWrite,
            entryEditor = if (canWrite) dashboardViewModel else null,
            onOpenStudio = { id, name ->
                closeSheet()
                onOpenStudio(id, name)
            },
            onOpenCharacter = profiles.onOpenCharacter,
            onOpenStaff = profiles.onOpenStaff
        )
    }

    // A sibling of the detail sheet, not a child: a `ModalBottomSheet` lives in its
    // own window, so one drawn from inside another covers it. A tap on the titles
    // row routes into the sheet's own relation navigation, which is how you walk
    // from a cast member to the next title without stacking a third window.
    profiles.Sheet { appearance -> navigatedRelation = appearance.toMediaNode() }
}

/**
 * The heart gluing itself back together when a failed load succeeds.
 *
 * Kept at screen level because the error view is *replaced* by the success
 * content, and an animation owned by the error view would be gone before it
 * could finish - see [ApiRecoveryOverlay]. Extracted from [DashboardScreen] so
 * the screen itself stays about layout: this owns two flags and the transition
 * between them, and nothing else.
 */
@Composable
private fun DashboardRecovery(uiState: DashboardUiState) {
    var awaitingRecovery by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is DashboardUiState.Error ->
                if (state.kind.isConnectionTrouble()) awaitingRecovery = true

            is DashboardUiState.Success -> if (awaitingRecovery) {
                awaitingRecovery = false
                showRecovery = true
            }

            is DashboardUiState.Loading -> Unit
        }
    }
    ApiRecoveryOverlay(
        visible = showRecovery,
        onFinished = { showRecovery = false }
    )
}
