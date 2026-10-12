package com.example.ui.screens.profile

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.MediaListEntryItem
import com.example.data.model.MediaNode
import com.example.data.model.isManga
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveEmptyOrb
import com.example.ui.components.expressive.ExpressiveLoadingIndicator
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.components.expressive.ExpressiveStateChip
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.theme.AniSequelTheme
import java.util.Locale

/** Which media type the Library is showing. */
enum class LibraryType { ALL, ANIME, MANGA }

/**
 * How the Library is ordered.
 *
 * A grid needs an order the reader chose, because "the order AniList returned"
 * is an implementation detail of a paginated API rather than an answer to
 * anything. It matters most once a status filter is on: 131 completed entries in
 * no meaningful order is a wall, and which one you are looking for depends on
 * whether you are after the newest or the highest rated.
 *
 * [TITLE] is always ascending - "Z-A" would be a sort nobody asks for, and the
 * control that offered it would be busywork.
 */
enum class LibrarySort(val label: String) {
    RECENT("Recent"),
    TITLE("A-Z"),
    SCORE("Score")
}

/**
 * Applies an ordering, and undoes it when asked for the same order twice.
 *
 * Tapping the active sort toggles its direction, which is the convention every
 * list header shares and the reason the reader does not have to hunt for a
 * separate ascending control. Returns the toggled order.
 */
internal fun toggleSort(current: LibrarySort, currentAscending: Boolean): Pair<LibrarySort, Boolean> =
    when (current) {
        LibrarySort.TITLE -> current to !currentAscending
        // Cycling score -> recent rather than score-descending -> score-ascending:
        // a score list that starts highest-first is useless, because the entries
        // you are actually hunting for are at the bottom, out of sight.
        LibrarySort.SCORE ->
            if (currentAscending) {
                // Entering the score list *descending*: highest first. Keeping the
                // previous direction put the lowest-scored entry on top, so the
                // first thing the list showed was the show they liked least -
                // and the comment here used to claim the opposite of what the
                // code did, which is how it survived.
                LibrarySort.SCORE to false
            } else {
                LibrarySort.RECENT to false
            }
        LibrarySort.RECENT -> current to !currentAscending
    }

/**
 * Orders entries for display.
 *
 * Pure and separate from the chips so the rule can be pinned: the interesting
 * failure is a score sort that puts unrated entries *above* rated ones, because
 * a null coerced to zero is a perfect score's neighbour and a list of zeroes
 * looks like a list of favourites.
 */
internal fun sortLibrary(
    entries: List<MediaListEntryItem>,
    sort: LibrarySort,
    ascending: Boolean
): List<MediaListEntryItem> {
    val comparator: Comparator<MediaListEntryItem> = when (sort) {
        LibrarySort.TITLE -> compareBy { it.media.title?.displayTitle?.lowercase(Locale.ROOT) ?: "" }
        // `null` sorts last in *both* directions. An unrated title has no score to
        // rank, so it belongs at the bottom either way rather than pretending to
        // be zero - which in a descending list would put it at the very top.
        LibrarySort.SCORE -> compareBy(
            { it.media.averageScore == null },
            { it.media.averageScore ?: 0 }
        )
        // No timestamp is carried on the list query, so "Recent" is the order
        // AniList returned, which for a status group is already newest-first.
        LibrarySort.RECENT -> return entries
    }
    return entries.sortedWith(if (ascending) comparator else comparator.reversed())
}

/**
 * The list buckets the Library can be narrowed to.
 *
 * Dropped and Rewatching are here even though they are less prominent than the
 * rest. AniList records both, an account routinely has entries in both, and a
 * filter row that quietly omitted them would make those entries reachable only
 * under "All" - a list you cannot narrow is a list you cannot finish working
 * through.
 */
enum class LibraryFilter(
    /**
     * The label as written for a *reading* list, which is the wording people
     * recognise for both.
     *
     * A manga row labelled "Watching" is the same class of mistake as the one
     * `displayStatusFor` documents on the activity feed: the status is a single
     * enum on AniList and both media types share it, but nobody says they are
     * watching a book. [animeLabel] is the alternative, and
     * [labelFor] picks between them per entry.
     */
    val label: String,
    val animeLabel: String = label
) {
    ALL("All"),
    WATCHING("Reading", "Watching"),
    COMPLETED("Completed"),
    PAUSED("Paused"),
    PLANNING("Planning"),
    DROPPED("Dropped"),
    REWATCHING("Rereading", "Rewatching"),
    FAVOURITES("Favourites");

    /** The chip label for a row whose media type is known. */
    fun labelFor(isManga: Boolean): String = if (isManga) label else animeLabel
}

