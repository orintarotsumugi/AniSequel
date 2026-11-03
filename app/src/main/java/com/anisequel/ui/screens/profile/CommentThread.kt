package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
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
import java.time.Instant
import com.anisequel.data.model.ActivityReply
import com.anisequel.data.repository.MotionStyle
import com.anisequel.domain.usecase.ReplyThreader
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.rememberIsResumed
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
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
    /**
     * Whether this thread has been placed at its newest row.
     *
     * Owned by the sheet because this composable is disposed whenever the editor is
     * up; see [ChatScrollEffects].
     */
    anchor: MutableState<Boolean>,
    pollIntervalMillis: Long,
    polling: Boolean,
    nowEpochSecond: Long?,
    /**
     * The author the open composer is addressing, or null.
     *
     * The thread paints that comment's row while the reader is answering it: the chip
     * above the field says *who*, and on a thread that has been scrolled three pages
     * it does not say *which*.
     */
    replyTargetName: String? = null,
    onRetry: () -> Unit,
    onStartCompose: () -> Unit,
    onReply: (String) -> Unit,
    onQuote: (ActivityReply) -> Unit,
    onLikeReply: (Int) -> Unit,
    onManageReply: (Int) -> Unit,
    onShowCommentLikers: (Int) -> Unit,
    onLoadOlder: () -> Unit,
    onRevealNewComments: () -> Unit,
    onCheckForNewComments: (atBottom: Boolean) -> Unit,
    /** Re-reads the thread now, because the reader tapped Refresh. */
    onRefresh: (atBottom: Boolean) -> Unit
) {
    // Where the reader is, computed once for the whole sheet: the poll needs it to
    // decide whether to move them, and Refresh needs it for the same question. It
    // used to live inside the scroll effects, which left the header with no honest
    // way to answer a tap. Held as a `State`, not a value, so the timer reads the
    // live answer when it fires instead of the one captured at launch.
    val isAtBottom = remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 2
        }
    }

    CommentsHeader(
        // Chat view prints the thread's own count, because the rows on screen are
        // deliberately fewer than that: "12 of 900" is the honest answer to how big
        // the conversation is, and a header that counted only what was fetched
        // would say 12 and let the reader think that was the whole thread.
        count = if (state.isChatView && state.replyCount > 0) state.replyCount else state.replies.size,
        isRefreshing = state.isRefreshing,
        isLoading = state.isLoading,
        lastCheckedAtMillis = state.lastCheckedAtMillis,
        nowEpochSecond = nowEpochSecond,
        onRefresh = { onRefresh(isAtBottom.value) }
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
        drawnIds = visible.map { it.id },
        rowsAboveReplies = threadRowsAboveReplies(state, visible.size),
        listState = listState,
        isAtBottom = isAtBottom,
        anchored = anchor,
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
        replyTargetName = replyTargetName,
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
private fun CommentsHeader(
    count: Int,
    isRefreshing: Boolean,
    isLoading: Boolean,
    lastCheckedAtMillis: Long,
    nowEpochSecond: Long?,
    onRefresh: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
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
            // The freshness line is the reason this is a button and not a gesture: a
            // reader who can see the thread was checked 40 seconds ago does not need
            // to tap it, and one who sees "8m ago" knows the tap will answer. AniList
            // holds a session at about 30 requests a minute, so an invisible poll is
            // a mystery that gets hammered.
            if (!isLoading && lastCheckedAtMillis > 0) {
                Text(
                    text = checkedAgoLabel(lastCheckedAtMillis, nowEpochSecond),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("comments_checked_ago")
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (isRefreshing || isLoading) {
                // The morphing loader, not a bare `CircularProgressIndicator`:
                // the body of this same sheet shimmers and the detail sheet's
                // editor already morphs, so a plain spinner here was the last
                // thing in the thread that belonged to a different system.
                //
                // It replaces the Refresh icon rather than sitting beside it: two
                // affordances for one state is a header that reads as busy, and the
                // control is doing that work right now.
                //
                // Gated the way every other loop in this app is. Material's
                // `LoadingIndicator` is an indefinite morph, and this header stays
                // composed under the manage dialog and the likers tray - so an
                // ungated one keeps the frame clock awake for a sheet nobody can
                // see, and ignores the one setting the reader has about motion.
                if (rememberIsResumed() && ExpressiveMotion.speed != MotionStyle.INSTANT) {
                    ExpressiveLoadingIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = if (isLoading) "Loading" else "Refreshing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // One interaction source shared between the button and the spring, the
                // way `AniTopAppBar` documents it: without one, `bouncyPress` falls back
                // to its own pointer loop, so a finger that lands here and starts a
                // scroll squashes the icon for the whole drag.
                val refreshSource = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .bouncyPress(interactionSource = refreshSource)
                        .testTag("comments_refresh"),
                    interactionSource = refreshSource
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Refresh,
                        contentDescription = "Check for new comments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * "Updated just now" / "Checked 8m ago", from the sheet's own last check.
 *
 * Reuses the feed's relative-time formatter so "3m ago" means the same thing here
 * as it does on every activity card. [nowEpochSecond] is the profile's minute tick:
 * without it the label is computed against the wall clock on each recomposition and
 * never changes while the sheet sits open, which is a freshness line that lies.
 */
private fun checkedAgoLabel(lastCheckedAtMillis: Long, nowEpochSecond: Long?): String {
    val checkedEpochSecond = (lastCheckedAtMillis / 1_000L).toInt()
    val now = nowEpochSecond?.toInt()?.let { Instant.ofEpochSecond(it.toLong()) } ?: Instant.now()
    val label = formatRelativeSeconds(checkedEpochSecond, now)
    return if (label == "just now") "Updated just now" else "Checked $label"
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
internal fun ThreadEmptyState(canPost: Boolean) {
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
internal fun ThreadLoadError(message: String, onRetry: () -> Unit) {
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
internal fun RefreshErrorBanner(message: String, onRetry: () -> Unit) {
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
 * The three things a chat does that a list does not: it opens at its newest message,
 * it keeps asking whether the conversation moved while it was on screen, and it holds
 * the reader's place when rows arrive *above* them.
 *
 * Split out of the thread because none of it has anything to do with drawing rows -
 * all three are about *where the reader is*, and they are the part of this sheet that
 * changes when the paging or the polling rules change.
 */
@Composable
private fun ChatScrollEffects(
    state: CommentThreadStore.CommentsState,
    drawnIds: List<Int>,
    rowsAboveReplies: Int,
    listState: LazyListState,
    isAtBottom: androidx.compose.runtime.State<Boolean>,
    // Held by the sheet, not here: this composable is disposed the moment the reader
    // taps Reply or Quote, and a flag that lived with it came back false on Cancel -
    // which re-ran the anchor scroll and teleported a reader who had walked five
    // pages back straight to the newest comment.
    anchored: MutableState<Boolean>,
    polling: Boolean,
    pollIntervalMillis: Long,
    onCheckForNewComments: (Boolean) -> Unit
) {
    // A chat opens at its newest message. AniList's own page opens at the top of
    // the thread and scrolls you down as replies arrive; in a sheet that has just
    // fetched one window of a nine-hundred-comment conversation, starting at row
    // zero would show the reader a windowful of nothing they asked for.
    //
    // Keyed on the thread, and one-shot: a reader who scrolls up to read something
    // and then posts a comment must not be dragged back down, and the posted-reply
    // scroll in the sheet owns that case - so it is skipped here rather than raced
    // against it. Both scrolls starting on the same frame is how each one cancels
    // the other and the reader stops on neither row.
    LaunchedEffect(state.activityId, state.isLoading, drawnIds.size) {
        if (state.isChatView && !anchored.value && !state.isLoading &&
            drawnIds.isNotEmpty() && state.postedReplyId == null
        ) {
            anchored.value = true
            // One frame, so the list has measured its items before being placed at
            // the end of them: `scrollToItem` on a layout that has not run yet
            // lands on index zero, which is the bug this effect replaces.
            withFrameNanos { }
            // Guarded: a layout that still reports no items would hand
            // `scrollToItem` -1, which throws instead of scrolling.
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) listState.scrollToItem(last)
        }
    }

    // The reply at the top of the viewport, and how far into it the reader has
    // scrolled. Tracked continuously because the place has to be recovered *after*
    // the list grew, when the row that was there is no longer on screen.
    var anchorTop by remember(state.activityId) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(listState, state.activityId) {
        snapshotFlow {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key is Int }
                ?: return@snapshotFlow null
            val key = item.key as Int
            key to (info.viewportStartOffset - item.offset).coerceAtLeast(0)
        }.collect { found -> if (found != null) anchorTop = found }
    }

    // The compensation. An Older page, or a window revealing rows it had already
    // fetched, puts new rows *above* the reader; a `LazyColumn` that is not reversed
    // keeps its index and moves the conversation, so the row being read slides off
    // the top of the screen and the placement springs animate the jump. This puts the
    // anchor back where it was, pixel for pixel.
    var previousDrawnIds by remember(state.activityId) { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(drawnIds) {
        val rows = CommentWindow.prependedRows(previousDrawnIds, drawnIds)
        previousDrawnIds = drawnIds
        if (rows == 0) return@LaunchedEffect
        val anchor = anchorTop ?: return@LaunchedEffect
        withFrameNanos { }
        val target = CommentWindow.drawnRowIndex(anchor.first, drawnIds, rowsAboveReplies)
        if (target >= 0) listState.scrollToItem(target, anchor.second)
    }

    // The timer. One per open thread, reading `isAtBottom` when it fires rather
    // than being rebuilt on every scroll - restarting the effect on a scroll would
    // reset the interval, and a reader who kept scrolling would keep postponing the
    // check they are waiting for.
    //
    // The flag arrives as a `State` rather than a `Boolean` for exactly that reason:
    // a plain value would be captured by the closure on the frame the effect started
    // and the poll would answer where the reader was when they opened the sheet, not
    // where they are now.
    if (polling && state.isChatView && !state.isLoading) {
        LaunchedEffect(state.activityId, pollIntervalMillis) {
            while (isActive) {
                delay(pollIntervalMillis)
                onCheckForNewComments(isAtBottom.value)
            }
        }
    }
}
