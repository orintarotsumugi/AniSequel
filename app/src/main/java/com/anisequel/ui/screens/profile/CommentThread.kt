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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.ActivityReply
import com.anisequel.domain.usecase.ReplyThreader
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.EngagementAction
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownWithCovers
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
    onRetry: () -> Unit,
    onStartCompose: () -> Unit,
    onReply: (String) -> Unit,
    onLikeReply: (Int) -> Unit,
    onManageReply: (Int) -> Unit
) {
    CommentsHeader(
        count = state.replies.size,
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

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        // The list is keyed by reply id, and a repeated key is a hard
        // `IllegalArgumentException` in Compose. The store guarantees one entry
        // per id; see `CommentsState.replies`.
        state = listState,
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
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

            state.loadError != null && state.replies.isEmpty() -> item(key = "load_error") {
                ThreadLoadError(message = state.loadError, onRetry = onRetry)
            }

            state.replies.isEmpty() -> item(key = "empty") {
                ThreadEmptyState(canPost = canPost)
            }

            else -> {
                val refreshError = state.loadError
                if (refreshError != null) {
                    item(key = "refresh_error") {
                        RefreshErrorBanner(message = refreshError, onRetry = onRetry)
                    }
                }
                items(
                    items = state.replies,
                    key = { it.id },
                    contentType = { "reply" }
                ) { reply ->
                    CommentRow(
                        reply = reply,
                        indent = replyDepths[reply.id] ?: 0,
                        likePending = reply.id in state.pendingReplyLikes,
                        canManage = viewerId != null && reply.user?.id == viewerId,
                        // A reply is addressed by a leading `@name`, so without an
                        // author name there is nothing to insert, even for a
                        // signed-in reader.
                        canReply = canPost && !reply.user?.name.isNullOrBlank(),
                        onLike = { onLikeReply(reply.id) },
                        onReply = onReply,
                        onManage = { onManageReply(reply.id) }
                    )
                }
            }
        }
    }

    CommentEntryBar(canPost = canPost, onClick = onStartCompose)
}

/**
 * The pinned header: what this list is, how much of it there is, and whether a
 * newer copy is on its way.
 *
 * The count is the thread's whole answer to "should I read this?", so it stays
 * beside the title. The refresh spinner sits at the far end rather than over the
 * list: the rows under it are still valid, and a scrim or a skeleton would say
 * they were not.
 */
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
 * One comment: a face, a name and a time, the body, and the row's controls.
 *
 * The controls are left-aligned under the body rather than pushed to the sheet's
 * edges. A body can be a multi-line quote or an image, and a right-aligned
 * overflow would sit at the end of whatever line happened to be last.
 */
@Composable
private fun CommentRow(
    reply: ActivityReply,
    indent: Int,
    likePending: Boolean,
    canManage: Boolean,
    canReply: Boolean,
    onLike: () -> Unit,
    onReply: (String) -> Unit,
    onManage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                // One step per threaded level, capped in [ReplyThreader]. The
                // mention itself is still in the body, so the indent is an aid
                // rather than the only record of who is being answered.
                start = 20.dp + 18.dp * indent,
                end = 20.dp,
                top = 10.dp,
                bottom = 10.dp
            )
            .testTag("activity_reply_row"),
        verticalAlignment = Alignment.Top
    ) {
        CommentAvatar(
            url = reply.user?.avatar?.large ?: reply.user?.avatar?.medium,
            name = reply.user?.name.orEmpty()
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.user?.name.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (reply.createdAt != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatRelativeSeconds(reply.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            ReplyBody(text = reply.text)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The same control the activity card draws, shared rather than
                // re-declared here - `EngagementAction`.
                EngagementAction(
                    icon = if (reply.isLiked == true) {
                        AppVectorIcons.ActivityLiked
                    } else {
                        AppVectorIcons.ActivityNotLiked
                    },
                    count = reply.likeCount ?: 0,
                    active = reply.isLiked == true,
                    // Null `isLiked` means AniList did not answer, which is what a
                    // signed-out reader gets. Disabled rather than drawn-as-unliked,
                    // so the control is never something that looks live and does
                    // nothing.
                    enabled = reply.isLiked != null && !likePending,
                    contentDescription = if (reply.isLiked == true) "Unlike comment" else "Like comment",
                    onClick = onLike,
                    modifier = Modifier.testTag("activity_reply_like_button")
                )
                if (canReply) {
                    Spacer(modifier = Modifier.width(4.dp))
                    ReplyActionButton(
                        name = reply.user?.name.orEmpty(),
                        onClick = { onReply(reply.user?.name.orEmpty()) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (canManage) {
                    CommentManageButton(onClick = onManage)
                }
            }
        }
    }
}

/**
 * "Reply", next to the heart.
 *
 * A labelled control rather than a second icon: on this row the heart is a bare
 * icon with a count, and a bare arrow beside it would be another glyph to guess.
 * The label is also what makes the row's two taps distinguishable to a screen
 * reader without a description that says "reply to Bild's comment" on every row.
 */
@Composable
private fun ReplyActionButton(name: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .bouncyPress(pressedScale = 0.9f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Reply to $name",
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("activity_reply_reply_button"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppVectorIcons.ActivityReplies,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "Reply",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The overflow that opens the manage dialog, on the viewer's own comment.
 *
 * A tap target of its own rather than a long press on the row: the sheet's only
 * hold gesture elsewhere (the heart) opens likers, and nothing about a comment's
 * body says "hold me to edit this". 48dp, the platform's minimum touch target,
 * rather than the 36dp box that was sized to its glyph - manage is the one
 * control in the row that changes somebody's comment, and it was also the
 * hardest to hit.
 */
@Composable
private fun CommentManageButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .bouncyPress(pressedScale = 0.9f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                // The spring is the feedback. A ripple inside a 48dp circle that is
                // already scaling reads as a second, competing animation - the same
                // call the format bar and the reply control make.
                indication = null,
                onClick = onClick
            )
            .testTag("activity_reply_manage_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppVectorIcons.ActivityManage,
            contentDescription = "Comment options",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ReplyBody(text: String?) {
    // A child composable rather than an early return in this one: `remember`
    // after a return is a conditional composable call, and a reply whose text
    // AniList omitted would then occupy the slot the parser state wants.
    if (!text.isNullOrBlank()) MarkdownBody(text)
}

@Composable
private fun MarkdownBody(text: String) {
    // The cover-aware renderer: a comment can link an AniList title, and the card
    // that link becomes draws a poster only if a loader is in scope. The loader
    // comes from `LocalMediaCoverLoader`, provided by the sheet's host, and the
    // document is parsed once inside this call rather than per frame.
    AniListMarkdownWithCovers(markdown = text)
}

@Composable
private fun CommentAvatar(url: String?, name: String) {
    val shape = CircleShape
    val base = Modifier
        .size(34.dp)
        .clip(shape)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = base.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = base.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
