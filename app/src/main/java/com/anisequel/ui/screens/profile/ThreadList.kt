package com.anisequel.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.ActivityReply
import com.anisequel.domain.usecase.CommentWindow
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.viewmodel.CommentThreadStore

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
internal fun ColumnScope.ThreadList(
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
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            // The list is keyed by reply id, and a repeated key is a hard
            // `IllegalArgumentException` in Compose. The store guarantees one entry
            // per id; see `CommentsState.replies`.
            state = listState,
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            val head = state.head
            if (head != null) {
                item(key = "thread_head", contentType = "head") {
                    ThreadHeadRow(
                        head = head,
                        canPost = canPost,
                        onReply = onReply,
                        onQuote = onQuote,
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

                state.replies.isEmpty() -> threadEmptyContent(
                    state = state,
                    hasHead = head != null,
                    canPost = canPost,
                    onRetry = onRetry
                )

                else -> threadReplyItems(
                    state = state,
                    viewerId = viewerId,
                    canPost = canPost,
                    visible = visible,
                    replyDepths = replyDepths,
                    continuations = continuations,
                    hiddenCount = hiddenCount,
                    onRetry = onRetry,
                    onLoadOlder = onLoadOlder,
                    onReply = onReply,
                    onQuote = onQuote,
                    onLikeReply = onLikeReply,
                    onManageReply = onManageReply,
                    onShowCommentLikers = onShowCommentLikers,
                    nowEpochSecond = nowEpochSecond
                )
            }
        }

        // Floating over the bottom of the list. A row *in* the list would be below
        // the newest comment the reader has not seen yet, which is the wrong way
        // round for something that announces what arrived.
        //
        // Animated rather than appearing: the pill lands while the reader is in the
        // middle of a sentence, and a control that is simply *there* on the next
        // frame reads as something they missed. It springs up from the bottom and
        // leaves the same way; the count itself is the only thing that can change
        // while it is up, and that is a settled number rather than a ticker.
        AnimatedVisibility(
            visible = state.unseenNewComments > 0,
            enter = fadeIn(ExpressiveMotion.FastEffects) +
                scaleIn(initialScale = 0.8f, animationSpec = ExpressiveMotion.BouncySpatial),
            exit = fadeOut(ExpressiveMotion.FastEffects) +
                scaleOut(targetScale = 0.9f, animationSpec = ExpressiveMotion.FastSpatial),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        ) {
            NewCommentsPill(
                count = state.unseenNewComments,
                onClick = {
                    onRevealNewComments()
                    // The pill promises the newest comments, so it takes the reader
                    // there: one frame for the revealed rows to be laid out, then a
                    // scroll to the last item (the head row is item 0, not a reply).
                    scope.launch {
                        withFrameNanos { }
                        val last = listState.layoutInfo.totalItemsCount - 1
                        if (last >= 0) listState.animateScrollToItem(last)
                    }
                }
            )
        }
    }
}

/**
 * The message a thread answers, drawn with the same row as every reply.
 *
 * It comes before the waiting branches so the message stays readable while its
 * replies are still arriving, and so a failed reply fetch does not erase the thing
 * the reader tapped. No heart and no manage dot: a message's like is an activity
 * like, which this store cannot toggle, and its edit/delete are the inbox's own
 * actions, not a comment's.
 */
