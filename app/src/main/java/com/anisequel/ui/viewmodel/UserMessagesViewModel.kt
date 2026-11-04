package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MessageNode
import com.anisequel.data.model.UserMessagesPage
import com.anisequel.data.model.asThreadHead
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.MessageVisibility
import com.anisequel.data.repository.MessageVisibilityPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
/**
 * State holder for the Messages tab: what the viewer sent, what arrived, and
 * the filters that separate them.
 *
 * ## Why two sources instead of one list
 *
 * AniList has no single "my messages" query. Sent messages are listable from
 * `Page.activities` by `messengerId`; received ones exist only as
 * `Page.notifications` entries carrying a `MessageActivity`. The two page
 * independently and can fail independently, so each keeps its own cursor,
 * `hasMore`, error and loading flag, and [MessageFilter.ALL] merges them at read
 * time. Sharing one state would mean a failed inbox fetch erasing the sent list
 * that had already loaded - the exact failure the activity feed's paging had to
 * be fixed for.
 *
 * ## Lazy, like the Activity tab
 *
 * Nothing is fetched in `init`. A profile opened to look at somebody's
 * favourites must not spend two requests on a tab nobody selected; the screen
 * calls [loadFirstPages] when the Messages segment is first shown. Once loaded,
 * switching chips never refetches - only a source that has not arrived yet is
 * asked for.
 */
