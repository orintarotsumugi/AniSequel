package com.anisequel.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.isManga
import com.anisequel.data.model.namespaceKey
import com.anisequel.data.model.posterUrl
import com.anisequel.domain.list.ListStatuses
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.ProfileAvatarButton
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaProgressPill
import com.anisequel.ui.components.nova.NovaSurface
import com.anisequel.ui.navigation.mainBarClearance
import com.anisequel.ui.screens.nova.NovaEmptyNote
import com.anisequel.ui.screens.nova.NovaSearchRow
import com.anisequel.ui.screens.profile.LibraryFilter
import com.anisequel.ui.screens.profile.LibrarySort
import com.anisequel.ui.screens.profile.LibraryType
import com.anisequel.ui.screens.profile.PendingDataNote
import com.anisequel.ui.screens.profile.RememberedLibraryView
import com.anisequel.ui.screens.profile.filterLibrary
import com.anisequel.ui.screens.profile.libraryCounts
import com.anisequel.ui.screens.profile.libraryProgressLabel
import com.anisequel.ui.screens.profile.selectSort
import com.anisequel.ui.screens.profile.sortLibrary
import com.anisequel.ui.screens.profile.toggleSort
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
    var sortOpen by rememberSaveable { mutableStateOf(false) }

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

    val visible = remember(scoped, type, filter, favouriteIds, query, sort, sortAscending) {
        sortLibrary(
            filterLibrary(scoped, type, filter, favouriteIds, query),
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
            sortOpen = sortOpen,
            type = type,
            animeCount = animeEntries.size,
            mangaCount = mangaEntries.size,
            isLoadingManga = isLoadingManga,
            onSearchOpenChange = { searchOpen = it },
            onSortOpenChange = { sortOpen = it },
            onOpenProfile = onOpenProfile,
            onSelectType = chooseType
        )

        NovaLibraryTools(
            isManga = isManga,
            searchOpen = searchOpen,
            sortOpen = sortOpen,
            query = query,
            onQueryChange = { query = it },
            onOpenSort = { sortOpen = true },
            sort = sort,
            sortAscending = sortAscending,
            onChooseSort = chooseSort,
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
            viewMode = viewMode,
            gridState = gridState,
            onOpenDetail = onOpenDetail,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                columns = viewMode.columns,
                compact = viewMode == LibraryViewMode.COMPACT,
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(28.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun NovaLibraryEmpty(
    awaitingAnime: Boolean,
    showsMangaLoader: Boolean,
    scopedError: String?,
    query: String,
    filter: LibraryFilter,
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
                text = emptyText(query = query, filter = filter, isManga = isManga)
            )
        }
    }
}

private fun emptyText(query: String, filter: LibraryFilter, isManga: Boolean): String =
    when {
        query.isNotBlank() -> "No title in this list matches \"$query\"."
        filter != LibraryFilter.ALL -> "Nothing in ${filter.labelFor(isManga)} yet."
        else -> "Nothing in this list yet."
    }

@Composable
private fun NovaLibraryList(
    visible: List<MediaListEntryItem>,
    isManga: Boolean,
    awaitingAnime: Boolean,
    scopedError: String?,
    onOpenDetail: (MediaNode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = mainBarClearance() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        notesFor(awaitingAnime = awaitingAnime, scopedError = scopedError)
            .forEach { note ->
                item(key = "note_$note") {
                    PendingDataNote(note)
                }
            }
        items(
            items = visible,
            key = { it.media.namespaceKey },
            contentType = { "library_row" }
        ) { entry ->
            NovaLibraryRow(
                entry = entry,
                isManga = isManga,
                onClick = { onOpenDetail(entry.media) },
                modifier = Modifier.animateItem(
                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                )
            )
        }
    }
}

@Composable
private fun NovaLibraryGrid(
    visible: List<MediaListEntryItem>,
    isManga: Boolean,
    columns: Int,
    compact: Boolean,
    awaitingAnime: Boolean,
    scopedError: String?,
    gridState: LazyGridState,
    onOpenDetail: (MediaNode) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .testTag("nova_library_grid"),
        state = gridState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = mainBarClearance() + 20.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        notesFor(awaitingAnime = awaitingAnime, scopedError = scopedError)
            .forEach { note ->
                item(
                    key = "note_$note",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    PendingDataNote(note)
                }
            }
        gridItems(
            items = visible,
            key = { it.media.namespaceKey },
            contentType = { "library_poster" }
        ) { entry ->
            NovaPoster(
                entry = entry,
                isManga = isManga,
                compact = compact,
                onClick = { onOpenDetail(entry.media) },
                modifier = Modifier.animateItem(
                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                )
            )
        }
    }
}