@Composable
private fun ThreadHeadRow(
    head: ActivityReply,
    canPost: Boolean,
    onReply: (String) -> Unit,
    onQuote: (ActivityReply) -> Unit,
    nowEpochSecond: Long?
) {
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

/**
 * The two answers a thread with no replies can give.
 *
 * Only a thread with nothing above it can be wholly replaced by its failure or its
 * empty state. A message thread already has its first line drawn above, so "no
 * comments yet" under it would be a second answer to a question the head already
 * answers, and a load failure there is the banner below rather than the whole
 * screen. That branch used to be `Unit`, which left a failed reply fetch under a
 * message with nothing to retry and no sentence saying the answers were missing.
 */
private fun LazyListScope.threadEmptyContent(
    state: CommentThreadStore.CommentsState,
    hasHead: Boolean,
    canPost: Boolean,
    onRetry: () -> Unit
) {
    if (!hasHead) {
        if (state.loadError != null) {
            item(key = "load_error") {
                ThreadLoadError(message = state.loadError, onRetry = onRetry)
            }
        } else {
            item(key = "empty") {
                ThreadEmptyState(canPost = canPost)
            }
        }
        return
    }
    val note = state.windowError ?: state.refreshError ?: state.loadError
    if (note != null) {
        item(key = "empty_thread_error") {
            RefreshErrorBanner(message = note, onRetry = onRetry)
        }
    }
}

/**
 * The banner, the Older row and the replies themselves.
 *
 * Three different failures share one shape: an Older page that did not arrive, a
 * background check that did not, and a thread that never loaded all leave the rows
 * on screen readable, and all need a retry that does not throw away what the
 * reader is looking at. The retry itself differs, so the banner names the request
 * that failed rather than assuming the older one: one field for both kinds made a
 * failed refresh offer a Retry that walked the thread backwards instead of
 * fetching what had not arrived.
 */
private fun LazyListScope.threadReplyItems(
    state: CommentThreadStore.CommentsState,
    viewerId: Int?,
    canPost: Boolean,
    visible: List<ActivityReply>,
    replyDepths: Map<Int, Int>,
    continuations: Set<Int>,
    hiddenCount: Int,
    onRetry: () -> Unit,
    onLoadOlder: () -> Unit,
    onReply: (String) -> Unit,
    onQuote: (ActivityReply) -> Unit,
    onLikeReply: (Int) -> Unit,
    onManageReply: (Int) -> Unit,
    onShowCommentLikers: (Int) -> Unit,
    nowEpochSecond: Long?
) {
    val note = state.windowError ?: state.refreshError ?: state.loadError
    if (note != null) {
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
        ThreadReplyRow(
            reply = reply,
            indent = replyDepths[reply.id] ?: 0,
            isContinuation = reply.id in continuations,
            likePending = reply.id in state.pendingReplyLikes,
            canManage = viewerId != null && reply.user?.id == viewerId,
            canPost = canPost,
            onLike = { onLikeReply(reply.id) },
            onShowLikers = { onShowCommentLikers(reply.id) },
            onReply = onReply,
            onQuote = { onQuote(reply) },
            onManage = { onManageReply(reply.id) },
            nowEpochSecond = nowEpochSecond
        )
    }
}

/**
 * One reply, with the placement spring a live list needs.
 *
 * A comment that lands while the sheet is open arrives at the end, an Older page
 * prepends a block above, and a delete takes one out. Without the placement spring
 * every survivor jumped to its new index on that frame, which reads as the
 * conversation being redrawn rather than as the conversation moving. Same tokens
 * the library grid uses - a slide on the spatial spring, a fade on the effects
 * pair - so a list rearranges the one way in this app.
 */
@Composable
private fun LazyItemScope.ThreadReplyRow(
    reply: ActivityReply,
    indent: Int,
    isContinuation: Boolean,
    likePending: Boolean,
    canManage: Boolean,
    canPost: Boolean,
    onLike: () -> Unit,
    onShowLikers: () -> Unit,
    onReply: (String) -> Unit,
    onQuote: (ActivityReply) -> Unit,
    onManage: () -> Unit,
    nowEpochSecond: Long?
) {
    CommentRow(
        reply = reply,
        indent = indent,
        isContinuation = isContinuation,
        likePending = likePending,
        canManage = canManage,
        // A reply is addressed by a leading `@name`, so without an author name
        // there is nothing to insert, even for a signed-in reader.
        canReply = canPost && !reply.user?.name.isNullOrBlank(),
        canQuote = canPost && !reply.text.isNullOrBlank(),
        onLike = onLike,
        onShowLikers = onShowLikers,
        onReply = onReply,
        onQuote = onQuote,
        onManage = onManage,
        nowEpochSecond = nowEpochSecond,
        modifier = Modifier.animateItem(
            placementSpec = ExpressiveMotion.DefaultSpatialOffset,
            fadeInSpec = ExpressiveMotion.ListItemFadeIn,
            fadeOutSpec = ExpressiveMotion.ListItemFadeOut
        )
    )
}
