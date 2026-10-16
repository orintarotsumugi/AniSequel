package com.anisequel.ui.screens.profile

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.withSummaryProgress
import com.anisequel.data.repository.LibraryPreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.detail.SequelDetailSheet
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveNavigationBar
import com.anisequel.ui.components.expressive.NavigationDestination
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.openExternalUrl
import com.anisequel.ui.viewmodel.UserActivityViewModel
import com.anisequel.ui.viewmodel.UserOverviewUiState
import com.anisequel.ui.viewmodel.UserOverviewViewModel
import com.anisequel.ui.viewmodel.UserSocialViewModel

/**
 * How much room a tab's list leaves for the floating navigation capsule,
 * *below the system navigation inset*.
 *
 * This is the capsule's own height - 52dp of item, 5dp of padding either side,
 * 12dp of margin - and deliberately **not** the whole clearance. The system inset
 * that follows it is read from the device at draw time by
 * [navigationBarClearance], because it is not a constant: a gesture bar, a
 * three-button bar and a cutout are three different heights, and hardcoding the
 * largest of them either wastes space or leaves the last row of a list sitting
 * under the bar. That mismatch is also what produces the "bounce back" snap when
 * the list reaches its end and the padding disagrees with where the bar really
 * is.
 */
internal val NavigationBarClearance = 96.dp

/**
 * The full clearance a list needs: the capsule, plus the device's own navigation
 * inset beneath it.
 *
 * Composable because it reads a `WindowInsets`, which is only available during
 * composition. Read rather than assumed - see [NavigationBarClearance] for why a
 * constant is the wrong answer here.
 */
@Composable
internal fun navigationBarClearance(): Dp {
    val systemInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return CAPSULE_CLEARANCE + systemInset
}

/** The capsule's own height above the system inset. */
private val CAPSULE_CLEARANCE = 96.dp

/**
 * The padding every tab's scrollable content uses.
 *
 * The bottom is *not* the Scaffold's. The navigation capsule is meant to float
 * over the content rather than push it, so the Scaffold's bottom inset is
 * replaced with [navigationBarClearance].
 */
