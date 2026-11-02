package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.anisequel.domain.usecase.CommentGrouping
import com.anisequel.domain.usecase.CommentWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.ActivityReply
import com.anisequel.domain.usecase.ReplyThreader
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.viewmodel.CommentThreadStore

/**
 * The thread side of `ActivityCommentsSheet`: the header, the list, and the bar
 * that opens the editor.
 *
 * Its own file because the sheet's two modes are genuinely different screens
 * sharing one draft - the editor is not the last row of the list - and because
 * the list's waiting, failed, refreshing and empty branches each describe a whole
 * screen rather than a row. The sheet keeps the draft, the two effects and the
 * mode switch; this keeps the conversation.
 *
 * ## The three waiting states, and why they are three
 *
 * [CommentThreadStore.CommentsState.isLoading] means there is nothing yet and the
 * rows are drawn as a skeleton. [CommentThreadStore.CommentsState.isRefreshing]
 * means the reader is looking at comments that are still valid while a newer copy
 * is fetched, so the list stays and a quiet spinner appears in the header.
 * `loadError != null` with rows means the refresh failed: the rows stay and the
 * error becomes a banner inside the list, with its own retry. Only
 * `loadError != null` with *no* rows is a whole-screen error, because only then
 * is there nothing else to show.
 *
 * Treating all four as "loading" is what made the old sheet blink to a skeleton
 * on every reopen and replace a loaded thread with an error line when a refresh
 * failed - including when the reader had only dismissed the sheet by accident.
 */
@Composable
internal fun ColumnScope.CommentsThread(
    state: CommentThreadStore.CommentsState,
    viewerId: Int?,
    canPost: Boolean,
    listState: LazyListState,
    pollIntervalMillis: Long,
    polling: Boolean,
    nowEpochSecond: Long?,
    onRetry: () -> Unit,
    onStartCompose: () -> Unit,
    onReply: (String) -> Unit,
    onQuote: (ActivityReply) -> Unit,
    onLikeReply: (Int) -> Unit,
    onManageReply: (Int) -> Unit,
    onShowCommentLikers: (Int) -> Unit,
    onLoadOlder: () -> Unit,
    onRevealNewComments: () -> Unit,
    onCheckForNewComments: (atBottom: Boolean) -> Unit
) {
    CommentsHeader(
        // Chat view prints the thread's own count, because the rows on screen are
        // deliberately fewer than that: "12 of 900" is the honest answer to how big
        // the conversation is, and a header that counted only what was fetched
        // would say 12 and let the reader think that was the whole thread.
        count = if (state.isChatView && state.replyCount > 0) state.replyCount else state.replies.size,
        isRefreshing = state.isRefreshing,
        isLoading = state.isLoading
    )

    // Computed once per thread, not per row: the answer depends on every earlier
    // reply, and the list is re-threaded only when the replies change. See
    // [ReplyThreader]. This has to live above the lazy list - the `LazyListScope`
    // builder is not composable, so `remember` cannot be called down there.
    val replyDepths = remember(state.replies) {
        ReplyThreader.depths(
            state.replies.map {
                ReplyThreader.Reply(
                    id = it.id,
                    author = it.user?.name,
                    text = it.text
                )
            }
        )
    }

    // The window, not the whole fetched list. `shownRows` is what the reader has
    // asked to see; Older grows it, and a refresh that found new comments grows it
    // by exactly what arrived so nothing already on screen is pushed off the top.
    val visible = remember(state.replies, state.shownRows) {
        state.replies.takeLast(
            CommentWindow.visibleRowCount(
                repliesLoaded = state.replies.size,
                shownRows = state.shownRows
            )
        )
    }

    // Which rows continue the one above them. Grouping needs the depth, so it is
    // taken from the same pass that laid the thread out - a merged header across two
    // different branches would claim somebody was still speaking when they were not.
    val continuations = remember(visible, replyDepths) {
        CommentGrouping.continuationIds(
            visible.map { reply ->
                CommentGrouping.Row(
                    id = reply.id,
                    authorId = reply.user?.id,
                    createdAt = reply.createdAt,
                    depth = replyDepths[reply.id] ?: 0
                )
            }
        )
    }

    ChatScrollEffects(
        state = state,
        visibleCount = visible.size,
        listState = listState,
        polling = polling,
        pollIntervalMillis = pollIntervalMillis,
        onCheckForNewComments = onCheckForNewComments
    )

    ThreadList(
        state = state,
        viewerId = viewerId,
        canPost = canPost,
        listState = listState,
        visible = visible,
        replyDepths = replyDepths,
        continuations = continuations,
        onRetry = onRetry,
        onLoadOlder = onLoadOlder,
        onReply = onReply,
        onQuote = onQuote,
        onLikeReply = onLikeReply,
        onManageReply = onManageReply,
        onShowCommentLikers = onShowCommentLikers,
        onRevealNewComments = onRevealNewComments,
        nowEpochSecond = nowEpochSecond
    )

    CommentEntryBar(canPost = canPost, onClick = onStartCompose)
}