class UserMessagesViewModel(
    private val aniListRepository: AniListRepository,
    viewerId: Int?,
    private val isDemo: Boolean = false,
    /**
     * The reader's own view of their inbox: which rows they dismissed, whether new
     * arrivals are flagged, and since when they have looked.
     *
     * Injected rather than read from a `Context` here because this class is built by
     * a [Factory] the route owns, and the demo profile has no reader to keep
     * dismissals for. The demo path passes the preferences anyway - hiding a fixture
     * row has to behave like hiding a real one, or the two halves of the tab drift.
     */
    private val visibilityPreferences: MessageVisibilityPreferences
) : ViewModel() {

    /**
     * The id a row's direction is decided against.
     *
     * In a real session this is the route's viewer id. The demo has no session,
     * but its fixture still has a sent half, and that half belongs to
     * [com.anisequel.data.repository.DemoDataProvider.DEMO_VIEWER_ID] - read
     * from the same profile record the rest of the demo is built on, so the
     * fixture and the direction test cannot disagree about whose account it is.
     */
    private val viewerId: Int? =
        viewerId ?: if (isDemo) aniListRepository.getDemoProfile().id else null

    /**
     * One source's page of messages, plus everything needed to page and retry it.
     *
     * [loaded] is kept apart from `messages.isEmpty()` because an inbox that
     * really is empty must stop being asked for: without it every re-entry would
     * refetch an empty list forever.
     */
    data class SourceState(
        val messages: List<MessageNode> = emptyList(),
        val loaded: Boolean = false,
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val error: String? = null,
        val loadMoreError: String? = null
    )

    data class UiState(
        val filter: MessageFilter = MessageFilter.ALL,
        val received: SourceState = SourceState(),
        val sent: SourceState = SourceState(),
        /**
         * Whose id decides "you sent this" - carried in the state so the card
         * does not have to re-derive the demo's viewer id from the route, where
         * it is null by definition.
         */
        val viewerId: Int? = null,
        /**
         * What the reader has taken out of the list and what they have not read.
         *
         * Held in the state rather than read by the composable from its own flow:
         * the derivations that decide what the tab draws need it, and a second
         * subscription would give the list and its empty state two answers about the
         * same dismissal.
         */
        val visibility: MessageVisibility = MessageVisibility()
    )

    private val _state = MutableStateFlow(UiState(viewerId = viewerId))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Not a network fetch, so the tab's lazy rule is untouched: nothing here
        // asks AniList for anything, and a profile opened to look at somebody's
        // favourites still costs one request.
        viewModelScope.launch {
            visibilityPreferences.visibility.collect { visibility ->
                _state.value = _state.value.copy(visibility = visibility)
            }
        }
    }

    /**
     * The one message the reader has opened, or null when no sheet is up.
     *
     * [message] is the row itself rather than an id to look up, because the inbox
     * is already in memory and the sheet is the one place a message has to be
     * readable the instant it is tapped: a target held by id would be a second
     * pass through the list for data this class is already holding, and would put
     * the sheet's content behind whatever that pass costs.
     *
     * [sent] and [counterpart] are resolved here rather than left to the sheet
     * because the id they are decided against is this class's own - see
     * [viewerId]. The card derives the same two from the same object, so the sheet
     * cannot disagree with the row it was opened from, and a demo fixture whose
     * viewer id comes from the profile rather than the route still reads in the
     * right direction.
     */
    data class MessageDetail(
        /**
         * The row that was tapped, or null when the sheet was opened from a name
         * rather than from the inbox.
         *
         * A conversation can start before its first message exists - tapping
         * "Message" on a friend's activity is exactly that - so the sheet cannot
         * require a row. Null means the sheet shows the conversation (possibly
         * empty) and the reply box, with no highlight and no sent/received chip.
         */
        val message: MessageNode?,
        val sent: Boolean,
        val counterpart: FollowUser?
    )

    /**
     * Held here, and not as `rememberSaveable` in the tab, for the two reasons the
     * likers tray is held in its ViewModel: the sheet is drawn as a sibling of the
     * tab content (so a profile-tab switch cannot tear it down mid-read - see
     * [com.anisequel.ui.screens.profile.ProfileTabsContent]), and this class
     * outlives a rotation without the open row having to be serialised.
     */
    private val _detail = MutableStateFlow<MessageDetail?>(null)
    val detail: StateFlow<MessageDetail?> = _detail.asStateFlow()

    /**
     * The reply thread AniList keeps for the message that is open.
     *
     * A site message is an activity, and the exchange under it is that
     * activity's replies - the same shape a comment thread has, and the reason
     * the site's own message page can show a conversation an inbox cannot. The
     * inbox is single messages - sent paged by `messengerId`, received only where
     * a notification exists - so matching those pages by id pair is how a thread
     * used to be assembled, and why it stopped at page one and missed replies
     * written on the site.
     *
     * [messageId] is the activity the replies belong to. It is held separately
     * from [UserMessagesViewModel.MessageDetail.message] so a late answer for a
     * sheet that has been closed, or reopened on somebody else, has somewhere to
     * be recognised as stale and dropped.
     */
    data class ThreadState(
        val messageId: Int? = null,
        val replies: List<ActivityReply> = emptyList(),
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        val error: String? = null
    )

    private val _thread = MutableStateFlow(ThreadState())
    val thread: StateFlow<ThreadState> = _thread.asStateFlow()

    /**
     * The request that reads the open thread. Cancelled when a different message
     * is opened, so a slow answer for the previous one cannot land over it.
     */
    private var threadJob: Job? = null

    /**
     * Opens [message] in the detail sheet and asks AniList for its thread.
     *
     * The row costs nothing: its body, sender, timestamp, privacy flag and site
     * URL arrived with the list it is in, so the sheet draws on the tap's frame.
     * The request is for the replies *under* it, which the inbox does not have;
     * a failure leaves the row readable with a retry where the thread should be.
     */
    fun openMessage(message: MessageNode) {
        // A "Sent to <name>" confirmation belongs to the sheet that earned it.
        // Opening a different message must not greet the reader with the last
        // one's outcome above a box they have not written in yet.
        if (!_reply.value.isSending) _reply.value = ReplyState()
        _detail.value = MessageDetail(
            message = message,
            sent = message.isSentBy(viewerId),
            counterpart = message.counterpartFor(viewerId)
        )
        // Every message is a thread, whether it was opened from the inbox or
        // from a friend's name - that is what makes a private exchange read like
        // the public comment thread it mirrors.
        loadThread(message.id)
        // The reader is looking at this one, and at everything older than it - which
        // is what the stamp means, and why it is taken now rather than per row.
        markInboxSeen(System.currentTimeMillis() / 1000L)
    }

    /**
     * The wait for a name-opened conversation to learn whether it has a history.
     * Kept short: the reader tapped a person, and both outcomes below draw a sheet.
     */
    private var conversationJob: Job? = null

    /**
     * Opens the conversation with [user], whether or not one exists yet.
     *
     * This is the way in from a friend's activity card. A conversation that already
     * exists opens in the comment sheet, on its newest message: that sheet is the
     * one that can quote a line and answer it, and the old bubbles-and-a-reply-box
     * sheet showed the same exchange with neither. The inbox is asked for here rather
     * than left to the Messages segment, because a conversation the reader has
     * already had is not something they should have to go and load first.
     *
     * A conversation that has *not* started has no `MessageActivity`, so there is
     * nothing to comment on and no thread to open, and that one case keeps the
     * compose sheet - it is the only place a first `SaveMessage` can be written.
     *
     * Messaging yourself is refused: the server would reject it, and a sheet
     * that offers a reply box for it is a control that can only fail.
     */
    fun openConversation(user: FollowUser) {
        if (user.id == viewerId) return
        // A confirmation belongs to the sheet that earned it. Opening a
        // conversation from the Global feed must not greet the reader with the
        // last person's "Sent to ..." - the same rule [openMessage] follows, and
        // the one this path used to skip.
        if (!_reply.value.isSending) _reply.value = ReplyState()
        conversationJob?.cancel()

        // Answered from what is already loaded, which is every case that started on
        // the Messages segment: no request, no sheet, no frame of the wrong UI. The
        // detail sheet is closed first, because opening a thread over the compose
        // sheet for somebody else would put two sheets on one screen.
        newestConversationWith(user.id)?.let { newest ->
            dismissMessage()
            openComments(newest)
            return
        }

        loadFirstPages()
        // A conversation that has not started has no activity and therefore no
        // replies. Clearing the previous thread is what stops the sheet from
        // drawing the last person's messages over this one's blank page.
        threadJob?.cancel()
        _thread.value = ThreadState()
        _detail.value = MessageDetail(
            message = null,
            sent = false,
            counterpart = user
        )

        // A cold inbox - the reader tapped "Message" on the Global feed without
        // ever opening their messages - is still a conversation, and it now resolves
        // into the comment sheet the moment the first pages arrive. Without this,
        // the one person they had talked to before got the sheet the reader had
        // already asked the app to stop showing them. The timeout is the whole of
        // the failure handling: an inbox that never answers leaves the compose sheet
        // up, which is the correct screen for a conversation with no history.
        conversationJob = viewModelScope.launch {
            val inbox = withTimeoutOrNull(CONVERSATION_WAIT_MILLIS) {
                state.first { it.received.loaded && it.sent.loaded }
            } ?: return@launch
            val newest = newestConversationWith(user.id, inbox) ?: return@launch
            // Only the sheet this tap opened: a dismissed sheet, or one reopened on
            // somebody else, is not ours to replace.
            val pending = _detail.value
            if (pending?.message != null || pending?.counterpart?.id != user.id) return@launch
            _detail.value = null
            openComments(newest)
        }
    }

    /** The newest message exchanged with [counterpartId], once both halves are in. */
    private fun newestConversationWith(
        counterpartId: Int,
        inbox: UiState = _state.value
    ): MessageNode? = conversationMessages(inbox, counterpartId, viewerId).lastOrNull()

    /** Closes the detail sheet. */
    fun dismissMessage() {
        conversationJob?.cancel()
        _detail.value = null
        threadJob?.cancel()
        _thread.value = ThreadState()
        if (!_reply.value.isSending) _reply.value = ReplyState()
    }

    /**
     * Reads the thread AniList holds for [messageId], once per open message.
     *
     * A repeat call while the same thread is loaded or loading is free - the same
     * lazy rule the inbox's sources follow. [forceReRead] is the retry after a
     * failure, and the only caller that may spend a second request on it.
     */
    fun loadThread(messageId: Int, forceReRead: Boolean = false) {
        // The demo has no server-side activities: its fixtures carry no thread,
        // and asking the network for one of its ids would spend a request on a
        // row that does not exist. The inbox halves still draw the conversation.
        if (isDemo) return
        val current = _thread.value
        if (!forceReRead && current.messageId == messageId && (current.isLoading || current.loaded)) {
            return
        }
        threadJob?.cancel()
        _thread.value = ThreadState(messageId = messageId, isLoading = true)
        threadJob = viewModelScope.launch {
            repositoryResult {
                aniListRepository.getActivityReplies(messageId)
            }.fold(
                onSuccess = { replies ->
                    if (_thread.value.messageId != messageId) return@fold
                    _thread.value = ThreadState(
                        messageId = messageId,
                        replies = replies.distinctBy { it.id },
                        loaded = true
                    )
                },
                onFailure = { failure ->
                    if (_thread.value.messageId != messageId) return@fold
                    _thread.value = ThreadState(
                        messageId = messageId,
                        loaded = true,
                        error = failure.message ?: "Couldn't load this conversation."
                    )
                }
            )
        }
    }

    /** Re-reads the open thread after its fetch failed. */
    fun retryThread() {
        val messageId = _detail.value?.message?.id ?: return
        loadThread(messageId, forceReRead = true)
    }

    /**
     * What the reply composer under the open message is doing.
     *
     * [sentTo] is a name rather than a flag because the confirmation reads "Sent to
     * <name>" - a bare "Sent" leaves the reader wondering which account received
     * it when the sheet was opened from the sent half of the inbox.
     */
    data class ReplyState(
        val isSending: Boolean = false,
        val error: String? = null,
        val sentTo: String? = null
    )

    private val _reply = MutableStateFlow(ReplyState())
    val reply: StateFlow<ReplyState> = _reply.asStateFlow()

    /** False for the demo and for any session without a viewer id. */
    val canReply: Boolean get() = !isDemo && viewerId != null

    /**
     * The comment thread on a message, with the same store the activity feed uses.
     *
     * A direct message is a `MessageActivity`, and AniList gives it replies the way
     * it gives any other activity one - so the sheet, the composer, the window, the
     * like handling and the moderation controls are the ones already built rather
     * than a second copy that would drift.
     *
     * Handed out whole instead of mirrored method by method: the activity view model
     * has twenty delegators over this same store, and this file is already at the
     * line ceiling `AGENTS.md` sets, so a page of pass-throughs would be a second
     * thing to keep in step with a class that is already public.
     *
     * One thread at a time, exactly as the feed's is. The comment sheet is a window
     * of its own, and a message and an activity post are never both being commented
     * on.
     */
    val comments: CommentThreadStore = CommentThreadStore(
        repository = aniListRepository,
        scope = viewModelScope,
        isDemo = isDemo,
        viewerId = viewerId
    )

    /**
     * Opens the comment thread on [message].
     *
     * A message row is an activity id away from the feed's own thread, so this only
     * aims the store: [knownReplyCount] rides along from the row's `replyCount`
     * because the window has to be placed by the thread's size before the first
     * request is made, and a chat that opens at the second-newest message is the bug
     * that number exists to avoid. `chatView` defaults to the shipped setting rather
     * than being read here: the sheet obeys the user's switch on open and on retry,
     * and this is the one call site that does not have the Settings flow's state.
     *
     * The tapped row rides along as [CommentThreadStore.CommentsState.head], so the
     * sheet shows the message at the top of its own replies. The messages tab used
     * to open a second sheet for the body - bubbles, a reply box under a scroll -
     * and the reader could not quote or answer one line of it. One sheet draws both,
     * which is the only way "the comment UI" can also be the message UI.
     */
    fun openComments(
        message: MessageNode,
        knownReplyCount: Int = message.replyCount ?: 0,
        chatView: Boolean = true
    ) = comments.open(
        activityId = message.id,
        knownReplyCount = knownReplyCount,
        chatView = chatView,
        head = message.asThreadHead()
    )

    /**
     * Sends [text] to the other party of the message the sheet has open.
     *
     * ## Two paths, because there are two situations
     *
     * An open message is an AniList activity with a reply thread, so the reply
     * belongs *in* that thread - `SaveActivityReply`, the same mutation the
     * site's own message page posts. A conversation opened from a friend's name
     * has no activity yet, so it starts one with `SaveMessage` and [isPrivate] is
     * the composer's public/private choice for that new message. The old code
     * sent a new message in both cases, which is what made a thread read as a list
     * of receipts: the answer landed beside the conversation instead of in it.
     *
     * The recipient is the row's *counterpart*, not its messenger: replying to a
     * message you sent has to go to the person you sent it to.
     */
    fun replyToOpenMessage(text: String, isPrivate: Boolean) {
        if (!canReply) return
        val open = _detail.value ?: return
        val body = text.trim()
        if (body.isEmpty() || _reply.value.isSending) return

        val openMessage = open.message
        if (openMessage != null) {
            postThreadReply(openMessage.id, body, open.counterpart?.name)
            return
        }

        // There is no open activity on this path, so the counterpart is the only
        // id there is to address. `message` is null by the branch above.
        val recipientId = open.counterpart?.id
        if (recipientId == null) {
            _reply.value = ReplyState(error = "There is no one to reply to on this message.")
            return
        }
        _reply.value = ReplyState(isSending = true)
        viewModelScope.launch {
            repositoryResult {
                aniListRepository.sendMessage(recipientId, body, isPrivate)
            }.fold(
                onSuccess = { messageId ->
                    // The send answered with the new message's id, so the sheet
                    // can show what was just written without waiting for the
                    // inbox to page again. This is what made a conversation read
                    // as one-sided: the reply landed, the box cleared, and the
                    // message only appeared after a manual refresh.
                    val counterpart = open.counterpart
                    val sent = MessageNode(
                        id = messageId,
                        message = body,
                        isPrivate = isPrivate,
                        createdAt = (System.currentTimeMillis() / 1000).toInt(),
                        recipientId = recipientId,
                        messengerId = viewerId,
                        recipient = counterpart
                    )
                    updateSource(MessageFilter.SENT) { current ->
                        current.copy(
                            messages = (listOf(sent) + current.messages).distinctBy { it.id }
                        )
                    }
                    // `ifBlank`, not `?:`: the name is a non-null field, so the fallback was
                    // never reachable as written and the compiler said so. An empty one is the
                    // case it meant to catch.
                    _reply.value = ReplyState(sentTo = counterpart.name.ifBlank { "them" })
                },
                onFailure = { failure ->
                    _reply.value = ReplyState(
                        error = failure.message ?: "Couldn't send that message."
                    )
                }
            )
        }
    }

    /**
     * Posts [body] into the open message's AniList reply thread.
     *
     * The reply is appended from the mutation's own answer rather than re-reading
     * the whole thread: `SaveActivityReply` returns the stored reply, its author
     * and its timestamp, so the reader sees the message they just sent without a
     * second round trip. A reply to a private message is private because the
     * activity is - there is no privacy argument to pass, which is the other half
     * of why a thread reply and a new message are two different calls.
     */
    private fun postThreadReply(activityId: Int, body: String, counterpartName: String?) {
        _reply.value = ReplyState(isSending = true)
        viewModelScope.launch {
            repositoryResult {
                aniListRepository.postActivityReply(activityId, body)
            }.fold(
                onSuccess = { reply ->
                    if (_thread.value.messageId == activityId) {
                        _thread.value = _thread.value.copy(
                            replies = (_thread.value.replies + reply).distinctBy { it.id },
                            loaded = true,
                            error = null
                        )
                    }
                    _reply.value = ReplyState(sentTo = counterpartName ?: "them")
                },
                onFailure = { failure ->
                    _reply.value = ReplyState(
                        error = failure.message ?: "Couldn't send that reply."
                    )
                }
            )
        }
    }

    /** Drops the composer's last outcome, for a fresh draft or a closed sheet. */
    fun clearReplyState() {
        if (!_reply.value.isSending) _reply.value = ReplyState()
    }

    // Cursors advance only on success, for the same reason the activity feed's
    // does: a refresh that failed over existing rows used to rewind the cursor
    // and the next scroll re-appended page one under page three.
    private var receivedNextPage = 1
    private var sentNextPage = 1
    private var receivedJob: Job? = null
    private var sentJob: Job? = null
    // Whether the inbox has ever been opened. The page-level refresh button asks
    // only the feeds that have been looked at, the same rule the Activity tab
    // follows: refreshing a page nobody opened is a request spent on nothing.
    private var started = false

    /**
     * Takes [message] out of the side of the inbox it belongs to.
     *
     * The side comes from the row's own direction, not from the selected chip: under
     * `All` one list holds both, and dismissing a message the reader wrote must not
     * also hide the reply they received. Nothing is sent to AniList - the message
     * stays exactly where the other party can still see it.
     */
    fun hideMessage(message: MessageNode) {
        viewModelScope.launch {
            visibilityPreferences.hide(message.sideOf(viewerId), message.id)
        }
    }

    /** Puts every dismissed row back on both sides. */
    fun showHiddenMessages() {
        viewModelScope.launch { visibilityPreferences.showAll() }
    }

    /** The option behind the badge: whether a new arrival is marked at all. */
    fun setFlagsNewMessages(enabled: Boolean) {
        viewModelScope.launch { visibilityPreferences.setFlagNew(enabled) }
    }

    /**
     * Records that the reader has read as far as now.
     *
     * Called when a message is opened rather than when the tab is: a badge that
     * cleared the moment the list appeared would never be readable, which is the one
     * thing it is for.
     */
    fun markInboxSeen(epochSecond: Long) {
        viewModelScope.launch { visibilityPreferences.markReceivedSeen(epochSecond) }
    }

    /** Switches the visible chip and makes sure the sources it shows are asked for. */
    fun selectFilter(filter: MessageFilter) {
        if (_state.value.filter == filter) return
        _state.value = _state.value.copy(filter = filter)
        when (filter) {
            MessageFilter.ALL -> loadFirstPages()
            MessageFilter.RECEIVED -> loadFirstPage(MessageFilter.RECEIVED)
            MessageFilter.SENT -> loadFirstPage(MessageFilter.SENT)
        }
    }

    /**
     * Loads both sources, once each.
     *
     * The screen calls this on the segment's first composition, because [ALL] is
     * the default filter and it draws both halves.
     */
    fun loadFirstPages(forceRefresh: Boolean = false) {
        started = true
        loadFirstPage(MessageFilter.RECEIVED, forceRefresh)
        loadFirstPage(MessageFilter.SENT, forceRefresh)
    }

    /**
     * Re-reads both halves over the rows already on screen.
     *
     * Called by the profile's page-level refresh, and deliberately keyed on
     * [started]: the messages tab loads lazily, so a refresh tapped before the
     * segment was ever opened must not be the thing that spends two requests on
     * it.
     *
     * An open detail sheet is left alone on purpose: the refresh rewrites the list
     * underneath, and the message being read travels with the sheet it was opened
     * from. Replacing that copy mid-sentence would redraw the sheet out from under
     * the reader because somebody else tapped a button.
     */
    fun refreshIfStarted() {
        if (!started) return
        loadFirstPages(forceRefresh = true)
    }

    private fun loadFirstPage(source: MessageFilter, forceRefresh: Boolean = false) {
        if (source == MessageFilter.ALL) return
        val current = sourceState(source)
        if (current.isLoading || current.isLoadingMore) return
        if (current.loaded && !forceRefresh) return
        fetch(source, forceRefresh = forceRefresh, appending = false)
    }

    /**
     * Fetches one more page of whatever the visible filter needs.
     *
     * Under [ALL] that is both sources when both still have pages: the merged
     * list is ordered by time, so the row after the last visible one can come
     * from either side, and only asking both tells the truth about what comes
     * next.
     */
    fun loadNextPage() {
        when (_state.value.filter) {
            MessageFilter.ALL -> {
                loadMore(MessageFilter.RECEIVED)
                loadMore(MessageFilter.SENT)
            }
            MessageFilter.RECEIVED -> loadMore(MessageFilter.RECEIVED)
            MessageFilter.SENT -> loadMore(MessageFilter.SENT)
        }
    }

    /**
     * Loads one more page of **both** sources, for the conversation view.
     *
     * [loadNextPage] follows the tab's filter, which is right for a list and wrong
     * for a thread: a conversation is assembled from both sides whatever the inbox
     * happens to be showing, and asking only the side the filter picked is how the
     * reader's own older messages stay unreachable behind an open sheet.
     */
    fun loadMoreConversation() {
        loadMore(MessageFilter.RECEIVED)
        loadMore(MessageFilter.SENT)
    }

    private fun loadMore(source: MessageFilter) {
        if (source == MessageFilter.ALL) return
        val current = sourceState(source)
        if (!current.loaded || !current.hasMore) return
        if (current.isLoading || current.isLoadingMore) return
        fetch(source, forceRefresh = false, appending = true)
    }

    /**
     * Re-reads the sources the visible filter shows that have not loaded.
     *
     * A source that succeeded is left alone: under [ALL], one half can fail
     * while the other is on screen, and refetching the good half to fix the bad
     * one spends a request on rows that are already correct.
     */
    fun retry() {
        fun retrySource(source: MessageFilter) {
            val current = sourceState(source)
            if (current.isLoading || current.isLoadingMore) return
            if (current.loaded && current.error == null) return
            fetch(source, forceRefresh = true, appending = false)
        }
        when (_state.value.filter) {
            MessageFilter.ALL -> {
                retrySource(MessageFilter.RECEIVED)
                retrySource(MessageFilter.SENT)
            }
            MessageFilter.RECEIVED -> retrySource(MessageFilter.RECEIVED)
            MessageFilter.SENT -> retrySource(MessageFilter.SENT)
        }
    }

    private fun fetch(source: MessageFilter, forceRefresh: Boolean, appending: Boolean) {
        if (source == MessageFilter.ALL) return
        if (refuseWithoutViewer(source)) return

        val page = cursorFor(source, appending)
        if (source == MessageFilter.RECEIVED) receivedJob?.cancel() else sentJob?.cancel()
        updateSource(source) { current ->
            if (appending) {
                current.copy(isLoadingMore = true, loadMoreError = null)
            } else {
                current.copy(isLoading = true, error = null, isLoadingMore = false, loadMoreError = null)
            }
        }

        val job = viewModelScope.launch {
            loadSourcePage(source, page, forceRefresh).fold(
                onSuccess = { pageResult -> recordPage(source, page, appending, pageResult) },
                onFailure = { error -> recordFailure(source, appending, error) }
            )
        }
        if (source == MessageFilter.RECEIVED) receivedJob = job else sentJob = job
    }

    /**
     * Marks the sent source as unreadable when there is nobody to ask for, and
     * answers true when the fetch must not go out.
     *
     * The id is part of the request, so this is the one case where the answer is
     * known before the network is. The demo is exempt because its fixture names a
     * viewer of its own - see [viewerId].
     */
    private fun refuseWithoutViewer(source: MessageFilter): Boolean {
        if (source != MessageFilter.SENT || viewerId != null || isDemo) return false
        updateSource(source) {
            it.copy(
                loaded = true,
                isLoading = false,
                error = "Sign in to read the messages you sent."
            )
        }
        return true
    }

    /** The page asked for: the source's own cursor when appending, page one otherwise. */
    private fun cursorFor(source: MessageFilter, appending: Boolean): Int = when {
        !appending -> 1
        source == MessageFilter.RECEIVED -> receivedNextPage
        else -> sentNextPage
    }

    /**
     * One page from one source, in demo or network form.
     *
     * The two are kept apart by this function rather than inlined into [fetch]
     * because the fold below must not have to know which one it got.
     */
    private suspend fun loadSourcePage(
        source: MessageFilter,
        page: Int,
        forceRefresh: Boolean
    ): Result<UserMessagesPage> {
        if (!isDemo) {
            return if (source == MessageFilter.RECEIVED) {
                repositoryResult { aniListRepository.getReceivedMessages(page, forceRefresh) }
            } else {
                repositoryResult {
                    aniListRepository.getSentMessages(viewerId!!, page, forceRefresh)
                }
            }
        }

        // The fixture is both halves in one list; each source takes its own
        // direction out of it, so the chips cannot show the other half's rows.
        // It is the whole feed, hence no next page.
        val wantSent = source == MessageFilter.SENT
        val directed = aniListRepository.getDemoMessages().filter { message ->
            message.isSentBy(viewerId) == wantSent
        }
        return Result.success(UserMessagesPage(messages = directed, hasMore = false))
    }

    /**
     * Folds a landed page into its source and advances that source's cursor.
     *
     * Only the source that answered moves its cursor - the two page independently,
     * and a shared counter would let one source skip the page the other had not
     * asked for.
     */
    private fun recordPage(
        source: MessageFilter,
        page: Int,
        appending: Boolean,
        pageResult: UserMessagesPage
    ) {
        if (source == MessageFilter.RECEIVED) {
            receivedNextPage = page + 1
        } else {
            sentNextPage = page + 1
        }
        updateSource(source) { current ->
            val combined = if (appending) {
                current.messages + pageResult.messages
            } else {
                pageResult.messages
            }
            current.copy(
                // De-duplicated on the id: a message arriving between two
                // requests appears on both pages, and a list keyed on the id
                // would throw on the duplicate.
                messages = combined.distinctBy { it.id },
                loaded = true,
                isLoading = false,
                isLoadingMore = false,
                hasMore = pageResult.hasMore,
                error = null,
                loadMoreError = null
            )
        }
    }

    /**
     * Records a failed page without discarding the rows that did arrive.
     *
     * A paging failure leaves the pages already read and puts the sentence in the
     * footer; a first-page failure owns the whole tab. The same split the activity
     * feed's paging makes, and for the same reason: replacing loaded messages with
     * an error screen throws away the half that worked.
     */
    private fun recordFailure(source: MessageFilter, appending: Boolean, error: Throwable) {
        updateSource(source) { current ->
            if (appending) {
                current.copy(
                    isLoadingMore = false,
                    loadMoreError = error.message ?: "Couldn't load more messages."
                )
            } else {
                current.copy(
                    isLoading = false,
                    error = error.message ?: "Couldn't load messages."
                )
            }
        }
    }

    private fun sourceState(source: MessageFilter): SourceState = when (source) {
        MessageFilter.RECEIVED -> _state.value.received
        MessageFilter.SENT -> _state.value.sent
        MessageFilter.ALL -> SourceState()
    }

    private fun updateSource(source: MessageFilter, transform: (SourceState) -> SourceState) {
        val current = _state.value
        _state.value = when (source) {
            MessageFilter.RECEIVED -> current.copy(received = transform(current.received))
            MessageFilter.SENT -> current.copy(sent = transform(current.sent))
            MessageFilter.ALL -> current
        }
    }

    class Factory(
        private val aniListRepository: AniListRepository,
        private val viewerId: Int? = null,
        private val isDemo: Boolean = false,
        private val visibilityPreferences: MessageVisibilityPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UserMessagesViewModel(
            aniListRepository = aniListRepository,
            viewerId = viewerId,
            isDemo = isDemo,
            visibilityPreferences = visibilityPreferences
        ) as T
    }
}

/**
 * How long a name-opened conversation waits on the inbox before giving up and
 * leaving the compose sheet up.
 *
 * Both outcomes already draw a sheet, so the wait only decides *which* one for a
 * reader whose messages have never been loaded. Four seconds is longer than the
 * two first pages take on a working connection and short enough that a dead one is
 * not a tap that appears to have done nothing.
 */
private const val CONVERSATION_WAIT_MILLIS = 4_000L
