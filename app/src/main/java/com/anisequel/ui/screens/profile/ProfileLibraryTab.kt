package com.anisequel.ui.screens.profile

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.repository.LibraryPreferences
import com.anisequel.data.model.isManga
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.AniSequelTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
    /**
     * The order AniList returned, which is grouped by list status.
     *
     * This used to be called "Recent" and it was a lie. `MediaListCollection`
     * returns one list per *status group* - all Completed, then all Currently
     * Watching, then all Planning - with no timestamp on an entry and no sort
     * argument in the query. So "Recent" showed a status-grouped blob, and
     * toggling it did nothing at all, because the branch returned the input
     * unchanged.
     *
     * Renamed to say what it is rather than adding a fake date. A sort the user
     * can identify from the result is worth more than a plausible name, and the
     * real recency sort now exists beside it: [UPDATED_AT] reads the entry's own
     * update time, which is the thing "Recent" was pretending to be.
     */
    LIST_ORDER("List order"),

    /**
     * Your own last edit, newest first - "you last updated this on ...".
     *
     * The recency sort that "List order" never was. AniList's list entry carries
     * `updatedAt`: the moment the viewer last changed its progress, score or
     * status, which is the honest answer to "what have I been working on". A title
     * finished this week sits above one added in 2019, whatever status group each
     * happens to arrive in.
     */
    UPDATED_AT("Recently updated"),

    TITLE("A-Z"),
    SCORE("Score"),
    RELEASE_YEAR("Year");

    /** True when this sort has a meaningful ascending/descending direction. */
    val isDirectional: Boolean get() = this != LIST_ORDER
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
        LibrarySort.RELEASE_YEAR -> current to !currentAscending
        // Entering Score opens *descending*: highest first. Keeping the previous
        // direction put the lowest-scored entry on top, so the first thing the
        // list showed was the show they liked least.
        LibrarySort.SCORE ->
            if (currentAscending) {
                LibrarySort.SCORE to false
            } else {
                LibrarySort.TITLE to true
            }
        // Same shape as Score: the newest update is the answer somebody sorting by
        // recency came for, so the first tap opens newest-first.
        LibrarySort.UPDATED_AT ->
            if (currentAscending) {
                LibrarySort.UPDATED_AT to false
            } else {
                LibrarySort.TITLE to true
            }
        // List order has no direction to toggle, so the tap moves on instead of
        // appearing to do nothing. Every control that visibly ignores a tap is a
        // control the reader learns to distrust.
        LibrarySort.LIST_ORDER -> LibrarySort.TITLE to true
    }

/**
 * The state a sort opens in when its chip is *selected*.
 *
 * Deliberately separate from [toggleSort], which answers the different question
 * "the reader tapped the sort that is already active". The chip row used to call
 * [toggleSort] for *every* chip, so pressing "Score" while "List order" was
 * active selected "A-Z" - the chip that was pressed and the sort that was applied
 * were different controls. Selecting a chip is not the same gesture as re-tapping
 * the active one.
 */
internal fun selectSort(sort: LibrarySort): Pair<LibrarySort, Boolean> = when (sort) {
    LibrarySort.LIST_ORDER -> LibrarySort.LIST_ORDER to true
    // Newest first: "recently updated" sorted oldest-first would open on the
    // titles the reader has not touched in years.
    LibrarySort.UPDATED_AT -> LibrarySort.UPDATED_AT to false
    LibrarySort.TITLE -> LibrarySort.TITLE to true
    LibrarySort.SCORE -> LibrarySort.SCORE to false
    // Newest release first, which is what "Year" is asked for in practice.
    LibrarySort.RELEASE_YEAR -> LibrarySort.RELEASE_YEAR to false
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
        LibrarySort.LIST_ORDER ->
            // Deliberately untouched. This is the order AniList returned, and
            // the label says so - there is no date on an entry to reorder by,
            // so reversing it would only swap two status groups for two others.
            return entries
        LibrarySort.TITLE -> compareBy { it.media.title?.displayTitle?.lowercase(Locale.ROOT) ?: "" }
        LibrarySort.SCORE -> return rankedBlanksLast(entries, { it.media.averageScore }, ascending)
        LibrarySort.RELEASE_YEAR -> return rankedBlanksLast(entries, ::releaseYearOf, ascending)
        LibrarySort.UPDATED_AT -> return rankedBlanksLast(entries, { it.updatedAt }, ascending)
    }
    return entries.sortedWith(if (ascending) comparator else comparator.reversed())
}