/**
 * Which bucket an entry belongs to, or null when there is none to file it under.
 *
 * AniList's `status` is an enum name on the wire and is compared
 * case-insensitively, because a lower-case value has been seen in a fixture and a
 * filter that misses it renders an empty list for somebody who demonstrably has
 * entries.
 */
internal fun libraryBucketOf(status: String?): LibraryFilter? =
    when (status?.lowercase(Locale.ROOT)) {
        "current" -> LibraryFilter.WATCHING
        "completed" -> LibraryFilter.COMPLETED
        "paused" -> LibraryFilter.PAUSED
        "planning" -> LibraryFilter.PLANNING
        "dropped" -> LibraryFilter.DROPPED
        "repeating" -> LibraryFilter.REWATCHING
        else -> null
    }

/**
 * How many entries sit in each bucket, within one media type.
 *
 * Computed over the *unfiltered* set on purpose: a count that shrank as you
 * narrowed would make "Completed (0)" appear for a bucket you had merely excluded,
 * which reads as "you have completed nothing" rather than "you are not looking at
 * completed right now".
 */
internal fun libraryCounts(
    entries: List<MediaListEntryItem>,
    favouriteIds: Set<Int>
): Map<LibraryFilter, Int> {
    val counts = mutableMapOf<LibraryFilter, Int>()
    counts[LibraryFilter.ALL] = entries.size
    entries.forEach { entry ->
        libraryBucketOf(entry.status)?.let { bucket ->
            counts[bucket] = (counts[bucket] ?: 0) + 1
        }
    }
    if (entries.any { it.media.id in favouriteIds }) {
        counts[LibraryFilter.FAVOURITES] = entries.count { it.media.id in favouriteIds }
    }
    return counts
}

/**
 * Narrows the library to one media type, one bucket and a search string.
 *
 * Free functions rather than composable state so the rule can be pinned by a unit
 * test. The failure this replaces was not that the filters looked wrong, it was
 * that "Manga" still showed anime because the filter compared the entry's status
 * but never the media's own `type`, and the check only ever ran on the first
 * screenful that happened to be composed.
 *
 * Favourites is a *membership* test rather than a bucket: AniList models a pinned
 * favourite as its own connection, not as a list status, so there is no status
 * string to match. Testing the media id against the ids the profile already holds
 * is also what stops a favourite the person has removed from their list still
 * showing under "Favourites".
 */
internal fun filterLibrary(
    entries: List<MediaListEntryItem>,
    type: LibraryType,
    filter: LibraryFilter,
    favouriteIds: Set<Int> = emptySet(),
    query: String = ""
): List<MediaListEntryItem> {
    val trimmed = query.trim()
    return entries.filter { entry ->
        val typeMatches = when (type) {
            LibraryType.ALL -> true
            LibraryType.ANIME -> !entry.media.isManga
            LibraryType.MANGA -> entry.media.isManga
        }
        val filterMatches = when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.FAVOURITES -> entry.media.id in favouriteIds
            else -> libraryBucketOf(entry.status) == filter
        }
        val queryMatches = trimmed.isEmpty() ||
            entry.media.title?.displayTitle?.contains(trimmed, ignoreCase = true) == true
        typeMatches && filterMatches && queryMatches
    }
}

/**
 * How far through it a list entry is, in the words that fit the type.
 *
 * "Ep 12/24" for anime and "Ch 142" for manga, because a manga badge reading
 * "Ep 142" is the one inconsistency a person who reads both notices immediately.
 * Returns null when AniList recorded no progress, so a poster is never given a
 * progress badge it does not have.
 */
internal fun libraryProgressLabel(entry: MediaListEntryItem): String? {
    val isManga = entry.media.isManga
    val unit = if (isManga) "Ch" else "Ep"
    val total = if (isManga) entry.media.chapters else entry.media.episodes
    val progress = entry.progress

    if (progress == null) {
        // A completed entry with no progress is the common case, not an edge
        // case: AniList does not fill `progress` from the episode count when a
        // title is marked finished, so `status` becomes "completed" while
        // `progress` stays null. The badge therefore vanished at exactly the
        // moment the person had finished watching something - the one time they
        // most want to see the number.
        //
        // Falling back to the total shows what they actually know ("24/24"),
        // and is only claimed when the entry really is complete.
        val finished = entry.status.equals("completed", ignoreCase = true)
        return if (finished && total != null && total > 0) "$unit $total/$total" else null
    }

    return if (total != null && total > 0) "$unit $progress/$total" else "$unit $progress"
}

