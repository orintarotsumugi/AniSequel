package com.anisequel.ui.screens.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownWithCovers
import com.anisequel.ui.components.openExternalUrl
import java.time.Instant

/**
 * One row of the inbox: who it is with, which way it went, and the two marks that
 * belong to the row rather than to its author.
 *
 * Split from [ProfileMessagesTab] because the tab's job is the list - what a filter
 * tap loads, when the next page is asked for, how a half of the inbox that failed is
 * reported - and this file's job is a single message. They were already two reasons to
 * change one file, and adding the new mark and the dismiss button put the tab over the
 * repository's ceiling for a file that does one thing.
 */

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
internal fun MessageCard(
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
