package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.ActivityReply
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.EngagementAction
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownWithCovers
import java.time.Instant

/**
 * One comment: a face, a name and a time, the body, and what the reader can do
 * about it.
 *
 * ## The row the sheet always had, and the one chat view adds
 *
 * [isContinuation] is the difference between the two shapes, and it is the whole
 * reason a thread of nine comments by one person stops costing nine headers. A
 * continuation draws no avatar, no name and no header line: the reader has just
 * seen who is speaking, and the third repetition of the same face is not
 * information - it is the thing between the eye and the sentence it came for.
 * [com.anisequel.domain.usecase.CommentGrouping] decides which rows that applies
 * to, from the author id, the gap in seconds and the thread depth, so a merged
 * header is only ever claimed for a row that really is the one above it continued.
 *
 * Continuations keep the indent and the controls. Dropping the controls with the
 * header would mean a person who posted four times could not like or quote three
 * of them, and a wrong like on somebody's comment is not something the reader can
 * walk back.
 *
 * ## Why the controls are where they are
 *
 * Left-aligned under the body rather than pushed to the sheet's edges: a body can
 * be a multi-line quote or an image, and a right-aligned overflow would sit at the
 * end of whatever line happened to be last.
 */
@Composable
internal fun CommentRow(
    reply: ActivityReply,
    indent: Int,
    isContinuation: Boolean,
    likePending: Boolean,
    canManage: Boolean,
    canReply: Boolean,
    canQuote: Boolean,
    onLike: () -> Unit,
    onShowLikers: () -> Unit,
    onReply: (String) -> Unit,
    onQuote: () -> Unit,
    onManage: () -> Unit,
    nowEpochSecond: Long? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                // One step per threaded level, capped in [ReplyThreader]. The
                // mention itself is still in the body, so the indent is an aid
                // rather than the only record of who is being answered.
                start = 20.dp + 18.dp * indent,
                end = 20.dp,
                top = if (isContinuation) 1.dp else 10.dp,
                bottom = if (isContinuation) 6.dp else 10.dp
            )
            .testTag("activity_reply_row"),
        verticalAlignment = Alignment.Top
    ) {
        if (isContinuation) {
            // The column the avatar would occupy, kept rather than collapsed: the
            // bodies of a burst of messages then line up with the bodies of the rows
            // that carry a header, which is what makes the group read as one block.
            Spacer(modifier = Modifier.width(46.dp))
        } else {
            CommentAvatar(
                url = reply.user?.avatar?.large ?: reply.user?.avatar?.medium,
                name = reply.user?.name.orEmpty()
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (!isContinuation) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reply.user?.name.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    CommentTimestamp(reply.createdAt, nowEpochSecond)
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            ReplyBody(text = reply.text)
            Spacer(modifier = Modifier.height(2.dp))
            CommentRowActions(
                reply = reply,
                isContinuation = isContinuation,
                likePending = likePending,
                canManage = canManage,
                canReply = canReply,
                canQuote = canQuote,
                nowEpochSecond = nowEpochSecond,
                onLike = onLike,
                onShowLikers = onShowLikers,
                onReply = onReply,
                onQuote = onQuote,
                onManage = onManage
            )
        }
    }
}

/**
 * The row's controls: heart, Reply, Quote, a continuation's time, and the manage
 * dot.
 *
 * Split out of the row because the row is about *a comment* - who, when, what -
 * and this is about what the reader can do about it, which is the half that grows
 * every time a control is added.
 *
 * The bar scrolls rather than clips. Heart, Reply, Quote, the timestamp and the
 * manage dot are five targets under one body, and a threaded comment starts 56dp
 * in from the edge: on a 360dp phone the last control ran off the screen, and the
 * one that edits or deletes your own comment is the worst one to hide.
 */
@Composable
private fun CommentRowActions(
    reply: ActivityReply,
    isContinuation: Boolean,
    likePending: Boolean,
    canManage: Boolean,
    canReply: Boolean,
    canQuote: Boolean,
    nowEpochSecond: Long?,
    onLike: () -> Unit,
    onShowLikers: () -> Unit,
    onReply: (String) -> Unit,
    onQuote: () -> Unit,
    onManage: () -> Unit
) {
    val liked = reply.isLiked == true
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The same control the activity card draws, shared rather than re-declared
        // here - `EngagementAction`.
        EngagementAction(
            icon = if (liked) AppVectorIcons.ActivityLiked else AppVectorIcons.ActivityNotLiked,
            count = reply.likeCount ?: 0,
            active = liked,
            // Null `isLiked` means AniList did not answer, which is what a
            // signed-out reader gets. Disabled rather than drawn-as-unliked, so the
            // control is never something that looks live and does nothing.
            enabled = reply.isLiked != null && !likePending,
            contentDescription = if (liked) "Unlike comment" else "Like comment",
            onClick = onLike,
            // The hold asks whose. A comment's like count used to be the only answer
            // the row had, while the card above it had always offered the names.
            onLongClick = if ((reply.likeCount ?: 0) > 0) onShowLikers else null,
            modifier = Modifier.testTag("activity_reply_like_button")
        )
        if (canReply) {
            Spacer(modifier = Modifier.width(4.dp))
            val name = reply.user?.name.orEmpty()
            CommentTextAction(
                label = "Reply",
                icon = AppVectorIcons.ActivityReplies,
                onClickLabel = "Reply to $name",
                tag = "activity_reply_reply_button",
                onClick = { onReply(name) }
            )
        }
        if (canQuote) {
            Spacer(modifier = Modifier.width(4.dp))
            CommentTextAction(
                label = "Quote",
                icon = AppVectorIcons.TextQuote,
                onClickLabel = "Quote this comment",
                tag = "activity_reply_quote_button",
                onClick = onQuote
            )
        }
        if (isContinuation) {
            CommentTimestamp(reply.createdAt, nowEpochSecond)
        }
        Spacer(modifier = Modifier.weight(1f))
        if (canManage) {
            CommentManageButton(onClick = onManage)
        }
    }
}

