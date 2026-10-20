package com.anisequel.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.detailFor
import com.anisequel.data.model.withSummaryProgress
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.ProfileAvatarButton
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.EntryEditor
import com.anisequel.ui.components.detail.SequelDetailSheet
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.navigation.mainBarClearance
import com.anisequel.ui.screens.profile.ProfileErrorView
import com.anisequel.ui.screens.profile.ProfileLibraryTab
import com.anisequel.ui.screens.profile.ProfileLoadingView
import com.anisequel.ui.screens.profile.RememberedLibraryView
import com.anisequel.ui.viewmodel.MediaDetailHost
import com.anisequel.ui.viewmodel.UserOverviewUiState
import com.anisequel.ui.viewmodel.UserOverviewViewModel
import com.anisequel.ui.viewmodel.rememberPersonProfileHost
import com.anisequel.ui.viewmodel.toMediaNode

/**
 * The reader's own library, as a page of the app's main bar.
 *
 * ## Why this is not a profile tab any more
 *
 * The list used to be the fourth tab of *every* profile - the viewer's own, a
 * follower's, a scanned stranger's - and it never belonged to most of them: a
 * library is one account's, and the bar is where the reader's own things live.
 * So the list left the profile and became one of the bar's pages. What is drawn
 * is unchanged - [ProfileLibraryTab] is the composable the tab always was -
 * because the browsing surface was never the problem; where it hung was.
 *
 * ## The sheet is the sheet
 *
 * A title here opens the same sheet the dashboard opens - same page, same
 * "My Entry" destination - because that tab always holds the reader's own
 * entry for the media, which has nothing to do with which list they came from.
 * The host hands over a `RepositoryEntryEditor` when a session exists; without
 * one the sheet is read-only and the tab is not drawn at all, which is the rule
 * `EntryEditor` states. There is still no add-to-list action on this page: a
 * library is titles already on the list, so there is nothing to add and the
 * quick-add belongs to the dashboard's rows. There is no activity feed or
 * comment thread either - those are about a *person*, and they stayed on the
 * profile this page left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    repository: AniListRepository,
    overviewViewModel: UserOverviewViewModel,
    detailHost: MediaDetailHost,
    remembered: RememberedLibraryView,
    gridState: LazyGridState,
    onOpenProfile: () -> Unit,
    onOpenTierList: () -> Unit,
    onOpenStudio: (Int, String?) -> Unit,
    onSignInAgain: () -> Unit,
    /**
     * Read/write access to the reader's own entry for the opened title, or null
     * when there is no session to write with.
     *
     * Supplied by the host rather than built here: which account is signed in is
     * the navigation's answer, not this page's - see
     * `com.anisequel.ui.components.detail.RepositoryEntryEditor`. Null removes the
     * "My Entry" destination entirely rather than drawing it disabled.
     */
    entryEditor: EntryEditor? = null,
    modifier: Modifier = Modifier
) {
    val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()
    // The manga half of the library. Collected at this level rather than inside
    // the grid because a fetch is already a request when it starts, and a
    // collection that started with a grid somebody scrolled away from would drop
    // an answer that is already on the wire.
    val mangaLibrary by overviewViewModel.mangaLibrary.collectAsStateWithLifecycle()
    val isLoadingManga by overviewViewModel.isLoadingManga.collectAsStateWithLifecycle()
    val detailNodes by detailHost.details.collectAsStateWithLifecycle()
    val loadingDetailIds by detailHost.loading.collectAsStateWithLifecycle()
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // The poster that was tapped, as its own list node so the sheet has
    // something to draw on the first frame. The fetched detail supersedes it
    // once it lands.
    var selectedMedia by remember { mutableStateOf<MediaNode?>(null) }

    val selectedSubject = selectedMedia?.let { summary ->
        AnimeDetailSubject.Media(detailNodes.detailFor(summary)?.withSummaryProgress(summary) ?: summary)
    }
    val detailLoading = selectedMedia?.id?.let { it in loadingDetailIds } == true

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen"),
        topBar = {
            val success = uiState as? UserOverviewUiState.Success
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(
                                text = "Library",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1
                            )
                            if (success != null) {
                                Text(
                                    text = "@${success.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        // Beside the title rather than in `actions`, for the reason
                        // the profile's trophy gives: the tier list ranks the
                        // library this page is showing, so it is worth reaching
                        // while looking at that library. In the actions slot it
                        // would read as another place the page goes rather than as
                        // a way of ordering what is on it.
                        IconButton(
                            onClick = onOpenTierList,
                            modifier = Modifier
                                .testTag("library_open_tier_list_button")
                                .bouncyPress(pressedScale = 0.9f)
                        ) {
                            // The glyph is a shade under the bar's 24dp rather
                            // than shrunk to match: it keeps the full 48dp touch
                            // target, so the button stays easy to hit while
                            // reading as lighter than the avatar beside it.
                            Icon(
                                imageVector = AppVectorIcons.Trophy,
                                contentDescription = "Open tier list",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                // The reader's own face leads the page, exactly as it leads the
                // dashboard. It is not a substitute for the bar's Profile tab:
                // both go to the same place, and the avatar is the recognisable
                // half of "you" while the tab is the place.
                navigationIcon = {
                    ProfileAvatarButton(
                        avatarUrl = success?.avatarUrl,
                        enabled = success != null,
                        onClick = onOpenProfile,
                        testTag = "library_profile_button"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        // Read once and passed to both, rather than read twice at the call site:
        // a CompositionLocal read is a dependency, and taking it in the same
        // lambda that consumes it keeps this to one.
        val layoutDirection = LocalLayoutDirection.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Start/end insets are applied; the bottom deliberately is not,
                // because the app's main bar floats over the content rather than
                // pushing it, and its clearance lives in the grid's own
                // `contentPadding`.
                //
                // Only the top was applied here, which is the safe-area bug this
                // fixes: `padding(top = ...)` discarded the start and end insets
                // the Scaffold had already resolved, so on a landscape device
                // with a display cutout along one edge the top bar and every
                // poster ran underneath it. The cutout was invisible as a cutout -
                // it looked like the layout was simply using more of the screen
                // than it should.
                .padding(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding()
                )
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val state = uiState) {
                is UserOverviewUiState.Loading -> ProfileLoadingView(message = state.message)
                is UserOverviewUiState.Error -> ProfileErrorView(
                    message = state.message,
                    canRetry = state.canRetry,
                    isAuthError = state.isAuthError,
                    onRetry = { overviewViewModel.load() },
                    onSignInAgain = onSignInAgain
                )
                is UserOverviewUiState.Success -> {
                    // Favourites are their own connection in AniList rather than a
                    // list status, so the Favourites filter tests membership of
                    // these ids instead of matching a status string. Taken from the
                    // same overview the profile's Home tab renders, so pinning and
                    // favouriting can never disagree.
                    val favouriteIds = remember(state.favouriteAnime, state.favouriteManga) {
                        (state.favouriteAnime + state.favouriteManga)
                            .mapNotNullTo(mutableSetOf()) { it.id }
                    }
                    ProfileLibraryTab(
                        animeEntries = state.libraryAnime,
                        mangaEntries = mangaLibrary,
                        favouriteIds = favouriteIds,
                        isLoadingManga = isLoadingManga,
                        // The list is only on the critical path of this page and
                        // the profile's Stats tab, so the flag is threaded here
                        // rather than baked into `libraryAnime` being empty - an
                        // empty list is a real answer for an account that tracks
                        // nothing.
                        isAnimeLoading = state.isLibraryLoading,
                        // A list that could not be read is not an empty one.
                        libraryError = state.libraryError,
                        onLoadManga = { overviewViewModel.loadMangaLibrary() },
                        // This page's whole bottom clearance, including the
                        // system navigation inset; see [mainBarClearance].
                        contentPadding = PaddingValues(top = 8.dp, bottom = mainBarClearance()),
                        onOpenDetail = { selectedMedia = it },
                        gridState = gridState,
                        remembered = remembered
                    )
                }
            }
        }
    }

    // The person whose profile sheet is opened from the cast and crew rows.
    //
    // Passed in rather than reached for on a ViewModel: `UserOverviewViewModel`
    // holds its repository privately, and a cast tap is a *read* belonging to
    // whoever drew the sheet rather than to the profile loader.
    val profiles = rememberPersonProfileHost(repository)

    // Both sheets are siblings of the page's content rather than children of it,
    // which is the rule `UserProfileScreen` states for its tabs and for the same
    // reason: a `ModalBottomSheet` is a window of its own, so one drawn from
    // inside the content is torn down with the piece that drew it.
    //
    // The poster's detail sheet. A plain media subject: a library entry is a
    // title the reader already tracks, so it has no parent and nothing to add -
    // the same distinction [com.anisequel.data.model.ArrivingEntry] draws.
    selectedSubject?.let { subject ->
        SequelDetailSheet(
            subject = subject,
            sheetState = detailSheetState,
            onDismiss = { selectedMedia = null },
            onAddToPlanning = {},
            onAddToWatching = {},
            onLoadDetail = detailHost.onLoadDetail,
            isDetailLoading = detailLoading,
            // The sheet is the same one the dashboard opens, so it gets the same
            // access: null here still draws the whole Info page, minus the one
            // tab it cannot honour. See the KDoc on [entryEditor].
            canWriteToAniList = entryEditor != null,
            entryEditor = entryEditor,
            // A relation replaces the subject rather than stacking a sheet: see
            // `InfoRelationsSection`. The summary is kept alongside the fetched
            // detail so the reader's own progress survives the hop.
            onOpenMedia = { media -> selectedMedia = media },
            onOpenStudio = { id, name ->
                // Close first: the studio page is pushed by the caller and a sheet
                // left open would be its own window over it.
                selectedMedia = null
                onOpenStudio(id, name)
            },
            onOpenCharacter = profiles.onOpenCharacter,
            onOpenStaff = profiles.onOpenStaff
        )
    }

    // A sibling of the detail sheet, not a child: a `ModalBottomSheet` lives in its
    // own window, so one drawn from inside another covers it, and a reader who
    // tapped a face in the cast would find themselves unable to get back. A tap on
    // the titles row replaces the title underneath rather than stacking a third
    // window - that row is how you walk from a cast member to the next season.
    profiles.Sheet { appearance -> selectedMedia = appearance.toMediaNode() }
}
