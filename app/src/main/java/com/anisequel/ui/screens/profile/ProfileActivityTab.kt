package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MediaNode
import com.anisequel.domain.usecase.ActivityCalendar
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.viewmodel.UserActivityViewModel

/**
 * Everything that differs between the two feeds the Activity tab can show.
 *
 * The profile's own history and the friends feed are the same screen with the
 * same cards, paging, skeleton and failure states - only whose rows they are,
 * what the heading says, and whether each card carries its own author differ.
 * Passing those differences as one object keeps [ProfileActivityTab] one
 * composable rather than two copies that would drift the first time a card or a
 * retry row changed.
 *
 * The author fields are for the profile feed: every card belongs to the person
 * whose profile is open, so the screen passes their name once instead of the
 * payload repeating it in every row. The friends feed leaves both null and each
 * card reads `ProfileActivity.user` instead.
 */
data class ActivityFeedSource(
    val state: UserActivityViewModel.ActivityState,
    val title: String,
    val icon: ImageVector,
    val emptyMessage: String,
    val onLoadFirstPage: () -> Unit,
    val onLoadNextPage: () -> Unit,
    val onRetry: () -> Unit,
    /**
     * Re-reads the first page over the rows already on screen.
     *
     * The retry offered by a failed refresh. Deliberately not [onRetry]: that
     * path is allowed to show the loading skeleton because there is nothing to
     * keep, whereas this one keeps the feed visible and swaps the rows in when
     * the new page lands.
     */
    val onRefresh: () -> Unit,
    val onLike: (Int) -> Unit,
    val onOpenComments: (Int) -> Unit,
    val onOpenLikers: (Int) -> Unit,
    val calendar: ActivityCalendar? = null,
    val userName: String? = null,
    val userAvatar: String? = null,
    /**
     * Opens the person a card is attributed to, or null when the card's author
     * cannot be somebody else - the profile feed, where the answer is "the page
     * you are already on".
     */
    val onOpenAuthor: ((FollowUser) -> Unit)? = null
)

/**
 * The Activity tab: the person's activity heatmap and list history, newest first.
 *
 * ## Two complementary views:
 *
 * 1. [ActivityCalendarCard] provides the 26-week contribution calendar summary
 *    (finished anime and list changes), giving an immediate visual overview of
 *    consistency and streaks.
 * 2. The paginated activity feed presents rich, tinted cards for individual episodes
 *    watched, titles completed, or items planned.
 *
 * ## One feed, two sources
 *
 * [source] is either the profile's own activity or the people the viewer
 * follows; see [ActivityFeedSource]. The tab itself is unchanged by which one it
 * is showing, which is what keeps the two from behaving differently under the
 * same finger.
 */
