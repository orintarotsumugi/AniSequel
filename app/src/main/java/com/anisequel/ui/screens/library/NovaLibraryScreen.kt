package com.anisequel.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
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
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
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
            type = if (stored == LibraryType.MANGA) LibraryType.MANGA else LibraryType.ANIME
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

    LaunchedEffect(type) {
        if (type == LibraryType.MANGA) onLoadManga()
    }

    val chooseType: (LibraryType) -> Unit = { chosen ->
        if (chosen != type) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            choiceMade = true
            type = chosen
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
            viewMode == LibraryViewMode.LIST -> NovaLibraryList(
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
    // The shape of the grid that is coming, not a spinner in the middle of an
    // empty page. The spinner said "something is happening"; the poster outlines
    // say what and where, so the covers land into a page that already has its
    // layout instead of replacing a blank one. One shimmer clock for every
    // skeleton on screen - see `rememberShimmerPhase`.
    val phase = rememberShimmerPhase()
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
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.68f),
                            shape = RoundedCornerShape(16.dp),
                            phase = phase
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(10.dp),
                            phase = phase
                        )
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
        if (awaitingAnime) {
            PendingDataNote("Still fetching this list - these counts are not final yet")
        }
        if (scopedError != null && !awaitingAnime) {
            PendingDataNote(
                "Couldn't load this list - $scopedError. " +
                    "Your bio, favourites and stats are still here."
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

