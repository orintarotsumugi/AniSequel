package com.anisequel.ui.screens.dashboard
import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.model.FilterCriteria
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.mediaCoverKey
import com.anisequel.data.model.namespaceKey
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.model.detailFor
import com.anisequel.data.model.withSummaryProgress
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.ApiStatusSnapshot
import com.anisequel.data.network.isConnectionTrouble
import com.anisequel.domain.jokes.JokeMoment
import com.anisequel.ui.components.AniTopAppBar
import com.anisequel.ui.components.ApiRecoveryOverlay
import com.anisequel.ui.components.EmptyStateView
import com.anisequel.ui.components.FilterSortSheet
import com.anisequel.ui.components.JokeMomentBus
import com.anisequel.ui.components.QuickFilterBar
import com.anisequel.ui.components.StateCrossfade
import com.anisequel.ui.components.StatsBanner
import com.anisequel.ui.components.cards.SequelCard
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.SequelDetailSheet
import com.anisequel.ui.components.detail.toDetailMedia
import com.anisequel.ui.components.detail.toParentSummary
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.rememberFullHeightSheetState
import com.anisequel.ui.navigation.MainBarClearance
import com.anisequel.domain.ui.AppLayout
import com.anisequel.ui.components.nova.NovaTopBar
import com.anisequel.ui.navigation.mainBarClearance
import com.anisequel.ui.screens.dashboard.ArrivingSection
import com.anisequel.ui.screens.dashboard.DashboardDemoBanner
import com.anisequel.ui.screens.dashboard.DashboardErrorView
import com.anisequel.ui.screens.dashboard.DashboardLoadingView
import com.anisequel.ui.screens.dashboard.DashboardSearchBar
import com.anisequel.ui.screens.dashboard.DashboardSectionHeader
import com.anisequel.ui.screens.nova.NovaHomeScreen
import com.anisequel.ui.screens.nova.NovaHomeSkeleton
import com.anisequel.ui.screens.profile.LibraryType
import com.anisequel.ui.theme.LocalAppLayout
import com.anisequel.ui.theme.LocalMainBarLift
import com.anisequel.ui.theme.LocalSearchTopBarShortcut
import com.anisequel.ui.theme.LocalTierTopBarShortcut
import com.anisequel.ui.viewmodel.DashboardEvent
import com.anisequel.ui.viewmodel.DashboardUiState
import com.anisequel.ui.viewmodel.DashboardViewModel
import com.anisequel.ui.viewmodel.rememberApiStatusProbe
import com.anisequel.ui.viewmodel.rememberPersonProfileHost
import com.anisequel.ui.viewmodel.rememberPlanningAddHost
import com.anisequel.ui.viewmodel.toMediaNode
import kotlinx.coroutines.flow.collectLatest
private val MaxContentWidth = 640.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    dashboardViewModel: DashboardViewModel,
    onOpenSettings: (ViewerProfile?) -> Unit,
    onOpenNotifications: () -> Unit = {},
    onSignInAgain: () -> Unit = {},
    onOpenProfile: (ViewerProfile?) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenTiers: () -> Unit = {},
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
    /** Opens the new look's feature hub. Only the new Home has a card for it. */
    onOpenOther: () -> Unit = {},
    /** Opens the Library, optionally on a named type. See [NovaHomeScreen]. */
    onOpenLibrary: (String?) -> Unit = {},
    /**
     * Whether the app is in the foreground, read at the app's root.
     *
     * No default: a caller that forgot it would pass `true` forever, and a
     * constant `true` fires its effect once per composition - which is the
     * page-resume bug this parameter exists to fix. Every route states its answer.
     */
    appResumed: Boolean,
    modifier: Modifier = Modifier
) {
    val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val appLayout = LocalAppLayout.current

    // The connection status strip. The probe reads through the repository so it
    // asks the same client, with the same auth and the same classifier, as every
    // real request; the demo route has no repository and reports UNKNOWN rather
    // than pretending to have checked.
    val apiProbe = rememberApiStatusProbe {
        repository?.checkApiReachability() ?: ApiStatusSnapshot(ApiReachability.UNKNOWN)
    }
    val apiStatus by apiProbe.ui.collectAsStateWithLifecycle()

    // The person whose profile is open, and the callbacks the cast and crew rows
    // open it with. Null repository - the demo route - draws captions, not buttons.
    val profiles = rememberPersonProfileHost(repository)
    val filterCriteria by dashboardViewModel.filterCriteria.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val filterSheetState = rememberFullHeightSheetState()

    // Collected unconditionally. The old form short-circuited on a nullable
    // parameter (`prefs?.settings?.collectAsState(...) ?: remember { ... }`),
    // which changes the composable calls between recompositions - a Rules of
    // Compose violation, not just a wasted allocation, that would drop or
    // scramble state the moment the parameter flipped.
    val quickAddSettings by quickAddPreferences.settings.collectAsStateWithLifecycle(
        initialValue = com.anisequel.data.repository.QuickAddPreferences.QuickAddSettings()
    )

    // `rememberSaveable` for the three pieces of state a rotation has to keep.
    // The ViewModel survives a configuration change, so the list and the viewer
    // come back — but the ids of the sheet that was open did not, and rotating
    // the phone closed the title the reader was reading and (worse) the filter
    // sheet over a list whose filters were still applied.
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var selectedSequelKey by rememberSaveable { mutableStateOf<String?>(null) }
    // An arriving row is not a sequel, so it is tracked by its own namespaced
    // key and resolved to a plain media subject below. One nullable key per
    // source keeps "what is open" unambiguous without a second sheet.
    var selectedArrivingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val loadingDetailIds by dashboardViewModel.loadingDetailIds.collectAsStateWithLifecycle()
    val detailNodes by dashboardViewModel.detailNodes.collectAsStateWithLifecycle()
    val detailFriends by dashboardViewModel.friends.collectAsStateWithLifecycle()
    val friendsLoadingIds by dashboardViewModel.friendsLoading.collectAsStateWithLifecycle()
    val hiddenSequels by dashboardViewModel.hiddenSequels.collectAsStateWithLifecycle()
    val arrivingEnabled by dashboardViewModel.arrivingEnabled.collectAsStateWithLifecycle()
    val arrivingCompact by dashboardViewModel.arrivingCompact.collectAsStateWithLifecycle()
    val arrivingSeparateGroups by dashboardViewModel.arrivingSeparateGroups.collectAsStateWithLifecycle()

    // The most recently hidden entry used to be mirrored here for the snackbar's
    // Undo. It lives on the event now: one slot could not tell two quick hides apart,
    // so the first message's Undo could restore the second entry.

    // This page is up, so the freshness timer may run. Only that much: the check
    // that decides the list is old enough to re-buy is the *app* coming back, not
    // this page coming back - see the effect below, which is keyed on the app's own
    // foreground flag from the root rather than on this destination's lifecycle.
    //
    // It used to be here, as a `LifecycleResumeEffect` on `dashboardViewModel`.
    // That effect reports ON_RESUME of this destination's `NavBackStackEntry`, and
    // an entry resumes every time the reader returns to it - so opening Settings
    // and tapping Back ran the whole freshness check, and on "Always" that check
    // is always due: a Settings round trip re-downloaded the collection. The two
    // events only looked like one because both arrive as ON_RESUME.
    //
    // `DisposableEffect` rather than a resume effect, so being *paused* is not
    // mistaken for being *gone*: an app that pauses here is still on this page
    // when it comes back, and the app-level resume then checks the list as it
    // always did. Disposal is the honest signal that the reader went elsewhere.
    DisposableEffect(dashboardViewModel) {
        dashboardViewModel.onShown()
        onDispose { dashboardViewModel.onHidden() }
    }

    // And this page is *up* while the app is in the foreground: the only moment a
    // list is worth re-buying. Keyed on [appResumed], so it runs when the app
    // resumes and on the first frame of a launch - never on a page change, which
    // is the difference between this and a resume effect on the destination's own
    // lifecycle.
    LaunchedEffect(dashboardViewModel, appResumed) {
        if (appResumed) dashboardViewModel.onForegrounded() else dashboardViewModel.onBackgrounded()
    }

    // Keyed on the ViewModel, not `Unit`: the collector holds the instance it
    // started with, so a different ViewModel kept publishing into a coroutine
    // that had been launched for the old one.
    LaunchedEffect(dashboardViewModel) {
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

    // The shortcut cards' artwork is only drawn by the new Home. Ask for it when
    // that page is actually on screen rather than on every dashboard load, so
    // the classic layout never pays a request for cards it does not draw. The
    // random AniList draw is what makes the Anime List and Other panels a real
    // piece of the catalogue rather than the same three local covers.
    LaunchedEffect(appLayout, currentViewer?.id) {
        if (appLayout == AppLayout.NOVA && currentViewer != null) {
            dashboardViewModel.ensureMangaPreview()
            dashboardViewModel.ensureRandomAnimeArtwork()
        }
    }

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
    val quickAddStatuses by planningAdd.statuses.collectAsStateWithLifecycle()

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
        selectedSequelKey,
        selectedArrivingKey,
        navigatedRelation,
        successState,
        detailNodes
    ) {
        resolveDetailSubject(
            selectedSequelKey = selectedSequelKey,
            selectedArrivingKey = selectedArrivingKey,
            navigatedRelation = navigatedRelation,
            successState = successState,
            detailNodes = detailNodes
        )
    }

    // Keep the last resolved subject for this exact selection while the dashboard
    // temporarily leaves Success during refresh. The key deliberately follows the
    // selected source/id rather than the Success state: a different selection gets
    // a fresh empty holder, and clearing the selection cannot resurrect an old page.
    val retainedSubjectState = remember(
        selectedSequelKey,
        selectedArrivingKey,
        navigatedRelation?.id,
        navigatedRelation?.type
    ) {
        androidx.compose.runtime.mutableStateOf<AnimeDetailSubject?>(null)
    }
    SideEffect {
        if (selectedSubject != null) retainedSubjectState.value = selectedSubject
    }
    val displayedSubject = selectedSubject ?: retainedSubjectState.value

    val isDetailLoading = displayedSubject?.media?.mediaCoverKey?.let { it in loadingDetailIds } == true
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // One definition of "the filters are narrowing this list", owned by
    // [FilterCriteria] so the top bar's badge, the quick filter row and the
    // empty state cannot drift apart again.
    val hasActiveFilters = filterCriteria.isNarrowing

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            // The collapse behaviour belongs to the classic bar, which is a real
            // `TopAppBar` the connection drives. The new look's bar is a static
            // shelf, so registering it there would let a bar that cannot collapse
            // take part in every scroll the new Home's list receives.
            .then(
                if (appLayout == AppLayout.NOVA) {
                    Modifier
                } else {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                }
            )
            .testTag("dashboard_screen"),
        // Lifted clear of the main bar: the Scaffold sits a snackbar on the system
        // inset, which is exactly where the floating bar begins, so an "Added to
        // Planning" confirmation was drawn behind the bar that had just been tapped
        // to come back from. `MainBarClearance` and not `mainBarClearance()`: the
        // Scaffold has already paid the inset, and this is only the bar's own height.
        // The reader's lift is added on top: the bar can now float above the inset,
        // and a confirmation left at the old height would appear behind it.
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(
                    bottom = MainBarClearance + LocalMainBarLift.current.dp
                )
            )
        },
        topBar = {
            DashboardTopBar(
                appLayout = appLayout,
                viewer = currentViewer,
                missedCount = missedCount,
                onOpenFilter = { showFilterSheet = true },
                onRefresh = requestRefresh,
                onOpenSettings = { onOpenSettings(currentViewer) },
                onOpenNotifications = onOpenNotifications,
                onOpenProfile = { onOpenProfile(currentViewer) },
                onOpenSearch = onOpenSearch,
                onOpenTiers = onOpenTiers,
                isRefreshing = successState?.isRefreshing == true,
                hasActiveFilters = hasActiveFilters,
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
                        // The waiting state is part of the look, not a third one.
                        // The classic skeleton is built from the classic page's
                        // sections, so drawing it under the new bar put the new
                        // bar over a page the reader had not chosen.
                        if (appLayout == AppLayout.NOVA) {
                            NovaHomeSkeleton()
                        } else {
                            DashboardLoadingView(
                                message = state.message,
                                maxWidth = MaxContentWidth
                            )
                        }
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
                        DashboardSuccessPane(
                            layout = appLayout,
                            nova = {
                            NovaHomeScreen(
                                state = state,
                                filterCriteria = filterCriteria,
                                quickAddSettings = quickAddSettings,
                                arrivingEnabled = arrivingEnabled,
                                arrivingCompact = arrivingCompact,
                                arrivingSeparateGroups = arrivingSeparateGroups,
                                onRefresh = requestRefresh,
                                onOpenOther = onOpenOther,
                                onOpenAnimeList = { onOpenLibrary(LibraryType.ANIME.name) },
                                onOpenMangaList = { onOpenLibrary(LibraryType.MANGA.name) },
                                onOpenSequel = {
                                    selectedSequelKey = it.sequelMedia.namespaceKey
                                    navigatedRelation = null
                                    selectedArrivingKey = null
                                },
                                onOpenArriving = {
                                    selectedArrivingKey = it.namespaceKey
                                    selectedSequelKey = null
                                    navigatedRelation = null
                                },
                                onOpenMedia = {
                                    selectedSequelKey = null
                                    selectedArrivingKey = null
                                    navigatedRelation = it
                                },
                                onAddToPlanning = { dashboardViewModel.addToPlanning(it) },
                                onAddToWatching = { dashboardViewModel.addToWatching(it) },
                                onHide = { dashboardViewModel.toggleHideSequel(it) },
                                onSearch = { dashboardViewModel.updateSearchQuery(it) },
                                onOpenFilter = { showFilterSheet = true },
                                onStatusSelected = { dashboardViewModel.updateStatusFilter(it) },
                                onFormatSelected = { dashboardViewModel.selectFormat(it) },
                                onClearFilters = { dashboardViewModel.resetFilters() }
                            )
                            },
                            classic = {
                                DashboardClassicList(
                                    state = state,
                                    filterCriteria = filterCriteria,
                                    quickAddSettings = quickAddSettings,
                                    arrivingEnabled = arrivingEnabled,
                                    arrivingCompact = arrivingCompact,
                                    arrivingSeparateGroups = arrivingSeparateGroups,
                                    onRequestRefresh = requestRefresh,
                                    onSelectSequel = { sequelKey ->
                                        selectedSequelKey = sequelKey
                                        navigatedRelation = null
                                    },
                                    onSelectArriving = { arrivingKey ->
                                        selectedArrivingKey = arrivingKey
                                        navigatedRelation = null
                                    },
                                    onSelectParent = { parent ->
                                        selectedSequelKey = null
                                        selectedArrivingKey = null
                                        navigatedRelation = parent
                                    },
                                    viewModel = dashboardViewModel
                                )
                            }
                        )
                    }
                }
            }

            DashboardRecovery(uiState = uiState)
        }
    }

    DashboardFilterSheetHost(
        visible = showFilterSheet,
        sheetState = filterSheetState,
        filterCriteria = filterCriteria,
        hiddenSequels = hiddenSequels,
        dashboardViewModel = dashboardViewModel,
        onDismiss = { showFilterSheet = false }
    )

    if (displayedSubject != null) {
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
            selectedSequelKey = null
            selectedArrivingKey = null
            navigatedRelation = null
        }
        SequelDetailSheet(
            subject = displayedSubject,
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
            friends = detailFriends[displayedSubject.media.mediaCoverKey].orEmpty(),
            isFriendsLoading = displayedSubject.media.mediaCoverKey in friendsLoadingIds,
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
 * The one subject the detail sheet is showing, from whichever source was tapped.
 *
 * A pure function rather than another `when` inside [DashboardScreen]. It was one,
 * and it was the densest branch cluster in a composable that CodeFactor reported as
 * a complex method: the screen's own decision is *which* sheet, and the resolution
 * of a tapped id into a subject is a separate question with its own four answers.
 *
 * ## The resolution itself
 *
 * A tapped node is the summary the sheet can draw on the first frame, and the
 * fetched detail supersedes it the moment it lands. Without this, a relation was the
 * one place a subject was built straight from its summary - the detail request went
 * out and its answer was thrown away.
 */
private fun resolveDetailSubject(
    selectedSequelKey: String?,
    selectedArrivingKey: String?,
    navigatedRelation: MediaNode?,
    successState: DashboardUiState.Success?,
    detailNodes: Map<MediaCoverKey, MediaNode>
): AnimeDetailSubject? = when {
    // The `let` names the summary `summary` because `RelationDetailResolutionTest`
    // pins this exact expression: the fetched detail wins, and the relation node's
    // progress is merged into it. That guard is a source-level one (there is no
    // Compose harness in the unit-test source set), so the shape is the contract.
    navigatedRelation != null -> navigatedRelation.let { summary ->
        AnimeDetailSubject.Media(
            detailNodes.detailFor(summary)?.withSummaryProgress(summary) ?: summary
        )
    }
    selectedSequelKey != null -> successState?.missedSequels
        ?.firstOrNull { it.sequelMedia.namespaceKey == selectedSequelKey }
        ?.let { AnimeDetailSubject.Sequel(it) }
    selectedArrivingKey != null -> successState?.arriving
        ?.firstOrNull { it.namespaceKey == selectedArrivingKey }
        ?.let { entry ->
            val summary = entry.toDetailMedia()
            AnimeDetailSubject.Media(
                detailNodes.detailFor(summary)?.withSummaryProgress(summary) ?: summary
            )
        }
    else -> null
}

/**
 * The classic layout's feed, split out of [DashboardScreen].
 *
 * It was the deepest nesting in that screen's body - Scaffold, Box, crossfade, state
 * branch, pull-to-refresh, list, item - and a complexity metric reads that as hard to
 * follow even though every branch in it is a section gate. The screen still owns
 * *which* pane is drawn and what a tap on a title means; this owns what the classic
 * pane paints.
 *
 * Selection is passed as three callbacks rather than three mutable ids: the open sheet
 * is the screen's state, and a helper that wrote it directly would have to know all
 * three ids the sheet keys off - the same reason the screen keeps one `closeSheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardClassicList(
    state: DashboardUiState.Success,
    filterCriteria: FilterCriteria,
    quickAddSettings: com.anisequel.data.repository.QuickAddPreferences.QuickAddSettings,
    arrivingEnabled: Boolean,
    arrivingCompact: Boolean,
    arrivingSeparateGroups: Boolean,
    onRequestRefresh: () -> Unit,
    onSelectSequel: (String) -> Unit,
    onSelectArriving: (String) -> Unit,
    onSelectParent: (MediaNode) -> Unit,
    viewModel: DashboardViewModel
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRequestRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("sequels_list"),
            // The bar floats over the list, so the Scaffold's bottom inset is
            // *replaced* by the bar's clearance rather than added to it - the two
            // describe the same strip of screen. The 32dp of breathing room stays on
            // top of it.
            contentPadding = PaddingValues(bottom = 32.dp + mainBarClearance()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.isDemoMode) {
                item(key = "demo_banner", contentType = "banner") {
                    DashboardDemoBanner(modifier = Modifier.widthIn(max = MaxContentWidth))
                }
            }

            // The saved-copy notice moved to the top app bar. It used to be an item
            // here, so it appeared and disappeared above the stats card as the network
            // answered and moved every row below it by its height.

            item(key = "stats_banner", contentType = "banner") {
                StatsBanner(
                    totalWatched = state.totalWatchedCount,
                    missedCount = state.totalMissedCount,
                    modifier = Modifier
                        .widthIn(max = MaxContentWidth)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Above the search bar and the missed-sequel list because it is a
            // different question from either, and hidden entirely when the viewer has
            // nothing arriving rather than rendering an empty header. The same
            // emptiness check gates the switch in Settings: disabled means the
            // section - and every poster load it draws - is not composed at all.
            if (state.arriving.isNotEmpty() && arrivingEnabled) {
                item(key = "arriving_section", contentType = "arriving") {
                    ArrivingSection(
                        entries = state.arriving,
                        compact = arrivingCompact,
                        separateGroups = arrivingSeparateGroups,
                        maxWidth = MaxContentWidth,
                        onEntryClick = { entry -> onSelectArriving(entry.namespaceKey) }
                    )
                }
            }

            item(key = "search_bar", contentType = "controls") {
                DashboardSearchBar(
                    query = filterCriteria.searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    maxWidth = MaxContentWidth
                )
            }

            item(key = "quick_filter_bar", contentType = "controls") {
                QuickFilterBar(
                    filterCriteria = filterCriteria,
                    onStatusSelected = { viewModel.updateStatusFilter(it) },
                    onFormatSelected = { viewModel.selectFormat(it) },
                    onClearAll = { viewModel.resetFilters() }
                )
            }

            item(key = "section_header", contentType = "header") {
                DashboardSectionHeader(
                    count = state.missedSequels.size,
                    filterCriteria = filterCriteria,
                    maxWidth = MaxContentWidth
                )
            }

            if (state.missedSequels.isEmpty()) {
                item(key = "empty_state", contentType = "empty") {
                    EmptyStateView(
                        // The same predicate as the badge and the quick filter row: an
                        // empty list with no filter active has nothing to reset, and an
                        // empty list with one active has a way out.
                        filtersActive = filterCriteria.isNarrowing,
                        onResetFilters = { viewModel.resetFilters() }
                    )
                }
            } else {
                items(
                    items = state.missedSequels,
                    // The bare ids collide across AniList's anime and manga
                    // namespaces, which made one card inherit another card's
                    // lazy state and could open the wrong title.
                    key = {
                        "${it.parentType.orEmpty().uppercase()}_${it.parentId}_${it.sequelMedia.namespaceKey}"
                    },
                    contentType = { "sequel_card" }
                ) { item ->
                    SequelCard(
                        sequel = item,
                        onClick = { onSelectSequel(item.sequelMedia.namespaceKey) },
                        onAddToPlanning = { viewModel.addToPlanning(item) },
                        onAddToWatching = { viewModel.addToWatching(item) },
                        holdDurationSeconds = quickAddSettings.holdDurationSeconds,
                        swipeEnabled = quickAddSettings.swipeEnabled,
                        onHide = { viewModel.toggleHideSequel(item) },
                        // The banner names the parent, and the parent is not in this
                        // list - so the tap hands the sheet a summary built from the
                        // four fields the relation walk kept, and the sheet's own
                        // detail fetch replaces it. Same route an arriving row takes.
                        onOpenParent = { onSelectParent(item.toParentSummary()) },
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

/** The filter sheet, hosted here so [DashboardScreen] does not branch for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardFilterSheetHost(
    visible: Boolean,
    sheetState: androidx.compose.material3.SheetState,
    filterCriteria: FilterCriteria,
    hiddenSequels: List<MissedSequel>,
    dashboardViewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    if (!visible) return

    FilterSortSheet(
        sheetState = sheetState,
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
        onDismiss = onDismiss
    )
}

/**
 * The bar is part of the two looks, not above them.
 *
 * The classic bar carries the filter button, the missed count and the collapse-
 * on-scroll behaviour the sequel feed is built around; the new look's bar is a
 * quiet identity strip over a page that puts its controls in the body. Swapping
 * only the body would leave a bar advertising controls the page does not have.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    appLayout: AppLayout,
    viewer: ViewerProfile?,
    missedCount: Int,
    onOpenFilter: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTiers: () -> Unit,
    isRefreshing: Boolean,
    hasActiveFilters: Boolean,
    showingSavedCopy: Boolean,
    scrollBehavior: TopAppBarScrollBehavior
) {
    if (appLayout == AppLayout.NOVA) {
        NovaTopBar(
            viewerName = viewer?.name,
            avatarUrl = viewer?.avatar?.medium,
            onOpenProfile = onOpenProfile,
            onOpenSettings = onOpenSettings,
            onOpenNotifications = onOpenNotifications,
            onOpenSearch = onOpenSearch.takeIf { LocalSearchTopBarShortcut.current },
            onOpenTiers = onOpenTiers.takeIf { LocalTierTopBarShortcut.current }
        )
    } else {
        AniTopAppBar(
            viewer = viewer,
            missedCount = missedCount,
            onOpenFilter = onOpenFilter,
            onRefresh = onRefresh,
            onOpenSettings = onOpenSettings,
            onOpenNotifications = onOpenNotifications,
            onOpenProfile = onOpenProfile,
            isRefreshing = isRefreshing,
            hasActiveFilters = hasActiveFilters,
            onOpenSearch = onOpenSearch.takeIf { LocalSearchTopBarShortcut.current },
            onOpenTiers = onOpenTiers.takeIf { LocalTierTopBarShortcut.current },
            showingSavedCopy = showingSavedCopy,
            scrollBehavior = scrollBehavior
        )
    }
}

/** Chooses the body without putting the layout branch in [DashboardScreen]. */
@Composable
private fun DashboardSuccessPane(
    layout: AppLayout,
    nova: @Composable () -> Unit,
    classic: @Composable () -> Unit
) {
    // Crossfade, not an `if`: switching the app layout in Settings swapped the
    // whole Home body in one frame, which read as the page reloading rather than
    // the reader changing how it is arranged. The two layouts share the same
    // view model, so the incoming one draws the data the outgoing one already
    // had; only the arrangement changes. `FastEffects` follows Motion style, so
    // Instant still switches in one frame as asked.
    Crossfade(
        targetState = layout,
        animationSpec = ExpressiveMotion.FastEffects,
        label = "dashboard_layout"
    ) { current ->
        if (current == AppLayout.NOVA) nova() else classic()
    }
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
