package com.anisequel.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.anisequel.data.model.ActivityReply
import com.anisequel.domain.usecase.ContentLimits
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
 * ## The sheet owns its insets, not just its height
 *
 * `contentWindowInsets` is zeroed and this composable pads for the keyboard and
 * the navigation bar itself, once. Material's default is to apply the system
 * bars *inside* the sheet's measured surface, which means the sheet's own height
 * changes as the keyboard animates in - the anchored draggable re-measures
 * against a moving `Expanded` anchor on every frame. The composer opens with the
 * keyboard, so that is exactly when the sheet collapsed and jumped under the
 * reader. With the insets moved inside this fixed-height column, the sheet's
 * outline stays still and only its contents make room for the keyboard.
 *
 * It is one padding of `ime union navigationBars`, not `imePadding` followed by
 * `navigationBarsPadding`: the IME inset on gesture navigation already covers the
 * nav bar's height, so adding both left a nav bar's worth of empty surface
 * between the composer and the keyboard. Because the padding is applied here, the
 * entry bar and the composer's bottom bar do not pad for the navigation bar
 * themselves - they did, and that was the second copy.
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
 *
 * ## The thread itself lives in `CommentThread.kt`
 *
 * This file used to be 763 lines and carried the list, the rows, the skeleton,
 * the empty state and the manage dialog's launcher alongside the draft and the
 * two effects. The split is by responsibility: this keeps what is true of the
 * sheet as a whole - which pane is showing, the draft both panes share, and what
 * happens when the post lands - and the list keeps everything about drawing a
 * conversation.
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
     * which would be a heart that does nothing - see `EngagementAction`'s note on the
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

    // A post is a round trip, and the editor has to stay open for it: on success
    // the draft is cleared and the thread comes back, on failure the text and the
    // reason stay exactly where the retry is.
    PostRoundTrip(
        isPosting = state.isPosting,
        postError = state.postError,
        awaitingPost = awaitingPost,
        onDraftCleared = {
            draft = TextFieldValue("")
            replyToName = null
            composing = false
        },
        onPostSettled = { awaitingPost = false }
    )

    // Keyed on `composing` as well as the id: the effect runs while the editor is
    // still up in the same frame the post lands, and the list it wants to scroll
    // is composed one frame later, when the editor closes.
    ScrollToPostedReply(
        state = state,
        composing = composing,
        listState = listState
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // The sheet's own window-inset padding would resize the sheet as the
        // keyboard moves; see the file KDoc. Zero it and let the content below
        // own the IME and navigation-bar insets.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The editor is taller than the thread, exactly as AniList's is:
                // a paragraph needs the room, and the field grows into it. The
                // thread keeps a little more than it used to: the header, three
                // comments and the write bar is the common case, and 78% cut the
                // last one off.
                .fillMaxHeight(if (composing) 0.92f else 0.82f)
                // Above the list's own scroll, so leftover upward fling velocity
                // is spent here instead of bouncing the already-expanded sheet.
                .nestedScroll(rememberSheetFlingGuard(sheetState))
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
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
    ReplyActionPane(
        action = state.action,
        onMode = onReplyActionMode,
        onDraftChange = onReplyActionDraft,
        onSubmitEdit = onSubmitReplyEdit,
        onSubmitDelete = onSubmitReplyDelete,
        onDismiss = onDismissReplyAction
    )
}

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
private fun clipDraft(value: TextFieldValue): TextFieldValue {
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

/**
 * The round trip of a post, kept open so a failure can be retried.
 *
 * The editor stays up while the request is in flight: on success the draft is
 * cleared and the thread comes back, on failure the text and the reason stay
 * exactly where the retry is. [awaitingPost] is the local "we are waiting"
 * half, because the store owns the request and a rotation cannot restore it.
 */
@Composable
private fun PostRoundTrip(
    isPosting: Boolean,
    postError: String?,
    awaitingPost: Boolean,
    onDraftCleared: () -> Unit,
    onPostSettled: () -> Unit
) {
    LaunchedEffect(isPosting, postError) {
        if (!awaitingPost || isPosting) return@LaunchedEffect
        if (postError == null) onDraftCleared()
        onPostSettled()
    }
}

/**
 * Scrolls to a comment this session posted, and to nothing else.
 *
 * Keyed on `composing` as well as the id: the effect runs while the editor is
 * still up in the same frame the post lands, and the list it wants to scroll is
 * composed one frame later, when the editor closes. The reply is looked up by id
 * rather than by list position, because the list may have been rebuilt since the
 * post was recorded.
 */
@Composable
private fun ScrollToPostedReply(
    state: CommentThreadStore.CommentsState,
    composing: Boolean,
    listState: LazyListState
) {
    LaunchedEffect(state.postedReplyId, composing) {
        if (composing) return@LaunchedEffect
        val posted = state.postedReplyId ?: return@LaunchedEffect
        val row = postedReplyRow(
            replies = state.replies,
            postedReplyId = posted,
            hasRefreshBanner = state.loadError != null
        )
        if (row < 0) return@LaunchedEffect
        // An index past the end is clamped by `animateScrollToItem`, so a thread
        // that changed under us cannot throw here.
        listState.animateScrollToItem(row)
    }
}

/**
 * The row a posted reply occupies in the thread's `LazyColumn`, or -1 when it is
 * not in the list.
 *
 * The list is not the same thing as `replies`: a failed refresh puts a retry
 * banner above them (see `CommentsThread`), and the skeleton and empty states
 * replace the rows entirely. Reading `indexOfFirst` straight into
 * `animateScrollToItem` was one row short whenever that banner was up, so the
 * scroll stopped on the comment above the one just posted and left the reader's
 * own reply below the fold. Pulled out of the effect so that arithmetic is
 * checkable without a Compose runtime - the failure was invisible on screen
 * unless a refresh happened to fail at the same time as a post.
 */
internal fun postedReplyRow(
    replies: List<ActivityReply>,
    postedReplyId: Int,
    hasRefreshBanner: Boolean
): Int {
    val index = replies.indexOfFirst { it.id == postedReplyId }
    if (index < 0) return -1
    return index + if (hasRefreshBanner) 1 else 0
}

/**
 * The manage dialog, drawn as a sibling of the sheet.
 *
 * `AlertDialog` opens a window of its own, so the anchor script would not
 * survive the list recycling the row anyway, and the dialog has to outlive the
 * row that opened it - an edit in flight is anchored to an id, not to a position.
 */
@Composable
private fun ReplyActionPane(
    action: CommentThreadStore.ReplyAction?,
    onMode: (CommentThreadStore.ReplyAction.Mode) -> Unit,
    onDraftChange: (String) -> Unit,
    onSubmitEdit: () -> Unit,
    onSubmitDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    if (action != null) {
        ReplyActionDialog(
            action = action,
            onMode = onMode,
            onDraftChange = onDraftChange,
            onSubmitEdit = onSubmitEdit,
            onSubmitDelete = onSubmitDelete,
            onDismiss = onDismiss
        )
    }
}
