package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.model.ArrivingEntry
import com.anisequel.data.model.FilterCriteria
import com.anisequel.data.model.MediaEntryEdit
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.RelationKind
import com.anisequel.data.model.SequelSortOption
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.StatusFilter
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.ArrivingPreferences
import com.anisequel.data.repository.RefreshInterval
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.domain.usecase.FindArrivingEntriesUseCase
import com.anisequel.domain.usecase.FindMissedSequelsUseCase
import com.anisequel.domain.usecase.GetViewerProfileUseCase
import com.anisequel.domain.usecase.RecentActivitySummary
import com.anisequel.domain.usecase.SaveToPlanningUseCase
import com.anisequel.domain.usecase.SummarizeRecentActivityUseCase
import com.anisequel.ui.components.detail.EntryEditor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import com.anisequel.data.repository.HiddenSequelsPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dashboard state holder: load, filter, and mutate the missed-sequel list.
 *
 * ## Where the rest of this went
 *
 * [DashboardUiState] and [DashboardEvent] live in `DashboardUiState.kt` now -
 * they are the screen's contract, not this class's internals. This file is
 * everything that *behaves*: fetching, the recompute pipeline, and the mutations
 * the UI calls.
 *
 * The recompute half in particular - `discoveredCandidates`, `discoveryKey`, the
 * detail cache, the watched-count memo and the generation guard on the debounced
 * search - is the part of this class most worth reading, and it is still here
 * only because it needs the ViewModel's own private state to be coherent. That
 * coupling is real; see TO-DO.md for the extraction that would let it move.
 */