@Composable
internal fun profileListPadding(contentPadding: PaddingValues = PaddingValues()): PaddingValues = PaddingValues(
    top = contentPadding.calculateTopPadding() + 8.dp,
    bottom = navigationBarClearance()
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
    /**
     * Identifies *which* profile this is, for state that must not be shared between
     * two of them.
     *
     * `ProfileDestination` already builds one for its ViewModels; this is that same
     * key, passed through so the profile's saved tab can be scoped by it. Without
     * it, opening a second profile from the Social grid hands that profile whichever
     * tab the first was left on.
     */
    viewModelKey: String = "",
    overviewViewModel: UserOverviewViewModel,
    activityViewModel: UserActivityViewModel,
    socialViewModel: UserSocialViewModel,
    onOpenUser: (FollowUser) -> Unit,
    onNavigateBack: () -> Unit,
    /**
     * The Library tab's remembered media type.
     *
     * Threaded to the tab rather than built there so there is one DataStore
     * reader per process. Defaulted because every other screen parameter here
     * is, and the tab treats a missing store as "ask me again" rather than
     * failing.
     */
    libraryPreferences: LibraryPreferences = LibraryPreferences(LocalContext.current),
    /**
     * Whether this profile is the signed-in viewer's own.
     *
     * The tier list is a single personal ranking, stored keyed on the signed-in
     * account - it is a judgement about the reader's own library, not about
     * whoever this profile happens to be about. So the trophy only means
     * something here when the profile *is* the reader's own.
     *
     * It used to be drawn on every profile, including a follower's and a scanned
     * stranger's. Opening one of those and tapping it ranked the *viewer's* titles
     * while the screen showed somebody else's library, which is the exact thing
     * the stored format refuses. A control that opens the wrong account's list is
     * worse than a missing one, so it is hidden off your own profile rather than
     * shown and re-explained.
     */
    isOwnProfile: Boolean = true,
    /**
     * Opens the tier list for this library.
     *
     * A callback rather than a navigation call so the screen keeps no reference to
     * the `NavController`, which is the same split `ProfileDestination` already
     * makes: every route decision lives in one place, and the screen only says
     * what it wants to happen.
     */
    onOpenTierList: () -> Unit = {},
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
    // Covers for the bio's AniList links. Arrive after the profile does, so the
    // bio renders immediately and posters fill in underneath the reader.
    val mediaCovers by overviewViewModel.mediaCovers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val detailNodes by activityViewModel.detailNodes.collectAsStateWithLifecycle()
    val loadingDetailIds by activityViewModel.loadingDetailIds.collectAsStateWithLifecycle()
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // `rememberSaveable` keys its registry entry on the *composition slot*, not on
    // the value passed here, so an unkeyed call shares one saved tab between every
    // profile opened from this composable: following a follower landed that person
    // on whichever tab the reader had left the previous profile on. `viewModelKey`
    // gives each profile its own entry in that same registry.
    //
    // `initialTab` is deliberately *not* part of the key. The widget asks for the
    // Activity tab on one profile, and keying on it would make that request
    // outrank the tab the reader last chose here.
    var selectedTab by rememberSaveable(viewModelKey) { mutableIntStateOf(initialTab) }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = (uiState as? UserOverviewUiState.Success)
                                ?.username
                                ?.let { "@$it" }
                                ?: "AniList",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        // Only on the reader's own profile: the tier list is their
                        // personal ranking, and the trophy used to appear here on
                        // everyone's - tapping it from a follower's page opened the
                        // viewer's own list over somebody else's library.
                        if (isOwnProfile) {
                            // Beside the name rather than in `actions`, because the
                            // tier list ranks the library this profile is showing and
                            // is worth reaching while looking at that library. In the
                            // actions slot it would sit among the links and read as
                            // another place this profile goes, rather than as a way of
                            // ordering what is on it.
                            IconButton(
                                onClick = onOpenTierList,
                                modifier = Modifier
                                    .testTag("profile_open_tier_list_button")
                                    .bouncyPress(pressedScale = 0.9f)
                            ) {
                                // The glyph is a shade under the bar's 24dp rather
                                // than shrunk to match: it keeps the full 48dp touch
                                // target, so the button stays easy to hit while
                                // reading as lighter than the back arrow and the
                                // AniList link beside it.
                                Icon(
                                    imageVector = AppVectorIcons.Trophy,
                                    contentDescription = "Open tier list",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
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
        // Read once and passed to both, rather than read twice at the call site:
        // a CompositionLocal read is a dependency, and taking it in the same
        // lambda that consumes it keeps this to one.
        val layoutDirection = LocalLayoutDirection.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Start/end insets are applied; the bottom deliberately is not,
                // because the navigation capsule floats over the content rather
                // than pushing it, and its clearance is handled by
                // [profileListPadding] instead.
                //
                // Only the top was applied here, which is the safe-area bug this
                // fixes: `padding(top = ...)` discarded the start and end insets
                // the Scaffold had already resolved, so on a landscape device
                // with a display cutout along one edge the header, the bio and
                // every poster ran underneath it. The cutout was invisible as a
                // cutout - it looked like the layout was simply using more of
                // the screen than it should.
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
                        libraryPreferences = libraryPreferences,
                        mediaCovers = mediaCovers,
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
    // add - the same distinction [com.anisequel.data.model.ArrivingEntry] draws.
    selectedSubject?.let { subject ->
        SequelDetailSheet(
            subject = subject,
            sheetState = detailSheetState,
            onDismiss = { selectedMedia = null },
            // A relation replaces the subject rather than stacking a sheet: see
            // `InfoRelationsSection`. The summary is kept alongside the fetched
            // detail so the viewer's own progress survives the hop.
            onOpenMedia = { media -> selectedMedia = media },
            onAddToPlanning = {},
            onAddToWatching = {},
            onLoadDetail = { id, type -> activityViewModel.loadDetail(id, type) },
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
    /** Forwarded to the Library tab, which owns the remembered media type. */
    libraryPreferences: LibraryPreferences,
    /** Media key to cover, for the AniList preview cards in the bio. */
    mediaCovers: Map<MediaCoverKey, MediaCover>,
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

    // Which group of Stats charts is open, held here for the same reason the scroll
    // states are: the tab is disposed on every switch away, so a choice made inside
    // it dies with the transition. It lives in this composable rather than in the
    // screen's own state because nothing outside the Stats tab reads it, and
    // `rememberSaveable` is what keeps it across a rotation - a reader who opened
    // Tags on a tablet and turned it should not be handed Overview back.
    var statsSection by rememberSaveable { mutableStateOf(StatsSection.OVERVIEW) }

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

                2 -> ProfileStatsTab(
                    state = state,
                    contentPadding = padding,
                    section = statsSection,
                    onSelectSection = { statsSection = it },
                    listState = statsListState
                )

                3 -> ProfileLibraryTab(
                    animeEntries = state.libraryAnime,
                    mangaEntries = mangaLibrary,
                    favouriteIds = favouriteIds,
                    isLoadingManga = isLoadingManga,
                    // The list is only on the critical path of this tab and Stats,
                    // so the flag is threaded here rather than baked into
                    // `libraryAnime` being empty - an empty list is a real answer
                    // for an account that tracks nothing.
                    isAnimeLoading = state.isLibraryLoading,
                    // A list that could not be read is not an empty one.
                    libraryError = state.libraryError,
                    onLoadManga = onLoadManga,
                    contentPadding = padding,
                    onOpenDetail = onOpenDetail,
                    gridState = libraryGridState,
                    libraryPreferences = libraryPreferences
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
                    listState = homeListState,
                    mediaCovers = mediaCovers
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
