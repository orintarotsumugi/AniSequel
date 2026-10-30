package com.anisequel.ui.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * lengths and nothing in common but their card shape.
 */
@Composable
fun ProfileActivityPage(
    /** The profile's own feed, loaded by the screen's Activity ViewModel. */
    activityViewModel: UserActivityViewModel,
    /** The viewer's friends feed. Only meaningful when [showViewerSegments]. */
    followingViewModel: UserActivityViewModel,
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
    calendar: ActivityCalendar?,
    userName: String?,
    userAvatar: String?,
    onOpenUser: (FollowUser) -> Unit,
    onOpenDetail: (MediaNode) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    activityListState: LazyListState = rememberLazyListState(),
    followingListState: LazyListState = rememberLazyListState(),
    messagesListState: LazyListState = rememberLazyListState()
) {
    val activityState by activityViewModel.state.collectAsStateWithLifecycle()
    val followingState by followingViewModel.state.collectAsStateWithLifecycle()
    val messagesState by messagesViewModel.state.collectAsStateWithLifecycle()

    // Saved, because the page is disposed and recreated by the profile's own tab
    // switch: a reader who left the inbox on "Sent" and came back should not be
    // handed "All". Not keyed on the profile, deliberately - `ProfileActivityPage`
    // is recomposed per profile anyway, and on a profile that hides the segments
    // the remembered index is never read.
    var segment by rememberSaveable { mutableIntStateOf(SEGMENT_ACTIVITY) }
    // A profile that does not offer the segments can never be showing one. The
    // saved value is kept rather than reset, so returning to an own profile
    // restores the last choice.
    val shownSegment = if (showViewerSegments) segment else SEGMENT_ACTIVITY

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
        onOpenComments = { activityViewModel.openComments(it) },
        onOpenLikers = { activityViewModel.openLikers(it) },
        calendar = calendar,
        userName = userName,
        userAvatar = userAvatar
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
        onOpenComments = { followingViewModel.openComments(it) },
        onOpenLikers = { followingViewModel.openLikers(it) },
        // Every row is a different person in this feed, and each card's
        // author row opens their profile - the same hop the Social tab makes.
        onOpenAuthor = onOpenUser
    )

    Column(modifier = modifier.fillMaxSize()) {
        if (showViewerSegments) {
            ActivitySegments(
                selected = shownSegment,
                onSelect = { segment = it },
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
                (
                    fadeIn(animationSpec = ExpressiveMotion.FastEffects) + slideInHorizontally(
                        animationSpec = ExpressiveMotion.FastSpatialOffset,
                        initialOffsetX = { width -> if (forward) width / 8 else -width / 8 }
                    )
                    ) togetherWith (
                    fadeOut(animationSpec = ExpressiveMotion.FastEffects) + slideOutHorizontally(
                        animationSpec = ExpressiveMotion.FastSpatialOffset,
                        targetOffsetX = { width -> if (forward) -width / 8 else width / 8 }
                    )
                    ) using null
            },
            label = "profile_activity_segment",
            modifier = Modifier.fillMaxSize()
        ) { which ->
            when (which) {
                SEGMENT_FRIENDS -> ProfileActivityTab(
                    source = friendsFeed,
                    contentPadding = contentPadding,
                    listState = followingListState,
                    onOpenDetail = onOpenDetail
                )

                SEGMENT_MESSAGES -> ProfileMessagesTab(
                    state = messagesState,
                    onSelectFilter = { messagesViewModel.selectFilter(it) },
                    onLoadFirstPage = { messagesViewModel.loadFirstPages() },
                    onLoadMore = { messagesViewModel.loadNextPage() },
                    onRetry = { messagesViewModel.retry() },
                    onOpenUser = onOpenUser,
                    contentPadding = contentPadding,
                    listState = messagesListState
                )

                else -> ProfileActivityTab(
                    source = ownFeed,
                    contentPadding = contentPadding,
                    listState = activityListState,
                    onOpenDetail = onOpenDetail
                )
            }
        }
    }
}

private const val SEGMENT_ACTIVITY = 0
private const val SEGMENT_FRIENDS = 1
private const val SEGMENT_MESSAGES = 2

/**
 * The chip row that switches between the tab's three views.
 *
 * Labels, not icons: the segment's job is to name a list, and "Messages" is the
 * word a reader would think of. The mailbox and the group glyph are still carried
 * by the feed headings below, where they have room to be read as decoration
 * rather than as the only label on a control.
 */
@Composable
private fun ActivitySegments(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegmentChip(
            label = "Activity",
            selected = selected == SEGMENT_ACTIVITY,
            onClick = { onSelect(SEGMENT_ACTIVITY) },
            testTag = "profile_segment_activity",
            modifier = Modifier.weight(1f)
        )
        SegmentChip(
            label = "Friends",
            selected = selected == SEGMENT_FRIENDS,
            onClick = { onSelect(SEGMENT_FRIENDS) },
            testTag = "profile_segment_friends",
            modifier = Modifier.weight(1f)
        )
        SegmentChip(
            label = "Messages",
            selected = selected == SEGMENT_MESSAGES,
            onClick = { onSelect(SEGMENT_MESSAGES) },
            testTag = "profile_segment_messages",
            modifier = Modifier.weight(1f)
        )
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
        // they are the only way into two of its three views.
        minTouchTargetHeight = 44.dp,
        modifier = modifier.testTag(testTag)
    )
}
