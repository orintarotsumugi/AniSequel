package com.anisequel.ui.viewmodel

import com.anisequel.data.model.ActivityReply
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.usecase.ContentLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One activity's comment thread, and the writes that act on it.
 *
 * ## Why this is not part of `UserActivityViewModel`
 *
 * It shares the ViewModel's repository and scope, and nothing else. The feed is
 * a paged read of somebody's history; this is one thread's lifecycle - open,
 * load, post, like, edit, delete - with its own query documents and its own two
 * sheets. Each changes for its own reasons, and the ViewModel had grown past the
 * point where the comment half could be found in it. The one thing the two do
 * share is the feed's reply *count*, which is why the caller passes
 * [onReplyCountDelta] in rather than this class reaching back into the feed.
 *
 * ## Why the manage dialog is state here
 *
 * Unlike the composer's draft - free text with nowhere to go but the field - the
 * manage dialog is a two-step choice whose outcome has to survive the dialog: an
 * edit in flight is a request, its failure belongs beside the field that will be
 * retried, and a delete's failure must not vanish with a dismissed window.
 * Modelled as state, the whole flow is also reachable from a unit test, which a
 * `remember` inside the sheet is not.
 *
 * [viewerId] is the signed-in account, not the profile owner: a comment is
 * written by the viewer but the thread hangs off somebody else's activity. Null
 * means ownership cannot be decided - signed out, or a route that did not know
 * it - and no comment then has a menu, which is the safe half of the choice: the
 * server enforces ownership anyway, so the wrong half would be a Delete that can
 * only fail.
 */
