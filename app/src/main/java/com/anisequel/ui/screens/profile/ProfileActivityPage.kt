package com.anisequel.ui.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.repository.CommentMediaSettings
import com.anisequel.data.repository.MediaPreferences
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MediaNode
import com.anisequel.domain.usecase.ActivityCalendar
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.viewmodel.UserActivityViewModel
import com.anisequel.ui.viewmodel.UserMessagesViewModel

/**
 * The Activity tab's three views: the person's own history, the viewer's
 * friends, and the viewer's messages.
 *
 * ## Why a segment row rather than more profile tabs
 *
 * The friends feed and the inbox are about the *viewer*, not about the profile
 * being read, so they cannot be tabs of it: a follower's page would offer the
 * reader's own friends feed under that person's name. They are also the same
 * kind of thing as the feed already on this tab - a list of AniList activities -
 * so they belong beside it rather than on a page of their own. One chip row at
 * the top of the tab says which list is on screen, and the segment only exists
 * on the viewer's own profile (see `showViewerSegments`).
 *
 * ## Owned here, not by the screen
 *
 * The two extra ViewModels are collected inside this composable so a like, a
 * page or a filter only recomposes the Activity page. `UserProfileScreen` is
 * already the largest file in the profile package; passing twenty callbacks
 * through it to reach here would have grown both the call site and the file for
 * no reader's benefit.
 *
 * ## One list state per segment
 *
 * Each segment keeps its own scroll position, hoisted by the screen and passed
 * through. Sharing one would drop the reader halfway down the friends feed when
 * they switched back to their own history - the two lists have unrelated
 * lengths and nothing in common but their card shape. The site-wide stream is
 * hoisted the same way: it used to create its `LazyListState` inside the
 * `AnimatedContent` branch, which is exactly the mistake described in
 * `ProfileTabsContent`, and every switch away from Global put the reader back at
 * the top of a stream they had scrolled.
 *
 * ## The segment can be asked for by name
 *
 * [initialSegment] is how a route reaches this page's state without the reader
 * tapping: it is how the main bar's held Profile tab offers the viewer's own
 * inbox. It is an *initial* value and nothing more - see the note on the saved
 * state below for why re-applying it after the first composition would be the
 * worse rule.
 */
