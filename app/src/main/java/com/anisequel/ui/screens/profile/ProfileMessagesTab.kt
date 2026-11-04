package com.anisequel.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MessageNode
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.viewmodel.MessageFilter
import com.anisequel.ui.viewmodel.UserMessagesViewModel
import com.anisequel.ui.viewmodel.hasHiddenMessages
import com.anisequel.ui.viewmodel.isNewMessage
import com.anisequel.ui.viewmodel.visibleError
import com.anisequel.ui.viewmodel.visibleHasMore
import com.anisequel.ui.viewmodel.visibleLoadMoreError
import com.anisequel.ui.viewmodel.visibleLoaded
import com.anisequel.ui.viewmodel.visibleLoading
import com.anisequel.ui.viewmodel.visibleLoadingMore
import com.anisequel.ui.viewmodel.visibleMessages
import com.anisequel.ui.viewmodel.visibleSourceError

/**
 * The Messages segment: every private message the viewer can read, one list.
 *
 * ## Two sources, one screen
 *
 * AniList has no single "my messages" query. Sent messages come from
 * `Page.activities` by `messengerId`; received ones only exist as notifications
 * carrying a `MessageActivity`. [UserMessagesViewModel] keeps the halves apart
 * because they page and fail independently, and this tab is the one place they
 * are merged back together - see `visibleMessages`.
 *
 * ## The filter row does not scroll away
 *
 * It sits above the list rather than as its first item. Switching from All to
 * Sent after reading a long merged inbox would otherwise cost a scroll to the
 * top first, and the chips are the only control this segment has.
 *
 * ## Direction is the row's spine
 *
 * In the merged view a received message and a sent one look identical until the
 * body is read: same card, same avatar. So every card states its direction twice
 * - in words under the counterpart's name ("You sent" / "Sent you") and as a
 * chip in the footer - because misreading who said what in an inbox is the one
 * mistake that matters.
 *
 * ## A tap opens the conversation, in the comment sheet
 *
 * Both the card and its comment chip open the message's own comment thread, with
 * the message itself as the first row. That sheet is the one that quotes a line
 * and answers it; the bubbles-and-a-reply-box sheet the card used to open showed
 * the same exchange with neither, and it is gone from this screen. The outbound
 * link is still a chip, for the reader who wants AniList's own page.
 */