class CommentThreadStore(
    private val repository: AniListRepository,
    private val scope: CoroutineScope,
    private val isDemo: Boolean,
    private val viewerId: Int?,
    /**
     * Called with the activity's id and `+1`/`-1` when a post or a delete lands,
     * so the feed's card can keep its reply count honest without a refetch.
     */
    private val onReplyCountDelta: (activityId: Int, delta: Int) -> Unit = { _, _ -> }
) {

    /**
     * The manage dialog's state, or null when it is not open.
     *
     * See the class KDoc for why this is not remembered in the sheet.
     */
    data class ReplyAction(
        val replyId: Int,
        val mode: Mode = Mode.ACTIONS,
        /** The edit draft. Seeded from the reply when the editor opens. */
        val draft: String = "",
        /** True while a request for this action is in flight. */
        val isBusy: Boolean = false,
        /**
         * Why the last attempt failed, or null.
         *
         * Kept on the dialog rather than in [CommentsState.postError] because it
         * belongs to the field being retried: the composer's error line is under
         * the composer, and an edit's must be under the editor.
         */
        val error: String? = null
    ) {
        enum class Mode {
            /** "Edit" / "Delete" / "Cancel". */
            ACTIONS,

            /** A text field over the comment, with Save and Cancel. */
            EDIT,

            /** "This can't be undone." */
            CONFIRM_DELETE
        }
    }

    /**
     * The comments sheet's state.
     *
     * Null when the sheet is closed, and non-null when it is open - the sheet's
     * visibility is derived from this rather than held beside it, so there is no
     * frame where a sheet is up with no thread under it.
     *
     * [activityId] is part of the state so a slow response for a thread the user
     * already closed and replaced with another one is discarded rather than
     * painted over the new thread.
     */
    data class CommentsState(
        val activityId: Int,
        val isLoading: Boolean = false,
        /**
         * True while a thread that already has comments on screen is being
         * fetched again.
         *
         * Kept apart from [isLoading] because the two states are the difference
         * between "there is nothing yet" and "what you were reading is still
         * here". Reopening a thread used to blank the list and redraw the
         * skeleton, so every accidental dismiss-and-reopen cost a second of a
         * conversation the app had already fetched - and a failed refresh left
         * the reader with an error where their comments used to be. The store
         * keeps the last closed thread (see [close]) and seeds it here.
         */
        val isRefreshing: Boolean = false,
        /**
         * The thread, at most one entry per reply id.
         *
         * The sheet keys its `LazyColumn` by id, and Compose treats a repeated key
         * as a hard `IllegalArgumentException` rather than as a duplicate row - the
         * list crashes instead of drawing. So the list is assembled with uniqueness
         * enforced here, where it has two sources: the fetch, and the reply a post
         * appends. Those two are allowed to agree about an id, and `open` and
         * `post` each make sure they cannot leave the state holding it twice.
         */
        val replies: List<ActivityReply> = emptyList(),
        val loadError: String? = null,
        val isPosting: Boolean = false,
        val postError: String? = null,
        /**
         * Replies with a like request in flight.
         *
         * The same job as `ActivityState.Success.pendingLikes`, for the same reason:
         * a double tap on a heart has to not toggle twice, and the optimistic count
         * already on screen is what the second tap would be applied to.
         */
        val pendingReplyLikes: Set<Int> = emptySet(),
        /**
         * The id of the comment this session posted, once it lands.
         *
         * The sheet scrolls to it, and it is a recorded id rather than "watch the
         * list grow" because the list also grows when the thread *loads* - and
         * scrolling to the bottom of a thread the reader has not seen yet would
         * hide its first comments behind an effect nobody asked for.
         */
        val postedReplyId: Int? = null,
        /** The manage dialog: its visibility, its step, and its request. */
        val action: ReplyAction? = null
    )

    private val _state = MutableStateFlow<CommentsState?>(null)
    val state: StateFlow<CommentsState?> = _state.asStateFlow()

    /**
     * The thread most recently closed, and the activity it belongs to.
     *
     * One entry, deliberately: this is the "I dismissed the sheet by accident"
     * case, not an offline cache. Keeping every thread would grow for as long as
     * the app is open and would need an eviction rule that nothing else here
     * wants; keeping the last one costs one list and makes the reopen instant.
     * The activity id is stored rather than inferred, because the next thread
     * the reader opens is usually a different one and must not be shown somebody
     * else's comments for a frame.
     */
    private var cachedActivityId: Int? = null
    private var cachedReplies: List<ActivityReply> = emptyList()

    /**
     * Opens the comment thread for [activityId] and loads it.
     *
     * If this is the thread that was just closed, its comments are drawn
     * immediately and the fetch becomes a refresh: [CommentsState.isLoading]
     * stays false, [CommentsState.isRefreshing] is true, and a failure leaves
     * the rows where they are with [CommentsState.loadError] set. See
     * [CommentsState.isRefreshing] for the problem that fixed.
     */
    fun open(activityId: Int) {
        val cached = if (cachedActivityId == activityId) cachedReplies else emptyList()
        _state.value = CommentsState(
            activityId = activityId,
            isLoading = cached.isEmpty(),
            isRefreshing = cached.isNotEmpty(),
            replies = cached
        )
        scope.launch {
            val result = if (isDemo) {
                Result.success(emptyList())
            } else {
                // `repositoryResult`, not a bare call: a repository that throws
                // instead of answering returns a failure here, which this fold
                // draws as the thread's error row. A bare call left the throw to
                // the coroutine, and the coroutine took the app with it.
                repositoryResult { repository.getActivityReplies(activityId) }
            }
            // Discard a response for a thread the user has already closed or
            // replaced: the state is only written if it is still this thread.
            val current = _state.value?.takeIf { it.activityId == activityId } ?: return@launch
            result.fold(
                onSuccess = { replies ->
                    _state.value = current.copy(
                        isLoading = false,
                        isRefreshing = false,
                        // First occurrence wins: the sheet draws one row per id, and
                        // the server's order is the conversation's order - see the
                        // unique-key note on [CommentsState.replies].
                        replies = replies.distinctBy { it.id },
                        loadError = null
                    )
                },
                onFailure = { error ->
                    _state.value = current.copy(
                        isLoading = false,
                        isRefreshing = false,
                        // The seeded rows stay: an error banner over a thread the
                        // reader can still read is better than replacing it with
                        // the error - and the retry is the banner's own button.
                        loadError = error.message ?: "Couldn't load comments."
                    )
                }
            )
        }
    }

    fun close() {
        val current = _state.value ?: return
        // Snapshot before the state is nulled. Replies, not the whole state: the
        // error, the draft and the manage dialog all describe a request or a
        // choice that has no meaning once the sheet is gone, and carrying them
        // into the next open is how a stale error appears under a fresh thread.
        cachedActivityId = current.activityId
        cachedReplies = current.replies
        _state.value = null
    }

    /** Posts [text] as a comment on the open thread. */
    fun post(text: String) {
        if (isDemo) return
        val current = _state.value ?: return
        val trimmed = text.trim()
        // The API's own window, not a preference: see [ContentLimits].
        if (current.isPosting || ContentLimits.checkReply(trimmed) != ContentLimits.ReplyVerdict.Valid) {
            return
        }

        val activityId = current.activityId
        _state.value = current.copy(isPosting = true, postError = null)

        scope.launch {
            repositoryResult { repository.postActivityReply(activityId, trimmed) }.fold(
                onSuccess = { reply ->
                    val latest = _state.value?.takeIf { it.activityId == activityId } ?: return@fold
                    _state.value = latest.copy(
                        isPosting = false,
                        postError = null,
                        // The posted reply moves to the end, and replaces an entry
                        // with the same id rather than sitting beside it: the
                        // server's copy is the authoritative one, and two rows keyed
                        // by one id is the crash [CommentsState.replies] describes.
                        replies = latest.replies.filterNot { it.id == reply.id } + reply,
                        // Recorded so the sheet can bring the new comment into
                        // view. See [CommentsState.postedReplyId].
                        postedReplyId = reply.id
                    )
                    onReplyCountDelta(activityId, 1)
                },
                onFailure = { error ->
                    _state.value = _state.value
                        ?.takeIf { it.activityId == activityId }
                        ?.copy(isPosting = false, postError = error.message ?: "Couldn't post that comment.")
                }
            )
        }
    }

    /**
     * Likes or un-likes one comment.
     *
     * The same shape as the feed's `toggleLike` - optimistic, one request in flight
     * per reply, rolled back on failure - against the reply's own id and namespace.
     *
     * `isLiked == null` refuses, exactly as it does for an activity: that is AniList
     * answering without a session, and guessing there would put the heart in a state
     * the server never agreed to.
     */
    fun toggleReplyLike(replyId: Int) {
        if (isDemo) return
        val current = _state.value ?: return
        if (replyId in current.pendingReplyLikes) return
        val index = current.replies.indexOfFirst { it.id == replyId }
        if (index < 0) return
        val reply = current.replies[index]
        val wasLiked = reply.isLiked ?: return

        val optimistic = reply.copy(
            isLiked = !wasLiked,
            likeCount = ((reply.likeCount ?: 0) + if (wasLiked) -1 else 1).coerceAtLeast(0)
        )

        fun write(replacement: ActivityReply, pending: Set<Int>) {
            val live = _state.value ?: return
            val at = live.replies.indexOfFirst { it.id == replyId }
            if (at < 0) return
            _state.value = live.copy(
                replies = live.replies.toMutableList().also { it[at] = replacement },
                pendingReplyLikes = pending
            )
        }

        write(optimistic, current.pendingReplyLikes + replyId)

        scope.launch {
            repositoryResult { repository.toggleActivityReplyLike(replyId) }.fold(
                onSuccess = { result ->
                    write(
                        optimistic.copy(isLiked = result.isLiked, likeCount = result.likeCount),
                        current.pendingReplyLikes
                    )
                },
                onFailure = {
                    write(reply, current.pendingReplyLikes)
                }
            )
        }
    }

    /**
     * Opens the manage dialog for [replyId] - if it is yours to manage.
     *
     * The ownership check is enforced here as well as in the sheet that draws the
     * menu. The sheet needs it to decide whether to draw the control at all; the
     * store needs it because it is the half a test can reach, and because "the
     * server would refuse anyway" is a sentence about a round trip, not about a UI.
     */
    fun openReplyActions(replyId: Int) {
        if (isDemo) return
        val current = _state.value ?: return
        val reply = current.replies.firstOrNull { it.id == replyId } ?: return
        if (reply.user?.id == null || reply.user.id != viewerId) return
        _state.value = current.copy(action = ReplyAction(replyId = replyId))
    }

    /**
     * Moves the dialog to [mode].
     *
     * Entering [ReplyAction.Mode.EDIT] reseeds the draft from the stored comment,
     * so reopening the editor after a cancelled attempt starts from the text on
     * screen rather than from a stale one. The other modes keep the draft: Cancel
     * from the editor and Back from the confirmation both mean "this dialog", and
     * dropping the text on the way to the menu is how a cancelled edit comes back
     * empty.
     */
    fun setReplyActionMode(mode: ReplyAction.Mode) {
        val current = _state.value ?: return
        val action = current.action ?: return
        if (action.isBusy) return
        val draft = if (mode == ReplyAction.Mode.EDIT) {
            current.replies.firstOrNull { it.id == action.replyId }?.text.orEmpty()
        } else {
            action.draft
        }
        _state.value = current.copy(
            action = action.copy(mode = mode, draft = draft, error = null)
        )
    }

    /** Rewrites the edit draft as the reader types. */
    fun updateReplyActionDraft(text: String) {
        val current = _state.value ?: return
        val action = current.action ?: return
        _state.value = current.copy(
            action = action.copy(
                draft = ContentLimits.clipReply(text),
                error = null
            )
        )
    }

    /** Saves the draft over the comment the dialog was opened for. */
    fun submitReplyEdit() {
        val current = _state.value ?: return
        val action = current.action ?: return
        if (action.mode != ReplyAction.Mode.EDIT || action.isBusy) return
        val text = action.draft.trim()
        if (ContentLimits.checkReply(text) != ContentLimits.ReplyVerdict.Valid) return
        val reply = current.replies.firstOrNull { it.id == action.replyId } ?: return

        _state.value = current.copy(action = action.copy(isBusy = true, error = null))

        scope.launch {
            repositoryResult {
                repository.editActivityReply(reply.id, current.activityId, text)
            }.fold(
                onSuccess = { updated ->
                    // The same guard every other write on this thread uses: the
                    // sheet may have been closed or replaced while the request was
                    // out, and a response for a dialog that is gone has nowhere to
                    // land.
                    val latest = _state.value?.takeIf { it.activityId == current.activityId }
                        ?: return@fold
                    if (latest.action?.replyId != reply.id) return@fold
                    _state.value = latest.copy(
                        replies = latest.replies.map { if (it.id == reply.id) updated else it },
                        action = null
                    )
                },
                onFailure = { error ->
                    val latest = _state.value?.takeIf { it.activityId == current.activityId }
                        ?: return@fold
                    if (latest.action?.replyId != reply.id) return@fold
                    _state.value = latest.copy(
                        action = latest.action.copy(
                            isBusy = false,
                            error = error.message ?: "Couldn't save that edit."
                        )
                    )
                }
            )
        }
    }

    /**
     * Deletes the comment the dialog was opened for.
     *
     * Not optimistic, unlike a like: putting the row back after a failed delete
     * would be the row and its thread position attempted twice, and a delete is
     * rare enough that the busy state costs nothing. The row leaves the list on
     * the server's answer, and on a failure the dialog stays up with the reason.
     */
    fun submitReplyDelete() {
        val current = _state.value ?: return
        val action = current.action ?: return
        if (action.mode != ReplyAction.Mode.CONFIRM_DELETE || action.isBusy) return
        val reply = current.replies.firstOrNull { it.id == action.replyId } ?: return

        _state.value = current.copy(action = action.copy(isBusy = true, error = null))

        scope.launch {
            repositoryResult { repository.deleteActivityReply(reply.id) }.fold(
                onSuccess = {
                    val latest = _state.value?.takeIf { it.activityId == current.activityId }
                        ?: return@fold
                    if (latest.action?.replyId != reply.id) return@fold
                    _state.value = latest.copy(
                        replies = latest.replies.filterNot { it.id == reply.id },
                        action = null
                    )
                    // The mirror of the bump a post does, so the feed's count
                    // still describes the thread AniList is holding.
                    onReplyCountDelta(current.activityId, -1)
                },
                onFailure = { error ->
                    val latest = _state.value?.takeIf { it.activityId == current.activityId }
                        ?: return@fold
                    if (latest.action?.replyId != reply.id) return@fold
                    _state.value = latest.copy(
                        action = latest.action.copy(
                            isBusy = false,
                            error = error.message ?: "Couldn't delete that comment."
                        )
                    )
                }
            )
        }
    }

    /**
     * Closes the manage dialog.
     *
     * Refused while a request is in flight: the answer still has to be written
     * somewhere, and a dialog that vanished mid-request would drop a failure the
     * reader needs to see. The sheet's Cancel is disabled on the same rule, so
     * this is the scrim tap and the back gesture.
     */
    fun dismissReplyAction() {
        val current = _state.value ?: return
        if (current.action?.isBusy == true) return
        _state.value = current.copy(action = null)
    }
}
