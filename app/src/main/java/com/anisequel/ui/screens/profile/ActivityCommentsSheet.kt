package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.ActivityReply
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.EngagementAction
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.AniListMarkdownView
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.viewmodel.CommentThreadStore
import com.anisequel.ui.viewmodel.MAX_ACTIVITY_REPLY_LENGTH
import com.anisequel.ui.viewmodel.MIN_ACTIVITY_REPLY_LENGTH

/**
 * The comment thread for one activity, with a composer.
 *
 * Everything lives in a single [LazyColumn] rather than a pinned composer under
 * a scrolling list. A pinned composer needs the list and the bar to share a
 * bounded height through a `weight`, and this is a sheet whose height is decided
 * by its content - a `weight` here is a crash waiting for the frame where the
 * sheet is measured unbounded. One list is taller to reach the composer when a
 * thread is long, and cannot fail to lay out.
 *
 * ## What happens when a comment lands
 *
 * A post is appended to the end of the thread, which is off-screen while the
 * composer that posted it is the last thing in the list - the author's own
 * comment could arrive *behind* them and nothing would move. The store records
 * the posted comment's id for exactly this, and the sheet scrolls it into view.
 * Only a recorded post scrolls: the list also grows when the thread *loads*, and
 * jumping to the bottom of a conversation the reader has not seen yet would hide
 * its first comments behind an effect nobody asked for.
 *
 * ## Who gets a menu
 *
 * [viewerId] is the signed-in account, compared to each comment's author. Null -
 * signed out, or a caller that did not know it - leaves every comment without
 * its manage control, which is the safe half of the choice: AniList enforces
 * ownership on the mutation anyway, so the wrong half would be a Delete that can
 * only fail. The store repeats the check for the half a unit test can reach.
 *
 * Posting is gated by [canPost]: reading a thread needs no session (AniList
 * serves replies to anyone), but `SaveActivityReply` does, so a signed-out
 * reader gets the thread and a note instead of a text field that will fail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityCommentsSheet(
    state: CommentThreadStore.CommentsState,
    canPost: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPost: (String) -> Unit,
    viewerId: Int? = null,
    /**
     * Likes or un-likes one comment.
     *
     * A reply is an `ActivityReply`, which AniList lists in `LikeableUnion` and types
     * as `ACTIVITY_REPLY`, so it is likeable on its own terms - and the reply model has
     * carried `isLiked` and `likeCount` since this sheet was written, with the query
     * selecting both and nothing reading either. The heart a reader expects under
     * every other comment on the site was simply never drawn.
     *
     * Defaults to a no-op so the sheet still renders in a caller that has not wired it,
     * which would be a heart that does nothing - see [EngagementAction]'s note on the
     * disabled state, and why the callers pass `isLiked != null` rather than `true`.
     */
    onLikeReply: (Int) -> Unit = {},
    /** Opens the manage dialog for one of the viewer's own comments. */
    onOpenReplyActions: (Int) -> Unit = {},
    /** Moves the manage dialog between its steps. */
    onReplyActionMode: (CommentThreadStore.ReplyAction.Mode) -> Unit = {},
    /** Rewrites the edit draft as the reader types. */
    onReplyActionDraft: (String) -> Unit = {},
    /** Saves the draft over the comment being edited. */
    onSubmitReplyEdit: () -> Unit = {},
    /** Confirms the comment deletion. */
    onSubmitReplyDelete: () -> Unit = {},
    /** Closes the manage dialog. */
    onDismissReplyAction: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val shimmerPhase = rememberShimmerPhase()

    // See the KDoc. Keyed on the recorded id rather than on the list's size, so a
    // recomposition for any other reason cannot move the reader.
    LaunchedEffect(state.postedReplyId) {
        if (state.postedReplyId == null || state.replies.isEmpty()) return@LaunchedEffect
        // The last reply's item index: the header is 0 and the replies start at 1.
        listState.animateScrollToItem(state.replies.size)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .testTag("activity_comments_sheet"),
            contentPadding = PaddingValues(bottom = 20.dp),
            state = listState
        ) {
            item(key = "header") {
                CommentsHeader(count = state.replies.size)
            }

            when {
                state.isLoading -> item(key = "loading") {
                    // The rows that are coming, at the sizes they will have. A
                    // spinner here was the one waiting state in the app whose
                    // shape was already known and thrown away.
                    CommentRowsSkeleton(phase = shimmerPhase)
                }

                state.loadError != null -> item(key = "load_error") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.loadError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onRetry) {
                            Text("Try again")
                        }
                    }
                }

                state.replies.isEmpty() -> item(key = "empty") {
                    Text(
                        text = "No comments yet. Be the first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                    )
                }

                else -> items(
                    items = state.replies,
                    key = { it.id },
                    contentType = { "reply" }
                ) { reply ->
                    CommentRow(
                        reply = reply,
                        likePending = reply.id in state.pendingReplyLikes,
                        canManage = viewerId != null && reply.user?.id == viewerId,
                        onLike = { onLikeReply(reply.id) },
                        onManage = { onOpenReplyActions(reply.id) }
                    )
                }
            }

            item(key = "composer") {
                CommentComposer(
                    canPost = canPost,
                    isPosting = state.isPosting,
                    postError = state.postError,
                    onPost = onPost
                )
            }
        }
    }

    // Sibling of the sheet rather than a row in it: `AlertDialog` opens a window
    // of its own, so the anchor script would not survive the list recycling the
    // row anyway, and the dialog has to outlive the row that opened it - an edit
    // in flight is anchored to an id, not to a position.
    val action = state.action
    if (action != null) {
        ReplyActionDialog(
            action = action,
            onMode = onReplyActionMode,
            onDraftChange = onReplyActionDraft,
            onSubmitEdit = onSubmitReplyEdit,
            onSubmitDelete = onSubmitReplyDelete,
            onDismiss = onDismissReplyAction
        )
    }
}

