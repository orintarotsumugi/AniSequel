package com.anisequel.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.domain.library.LibraryAdvancedFilters
import com.anisequel.domain.library.filterLibraryAdvanced
import com.anisequel.domain.library.libraryReleaseYears
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.rememberFullHeightSheetState
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.screens.nova.NovaEmptyNote
import com.anisequel.ui.screens.profile.LibraryFilter
import com.anisequel.ui.screens.profile.LibrarySort
import com.anisequel.ui.screens.profile.LibraryType
import com.anisequel.ui.screens.profile.PendingDataNote
import com.anisequel.ui.screens.profile.RememberedLibraryView
import com.anisequel.ui.screens.profile.filterLibrary
import com.anisequel.ui.screens.profile.libraryCounts
import com.anisequel.ui.screens.profile.sortLibrary
import com.anisequel.ui.theme.LocalLibraryViewMode
import kotlinx.coroutines.launch

/**
 * The new look's Library.
 *
 * ## What the shape does differently
 *
 * The classic Library is a single scrolling grid whose first rows are its
 * controls. That is a good browsing surface and a poor *answering* surface: the
 * type, the status and the sort all live inside the thing that scrolls away.
 * Here the header stays put - which list this is, how many are in it, and the
 * status row with its counts - and only the posters move underneath. It is the
 * reference layout's split, and it is the reason the type can be a two-way
 * choice rather than a third "All" bucket.
 *
 * The filters, counts and ordering are the *same pure functions* the classic
 * page uses (`filterLibrary`, `libraryCounts`, `sortLibrary`), so a title can
 * never be filed differently depending on which look is on. Only the drawing is
 * new.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaLibraryScreen(
    animeEntries: List<MediaListEntryItem>,
    mangaEntries: List<MediaListEntryItem>,
    favouriteIds: Set<String>,
    isLoadingManga: Boolean,
    isAnimeLoading: Boolean,
    libraryError: String?,
    mangaError: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadManga: () -> Unit,
    onOpenDetail: (MediaNode) -> Unit,
    onOpenProfile: () -> Unit,
    remembered: RememberedLibraryView,
    gridState: LazyGridState,
    username: String? = null,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val viewMode = LocalLibraryViewMode.current
    val haptic = LocalHapticFeedback.current

    var choiceMade by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable {
        mutableStateOf(
            if (remembered.type == LibraryType.MANGA) LibraryType.MANGA else LibraryType.ANIME
        )
    }
    var filter by rememberSaveable { mutableStateOf(remembered.filter ?: LibraryFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(remembered.sort ?: LibrarySort.UPDATED_AT) }
    var sortAscending by rememberSaveable { mutableStateOf(remembered.sortAscending ?: false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var advancedFilters by remember { mutableStateOf(LibraryAdvancedFilters()) }
    val filterSheetState = rememberFullHeightSheetState()

    // A remembered choice arriving after the page is already up. Once this page
    // has written a choice, later emissions are echoes of its own writes and
    // applying them would let a stale value overwrite a newer local one.
    LaunchedEffect(remembered.type, remembered.filter, remembered.sort, remembered.sortAscending) {
        if (choiceMade) return@LaunchedEffect
        remembered.type?.let { stored ->
            val next = if (stored == LibraryType.MANGA) LibraryType.MANGA else LibraryType.ANIME
            // A type can arrive from outside a tap - the dashboard's "Manga List"
            // shortcut writes the preference before it navigates. That is still a
            // switch between the halves, so it still starts at the top; the saved
            // position belongs to the list that was on screen.
            if (next != type) gridState.requestScrollToItem(0)
            type = next
        }
        remembered.filter?.let { filter = it }
        remembered.sort?.let { stored ->
            sort = stored
            sortAscending = remembered.sortAscending ?: false
        }
    }

    val isManga = type == LibraryType.MANGA
    val scoped = if (isManga) mangaEntries else animeEntries
    val counts = remember(scoped, favouriteIds) { libraryCounts(scoped, favouriteIds) }
    val scopedError = if (isManga) mangaError else libraryError
    val awaitingAnime = isAnimeLoading && !isManga
    val availableYears = remember(scoped) { libraryReleaseYears(scoped) }
    val availableGenres = remember(scoped) {
        scoped.flatMap { entry -> entry.media.genres.orEmpty() }
            .distinct()
            .sorted()
    }

    val visible = remember(
        scoped,
        type,
        filter,
        favouriteIds,
        query,
        advancedFilters,
        sort,
        sortAscending
    ) {
        sortLibrary(
            filterLibraryAdvanced(
                filterLibrary(scoped, type, filter, favouriteIds, query),
                advancedFilters,
                favouriteIds
            ),
            sort,
            sortAscending
        )
    }

    LoadScopedList(type = type, onLoadManga = onLoadManga)

    val chooseType: (LibraryType) -> Unit = { chosen ->
        if (chosen != type) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            choiceMade = true
            type = chosen
            // The new list starts at the top, and it is told so *here* rather
            // than from an effect watching `type`. An effect runs after the frame
            // that swapped the item set, so the grid laid the new list out at the
            // old list's scroll index first and was then jumped - with a spring on
            // every cell, so the page slid into place from positions it never
            // belonged at. `requestScrollToItem` is applied by the next measure
            // pass, which is the same one that lays the new list out, so that
            // frame does not exist, and it cannot be cancelled the way a
            // `scrollToItem` coroutine could be when the reader switches again.
            gridState.requestScrollToItem(0)
            query = ""
            // A format/year filter chosen for anime would empty the manga half,
            // and the switch would look broken rather than separated. The two
            // lists are separate searches, so their filter drafts are too.
            advancedFilters = LibraryAdvancedFilters()
            filterOpen = false
            scope.launch { remembered.preferences.setType(chosen.name) }
        }
    }

    val chooseFilter: (LibraryFilter) -> Unit = { chosen ->
        if (chosen != filter) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            choiceMade = true
            filter = chosen
            scope.launch { remembered.preferences.setFilter(chosen.name) }
        }
    }

    val chooseSort: (LibrarySort, Boolean) -> Unit = { nextSort, nextAscending ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        choiceMade = true
        sort = nextSort
        sortAscending = nextAscending
        scope.launch { remembered.preferences.setSort(nextSort.name, nextAscending) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("nova_library_screen")
    ) {
        NovaLibraryHeader(
            isManga = isManga,
            scopedSize = scoped.size,
            username = username,
            avatarUrl = avatarUrl,
            searchOpen = searchOpen,
            type = type,
            animeCount = animeEntries.size,
            mangaCount = mangaEntries.size,
            isLoadingManga = isLoadingManga,
            isLoadingAnime = isAnimeLoading,
            activeFilterCount = advancedFilters.activeCount,
            onSearchOpenChange = { searchOpen = it },
            onOpenFilters = { filterOpen = true },
            onOpenProfile = onOpenProfile,
            onSelectType = chooseType
        )

        NovaLibraryTools(
            isManga = isManga,
            searchOpen = searchOpen,
            query = query,
            onQueryChange = { query = it },
            onOpenFilters = { filterOpen = true },
            sort = sort,
            sortAscending = sortAscending,
            onToggleDirection = { chooseSort(sort, !sortAscending) },
            filter = filter,
            counts = counts,
            countsKnown = !scopedLibraryLoading(
                isManga = isManga,
                isLoadingManga = isLoadingManga,
                isLoadingAnime = isAnimeLoading
            ),
            onChooseFilter = chooseFilter
        )

        NovaLibraryBody(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            rememberedAnswered = remembered.answered,
            awaitingAnime = awaitingAnime,
            visible = visible,
            isManga = isManga,
            isLoadingManga = isLoadingManga,
            animeEntriesEmpty = animeEntries.isEmpty(),
            scopedError = scopedError,
            query = query,
            filter = filter,
            advancedFiltersActive = advancedFilters.isActive,
            viewMode = viewMode,
            gridState = gridState,
            onOpenDetail = onOpenDetail,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }

    if (filterOpen) {
        LibraryFilterSheet(
            sheetState = filterSheetState,
            filters = advancedFilters,
            sort = sort,
            ascending = sortAscending,
            years = availableYears,
            genres = availableGenres,
            onApply = { nextFilters, nextSort, nextAscending ->
                advancedFilters = nextFilters
                chooseSort(nextSort, nextAscending)
                filterOpen = false
            },
            onDismiss = { filterOpen = false }
        )
    }
}

/**
 * Asks for the other list on a type switch.
 *
 * The fetch only. This used to reset the scroll as well, from a `LaunchedEffect`
 * watching `type` - which runs on the frame *after* the one that replaced the
 * item set, so the grid laid the new list out at the old list's scroll index
 * before being jumped. See [NovaLibraryScreen]'s `chooseType` for where the
 * reset lives now and what the old shape looked like.
 */
