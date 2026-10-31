package com.anisequel.ui.screens.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.model.FollowUser
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.cards.SequelInfoChip
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownWithCovers
import com.anisequel.ui.components.openExternalUrl
import com.anisequel.ui.viewmodel.UserMessagesViewModel

/**
 * One message read whole, inside the app.
 *
 * ## It never loads
 *
 * The sheet is drawn from the row that was tapped
 * ([UserMessagesViewModel.MessageDetail]), and that row is already in memory -
 * the inbox the card list is drawing is the sheet's only source of content. So
 * there is no request behind opening it, no skeleton, and no spinner: the body is
 * text the app downloaded to draw the card, and it is on screen on frame one.
 *
 * The markdown's AniList preview cards *do* enrich afterwards, through the same
 * cover and entity loaders every other markdown surface uses, and a badge without
 * a poster is what they draw in the meantime. That is the intended split: the
 * words are never waiting on a lookup about links inside them.
 *
 * ## Why a sheet, and why this one
 *
 * A second route would have to re-open the message by id - a fetch, or the row
 * parcelled through the navigation graph - for something the reader reached by
 * tapping a card that is on screen. The app already answers "show me the rest of
 * this row" with a bottom sheet in three places (a title, a comment thread, the
 * likers of an activity), so this is the fourth, and it copies the family's
 * furniture: the same skipped partial expansion, the same container colour, the
 * same fling guard, the same divider under a header that leads with a face and a
 * name.
 *
 * The card's own clamp and "Read more" stay. They are a preview of the row in a
 * list, and this is the message; a short one fully visible on the card still opens
 * the sheet, so there is one gesture to learn rather than one for long messages
 * and another for short ones.
 *
 * ## The outbound link is demoted, not removed
 *
 * The footer's action is the same URL the card's chip always opened. It is no
 * longer the only way to finish reading a message, which is what it used to be.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailSheet(
    detail: UserMessagesViewModel.MessageDetail,
    onDismiss: () -> Unit,
    /** Opens the counterpart's profile. The face and name are the link, as on the card. */
    onOpenUser: (FollowUser) -> Unit,
    /** What the reply composer is doing; a fresh state hides every outcome. */
    reply: UserMessagesViewModel.ReplyState = UserMessagesViewModel.ReplyState(),
    /** False for the demo: there is no account to send from. */
    canReply: Boolean = false,
    onSend: (text: String, isPrivate: Boolean) -> Unit = { _, _ -> },
    onClearReply: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val message = detail.message

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Attached above the scrolling body, so an upward fling the body
                // could not spend is eaten here rather than bounced by a sheet that
                // is already at its top anchor - [rememberSheetFlingGuard] is the
                // failure this is for, and the two engagement sheets carry the same
                // guard for the same reason.
                .nestedScroll(rememberSheetFlingGuard(sheetState))
                // One scroll container for the whole sheet, the way the likers tray
                // has one: the header travels with the message rather than staying
                // pinned over it, and nothing here has to guess how much height is
                // left for the body.
                .verticalScroll(rememberScrollState())
                .testTag("message_detail_sheet")
        ) {
            MessageDetailHeader(
                detail = detail,
                onDismiss = onDismiss,
                onOpenUser = onOpenUser
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // The whole body, unclamped - this is where all of it lives. Null only
            // for a row the repository did not drop: the card does not draw a
            // message with no text, and neither does the sheet.
            message.message?.let { body ->
                AniListMarkdownWithCovers(
                    markdown = body,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .testTag("message_detail_body")
                )
            }

            MessageDetailFooter(
                isPrivate = message.isPrivate == true,
                siteUrl = message.siteUrl
            )

            // The reply belongs to the message that is open, so it lives in this
            // sheet rather than on the inbox row: the reader has already decided
            // who they are answering.
            if (canReply) {
                MessageReplyComposer(
                    messageId = message.id,
                    recipientName = detail.counterpart?.name,
                    reply = reply,
                    onSend = onSend,
                    onClearReply = onClearReply
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Who the message is from or to, which way it went, and when it was sent.
 *
 * The direction is a chip rather than a prose line, because the sheet has the room
 * the card did not: on the card "You sent" / "Sent you" had to fit under a name in
 * `labelSmall`, and here the same fact is the one the reader is most likely to get
 * wrong when the two halves of the inbox are merged.
 *
 * The timestamp is absolute. The card's relative "3h ago" is a feed cue, and a
 * message worth opening in full is worth dating.
 */
@Composable
private fun MessageDetailHeader(
    detail: UserMessagesViewModel.MessageDetail,
    onDismiss: () -> Unit,
    onOpenUser: (FollowUser) -> Unit
) {
    val counterpart = detail.counterpart
    val createdAt = detail.message.createdAt

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    // The face and the name are one target, and nobody is the
                    // profile of nobody: same rule and same ripple shape as the card.
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
            MessageAvatar(counterpart, size = 36.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = counterpart?.name ?: "Unknown account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SequelInfoChip(
                        icon = if (detail.sent) {
                            AppVectorIcons.ActivitySend
                        } else {
                            AppVectorIcons.MessageReceived
                        },
                        label = if (detail.sent) "Sent" else "Received"
                    )
                    if (createdAt != null) {
                        Text(
                            text = formatFullTimestamp(createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                // 44dp: the sheet's only explicit close, and the smallest target a
                // reader can leave a full-screen message with.
                .size(44.dp)
                .bouncyPress(pressedScale = 0.9f)
                .testTag("message_detail_close")
        ) {
            Icon(
                imageVector = AppVectorIcons.Close,
                contentDescription = "Close message",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * The flag and the way out: private when AniList says the message was, and the
 * link to the site's own page for it.
 *
 * The action is absent when there is no URL to open, exactly as on the card - the
 * demo's fixtures carry none, and a made-up one would open an unrelated real
 * activity.
 */
@Composable
private fun MessageDetailFooter(
    isPrivate: Boolean,
    siteUrl: String?
) {
    if (!isPrivate && siteUrl == null) return
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isPrivate) {
            SequelInfoChip(
                icon = AppVectorIcons.Privacy,
                label = "Private"
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        siteUrl?.let { url ->
            TextButton(
                onClick = { openExternalUrl(context, url) },
                modifier = Modifier
                    .bouncyPress(pressedScale = 0.95f)
                    .testTag("message_detail_open_anilist")
            ) {
                Icon(
                    imageVector = AppVectorIcons.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Open on AniList",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Draws [MessageDetailSheet] when the inbox has an open row, and nothing otherwise.
 *
 * Its own composable, and called by the screen rather than by the tab, for the
 * reason [ActivityEngagementSheets] gives: a `ModalBottomSheet` is a window of its
 * own, and one drawn from inside the `AnimatedContent` that switches profile tabs
 * is torn down with the tab it was opened in. The open row lives in
 * [UserMessagesViewModel] for the same reason - see
 * [UserMessagesViewModel.MessageDetail].
 *
 * The window is not composed at all while nothing is open, rather than composed and
 * hidden: a sheet that exists with nothing in it is a scrim over the profile.
 */
@Composable
internal fun MessagesDetailSheetHost(
    messagesViewModel: UserMessagesViewModel,
    onOpenUser: (FollowUser) -> Unit
) {
    val detail by messagesViewModel.detail.collectAsStateWithLifecycle()
    val open = detail ?: return
    val reply by messagesViewModel.reply.collectAsStateWithLifecycle()

    MessageDetailSheet(
        detail = open,
        onDismiss = { messagesViewModel.dismissMessage() },
        onOpenUser = onOpenUser,
        reply = reply,
        canReply = messagesViewModel.canReply,
        onSend = messagesViewModel::replyToOpenMessage,
        onClearReply = messagesViewModel::clearReplyState
    )
}

/**
 * The reply box under an open message.
 *
 * Private by default and switchable to public: a direct message is the private
 * case, and a reader who wants it on their profile stream can say so in one tap.
 * The choice is per-send rather than remembered - a preference that quietly made
 * every reply public because it was chosen once is the kind of persistence that
 * costs somebody a message.
 */
@Composable
private fun MessageReplyComposer(
    messageId: Int,
    recipientName: String?,
    reply: UserMessagesViewModel.ReplyState,
    onSend: (text: String, isPrivate: Boolean) -> Unit,
    onClearReply: () -> Unit
) {
    // Keyed on the message: opening a second message must not carry the draft of
    // the first, and a rotation must not lose the one being typed.
    var draft by rememberSaveable(messageId) { mutableStateOf("") }
    var isPrivate by rememberSaveable(messageId) { mutableStateOf(true) }

    // A send that lands clears the box; the confirmation stays until the next
    // keystroke clears it, so the reader can see which account received it.
    LaunchedEffect(reply.sentTo) {
        if (reply.sentTo != null) draft = ""
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(
            text = recipientName?.let { "Reply to $it" } ?: "Reply",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReplyPrivacyChoice(
                label = "Public",
                icon = AppVectorIcons.Visibility,
                selected = !isPrivate,
                testTag = "message_reply_public",
                onClick = { isPrivate = false }
            )
            ReplyPrivacyChoice(
                label = "Private",
                icon = AppVectorIcons.Privacy,
                selected = isPrivate,
                testTag = "message_reply_private",
                onClick = { isPrivate = true }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { next ->
                draft = next.take(MAX_REPLY_LENGTH)
                if (reply.sentTo != null || reply.error != null) onClearReply()
            },
            placeholder = { Text("Write a message…") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("message_reply_field")
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isPrivate) {
                    "Private - only the two of you will see it."
                } else {
                    "Public - it also appears on your profile stream."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = { onSend(draft, isPrivate) },
                enabled = draft.isNotBlank() && !reply.isSending,
                modifier = Modifier.testTag("message_reply_send")
            ) {
                Text(if (reply.isSending) "Sending…" else "Send")
            }
        }

        reply.error?.let { message ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        reply.sentTo?.let { name ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Sent to $name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** One of the two privacy choices. Capsule, selectable, and names its consequence. */
@Composable
private fun ReplyPrivacyChoice(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "reply_privacy_container_$label"
    )
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = if (selected) {
                androidx.compose.ui.text.font.FontWeight.SemiBold
            } else {
                androidx.compose.ui.text.font.FontWeight.Medium
            }
        )
    }
}

/** AniList's message cap; the field stops the reader before the server can. */
private const val MAX_REPLY_LENGTH = 2_000
