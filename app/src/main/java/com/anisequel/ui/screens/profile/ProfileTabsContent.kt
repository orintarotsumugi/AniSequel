package com.anisequel.ui.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaNode
import com.anisequel.domain.usecase.canBrowseProfileLibrary
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveNavigationBar
import com.anisequel.ui.components.expressive.NavigationDestination
import com.anisequel.ui.viewmodel.UserActivityViewModel
import com.anisequel.ui.viewmodel.UserMessagesViewModel
import com.anisequel.ui.viewmodel.UserOverviewUiState
import com.anisequel.ui.viewmodel.UserOverviewViewModel
import com.anisequel.ui.viewmodel.UserSocialViewModel

/**
 * The four destinations, declared once so the bar and the `when` cannot disagree.
 *
 * The Library used to sit fourth, between Stats and Social. It left for the app's
 * main bar: a person's own list is a top-level page now, not a tab of a profile
 * that could belong to anybody. The remaining four keep their order.
 */
private val ProfileTabs = listOf(
    NavigationDestination("Home", AppVectorIcons.ProfileHome, "profile_tab_home"),
    NavigationDestination("Activity", AppVectorIcons.ProfileActivity, "profile_tab_activity"),
    NavigationDestination("Stats", AppVectorIcons.ProfileStats, "profile_tab_stats"),
    NavigationDestination("Social", AppVectorIcons.ProfileSocial, "profile_tab_social")
)

/** The Stats tab, which reads the person's list for three of its charts. */
private const val STATS_TAB = 2

/** The borrowed list, which is only in the bar for somebody else's profile. */
private const val BORROWED_LIST_TAB = 4

/**
 * The bar for one profile, which is four tabs for the reader's own and five for
 * anybody else's.
 *
 * The fifth is a *borrowed* list and is not the reader's Library page under another
 * name: [canBrowseProfileLibrary] is the rule, and the reason it refuses an own
 * profile is that the viewer's list is one of the bar's pages with its own remembered
 * view in DataStore. Drawing it here too would be two doors on one page with two
 * different sets of chips behind them, and one of them would quietly stop being the
 * reader's own view of their own things.
 *
 * Appended rather than inserted, so every existing tab index keeps its meaning -
 * [com.anisequel.ui.navigation.ProfileRoutes.ACTIVITY_TAB_INDEX] and a profile opened
 * *on* the Activity tab from the home-screen widget are both indices into this list.
 */
private fun profileTabs(isOwnProfile: Boolean, isDemo: Boolean): List<NavigationDestination> =
    if (canBrowseProfileLibrary(isOwnProfile = isOwnProfile, isDemo = isDemo)) {
        ProfileTabs + NavigationDestination(
            label = "List",
            icon = AppVectorIcons.Library,
            testTag = "profile_tab_library"
        )
    } else {
        ProfileTabs
    }

/**
 * The activity feed's two engagement sheets, and only they.
 *
 * It exists to give the sheet states a composition boundary. `comments` and
 * `likers` are read here rather than in [UserProfileScreen], so a reply like, a
 * page of likers or a post settling recomposes this and not the profile's feed - a
 * feed that is a paged `LazyColumn` of blurred covers and is the most expensive
 * thing on the screen.
 *
 * The sheets themselves are siblings of the tab content, not children of a tab: a
 * `ModalBottomSheet` is a window of its own, so one drawn from inside the tab
 * `AnimatedContent` disposes is torn down with the tab - mid-sentence, draft
 * included.
 *
 * Called twice by the screen - once for the profile's own feed and once for the
 * friends feed - because each ViewModel owns one open thread, and sharing the sheet
 * state would put the profile feed's thread over a card in the friends feed.
 */