@Composable
private fun LoadScopedList(
    type: LibraryType,
    onLoadManga: () -> Unit
) {
    LaunchedEffect(type) {
        if (type == LibraryType.MANGA) onLoadManga()
    }
}

/**
 * The scrolling half of the page: the resolving spinner, the empty state, and
 * the list or grid of posters.
 *
 * Extracted from [NovaLibraryScreen] so the page's own body is state and layout,
 * while the branches that pick a surface live here with the surfaces themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaLibraryBody(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    rememberedAnswered: Boolean,
    awaitingAnime: Boolean,
    visible: List<MediaListEntryItem>,
    isManga: Boolean,
    isLoadingManga: Boolean,
    animeEntriesEmpty: Boolean,
    scopedError: String?,
    query: String,
    filter: LibraryFilter,
    advancedFiltersActive: Boolean,
    viewMode: LibraryViewMode,
    gridState: LazyGridState,
    onOpenDetail: (MediaNode) -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        when {
            !rememberedAnswered -> NovaLibraryResolving()
            visible.isEmpty() -> NovaLibraryEmpty(
                awaitingAnime = awaitingAnime,
                showsMangaLoader = (isManga && isLoadingManga) ||
                    (awaitingAnime && animeEntriesEmpty),
                scopedError = scopedError,
                query = query,
                filter = filter,
                advancedFiltersActive = advancedFiltersActive,
                isManga = isManga
            )
            viewMode == LibraryViewMode.TABLE -> NovaLibraryTable(
                visible = visible,
                awaitingAnime = awaitingAnime,
                scopedError = scopedError,
                onOpenDetail = onOpenDetail
            )
            viewMode == LibraryViewMode.LIST -> NovaLibraryList(
                visible = visible,
                isManga = isManga,
                awaitingAnime = awaitingAnime,
                scopedError = scopedError,
                onOpenDetail = onOpenDetail
            )
            viewMode == LibraryViewMode.GROUPED -> NovaLibraryGrouped(
                visible = visible,
                isManga = isManga,
                awaitingAnime = awaitingAnime,
                scopedError = scopedError,
                onOpenDetail = onOpenDetail
            )
            else -> NovaLibraryGrid(
                visible = visible,
                isManga = isManga,
                viewMode = viewMode,
                awaitingAnime = awaitingAnime,
                scopedError = scopedError,
                gridState = gridState,
                onOpenDetail = onOpenDetail
            )
        }
    }
}

@Composable
private fun NovaLibraryResolving() {
    // The shape of the library that is coming, matched to the selected view mode so
    // covers/rows replace placeholders in place instead of jumping from a generic grid.
    val phase = rememberShimmerPhase()
    val viewMode = LocalLibraryViewMode.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(percent = 50),
                    phase = phase
                )
            }
        }
        when (viewMode) {
            LibraryViewMode.LIST,
            LibraryViewMode.TABLE,
            LibraryViewMode.GROUPED -> {
                repeat(if (viewMode == LibraryViewMode.TABLE) 6 else 7) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShimmerBox(
                            modifier = Modifier.size(width = 40.dp, height = 56.dp),
                            shape = RoundedCornerShape(10.dp),
                            phase = phase
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth(if (index % 2 == 0) 0.72f else 0.56f)
                                    .height(12.dp),
                                phase = phase
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth(0.38f)
                                    .height(10.dp),
                                phase = phase
                            )
                        }
                        if (viewMode == LibraryViewMode.TABLE) {
                            ShimmerBox(
                                modifier = Modifier.size(width = 34.dp, height = 10.dp),
                                phase = phase
                            )
                        }
                    }
                }
            }
            else -> {
                // The *narrowed* count the real grid will use, not the mode's
                // nominal one. On a 360dp phone a five-across shelf is drawn three
                // across, so the skeleton used to promise five covers a row for the
                // length of the fetch and then reflow into three - the exact jump
                // the mode-shaped skeleton exists to prevent.
                val width = LocalConfiguration.current.screenWidthDp.dp - 32.dp
                val columns = libraryColumns(viewMode, width)
                val showCaptions = viewMode != LibraryViewMode.COVERS &&
                    viewMode != LibraryViewMode.SHELF
                if (viewMode == LibraryViewMode.MOSAIC) {
                    // Mosaic opens with its wide anchor cell, so the placeholder does
                    // too; without it the first real frame collapsed the row above it.
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(MOSAIC_HERO_ASPECT),
                        shape = RoundedCornerShape(16.dp),
                        phase = phase
                    )
                }
                val rows = if (columns >= 4) 3 else 2
                repeat(rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(columns.coerceAtMost(5)) { index ->
                            Column(modifier = Modifier.weight(1f)) {
                                ShimmerBox(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(if (viewMode == LibraryViewMode.CARDS) 0.66f else 0.68f),
                                    shape = RoundedCornerShape(16.dp),
                                    phase = phase
                                )
                                if (showCaptions) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    ShimmerBox(
                                        modifier = Modifier
                                            .fillMaxWidth(if (index % 3 == 0) 0.82f else 0.62f)
                                            .height(10.dp),
                                        phase = phase
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovaLibraryEmpty(
    awaitingAnime: Boolean,
    showsMangaLoader: Boolean,
    scopedError: String?,
    query: String,
    filter: LibraryFilter,
    advancedFiltersActive: Boolean,
    isManga: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // The skeleton below already says the list is coming; a line above it saying
        // the same thing made an empty Library open with two loading states stacked
        // on each other. The note stays for the case it exists for - a list that has
        // rows already and is still being completed.
        if (awaitingAnime && !showsMangaLoader) {
            PendingDataNote("Still fetching this list - these counts are not final yet")
        }
        if (scopedError != null && !awaitingAnime) {
            // `busy = false`: a failure is an answer, not a wait. With the default
            // this note spun a loader beside "Couldn't load this list", promising an
            // answer that had already arrived - the rule PendingDataNote documents
            // and the profile's stats tab already follows.
            PendingDataNote(
                "Couldn't load this list - $scopedError. " +
                    "Your bio, favourites and stats are still here.",
                busy = false
            )
        }
        when {
            showsMangaLoader -> NovaLibraryResolving()
            scopedError == null -> NovaEmptyNote(
                text = emptyText(
                    query = query,
                    filter = filter,
                    advancedFiltersActive = advancedFiltersActive,
                    isManga = isManga
                )
            )
        }
    }
}

private fun emptyText(
    query: String,
    filter: LibraryFilter,
    advancedFiltersActive: Boolean,
    isManga: Boolean
): String = when {
    query.isNotBlank() -> "No title in this list matches \"$query\"."
    advancedFiltersActive -> "No title in this list matches these filters."
    filter != LibraryFilter.ALL -> "Nothing in ${filter.labelFor(isManga)} yet."
    else -> "Nothing in this list yet."
}