@Composable
private fun CommentsHeader(count: Int, isRefreshing: Boolean, isLoading: Boolean) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.ActivityReplies,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Comments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isRefreshing || isLoading) {
                Text(
                    text = if (isLoading) "Loading" else "Refreshing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                // The morphing loader, not a bare `CircularProgressIndicator`:
                // the body of this same sheet shimmers and the detail sheet's
                // editor already morphs, so a plain spinner here was the last
                // thing in the thread that belonged to a different system.
                ExpressiveLoadingIndicator(
                    modifier = Modifier.size(14.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Nothing has arrived yet, and that is a fine state to be in.
 *
 * The old copy was one grey sentence ("No comments yet. Be the first.") sitting
 * where a conversation would be, which read as a failed load. The orb, the
 * heading and the sentence separate "empty" from "broken", and the sentence says
 * what to do next - the bar below is the answer, so the empty state does not need
 * a second button for it.
 */
@Composable
private fun ThreadEmptyState(canPost: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(icon = AppVectorIcons.ActivityReplies)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No comments yet",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (canPost) {
                "Start the conversation - yours will be the first comment."
            } else {
                // The bar under the list already says how to sign in; repeating
                // it here made an empty thread read as two refusals.
                "Nobody has commented on this activity yet."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The whole-screen failure: no rows, so the error *is* the thread.
 *
 * A centered explanation rather than the old bare red sentence and a text button
 * floating at the top of an empty list, which read as a comment that had failed
 * to render rather than as a request that had.
 */
@Composable
private fun ThreadLoadError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(
            icon = AppVectorIcons.BrokenHeart,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            iconTint = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Couldn't load comments",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRetry, modifier = Modifier.testTag("activity_comments_retry")) {
            Text("Try again")
        }
    }
}

/**
 * A refresh that failed over rows that are still there.
 *
 * The thread is readable, so the failure is a note inside the list rather than a
 * replacement for it. It sits above the first row so a reader who scrolled to
 * the bottom of a long thread still sees it when the list resets - the retry is
 * one tap either way, but an error at the very top of a stale list is a message
 * about the top of the list.
 */
@Composable
private fun RefreshErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Couldn't refresh",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2
                )
            }
            TextButton(onClick = onRetry) {
                Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

/**
 * The bar under the thread that opens the editor.
 *
 * A bar rather than a floating button so it reads as the next thing to do in a
 * conversation, and it is what AniList's thread screen has. Signed out it becomes
 * the explanation instead, because a field that cannot send is a worse answer
 * than a sentence saying why.
 */
@Composable
private fun CommentEntryBar(canPost: Boolean, onClick: () -> Unit) {
    Column(
        // No navigation-bar padding: the sheet's fixed-height column already pads
        // for `ime union navigationBars` once. A second copy here lifted the bar a
        // nav bar's height off the edge, and a nav bar above the keyboard when the
        // editor was open.
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // The two states are separate composables rather than an early return in
        // one: a `remember` after an early return is a conditional composable
        // call, and signing in while the sheet is open would then land the bar's
        // state in the slot the sentence used.
        if (canPost) {
            WriteCommentBar(onClick = onClick)
        } else {
            Text(
                text = "Sign in to AniList to join the conversation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WriteCommentBar(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .bouncyPress(pressedScale = 0.98f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Write a comment",
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("activity_comment_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppVectorIcons.ActivityReplies,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Write a comment...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


/**
 * The two things a chat does that a list does not: it opens at its newest message,
 * and it keeps asking whether the conversation moved while it was on screen.
 *
 * Split out of the thread because neither has anything to do with drawing rows -
 * both are about *where the reader is*, and they are the part of this sheet that
 * changes when the paging or the polling rules change.
 */
@Composable
private fun ChatScrollEffects(
    state: CommentThreadStore.CommentsState,
    visibleCount: Int,
    listState: LazyListState,
    polling: Boolean,
    pollIntervalMillis: Long,
    onCheckForNewComments: (Boolean) -> Unit
) {
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 2
        }
    }

    // A chat opens at its newest message. AniList's own page opens at the top of
    // the thread and scrolls you down as replies arrive; in a sheet that has just
    // fetched one window of a nine-hundred-comment conversation, starting at row
    // zero would show the reader a windowful of nothing they asked for.
    //
    // Keyed on the thread, and one-shot: a reader who scrolls up to read something
    // and then posts a comment must not be dragged back down, and the posted-reply
    // scroll in the sheet owns that case.
    var anchored by remember(state.activityId) { mutableStateOf(false) }
    LaunchedEffect(state.activityId, state.isLoading, visibleCount) {
        if (state.isChatView && !anchored && !state.isLoading && visibleCount > 0) {
            anchored = true
            // One frame, so the list has measured its items before being placed at
            // the end of them: `scrollToItem` on a layout that has not run yet
            // lands on index zero, which is the bug this effect replaces.
            withFrameNanos { }
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    // The timer. One per open thread, reading `isAtBottom` when it fires rather
    // than being rebuilt on every scroll - restarting the effect on a scroll would
    // reset the interval, and a reader who kept scrolling would keep postponing the
    // check they are waiting for.
    if (polling && state.isChatView && !state.isLoading) {
        LaunchedEffect(state.activityId, pollIntervalMillis) {
            while (isActive) {
                delay(pollIntervalMillis)
                onCheckForNewComments(isAtBottom)
            }
        }
    }
}

/**
 * The list itself: which waiting state is drawn, the rows that sit above the
 * comments, the comments, and the pill that floats over the bottom of it all.
 *
 * Split out of the thread because the two have different reasons to change. The
 * thread works out *what* is on screen - the window, the depths, the grouping,
 * where the reader is parked; this draws the answer, so a new waiting state or a
 * new row type lands here and a new paging rule lands above. It also keeps the
 * branch count in either function small enough to read in one screen, which is
 * the difference between a list of states you can check and a wall you cannot.
 */
@Composable
@Suppress("LongParameterList")
private fun ColumnScope.ThreadList(
    state: CommentThreadStore.CommentsState,
    viewerId: Int?,
    canPost: Boolean,
    listState: LazyListState,
    visible: List<ActivityReply>,
    replyDepths: Map<Int, Int>,
    continuations: Set<Int>,
    onRetry: () -> Unit,
    onLoadOlder: () -> Unit,
    onReply: (String) -> Unit,
    onQuote: (ActivityReply) -> Unit,
    onLikeReply: (Int) -> Unit,
    onManageReply: (Int) -> Unit,
    onShowCommentLikers: (Int) -> Unit,
    onRevealNewComments: () -> Unit,
    nowEpochSecond: Long?
) {
val hiddenCount = CommentWindow.hiddenCount(state.replyCount, visible.size)

Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        // The list is keyed by reply id, and a repeated key is a hard
        // `IllegalArgumentException` in Compose. The store guarantees one entry
        // per id; see `CommentsState.replies`.
        state = listState,
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        // The message a thread is answering, when the thread is a conversation.
        // Drawn with the same row as every reply - the same face, body, Reply and
        // Quote - because it *is* one line of the conversation, just the first.
        // It comes before the waiting branches so the message stays readable while
        // its replies are still arriving, and so a failed reply fetch does not erase
        // the thing the reader tapped. No heart and no manage dot: a message's like
        // is an activity like, which this store cannot toggle, and its edit/delete
        // are the inbox's own actions, not a comment's.
        val head = state.head
        if (head != null) {
            item(key = "thread_head", contentType = "head") {
                CommentRow(
                    reply = head,
                    indent = 0,
                    isContinuation = false,
                    likePending = false,
                    canManage = false,
                    canReply = canPost && !head.user?.name.isNullOrBlank(),
                    canQuote = canPost && !head.text.isNullOrBlank(),
                    showLikes = false,
                    onLike = {},
                    onShowLikers = {},
                    onReply = onReply,
                    onQuote = { onQuote(head) },
                    onManage = {},
                    nowEpochSecond = nowEpochSecond
                )
            }
        }

        when {
            state.isLoading -> item(key = "loading") {
                // The rows that are coming, at the sizes they will have. A
                // spinner here was the one waiting state in the app whose shape
                // was already known and thrown away.
                //
                // The shimmer is owned by this item rather than by the sheet: an
                // infinite transition composed above the list kept ticking while
                // comments were on screen and while the editor covered them, for
                // a skeleton that was not drawn. Composed here, it exists only
                // for as long as the waiting state does.
                CommentRowsSkeleton(phase = rememberShimmerPhase())
            }

            // Only a thread with nothing above it can be wholly replaced by its
            // failure or its empty state. A message thread already has its first
            // line drawn above, so "no comments yet" under it would be a second
            // answer to a question the head already answers, and a load failure
            // there is the banner below rather than the whole screen.
            state.replies.isEmpty() && head == null -> {
                if (state.loadError != null) {
                    item(key = "load_error") {
                        ThreadLoadError(message = state.loadError, onRetry = onRetry)
                    }
                } else {
                    item(key = "empty") {
                        ThreadEmptyState(canPost = canPost)
                    }
                }
            }

            state.replies.isEmpty() -> Unit

            else -> {
                val note = state.windowError ?: state.refreshError ?: state.loadError
                if (note != null) {
                    // Three different failures, one shape: an Older page that did not
                    // arrive, a background check that did not and a thread that never
                    // loaded all leave the rows on screen readable, and all need a
                    // retry that does not throw away what the reader is looking at.
                    // The retry itself differs, so the banner names the request that
                    // failed rather than assuming the older one: one field for both
                    // kinds made a failed refresh offer a Retry that walked the
                    // thread backwards instead of fetching what had not arrived.
                    val olderFailed = state.windowError != null
                    item(
                        key = when {
                            olderFailed -> "window_error"
                            state.refreshError != null -> "refresh_error"
                            else -> "load_error"
                        }
                    ) {
                        RefreshErrorBanner(
                            message = note,
                            onRetry = if (olderFailed) onLoadOlder else onRetry
                        )
                    }
                }
                if (state.isChatView && hiddenCount > 0) {
                    item(key = "older", contentType = "older") {
                        CommentOlderRow(
                            hiddenCount = hiddenCount,
                            isLoading = state.isLoadingOlder,
                            onClick = onLoadOlder
                        )
                    }
                }
                items(
                    items = visible,
                    key = { it.id },
                    contentType = { "reply" }
                ) { reply ->
                    CommentRow(
                        reply = reply,
                        indent = replyDepths[reply.id] ?: 0,
                        isContinuation = reply.id in continuations,
                        likePending = reply.id in state.pendingReplyLikes,
                        canManage = viewerId != null && reply.user?.id == viewerId,
                        // A reply is addressed by a leading `@name`, so without an
                        // author name there is nothing to insert, even for a
                        // signed-in reader.
                        canReply = canPost && !reply.user?.name.isNullOrBlank(),
                        canQuote = canPost && !reply.text.isNullOrBlank(),
                        onLike = { onLikeReply(reply.id) },
                        onShowLikers = { onShowCommentLikers(reply.id) },
                        onReply = onReply,
                        onQuote = { onQuote(reply) },
                        onManage = { onManageReply(reply.id) },
                        nowEpochSecond = nowEpochSecond,
                        // A thread is a live list: a comment that lands while the
                        // sheet is open arrives at the end, an Older page prepends a
                        // block above, and a delete takes one out. Without the
                        // placement spring every survivor jumped to its new index on
                        // that frame, which reads as the conversation being redrawn
                        // rather than as the conversation moving. Same tokens the
                        // library grid uses - a slide on the spatial spring, a fade on
                        // the effects pair - so a list rearranges the one way in this
                        // app.
                        modifier = Modifier.animateItem(
                            placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                            fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                            fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                        )
                    )
                }
            }
        }
    }

    if (state.unseenNewComments > 0) {
        // Floating over the bottom of the list. A row *in* the list would be
        // below the newest comment the reader has not seen yet, which is the
        // wrong way round for something that announces what arrived.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        ) {
            NewCommentsPill(
                count = state.unseenNewComments,
                onClick = {
                    onRevealNewComments()
                }
            )
        }
    }
}
}
