package com.example.ui.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FollowUser
import com.example.data.model.MediaListEntryItem
import com.example.data.model.MediaNode
import com.example.data.model.withSummaryProgress
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.SequelDetailSheet
import com.example.ui.components.detail.AnimeDetailSubject
import com.example.ui.components.expressive.ExpressiveLoadingIndicator
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.ExpressiveNavigationBar
import com.example.ui.components.expressive.NavigationDestination
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.components.openExternalUrl
import com.example.ui.viewmodel.UserActivityViewModel
import com.example.ui.viewmodel.UserOverviewUiState
import com.example.ui.viewmodel.UserOverviewViewModel
import com.example.ui.viewmodel.UserSocialViewModel

/**
 * How much room a tab's list leaves for the floating navigation capsule.
 *
 * A shared constant because each tab would otherwise guess it, and a guess that
 * is slightly short is not visibly wrong - the last item just ends up behind the
 * bar until the user scrolls again. The capsule is 52dp of item plus 5dp of
 * padding either side plus 12dp of margin, so 96dp clears it with the gesture
 * inset on top.
 */
internal val NavigationBarClearance = 96.dp

/**
 * The padding every tab's scrollable content uses.
 *
 * The bottom is *not* the Scaffold's. The navigation capsule is meant to float
 * over the content rather than push it, so the Scaffold's bottom inset is
 * replaced with [NavigationBarClearance].
 */
internal fun profileListPadding(contentPadding: PaddingValues = PaddingValues()): PaddingValues = PaddingValues(
    top = contentPadding.calculateTopPadding() + 8.dp,
    bottom = NavigationBarClearance
)

/**
 * The five destinations, declared once so the bar and the `when` cannot disagree.
 *
 * The Library destination is fourth because it is the one a person arrives at to
 * answer "what is this account actually tracking", which is a question about the
 * person rather than about their activity - so it sits beside the other
 * person-shaped tabs and before Social, which is the furthest from the profile's
 * subject.
 */
