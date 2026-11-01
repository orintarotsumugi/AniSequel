package com.anisequel.ui.viewmodel

import com.anisequel.data.model.MessageNode
import com.anisequel.data.model.isWith

/**
 * Which half of the viewer's messages a chip is asking for.
 *
 * [ALL] is not a source: it is the merge of the other two, which is why every
 * load and retry in [UserMessagesViewModel] resolves it to one or both of them
 * and never fetches "all" as if it were a document of its own.
 */
enum class MessageFilter { ALL, RECEIVED, SENT }

/**
 * What the inbox's state means, without the loading it came from.
 *
 * These were the bottom third of `UserMessagesViewModel`, below a class that
 * loads two independent sources. They share nothing with it but its state type:
 * each is a pure function of [UserMessagesViewModel.UiState] (plus, for a
 * conversation, a pair of ids), which is why they are testable without a
 * coroutine harness and why they live here - the ViewModel's job is the fetch,
 * the cursors and the failure bookkeeping, and a file that does both is a file
 * that has to be read twice to answer one question.
 */

/**
 * The rows the visible filter should draw, newest first.
 *
 * [MessageFilter.ALL] merges the two sources and re-sorts them: each source is
 * already newest-first on its own, but interleaving two ordered lists is not an
 * ordering, and a merged inbox that showed older messages above newer ones
 * would read as broken. De-duplicated on the id for the same reason the ViewModel
 * de-duplicates each append - an overlap between the two sources is not expected,
 * but a repeated key in a `LazyColumn` is a crash, not a cosmetic issue.
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
 * Only a filter with no rows at all may show an error page. Under [MessageFilter.ALL],
 * one half failing while the other arrived must not throw away the rows that did
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
 * foot because the missing rows are not necessarily at the bottom - under
 * [MessageFilter.ALL] the failed half's rows are missing from *throughout* the
 * merged list.
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

/**
 * The conversation with one person, assembled from the inbox halves, oldest first.
 *
 * AniList's own reply thread for a message is the primary source now - see
 * `UserMessagesViewModel.ThreadState` - and this is the fallback and the union
 * with it: the inbox can hold a message the open thread does not (a second,
 * separate message activity to the same person), and dropping those rows would
 * hide half of an exchange that the reader is looking straight at.
 *
 * The inbox is two independent paged sources, because AniList can only be asked
 * for one side of an exchange at a time - `Page.activities` has no recipient
 * filter - and the sheet that shows a conversation needs them interleaved and
 * read in the order they happened, which is the opposite of the inbox's
 * newest-first.
 *
 * Sorting ascending is what makes the detail sheet a chat rather than a list of
 * receipts: the newest message sits at the bottom, where a reply is written, and
 * the reader scrolls *up* for older ones the way they would in any other
 * messenger.
 *
 * Top-level and pure, like the inbox's own merge helpers, so the ordering and the
 * pair-matching are checkable without a coroutine harness.
 */
internal fun conversationMessages(
    state: UserMessagesViewModel.UiState,
    counterpartId: Int?,
    viewerId: Int?
): List<MessageNode> =
    (state.received.messages + state.sent.messages)
        .distinctBy { it.id }
        .filter { it.isWith(counterpartId, viewerId) }
        .sortedBy { it.createdAt ?: 0 }
