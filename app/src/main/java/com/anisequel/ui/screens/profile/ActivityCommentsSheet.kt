package com.anisequel.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.anisequel.data.model.ActivityReply
import com.anisequel.data.network.CatboxApi
import com.anisequel.data.network.SafebooruGifs
import com.anisequel.data.network.TenorGifs
import com.anisequel.data.repository.PickedImageReader
import com.anisequel.domain.usecase.CommentWindow
import com.anisequel.domain.usecase.ContentLimits
import com.anisequel.domain.usecase.MediaAttachment
import com.anisequel.ui.viewmodel.CommentThreadStore
import com.anisequel.ui.viewmodel.GifSearchStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    onDismissReplyAction: () -> Unit = {},
    /** Reveals the page of comments older than everything on screen. */
    onLoadOlder: () -> Unit = {},
    /** Takes in the comments a background check found and clears the count. */
    onRevealNewComments: () -> Unit = {},
    /** Runs one background check; the flag says whether the reader is at the end. */
    onCheckForNewComments: (atBottom: Boolean) -> Unit = {},
    /** Opens the "liked by" sheet for one comment. */
    onShowCommentLikers: (Int) -> Unit = {},
    /**
     * How often an open sheet asks for new comments, from the store that owns the
     * interval. Passed in rather than defaulted here so the sheet does not name the
     * window class the interval lives in, and so a caller cannot silently stop
     * polling by forgetting a default.
     */
    pollIntervalMillis: Long,
    /** Whether the sheet may ask at all - the reader's Settings switch. */
    polling: Boolean = true,
    /** The reader's Tenor key, when they saved one. Blank means the anonymous index. */
    tenorApiKey: String? = null,
    /** The profile's minute-tick, so "12m" becomes "1h" while the sheet is open. */
    nowEpochSecond: Long? = null
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

    // Rewriting one of the viewer's own comments is the composer again, not a
    // second editor in an `AlertDialog`. The draft still lives in the store
    // (the edit is anchored to a reply id, and the response outlives a
    // recomposition), so this `TextFieldValue` is the field's own value while
    // every change is mirrored back through `onReplyActionDraft`.
    //
    // Keyed on the reply id: the store reseeds its draft from the comment when
    // the mode moves to EDIT, and a cancelled edit must start from the text on
    // screen rather than from whatever the last attempt left behind.
    val editing = state.action?.takeIf {
        it.mode == CommentThreadStore.ReplyAction.Mode.EDIT
    }
    var editDraft by remember(editing?.replyId) {
        mutableStateOf(
            TextFieldValue(
                text = editing?.draft.orEmpty(),
                selection = TextRange(editing?.draft?.length ?: 0)
            )
        )
    }

    fun insertIntoDraft(edit: DraftEdit) {
        draft = draft.withDraftEdit(edit)
    }

    // Attachments. AniList has nowhere to put a file, so a photo leaves the app
    // for catbox.moe and comes back as the URL that gets written into the
    // comment - which is why the note is kept beside the composer rather than
    // in the store: the reader is looking at the field they are typing, and the
    // upload belongs to the draft, not to the thread.
    //
    // Two instances, because the two drafts are two fields: a photo picked while
    // editing must land in the edit field, not in the new-comment draft waiting
    // behind it.
    val photo = rememberPhotoAttachment { url ->
        insertIntoDraft(
            insertBlock(
                draft.text,
                draft.selection.start,
                draft.selection.end,
                MediaAttachment.embedMarkdown(url)
            )
        )
    }
    val editPhoto = rememberPhotoAttachment { url ->
        val next = editDraft.withDraftEdit(
            insertBlock(
                editDraft.text,
                editDraft.selection.start,
                editDraft.selection.end,
                MediaAttachment.embedMarkdown(url)
            )
        )
        editDraft = next
        onReplyActionDraft(next.text)
    }

    val scope = rememberCoroutineScope()
    val safebooru = remember { SafebooruGifs() }
    // Tenor when the reader has a key for it, Safebooru when they have not. The
    // booru is the default because it answers with anime GIFs and asks for nothing:
    // Tenor's public API is discontinued, and a reader who has not registered a
    // Google key still gets a working picker.
    val gifStore = remember(safebooru, tenorApiKey) {
        GifSearchStore(
            scope = scope,
            provider = tenorApiKey?.takeIf { it.isNotBlank() }?.let { TenorGifs(it) } ?: safebooru
        )
    }

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
                .fillMaxHeight(if (composing || editing != null) 0.92f else 0.82f)
                // Above the list's own scroll, so leftover upward fling velocity
                // is spent here instead of bouncing the already-expanded sheet.
                .nestedScroll(rememberSheetFlingGuard(sheetState))
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .testTag("activity_comments_sheet")
        ) {
            when {
                // An edit replaces the thread exactly as writing a new comment
                // does, with the same header, preview, format bar, counter and
                // attachment controls. It used to be a bare `OutlinedTextField`
                // in an `AlertDialog`: no markup, no preview, and a second editor
                // to keep in step with the first.
                editing != null -> CommentComposer(
                    draft = editDraft,
                    onDraftChange = { next ->
                        val clipped = clipDraft(next)
                        editDraft = clipped
                        onReplyActionDraft(clipped.text)
                    },
                    replyToName = null,
                    onClearReplyTo = {},
                    isPosting = editing.isBusy,
                    postError = editing.error,
                    uploadNote = editPhoto.note,
                    isUploading = editPhoto.isUploading,
                    onPickFromDevice = editPhoto.launch,
                    gifPane = { onUse ->
                        GifBrowser(store = gifStore, onUse = onUse)
                    },
                    onCancel = onDismissReplyAction,
                    onSubmit = onSubmitReplyEdit,
                    title = "Edit Comment",
                    submitLabel = "Save",
                    busyLabel = "Saving..."
                )

                composing -> CommentComposer(
                    draft = draft,
                    onDraftChange = { draft = clipDraft(it) },
                    replyToName = replyToName,
                    onClearReplyTo = { replyToName = null },
                    isPosting = state.isPosting,
                    postError = state.postError,
                    uploadNote = photo.note,
                    isUploading = photo.isUploading,
                    onPickFromDevice = photo.launch,
                    gifPane = { onUse ->
                        GifBrowser(store = gifStore, onUse = onUse)
                    },
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

                else -> CommentsThread(
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
                    onQuote = { reply ->
                        // The quote is an edit like any other the bar makes: it goes
                        // in at the caret, the draft is clipped to the API's ceiling,
                        // and the reader keeps typing under it.
                        //
                        // It also has to thread. AniList puts no parent pointer on a
                        // reply - the leading `@name` is the whole mechanism - so
                        // quoting somebody without mentioning them posted a comment at
                        // the root of the thread, and the grouping then merged it as a
                        // continuation of the *quoting* reader, which is the one thing
                        // a quote should not look like. The credit line names the
                        // author to the reader; the mention is what makes the site
                        // put the comment under their post.
                        val name = reply.user?.name?.trim()?.takeIf { it.isNotEmpty() }
                        val mentioned = startReplyTo(draft.text, name.orEmpty())
                        val quote = quoteBlock(
                            text = reply.text.orEmpty(),
                            authorName = name,
                            addressedTo = name
                        )
                        draft = mentioned.withDraftEdit(
                            insertBlock(
                                mentioned.text,
                                mentioned.selectionStart,
                                mentioned.selectionEnd,
                                quote
                            )
                        )
                        replyToName = name
                        composing = true
                    },
                    onLikeReply = onLikeReply,
                    onManageReply = onOpenReplyActions,
                    onShowCommentLikers = onShowCommentLikers,
                    onLoadOlder = onLoadOlder,
                    onRevealNewComments = onRevealNewComments,
                    onCheckForNewComments = onCheckForNewComments,
                    pollIntervalMillis = pollIntervalMillis,
                    polling = polling,
                    nowEpochSecond = nowEpochSecond
                )
            }
        }
    }

    // The menu and the delete confirmation, as siblings of the sheet rather than
    // rows in it: `AlertDialog` opens a window of its own, so the anchor script
    // would not survive the list recycling the row anyway, and the dialog has to
    // outlive the row that opened it. The edit step is the sheet's own composer
    // pane instead - see the `when` above.
    ReplyActionPane(
        action = state.action,
        onMode = onReplyActionMode,
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
 * One draft's photo picker: the launcher, and the upload it is currently doing.
 *
 * The picker is a round trip through catbox.moe, so it has to report two things
 * the field must draw - "Uploading…" and why an upload failed - beside the
 * composer. Pulled out of [ActivityCommentsSheet] because the edit pane needs
 * the same picker against a different draft, and two copies of the upload would
 * drift the first time either was fixed.
 */
private data class PhotoAttachment(
    val launch: () -> Unit,
    val isUploading: Boolean,
    val note: String?
)

@Composable
private fun rememberPhotoAttachment(onUploaded: (String) -> Unit): PhotoAttachment {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val catbox = remember { CatboxApi() }
    var isUploading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isUploading = true
        note = "Uploading…"
        scope.launch {
            // Written with a `finally` rather than `mapCatching`/`fold`, because a
            // cancelled upload is neither a success nor a failure: `mapCatching`
            // catches `Throwable`, so closing the sheet mid-transfer took the
            // cancellation as its "failure", wrote a reason the reader never asked
            // for, and left `isUploading` true on the way out - a Send button
            // disabled by an upload that had already been thrown away.
            try {
                val image = PickedImageReader.read(context, uri).getOrThrow()
                val url = catbox.upload(image.bytes, image.fileName, image.mimeType).getOrThrow()
                onUploaded(url)
                note = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                note = failure.message ?: "The upload did not go through."
            } finally {
                isUploading = false
            }
        }
    }

    return PhotoAttachment(
        launch = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        isUploading = isUploading,
        note = note
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
            // The thread shows one banner row for any failed request, and one Older
            // row when the window does not cover the thread; both push every reply
            // down by one, and the scroll has to count them the way the list does.
            // That means the Older row's own condition - the rows *drawn*, not the
            // rows fetched: on a thread the reader had scrolled up in the two differ,
            // and counting the fetched ones here said "no Older row" while the list
            // was drawing one, so the scroll landed a row short of the comment just
            // posted. [CommentWindow.visibleRowCount] is the one answer both sides use.
            hasBannerRow = state.loadError != null ||
                state.windowError != null ||
                state.refreshError != null,
            hasOlderRow = state.isChatView && CommentWindow.hiddenCount(
                replyCount = state.replyCount,
                loadedCount = CommentWindow.visibleRowCount(
                    repliesLoaded = state.replies.size,
                    shownRows = state.shownRows
                )
            ) > 0
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
 * The list is not the same thing as `replies`: a failed read puts a retry banner
 * above them, chat view puts an Older row above that, and the skeleton and empty
 * states replace the rows entirely. Reading `indexOfFirst` straight into
 * `animateScrollToItem` was one row short whenever that banner was up, so the
 * scroll stopped on the comment above the one just posted and left the reader's
 * own reply below the fold. Pulled out of the effect so that arithmetic is
 * checkable without a Compose runtime - the failure was invisible on screen
 * unless a refresh happened to fail at the same time as a post.
 */
internal fun postedReplyRow(
    replies: List<ActivityReply>,
    postedReplyId: Int,
    hasBannerRow: Boolean,
    hasOlderRow: Boolean = false
): Int {
    val index = replies.indexOfFirst { it.id == postedReplyId }
    if (index < 0) return -1
    // Counted with statements rather than `index + (if (...) 1 else 0) + ...`:
    // dropping the parentheses to satisfy a lint rule changes what the operands
    // bind to, and keeping them invites the next reader to drop them. Two `if`s
    // cannot be misread either way.
    var row = index
    if (hasBannerRow) row++
    if (hasOlderRow) row++
    return row
}

/**
 * The manage dialog's menu and delete confirmation, drawn as a sibling of the
 * sheet.
 *
 * `AlertDialog` opens a window of its own, so the anchor script would not
 * survive the list recycling the row anyway, and the dialog has to outlive the
 * row that opened it - an edit in flight is anchored to an id, not to a position.
 *
 * EDIT is absent here on purpose: it is the sheet's own composer pane, not a
 * window on top of it (see [ActivityCommentsSheet]).
 */
@Composable
private fun ReplyActionPane(
    action: CommentThreadStore.ReplyAction?,
    onMode: (CommentThreadStore.ReplyAction.Mode) -> Unit,
    onSubmitDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    if (action != null) {
        ReplyActionDialog(
            action = action,
            onMode = onMode,
            onSubmitDelete = onSubmitDelete,
            onDismiss = onDismiss
        )
    }
}