private val ProfileTabs = listOf(
    NavigationDestination("Home", AppVectorIcons.ProfileHome, "profile_tab_home"),
    NavigationDestination("Activity", AppVectorIcons.ProfileActivity, "profile_tab_activity"),
    NavigationDestination("Stats", AppVectorIcons.ProfileStats, "profile_tab_stats"),
    NavigationDestination("Library", AppVectorIcons.StatEpisodeCount, "profile_tab_library"),
    NavigationDestination("Social", AppVectorIcons.ProfileSocial, "profile_tab_social")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    overviewViewModel: UserOverviewViewModel,
    activityViewModel: UserActivityViewModel,
    socialViewModel: UserSocialViewModel,
    onOpenUser: (FollowUser) -> Unit,
    onNavigateBack: () -> Unit,
    onSignInAgain: () -> Unit = {},
    /**
     * The tab the screen opens on.
     *
     * An argument rather than only screen state because the profile can be
     * opened *for* a tab: the home-screen widget opens the Activity feed, and a
     * screen that always started on Home would land the user one tap short of
     * what they asked for. Only the first composition reads it - after that the
     * user's own taps own the selection.
     */
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()
    val activityState by activityViewModel.state.collectAsStateWithLifecycle()
    val commentsState by activityViewModel.comments.collectAsStateWithLifecycle()
    val likersState by activityViewModel.likers.collectAsStateWithLifecycle()
    val socialState by socialViewModel.state.collectAsStateWithLifecycle()
    // The manga half of the Library tab. Collected here rather than inside the tab
    // because the tab is disposed on every switch away, and a collection that
    // started and stopped with it would drop a fetch that was already in flight.
    val mangaLibrary by overviewViewModel.mangaLibrary.collectAsStateWithLifecycle()
    val isLoadingManga by overviewViewModel.isLoadingManga.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val detailNodes by activityViewModel.detailNodes.collectAsStateWithLifecycle()
    val loadingDetailIds by activityViewModel.loadingDetailIds.collectAsStateWithLifecycle()
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    // The activity row that was tapped, as the row's own media node so the
    // sheet has something to draw on the first frame. The fetched detail
    // supersedes it once it lands.
    var selectedMedia by remember { mutableStateOf<MediaNode?>(null) }

    val selectedSubject = selectedMedia?.let { summary ->
        AnimeDetailSubject.Media(detailNodes[summary.id]?.withSummaryProgress(summary) ?: summary)
    }
    val detailLoading = selectedMedia?.id?.let { it in loadingDetailIds } == true

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (uiState as? UserOverviewUiState.Success)
                            ?.username
                            ?.let { "@$it" }
                            ?: "AniList",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .testTag("profile_back_button")
                            .bouncyPress(pressedScale = 0.9f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    val siteUrl = (uiState as? UserOverviewUiState.Success)?.siteUrl
                    if (siteUrl != null) {
                        IconButton(
                            onClick = { openExternalUrl(context, siteUrl) },
                            modifier = Modifier
                                .testTag("profile_open_anilist_button")
                                .bouncyPress(pressedScale = 0.9f)
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.OpenInNew,
                                contentDescription = "Open on AniList"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
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
                    LaunchedEffect(state.userId) {
                        if (state.userId > 0) {
                            activityViewModel.updateUserId(state.userId)
                            socialViewModel.updateUserId(state.userId)
                        }
                    }
                    ProfileTabsContent(
                        state = state,
                        selectedTab = selectedTab,
                        activityState = activityState,
                        commentsState = commentsState,
                        activityViewModel = activityViewModel,
                        socialState = socialState,
                        socialViewModel = socialViewModel,
                        mangaLibrary = mangaLibrary,
                        isLoadingManga = isLoadingManga,
                        onLoadManga = { overviewViewModel.loadMangaLibrary() },
                        onOpenUser = onOpenUser,
                        onOpenDetail = { selectedMedia = it }
                    )
                    ExpressiveNavigationBar(
                        destinations = ProfileTabs,
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    )
                }
            }
        }
    }

    // The activity row's detail sheet. A plain media subject: an activity entry
    // is a show the person already tracks, so it has no parent and nothing to
    // add - the same distinction [com.example.data.model.ArrivingEntry] draws.
    selectedSubject?.let { subject ->
        SequelDetailSheet(
            subject = subject,
            sheetState = detailSheetState,
            onDismiss = { selectedMedia = null },
            onAddToPlanning = {},
            onAddToWatching = {},
            onLoadDetail = { activityViewModel.loadDetail(it) },
            isDetailLoading = detailLoading,
            canWriteToAniList = false
        )
    }

    // The heart's long-press sheet, a sibling of the detail sheet rather than a
    // child of the activity list: a `ModalBottomSheet` lives in its own window,
    // and nesting it inside the tab's `AnimatedContent` would take it down with
    // the tab's outgoing transition on any tab switch.
    likersState?.let { likers ->
        ActivityLikersSheet(
            state = likers,
            onDismiss = { activityViewModel.dismissLikers() }
        )
    }
}

