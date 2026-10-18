package com.anisequel.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * The Activity tab: the person's activity heatmap and list history, newest first.
 *
 * ## Two complementary views:
 *
 * 1. [ActivityCalendarCard] provides the 26-week contribution calendar summary
 *    (finished anime and list changes), giving an immediate visual overview of
 *    consistency and streaks.
 * 2. The paginated activity feed presents rich, tinted cards for individual episodes
 *    watched, titles completed, or items planned.
 */
@Composable
fun ProfileActivityTab(
    activityState: UserActivityViewModel.ActivityState,
    onLoadFirstPage: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit = onLoadFirstPage,
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
     *
     * Named `listState` to match the other tabs even though this one also has an
     * `activityState`; a bare `state` next to that read as the tab's data rather
     * than its scroll position.
     */
    listState: LazyListState = rememberLazyListState(),
    calendar: ActivityCalendar? = null,
    userName: String? = null,
    userAvatar: String? = null,
    onLike: (Int) -> Unit = {},
    onOpenComments: (Int) -> Unit = {},
    onOpenLikers: (Int) -> Unit = {},
    onOpenDetail: (MediaNode) -> Unit = {}
) {
    // Fetched on first composition, so opening the tab is what costs the request -
    // not opening the profile.
    LaunchedEffect(Unit) {
        if (activityState is UserActivityViewModel.ActivityState.Idle) {
            onLoadFirstPage()
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
    LaunchedEffect(shouldPage) {
        if (shouldPage) onLoadNextPage()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_activity_tab"),
        contentPadding = profileListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = listState
    ) {
        if (calendar != null && calendar.columns.isNotEmpty()) {
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
                        trailing = "${calendar.totalChanges} changes"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ActivityCalendarCard(calendar = calendar)
                }
            }
        }

        item(key = "activity_header") {
            // The count mirrors the calendar's "N changes" line above it, so the
            // two sections of the tab read as the same kind of heading rather
            // than one labelled and one bare.
            val loadedCount = (activityState as? UserActivityViewModel.ActivityState.Success)
                ?.activities
                ?.size
                ?.takeIf { it > 0 }
                ?.toString()
            ProfileSectionHeader(
                title = "Recent Activity",
                icon = AppVectorIcons.ProfileActivity,
                trailing = loadedCount,
                modifier = Modifier
                    .widthIn(max = ProfileMaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        when (activityState) {
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
                        onRetry = onRetry
                    )
                }
            }

            is UserActivityViewModel.ActivityState.Success -> {
                if (activityState.activities.isEmpty()) {
                    item(key = "activity_empty") {
                        ActivityEmpty()
                    }
                } else {
                    items(
                        items = activityState.activities,
                        key = { it.id },
                        contentType = { "activity" }
                    ) { activity ->
                        ActivityCard(
                            activity = activity,
                            userName = userName,
                            userAvatar = userAvatar,
                            modifier = Modifier
                                .widthIn(max = ProfileMaxContentWidth)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            onLike = { onLike(activity.id) },
                            onComments = { onOpenComments(activity.id) },
                            onOpenLikers = { onOpenLikers(activity.id) },
                            onOpenDetail = onOpenDetail,
                            likePending = activity.id in activityState.pendingLikes
                        )
                    }

                    if (activityState.loadMoreError != null) {
                        // Drawn instead of the spinner, and *before* the
                        // `hasMore` check: `hasMore` is still true after a failed
                        // page, so a plain spinner here would sit under a feed
                        // that has stopped growing with nothing to indicate why.
                        //
                        // A tap, rather than relying on the reader to scroll away
                        // from the end and back to re-trigger `shouldPage`, which
                        // is the only thing that used to retry a page.
                        item(key = "activity_more_error") {
                            ActivityPagingRetry(
                                message = activityState.loadMoreError,
                                onRetry = onLoadNextPage
                            )
                        }
                    } else if (activityState.hasMore) {
                        item(key = "activity_more") {
                            ActivityPagingIndicator(loading = activityState.isLoadingMore)
                        }
                    }
                }
            }
        }
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
private fun ActivityEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp)
            .testTag("activity_empty"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(
            icon = AppVectorIcons.ProfileActivity,
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No list activity to show",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
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