@Composable
private fun CommentsHeader(count: Int) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
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
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun CommentRow(
    reply: ActivityReply,
    likePending: Boolean,
    canManage: Boolean,
    onLike: () -> Unit,
    onManage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
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
            // The same control the activity card draws, shared rather than re-declared
            // here - `EngagementAction`. Left-aligned and below the body rather than
            // beside the name so a reply's own markdown (which can be a multi-line
            // quote or an image) is not squeezed to make room for it.
            EngagementAction(
                icon = if (reply.isLiked == true) {
                    AppVectorIcons.ActivityLiked
                } else {
                    AppVectorIcons.ActivityNotLiked
                },
                count = reply.likeCount ?: 0,
                active = reply.isLiked == true,
                // Null `isLiked` means AniList did not answer, which is what a
                // signed-out reader gets. Disabled rather than drawn-as-unliked, so the
                // control is never something that looks live and does nothing.
                enabled = reply.isLiked != null && !likePending,
                contentDescription = if (reply.isLiked == true) "Unlike comment" else "Like comment",
                onClick = onLike,
                modifier = Modifier.testTag("activity_reply_like_button")
            )
        }
        if (canManage) {
            Spacer(modifier = Modifier.width(4.dp))
            CommentManageButton(onClick = onManage)
        }
    }
}

/**
 * The overflow that opens the manage dialog, on the viewer's own comment.
 *
 * A tap target of its own rather than a long press on the row. A hold is invisible
 * - the sheet's only hold gesture elsewhere (the heart) opens likers, and nothing
 * about a comment's body says "hold me to edit this" - so the control is a thing
 * the reader can see. 36dp because it sits beside a 34dp avatar and a name line
 * that are both smaller than it; the glyph is 18 of those.
 */
@Composable
private fun CommentManageButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .bouncyPress(pressedScale = 0.9f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onClick)
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
    if (text.isNullOrBlank()) return
    // Parsed once per reply; a comment thread is exactly the kind of list that
    // recomposes on every scroll, and reparsing markdown per frame is the cost
    // this `remember` removes.
    val nodes = remember(text) { AniListMarkdownParser.parse(text) }
    AniListMarkdownView(nodes = nodes)
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

