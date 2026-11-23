package com.anisequel.ui.screens.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.anisequel.ui.components.icons.Icons
import com.anisequel.ui.components.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.mediaCoverKey
import com.anisequel.data.model.MediaSearchType
import com.anisequel.data.model.displayName
import com.anisequel.data.model.detailFor
import com.anisequel.data.model.withSummaryProgress
import com.anisequel.data.model.isManga
import com.anisequel.data.model.posterColor
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.domain.jokes.JokeMoment
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.JokeMomentBus
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.cards.cardPosterUrl
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressivePolygonSegmentedBar
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.SegmentedOption
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaBackdrop
import com.anisequel.ui.components.nova.NovaSurface
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.navigation.mainBarClearance
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.SequelDetailSheet
import com.anisequel.ui.viewmodel.MediaDetailHost
import com.anisequel.ui.viewmodel.rememberPlanningAddHost
import com.anisequel.ui.viewmodel.rememberPersonProfileHost
import com.anisequel.ui.viewmodel.toMediaNode
import com.anisequel.ui.theme.LocalSearchAnimeEnabled
import com.anisequel.ui.theme.LocalSearchMangaEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** How long the field waits after a keystroke before asking AniList. */
private const val SEARCH_DEBOUNCE_MILLIS = 450L

/** Two characters, because one is a prefix rather than a question. */
private const val SEARCH_MIN_LENGTH = 2

/** A late response is allowed to publish only while it still owns the field. */
internal fun isCurrentSearchGeneration(response: Int, current: Int): Boolean =
    response == current