@Composable
fun ProfileMessagesTab(
    state: UserMessagesViewModel.UiState,
    onSelectFilter: (MessageFilter) -> Unit,
    /**
     * Takes one row out of the reader's list.
     *
     * Local to this app: the message stays on AniList and stays visible to the
     * person on the other end of it, which is the whole reason a dismissal is a
     * preference rather than a delete.
     */
    onHideMessage: (MessageNode) -> Unit,
    /** Puts every dismissed row back. */
    onShowHidden: () -> Unit,
    /** The option: whether a new arrival carries the badge. */
    onFlagNewChange: (Boolean) -> Unit,
    /**
     * The segment's entry trigger.
     *
     * Called from a `LaunchedEffect(Unit)`; the ViewModel's own guards make it
     * cost one request per source per session rather than one per visit.
     */
    onLoadFirstPage: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenUser: (FollowUser) -> Unit,
    /**
     * Opens the comment thread on one message - the card's tap, and its chip.
     *
     * A direct message is a `MessageActivity`, and AniList gives it replies the way
     * it gives any other activity one - so this is the same sheet the Activity tab's
     * comment button opens. The row travels with it because the sheet draws the
     * message as the thread's first line and aims the window with the row's own
     * reply count; a lookup by id would be a second pass over a list this call
     * already holds.
     */
    onOpenComments: (MessageNode) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /**
     * Hoisted by the screen, like every other tab's list state: the profile's tab
     * switch disposes this composable, and an inbox read halfway down should not
     * start over at the top.
     */
    listState: LazyListState = rememberLazyListState(),
    nowEpochSecond: Long = rememberNowEpochSecond()
) {
    LaunchedEffect(Unit) { onLoadFirstPage() }

    val messages = visibleMessages(state)
    val loading = visibleLoading(state)
    val loaded = visibleLoaded(state)
    val loadingMore = visibleLoadingMore(state)
    val hasMore = visibleHasMore(state)
    val error = visibleError(state)
    val sourceError = visibleSourceError(state)
    val loadMoreError = visibleLoadMoreError(state)
    val hasHidden = hasHiddenMessages(state)

    // The same six-rows-from-the-end trigger the activity feed uses: far enough
    // that the page usually lands before the reader, near enough that a short
    // list does not spin. `OnLoadMore` is guarded in the ViewModel, so the effect
    // firing on every scroll frame costs nothing.
    // Keyed on the state it reads. The tab is normally given one hoisted by the
    // profile screen, and an unkeyed `remember` would keep watching the first one
    // if the host ever swapped it - a trigger bound to a list that is no longer on
    // screen never fires, and the inbox stops paging with no visible cause.
    val shouldPage by remember(listState) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - MESSAGE_PAGE_TRIGGER_ROWS
        }
    }
    // Keyed on the row count as well as the boolean: `shouldPage` can stay true
    // across a short page - the same stall the activity feed documents - and a
    // reader parked inside the trigger distance otherwise stops paging with `hasMore`
    // still saying there is more. The ViewModel guards make the extra firings free.
    LaunchedEffect(shouldPage, messages.size) {
        if (shouldPage) onLoadMore()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_messages_tab")
    ) {
        MessageFilterRow(
            selected = state.filter,
            onSelect = onSelectFilter,
            hasHidden = hasHidden,
            onShowHidden = onShowHidden,
            flagNew = state.visibility.flagNew,
            onFlagNewChange = onFlagNewChange,
            modifier = Modifier
                .widthIn(max = ProfileMaxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = profileListPadding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            state = listState
        ) {
            if (messages.isEmpty()) {
                when {
                    error != null -> item(key = "messages_error", contentType = "state") {
                        MessagesError(message = error, onRetry = onRetry)
                    }
                    // Loading covers the first frame too: `loaded` is false until
                    // the sources answer, so the empty orb cannot flash before
                    // the request has even started.
                    loading || !loaded -> item(key = "messages_loading", contentType = "state") {
                        MessagesLoading()
                    }
                    else -> item(key = "messages_empty", contentType = "state") {
                        MessagesEmpty(filter = state.filter)
                    }
                }
            } else {
                if (sourceError != null) {
                    // Above the rows, not at the foot: under `All` a failed half
                    // is missing from throughout the merged list, so the notice
                    // belongs where the reader starts rather than under what
                    // happened to arrive.
                    item(key = "messages_source_error", contentType = "state") {
                        MessagesNotice(message = sourceError, onRetry = onRetry)
                    }
                }

                items(
                    items = messages,
                    key = { it.id },
                    contentType = { "message_card" }
                ) { message ->
                    MessageCard(
                        message = message,
                        viewerId = state.viewerId,
                        nowEpochSecond = nowEpochSecond,
                        isNew = isNewMessage(state, message),
                        onOpenUser = onOpenUser,
                        onHide = { onHideMessage(message) },
                        onOpenComments = onOpenComments,
                        modifier = Modifier
                            .widthIn(max = ProfileMaxContentWidth)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                when {
                    loadingMore -> item(key = "messages_more", contentType = "paging") {
                        MessagesPagingIndicator()
                    }
                    loadMoreError != null -> item(key = "messages_more_error", contentType = "paging") {
                        MessagesPagingRetry(message = loadMoreError, onRetry = onLoadMore)
                    }
                    // While `hasMore` is true and nothing is in flight, scrolling
                    // is what asks for the next page - no footer is drawn, so the
                    // trigger's geometry is not disturbed by a placeholder.
                    else -> Unit
                }

                if (!hasMore && !loadingMore && messages.size >= MESSAGE_END_THRESHOLD) {
                    item(key = "messages_end", contentType = "paging") {
                        MessagesFeedEnd()
                    }
                }
            }
        }
    }
}

/** Rows from the end that ask for the next page. Matches the activity feed. */
private const val MESSAGE_PAGE_TRIGGER_ROWS = 6

/**
 * The number of rows after which "that's everything" is worth saying.
 *
 * A one-page inbox ends with rows still on screen and no indication that anything
 * else was ever coming; an inbox of three messages does not need the sentence.
 */
private const val MESSAGE_END_THRESHOLD = 25

@Composable
private fun MessageFilterRow(
    selected: MessageFilter,
    onSelect: (MessageFilter) -> Unit,
    hasHidden: Boolean,
    onShowHidden: () -> Unit,
    flagNew: Boolean,
    onFlagNewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                label = "All",
                selected = selected == MessageFilter.ALL,
                onClick = { onSelect(MessageFilter.ALL) },
                testTag = "message_filter_all",
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                label = "Received",
                selected = selected == MessageFilter.RECEIVED,
                onClick = { onSelect(MessageFilter.RECEIVED) },
                testTag = "message_filter_received",
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                label = "Sent",
                selected = selected == MessageFilter.SENT,
                onClick = { onSelect(MessageFilter.SENT) },
                testTag = "message_filter_sent",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Only when there is something behind it. A "Show hidden" control on an
            // inbox with nothing dismissed is a button that does nothing, and the
            // tab has no way to say why.
            if (hasHidden) {
                TextButton(
                    onClick = onShowHidden,
                    modifier = Modifier.testTag("message_show_hidden")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Show hidden")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            // The option the badge answers to. Kept on the tab rather than in
            // Settings because it is about *this* list, and a reader who wants it
            // off is looking at the list when they decide that.
            Text(
                text = "Flag new",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = flagNew,
                onCheckedChange = onFlagNewChange,
                modifier = Modifier.testTag("message_flag_new_switch")
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    ExpressiveStateChip(
        label = label,
        selected = selected,
        onClick = onClick,
        // 44dp, matching the segment row above: these three are the only way to
        // narrow the inbox, and a chip at its natural height would be the
        // smallest target on the page.
        minTouchTargetHeight = 44.dp,
        modifier = modifier.testTag(testTag)
    )
}
