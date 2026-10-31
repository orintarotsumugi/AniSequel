package com.anisequel.ui.screens.studio

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.detailFor
import com.anisequel.data.model.withSummaryProgress
import com.anisequel.domain.studio.StudioYearShelf
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.openExternalUrl
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.EntryEditor
import com.anisequel.ui.viewmodel.MediaDetailHost
import com.anisequel.ui.viewmodel.toMediaNode
import com.anisequel.ui.components.detail.SequelDetailSheet
import com.anisequel.ui.viewmodel.rememberPersonProfileHost
import com.anisequel.ui.viewmodel.rememberPlanningAddHost
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.viewmodel.StudioViewModel

/**
 * One studio's page: who they are, and everything AniList credits them with, by year.
 *
 * ## Why this is a route and not a sheet
 *
 * The title page opens in a sheet because it is a thing you look at *from* a list.
 * A studio is somewhere you go: it is reachable from a title's credits, from its
 * hero chip, and from another studio's own titles, and once there a reader scrolls
 * a filmography that is longer than the page it came from. As a sheet it would be
 * dismissed by the first back gesture and could not be linked, restored or scrolled
 * without dragging whatever was underneath it.
 *
 * ## One page, three answers to "what did they make"
 *
 * The shelves are years ([StudioYearShelf]) because a studio's work is read
 * chronologically - "what did they do after that one" is the question the page
 * exists for. Within a shelf, AniList's own `START_DATE_DESC` order is kept: two
 * titles in the same year were already ordered correctly by the server, and
 * re-sorting them here by a field this screen does not have would be a guess.
 *
 * ## Tapping a title
 *
 * The same detail sheet the dashboard and the profile open, so a title on a studio
 * page is the same page as a title anywhere else - which is the whole point of the
 * destination, and the reason it is one [com.anisequel.ui.components.detail.EntryEditor]
 * short of being *identical* to the one the dashboard opens. The host supplies that
 * editor when a session exists; without one there is no session to write with, so
 * the sheet draws the Info page and leaves the "My Entry" tab out rather than
 * drawing a tab whose Save could not do anything.
 *
 * The parts this page is made of - the header, the shelf headings and the three
 * states it can end in - live in `StudioSections.kt`; this file is about *what the
 * page is*, not how its capsules are drawn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun StudioScreen(
    viewModel: StudioViewModel,
    studioId: Int,
    /** The name the caller already had, so the bar is never blank on the first frame. */
    initialName: String?,
    onNavigateBack: () -> Unit,
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
    /**
     * The connection the cast and crew rows open a profile with.
     *
     * Nullable for the same reason as on the dashboard: a host with no repository
     * draws the cards as captions rather than as buttons that can only fail.
     */
    repository: com.anisequel.data.repository.AniListRepository? = null,
    /**
     * The app's one on-demand detail store.
     *
     * Passed in rather than built here, because the title this page opens has to be
     * the same page the Library and the Profile open: one fetch, one cache, one
     * merge rule. A studio page used to hold a store of its own, so a title walked
     * to from a filmography was fetched a second time and could disagree with the
     * same title opened from a list. See [MediaDetailHost].
     */
    detailHost: MediaDetailHost,
    /**
     * Read/write access to the viewer's own entry for the opened title, or null
     * when there is no session to write with.
     *
     * A studio page carries no session of its own - the navigation passes this
     * for the same reason it passes everything else that knows which account is
     * signed in. Null draws the Info page without the "My Entry" tab rather than
     * with a disabled one.
     */
    entryEditor: EntryEditor? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    // The person whose profile is open, and the callbacks the cast and crew rows
    // open it with. Null repository draws captions, not buttons.
    val profiles = rememberPersonProfileHost(repository)
    val detailNodes by detailHost.details.collectAsState()
    val loadingDetailIds by detailHost.loading.collectAsState()
    val detailFriends by detailHost.friends.collectAsState()

    // One-tap Planning for a studio's title. A studio page carries no session of
    // its own, so the host is gated on the editor the navigation passed - the
    // same answer the sheet's "My Entry" destination uses.
    val planningAdd = rememberPlanningAddHost(
        repository = repository,
        canWrite = entryEditor != null
    )
    val quickAddStatuses by planningAdd.statuses.collectAsState()

    val context = LocalContext.current
    val listState = rememberLazyListState()
    // Two ways to read a filmography: the year shelves the page shipped with, and
    // one packed wall with the years dropped. The choice is this visit's, not a
    // preference - a studio is browsed by mood, and the layout follows the mood.
    var wallLayout by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // The title the sheet is showing, as the shelf's own node so the sheet has
    // something to draw on the first frame; the fetched detail takes over as soon
    // as it lands.
    var selectedMedia by remember { mutableStateOf<MediaNode?>(null) }

    LaunchedEffect(Unit) { viewModel.loadFirstPage() }

    val success = state as? StudioViewModel.StudioUiState.Success
    val name = success?.studio?.name ?: initialName?.takeIf { it.isNotBlank() } ?: "Studio"

    // Paging is driven by the *end of the list coming into view* rather than by a
    // button, because a filmography is read by scrolling. `derivedStateOf` so this
    // recomposes when the answer flips rather than on every scrolled frame.
    //
    // It fires at most once per change of either key: after a page lands, the state
    // changes but `reachedEnd` usually does not, so the next page waits for the
    // reader to scroll again rather than pulling the whole filmography at once.
    val reachedEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 2
        }
    }
    LaunchedEffect(reachedEnd, success?.hasMore) {
        if (reachedEnd && success?.hasMore == true) viewModel.loadNextPage()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("studio_screen"),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .testTag("studio_back_button")
                                .bouncyPress(pressedScale = 0.9f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { wallLayout = false },
                            modifier = Modifier
                                .testTag("studio_layout_shelves")
                                .bouncyPress(pressedScale = 0.9f)
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.List,
                                contentDescription = "Shelf layout",
                                tint = if (!wallLayout) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(
                            onClick = { wallLayout = true },
                            modifier = Modifier
                                .testTag("studio_layout_wall")
                                .bouncyPress(pressedScale = 0.9f)
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.ViewGrid,
                                contentDescription = "Wall layout",
                                tint = if (wallLayout) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                // `siteUrl` when AniList sent one, derived from the id
                                // otherwise - the field is nullable, and a button that
                                // silently does nothing is worse than a guessed URL the
                                // browser can 404 on.
                                openExternalUrl(
                                    context,
                                    success?.studio?.siteUrl ?: "https://anilist.co/studio/$studioId"
                                )
                            },
                            modifier = Modifier
                                .testTag("studio_anilist_button")
                                .bouncyPress(pressedScale = 0.9f)
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.OpenInBrowser,
                                contentDescription = "Open on AniList"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider()
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = listState,
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "studio_identity") {
                StudioIdentity(
                    studio = success?.studio,
                    fallbackName = name,
                    titleCount = success?.titleCount
                )
            }

            when (val current = state) {
                is StudioViewModel.StudioUiState.Loading -> item(key = "studio_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveLoadingIndicator(
                            modifier = Modifier.size(30.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                is StudioViewModel.StudioUiState.Error -> item(key = "studio_error") {
                    StudioFailure(message = current.message, onRetry = viewModel::retry)
                }

                is StudioViewModel.StudioUiState.Success -> {
                    if (current.shelves.isEmpty()) {
                        item(key = "studio_empty") { StudioEmpty() }
                    }

                    if (wallLayout) {
                        item(key = "studio_wall") {
                            // One packed wall rather than a lazy grid: the page is
                            // already inside a LazyColumn, and a second lazy
                            // scroller would fight it for the gesture. Fifty cards
                            // laid out directly is cheaper than that fight.
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .testTag("studio_wall"),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                current.shelves.flatMap { it.titles }.forEach { media ->
                                    StudioTitleCard(
                                        media = media,
                                        onClick = { selectedMedia = media }
                                    )
                                }
                            }
                        }
                    } else {
                        current.shelves.forEach { shelf ->
                            item(key = "shelf_header_${shelf.label}") {
                                StudioShelfHeader(shelf = shelf)
                            }
                            item(key = "shelf_row_${shelf.label}") {
                                LazyRow(
                                    modifier = Modifier.testTag("studio_shelf_${shelf.label}"),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(items = shelf.titles, key = { it.id }) { media ->
                                        StudioTitleCard(
                                            media = media,
                                            onClick = { selectedMedia = media }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "studio_footer") {
                        StudioFooter(
                            success = current,
                            name = name,
                            onRetry = viewModel::retry
                        )
                    }
                }
            }
        }
    }

    selectedMedia?.let { summary ->
        // The shelf's node is a list node, so the fetched detail supersedes it and
        // the progress it carried - none, on a studio page - is preserved by the
        // same merge the profile uses.
        val subject = AnimeDetailSubject.Media(
            detailNodes.detailFor(summary)?.withSummaryProgress(summary) ?: summary
        )
        SequelDetailSheet(
            subject = subject,
            sheetState = sheetState,
            onDismiss = { selectedMedia = null },
            // A relation replaces the subject rather than stacking a sheet; see
            // `InfoRelationsSection`.
            onOpenMedia = { media -> selectedMedia = media },
            onAddToPlanning = {},
            onAddToWatching = {},
            quickAddStatuses = quickAddStatuses,
            onAddMediaToPlanning = planningAdd.onAddToPlanning,
            onAddMediaToWatching = planningAdd.onAddToWatching,
            onLoadDetail = detailHost.onLoadDetail,
            onLoadFriends = detailHost.onLoadFriends,
            friends = detailFriends[summary.id].orEmpty(),
            isDetailLoading = summary.id in loadingDetailIds,
            // Same sheet, same access: the tab follows the editor, and an
            // unauthenticated reader gets exactly the page they always got.
            canWriteToAniList = entryEditor != null,
            entryEditor = entryEditor,
            // A studio name on a title's own page - reached from the credits of a
            // title opened here - goes to *that* studio. The sheet is closed first:
            // a `ModalBottomSheet` lives in its own window and would otherwise stay
            // drawn over the screen that replaced it.
            onOpenStudio = { id, studioName ->
                selectedMedia = null
                onOpenStudio(id, studioName)
            },
            onOpenCharacter = profiles.onOpenCharacter,
            onOpenStaff = profiles.onOpenStaff
        )
    }

    // A sibling of the detail sheet, not a child. A studio page's filmography is
    // where you walked in from, so a tap on the titles row leaves you one step
    // further along it rather than stacking a third window on top.
    profiles.Sheet { appearance -> selectedMedia = appearance.toMediaNode() }
}