@Composable
internal fun ActivityEngagementSheets(
    activityViewModel: UserActivityViewModel,
    viewerId: Int?,
    canPost: Boolean
) {
    val commentsState by activityViewModel.comments.collectAsStateWithLifecycle()
    commentsState?.let { comments ->
        ActivityCommentsSheet(
            state = comments,
            canPost = canPost,
            viewerId = viewerId,
            onDismiss = { activityViewModel.closeComments() },
            onRetry = { activityViewModel.openComments(comments.activityId) },
            onPost = { activityViewModel.postComment(it) },
            onLikeReply = { activityViewModel.toggleReplyLike(it) },
            onOpenReplyActions = { activityViewModel.openReplyActions(it) },
            onReplyActionMode = { activityViewModel.setReplyActionMode(it) },
            onReplyActionDraft = { activityViewModel.updateReplyActionDraft(it) },
            onSubmitReplyEdit = { activityViewModel.submitReplyEdit() },
            onSubmitReplyDelete = { activityViewModel.submitReplyDelete() },
            onDismissReplyAction = { activityViewModel.dismissReplyAction() }
        )
    }

    val likersState by activityViewModel.likers.collectAsStateWithLifecycle()
    likersState?.let { likers ->
        ActivityLikersSheet(
            state = likers,
            onDismiss = { activityViewModel.dismissLikers() },
            onRetry = { activityViewModel.openLikers(likers.activityId) }
        )
    }
}

/**
 * The profile's tab capsule, the refresh bar, and whichever tab is selected.
 *
 * Split from [UserProfileScreen] for the reason every other profile file is split:
 * the screen owns the top bar, the sheets and the routing, while this owns the
 * page-switch itself - the hoisted scroll states, the per-tab saveable holder and
 * the `when` that decides what a tab index means.
 */