/**
 * Sorts by a number some entries may not have, putting those entries at the
 * bottom in *both* directions.
 *
 * The obvious spelling is `compareBy { it.value == null }` followed by
 * `.reversed()` for descending, and it is wrong in the way that reads as
 * right: reversing the comparator reverses the flag along with the value, so
 * descending puts every blank at the **top**. A library sorted by score then
 * opens on its unrated titles, which is indistinguishable from a list of
 * favourites - the exact failure the flag was added to prevent.
 *
 * So the flag leaves the comparator. Partitioning keeps the direction's reach
 * over the value and nothing else, and the unrankable are appended afterwards
 * where no direction can reach them.
 */
private fun rankedBlanksLast(
    entries: List<MediaListEntryItem>,
    value: (MediaListEntryItem) -> Int?,
    ascending: Boolean
): List<MediaListEntryItem> {
    val (rankable, blank) = entries.partition { value(it) != null }
    // `Int.MIN_VALUE` is unreachable - every element here has a value - and
    // exists only to give the selector a non-null return type.
    val byValue = compareBy<MediaListEntryItem> { value(it) ?: Int.MIN_VALUE }
    return rankable.sortedWith(if (ascending) byValue else byValue.reversed()) + blank
}

/**
 * The year a title first aired or was first published.
 *
 * `seasonYear` first because AniList populates it more reliably than
 * `startDate`, which is frequently partial - a year with no month, or a month
 * with no year. Falling back rather than preferring the other way round means a
 * dated title wins even when its second source is absent.
 */