/**
 * The shelf over the page: who this list belongs to, its size, the two controls
 * and the Anime/Manga switch.
 */
@Composable
private fun NovaLibraryHeader(
    isManga: Boolean,
    scopedSize: Int,
    username: String?,
    avatarUrl: String?,
    searchOpen: Boolean,
    sortOpen: Boolean,
    type: LibraryType,
    animeCount: Int,
    mangaCount: Int,
    isLoadingManga: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    onSortOpenChange: (Boolean) -> Unit,
    onOpenProfile: () -> Unit,
    onSelectType: (LibraryType) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    NovaSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        tonalElevation = true
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatarButton(
                    avatarUrl = avatarUrl,
                    enabled = true,
                    onClick = onOpenProfile,
                    testTag = "nova_library_profile_button"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isManga) "Manga List" else "Anime List",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = librarySubtitle(
                            size = scopedSize,
                            username = username
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSearchOpenChange(!searchOpen)
                        onSortOpenChange(false)
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .testTag("nova_library_search_toggle")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Search,
                        contentDescription = "Search this list",
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSortOpenChange(!sortOpen)
                        onSearchOpenChange(false)
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .testTag("nova_library_sort_toggle")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Tune,
                        contentDescription = "Sort this list",
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            NovaTypeSwitch(
                type = type,
                animeCount = animeCount,
                mangaCount = mangaCount,
                isLoadingManga = isLoadingManga,
                onSelect = onSelectType
            )
        }
    }
}

private fun librarySubtitle(size: Int, username: String?): String = buildString {
    append(size)
    append(if (size == 1) " title" else " titles")
    username?.takeIf { it.isNotBlank() }?.let {
        append(" · @")
        append(it)
    }
}

/** The search field, the sort row and the status pills. */
@Composable
private fun NovaLibraryTools(
    isManga: Boolean,
    searchOpen: Boolean,
    sortOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSort: () -> Unit,
    sort: LibrarySort,
    sortAscending: Boolean,
    onChooseSort: (LibrarySort, Boolean) -> Unit,
    filter: LibraryFilter,
    counts: Map<LibraryFilter, Int>,
    onChooseFilter: (LibraryFilter) -> Unit
) {
    AnimatedVisibility(
        visible = searchOpen,
        enter = fadeIn(ExpressiveMotion.FastEffects) +
            expandVertically(ExpressiveMotion.FastSpatialSize),
        exit = fadeOut(ExpressiveMotion.FastEffects) +
            shrinkVertically(ExpressiveMotion.FastSpatialSize)
    ) {
        NovaSearchRow(
            query = query,
            onQueryChange = onQueryChange,
            onOpenFilter = onOpenSort,
            placeholder = if (isManga) "Search your manga" else "Search your anime",
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
        )
    }

    AnimatedVisibility(
        visible = sortOpen,
        enter = fadeIn(ExpressiveMotion.FastEffects) +
            expandVertically(ExpressiveMotion.FastSpatialSize),
        exit = fadeOut(ExpressiveMotion.FastEffects) +
            shrinkVertically(ExpressiveMotion.FastSpatialSize)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibrarySort.entries, key = { it.name }, contentType = { "sort" }) { option ->
                val active = option == sort
                ExpressiveStateChip(
                    label = sortLabel(option, active, sortAscending),
                    selected = active,
                    onClick = {
                        val next = if (active) {
                            toggleSort(sort, sortAscending)
                        } else {
                            selectSort(option)
                        }
                        onChooseSort(next.first, next.second)
                    },
                    minTouchTargetHeight = 36.dp,
                    modifier = Modifier.testTag("nova_library_sort_${option.name.lowercase()}")
                )
            }
        }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(
            items = LibraryFilter.entries,
            key = { it.name },
            contentType = { "filter" }
        ) { option ->
            NovaFilterPill(
                label = option.labelFor(isManga),
                count = counts[option] ?: 0,
                selected = option == filter,
                onClick = { onChooseFilter(option) },
                testTag = "nova_library_filter_${option.name.lowercase()}"
            )
        }
    }
}

