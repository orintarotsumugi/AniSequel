package com.anisequel.ui.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.detailFor
import com.anisequel.data.model.withSummaryProgress
import com.anisequel.data.repository.ProfileLayoutPreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.StateCrossfade
import com.anisequel.ui.components.detail.EntryEditor
import com.anisequel.ui.components.detail.SequelDetailSheet
import com.anisequel.ui.viewmodel.MediaDetailHost
import com.anisequel.ui.viewmodel.rememberPersonProfileHost
import com.anisequel.ui.viewmodel.rememberPlanningAddHost
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.viewmodel.toMediaNode
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.openExternalUrl
import com.anisequel.ui.navigation.MainBarClearance
import com.anisequel.ui.viewmodel.UserActivityViewModel
import com.anisequel.ui.viewmodel.UserMessagesViewModel
import com.anisequel.ui.viewmodel.UserOverviewUiState
import com.anisequel.ui.viewmodel.UserOverviewViewModel
import com.anisequel.ui.viewmodel.UserSocialViewModel

/**
 * Whether the app's main bar floats over this profile's lists.
 *
 * A `CompositionLocal` rather than a parameter: the flag is a property of this
 * screen's bottom edge, and a parameter would have to travel through
 * `ProfileTabsContent` and every tab's signature to reach the one place that uses
 * it - `profileListPadding` - where the next tab somebody adds would have to
 * re-pass it. Provided by [UserProfileScreen] from `isOwnProfile`, which is true
 * only on the route the bar draws over.
 */
private val LocalMainBarOverProfile = staticCompositionLocalOf { false }

/**
 * The padding every tab's scrollable content uses.
 *
 * The bottom is *not* the Scaffold's. The app's main bar is meant to float over
 * the content rather than push it, so the Scaffold's bottom inset is replaced
 * with the bar's clearance - but only on the profile the bar draws as a tab, the
 * viewer's own. A followed or scanned profile is pushed from one of the bar's
 * pages, and the bar is hidden there, so its lists would otherwise leave a strip
 * of nothing under them for a bar that is not on screen.
 */