@Composable
fun ProfileActivityPage(
    /** The profile's own feed, loaded by the screen's Activity ViewModel. */
    activityViewModel: UserActivityViewModel,
    /** The viewer's friends feed. Only meaningful when [showViewerSegments]. */
    followingViewModel: UserActivityViewModel,
    /** The site-wide stream, offered as a fourth chip when the reader turns it on. */
    globalViewModel: UserActivityViewModel,
    /** The viewer's inbox: sent and received, filtered. */
    messagesViewModel: UserMessagesViewModel,
    /**
     * Whether the Friends and Messages chips are offered at all.
     *
     * True only on the viewer's own profile: both segments read the *reader's*
     * account, and drawing them on a follower's page would either show that
     * person's strangers' rows under the wrong name or an empty feed whose empty
     * state explains nothing. The demo qualifies too - it has fixtures for both.
     */
    showViewerSegments: Boolean,
    /**
     * The segment this page opens on.
     *
     * [SEGMENT_ACTIVITY] for every way in that does not name one, which is what
     * leaves the reader's own last choice in charge - this only ever decides the
     * *first* frame. The app's main bar asks for [SEGMENT_MESSAGES] through the
     * profile route's `segment` argument so the inbox opens without a tap, and
     * seeding the saved state here rather than writing to it after composition is
     * the difference between opening on Messages and flashing Activity first.
     */
    initialSegment: Int = SEGMENT_ACTIVITY,
    calendar: ActivityCalendar?,
    userName: String?,
    userAvatar: String?,
    onOpenUser: (FollowUser) -> Unit,
    onOpenDetail: (MediaNode) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    activityListState: LazyListState = rememberLazyListState(),
    followingListState: LazyListState = rememberLazyListState(),
    globalListState: LazyListState = rememberLazyListState(),
    messagesListState: LazyListState = rememberLazyListState()
) {
    val activityState by activityViewModel.state.collectAsStateWithLifecycle()
    val followingState by followingViewModel.state.collectAsStateWithLifecycle()
    val globalState by globalViewModel.state.collectAsStateWithLifecycle()
    val messagesState by messagesViewModel.state.collectAsStateWithLifecycle()

    // Chat view is the reader's call, and it is read at the door a thread is opened
    // through rather than inside the store: a thread that began as a window stays
    // one however the switch moves afterwards, and the store has no business
    // knowing where a preference lives. The application context is what the
    // DataStore delegate is keyed on, so a second screen asking for the same file
    // gets the same instance rather than a crash.
    val context = LocalContext.current
    val mediaPreferences = remember(context) { MediaPreferences(context.applicationContext) }
    val mediaSettings by mediaPreferences.settings.collectAsStateWithLifecycle(
        initialValue = CommentMediaSettings()
    )
    val chatView = mediaSettings.chatView
    val showGlobalSegment = mediaSettings.globalActivityFeed

    // Saved, because the page is disposed and recreated by the profile's own tab
    // switch: a reader who left the inbox on "Sent" and came back should not be
    // handed "All". Not keyed on the profile, deliberately - `ProfileActivityPage`
    // is recomposed per profile anyway, and on a profile that hides the segments
    // the remembered index is never read.
    //
    // [initialSegment] is the *initial value* only, which is the same rule the
    // profile's tab follows (see `UserProfileScreen`): a route that names a
    // segment asks where to open, it does not outrank the choice the reader made
    // here afterwards. Applying it on every recomposition instead would both
    // fight the chips and flash the wrong feed on the way in.
    var segment by rememberSaveable { mutableIntStateOf(initialSegment) }
    // A profile that does not offer the segments can never be showing one. The
    // saved value is kept rather than reset, so returning to an own profile
    // restores the last choice.
    // Turning the setting off while the stream is open cannot leave a chip on
    // screen that no longer exists; the reader goes back to their own feed rather
    // than to a blank frame under a segment that cannot be selected.
    val shownSegment = when {
        !showViewerSegments -> SEGMENT_ACTIVITY
        segment == SEGMENT_GLOBAL && !showGlobalSegment -> SEGMENT_ACTIVITY
        else -> segment
    }

    // The two feeds have the same identity except for whose rows they are and
    // what the heading calls them. Built here rather than as fields on the
    // screen's state, because every one of them closes over a ViewModel.
    val ownFeed = ActivityFeedSource(
        state = activityState,
        title = "Recent Activity",
        icon = AppVectorIcons.ProfileActivity,
        emptyMessage = "No list activity to show",
        onLoadFirstPage = { activityViewModel.loadFirstPage() },
        onLoadNextPage = { activityViewModel.loadNextPage() },
        onRetry = { activityViewModel.retry() },
        onRefresh = { activityViewModel.refresh() },
        onLike = { activityViewModel.toggleLike(it) },
        onOpenComments = { id, count -> activityViewModel.openComments(id, count, chatView) },
        onOpenLikers = { activityViewModel.openLikers(it) },
        calendar = calendar,
        userName = userName,
        userAvatar = userAvatar,
        // The compose action is the viewer's own, and only on their own page:
        // `showViewerSegments` is exactly "this is the signed-in reader's profile",
        // and `canCompose` is the half that also refuses a session-less or demo
        // route. A post written from a friend's page would file the reader's words
        // under that page's heading.
        onCompose = if (showViewerSegments && activityViewModel.canCompose) {
            { activityViewModel.openComposer() }
        } else {
            null
        }
    )
    val globalFeed = ActivityFeedSource(
        state = globalState,
        title = "Everything on AniList",
        icon = AppVectorIcons.ProfileSocial,
        emptyMessage = "No public activity to show",
        onLoadFirstPage = { globalViewModel.loadFirstPage() },
        onLoadNextPage = { globalViewModel.loadNextPage() },
        onRetry = { globalViewModel.retry() },
        onRefresh = { globalViewModel.refresh() },
        onLike = { globalViewModel.toggleLike(it) },
        onOpenComments = { id, count -> globalViewModel.openComments(id, count, chatView) },
        onOpenLikers = { globalViewModel.openLikers(it) },
        // Every row is a different person, and each card's author row opens their
        // profile - the same hop the friends feed and the Social tab make.
        onOpenAuthor = onOpenUser,
        // The same rows carry a way to write to that person: the sheet is the
        // inbox's own conversation view, opened on a name instead of a message.
        onMessageAuthor = { author -> messagesViewModel.openConversation(author) }
    )
    val friendsFeed = ActivityFeedSource(
        state = followingState,
        title = "Friends Activity",
        icon = AppVectorIcons.ProfileSocial,
        emptyMessage = "No activity from the people you follow",
        onLoadFirstPage = { followingViewModel.loadFirstPage() },
        onLoadNextPage = { followingViewModel.loadNextPage() },
        onRetry = { followingViewModel.retry() },
        onRefresh = { followingViewModel.refresh() },
        onLike = { followingViewModel.toggleLike(it) },
        onOpenComments = { id, count -> followingViewModel.openComments(id, count, chatView) },
        onOpenLikers = { followingViewModel.openLikers(it) },
        // Every row is a different person in this feed, and each card's
        // author row opens their profile - the same hop the Social tab makes.
        onOpenAuthor = onOpenUser,
        onMessageAuthor = { author -> messagesViewModel.openConversation(author) }
    )

    Column(modifier = modifier.fillMaxSize()) {
        if (showViewerSegments) {
            ActivitySegments(
                selected = shownSegment,
                onSelect = { segment = it },
                showGlobalSegment = showGlobalSegment,
                modifier = Modifier
                    .widthIn(max = ProfileMaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        AnimatedContent(
            targetState = shownSegment,
            transitionSpec = {
                val forward = targetState > initialState
                // The same short slide-and-fade the profile's tabs use, on the
                // same tokens: these are two views of one page, so they should
                // travel like the page's own switches rather than like a new
                // destination being pushed. `using null` drops the default height
                // spring, which would re-measure both feeds on every frame.
                val enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    slideInHorizontally(
                        animationSpec = ExpressiveMotion.FastSpatialOffset,
                        initialOffsetX = { width -> if (forward) width / 8 else -width / 8 }
                    )
                val exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    slideOutHorizontally(
                        animationSpec = ExpressiveMotion.FastSpatialOffset,
                        targetOffsetX = { width -> if (forward) -width / 8 else width / 8 }
                    )
                enter togetherWith exit using null
            },
            label = "profile_activity_segment",
            modifier = Modifier.fillMaxSize()
        ) { which ->
            when (which) {
                SEGMENT_FRIENDS -> ProfileActivityTab(
                    source = friendsFeed,
                    contentPadding = contentPadding,
                    listState = followingListState,
                    viewerId = messagesState.viewerId,
                    onOpenDetail = onOpenDetail
                )

                SEGMENT_MESSAGES -> ProfileMessagesTab(
                    state = messagesState,
                    onSelectFilter = { messagesViewModel.selectFilter(it) },
                    onLoadFirstPage = { messagesViewModel.loadFirstPages() },
                    onLoadMore = { messagesViewModel.loadNextPage() },
                    onRetry = { messagesViewModel.retry() },
                    onOpenUser = onOpenUser,
                    // The row travels to the ViewModel, which is what the detail
                    // sheet is drawn from - see `MessageDetailSheet` for why that
                    // means the sheet opens on the tap's own frame.
                    onOpenMessage = { message -> messagesViewModel.openMessage(message) },
                    // The same sheet the Activity cards open, aimed at the message's
                    // own activity id - see `UserMessagesViewModel.comments`.
                    onOpenComments = { message -> messagesViewModel.openComments(message) },
                    contentPadding = contentPadding,
                    listState = messagesListState
                )

                SEGMENT_GLOBAL -> ProfileActivityTab(
                    source = globalFeed,
                    contentPadding = contentPadding,
                    listState = globalListState,
                    viewerId = messagesState.viewerId,
                    onOpenDetail = onOpenDetail
                )

                else -> ProfileActivityTab(
                    source = ownFeed,
                    contentPadding = contentPadding,
                    listState = activityListState,
                    viewerId = messagesState.viewerId,
                    onOpenDetail = onOpenDetail
                )
            }
        }
    }
}

// `internal`, not private: the profile route has to name one of these to open the
// inbox from a shortcut, and a second copy of the numbering in `ui/navigation` is
// how the two drift - silently, because nothing fails when an index means a
// different chip than the one the route said.
internal const val SEGMENT_ACTIVITY = 0
internal const val SEGMENT_FRIENDS = 1
internal const val SEGMENT_MESSAGES = 2

/**
 * The site-wide stream. Numbered past [SEGMENT_MESSAGES] rather than inserted at
 * two, because the profile route names these indices in its URL and a renumbered
 * segment would silently open a different chip than the link asked for.
 */
internal const val SEGMENT_GLOBAL = 3

/**
 * The chip row that switches between the tab's views.
 *
 * Labels, not icons: the segment's job is to name a list, and "Messages" is the
 * word a reader would think of. The mailbox and the group glyph are still carried
 * by the feed headings below, where they have room to be read as decoration
 * rather than as the only label on a control.
 *
 * ## Why the row scrolls instead of dividing the width
 *
 * The chips used to carry `Modifier.weight(1f)` each, which looked right as a
 * segmented control and stopped being right the moment there were four of them:
 * on a 411dp phone each got about 88dp, minus 28dp of chip padding, leaving
 * roughly 60dp for a label. "Messages" is wider than that at the default text
 * size, and the shared chip's `Text` has no line limit, so turning Global on
 * wrapped the longest labels onto a second line - the capsules grew to different
 * heights and the row read as broken text rather than as a control. A weight
 * cannot both fill the width and guarantee room for the text, so the chips are
 * sized by their labels and the row scrolls when their sum is wider than the
 * screen, which is the pattern AniList's own profile uses. Three chips fit with
 * room to spare and sit at the row's start.
 */
@Composable
private fun ActivitySegments(
    selected: Int,
    onSelect: (Int) -> Unit,
    showGlobalSegment: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegmentChip(
            label = "Activity",
            selected = selected == SEGMENT_ACTIVITY,
            onClick = { onSelect(SEGMENT_ACTIVITY) },
            testTag = "profile_segment_activity"
        )
        SegmentChip(
            label = "Friends",
            selected = selected == SEGMENT_FRIENDS,
            onClick = { onSelect(SEGMENT_FRIENDS) },
            testTag = "profile_segment_friends"
        )
        SegmentChip(
            label = "Messages",
            selected = selected == SEGMENT_MESSAGES,
            onClick = { onSelect(SEGMENT_MESSAGES) },
            testTag = "profile_segment_messages"
        )
        // Global comes last, and after Messages on purpose. It used to sit
        // between Friends and Messages, which on a 411dp phone pushed Messages -
        // the segment every reader has - past the right edge of a row that scrolls
        // without saying so. Turning the opt-in stream on then made the inbox look
        // like it had vanished, which is the report this order fixes. Ascending
        // indices still match the visual order, so the feed transition's
        // forward/backward direction stays truthful.
        AnimatedVisibility(
            visible = showGlobalSegment,
            enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                expandHorizontally(animationSpec = ExpressiveMotion.FastSpatialSize),
            exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                shrinkHorizontally(animationSpec = ExpressiveMotion.FastSpatialSize)
        ) {
            SegmentChip(
                label = "Global",
                selected = selected == SEGMENT_GLOBAL,
                onClick = { onSelect(SEGMENT_GLOBAL) },
                testTag = "profile_segment_global"
            )
        }
    }
}

@Composable
private fun SegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    ExpressiveStateChip(
        label = label,
        selected = selected,
        onClick = onClick,
        // The tab switch above this is a 44dp-tall capsule. A row of chips half
        // its height underneath would be the smallest targets on the page, and
        // they are the only way into the tab's other views.
        minTouchTargetHeight = 44.dp,
        modifier = modifier.testTag(testTag)
    )
}