@Composable
private fun CommentComposer(
    canPost: Boolean,
    isPosting: Boolean,
    postError: String?,
    onPost: (String) -> Unit
) {
    // The draft is owned here and cleared only when the post succeeds: clearing
    // optimistically would delete what the user wrote the moment a request
    // failed, which is the worst possible time to lose it. `awaitingPost` is what
    // distinguishes "a post finished" from "the thread loaded".
    var draft by rememberSaveable { mutableStateOf("") }
    var awaitingPost by remember { mutableStateOf(false) }

    LaunchedEffect(isPosting, postError) {
        if (awaitingPost && !isPosting) {
            if (postError == null) draft = ""
            awaitingPost = false
        }
    }

    val trimmed = draft.trim()
    // The one place the API's floor and ceiling are enforced on the way *in*.
    // The button used to enable on `isNotBlank()`, so a one-character comment was
    // accepted by the field, sent, and refused by the server as a post failure.
    val canSend = canPost && !isPosting && trimmed.length >= MIN_ACTIVITY_REPLY_LENGTH
    val submit = {
        if (canSend) {
            awaitingPost = true
            onPost(trimmed)
        }
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding()) {
        if (!canPost) {
            Text(
                text = "Sign in to AniList to join the conversation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                // Past the schema's 8000 the field simply stops accepting text,
                // rather than letting the reader write a comment AniList would
                // reject on send.
                onValueChange = { draft = it.take(MAX_ACTIVITY_REPLY_LENGTH) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("activity_comment_field"),
                placeholder = { Text("Add a comment") },
                enabled = !isPosting,
                maxLines = 4,
                shape = MaterialTheme.shapes.large,
                // Send from the keyboard, like every chat this sheet is one of.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() })
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = submit,
                enabled = canSend,
                modifier = Modifier.testTag("activity_comment_send")
            ) {
                Icon(
                    imageVector = AppVectorIcons.ActivitySend,
                    contentDescription = "Post comment"
                )
            }
        }

        // Only ever drawn for a draft that is one character short of sendable, so
        // it explains the disabled button instead of chasing every keystroke.
        if (canPost && !isPosting && draft.isNotEmpty() && trimmed.length < MIN_ACTIVITY_REPLY_LENGTH) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "A comment needs at least $MIN_ACTIVITY_REPLY_LENGTH characters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (postError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = postError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * The manage dialog for one of the viewer's own comments.
 *
 * Three steps in one window: the menu, the editor, and the confirmation. State -
 * which comment, which step, the draft in flight, the failure of the last
 * attempt - lives in [CommentThreadStore] rather than in the sheet, so a request
 * that outlives a recomposition still has somewhere to land and a failure is
 * still on screen next to the control that will retry it.
 *
 * The busy states rename their buttons rather than disabling silently: a delete
 * is a round trip, and a button that goes grey with nothing else changing reads
 * as a broken tap.
 */
@Composable
private fun ReplyActionDialog(
    action: CommentThreadStore.ReplyAction,
    onMode: (CommentThreadStore.ReplyAction.Mode) -> Unit,
    onDraftChange: (String) -> Unit,
    onSubmitEdit: () -> Unit,
    onSubmitDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    when (action.mode) {
        CommentThreadStore.ReplyAction.Mode.ACTIONS -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Your comment") },
            confirmButton = {
                TextButton(onClick = { onMode(CommentThreadStore.ReplyAction.Mode.EDIT) }) {
                    Text("Edit")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onMode(CommentThreadStore.ReplyAction.Mode.CONFIRM_DELETE) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        )

        CommentThreadStore.ReplyAction.Mode.EDIT -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit comment") },
            text = {
                Column {
                    OutlinedTextField(
                        value = action.draft,
                        onValueChange = onDraftChange,
                        enabled = !action.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activity_reply_edit_field"),
                        // The same 2..8000 window the composer enforces, from the
                        // one place both read: see [MIN_ACTIVITY_REPLY_LENGTH].
                        placeholder = { Text("Edit your comment") },
                        maxLines = 6,
                        shape = MaterialTheme.shapes.large
                    )
                    if (action.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = action.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onSubmitEdit,
                    enabled = !action.isBusy &&
                        action.draft.trim().length >= MIN_ACTIVITY_REPLY_LENGTH
                ) {
                    Text(if (action.isBusy) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !action.isBusy) {
                    Text("Cancel")
                }
            }
        )

        CommentThreadStore.ReplyAction.Mode.CONFIRM_DELETE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete this comment?") },
            text = {
                Column {
                    Text("This can't be undone.")
                    if (action.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = action.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onSubmitDelete, enabled = !action.isBusy) {
                    Text(
                        text = if (action.isBusy) "Deleting..." else "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !action.isBusy) {
                    Text("Cancel")
                }
            }
        )
    }
}