/**
 * The Library tab: every anime and manga on this person's AniList, as posters.
 *
 * ## Posters, not rows
 *
 * A browsable library is a *recognising* task - the person is looking for a title
 * they half-remember, the way the followers grid is a face-recognising task. A
 * row spends a third of the screen on two lines of text to show what a cover
 * already says, and at 271 entries that is the difference between seeing a shelf
 * and reading a list.
 *
 * ## Where the anime comes from
 *
 * Nowhere near the network. The Stats tab already forces the whole anime list to
 * be fetched and chunk-merged, because AniList publishes no aggregate for country
 * of origin, release year, watch year or episode bands - and this screen was
 * discarding all of it the moment it had counted what it needed. The list is now
 * carried in the state, so a tab that browses hundreds of entries costs zero extra
 * requests, which is the whole game while AniList is serving 30 a minute.
 *
 * ## Why manga is fetched but not waited for
 *
 * Manga is the one half nothing else on the screen needed. It is requested the
 * first time somebody filters to it, behind a spinner on that chip, and never
 * again - the repository caches it and the ViewModel refuses a second call.
 *
 * ## Why the title sits *under* the poster
 *
 * Overlaying it on the cover is the obvious design and this app already tried it.
 * [FavouriteMediaRow]'s comment records why it was undone: AniList covers carry
 * the series' own typography, frequently vertical and running to the edges, and a
 * caption on top of that is unreadable - and a gradient under twelve covers at
 * once is a stripe across the screen rather than a shadow. The progress badge
 * survives on the cover because it sits in the top-left corner, which is empty
 * padding on almost every cover; a two-line title does not.
 */
@Composable
fun ProfileLibraryTab(
    animeEntries: List<MediaListEntryItem>,
    mangaEntries: List<MediaListEntryItem>?,
    favouriteIds: Set<Int>,
    isLoadingManga: Boolean,
    onLoadManga: () -> Unit,
    contentPadding: PaddingValues,
    onOpenDetail: (MediaNode) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState()
) {
    var type by rememberSaveable { mutableStateOf(LibraryType.ALL) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENT) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }

    // The type scope decides what the counts describe, so it is applied first and
    // the counts are tallied over that - never over the already-narrowed set.
    val scoped = remember(animeEntries, mangaEntries, type) {
        when (type) {
            LibraryType.ANIME -> animeEntries
            LibraryType.MANGA -> mangaEntries.orEmpty()
            LibraryType.ALL -> animeEntries + mangaEntries.orEmpty()
        }
    }
    val counts = remember(scoped, favouriteIds) { libraryCounts(scoped, favouriteIds) }
    val visible = remember(scoped, type, filter, favouriteIds, query, sort, sortAscending) {
        sortLibrary(
            filterLibrary(scoped, type, filter, favouriteIds, query),
            sort,
            sortAscending
        )
    }

    LaunchedEffect(type) {
        // Only the manga filter needs the network; the other two are already in
        // hand, so asking for manga on the way in would spend a request to build a
        // view the user did not select.
        if (type == LibraryType.MANGA) onLoadManga()
    }

    LazyVerticalGrid(
        // `Fixed` rather than `Adaptive`, for the reason the followers grid is:
        // the grid must not reflow when the window changes, because the person's
        // scroll position would no longer be the entry they were looking at.
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_library_tab"),
        state = gridState,
        contentPadding = profileListPadding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "library_controls") {
            LibraryControls(
                type = type,
                filter = filter,
                counts = counts,
                scopeSize = scoped.size,
                scopeIsManga = type == LibraryType.MANGA,
                sort = sort,
                sortAscending = sortAscending,
                onSortChange = { nextSort, nextAscending ->
                    sort = nextSort
                    sortAscending = nextAscending
                },
                isLoadingManga = isLoadingManga,
                query = query,
                onType = {
                    type = it
                    query = ""
                },
                onFilter = { filter = it },
                onQueryChange = { query = it }
            )
        }

        if (visible.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "library_empty") {
                LibraryEmpty(
                    isLoading = type == LibraryType.MANGA && isLoadingManga,
                    query = query
                )
            }
        } else {
            items(
                items = visible,
                key = { it.media.id },
                contentType = { "library" }
            ) { entry ->
                LibraryPoster(
                    entry = entry,
                    isFavourite = entry.media.id in favouriteIds,
                    modifier = Modifier
                        .widthIn(max = ProfileMaxContentWidth)
                        .fillMaxWidth(),
                    onClick = { onOpenDetail(entry.media) }
                )
            }
        }
    }
}

