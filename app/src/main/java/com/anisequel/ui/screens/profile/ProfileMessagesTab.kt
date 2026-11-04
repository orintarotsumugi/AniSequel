package com.anisequel.ui.screens.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MessageNode
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.EngagementAction
import com.anisequel.ui.components.cards.SequelInfoChip
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownWithCovers
import com.anisequel.ui.components.openExternalUrl
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
import java.time.Instant

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

/** Lines of a message a closed card shows before "Read more". */
private const val MESSAGE_COLLAPSED_LINES = 8

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

/**
 * One message, both directions, with the counterpart named on it.
 *
 * The card is clickable as a whole, and the things inside it that are already
 * controls stay controls: a nested `clickable` or button claims its own region, so
 * the counterpart's face still opens their profile, "Read more" still expands in
 * place, the footer's chip still leaves for AniList, and the dismiss button at the
 * end of the author line takes only its own width off the row. A tap on the card -
 * the body, the padding, anywhere that is not one of those - opens the message's
 * comment thread, with the message as its first row.
 *
 * The note here used to say the card was "deliberately not clickable as a whole,
 * because a message has no thread to open in this app". That was true of the inbox
 * as it stood; the thread is the message's activity, and the comment sheet is
 * where it is answered now.
 */
@Composable
private fun MessageCard(
    message: MessageNode,
    viewerId: Int?,
    nowEpochSecond: Long,
    isNew: Boolean,
    onOpenUser: (FollowUser) -> Unit,
    onOpenComments: (MessageNode) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val body = message.message ?: return
    val sent = message.isSentBy(viewerId)
    val counterpart = message.counterpartFor(viewerId)
    var expanded by remember(message.id) { mutableStateOf(false) }
    // Latched for the same reason the text-post card latches it: the overflow
    // report describes the collapsed render, so an expanded card reports false
    // and a live-bound control would delete its own "Show less".
    var everOverflowed by remember(message.id) { mutableStateOf(false) }
    // Shared with [bouncyPress] for the reason [com.anisequel.ui.screens.profile.ActivityTextCard]
    // gives: one press signal for the card, cancelled the moment the gesture
    // becomes a scroll, instead of two pointer-input loops competing on every row.
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                // The label a screen reader announces in place of the card's
                // contents, so the gesture is described as opening the message's
                // comments rather than as a nameless group of text.
                onClickLabel = "Open this message's comments",
                onClick = { onOpenComments(message) }
            )
            .testTag("message_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(ExpressiveMotion.FastSpatialSize)
                .padding(14.dp)
        ) {
            // The author row and the two marks that belong to the row rather than to
            // its author, in one line: a badge that sat above the name would read as
            // part of the sender, and a dismiss button under the body would read as
            // part of the message.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MessageAuthorRow(
                    counterpart = counterpart,
                    sent = sent,
                    createdAt = message.createdAt,
                    nowEpochSecond = nowEpochSecond,
                    onOpenUser = onOpenUser,
                    modifier = Modifier.weight(1f)
                )
                if (isNew) {
                    NewMessagePill()
                }
                MessageHideButton(onHide = onHide)
            }

            AniListMarkdownWithCovers(
                markdown = body,
                maxLines = if (expanded) Int.MAX_VALUE else MESSAGE_COLLAPSED_LINES,
                onOverflowChange = { hidden -> if (hidden) everOverflowed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("message_body")
            )

            if (everOverflowed) {
                MessageExpandToggle(
                    expanded = expanded,
                    onToggle = { expanded = !expanded }
                )
            }

            MessageFooter(
                sent = sent,
                isPrivate = message.isPrivate == true,
                siteUrl = message.siteUrl,
                replyCount = message.replyCount ?: 0,
                onOpenComments = { onOpenComments(message) }
            )
        }
    }
}

/**
 * The mark on a received message that arrived after the reader last read the inbox.
 *
 * A pill rather than a coloured edge: the edge is what the unread convention looks
 * like in every mail client, and this tab is not a mail client - it shows both
 * directions, and the thing being marked is *which side of the stamp* the row falls
 * on. The text carries that for a reader who cannot see the colour difference, and it
 * is a `Text`, so there is no icon to conflict with the repo's no-emoji rule.
 */
@Composable
private fun NewMessagePill(modifier: Modifier = Modifier) {
    Text(
        text = "New",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("message_new_pill")
    )
}