@Composable
fun ProfileActivityTab(
    source: ActivityFeedSource,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /**
     * The feed's scroll state, hoisted by the screen.
     *
     * Parameterised rather than created here because `AnimatedContent` disposes
     * the outgoing tab once its transition finishes: a state created inside this
     * composable died with it, so switching to Stats and back dropped the reader
     * at the top of the feed they had scrolled a long way down. Every tab on the
     * screen keeps one of these for the same reason.
     */
    listState: LazyListState = rememberLazyListState(),
    onOpenDetail: (MediaNode) -> Unit = {},
    nowEpochSecond: Long = rememberNowEpochSecond()
) {
    // Fetched on first composition, so opening the tab is what costs the request -
    // not opening the profile.
    LaunchedEffect(Unit) {
        if (source.state is UserActivityViewModel.ActivityState.Idle) {
            source.onLoadFirstPage()
        }
    }

    // Six rows from the end is far enough that the fetch has usually landed before
    // the user gets there, and near enough that a short list does not spin.
    val shouldPage by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - PAGE_TRIGGER_ROWS
        }
    }
    // The row count rides along in the key because `shouldPage` is a boolean that can
    // stay true across a page landing. A page can arrive shorter than
    // [PAGE_TRIGGER_ROWS] - the repository drops union members and updates for removed
    // media - and a reader parked at the end of a list that short never saw
    // `shouldPage` flip false and true again, so the feed simply stopped growing with
    // `hasMore` still true and nothing on screen saying why. Re-keying re-asks after
    // every append; the ViewModel's own `isLoadingMore`/`hasMore` guards make the
    // extra evaluations free.
    val activityCount =
        (source.state as? UserActivityViewModel.ActivityState.Success)?.activities?.size ?: 0
    LaunchedEffect(shouldPage, activityCount) {
        if (shouldPage) source.onLoadNextPage()
    }

    // A failed refresh over existing rows is kept apart from the feed's paging
    // error: the retry below re-reads page one, where the footer's retry appends.
    val refreshError = (source.state as? UserActivityViewModel.ActivityState.Success)?.refreshError

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_activity_tab"),
        contentPadding = profileListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = listState
    ) {
        if (source.calendar != null && source.calendar.columns.isNotEmpty()) {
            item(key = "activity_calendar_section") {
                Column(
                    modifier = Modifier
                        .widthIn(max = ProfileMaxContentWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    ProfileSectionHeader(
                        title = "Activity History",
                        icon = AppVectorIcons.Calendar,
                        trailing = "${source.calendar.totalChanges} changes"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ActivityCalendarCard(calendar = source.calendar)
                }
            }
        }

        item(key = "activity_header") {
            // The count mirrors the calendar's "N changes" line above it, so the
            // two sections of the tab read as the same kind of heading rather
            // than one labelled and one bare.
            val loadedCount = (source.state as? UserActivityViewModel.ActivityState.Success)
                ?.activities
                ?.size
                ?.takeIf { it > 0 }
                ?.toString()
            ProfileSectionHeader(
                title = source.title,
                icon = source.icon,
                trailing = loadedCount,
                modifier = Modifier
                    .widthIn(max = ProfileMaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        // Under the heading it belongs to: a failed refresh is news about this
        // feed, not about the calendar above it.
        if (refreshError != null) {
            item(key = "activity_refresh_error") {
                ActivityRefreshFailure(
                    message = refreshError,
                    onRetry = source.onRefresh
                )
            }
        }

        activityFeedItems(
            source = source,
            nowEpochSecond = nowEpochSecond,
            onOpenDetail = onOpenDetail
        )
    }

    // The comment sheet is NOT rendered here, and that is deliberate.
    //
    // This composable is called from inside `UserProfileScreen`'s `AnimatedContent`,
    // which composes the incoming tab alongside the outgoing one and then *disposes*
    // the outgoing one when the transition finishes. A `ModalBottomSheet` is a window
    // of its own, so anything drawing one from inside a tab is torn down with the tab:
    // open the thread, tap another tab, and the sheet goes with it - mid-sentence,
    // draft included. The likers sheet was hoisted out of this file for exactly that
    // reason and carries the note; the comments sheet was left behind, so the two
    // sheets on this screen had opposite lifetimes for no reason either could state.
    //
    // `UserProfileScreen` draws both, as siblings of the tab content.
}

/**
 * The feed itself, for whichever load state it is in.
 *
 * Out of [ProfileActivityTab] because the tab's own work - the paging trigger,
 * the minute ticker, the calendar header - was tangled with a state `when` that
 * nested four more branches, so the reader of the composable could not tell the
 * page's scaffolding from the feed's contents.
 */
private fun LazyListScope.activityFeedItems(
    source: ActivityFeedSource,
    nowEpochSecond: Long,
    onOpenDetail: (MediaNode) -> Unit
) {
    when (val activityState = source.state) {
        is UserActivityViewModel.ActivityState.Idle,
        is UserActivityViewModel.ActivityState.Loading -> {
            item(key = "activity_loading") {
                ActivityLoading()
            }
        }

        is UserActivityViewModel.ActivityState.Error -> {
            item(key = "activity_error") {
                ActivityError(
                    message = activityState.message,
                    onRetry = source.onRetry
                )
            }
        }

        is UserActivityViewModel.ActivityState.Success -> {
            if (activityState.activities.isEmpty()) {
                item(key = "activity_empty") {
                    ActivityEmpty(message = source.emptyMessage)
                }
            } else {
                items(
                    items = activityState.activities,
                    key = { it.id },
                    // Two card shapes now share this list; telling LazyColumn
                    // which is which keeps its reuse slots from mixing a poster
                    // row with a post row's composition.
                    contentType = { if (it.isTextPost) "post" else "list" }
                ) { activity ->
                    // The friends feed carries a different author on every row;
                    // the profile feed carries one name for all of them and
                    // passes it once, through the source. Falling back to the
                    // row's own `user` is what lets one card serve both.
                    val author = activity.user
                    ActivityCard(
                        activity = activity,
                        userName = source.userName ?: author?.name,
                        userAvatar = source.userAvatar
                            ?: author?.avatar?.large
                            ?: author?.avatar?.medium,
                        modifier = Modifier
                            .widthIn(max = ProfileMaxContentWidth)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        onLike = { source.onLike(activity.id) },
                        onComments = { source.onOpenComments(activity.id) },
                        onOpenLikers = { source.onOpenLikers(activity.id) },
                        onOpenDetail = onOpenDetail,
                        likePending = activity.id in activityState.pendingLikes,
                        nowEpochSecond = nowEpochSecond,
                        // Only a feed of other people's rows has anywhere for
                        // the author tap to go.
                        onOpenAuthor = if (source.onOpenAuthor != null && author != null) {
                            { source.onOpenAuthor(author) }
                        } else {
                            null
                        }
                    )
                }

                if (activityState.loadMoreError != null) {
                    // Drawn instead of the spinner, and *before* the `hasMore`
                    // check: `hasMore` is still true after a failed page, so a
                    // plain spinner here would sit under a feed that has stopped
                    // growing with nothing to indicate why.
                    //
                    // A tap, rather than relying on the reader to scroll away
                    // from the end and back to re-trigger `shouldPage`, which
                    // is the only thing that used to retry a page.
                    item(key = "activity_more_error") {
                        ActivityPagingRetry(
                            message = activityState.loadMoreError,
                            onRetry = source.onLoadNextPage
                        )
                    }
                } else if (activityState.hasMore) {
                    item(key = "activity_more") {
                        ActivityPagingIndicator(loading = activityState.isLoadingMore)
                    }
                } else if (activityState.activities.size >= PAGE_TRIGGER_ROWS) {
                    // Only on a feed long enough to have been paged. Saying
                    // "that's everything" under three rows is noise; under a
                    // scrollback it is the difference between a feed that
                    // ended and one that silently failed to load more.
                    item(key = "activity_end") {
                        ActivityFeedEnd()
                    }
                }
            }
        }
    }
}

private const val PAGE_TRIGGER_ROWS = 6

/**
 * The feed's first page, sketched as the cards that are coming.
 *
 * This used to be one spinner. A spinner says "wait"; a skeleton says "wait, and
 * here is what is on its way", and the activity card is the most predictable
 * shape in the app to sketch. The shimmer clock is hoisted here and passed down
 * to every block so the whole stack sweeps together on one transition.
 */
@Composable
private fun ActivityLoading() {
    val phase = rememberShimmerPhase()
    ActivityFeedSkeleton(
        phase = phase,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_loading")
    )
}

@Composable
private fun ActivityError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .testTag("activity_error"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onRetry,
            modifier = Modifier
                .bouncyPress()
                .testTag("activity_retry_button")
        ) {
            Text("Try again")
        }
    }
}

@Composable
private fun ActivityEmpty(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp)
            .testTag("activity_empty"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(
            // The same list glyph the Library's empty state uses. These two
            // screens sit beside each other in the profile's tab bar, and a
            // comment bubble here beside the Library's timer (or its magnifier,
            // once a search came back empty) made the same fact - "this list has
            // nothing in it" - look like three different states. Search is for a
            // search that came back empty; an unfiltered list is just a list.
            icon = AppVectorIcons.List,
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A refresh that failed over rows that are still on screen.
 *
 * Deliberately not the full-width error state: nothing was lost. It says what
 * happened in the reader's terms and offers the one action that would fix it,
 * and it sits above the feed so it is not read as commentary on the last row.
 */
@Composable
private fun ActivityRefreshFailure(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 14.dp, top = 6.dp, end = 4.dp, bottom = 6.dp)
            .testTag("activity_refresh_error"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier
                .bouncyPress()
                .testTag("activity_refresh_retry")
        ) {
            Text("Retry")
        }
    }
}

/** The end of a feed long enough to have been paged. */
@Composable
private fun ActivityFeedEnd() {
    Text(
        text = "You're all caught up",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("activity_feed_end")
    )
}

@Composable
private fun ActivityPagingIndicator(loading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("activity_paging"),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            ExpressiveLoadingIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * The way back to a page that failed.
 *
 * A button rather than a message, because the failure is not the reader's to fix -
 * it is a rate limit or a dropped connection that has already passed, and the
 * request that would succeed is one tap away.
 *
 * Says what failed *and* that retrying is the next action, since the alternative
 * on offer used to be scrolling seven rows away and back, which nobody would
 * think to try.
 */
@Composable
private fun ActivityPagingRetry(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("activity_paging_retry"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Try again",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .bouncyPress()
                .clickable(onClick = onRetry)
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 12.dp)
        )
    }
}