internal fun releaseYearOf(entry: MediaListEntryItem): Int? =
    entry.media.seasonYear
        ?: entry.media.startDate?.year?.takeIf { it > 0 }

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

    // A rewatch sits at its finished progress while `status` says `REPEATING`, so
    // "24/24" is the badge for a person who is on episode 3 of a show they have
    // already finished once - it says they are done, which is the opposite of what
    // `REPEATING` records. The percentage is the only number that stays true.
    if (entry.status.equals("repeating", ignoreCase = true) && total != null && total > 0) {
        val percent = (progress.coerceAtMost(total) * 100) / total
        return "$unit $percent%"
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
    /**
     * Whether the anime list is still on its way.
     *
     * The Library reads entries the Stats tab's tallies forced a download for, so
     * it used to inherit two possible states - here, or not yet - and rendered the
     * second as "Nothing in this list yet" over an account with 900 entries. Null
     * entries cannot express the difference, so the flag does. Defaulted because a
     * caller that does not know is a caller whose list is already in hand.
     */
    isAnimeLoading: Boolean = false,
    /**
     * Why the anime list could not be read, or null when it was.
     *
     * Distinct from [isAnimeLoading] on purpose: a list that is still arriving is
     * "counting", and a list that could not be read is "unknown". Rendering the
     * second as the first - or as an empty list - states a fact about the account
     * that nothing established.
     */
    libraryError: String? = null,
    onLoadManga: () -> Unit,
    contentPadding: PaddingValues,
    onOpenDetail: (MediaNode) -> Unit,
    modifier: Modifier = Modifier,
    /** Remembers the last chosen media type. Not defaulted: a caller that forgot
     *  it would silently never remember, which looks identical to the bug. */
    libraryPreferences: LibraryPreferences,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val scope = rememberCoroutineScope()
    /**
 * The media type, status filter and sort, remembered across visits.
 *
 * All three used to be plain state inside this composable, and that dies with
 * it: the profile screen switches tabs through an `AnimatedContent`, which
 * disposes the tab it animates away from. Only scroll position survived (it is
 * hoisted), so every trip to Activity and back reset a narrowed "Completed,
 * newest first" view to "All, list order" - the reader's decision silently
 * unmade because the state lived somewhere a transition could reach. The type
 * already lived in DataStore; the filter and sort now live beside it.
 *
 * [rememberedType] is null until DataStore answers, which is asynchronous, so for
 * one composition there is no stored answer to apply. `type` starts at
 * [LibraryType.ALL] - it has to, because `rememberSaveable` cannot hold a
 * nullable here without every read below unwrapping it - and so
 * [rememberedTypePending] is what keeps the grid from *drawing* that default.
 * The same gate covers filter and sort: all the keys are read from one
 * DataStore file, so they arrive in one emission and the grid never draws a
 * half-restored selection.
 *
 * Once it resolves, the stored name wins. An unrecognised stored value falls
 * back to its default rather than leaving the tab empty - a stored value the
 * enum no longer has means the preference was written by a build that knew an
 * entry this one does not, and showing the default list is the recoverable
 * outcome.
 */
    val rememberedType by libraryPreferences.lastType.collectAsStateWithLifecycle(initialValue = null)
    val rememberedFilter by libraryPreferences.lastFilter.collectAsStateWithLifecycle(initialValue = null)
    val rememberedSort by libraryPreferences.lastSort.collectAsStateWithLifecycle(initialValue = null)
    val rememberedSortAscending by libraryPreferences.lastSortAscending.collectAsStateWithLifecycle(initialValue = null)

    var choiceMade by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(LibraryType.ALL) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.LIST_ORDER) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    val rememberedTypePending = rememberedType == null

    LaunchedEffect(rememberedType, rememberedFilter, rememberedSort, rememberedSortAscending) {
        // Once anything on screen has been chosen, later emissions are echoes of
        // this screen's own writes and applying them would be at best a no-op and
        // at worst a stale value overwriting a newer local one.
        if (choiceMade) return@LaunchedEffect
        rememberedType?.let { name ->
            LibraryType.entries.firstOrNull { it.name == name }
        }?.let { stored -> type = stored }
        rememberedFilter?.let { name ->
            LibraryFilter.entries.firstOrNull { it.name == name }
        }?.let { stored -> filter = stored }
        rememberedSort?.let { name ->
            LibrarySort.entries.firstOrNull { it.name == name }
        }?.let { stored ->
            sort = stored
            // The direction is written in the same edit as the sort, so a stored
            // sort without one cannot come from this app; `?: true` is only a
            // floor for that impossible pair.
            sortAscending = rememberedSortAscending ?: true
        }
    }

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

    // Only a scope that reads the anime list is waiting on it: a Manga-only view
    // is waiting on its own, separate request, and a spinner for the wrong one
    // would be a second, quieter version of the same lie as the zeroes.
    val awaitingAnime = isAnimeLoading && type != LibraryType.MANGA
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
        // Nothing at all until DataStore has answered, because `type` is `ALL`
        // for exactly that window and `ALL` is the list the person chose to avoid
        // remembering a narrower type for. A spinner rather than an empty state:
        // an empty grid here would be indistinguishable from the account having
        // no library, which is the same false answer the pending note below
        // exists to avoid.
        if (rememberedTypePending) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "library_resolving_type") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                        .testTag("library_resolving_type"),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveLoadingIndicator(
                        modifier = Modifier.size(30.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

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
                    choiceMade = true
                    sort = nextSort
                    sortAscending = nextAscending
                    scope.launch {
                        libraryPreferences.setSort(nextSort.name, nextAscending)
                    }
                },
                isLoadingManga = isLoadingManga,
                query = query,
                onType = { chosen ->
                    choiceMade = true
                    type = chosen
                    // Recorded on the tap, not on a save button: the memory is
                    // there so they are not asked again, and asking them to
                    // confirm the choice would defeat it.
                    query = ""
                    scope.launch { libraryPreferences.setType(chosen.name) }
                },
                onFilter = { chosen ->
                    choiceMade = true
                    filter = chosen
                    scope.launch { libraryPreferences.setFilter(chosen.name) }
                },
                onQueryChange = { query = it }
            )
        }

        if (awaitingAnime) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "library_pending") {
                PendingDataNote("Still fetching this list - these counts are not final yet")
            }
        }

        // A failed fetch, said plainly, in place of "nothing here".
        //
        // These two were the same message for every account whose list could not be
        // read - a private list, a rate limit, a dropped connection all produced
        // "Nothing in this list yet" and chips reading (0), which is the app
        // asserting the one thing it has the least evidence for. The counts below
        // are still zeroes for the same reason and are not faked here.
        if (libraryError != null && !awaitingAnime) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "library_error") {
                PendingDataNote(
                    "Couldn't load this list - $libraryError. Your bio, " +
                        "favourites and stats are still here."
                )
            }
        }

        if (visible.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "library_empty") {
                LibraryEmpty(
                    isLoading = (type == LibraryType.MANGA && isLoadingManga) ||
                        (awaitingAnime && animeEntries.isEmpty()),
                    // A failed fetch is not an empty list. Saying "nothing here"
                    // when the truth is "we could not read it" is the false answer
                    // this whole path exists to stop.
                    failedMessage = if (libraryError != null && !awaitingAnime) {
                        "Couldn't load this list - $libraryError"
                    } else {
                        null
                    },
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
                // A scrolling row rather than a plain one. There are five sorts
                // now and "Recently updated" is a long label; laid out edge to
                // edge they overflowed the screen and the last chip was simply
                // unreachable.
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(LibrarySort.entries, key = { it.name }) { option ->
                        val active = option == sort
                        ExpressiveStateChip(
                            // The direction is only on the chip that is already
                            // active, so it cannot contradict itself: the chip you
                            // did not touch has no direction to show. It used to be
                            // drawn on every directional chip, which claimed each
                            // one would sort the way the current sort does.
                            label = when {
                                // No arrow on a sort that has no direction: an arrow
                                // that cannot change is a lie about the control.
                                !option.isDirectional -> option.label
                                !active -> option.label
                                sortAscending -> "${option.label} ↑"
                                else -> "${option.label} ↓"
                            },
                            selected = active,
                            // Selecting a chip is not the same gesture as re-tapping
                            // the active one. Every chip used to call `toggleSort`
                            // with the *current* sort, so pressing "Score" while
                            // "List order" was active selected "A-Z" - the chip
                            // pressed and the sort applied were different controls.
                            onClick = {
                                val next = if (active) {
                                    toggleSort(sort, sortAscending)
                                } else {
                                    selectSort(option)
                                }
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
                .clip(RoundedCornerShape(ExpressiveShapes.SmallCorner))
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
    query: String,
    /** Why the list could not be read, or null when it was. Never both empty and failed. */
    failedMessage: String? = null
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
        val failed = failedMessage != null
        ExpressiveEmptyOrb(
            icon = when {
                failed -> AppVectorIcons.Info
                query.isBlank() -> AppVectorIcons.StatEpisodeCount
                else -> AppVectorIcons.Search
            },
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            // A failed read is never "nothing here". These are different facts
            // about the account and only one of them is a thing about the person,
            // so they are different sentences.
            text = when {
                failed -> failedMessage
                query.isBlank() -> "Nothing in this list yet"
                else -> "No matches for \"$query\""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** AniList covers are 2:3. */
private const val POSTER_ASPECT = 2f / 3f