/**
 * Dismisses the row from this list.
 *
 * Deliberately not an `IconButton`: the card is one clickable surface with nested
 * controls that each claim their own region, and a full-size button inside it would
 * take a thumb-width of the body away from the tap that opens the thread. This is the
 * same small target the expand toggle uses.
 */
@Composable
private fun MessageHideButton(onHide: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onHide,
        modifier = modifier.testTag("message_hide")
    ) {
        Icon(
            imageVector = AppVectorIcons.Close,
            contentDescription = "Hide this message",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Who the message is with, which way it went, and when.
 *
 * The face and the name are the link; the direction line underneath is the only
 * prose cue to whose voice the body is, and it is repeated by the footer's chip
 * for readers who scan rather than read.
 */
@Composable
private fun MessageAuthorRow(
    counterpart: FollowUser?,
    sent: Boolean,
    createdAt: Int?,
    nowEpochSecond: Long,
    onOpenUser: (FollowUser) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (counterpart != null) {
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpenUser(counterpart) }
                    } else {
                        Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MessageAvatar(counterpart)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = counterpart?.name ?: "Unknown account",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // The body has no other cue about whose voice it is
                    // in: both directions share the card. This line is
                    // that cue, and the footer chip repeats it for
                    // readers scanning rather than reading.
                    text = if (sent) "You sent" else "Sent you",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (createdAt != null) {
            Text(
                text = formatRelativeSeconds(
                    timestamp = createdAt,
                    now = Instant.ofEpochSecond(nowEpochSecond)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Read more / Show less, drawn only once the collapsed body has actually overflowed. */
@Composable
private fun MessageExpandToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = if (expanded) "Show less" else "Read more",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(top = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("message_read_more")
    )
}

/**
 * The chip row under the body: the direction again, the privacy flag when AniList
 * says the message was private, the comment count, and the link out to the site.
 *
 * The count is an action, not a label. A `MessageActivity` carries replies like any
 * other activity, and the feed's cards have always offered them; the inbox did not,
 * so a conversation could be read here but commented on only by leaving for the
 * site. It draws even at zero, exactly as [ProfileActivityCard]'s does, because the
 * control that opens an empty thread is the control that lets you start one.
 */
@Composable
private fun MessageFooter(
    sent: Boolean,
    isPrivate: Boolean,
    siteUrl: String?,
    replyCount: Int,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 10.dp)
    ) {
        SequelInfoChip(
            icon = if (sent) {
                AppVectorIcons.ActivitySend
            } else {
                AppVectorIcons.MessageReceived
            },
            label = if (sent) "Sent" else "Received"
        )
        if (isPrivate) {
            SequelInfoChip(
                icon = AppVectorIcons.Privacy,
                label = "Private"
            )
        }
        EngagementAction(
            icon = AppVectorIcons.ActivityReplies,
            count = replyCount,
            active = false,
            enabled = true,
            contentDescription = "Comments",
            onClick = onOpenComments,
            modifier = Modifier.testTag("message_comments_button")
        )
        Spacer(modifier = Modifier.weight(1f))
        // Only when the row is a real AniList activity. The demo's
        // fixtures carry no URL, and a made-up one would open an
        // unrelated real activity - so the action is simply absent there.
        siteUrl?.let { url ->
            IconButton(
                onClick = { openExternalUrl(context, url) },
                modifier = Modifier
                    .size(34.dp)
                    .bouncyPress(pressedScale = 0.9f)
                    .testTag("message_open_anilist")
            ) {
                Icon(
                    imageVector = AppVectorIcons.OpenInNew,
                    contentDescription = "Open on AniList",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * The counterpart's face, at the size the surface carrying it uses.
 *
 * `internal` rather than private because the detail sheet draws the same face: one
 * person rendered as two avatars of different shape or different fallback is how a
 * sheet stops reading as the row it was opened from.
 */
@Composable
internal fun MessageAvatar(
    counterpart: FollowUser?,
    size: Dp = 32.dp
) {
    val avatar = counterpart?.avatar?.large ?: counterpart?.avatar?.medium
    val shape = RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner)
    if (avatar != null) {
        AsyncImage(
            model = avatar,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop
        )
    } else {
        // A missing face is drawn rather than left blank: a message whose sender
        // the response did not name still has to occupy the row the name sits on.
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.ProfilePerson,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