@Composable
private fun ProfileTabsContent(
    state: UserOverviewUiState.Success,
    selectedTab: Int,
    activityState: UserActivityViewModel.ActivityState,
    commentsState: UserActivityViewModel.CommentsState?,
    activityViewModel: UserActivityViewModel,
    socialState: UserSocialViewModel.SocialState,
    socialViewModel: UserSocialViewModel,
    mangaLibrary: List<MediaListEntryItem>?,
    isLoadingManga: Boolean,
    /**
     * Passed rather than the ViewModel itself.
     *
     * [ProfileTabsContent] is a private composable that exists to keep the tab
     * switch in one place; handing it the overview ViewModel would widen its
     * surface to the whole profile loader when all it needs is one action. It is
     * a lambda rather than the ViewModel so the dependency stays visible at the
     * call site.
     */
    onLoadManga: () -> Unit,
    onOpenUser: (FollowUser) -> Unit,
    onOpenDetail: (MediaNode) -> Unit
) {
    // Hoisted above [AnimatedContent] so each tab keeps its own scroll position.
    //
    // `AnimatedContent` composes the incoming tab alongside the outgoing one and
    // then *disposes* the outgoing one when its transition finishes. Anything
    // remembered inside a tab therefore dies with it, and a tab remembered its
    // `LazyListState` there: switching to Stats and back dropped the reader at
    // the top of a feed they had scrolled a long way down, every time, with no
    // indication that anything had happened. Hoisting makes the states outlive
    // the transition, so returning to a tab restores exactly where it was.
    val homeListState = rememberLazyListState()
    val activityListState = rememberLazyListState()
    val statsListState = rememberLazyListState()
    val libraryGridState = rememberLazyGridState()
    val socialGridState = rememberLazyGridState()

    // Favourites are their own connection in AniList rather than a list status,
    // so the Library's Favourites filter tests membership of these ids instead of
    // matching a status string. Taken from the same state the Home tab renders,
    // so pinning and favouriting can never disagree.
    val favouriteIds = remember(state.libraryAnime, state.favouriteAnime, state.favouriteManga) {
        (state.favouriteAnime + state.favouriteManga).mapNotNullTo(mutableSetOf()) { it.id }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.isRefreshing) {
            ProfileRefreshBar()
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val forward = targetState > initialState
                (
                    fadeIn(animationSpec = ExpressiveMotion.FastEffects) + slideInHorizontally(
                        animationSpec = ExpressiveMotion.DefaultSpatialOffset,
                        initialOffsetX = { fullWidth -> if (forward) fullWidth / 8 else -fullWidth / 8 }
                    )
                ) togetherWith (
                    fadeOut(animationSpec = ExpressiveMotion.FastEffects) + slideOutHorizontally(
                        animationSpec = ExpressiveMotion.DefaultSpatialOffset,
                        targetOffsetX = { fullWidth -> if (forward) -fullWidth / 8 else fullWidth / 8 }
                    )
                )
            },
            label = "profile_tab_transition",
            modifier = Modifier.fillMaxSize()
        ) { tabIndex ->
            val padding = PaddingValues(top = 0.dp)
            when (tabIndex) {
                1 -> ProfileActivityTab(
                    activityState = activityState,
                    onLoadFirstPage = { activityViewModel.loadFirstPage() },
                    onLoadNextPage = { activityViewModel.loadNextPage() },
                    onRetry = { activityViewModel.retry() },
                    contentPadding = padding,
                    listState = activityListState,
                    calendar = state.activity,
                    commentsState = commentsState,
                    // A comment is a write, and the demo feed has no session to
                    // write with - the same gate every other mutation uses.
                    canPostComment = !state.isDemoMode,
                    userName = state.username,
                    userAvatar = state.avatarUrl,
                    onLike = { activityViewModel.toggleLike(it) },
                    onOpenComments = { activityViewModel.openComments(it) },
                    onCloseComments = { activityViewModel.closeComments() },
                    onRetryComments = { activityViewModel.openComments(it) },
                    onPostComment = { activityViewModel.postComment(it) },
                    onOpenLikers = { activityViewModel.openLikers(it) },
                    onOpenDetail = onOpenDetail
                )

                2 -> ProfileStatsTab(state = state, contentPadding = padding, listState = statsListState)

                3 -> ProfileLibraryTab(
                    animeEntries = state.libraryAnime,
                    mangaEntries = mangaLibrary,
                    favouriteIds = favouriteIds,
                    isLoadingManga = isLoadingManga,
                    onLoadManga = onLoadManga,
                    contentPadding = padding,
                    onOpenDetail = onOpenDetail,
                    gridState = libraryGridState
                )

                4 -> ProfileSocialTab(
                    socialState = socialState,
                    onSelectList = { list ->
                        socialViewModel.select(list)
                    },
                    onLoad = { list ->
                        socialViewModel.load(list)
                    },
                    onOpenUser = onOpenUser,
                    contentPadding = padding,
                    gridState = socialGridState
                )

                else -> ProfileHomeTab(
                    state = state,
                    contentPadding = padding,
                    onOpenDetail = onOpenDetail,
                    listState = homeListState
                )
            }
        }
    }
}

@Composable
private fun ProfileRefreshBar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_refreshing"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
}