@Composable
private fun LibraryControls(
    type: LibraryType,
    filter: LibraryFilter,
    counts: Map<LibraryFilter, Int>,
    /** Size of the current type scope, so the search row can decide for itself. */
    scopeSize: Int,
    /**
     * Whether the current scope is manga, so the filter chips can say "Reading"
     * rather than "Watching". Under "All" the chips are worded for anime, because
     * that is the majority of any list and the alternative would mislabel every
     * manga row above it.
     */
    scopeIsManga: Boolean,
    sort: LibrarySort,
    sortAscending: Boolean,
    onSortChange: (LibrarySort, Boolean) -> Unit,
    isLoadingManga: Boolean,
    query: String,
    onType: (LibraryType) -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    onQueryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryType.entries.forEach { option ->
                val selected = option == type
                ExpressiveStateChip(
                    label = when {
                        option != LibraryType.MANGA -> option.name.lowercase()
                            .replaceFirstChar { it.uppercase() }
                        isLoadingManga && selected -> "Loading..."
                        else -> "Manga"
                    },
                    selected = selected,
                    onClick = { onType(option) },
                    minTouchTargetHeight = 40.dp,
                    modifier = Modifier.testTag("library_type_${option.name.lowercase()}")
                )
            }
        }

        // Search only earns its row on a list long enough to need one. It is the
        // fastest route to a half-remembered title, and useless on nine entries.
        if (scopeSize > SEARCH_THRESHOLD) {
            LibrarySearchField(query = query, onQueryChange = onQueryChange)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LibraryFilter.entries, key = { it.name }) { option ->
                val count = counts[option] ?: 0
                ExpressiveStateChip(
                    label = "${option.labelFor(scopeIsManga)} ($count)",
                    selected = option == filter,
                    onClick = { onFilter(option) },
                    minTouchTargetHeight = 44.dp,
                    modifier = Modifier.testTag("library_filter_${option.name.lowercase()}")
                )
            }
        }

        // Sort sits on its own row rather than in the filter row, because it
        // answers a different question: the filters say *which* entries, this
        // says *in what order*. Under 12 entries the order is obvious without
        // being asked for.
        if (scopeSize > SEARCH_THRESHOLD) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sort",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LibrarySort.entries.forEach { option ->
                    val active = option == sort
                    ExpressiveStateChip(
                        // The direction is on the chip that is already active,
                        // so it cannot contradict itself: the chip you did not
                        // touch has no direction to show.
                        label = when {
                            active && option != LibrarySort.RECENT && sortAscending -> "${option.label} ↑"
                            active && option != LibrarySort.RECENT -> "${option.label} ↓"
                            else -> option.label
                        },
                        selected = active,
                        onClick = {
                            val next = toggleSort(sort, sortAscending)
                            onSortChange(next.first, next.second)
                        },
                        minTouchTargetHeight = 40.dp,
                        modifier = Modifier.testTag("library_sort_${option.name.lowercase()}")
                    )
                }
            }
        }
    }
}

/**
 * Above this many entries the search field appears.
 *
 * Twelve is not arbitrary in the sense that matters: below it, scrolling reaches
 * the end faster than typing does, and a field that takes two thirds of a screen
 * to find something in the first row is in the way.
 */
private const val SEARCH_THRESHOLD = 12

@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_search_input"),
        placeholder = {
            Text(
                text = "Search this list...",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = AppVectorIcons.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                androidx.compose.material3.IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = ExpressiveShapes.pill,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

/**
 * One poster, with the progress badge and the title beneath it.
 *
 * The badge is the only thing drawn on top of the cover. It sits top-start in a
 * translucent container so it stays readable over a pale cover without needing
 * the scrim a title would, and it is skipped entirely for an entry with no
 * progress rather than being drawn with a placeholder number.
 */
@Composable
private fun LibraryPoster(
    entry: MediaListEntryItem,
    isFavourite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = entry.media
    val isManga = media.isManga
    val interactionSource = remember { MutableInteractionSource() }
    val placeholder = if (isManga) {
        AppVectorIcons.FavouriteManga
    } else {
        AppVectorIcons.FavouriteAnime
    }

    Column(
        modifier = modifier
            .bouncyPress(pressedScale = 0.96f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onClick)
            .testTag("library_poster")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = media.coverImage?.bestUrl,
                contentDescription = media.title?.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(placeholder),
                error = rememberVectorPainter(placeholder),
                fallback = rememberVectorPainter(placeholder)
            )

            libraryProgressLabel(entry)?.let { label ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(ExpressiveShapes.pill)
                        // Opaque enough to read a number over a pale cover, and
                        // translucent enough that the artwork still shows through.
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }

            if (isFavourite) {
                Icon(
                    imageVector = AppVectorIcons.FavouriteAnime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = media.title?.displayTitle.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LibraryEmpty(
    isLoading: Boolean,
    query: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            ExpressiveLoadingIndicator(
                modifier = Modifier.size(30.dp),
                color = MaterialTheme.colorScheme.primary
            )
            return@Column
        }
        ExpressiveEmptyOrb(
            icon = if (query.isBlank()) AppVectorIcons.StatEpisodeCount else AppVectorIcons.Search,
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (query.isBlank()) "Nothing in this list yet" else "No matches for \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** AniList covers are 2:3. */
private const val POSTER_ASPECT = 2f / 3f