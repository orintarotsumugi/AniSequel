package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
        if (source == MessageFilter.SENT && viewerId == null && !isDemo) {
            updateSource(source) {
                it.copy(
                    loaded = true,
                    isLoading = false,
                    error = "Sign in to read the messages you sent."
                )
            }
            return
        }

        val page = if (appending) {
            if (source == MessageFilter.RECEIVED) receivedNextPage else sentNextPage
        } else {
            1
        }

        if (source == MessageFilter.RECEIVED) receivedJob?.cancel() else sentJob?.cancel()
        updateSource(source) { current ->
            if (appending) {
                current.copy(isLoadingMore = true, loadMoreError = null)
            } else {
                current.copy(isLoading = true, error = null, isLoadingMore = false, loadMoreError = null)
            }
        }

        val job = viewModelScope.launch {
            val result: Result<UserMessagesPage> = if (isDemo) {
                // The fixture is both halves in one list; each source takes its
                // own direction out of it, so the chips cannot show the other
                // half's rows. It is the whole feed, hence no next page.
                val directed = aniListRepository.getDemoMessages().filter { message ->
                    if (source == MessageFilter.RECEIVED) {
                        !message.isSentBy(viewerId)
                    } else {
                        message.isSentBy(viewerId)
                    }
                }
                Result.success(UserMessagesPage(messages = directed, hasMore = false))
            } else {
                repositoryResult {
                    when (source) {
                        MessageFilter.RECEIVED ->
                            aniListRepository.getReceivedMessages(page, forceRefresh)
                        else ->
                            aniListRepository.getSentMessages(viewerId!!, page, forceRefresh)
                    }
                }
            }

            result.fold(
                onSuccess = { pageResult ->
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
                            // De-duplicated on the id: a message arriving
                            // between two requests appears on both pages, and a
                            // list keyed on the id would throw on the duplicate.
                            messages = combined.distinctBy { it.id },
                            loaded = true,
                            isLoading = false,
                            isLoadingMore = false,
                            hasMore = pageResult.hasMore,
                            error = null,
                            loadMoreError = null
                        )
                    }
                },
                onFailure = { error ->
                    updateSource(source) { current ->
                        if (appending) {
                            // The pages already read stay; only the footer says
                            // that the next one did not arrive, the same rule the
                            // activity feed's paging follows.
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
            )
        }
        if (source == MessageFilter.RECEIVED) receivedJob = job else sentJob = job
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
