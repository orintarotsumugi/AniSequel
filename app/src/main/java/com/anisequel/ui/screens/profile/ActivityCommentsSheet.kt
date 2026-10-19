package com.anisequel.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.ActivityReply
import com.anisequel.domain.usecase.ContentLimits
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.EngagementAction
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.AniListMarkdownView
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.viewmodel.CommentThreadStore

/**
 * One activity's comment thread, with a composer.
 *
 * ## Two modes in one sheet
 *
 * The thread and the editor are not the same screen, and AniList's own app does
 * not pretend they are: the thread is a list with a "write a comment" bar pinned
 * under it, and pressing that bar opens an editor that takes most of the sheet,
 * with Cancel, a Preview step, the format bar and a counter. This sheet follows
 * that split because it solves a real problem: a composer that is the last row of
 * a long thread is only reachable by scrolling past every comment, and a composer
 * that replaces the list loses the conversation the reader was answering.
 *
 * The draft survives Cancel - it is held here, above both modes - so backing out
 * of the editor does not delete what was written. Only a successful post clears
 * it. That is the same reason [CommentThreadStore] holds the manage dialog's
 * draft, applied to the composer.
 *
 * ## Why the layouts have an explicit height, and why that is safe
 *
 * The old version avoided `weight` entirely, on the theory that a sheet measured
 * unbounded would crash a weighted column. A `LazyColumn` is already in this
 * sheet and works, which means the sheet hands down a bounded height - an
 * unbounded one is the "infinitely scrollable" crash. Given a definite height,
 * `weight(1f)` for the list is the thing that *keeps* the composer pinned: the
 * list takes what is left after the header and the bar, and cannot push the
 * composer off-screen. `fillMaxHeight` states that height explicitly rather than
 * relying on the sheet's wrap-content measurement to decide it.
 *
 * ## What happens when a comment lands
 *
 * A post is appended to the end of the thread, which is off-screen while the
 * editor that posted it covers the list. The store records the posted comment's
 * id for exactly this, and the sheet scrolls it into view once the editor closes.
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
 * reader gets the thread and a note instead of an editor that will fail.
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // The editor's whole state. Held here rather than in the composer because a
    // post clears it and Cancel must not: both are thread-level decisions, and
    // the composer is disposed when the mode flips.
    var composing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var replyToName by rememberSaveable { mutableStateOf<String?>(null) }
    // Not saveable: an in-flight request after a rotation is owned by the store,
    // and restoring the "we are waiting" half without it would swallow the answer.
    var awaitingPost by remember { mutableStateOf(false) }

    /**
     * The field's next value, with the ceiling applied to the text *and* the
     * selection.
     *
     * `TextFieldValue` requires its selection and composing region to fall inside
     * its text, and a copy that truncates one and keeps the other throws from the
     * constructor. A paste longer than 8,000 characters is the case: the field
     * hands back a selection at the end of the pasted text, and the old composer's
     * `.take(MAX)` on a `String` quietly hid the same mismatch behind a value type
     * that recomputes the caret. Here the caret is clamped, and the IME's
     * composing range is dropped rather than clamped - a zero-width composition
     * pinned to the end of the text is not a composing range any more.
     */
    fun clipDraft(value: TextFieldValue): TextFieldValue {
        if (value.text.length <= ContentLimits.REPLY_MAX) return value
        val text = ContentLimits.clipReply(value.text)
        return TextFieldValue(
            text = text,
            selection = TextRange(
                value.selection.start.coerceIn(0, text.length),
                value.selection.end.coerceIn(0, text.length)
            )
        )
    }

    // A post is a round trip, and the editor has to stay open for it: on success
    // the draft is cleared and the thread comes back, on failure the text and the
    // reason stay exactly where the retry is.
    LaunchedEffect(state.isPosting, state.postError) {
        if (!awaitingPost || state.isPosting) return@LaunchedEffect
        if (state.postError == null) {
            draft = TextFieldValue("")
            replyToName = null
            composing = false
        }
        awaitingPost = false
    }

    // Keyed on `composing` as well as the id: the effect runs while the editor is
    // still up in the same frame the post lands, and the list it wants to scroll
    // is composed one frame later, when the editor closes.
    LaunchedEffect(state.postedReplyId, composing) {
        if (composing) return@LaunchedEffect
        val posted = state.postedReplyId ?: return@LaunchedEffect
        val index = state.replies.indexOfFirst { it.id == posted }
        if (index < 0) return@LaunchedEffect
        // The reply's own position: the count lives above the list, not in it.
        // An index past the end is clamped by `animateScrollToItem`, so a thread
        // that changed under us cannot throw here.
        listState.animateScrollToItem(index)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The editor is taller than the thread, exactly as AniList's is:
                // a paragraph needs the room, and the field grows into it.
                .fillMaxHeight(if (composing) 0.92f else 0.78f)
                .imePadding()
                .testTag("activity_comments_sheet")
        ) {
            if (composing) {
                CommentComposer(
                    draft = draft,
                    onDraftChange = { draft = clipDraft(it) },
                    replyToName = replyToName,
                    onClearReplyTo = { replyToName = null },
                    isPosting = state.isPosting,
                    postError = state.postError,
                    onCancel = { composing = false },
                    onSubmit = {
                        val text = draft.text.trim()
                        if (!state.isPosting &&
                            ContentLimits.checkReply(text) == ContentLimits.ReplyVerdict.Valid
                        ) {
                            awaitingPost = true
                            onPost(text)
                        }
                    }
                )
            } else {
                CommentsThread(
                    state = state,
                    viewerId = viewerId,
                    canPost = canPost,
                    listState = listState,
                    shimmerPhase = shimmerPhase,
                    onRetry = onRetry,
                    onStartCompose = { composing = true },
                    // The mention is an edit like any toolbar's: it must not push
                    // the draft past the API's ceiling, and the caret lands after
                    // it so the reader keeps typing their comment.
                    onReply = { name ->
                        draft = draft.withDraftEdit(startReplyTo(draft.text, name))
                        replyToName = name
                        composing = true
                    },
                    onLikeReply = onLikeReply,
                    onManageReply = onOpenReplyActions
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

/**
 * The thread side of [ActivityCommentsSheet]: the header, the list, and the bar
 * that opens the editor.
 *
 * Its own composable because the sheet's two modes are genuinely different
 * screens sharing one draft - the editor is not the last row of the list - and
 * because the list's waiting, failed and empty branches each describe a whole
 * screen rather than a row. Keeping them here leaves [ActivityCommentsSheet]
 * with the draft, the two effects, and the mode switch, which is all it owns.
 */
@Composable
private fun ColumnScope.CommentsThread(
    state: CommentThreadStore.CommentsState,
    viewerId: Int?,
    canPost: Boolean,
    listState: LazyListState,
    shimmerPhase: State<Float>,
    onRetry: () -> Unit,
    onStartCompose: () -> Unit,
    onReply: (String) -> Unit,
    onLikeReply: (Int) -> Unit,
    onManageReply: (Int) -> Unit
) {
    CommentsHeader(count = state.replies.size)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        // The list is keyed by reply id, and a repeated key is a
        // hard `IllegalArgumentException` in Compose. The store
        // guarantees one entry per id; see `CommentsState.replies`.
        state = listState,
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        when {
            state.isLoading -> item(key = "loading") {
                // The rows that are coming, at the sizes they will
                // have. A spinner here was the one waiting state in
                // the app whose shape was already known and thrown away.
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
                    // A reply is addressed by a leading `@name`, so
                    // without an author name there is nothing to
                    // insert, even for a signed-in reader.
                    canReply = canPost && !reply.user?.name.isNullOrBlank(),
                    onLike = { onLikeReply(reply.id) },
                    onReply = onReply,
                    onManage = { onManageReply(reply.id) }
                )
            }
        }
    }

    CommentEntryBar(
        canPost = canPost,
        onClick = onStartCompose
    )
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
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
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

@Composable
private fun CommentRow(
    reply: ActivityReply,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The same control the activity card draws, shared rather than
                // re-declared here - `EngagementAction`. Left-aligned and below
                // the body rather than beside the name so a reply's own markdown
                // (which can be a multi-line quote or an image) is not squeezed
                // to make room for it.
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
 * A tap target of its own rather than a long press on the row. A hold is invisible
 * - the sheet's only hold gesture elsewhere (the heart) opens likers, and nothing
 * about a comment's body says "hold me to edit this" - so the control is a thing
 * the reader can see. It sits at the end of the comment's action row, the same
 * place AniList's own app puts it, rather than on the name line where it read as
 * part of the byline.
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
    // A child composable rather than an early return in this one: `remember`
    // after a return is a conditional composable call, and a reply whose text
    // AniList omitted would then occupy the slot the parser state wants.
    if (!text.isNullOrBlank()) MarkdownBody(text)
}

@Composable
private fun MarkdownBody(text: String) {
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