/**
 * The catalogue search: AniList's whole library, not the reader's list.
 *
 * ## Why this is a page and not a field on Home
 *
 * The dashboard's search narrows the titles the app has already found for you,
 * which is a different question from "does this show exist and what is it
 * called". Mixing them put a field on Home that silently changed meaning
 * depending on which list happened to be loaded, and it is the reason this page
 * has its own tab rather than a shortcut.
 *
 * ## No ViewModel
 *
 * The page owns two pieces of state - what was typed and what came back - and
 * nothing else in the app reads either. A ViewModel here would be a factory, a
 * lifecycle and a key for no second reader; the repository is the only thing
 * that outlives the composition, and it is handed in.
 *
 * The search is debounced and cancelled by `LaunchedEffect(query, type)`: every
 * keystroke restarts the coroutine, so only the last one in a burst reaches the
 * network. AniList allows roughly thirty requests a minute, and a request per
 * character is how the field would spend them before the reader finished typing.
 *
 * The page asks **one namespace at a time**, chosen by the segmented control and
 * limited by the two switches in Settings. A combined list could not answer "the
 * anime I meant" without also offering a manga of the same name, and the two
 * namespaces number their ids independently - so `anime/1` and `manga/1` are
 * different titles that a shared result card would key as one.
 *
 * A result is a full, tappable title card. `mediaListEntry` is displayed on
 * that card and the shared detail sheet handles every edit, so search cannot
 * accidentally overwrite an existing status with a quick-add mutation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaSearchScreen(
    repository: AniListRepository,
    canWrite: Boolean,
    quickAddPreferences: QuickAddPreferences,
    onBack: () -> Unit,
    detailHost: MediaDetailHost,
    entryEditor: com.anisequel.ui.components.detail.EntryEditor? = null,
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    /**
     * The two halves Settings allows. Read as composition locals by default so
     * the nav graph does not have to thread two booleans through every layer, and
     * a preview can show either arm without a DataStore.
     */
    searchAnimeEnabled: Boolean = LocalSearchAnimeEnabled.current,
    searchMangaEnabled: Boolean = LocalSearchMangaEnabled.current
) {
    val profiles = rememberPersonProfileHost(repository)
    val planningAdd = rememberPlanningAddHost(repository = repository, canWrite = canWrite)
    val quickAddStatuses by planningAdd.statuses.collectAsStateWithLifecycle()
    val quickAdd by quickAddPreferences.settings.collectAsStateWithLifecycle(
        initialValue = QuickAddPreferences.QuickAddSettings()
    )

    val enabledTypes = remember(searchAnimeEnabled, searchMangaEnabled) {
        buildList {
            if (searchAnimeEnabled) add(MediaSearchType.ANIME)
            if (searchMangaEnabled) add(MediaSearchType.MANGA)
        }
    }
    // Saved by name rather than by ordinal: the enabled set can lose an entry
    // when a Settings switch changes, and an ordinal would then silently point at
    // the other namespace instead of falling back to the first enabled one.
    var typeName by rememberSaveable { mutableStateOf(MediaSearchType.DEFAULT.name) }
    val activeType = enabledTypes.firstOrNull { it.name == typeName } ?: enabledTypes.firstOrNull()
    val activeLabel = activeType?.displayName?.lowercase(Locale.ROOT)

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaNode>>(emptyList()) }
    var searchedTerm by remember { mutableStateOf("") }
    var searchedType by remember { mutableStateOf<MediaSearchType?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMedia by remember { mutableStateOf<MediaNode?>(null) }
    var requestGeneration by remember { mutableIntStateOf(0) }
    val details by detailHost.details.collectAsStateWithLifecycle()
    val loadingDetailIds by detailHost.loading.collectAsStateWithLifecycle()
    val friends by detailHost.friends.collectAsStateWithLifecycle()
    val friendsLoadingIds by detailHost.friendsLoading.collectAsStateWithLifecycle()

    /**
     * The one place a search is asked for.
     *
     * A local function rather than the body of the effect, because the "Try again"
     * control has to make the *same* request as the search it repeats, and a second
     * copy is how a retry quietly starts asking a different question. It does not
     * debounce - the caller decides that - so pressing the button goes out at once
     * while typing still waits for the burst to end.
     */
    suspend fun runSearch(term: String, type: MediaSearchType, generation: Int) {
        isSearching = true
        error = null
        try {
            repository.searchMedia(term, type)
                .onSuccess { found ->
                    // A repository implementation may complete despite cancellation.
                    // The generation check prevents its stale answer replacing the
                    // results for the query currently visible in the field.
                    if (isCurrentSearchGeneration(generation, requestGeneration)) {
                        results = found
                        searchedTerm = term
                        searchedType = type
                        error = null
                        // A settled search is a system moment the reader can invite
                        // a line into. Announced only for a real answer with results:
                        // a joke over "nothing found" would read as the app mocking
                        // the query it just failed.
                        if (found.isNotEmpty()) {
                            JokeMomentBus.announce(JokeMoment.SEARCH)
                        }
                    }
                }
                .onFailure { failure ->
                    if (isCurrentSearchGeneration(generation, requestGeneration)) {
                        // The results already on screen are kept. This used to clear
                        // them, so one rate-limited refinement threw away a complete,
                        // still-true answer the reader was reading and replaced it
                        // with a one-line apology. A failure is about the *new* term;
                        // it is not evidence that the old answer was wrong.
                        searchedTerm = term
                        error = failure.message ?: "AniList did not answer."
                    }
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (isCurrentSearchGeneration(generation, requestGeneration)) isSearching = false
        }
    }

    val searchScope = rememberCoroutineScope()
    // No debounce here: the reader has already waited once, and tapping the control
    // is the strongest possible statement that they mean this query now.
    val retrySearch: () -> Unit = {
        val type = activeType
        val term = query.trim()
        if (type != null && term.length >= SEARCH_MIN_LENGTH) {
            val generation = ++requestGeneration
            searchScope.launch {
                runSearch(term = term, type = type, generation = generation)
            }
        }
    }

    LaunchedEffect(query, activeType) {
        val type = activeType
        if (type == null) {
            results = emptyList()
            searchedTerm = ""
            searchedType = null
            isSearching = false
            error = null
            return@LaunchedEffect
        }

        val term = query.trim()
        val generation = ++requestGeneration
        // A result from the other namespace is worse than no result: it is an
        // answer to a question the reader did not ask, drawn under the selected
        // chip. Drop it the moment the chip moves.
        if (searchedType != type) {
            results = emptyList()
            searchedTerm = ""
            searchedType = type
        }
        if (term.length < SEARCH_MIN_LENGTH) {
            results = emptyList()
            searchedTerm = ""
            isSearching = false
            error = null
            return@LaunchedEffect
        }

        isSearching = true
        error = null
        delay(SEARCH_DEBOUNCE_MILLIS)
        runSearch(term = term, type = type, generation = generation)
    }

    NovaBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .navigationBarsPadding()
                .testTag("nova_search_screen")
        ) {
            CatalogueSearchHeader(
                activeType = activeType,
                enabledTypes = enabledTypes,
                activeLabel = activeLabel,
                onBack = onBack,
                onSelectType = { chosen -> typeName = chosen.name }
            )
            CatalogueSearchField(
                query = query,
                onQueryChange = { query = it },
                activeType = activeType,
                isSearching = isSearching
            )
            CatalogueSearchBody(
                activeType = activeType,
                activeLabel = activeLabel,
                query = query,
                results = results,
                searchedTerm = searchedTerm,
                isSearching = isSearching,
                error = error,
                onRetry = retrySearch,
                onOpenMedia = { selectedMedia = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }

    selectedMedia?.let { summary ->
        val subject = AnimeDetailSubject.Media(
            details.detailFor(summary)?.withSummaryProgress(summary) ?: summary
        )
        SequelDetailSheet(
            subject = subject,
            onDismiss = { selectedMedia = null },
            onAddToPlanning = {},
            onAddToWatching = {},
            quickAddStatuses = quickAddStatuses,
            onAddMediaToPlanning = planningAdd.onAddToPlanning,
            onAddMediaToWatching = planningAdd.onAddToWatching,
            holdDurationSeconds = quickAdd.holdDurationSeconds,
            swipeEnabled = quickAdd.swipeEnabled,
            onLoadDetail = detailHost.onLoadDetail,
            onLoadFriends = detailHost.onLoadFriends,
            friends = friends[subject.media.mediaCoverKey].orEmpty(),
            isFriendsLoading = subject.media.mediaCoverKey in friendsLoadingIds,
            isDetailLoading = subject.media.mediaCoverKey in loadingDetailIds,
            canWriteToAniList = canWrite,
            entryEditor = entryEditor,
            onOpenMedia = { selectedMedia = it },
            onOpenStudio = { id, name ->
                selectedMedia = null
                onOpenStudio(id, name)
            },
            onOpenCharacter = profiles.onOpenCharacter,
            onOpenStaff = profiles.onOpenStaff
        )
    }
    profiles.Sheet { appearance -> selectedMedia = appearance.toMediaNode() }
}

/**
 * The page's title, back action and namespace picker.
 *
 * Extracted from [NovaSearchScreen] rather than left inline: the page's state and
 * its request logic already fill a screenful, and CodeFactor was right that the
 * combination crossed its complexity line. Splitting the three controls that only
 * read state also makes each one previewable without a repository.
 */
@Composable
private fun CatalogueSearchHeader(
    activeType: MediaSearchType?,
    enabledTypes: List<MediaSearchType>,
    activeLabel: String?,
    onBack: () -> Unit,
    onSelectType: (MediaSearchType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                                    .size(48.dp)
                    .bouncyPress(pressedScale = 0.9f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .testTag("nova_search_back")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    activeType == null -> "Both catalogue halves are off"
                    enabledTypes.size == 1 -> "The ${activeLabel ?: "catalogue"} " +
                        "catalogue on AniList"
                    else -> "Anime and manga, one catalogue at a time"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (enabledTypes.size > 1 && activeType != null) {
        ExpressivePolygonSegmentedBar(
            options = enabledTypes.map { SegmentedOption(label = it.displayName) },
            selectedIndex = enabledTypes.indexOf(activeType),
            onSelect = { index ->
                enabledTypes.getOrNull(index)
                    ?.takeIf { it != activeType }
                    ?.let(onSelectType)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .testTag("catalogue_search_type_row")
        )
    }
}

/** The field, including the indicator that does not replace the results below it. */
@Composable
private fun CatalogueSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    activeType: MediaSearchType?,
    isSearching: Boolean
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("nova_search_input"),
        placeholder = {
            Text(
                text = when (activeType) {
                    MediaSearchType.MANGA -> "Search the manga catalogue"
                    MediaSearchType.ANIME -> "Search the anime catalogue"
                    null -> "Turn a catalogue half on in Settings"
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        singleLine = true,
        shape = ExpressiveShapes.pill,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        leadingIcon = {
            Icon(
                imageVector = AppVectorIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
        },
        trailingIcon = {
            // Both, not either. The clear button used to be *replaced* by the
            // spinner while a request was in flight - identical to the results
            // staying on screen and the field staying readable - so the one moment
            // the reader most wants to start over was the moment there was no way
            // to empty the field. The indicator is a second, smaller element now.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSearching) {
                    ExpressiveLoadingIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("nova_search_clear")
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Close,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    )
}

/** The one of five answers the page has for the current field and request. */
@Composable
private fun CatalogueSearchBody(
    activeType: MediaSearchType?,
    activeLabel: String?,
    query: String,
    results: List<MediaNode>,
    searchedTerm: String,
    isSearching: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenMedia: (MediaNode) -> Unit,
    modifier: Modifier = Modifier
) {
    // A settled search replaces the result set, and the grid kept its scroll
    // offset across that swap - so a fresh answer could open halfway down a page
    // the reader had never seen, with the top of it apparently missing. Hoisted
    // here because the reset belongs to the grid, and keyed on the answer rather
    // than on every keystroke: typing must not scroll a page that is not changing.
    val resultGridState = rememberLazyGridState()
    // Keyed on the answer, not the term. A failed search still sets `searchedTerm`
    // (it is what the note is about), so keying on it scrolled the reader to the top
    // of results that had not changed, under a failure that had nothing to do with
    // where they were reading.
    LaunchedEffect(results, activeType) {
        resultGridState.scrollToItem(0)
    }
    Box(modifier = modifier) {
        when {
            activeType == null -> SearchNote(
                text = "Anime search and manga search are both off. Turn one " +
                    "on in Settings > Library > Bottom bar to bring this page back."
            )

            query.trim().length < SEARCH_MIN_LENGTH -> SearchNote(
                text = "Type at least $SEARCH_MIN_LENGTH characters to search " +
                    "the ${activeLabel ?: "catalogue"} catalogue. Your list " +
                    "only changes after you edit a title's entry."
            )

            isSearching && results.isEmpty() -> SearchSkeleton()

            // A failure with no answer on screen is this note; a failure *with* an
            // answer keeps the grid and puts the note above it (see the grid branch).
            error != null && results.isEmpty() ->
                SearchFailureNote(message = error, onRetry = onRetry)

            results.isEmpty() -> SearchNote(
                text = "No ${activeLabel ?: "catalogue"} title matches " +
                    "\"$searchedTerm\"."
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = resultGridState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("nova_search_results"),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 6.dp,
                    bottom = mainBarClearance() + 20.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(
                    key = "search_result_summary",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "search_summary"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (results.size == 1) "1 title found" else "${results.size} titles found",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isSearching) "Updating AniList results…" else "For “$searchedTerm”",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSearching) {
                            ExpressiveLoadingIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (error != null) {
                    // The previous answer is still true, so it stays; this strip says
                    // the *new* query did not arrive, and offers the one action that
                    // can change that without editing the field.
                    item(
                        key = "search_stale_failure",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "search_summary"
                    ) {
                        SearchFailureNote(message = error, onRetry = onRetry)
                    }
                }
                items(
                    items = results,
                    key = { media -> "${media.type}_${media.id}" },
                    contentType = { "search_result" }
                ) { media ->
                    SearchResultCard(
                        media = media,
                        onOpen = { onOpenMedia(media) }
                    )
                }
            }
        }
    }
}

/** One catalogue title as a complete, tappable poster card. */
@Composable
private fun SearchResultCard(media: MediaNode, onOpen: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val coverColor = media.posterColor.toCoverColorOrNull() ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val metadata = listOfNotNull(
        media.type?.lowercase(Locale.ROOT)?.replaceFirstChar { it.uppercase(Locale.ROOT) },
        media.format?.takeIf { it.isNotBlank() },
        media.averageScore?.takeIf { it > 0 }?.let { "★ $it" },
        media.seasonYear?.toString()
    ).joinToString(" · ")
    NovaSurface(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpen
            )
            .testTag("nova_search_result_${media.id}"),
        tonalElevation = true,
        shape = RoundedCornerShape(ExpressiveShapes.MediumCorner)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .background(coverColor)
            ) {
                AsyncImage(
                    model = cardPosterUrl(media),
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = ExpressiveShapes.pill,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                ) {
                    Text(
                        text = if (media.isManga) "MANGA" else "ANIME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .padding(horizontal = 9.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = media.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = metadata.ifBlank { "Tap for details" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val listStatus = media.mediaListEntry?.status
                if (listStatus != null) {
                    Text(
                        text = com.anisequel.domain.list.ListStatuses.label(listStatus, media.isManga),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** A sentence where the results would be, with no fake cards around it. */
@Composable
private fun SearchNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The shape of the results that are on the wire, drawn with the app's shimmer. */
@Composable
private fun SearchSkeleton() {
    // One clock for this screen. The skeleton used to put a Material loading
    // indicator above the shimmering cards, so a wait that is already animated ran
    // two independent loops and the smaller one was the only thing moving. The
    // shimmer is the state; the spinner above it said the same thing twice.
    val phase = rememberShimmerPhase()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .testTag("nova_search_skeleton"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(count = 6, key = { "search_skeleton_$it" }, contentType = { "search_skeleton" }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(ExpressiveShapes.MediumCorner)
                    )
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.68f),
                    shape = RoundedCornerShape(ExpressiveShapes.SmallCorner),
                    phase = phase
                )
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.9f).height(13.dp), phase = phase)
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.62f).height(10.dp), phase = phase)
            }
        }
    }
}

/**
 * A failed search, said in one line, with the one control that can change it.
 *
 * It replaces the bare sentence the page used to print: "Couldn't search AniList -
 * ..." with no way forward, so the reader's only exit was to edit the field and hope
 * the next keystroke fared better. AniList's own client guidance for this state is a
 * retry action rather than an empty list, and the request is right there.
 */
@Composable
private fun SearchFailureNote(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "Couldn't search AniList - $message",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.testTag("nova_search_retry")
        ) {
            Text("Try again")
        }
    }
}