class DashboardViewModel(
    private val aniListRepository: AniListRepository,
    private val targetUsername: String? = null,
    private val isDemo: Boolean = false,
    private val getViewerProfileUseCase: GetViewerProfileUseCase = GetViewerProfileUseCase(aniListRepository),
    private val saveToPlanningUseCase: SaveToPlanningUseCase = SaveToPlanningUseCase(aniListRepository),
    private val saveToWatchingUseCase: com.anisequel.domain.usecase.SaveToWatchingUseCase = com.anisequel.domain.usecase.SaveToWatchingUseCase(aniListRepository),
    private val findMissedSequelsUseCase: FindMissedSequelsUseCase = FindMissedSequelsUseCase(),
    private val findArrivingEntriesUseCase: FindArrivingEntriesUseCase = FindArrivingEntriesUseCase(),
    private val summarizeRecentActivityUseCase: SummarizeRecentActivityUseCase = SummarizeRecentActivityUseCase(),
    /**
     * Optional so the demo and public-profile dashboards - which have no reason
     * to remember anything - construct without one, and so existing call sites
     * keep compiling.
     */
    private val hiddenSequelsPreferences: HiddenSequelsPreferences? = null,
    /**
     * How stale the list is allowed to get before it is re-fetched.
     *
     * Optional for the same reason as [hiddenSequelsPreferences]: demo mode has
     * nothing to refresh, and without a store the app falls back to
     * [RefreshInterval.DEFAULT] rather than refusing to start.
     */
    private val refreshIntervalPreferences: RefreshIntervalPreferences? = null,

    /**
     * Whether the "Currently arriving" section shows, and how it draws.
     *
     * Optional for the same reason as [hiddenSequelsPreferences]: the demo and
     * public-profile dashboards construct without a store and fall back to the
     * shipped defaults (visible, full cards).
     */
    private val arrivingPreferences: ArrivingPreferences? = null
) : ViewModel(), EntryEditor {

    companion object {
        // Re-analysing the whole list on every keystroke visibly stutters once a
        // user has a few thousand completed entries, so the query is held back
        // until the typing pauses.
        private const val SEARCH_DEBOUNCE_MS = 220L
    }

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _filterCriteria = MutableStateFlow(FilterCriteria())
    val filterCriteria: StateFlow<FilterCriteria> = _filterCriteria.asStateFlow()

    private val _eventFlow = MutableSharedFlow<DashboardEvent>(
        // Default SharedFlow has no buffer, so emit() used to suspend until a
        // collector asked for the value. The snackbar collector is briefly
        // absent on every configuration change and detail sheet, and an undo
        // emitted during that gap simply vanished - and with `emit` suspending,
        // the hide persistence that follows it was delayed by the same gap.
        // Buffer a few events instead, dropping the oldest if a collector never
        // comes back, so the action and the Undo offer cannot wedge the VM.
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val eventFlow: SharedFlow<DashboardEvent> = _eventFlow.asSharedFlow()

    /**
     * What the user has chosen to stop being reminded about.
     *
     * A separate stream from [uiState] because it is not filtered, sorted or
     * searched - it is a settings-style list of decisions, and it has to survive
     * a search box that is filtering everything else out. Without it the only
     * route back to a hidden anime was to clear app data.
     */
    private val _hiddenSequels = MutableStateFlow<List<MissedSequel>>(emptyList())
    val hiddenSequels: StateFlow<List<MissedSequel>> = _hiddenSequels.asStateFlow()

    /**
     * Whether the "Currently arriving" section is shown at all.
     *
     * Mirrors [ArrivingPreferences] rather than storing the value once, so
     * flipping the switch in Settings changes the dashboard immediately and
     * without a refresh. Defaults to enabled until the store's first emission,
     * which matches the shipped behaviour for builds without a preferences
     * object behind them.
     */
    private val _arrivingEnabled = MutableStateFlow(true)
    val arrivingEnabled: StateFlow<Boolean> = _arrivingEnabled.asStateFlow()

    /** Mirror of [ArrivingPreferences]' compact choice; see [arrivingEnabled]. */
    private val _arrivingCompact = MutableStateFlow(false)
    val arrivingCompact: StateFlow<Boolean> = _arrivingCompact.asStateFlow()

    /**
     * The interval the user has chosen, mirrored here from the store.
     *
     * Collected rather than read once because [ListFreshnessWatch] asks for it on
     * every tick: picking "15 min" in Settings has to take effect immediately,
     * not on the next launch.
     */
    private var currentRefreshInterval: RefreshInterval = RefreshInterval.DEFAULT

    private val freshnessWatch = ListFreshnessWatch(
        scope = viewModelScope,
        interval = { currentRefreshInterval },
        // A lambda rather than a method reference, so the reference cannot depend
        // on the declaration order of the members it closes over.
        onRefresh = { performRefresh() }
    )

    init {
        // The hidden set outlives the process, so it is re-read on every launch
        // rather than held in memory. Guarded on Success because the stored
        // value usually arrives before the list has been fetched, and
        // recomputing against an unloaded list would just throw it away.
        hiddenSequelsPreferences?.let { preferences ->
            viewModelScope.launch {
                preferences.hiddenIds.collectLatest { ids ->
                    if (ids == _filterCriteria.value.hiddenMediaIds) return@collectLatest
                    _filterCriteria.value = _filterCriteria.value.copy(hiddenMediaIds = ids)
                    if (_uiState.value is DashboardUiState.Success) recompute()
                }
            }
        }

        // Collected rather than read once, so changing the interval in Settings
        // takes effect on the next check instead of after a restart. The watch
        // asks for this on every tick precisely so it does not need to be told.
        refreshIntervalPreferences?.let { preferences ->
            viewModelScope.launch {
                preferences.interval.collectLatest { interval ->
                    currentRefreshInterval = interval
                }
            }
        }

        // Mirrored the same way as the interval: a settings change should reach
        // the dashboard on the next composition, not after a rebuild.
        arrivingPreferences?.let { preferences ->
            viewModelScope.launch {
                preferences.settings.collectLatest { settings ->
                    _arrivingEnabled.value = settings.showArrivingSection
                    _arrivingCompact.value = settings.compactArrivingCards
                }
            }
        }
    }

    /**
     * The app has come to the foreground.
     *
     * Called on every resume and on every return to this destination, which is
     * the fix for handing back a list that went stale the moment it was fetched:
     * Android resumes a process rather than restarting it, so reopening AniSequel
     * used to answer from the same in-memory cache until the hour elapsed.
     *
     * A load already in flight is *deferred to*, not skipped. On a cold start
     * this runs moments after `init` started `loadData`, and Android delivers
     * exactly one resume per launch - so returning here left the freshness watch
     * stopped for the whole session, and a dashboard left open never noticed its
     * list had aged. Joining the load instead starts the watch the instant the
     * load settles, without stacking a second fetch on the first.
     */
    fun onForegrounded() {
        if (isDemo) return
        val load = loadJob
        if (load != null && load.isActive) {
            viewModelScope.launch {
                load.join()
                if (!isDemo) freshnessWatch.start()
            }
            return
        }

        // Starting is also the immediate staleness check, so this is the whole of
        // the reopen behaviour: an overdue list is refetched now, a current one
        // is left alone until the interval says otherwise.
        freshnessWatch.start()
    }

    /** The app has gone to the background; stop spending requests on nobody's screen. */
    fun onBackgrounded() {
        freshnessWatch.stop()
    }

    private var cachedViewer: ViewerProfile? = null
    private var cachedCollection: MediaListCollection? = null

    /**
     * Whether the pair above came from the disk snapshot rather than the network.
     *
     * Set when the saved copy is painted at the start of a load and cleared the
     * moment a live list is folded in. [recompute] reads it, so every Success
     * published from the cached pair carries `isCached = true` without each call
     * site having to remember - and the flag cannot be forgotten by the next path
     * that publishes a state.
     */
    private var showingCachedData = false
    private val addedToPlanningIds = mutableSetOf<Int>()
    private val addedToWatchingIds = mutableSetOf<Int>()
    private var searchJob: Job? = null

    /**
     * The in-flight recompute, and a token identifying the newest request.
     *
     * Every filter control - sort, status, format, relations, the two switches -
     * used to `launch { recompute() }` independently, with no relationship
     * between the jobs. `recompute` suspends on [Dispatchers.Default], so two
     * quick taps produced two overlapping runs, and whichever *finished* last
     * wrote `_uiState` - not whichever was asked for last. Tapping "Newest
     * First" then "Title (A-Z)" could leave the list sorted by the first, with
     * the header claiming the second.
     *
     * Both halves of the fix matter: [recomputeJob] cancels the previous run so
     * the wasted sort never happens, and [recomputeGeneration] is the guard that
     * makes the *result* order correct even when a cancelled run has already
     * passed its last suspension point and cannot be stopped.
     */
    private var recomputeJob: Job? = null
    private var recomputeGeneration = 0

    /**
     * Everything the list is missing, before filtering - *including* the entries
     * the user has hidden.
     *
     * Walking the relations of a few hundred entries is the expensive half of
     * this screen, and it only depends on two things: whether already-planned
     * entries are hidden, and which relation kinds are included. Caching on
     * those means sorting, searching and changing status or format no longer
     * re-walk the whole list.
     *
     * The hidden set is deliberately *not* part of the key, and hiding no longer
     * rebuilds this list. It used to be, because `discover` dropped hidden
     * entries - so hiding one entry re-walked every relation edge in the
     * account, on the UI thread's critical path, for a change that is really a
     * set membership test. The hidden half is now a partition of this same list
     * (see [FindMissedSequelsUseCase.splitHidden]), which makes hide and
     * unhide instant *and* is what gives the hidden list something to render.
     */
    private var discoveredCandidates: List<MissedSequel>? = null
    private var discoveryKey: Triple<Boolean, Set<RelationKind>, Pair<Boolean, Boolean>>? = null

    /**
     * The collection [discoveredCandidates] was walked from.
     *
     * The cache is a function of the collection as well as the filter key, and a
     * superseded run cannot be stopped once it has passed its last suspension
     * point (see [recomputeGeneration]). Without this identity check, such a run
     * could still *store* candidates derived from the previous collection, and
     * the next run with the same filters - a refresh's fresh data included -
     * would be served that stale walk. Same shape as [watchedCountSource] and
     * [arrivingSource], which is the pattern this one was missing.
     */
    private var discoveredSource: MediaListCollection? = null

    /**
     * Detail fetched on demand for the sheet.
     *
     * The dashboard is the one host that keeps a store of its own rather than the
     * app's shared `MediaDetailHost`: this one folds a fetched node into the
     * recommendation list through [foldDetailIntoList] and reads
     * [MediaDetailStore.cached] during recompute, so its store is also a writer
     * into the dashboard's own list. That is an addition to the shared rule, not a
     * second rule - the request, the cache key and the merge are the same code.
     */
    private val detailStore = MediaDetailStore(
        repository = aniListRepository,
        scope = viewModelScope,
        isDemo = isDemo,
        onLoaded = ::foldDetailIntoList
    )

    /** The node fetched for the entry a sheet is showing, keyed by media id. */
    val detailNodes: StateFlow<Map<Int, MediaNode>> get() = detailStore.details

    /** Followed users' entries for the entry a sheet is showing, keyed by media id. */
    val friends: StateFlow<Map<Int, List<MediaFriend>>> get() = detailStore.friends

    /**
     * The completed count for [cachedCollection], and the collection it was
     * computed from.
     *
     * [countWatched] walks every list entry and is a pure function of the
     * collection - but [recompute] runs on every filter change, so typing in the
     * search box re-counted the whole account (a 476-entry account means ~476
     * status comparisons) on every debounced keystroke, to produce a number that
     * cannot have changed. Keyed on the collection *identity*, which is what the
     * refresh and demo paths replace it with.
     */
    private var watchedCountCache: Int? = null
    private var watchedCountSource: MediaListCollection? = null

    /**
     * The arriving rows for [cachedCollection], and the collection they were
     * derived from.
     *
     * The same memo shape as [watchedCountCache] for the same reason: the
     * answer cannot change while the collection does not, and [recompute] runs
     * on every keystroke. Walking the entries is cheap next to the relation
     * walk, but "cheap per keystroke" is how a large account still ends up
     * stuttering.
     */
    private var arrivingCache: List<ArrivingEntry>? = null
    private var arrivingSource: MediaListCollection? = null

    /**
     * The widget's recent-activity summary for [cachedCollection], and the
     * collection it was computed from.
     *
     * The same memo shape as [watchedCountCache]: the answer cannot change
     * while the collection does not, and [recompute] runs on every keystroke.
     * The seven-day window is time-dependent, but the only way the count moves
     * without a new collection is the clock crossing the edge - and the
     * dashboard refetches on its interval and on open, which replaces the
     * collection and invalidates this.
     */
    private var recentActivityCache: RecentActivitySummary? = null
    private var recentActivitySource: MediaListCollection? = null

    /**
     * Ids whose one-off detail query is in flight.
     *
     * Kept out of [DashboardUiState.Success] deliberately: a detail arriving
     * patches a single card, and routing that through a full recompute would
     * throw away the "loading" state the sheet is rendering.
     */
    val loadingDetailIds: StateFlow<Set<Int>> get() = detailStore.loading

    /** True when the current session may write back to the user's AniList. */
    private val canWriteToAniList: Boolean
        get() = !isDemo && targetUsername.isNullOrBlank()

    init {
        loadData()
    }

    /**
     * The in-flight load, cancelled before a new one starts.
     *
     * Without this, two overlapping loads both ran to completion and the
     * slower one wrote its result last: tap Refresh twice quickly, or rotate
 * *while* the first load was still running, and the screen ended up showing a
     * stale list with no error anywhere - the two coroutines were writing to
     * the same `_uiState` and the loser won.
     */
    private var loadJob: Job? = null

    fun loadData() {
        if (isDemo) {
            viewModelScope.launch { loadDemoData() }
            return
        }

        // A load already in flight is cancelled before starting another, so
        // only the most recent request can write to the state.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val username = targetUsername?.takeUnless { it.isBlank() }
            if (!paintSavedCopy()) {
                _uiState.value = DashboardUiState.Loading(loadingMessage(username))
            }

            // A public profile is two independent requests: the profile is
            // looked up by name and so is the list, so neither needs the
            // other's id and nothing about one determines the other. Running
            // them together halves the wait to first paint on that screen -
            // and it is what makes `getUserAnimeListByUsername`, the by-name
            // half of the repository, a path anything actually calls.
            //
            // The signed-in path cannot do this: the list query is keyed on
            // the viewer's id, which is the thing being fetched.
            val viewerDeferred = async {
                if (username != null) {
                    aniListRepository.getUserByName(username)
                } else {
                    getViewerProfileUseCase.execute()
                }
            }
            val listDeferred = username?.let { name ->
                async { aniListRepository.getUserAnimeListByUsername(name) }
            }

            viewerDeferred.await().fold(
                onSuccess = { viewer -> loadListFor(viewer, listDeferred) },
                onFailure = { error -> keepSavedCopyOrFail(error, "Failed to connect with AniList") }
            )
        }
    }

    /**
     * The saved copy, painted as the first frame of a load.
     *
     * Split out of [loadData] partly because it is a state machine of its own -
     * read, adopt, recompute, report whether anything was drawn - and partly
     * because the whole pipeline in one function had grown past what CodeFactor's
     * "complex method" gate tolerates. The gate is coarse, but the split is the
     * one a reader wants anyway.
     *
     * Signed-in only: the public-profile path is addressed by name and has no
     * viewer record to read.
     */
    private suspend fun paintSavedCopy(): Boolean {
        if (!targetUsername.isNullOrBlank()) return false
        val savedViewer = aniListRepository.lastKnownViewer() ?: return false
        val savedList = aniListRepository.lastKnownAnimeList(savedViewer.id) ?: return false
        cachedViewer = savedViewer
        cachedCollection = savedList
        showingCachedData = true
        invalidateDerivedState(clearDetailCache = true)
        return true
    }

    private fun loadingMessage(username: String?): String =
        if (username != null) {
            "Searching for @$username on AniList..."
        } else {
            "Connecting to AniList..."
        }

    /** The second half of [loadData]: the profile answered, so ask for its list. */
    private suspend fun loadListFor(
        viewer: ViewerProfile,
        listDeferred: Deferred<Result<MediaListCollection>>?
    ) {
        cachedViewer = viewer
        if (_uiState.value !is DashboardUiState.Success) {
            _uiState.value = DashboardUiState.Loading("Scanning ${viewer.name}'s anime history...")
        }

        val listResult = listDeferred?.await() ?: aniListRepository.getUserAnimeList(viewer.id)
        listResult.fold(
            onSuccess = { collection ->
                cachedCollection = collection
                showingCachedData = false
                freshnessWatch.markLoaded()
                // A new list invalidates everything derived from it.
                invalidateDerivedState()
            },
            onFailure = { error -> keepSavedCopyOrFail(error, "Failed to fetch anime list") }
        )
    }

    /**
     * The saved copy is on screen and is still the reader's list, so a failed
     * request leaves it there, marked as cached.
     *
     * A rejected token is the one failure this may not paper over: the screen
     * would keep drawing a session that no longer works, and the "Sign in again"
     * exit lives on the error state this would replace.
     */
    private suspend fun keepSavedCopyOrFail(error: Throwable, fallback: String) {
        val cached = _uiState.value as? DashboardUiState.Success
        val isDeadSession = (error as? AniListException)?.kind == AniListErrorKind.INVALID_SESSION
        if (cached == null || isDeadSession) {
            showError(error, fallback)
            return
        }
        _uiState.value = cached.copy(isRefreshing = false)
        if (cached.isCached) {
            _eventFlow.emit(
                DashboardEvent.ShowSnackbar("Showing your saved copy - AniList did not answer.")
            )
        }
    }

    /**
     * A dead token is not a network blip: retrying the same request can never
     * succeed, so the screen needs a way back to the login screen rather than a
     * Retry button that fails identically every time.
     */
    private fun showError(error: Throwable, fallback: String) {
        _uiState.value = DashboardUiState.Error(
            message = error.message ?: fallback,
            isAuthError = error.isAuthFailure(),
            kind = (error as? AniListException)?.kind
        )
    }

    private fun Throwable.isAuthFailure(): Boolean =
        (this as? AniListException)?.kind == AniListErrorKind.INVALID_SESSION

    /**
     * A refresh the reader asked for - the button or the pull gesture.
     *
     * Goes through the freshness watch rather than straight to [performRefresh]
     * so a double pull and an accidental second tap collapse into one request.
     * The watch also owns the automatic path, which is why the two cannot drift
     * into different cooldowns.
     */
    fun refresh() {
        freshnessWatch.requestManualRefresh()
    }

    /**
     * The fetch itself, shared by the gesture, the interval and the post-save
     * reload. Never called directly by a screen: a caller outside this class
     * goes through [refresh] so the cooldown applies.
     */
    private fun performRefresh() {
        // The refresh button used to blank the list back to a full-screen loading
        // state, which for a large account meant losing the results the user was
        // reading to watch a spinner. Keep the list up and show progress in place.
        (_uiState.value as? DashboardUiState.Success)?.let { current ->
            _uiState.value = current.copy(isRefreshing = true)
        }

        if (isDemo) {
            viewModelScope.launch { loadDemoData() }
            return
        }

        viewModelScope.launch {
            // Falling back to cached data rather than to `null` - a detail load
            // landing in between used to clear this, so tapping Refresh after
            // opening a card re-fetched the viewer's entire list from scratch
            // instead of just the entries.
            //
            // When even the viewer cannot be resolved, surface it as an error the
            // screen can retry from. This used to install a `Loading` state and
            // return, with nothing left to leave it: the spinner spun forever and
            // the Refresh button that would have fixed it was on the screen this
            // replaced.
            val viewer = cachedViewer ?: getViewerProfileUseCase.execute().getOrElse { error ->
                showError(error, fallback = "Failed to refresh from AniList")
                return@launch
            }
            cachedViewer = viewer

            // forceRefresh, or this would be served from the list cache and the
            // gesture would do nothing at all - the list would look stuck
            // while the spinner dutifully ran.
            aniListRepository.getUserAnimeList(viewer.id, forceRefresh = true).fold(
                onSuccess = { collection ->
                    cachedCollection = collection
                    showingCachedData = false
                    // Both a manual refresh and an automatic one land here, so
                    // this is the single place the freshness clock restarts.
                    // Without it the interval is measured from the *previous*
                    // fetch, so a dashboard left open would keep re-fetching on a
                    // schedule that ignored the ones it had just done.
                    freshnessWatch.markLoaded()
                    // The server is the source of truth for what is already
                    // planned, so the optimistic local set is dropped here.
                    //
                    // The detail cache is deliberately kept: a refresh that had to
                    // re-fetch every entry detail would be slower than the
                    // gesture is worth, and the entries themselves have not
                    // changed identity.
                    invalidateDerivedState(clearDetailCache = false)
                },
                onFailure = { err ->
                    val success = _uiState.value as? DashboardUiState.Success
                    if (success != null) {
                        _uiState.value = success.copy(isRefreshing = false)
                    } else {
                        showError(err, fallback = "Failed to refresh from AniList")
                    }
                }
            )
        }
    }

    private suspend fun loadDemoData() {
        cachedViewer = aniListRepository.getDemoProfile()
        cachedCollection = aniListRepository.getDemoAnimeList()
        // Cleared here for the same reason as a real load: the demo list is a
        // different collection from whatever was loaded before it, and the
        // detail cache is keyed by media id. This path used to skip the clear,
        // so a demo load following a real one could show details fetched for the
        // previous viewer.
        invalidateDerivedState()
    }

    /**
     * Drops everything derived from the current [cachedCollection] and rebuilds
     * the screen from it.
     *
     * ## Why this is one function
     *
     * The reset was written out three times - in `loadData`, `refresh` and
     * `loadDemoData` - and the three copies had drifted apart. The demo path had
     * come to omit the detail clear and `addedToPlanningIds.clear()`,
     * which is a latent wrong-data bug rather than a style one: the detail cache
     * is keyed by media id, so a demo load following a real one could render
     * entries against details fetched for a different viewer.
     *
     * The refresh path omits the detail clear on purpose - re-fetching every
     * entry detail would make the gesture slower than it is worth - so the two
     * flags are named at the call site rather than left to be remembered. What
     * is no longer possible is a *third* path quietly forgetting a step.
     */
    private suspend fun invalidateDerivedState(clearDetailCache: Boolean = true) {
        discoveredCandidates = null
        discoveredSource = null
        discoveryKey = null
        addedToPlanningIds.clear()
        addedToWatchingIds.clear()
        if (clearDetailCache) detailStore.clear()
        recompute()
    }

    /**
     * Recomputes what is on screen.
     *
     * The list walk and the sort both run on [Dispatchers.Default]: for a large
     * account this is thousands of comparisons over hundreds of objects, which
     * used to run on the main thread and drop frames on every filter change.
     */
    private suspend fun recompute() {
        val viewer = cachedViewer ?: return
        val collection = cachedCollection ?: return
        val criteria = _filterCriteria.value

        // Claim a token for this run. See [recomputeGeneration].
        val generation = ++recomputeGeneration

        val candidates = discoverOnce(collection, criteria)

        // Partition before filtering: the hidden half must be published even
        // when a search or a status filter has emptied the visible one,
        // otherwise the settings list of hidden anime would vanish the moment
        // someone typed into the search box. Both halves of this are list work
        // over immutable inputs, so they share one trip to the default
        // dispatcher rather than paying a suspension each.
        val (split, filteredVisible) = withContext(Dispatchers.Default) {
            val splitResult = findMissedSequelsUseCase.splitHidden(candidates, criteria.hiddenMediaIds)
            splitResult to findMissedSequelsUseCase.applyFilters(splitResult.visible, criteria)
        }

        val watchedCount = countWatched(collection)
        // Derived unconditionally: the work is memoized (one walk per
        // collection), and re-deriving on a toggle is more than it saves. The
        // switches in Settings pay their way by stopping the section - and its
        // poster loads - from being composed at all, not by skipping this.
        val arriving = arrivingEntries(collection)
        val recentActivity = recentActivity(collection)

        val updated = filteredVisible.map { found ->
            val entry = withCachedDetail(found)
            // The server is authoritative. `addedToPlanningIds` records what
            // *this session* added, and is cleared on every refresh, so a
            // mutation that AniList accepted and a filter that hides the
            // entry can both leave the optimistic flag behind. Re-checking
            // against the loaded list keeps "Add to Planning" from showing
            // as still-pending for something already saved.
            val onList = entry.sequelMedia.mediaListEntry
            val isWatching = addedToWatchingIds.contains(entry.sequelId) ||
                    onList?.status.equals("CURRENT", ignoreCase = true)
            val isPlanned = !isWatching && (addedToPlanningIds.contains(entry.sequelId) ||
                    onList?.status != null)

            entry.copy(
                isAddedToWatching = isWatching,
                isAddedToPlanning = isPlanned
            )
        }

        // A run that has been superseded must not publish. Without this an
        // older recompute that happened to finish last overwrote the newer list
        // with a stale sort or filter - the control and the content disagreeing
        // with nothing on screen to explain it.
        if (generation != recomputeGeneration) return

        _hiddenSequels.value = split.hidden

        _uiState.value = DashboardUiState.Success(
            viewer = viewer,
            missedSequels = updated,
            arriving = arriving,
            totalWatchedCount = watchedCount,
            totalMissedCount = updated.size,
            recentActivity = recentActivity,
            isRefreshing = false,
            isCached = showingCachedData,
            isDemoMode = isDemo,
            canWriteToAniList = canWriteToAniList
        )
    }

    /**
     * Recomputes on behalf of a filter change, cancelling any run already in
     * flight.
     *
     * A cancelled recompute still bumps [recomputeGeneration] when it starts, so
     * the generation guard alone would be enough for correctness; cancelling on
     * top of it is what stops a rapid sequence of taps from paying for a sort
     * per tap when only the last one is ever drawn.
     */
    private fun requestRecompute() {
        recomputeJob?.cancel()
        recomputeJob = viewModelScope.launch { recompute() }
    }

    private suspend fun discoverOnce(
        collection: MediaListCollection,
        criteria: FilterCriteria
    ): List<MissedSequel> {
        // Every input [FindMissedSequelsUseCase.discover] actually reads has to
        // be in this key.
        val key = Triple(
            criteria.hideAlreadyPlanned,
            criteria.includedRelations,
            Pair(criteria.includeCurrentlyWatching, criteria.includeInList)
        )
        discoveredCandidates?.let { cached ->
            // Both the criteria *and* the collection have to match. The
            // collection was not part of this check before, so a run that started
            // before a refresh could store a walk of the old list and the next
            // run - over the new list - would reuse it, silently replacing the
            // refreshed data with the previous snapshot.
            if (discoveredSource === collection && discoveryKey == key) return cached
        }

        val discovered = withContext(Dispatchers.Default) {
            // Empty hidden set plus `includeHidden`: the walk returns the whole
            // picture and the split decides what the user sees.
            findMissedSequelsUseCase.discover(
                collection = collection,
                filterCriteria = criteria.copy(hiddenMediaIds = emptySet()),
                includeHidden = true
            )
        }
        discoveredCandidates = discovered
        discoveredSource = collection
        discoveryKey = key
        return discovered
    }

    /** The completed count, recomputed only when the collection itself changes. */
    private fun countWatched(collection: MediaListCollection): Int {
        watchedCountCache?.let { cached ->
            if (watchedCountSource === collection) return cached
        }
        val computed = countWatchedUncached(collection)
        watchedCountSource = collection
        watchedCountCache = computed
        return computed
    }

    private fun countWatchedUncached(collection: MediaListCollection): Int {
        val lists = collection.lists ?: return 0
        val entries = lists.flatMap { group -> group.entries.orEmpty() }
        return entries.count { entry ->
            val status = entry.status ?: entry.media.mediaListEntry?.status
            status.equals("COMPLETED", ignoreCase = true) ||
                    (entry.progress != null && entry.media.episodes != null &&
                            entry.media.episodes > 0 && entry.progress >= entry.media.episodes)
        }
    }

    /**
     * Folds an already-fetched detail into a sequel card, without a request.
     *
     * Namespaced like the fetch it reads from: the cache is keyed on
     * [MediaCoverKey], so a manga's detail is not read back out of the anime's slot
     * when the two ids happen to collide. Falls back to the card's own node, which
     * is the behaviour that predates the namespacing.
     */
    private fun withCachedDetail(sequel: MissedSequel): MissedSequel =
        detailStore.cached(sequel.sequelId, sequel.sequelMedia.type)
            ?.let(sequel::withDetail)
            ?: sequel

    /**
     * The arriving rows, recomputed only when the collection itself changes.
     * See [arrivingCache] for why this is memoised at all.
     */
    private fun arrivingEntries(collection: MediaListCollection): List<ArrivingEntry> {
        arrivingCache?.let { cached ->
            if (arrivingSource === collection) return cached
        }
        val computed = findArrivingEntriesUseCase.execute(collection)
        arrivingSource = collection
        arrivingCache = computed
        return computed
    }

    /**
     * The activity tile's summary, recomputed only when the collection itself
     * changes. See [recentActivityCache] for why this is memoised.
     */
    private fun recentActivity(collection: MediaListCollection): RecentActivitySummary {
        recentActivityCache?.let { cached ->
            if (recentActivitySource === collection) return cached
        }
        val computed = summarizeRecentActivityUseCase.execute(
            collection = collection,
            nowSeconds = (System.currentTimeMillis() / 1000L).toInt()
        )
        recentActivitySource = collection
        recentActivityCache = computed
        return computed
    }

    /**
     * Fetches the synopsis, banner and studio for one entry.
     *
     * These are the fields the list query leaves out on purpose: they are only
     * ever rendered for the entry a user opens, and asking AniList for them
     * across every relation node is what makes it return HTTP 500 on large
     * accounts. Demo data is already complete, so [MediaDetailStore] skips the
     * call.
     */
    /**
     * Fetches the heavy fields for one entry, namespaced by media type.
     *
     * The type is a parameter rather than being looked up from the id because the
     * two are not interchangeable: AniList numbers `anime/` and `manga/`
     * independently, and an untyped fetch resolves against both. See
     * [MediaDetailStore.load].
     */
    fun loadDetail(mediaId: Int, mediaType: String? = null) {
        detailStore.load(mediaId, mediaType)
    }

    /**
     * Fetches the followed users who have this title on their list.
     *
     * Separate from [loadDetail] because it is a separate request with a separate
     * failure mode - see [MediaDetailStore.loadFriends].
     */
    fun loadFriends(mediaId: Int, mediaType: String? = null) {
        detailStore.loadFriends(mediaId, mediaType)
    }

    /**
     * Folds a freshly fetched detail into the list entry it belongs to.
     *
     * The sheet reads the node from [detailNodes] directly, but a card sitting
     * behind the sheet should reflect the same detail the moment it lands
     * rather than on the next recompute.
     */
    private fun foldDetailIntoList(mediaId: Int, detail: MediaNode) {
        val success = _uiState.value as? DashboardUiState.Success ?: return
        _uiState.value = success.copy(
            missedSequels = success.missedSequels.map { item ->
                if (item.sequelId == mediaId) item.withDetail(detail) else item
            }
        )
    }

    fun updateSearchQuery(query: String) {
        _filterCriteria.value = _filterCriteria.value.copy(searchQuery = query)

        // Debounced here, but the run itself goes through [requestRecompute] so
        // it cancels an in-flight recompute the way every other filter change
        // does. It used to call `recompute()` directly, which left a sort from
        // a previous tap free to finish after this one and win.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            requestRecompute()
        }
    }

    fun updateSortOption(sortOption: SequelSortOption) {
        _filterCriteria.value = _filterCriteria.value.copy(sortOption = sortOption)
        requestRecompute()
    }

    fun updateStatusFilter(status: StatusFilter) {
        // Choosing "Upcoming" while "Include unreleased" was off produced an
        // empty screen with nothing on it to explain why: the switch said
        // upcoming entries were filtered out, and the status chip asked for
        // exactly those.
        val criteria = _filterCriteria.value
        _filterCriteria.value = if (status == StatusFilter.NOT_YET_RELEASED) {
            criteria.copy(statusFilter = status, includeUnreleased = true)
        } else {
            criteria.copy(statusFilter = status)
        }
        requestRecompute()
    }

    fun toggleIncludeUnreleased(include: Boolean = !_filterCriteria.value.includeUnreleased) {
        _filterCriteria.value = _filterCriteria.value.copy(includeUnreleased = include)
        requestRecompute()
    }

    fun toggleHideAlreadyPlanned(hide: Boolean = !_filterCriteria.value.hideAlreadyPlanned) {
        _filterCriteria.value = _filterCriteria.value.copy(hideAlreadyPlanned = hide)
        requestRecompute()
    }

    /** Widens the results to prequels, side stories and spin-offs. */
    fun toggleRelation(kind: RelationKind, enabled: Boolean? = null) {
        val current = _filterCriteria.value.includedRelations
        val next = when (enabled) {
            null -> if (kind in current) current - kind else current + kind
            true -> current + kind
            false -> current - kind
        }
        _filterCriteria.value = _filterCriteria.value.copy(
            includedRelations = next.ifEmpty { setOf(RelationKind.SEQUEL) }
        )
        requestRecompute()
    }

    fun selectFormat(format: String?) {
        _filterCriteria.value = _filterCriteria.value.copy(selectedFormat = format)
        requestRecompute()
    }

    /** Adds or removes one genre from the OR-ed genre selection. */
    fun toggleGenre(genre: String) {
        val current = _filterCriteria.value.selectedGenres
        _filterCriteria.value = _filterCriteria.value.copy(
            selectedGenres = if (genre in current) current - genre else current + genre
        )
        requestRecompute()
    }

    /** Sets the minimum average score, or null for no minimum. */
    fun selectMinScore(score: Int?) {
        _filterCriteria.value = _filterCriteria.value.copy(minScore = score)
        requestRecompute()
    }

    /**
     * Narrows the list to sequels releasing in the current calendar year.
     *
     * Kept out of the quick filter bar deliberately: it is a refinement of the
     * kind of thing the filter sheet exists for, and there is no room on the
     * bar for a control that needs a sentence to explain what "this year"
     * measures.
     */
    fun toggleSequelReleasedThisYear(enabled: Boolean = !_filterCriteria.value.sequelReleasedThisYear) {
        _filterCriteria.value = _filterCriteria.value.copy(sequelReleasedThisYear = enabled)
        requestRecompute()
    }

    /** Narrows the list to sequels whose parent the viewer completed this year. */
    fun toggleParentCompletedThisYear(enabled: Boolean = !_filterCriteria.value.parentCompletedThisYear) {
        _filterCriteria.value = _filterCriteria.value.copy(parentCompletedThisYear = enabled)
        requestRecompute()
    }

    /** Finds sequels to currently watching shows or sequels already in watching list. */
    fun toggleIncludeCurrentlyWatching(enabled: Boolean = !_filterCriteria.value.includeCurrentlyWatching) {
        _filterCriteria.value = _filterCriteria.value.copy(includeCurrentlyWatching = enabled)
        requestRecompute()
    }

    /** Finds sequels already tracked in the user's list (Completed, Paused, etc.). */
    fun toggleIncludeInList(enabled: Boolean = !_filterCriteria.value.includeInList) {
        _filterCriteria.value = _filterCriteria.value.copy(includeInList = enabled)
        requestRecompute()
    }

    fun resetFilters() {
        searchJob?.cancel()
        // Hidden entries are deliberately carried across a filter reset. They are
        // not a view of the list, they are a standing decision about particular
        // anime - resetting the search box should not quietly put back something
        // the user asked never to see again.
        _filterCriteria.value = FilterCriteria(
            hiddenMediaIds = _filterCriteria.value.hiddenMediaIds
        )
        requestRecompute()
    }

    /**
     * Stops offering [sequel] again, or brings it back if it is already hidden.
     *
     * The result is persisted rather than kept in memory: this is the one thing
     * in [FilterCriteria] the user made on purpose, and losing it on next
     * launch would make it a gesture with no effect.
     *
     * The in-memory set is updated as well as the store, and *first*. The store
     * write re-emits through [init]'s collector, which is what would otherwise
     * be the only thing to update the set - and that path is skipped whenever
     * there is no store at all. Demo mode builds this ViewModel without one, so
     * "Not interested" there used to return before doing anything at all: the
     * gesture looked broken rather than unsupported.
     */
    fun toggleHideSequel(sequel: MissedSequel) {
        val nowHidden = sequel.sequelId !in _filterCriteria.value.hiddenMediaIds
        setHidden(sequel.sequelId, hidden = nowHidden)

        viewModelScope.launch {
            hiddenSequelsPreferences?.let { preferences ->
                preferences.toggle(sequel.sequelId)
            }
            _eventFlow.emit(
                if (nowHidden) {
                    DashboardEvent.ShowSnackbar(
                        message = "Hidden \"${sequel.sequelTitle}\".",
                        actionLabel = "Undo"
                    )
                } else {
                    DashboardEvent.ShowSnackbar("\"${sequel.sequelTitle}\" can show up again.")
                }
            )
        }
    }

    /**
     * Brings one hidden entry back, for the row button in the hidden list.
     *
     * The mirror of [toggleHideSequel] rather than a call to it: the hidden list
     * already knows the entry *is* hidden, so re-deriving that to pick a branch
     * would only be able to get it wrong.
     */
    fun restoreHiddenSequel(sequel: MissedSequel) {
        setHidden(sequel.sequelId, hidden = false)

        viewModelScope.launch {
            hiddenSequelsPreferences?.let { preferences ->
                preferences.toggle(sequel.sequelId)
            }
            _eventFlow.emit(
                DashboardEvent.ShowSnackbar("\"${sequel.sequelTitle}\" can show up again.")
            )
        }
    }

    /**
 * Unhides everything, for the "show all again" affordance.
 *
 * Recomputes explicitly rather than leaning on [setHidden]. The persisted
 * store used to be the only thing that republished the list - its flow
 * re-emitted and `init`'s collector recomputed - but that collector returns
 * early when the stored set already matches, and `clear()` emits exactly the
 * empty set that was just written to the criteria. So the stored path is silent
 * here and the rows would sit there looking like the button had done nothing.
 */
    fun restoreAllHiddenSequels() {
        if (_filterCriteria.value.hiddenMediaIds.isEmpty()) return
        _filterCriteria.value = _filterCriteria.value.copy(hiddenMediaIds = emptySet())
        requestRecompute()

        viewModelScope.launch {
            hiddenSequelsPreferences?.let { preferences ->
                preferences.clear()
            }
            _eventFlow.emit(DashboardEvent.ShowSnackbar("All hidden anime can show up again."))
        }
    }

    /**
     * Applies a change to the hidden set and republishes the list.
     *
     * One place for every caller, because forgetting the `recompute` is how the
     * gesture ends up doing nothing: the list on screen is derived from the
     * candidates partitioned against this set, so the set moving without a
     * recompute leaves the card still sitting there.
     */
    private fun setHidden(mediaId: Int, hidden: Boolean) {
        val current = _filterCriteria.value.hiddenMediaIds
        val updated = if (hidden) current + mediaId else current - mediaId
        if (updated == current) return
        _filterCriteria.value = _filterCriteria.value.copy(hiddenMediaIds = updated)
        requestRecompute()
    }

    fun addToPlanning(sequel: MissedSequel) {
        val current = _uiState.value
        if (current !is DashboardUiState.Success) return

        if (!current.canWriteToAniList) {
            viewModelScope.launch {
                _eventFlow.emit(
                    DashboardEvent.ShowSnackbar(
                        if (current.isDemoMode) {
                            "Demo mode: nothing is saved to AniList. Connect an account to add entries for real."
                        } else {
                            "Connect your own AniList account to add entries to your Planning list."
                        }
                    )
                )
            }
            return
        }

        val updatedList = current.missedSequels.map {
            if (it.sequelId == sequel.sequelId) it.copy(isAddingToPlanning = true) else it
        }
        _uiState.value = current.copy(missedSequels = updatedList)

        viewModelScope.launch {
            saveToPlanningUseCase.execute(sequel.sequelId).fold(
                onSuccess = {
                    addedToPlanningIds.add(sequel.sequelId)
                    _eventFlow.emit(
                        DashboardEvent.ShowSnackbar(
                            "Added \"${sequel.sequelTitle}\" to your AniList Planning list"
                        )
                    )
                    recompute()
                },
                onFailure = { err ->
                    // Clearing isAddingToPlanning matters: the detail sheet holds
                    // its own copy, and without this it spins forever because
                    // nothing told it the request had ended.
                    val success = _uiState.value as? DashboardUiState.Success
                    if (success != null) {
                        _uiState.value = success.copy(
                            missedSequels = success.missedSequels.map { item ->
                                if (item.sequelId == sequel.sequelId) {
                                    item.copy(isAddingToPlanning = false)
                                } else {
                                    item
                                }
                            }
                        )
                    }

                    _eventFlow.emit(
                        DashboardEvent.ShowSnackbar(
                            err.message ?: "Could not add to your Planning list"
                        )
                    )
                }
            )
        }
    }

    fun addToWatching(sequel: MissedSequel) {
        val current = _uiState.value
        if (current !is DashboardUiState.Success) return

        if (!current.canWriteToAniList) {
            viewModelScope.launch {
                _eventFlow.emit(
                    DashboardEvent.ShowSnackbar(
                        if (current.isDemoMode) {
                            "Demo mode: nothing is saved to AniList. Connect an account to add entries for real."
                        } else {
                            "Connect your own AniList account to add entries to your Watching list."
                        }
                    )
                )
            }
            return
        }

        val updatedList = current.missedSequels.map {
            if (it.sequelId == sequel.sequelId) it.copy(isAddingToWatching = true) else it
        }
        _uiState.value = current.copy(missedSequels = updatedList)

        viewModelScope.launch {
            saveToWatchingUseCase.execute(sequel.sequelId).fold(
                onSuccess = {
                    addedToWatchingIds.add(sequel.sequelId)
                    addedToPlanningIds.remove(sequel.sequelId)
                    _eventFlow.emit(
                        DashboardEvent.ShowSnackbar(
                            "Added \"${sequel.sequelTitle}\" to your Currently Watching list"
                        )
                    )
                    recompute()
                },
                onFailure = { err ->
                    val success = _uiState.value as? DashboardUiState.Success
                    if (success != null) {
                        _uiState.value = success.copy(
                            missedSequels = success.missedSequels.map { item ->
                                if (item.sequelId == sequel.sequelId) {
                                    item.copy(isAddingToWatching = false)
                                } else {
                                    item
                                }
                            }
                        )
                    }

                    _eventFlow.emit(
                        DashboardEvent.ShowSnackbar(
                            err.message ?: "Could not add to your Watching list"
                        )
                    )
                }
            )
        }
    }

    // ---------------------------------------------------------------------
    // EntryEditor - the detail sheet's "My Entry" tab
    // ---------------------------------------------------------------------

    /**
     * The viewer's own entry, which the editor tab creates when there is none.
     *
     * [cachedViewer] is the id the editor's `MediaList` query needs. A demo or a
     * scanned profile never reaches here: the screen only builds the tab when the
     * dashboard can actually write, so there is no session to write with and no
     * tab to write from.
     */
    override suspend fun load(mediaId: Int): Result<SimpleMediaListEntry> =
        aniListRepository.getMediaListEntry(mediaId, cachedViewer?.id)

    /**
     * Saves the edited entry and reloads the dashboard.
     *
     * The reload is not optional: the "Currently arriving" section and every count
     * on the screen are derived from the list, and a status moved from Watching to
     * Completed has to leave that section rather than sit in it until the next
     * manual refresh. The repository has already dropped its own list cache, so
     * this is one request, on an explicit button press.
     */
    override suspend fun save(edit: MediaEntryEdit): Result<SimpleMediaListEntry> =
        aniListRepository.saveMediaListEntry(edit).onSuccess { performRefresh() }

    /** Removes the entry and reloads, for the same reason [save] does. */
    override suspend fun delete(entryId: Int): Result<Unit> =
        aniListRepository.deleteMediaListEntry(entryId).onSuccess { performRefresh() }

    /**
     * Factory for the manual construction in AppNavigation.
     *
     * The ViewModels used to be built with `remember { DashboardViewModel(...) }`,
     * which tied them to the composition rather than to a ViewModelStore: rotating
     * the device threw one away and re-fetched the user's whole list, and they
     * were never cleared when a destination left the back stack.
     */
    class Factory(
        private val aniListRepository: AniListRepository,
        private val targetUsername: String? = null,
        private val isDemo: Boolean = false,
        private val hiddenSequelsPreferences: HiddenSequelsPreferences? = null,
        private val refreshIntervalPreferences: RefreshIntervalPreferences? = null,
        private val arrivingPreferences: ArrivingPreferences? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(
                aniListRepository = aniListRepository,
                targetUsername = targetUsername,
                isDemo = isDemo,
                hiddenSequelsPreferences = hiddenSequelsPreferences,
                refreshIntervalPreferences = refreshIntervalPreferences,
                arrivingPreferences = arrivingPreferences
            ) as T
        }
    }
}