@Composable
internal fun ProfileTabsContent(
    state: UserOverviewUiState.Success,
    selectedTab: Int,
    /**
     * The capsule's tap, owned by the screen above.
     *
     * [ProfileTabsContent] draws the capsule but does not keep the selection: the
     * saved tab belongs to the screen, which is also what the widget's requested
     * tab is resolved against.
     */
    onSelectTab: (Int) -> Unit,
    /**
     * The Activity tab's opening segment, from the route.
     *
     * Threaded rather than read here because this composable does not own the
     * selection either: like [selectedTab], it belongs to the page it seeds, and
     * the only route that can ask for one is the viewer's own profile.
     */
    initialSegment: Int = SEGMENT_ACTIVITY,
    /**
     * Held rather than read from [state], because two of the tabs ask it for work.
     *
     * The Stats tab and a borrowed list both render this person's anime list, and on
     * anybody but the viewer that list is not fetched with the profile - it is asked
     * for by whichever of the two is opened, because the download is up to forty
     * sequential chunk requests. Its manga half arrives the same way.
     */
    overviewViewModel: UserOverviewViewModel,
    /** Whether the list tab is offered at all. See [profileTabs]. */
    isOwnProfile: Boolean,
    activityViewModel: UserActivityViewModel,
    followingViewModel: UserActivityViewModel,
    messagesViewModel: UserMessagesViewModel,
    /**
     * The signed-in viewer's id.
     *
     * Only read to decide whether the Activity tab's Friends and Messages
     * segments exist: both read the *reader's* account, so a follower's page or
     * a signed-out session has nothing to put in them.
     */
    viewerId: Int?,
    socialState: UserSocialViewModel.SocialState,
    socialViewModel: UserSocialViewModel,
    /** Media key to cover, for the AniList preview cards in the bio. */
    mediaCovers: Map<MediaCoverKey, MediaCover>,
    onOpenUser: (FollowUser) -> Unit,
    onOpenDetail: (MediaNode) -> Unit,
    /** Forwarded to the Home tab's pinned-studio chips. */
    onOpenStudio: (Int, String?) -> Unit,
    /** Forwarded to the Home tab's pinned characters; null opens the AniList page. */
    onOpenCharacter: ((Int) -> Unit)? = null,
    /** The same, for the pinned staff row. */
    onOpenStaff: ((Int) -> Unit)? = null
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
    // One per Activity segment. Hoisted for the same reason as the tab states
    // above: the segment's `AnimatedContent` disposes the feed it leaves, so a
    // state created inside would reset the friends feed on every switch back.
    val followingListState = rememberLazyListState()
    val messagesListState = rememberLazyListState()
    val statsListState = rememberLazyListState()
    val socialGridState = rememberLazyGridState()
    // Its own state, not the Social tab's: two grids of a stranger's things that
    // share a scroll position would drop the reader halfway down one when they
    // switch to the other.
    val listGridState = rememberLazyGridState()

    // Which group of Stats charts is open, held here for the same reason the scroll
    // states are: the tab is disposed on every switch away, so a choice made inside
    // it dies with the transition. It lives in this composable rather than in the
    // screen's own state because nothing outside the Stats tab reads it, and
    // `rememberSaveable` is what keeps it across a rotation - a reader who opened
    // Tags on a tablet and turned it should not be handed Overview back.
    var statsSection by rememberSaveable { mutableStateOf(StatsSection.OVERVIEW) }

    // Per-tab saveable state, so what a tab remembers inside itself survives the
    // `AnimatedContent` disposing it. The hoisted scroll states above are one half
    // of that; this is the other - the Social tab's search query, A-Z switch and
    // grid/list choice are `rememberSaveable` *inside* the tab, and switching to
    // Stats and back silently threw all three away.
    val tabStateHolder = rememberSaveableStateHolder()

    val tabs = profileTabs(isOwnProfile = isOwnProfile, isDemo = state.isDemoMode)
    // A saved index can only be out of range if the tab set changed under it, which
    // cannot happen for one profile (the saved tab is keyed per profile) - but a
    // capsule with nothing selected and a `when` that falls through to Home is the
    // better outcome if it ever does.
    val shownTab = selectedTab.coerceIn(0, tabs.lastIndex)

    // The two tabs that read this person's anime list are what ask for it.
    //
    // On the reader's own profile the list already came down with the overview, and
    // `loadAnimeLibrary` returns without doing anything in that case. On anybody
    // else's it is up to forty sequential chunk requests, so it waits until a tab
    // that shows it is open: opening a follower's profile to read their bio used to
    // spend the whole download on tallies behind three more taps.
    LaunchedEffect(shownTab, state.userId) {
        if (shownTab == STATS_TAB || shownTab == BORROWED_LIST_TAB) {
            overviewViewModel.loadAnimeLibrary()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // The tab capsule, at the top of the page.
        //
        // It used to be pinned to the bottom edge, and that edge now belongs to the
        // app's main bar, which is drawn by the host over this screen: a profile row
        // on the same edge would be two bars stacked at one edge, and the selected
        // tab of one would read as the selected page of the other. The profile's
        // own pages are still a page-level switch, so they keep the capsule -
        // directly under the top bar, above the content it switches.
        ExpressiveNavigationBar(
            destinations = tabs,
            selectedIndex = shownTab,
            onSelect = onSelectTab
        )

        // The refresh spinner is ~32dp of height that the tab below has to make room
        // for, and it used to appear and vanish with a bare `if`: a silent background
        // refresh therefore snapped every tab up and down by that height twice per
        // round trip. `expandVertically`/`shrinkVertically` grow and give back the bar's
        // own measured height on the spatial spring, so the content beneath it travels
        // rather than jumping, and the spinner fades in alongside it rather than
        // popping into existence.
        // `FastSpatialSize`, not the `Int` token: `expandVertically` is typed on
        // `FiniteAnimationSpec<IntSize>` (it animates a measured height, not a
        // count), and the two are not interchangeable - the compiler rejects the
        // `Int` one outright.
        AnimatedVisibility(
            visible = state.isRefreshing,
            enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                expandVertically(animationSpec = ExpressiveMotion.FastSpatialSize),
            exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                shrinkVertically(animationSpec = ExpressiveMotion.FastSpatialSize)
        ) {
            ProfileRefreshBar()
        }

        AnimatedContent(
            targetState = shownTab,
            transitionSpec = {
                val forward = targetState > initialState
                // The trailing `using null` drops the default `SizeTransform`. Every
                // tab here is a `LazyColumn`, and the default transform springs the
                // container's height between the outgoing and incoming page - which
                // re-measures both lists on every frame of the switch, under a reader
                // who may already be scrolling the one that is arriving. Settings and
                // the detail sheet carry the same note: the height snaps, while the
                // slide and the fade are draw-phase and stay.
                //
                // `FastSpatialOffset`, not `DefaultSpatialOffset`: the tab capsule is
                // a control the reader can tap faster than a page settles, and the
                // main bar's own pages use the fast token for exactly that reason. On
                // the calm spring a second tap lands while the first slide is still
                // travelling, and the two half-finished pages read as a stutter.
                val enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    slideInHorizontally(
                        animationSpec = ExpressiveMotion.FastSpatialOffset,
                        initialOffsetX = { fullWidth -> if (forward) fullWidth / 8 else -fullWidth / 8 }
                    )
                val exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    slideOutHorizontally(
                        animationSpec = ExpressiveMotion.FastSpatialOffset,
                        targetOffsetX = { fullWidth -> if (forward) -fullWidth / 8 else fullWidth / 8 }
                    )
                enter togetherWith exit using null
            },
            label = "profile_tab_transition",
            modifier = Modifier.fillMaxSize()
        ) { tabIndex ->
            tabStateHolder.SaveableStateProvider(tabIndex) {
                val padding = PaddingValues(top = 0.dp)
                when (tabIndex) {
                    1 -> ProfileActivityPage(
                        activityViewModel = activityViewModel,
                        followingViewModel = followingViewModel,
                        messagesViewModel = messagesViewModel,
                        // The demo has fixtures for both viewer feeds, so the
                        // segments are part of the demo's tour of the screen -
                        // but a signed-out session or somebody else's profile
                        // has no viewer account to show and hides them rather
                        // than drawing two chips that would open empty pages.
                        showViewerSegments = isOwnProfile &&
                            (state.isDemoMode || viewerId != null),
                        // The route's request, which is [SEGMENT_ACTIVITY] for
                        // every way in that did not name one. Only meaningful
                        // while the segments exist at all - a profile that hides
                        // them cannot be showing one, which `ProfileActivityPage`
                        // already enforces.
                        initialSegment = initialSegment,
                        calendar = state.activity,
                        userName = state.username,
                        userAvatar = state.avatarUrl,
                        onOpenUser = onOpenUser,
                        onOpenDetail = onOpenDetail,
                        contentPadding = padding,
                        activityListState = activityListState,
                        followingListState = followingListState,
                        messagesListState = messagesListState
                    )

                    2 -> ProfileStatsTab(
                        state = state,
                        contentPadding = padding,
                        section = statsSection,
                        onSelectSection = { statsSection = it },
                        listState = statsListState
                    )

                    3 -> ProfileSocialTab(
                        socialState = socialState,
                        onSelectList = { list ->
                            socialViewModel.select(list)
                        },
                        // The failure state's button: the reader is asking again, so
                        // this one does mean a fresh pair of requests.
                        onLoad = { list ->
                            socialViewModel.load(list, forceRefresh = true)
                        },
                        // And the tab's own entry trigger, which spends nothing when
                        // rows are on screen or a fetch is already producing them.
                        onEnsureLoaded = { list ->
                            socialViewModel.ensureLoaded(list)
                        },
                        onOpenUser = onOpenUser,
                        contentPadding = padding,
                        gridState = socialGridState
                    )

                    BORROWED_LIST_TAB -> ProfileBorrowedListTab(
                        state = state,
                        overviewViewModel = overviewViewModel,
                        onOpenDetail = onOpenDetail,
                        onRefresh = { overviewViewModel.refresh() },
                        contentPadding = padding,
                        gridState = listGridState
                    )

                    else -> ProfileHomeTab(
                        state = state,
                        contentPadding = padding,
                        onOpenDetail = onOpenDetail,
                        onOpenStudio = onOpenStudio,
                        onOpenCharacter = onOpenCharacter,
                        onOpenStaff = onOpenStaff,
                        listState = homeListState,
                        mediaCovers = mediaCovers
                    )
                }
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