@Composable
internal fun profileListPadding(contentPadding: PaddingValues = PaddingValues()): PaddingValues {
    val mainBar = if (LocalMainBarOverProfile.current) MainBarClearance else 0.dp
    // Read from the device at draw time rather than assumed, for the reason
    // [MainBarClearance] gives: a gesture bar, a three-button bar and a cutout
    // are three different heights, and hardcoding the largest of them either
    // wastes space or leaves the last row of a list sitting under the bar. That
    // mismatch is also what produces the "bounce back" snap when the list reaches
    // its end and the padding disagrees with where the bar really is.
    val systemInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(
        top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = mainBar + systemInset
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    /**
     * The person whose profile sheet is opened from the cast and crew rows.
     *
     * Passed rather than reached for on a ViewModel: `UserOverviewViewModel` holds its
     * repository privately, and the cast's tap is a *read* that belongs to whoever
     * drew the sheet rather than to the profile loader - which is why this is a
     * parameter and not a field on that ViewModel.
     */
    repository: com.anisequel.data.repository.AniListRepository,
    /**
     * The app's one on-demand detail store.
     *
     * Passed in rather than reached for on [activityViewModel], which is where this
     * screen used to get it. That store was one of four - the dashboard, the
     * activity feed, the studio page and the Library each held their own - so the
     * same title opened from two tabs was two fetches, two caches and, in the
     * profile's case, a merge that could disagree with the Library's. One host for
     * the app is what makes a title the same page wherever the tap came from; the
     * dashboard keeps its own only because its store also folds the answer into
     * the recommendation cards behind the sheet.
     */
    detailHost: MediaDetailHost,
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
    /**
     * The name this profile was opened with, when the route carried one.
     *
     * Only a label for the first frames: the bar prints it before the overview
     * answers, and the overview's own name replaces it. It used to have no source at
     * all on the by-id route, so tapping a face in the Social grid opened a page
     * titled "Profile" - the name was in the row that was just tapped and never
     * travelled with the tap.
     */
    titleHint: String? = null,
    overviewViewModel: UserOverviewViewModel,
    activityViewModel: UserActivityViewModel,
    /**
     * The viewer's friends feed, shown as the Activity tab's second segment.
     *
     * A `UserActivityViewModel` in `followingOnly` mode rather than a class of
     * its own: it is the same feed - paging, refresh, likes, comments - with the
     * rows coming from everyone the viewer follows instead of one person.
     */
    followingViewModel: UserActivityViewModel,
    /** The viewer's sent and received messages, shown as the third segment. */
    messagesViewModel: UserMessagesViewModel,
    socialViewModel: UserSocialViewModel,
    onOpenUser: (FollowUser) -> Unit,
    onNavigateBack: () -> Unit,
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
     * The signed-in viewer's id, for deciding which comments are the reader's own.
     *
     * A comment's author and the profile's owner are two different questions: the
     * thread hangs off somebody else's profile, and a reader can comment in a
     * stranger's. Null - signed out, or a caller that did not know it - leaves
     * every comment without its manage control, which the thread still renders and
     * reads from, so the safe half costs nothing but the menu.
     */
    viewerId: Int? = null,
    /**
     * Read/write access to the *viewer's* entry for the opened title, or null
     * when there is no session to write with.
     *
     * Note whose entry it is: the tab holds the reader's own list entry for the
     * media, not the profile owner's, so opening a stranger's favourites tab
     * offers the same "My Entry" the dashboard would for that title. That is what
     * makes the sheet identical everywhere it opens; a profile route with no
     * viewer id (signed out, demo) passes null and the tab is not drawn.
     */
    entryEditor: EntryEditor? = null,
    /**
     * Opens the tier list for this library.
     *
     * A callback rather than a navigation call so the screen keeps no reference to
     * the `NavController`, which is the same split `ProfileDestination` already
     * makes: every route decision lives in one place, and the screen only says
     * what it wants to happen.
     */
    onOpenTierList: () -> Unit = {},
    /**
     * Opens a studio's page, from a pinned studio chip on the Home tab as well as from
     * the credits on a title the sheet is showing.
     *
     * One parameter for both, deliberately: they want the same thing, and two
     * parameters would be two definitions of "open this studio" on one screen that
     * could drift into opening different things. A callback for the same reason
     * [onOpenTierList] is - the screen holds no `NavController`. Where the sheet's own
     * close has to happen first, so the studio page is not covered by a window that
     * belongs to this one, that is done at the call site rather than here.
     */
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
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
    /**
     * The Activity tab's opening segment.
     *
     * The same rule as [initialTab] and for the same reason: it is what the first
     * composition opens, not a standing instruction. A profile reached from the
     * main bar's held Profile tab starts on the inbox; after that the reader's own
     * taps own the choice, and a rotation restores wherever they left it.
     */
    initialSegment: Int = SEGMENT_ACTIVITY,
    modifier: Modifier = Modifier
) {
    val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()

    // The activity feed's state is deliberately *not* collected here. It is read
    // by `ProfileActivityPage`, which is composed only while the Activity tab is
    // open; reading it here made every page of the feed - and every like - redraw
    // the top bar, the tab capsule and the whole Home tab behind them.
    val socialState by socialViewModel.state.collectAsStateWithLifecycle()
    // Covers for the bio's AniList links. Arrive after the profile does, so the
    // bio renders immediately and posters fill in underneath the reader.
    val mediaCovers by overviewViewModel.mediaCovers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The viewer's own arrangement of the Home tab. The editor writes; the page
    // behind it reads the same flow, so a toggle lands without a hand-off state.
    val layoutPreferences = remember(context) { ProfileLayoutPreferences(context) }
    val profileLayout by layoutPreferences.layout.collectAsState(
        initial = com.anisequel.domain.profile.ProfileLayout()
    )
    var layoutEditorOpen by rememberSaveable { mutableStateOf(false) }
    val layoutScope = rememberCoroutineScope()
    val detailNodes by detailHost.details.collectAsStateWithLifecycle()
    val loadingDetailIds by detailHost.loading.collectAsStateWithLifecycle()
    val detailFriends by detailHost.friends.collectAsStateWithLifecycle()
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
        AnimeDetailSubject.Media(detailNodes.detailFor(summary)?.withSummaryProgress(summary) ?: summary)
    }
    val detailLoading = selectedMedia?.id?.let { it in loadingDetailIds } == true

    // The person whose profile sheet is opened from the cast and crew rows, and from
    // the pinned characters and staff on the Home tab.
    //
    // Passed in rather than reached for on a ViewModel: `UserOverviewViewModel` holds
    // its repository privately, and a cast tap is a *read* belonging to whoever drew
    // the sheet rather than to the profile loader. Declared above the `Scaffold`
    // rather than beside the sheet it draws, because the Home tab needs the two
    // callbacks and it is composed *inside* the Scaffold's content.
    val profiles = rememberPersonProfileHost(repository)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // "Profile" until the name is known, then the name.
                        //
                        // It used to fall back to "AniList", which read as a data
                        // value - the account's name - rather than as the page's
                        // title, and made every scanned profile look like the
                        // official account for the length of its load. The
                        // crossfade is short and on the effects spring: the title
                        // is in the reader's peripheral vision while they read the
                        // page, and a hard swap there reads as a flicker.
                        AnimatedContent(
                            targetState = (uiState as? UserOverviewUiState.Success)
                                ?.username
                                ?.let { "@$it" }
                                // The name the route came with, before the overview
                                // answers. Falling back to "Profile" here was the
                                // whole of the by-id route's load: a follower's
                                // profile titled "Profile" for a round trip, with the
                                // name one tap away in the grid behind it.
                                ?: titleHint?.let { "@$it" }
                                ?: "Profile",
                            transitionSpec = {
                                fadeIn(animationSpec = ExpressiveMotion.FastEffects) togetherWith
                                    fadeOut(animationSpec = ExpressiveMotion.FastEffects)
                            },
                            label = "profile_title"
                        ) { title ->
                            Text(
                                text = title,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
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
                    // The page had a refresh bar but no way to start a refresh:
                    // `UserOverviewViewModel.refresh()` was reachable only from
                    // code that nothing called, so the bar was decoration for a
                    // state the reader could not enter. The demo has nothing to
                    // re-fetch, so the control is left out there rather than
                    // drawn as a button that does nothing.
                    //
                    // The feed joins in only when its tab has been opened: this is
                    // a page-level control, and fetching a feed nobody has looked at
                    // is the lazy-loading rule the Activity tab is built around.
                    if ((uiState as? UserOverviewUiState.Success)?.isDemoMode == false) {
                        IconButton(
                            onClick = {
                                overviewViewModel.refresh()
                                activityViewModel.refreshIfStarted()
                                // The two viewer feeds refresh only if their
                                // segment has been opened: a page-level refresh
                                // must not spend requests on an inbox nobody has
                                // looked at.
                                followingViewModel.refreshIfStarted()
                                messagesViewModel.refreshIfStarted()
                            },
                            modifier = Modifier
                                .testTag("profile_refresh_button")
                                .bouncyPress(pressedScale = 0.9f)
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.Refresh,
                                contentDescription = "Refresh profile"
                            )
                        }
                    }

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
                // because the app's main bar floats over the content rather than
                // pushing it, and its clearance is handled by
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
            StateCrossfade(
                targetState = uiState,
                modifier = Modifier.fillMaxSize(),
                label = "profile_state",
            ) { state ->
                when (state) {
                    is UserOverviewUiState.Loading -> ProfileLoadingView(message = state.message)
                    is UserOverviewUiState.Error -> {
                        // The calm view, not the dashboard's failure theatre: this is a
                        // tab of the app's main bar, not a screen the reader opened to
                        // find out why nothing loaded. See [ProfileErrorView].
                        ProfileErrorView(
                            message = state.message,
                            kind = state.kind,
                            canRetry = state.canRetry,
                            isAuthError = state.isAuthError,
                            onRetry = { overviewViewModel.load() },
                            onSignInAgain = onSignInAgain
                        )
                    }
                    is UserOverviewUiState.Success -> {
                        LaunchedEffect(state.userId) {
                            if (state.userId > 0) {
                                activityViewModel.updateUserId(state.userId)
                                socialViewModel.updateUserId(state.userId)
                                // And this screen's own ViewModel, which needs it for
                                // one half only: `getUserMangaList` is addressable by
                                // id, so a profile opened *by name* could otherwise
                                // never read its manga - the id had arrived with this
                                // very state and been thrown away. See
                                // `UserOverviewViewModel.updateUserId`.
                                overviewViewModel.updateUserId(state.userId)
                            }
                        }
                        // The flag the tabs' list padding reads. Provided here rather
                        // than threaded through, because only this route is drawn over
                        // by the app's main bar; see [LocalMainBarOverProfile].
                        CompositionLocalProvider(LocalMainBarOverProfile provides isOwnProfile) {
                            ProfileTabsContent(
                                state = state,
                                selectedTab = selectedTab,
                                onSelectTab = { selectedTab = it },
                                initialSegment = initialSegment,
                                overviewViewModel = overviewViewModel,
                                isOwnProfile = isOwnProfile,
                                activityViewModel = activityViewModel,
                                followingViewModel = followingViewModel,
                                messagesViewModel = messagesViewModel,
                                // The viewer's id, which decides whether the two
                                // viewer-only segments can be offered at all.
                                viewerId = viewerId,
                                socialState = socialState,
                                socialViewModel = socialViewModel,
                                mediaCovers = mediaCovers,
                                onOpenUser = onOpenUser,
                                onOpenDetail = { selectedMedia = it },
                                onOpenStudio = onOpenStudio,
                                // The pinned people rows open the same in-app profile
                                // sheet the cast rows do; null falls back to the
                                // AniList page.
                                onOpenCharacter = profiles.onOpenCharacter,
                                onOpenStaff = profiles.onOpenStaff,
                                // Only the viewer's own page is theirs to arrange;
                                // somebody else's draws the shipped layout.
                                profileLayout = if (isOwnProfile) {
                                    profileLayout
                                } else {
                                    com.anisequel.domain.profile.ProfileLayout()
                                },
                                onEditProfileLayout = if (isOwnProfile) {
                                    { layoutEditorOpen = true }
                                } else {
                                    null
                                }
                            )
                        }

                        if (layoutEditorOpen) {
                            ProfileLayoutSheet(
                                layout = profileLayout,
                                onMove = { section, direction ->
                                    layoutScope.launch {
                                        val moved = profileLayout.move(section, direction)
                                        layoutPreferences.setOrder(moved.resolvedOrder)
                                    }
                                },
                                onToggle = { section ->
                                    layoutScope.launch {
                                        layoutPreferences.setHidden(
                                            profileLayout.toggle(section).hidden
                                        )
                                    }
                                },
                                onCharactersTwoColumn = { twoColumn ->
                                    layoutScope.launch {
                                        layoutPreferences.setCharactersTwoColumn(twoColumn)
                                    }
                                },
                                onReset = {
                                    layoutScope.launch { layoutPreferences.reset() }
                                },
                                onDismiss = { layoutEditorOpen = false }
                            )
                        }
                    }
                }
            }
        }
    }

    // One-tap Planning for a title the sheet is showing - an activity row, a
    // favourite, or a relation walked to from either. Gated on the same editor
    // the "My Entry" tab uses, so a profile opened without a session (the demo,
    // or a signed-out route) says "sign in" instead of drawing a write control.
    val planningAdd = rememberPlanningAddHost(
        repository = repository,
        canWrite = entryEditor != null
    )
    val quickAddStatuses by planningAdd.statuses.collectAsStateWithLifecycle()

    // The activity row's detail sheet. A plain media subject: an activity entry
    // is a show the person already tracks, so it has no parent - the same
    // distinction [com.anisequel.data.model.ArrivingEntry] draws.
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
            quickAddStatuses = quickAddStatuses,
            onAddMediaToPlanning = planningAdd.onAddToPlanning,
            onAddMediaToWatching = planningAdd.onAddToWatching,
            onLoadDetail = detailHost.onLoadDetail,
            onLoadFriends = detailHost.onLoadFriends,
            friends = detailFriends[subject.mediaId].orEmpty(),
            isDetailLoading = detailLoading,
            // Same sheet, same access as every other host: the tab follows the
            // editor, and with no session the page is exactly what it was.
            canWriteToAniList = entryEditor != null,
            entryEditor = entryEditor,
            onOpenStudio = { id, name ->
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

    // Both sheets are siblings of the tab content rather than children of a tab, for
    // the same reason: `AnimatedContent` composes the incoming tab alongside the
    // outgoing one and then *disposes* the outgoing one, and a `ModalBottomSheet` is a
    // window of its own - so one drawn from inside a tab is torn down with it. Open a
    // thread, tap another tab, and the sheet went with it - draft included.
    //
    // They are also drawn by their own composable, which is where their state is
    // collected. Both flows used to be read here, at the top of the screen, so every
    // comment like and every liker that arrived invalidated the screen - the header,
    // the tab bar and the whole activity feed behind the sheet - to redraw a sheet
    // that was already on top of them. The subscription is still active for as long
    // as this screen is, because the child is composed unconditionally.
    ActivityEngagementSheets(
        activityViewModel = activityViewModel,
        viewerId = viewerId,
        // A comment is a write, and only a signed-in reader can write one. This
        // used to test `isDemoMode` alone, so a signed-out visitor on a public
        // profile (which is not the demo) was shown a composer whose every
        // submission could only fail with an auth error. The demo is covered too:
        // it has no viewer id.
        canPost = viewerId != null
    )

    // The friends feed's own sheets, from its own ViewModel. They exist as a
    // second call rather than a shared one because the two feeds page and fail
    // independently and each ViewModel owns one open thread - sharing the sheet
    // state would have the profile feed's comment thread open over a card in the
    // friends feed.
    ActivityEngagementSheets(
        activityViewModel = followingViewModel,
        viewerId = viewerId,
        canPost = viewerId != null
    )

    // The inbox's message sheet, on the same terms as the two above: a sibling of
    // the tab content rather than a child of the tab that opened it, and reading
    // the row the ViewModel is holding rather than asking anybody for it.
    MessagesDetailSheetHost(
        messagesViewModel = messagesViewModel,
        onOpenUser = onOpenUser
    )
}