/**
 * The time, after the name on a header row and at the end of the controls on a
 * continuation row.
 *
 * A continuation still needs it: "they said this, then this, then this" is one turn
 * of a conversation, and "an hour later, again" is a different thing. It is the
 * header that is redundant, not the timestamp.
 */
@Composable
private fun CommentTimestamp(createdAt: Int?, nowEpochSecond: Long?) {
    if (createdAt == null) return
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        text = formatRelativeSeconds(
            createdAt,
            // `nowEpochSecond` is the profile's minute-tick (see `ProfileClock`), so
            // "12m" becomes "1h" while the reader is still looking at it. Null falls
            // back to the clock on the device.
            nowEpochSecond?.let { Instant.ofEpochSecond(it) } ?: Instant.now()
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

/**
 * A labelled row control - Reply, Quote.
 *
 * A labelled control rather than a second icon: on this row the heart is a bare
 * icon with a count, and a bare arrow beside it would be another glyph to guess.
 * The label is also what makes the row's taps distinguishable to a screen reader
 * without a description that says "reply to Bild's comment" on every row.
 */
@Composable
private fun CommentTextAction(
    label: String,
    icon: ImageVector,
    onClickLabel: String,
    tag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .bouncyPress(pressedScale = 0.9f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = onClickLabel,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The overflow that opens the manage dialog, on the viewer's own comment.
 *
 * A tap target of its own rather than a long press on the row: the sheet's only
 * hold gesture elsewhere (the heart) opens likers, and nothing about a comment's
 * body says "hold me to edit this". 48dp, the platform's minimum touch target,
 * rather than the 36dp box that was sized to its glyph - manage is the one control
 * in the row that changes somebody's comment, and it was also the hardest to hit.
 */
@Composable
private fun CommentManageButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .bouncyPress(pressedScale = 0.9f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                // The spring is the feedback. A ripple inside a 48dp circle that is
                // already scaling reads as a second, competing animation - the same
                // call the format bar and the reply control make.
                indication = null,
                onClick = onClick
            )
            .testTag("activity_reply_manage_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppVectorIcons.ActivityManage,
            contentDescription = "Comment options",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ReplyBody(text: String?) {
    // A child composable rather than an early return in the row: `remember` after a
    // return is a conditional composable call, and a reply whose text AniList
    // omitted would then occupy the slot the parser state wants.
    if (!text.isNullOrBlank()) MarkdownBody(text)
}

@Composable
private fun MarkdownBody(text: String) {
    // The cover-aware renderer: a comment can link an AniList title, and the card
    // that link becomes draws a poster only if a loader is in scope. The loader
    // comes from `LocalMediaCoverLoader`, provided by the sheet's host, and the
    // document is parsed once inside this call rather than per frame.
    AniListMarkdownWithCovers(markdown = text)
}

@Composable
private fun CommentAvatar(url: String?, name: String) {
    val base = Modifier
        .size(34.dp)
        .clip(CircleShape)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = base.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = base.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * The row that reaches further back: how many comments exist that are not drawn,
 * and a tap that fetches the page above.
 *
 * It is the first item of the list rather than a button over it, so it scrolls
 * away with the thread instead of parking itself permanently above the newest
 * message. That placement is also what makes the control honest: pressing it adds
 * rows *above*, and the row that does the adding is the one the reader has to
 * reach before they get them.
 */
@Composable
internal fun CommentOlderRow(
    hiddenCount: Int,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .bouncyPress(pressedScale = 0.94f, interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isLoading,
                    onClickLabel = "Load older comments",
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("activity_comments_older"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isLoading) {
                    "Loading…"
                } else {
                    // The thread's own count rather than a vague "older": a reader
                    // deciding whether to read back through nine hundred comments
                    // needs to know it is nine hundred.
                    "Older  $hiddenCount"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * The pill that says the conversation moved while you were reading it.
 *
 * A count, not an arrow, because the number is the question the reader is asking -
 * and it floats over the bottom of the list rather than sitting in it, so it cannot
 * be missed by a reader parked three pages back. Tapping it does the two things the
 * store has already decided: the new rows come into the window, and the list goes
 * to them.
 */
@Composable
internal fun NewCommentsPill(count: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .bouncyPress(pressedScale = 0.94f, interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Read the new comments",
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("activity_comments_new_pill"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppVectorIcons.ActivityReplies,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count new",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
    }
}