private fun sortLabel(
    option: LibrarySort,
    active: Boolean,
    ascending: Boolean
): String = when {
    !option.isDirectional || !active -> option.label
    ascending -> "${option.label} ↑"
    else -> "${option.label} ↓"
}

/**
 * The waiting and failure notes a populated list still carries above its rows.
 *
 * Returned as strings because the grid and the list place them differently - one
 * full-span grid item, one list item - and the wording is the part that must not
 * diverge.
 */
private fun notesFor(awaitingAnime: Boolean, scopedError: String?): List<String> =
    buildList {
        if (awaitingAnime) {
            add("Still fetching this list - these counts are not final yet")
        }
        if (scopedError != null && !awaitingAnime) {
            add("Couldn't load this list - $scopedError")
        }
    }

/** Anime/Manga, as the two-way switch the new look uses instead of an "All" bucket. */
@Composable
private fun NovaTypeSwitch(
    type: LibraryType,
    animeCount: Int,
    mangaCount: Int,
    isLoadingManga: Boolean,
    onSelect: (LibraryType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp)
    ) {
        listOf(
            LibraryType.ANIME to animeCount,
            LibraryType.MANGA to mangaCount
        ).forEach { (option, count) ->
            val selected = option == type
            val container by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                animationSpec = ExpressiveMotion.FastColorEffects,
                label = "nova_type_container"
            )
            val content = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(ExpressiveShapes.pill)
                    .background(container)
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp)
                    .testTag("nova_library_type_${option.name.lowercase()}"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (option == LibraryType.MANGA) {
                        AppVectorIcons.SourceBook
                    } else {
                        AppVectorIcons.Tv
                    },
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = when {
                        option == LibraryType.MANGA && isLoadingManga && !selected -> "Manga..."
                        option == LibraryType.MANGA -> "Manga"
                        else -> "Anime"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = content,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1
                )
            }
        }
    }
}

/** One status bucket, with the count over the *whole* type scope rather than the narrowed set. */
@Composable
private fun NovaFilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nova_filter_container"
    )
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(container)
            .bouncyPress(pressedScale = 0.94f)
            .clickable(onClick = onClick)
            .padding(start = 13.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(content.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1
            )
        }
    }
}

/**
 * One cover in the new grid.
 *
 * The progress capsule is on the cover, top-left, exactly as the reference draws
 * it and as the classic Library already does - the corner AniList leaves free.
 * The title is under the artwork, not over it: a second caption on a cover that
 * already carries its own typography is the failure `ProfileLibraryTab` documents.
 */
@Composable
private fun NovaPoster(
    entry: MediaListEntryItem,
    isManga: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = entry.media
    val interactionSource = remember { MutableInteractionSource() }
    val total = if (isManga) media.chapters else media.episodes
    val completed = entry.status.equals("COMPLETED", ignoreCase = true)
    val progress = entry.progress ?: if (completed) total else null

    Column(
        modifier = modifier
            .bouncyPress(pressedScale = 0.95f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("nova_library_poster")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // A ratio rather than a fixed height: the grid is three columns
                // or four, so the same card is measured at two widths - and a
                // height literal would turn the wider one into a portrait and the
                // narrower into a strip.
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
                .background(media.coverImage?.color.toCoverColorOrNull() ?: Color.Transparent)
        ) {
            AsyncImage(
                model = media.posterUrl,
                contentDescription = media.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (progress != null) {
                NovaProgressPill(
                    progress = progress,
                    total = total,
                    isManga = isManga,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = media.displayName,
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The same entry as a row, for the List density.
 *
 * This is the mode that makes a long library readable rather than browsable:
 * progress, status and score are all in one line, and the cover is a thumbnail
 * rather than the page.
 */
@Composable
private fun NovaLibraryRow(
    entry: MediaListEntryItem,
    isManga: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = entry.media
    val interactionSource = remember { MutableInteractionSource() }
    val score = media.averageScore?.takeIf { it > 0 }
    val progressLabel = libraryProgressLabel(entry)

    NovaSurface(
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("nova_library_row"),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(media.coverImage?.color.toCoverColorOrNull() ?: Color.Transparent)
            ) {
                AsyncImage(
                    model = media.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = ExpressiveShapes.pill,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = ListStatuses.label(entry.status, isManga),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                    if (progressLabel != null) {
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            if (score != null) {
                Text(
                    text = "★ $score",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
