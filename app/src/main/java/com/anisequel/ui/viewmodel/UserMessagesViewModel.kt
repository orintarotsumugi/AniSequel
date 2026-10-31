package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MessageNode
import com.anisequel.data.model.UserMessagesPage
import com.anisequel.data.repository.AniListRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Which half of the viewer's messages a chip is asking for.
 *
 * [ALL] is not a source: it is the merge of the other two, which is why every
 * load and retry in [UserMessagesViewModel] resolves it to one or both of them
 * and never fetches "all" as if it were a document of its own.
 */
enum class MessageFilter { ALL, RECEIVED, SENT }

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
    private val isDemo: Boolean = false
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
        val viewerId: Int? = null
    )

    private val _state = MutableStateFlow(UiState(viewerId = viewerId))
    val state: StateFlow<UiState> = _state.asStateFlow()

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
        val message: MessageNode,
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
     * Opens [message] in the detail sheet.
     *
     * Writes the state and nothing else: no request goes out, because everything
     * the sheet draws - body, sender, timestamp, privacy flag, site URL - arrived
     * with the list the row is sitting in.
     */
    fun openMessage(message: MessageNode) {
        _detail.value = MessageDetail(
            message = message,
            sent = message.isSentBy(viewerId),
            counterpart = message.counterpartFor(viewerId)
        )
    }

    /** Closes the detail sheet. */
    fun dismissMessage() {
        _detail.value = null
        if (!_reply.value.isSending) _reply.value = ReplyState()
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
     * Sends [text] to the other party of the message the sheet has open.
     *
     * The recipient is the row's *counterpart*, not its messenger: replying to a
     * message you sent has to go to the person you sent it to. [isPrivate] is the
     * composer's public/private choice and travels to AniList, which keeps a
     * private message between the two accounts instead of also placing it on the
     * sender's profile stream.
     */
    fun replyToOpenMessage(text: String, isPrivate: Boolean) {
        if (!canReply) return
        val open = _detail.value ?: return
        val body = text.trim()
        if (body.isEmpty() || _reply.value.isSending) return
        val recipientId = open.counterpart?.id
            ?: if (open.sent) open.message.recipientId else open.message.messengerId
        if (recipientId == null) {
            _reply.value = ReplyState(error = "There is no one to reply to on this message.")
            return
        }
        _reply.value = ReplyState(isSending = true)
        viewModelScope.launch {
            repositoryResult {
                aniListRepository.sendMessage(recipientId, body, isPrivate)
            }.fold(
                onSuccess = {
                    _reply.value = ReplyState(sentTo = open.counterpart?.name ?: "them")
                },
                onFailure = { failure ->
                    _reply.value = ReplyState(
                        error = failure.message ?: "Couldn't send that message."
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
        private val isDemo: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UserMessagesViewModel(
            aniListRepository = aniListRepository,
            viewerId = viewerId,
            isDemo = isDemo
        ) as T
    }
}

/**
 * The rows the visible filter should draw, newest first.
 *
 * [MessageFilter.ALL] merges the two sources and re-sorts them: each source is
 * already newest-first on its own, but interleaving two ordered lists is not an
 * ordering, and a merged inbox that showed older messages above newer ones
 * would read as broken. De-duplicated on the id for the same reason the ViewModel
 * de-duplicates each append - an overlap between the two sources is not expected,
 * but a repeated key in a `LazyColumn` is a crash, not a cosmetic issue.
 *
 * Top-level and internal rather than a method so the rule is testable without a
 * coroutine harness; the snippet of logic has no state of its own.
 */
internal fun visibleMessages(state: UserMessagesViewModel.UiState): List<MessageNode> =
    when (state.filter) {
        MessageFilter.ALL -> (state.received.messages + state.sent.messages)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt ?: 0 }
        MessageFilter.RECEIVED -> state.received.messages
        MessageFilter.SENT -> state.sent.messages
    }

/**
 * True once every source the visible filter needs has answered.
 *
 * Read by the empty state, not by the list: an inbox that has not loaded yet and
 * one that loaded and was empty both have no rows, and only the second deserves
 * "No messages yet". Without this the tab flashed the empty orb for a frame
 * before the first request even started.
 */
internal fun visibleLoaded(state: UserMessagesViewModel.UiState): Boolean =
    when (state.filter) {
        MessageFilter.ALL -> state.received.loaded && state.sent.loaded
        MessageFilter.RECEIVED -> state.received.loaded
        MessageFilter.SENT -> state.sent.loaded
    }

/** True while a first page the visible filter needs is in flight. */
internal fun visibleLoading(state: UserMessagesViewModel.UiState): Boolean =
    when (state.filter) {
        MessageFilter.ALL -> state.received.isLoading || state.sent.isLoading
        MessageFilter.RECEIVED -> state.received.isLoading
        MessageFilter.SENT -> state.sent.isLoading
    }

/** True while the next page of any source the visible filter shows is in flight. */
internal fun visibleLoadingMore(state: UserMessagesViewModel.UiState): Boolean =
    when (state.filter) {
        MessageFilter.ALL -> state.received.isLoadingMore || state.sent.isLoadingMore
        MessageFilter.RECEIVED -> state.received.isLoadingMore
        MessageFilter.SENT -> state.sent.isLoadingMore
    }

/** True when there is another page behind the rows the visible filter shows. */
internal fun visibleHasMore(state: UserMessagesViewModel.UiState): Boolean =
    when (state.filter) {
        // Either source having more means the merged list is not finished.
        MessageFilter.ALL -> state.received.hasMore || state.sent.hasMore
        MessageFilter.RECEIVED -> state.received.hasMore
        MessageFilter.SENT -> state.sent.hasMore
    }

/**
 * The full-page error, or null when there is anything to draw.
 *
 * Only a filter with no rows at all may show an error page. Under [ALL], one
 * half failing while the other arrived must not throw away the rows that did
 * load - that is what [visibleSourceError] is for.
 */
internal fun visibleError(state: UserMessagesViewModel.UiState): String? {
    if (visibleMessages(state).isNotEmpty()) return null
    return when (state.filter) {
        MessageFilter.ALL -> state.received.error ?: state.sent.error
        MessageFilter.RECEIVED -> state.received.error
        MessageFilter.SENT -> state.sent.error
    }
}

/**
 * A compact line above rows that did load, or null when nothing needs saying.
 *
 * Covers the failure that must not replace the list: one half of the merged view
 * failing while the other arrived. It is drawn above the rows rather than at the
 * foot because the missing rows are not necessarily at the bottom - under [ALL]
 * the failed half's rows are missing from *throughout* the merged list.
 *
 * Kept apart from [visibleLoadMoreError] because the two retries do different
 * things: this one asks for the source that never arrived, while a paging retry
 * appends the page after the rows on screen.
 */
internal fun visibleSourceError(state: UserMessagesViewModel.UiState): String? {
    if (visibleMessages(state).isEmpty()) return null
    return when (state.filter) {
        MessageFilter.ALL -> if (state.received.error != null || state.sent.error != null) {
            "Some messages couldn't load."
        } else {
            null
        }
        MessageFilter.RECEIVED -> state.received.error
        MessageFilter.SENT -> state.sent.error
    }
}

/**
 * The next page's failure, for the footer's retry.
 *
 * Only when rows are on screen: a failure with nothing above it is
 * [visibleError]'s page, not a footer notice under nothing.
 */
internal fun visibleLoadMoreError(state: UserMessagesViewModel.UiState): String? {
    if (visibleMessages(state).isEmpty()) return null
    return when (state.filter) {
        MessageFilter.ALL -> state.received.loadMoreError ?: state.sent.loadMoreError
        MessageFilter.RECEIVED -> state.received.loadMoreError
        MessageFilter.SENT -> state.sent.loadMoreError
    }
}
