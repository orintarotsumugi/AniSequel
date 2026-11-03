package com.anisequel.ui.screens.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.ActivityReply
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
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
 * One line under the body, with the two things you press on the left and the two
 * things that describe the row - the time and the overflow - on the right. A body can
 * be a multi-line quote or an image, so nothing is allowed to sit at the end of
 * whatever line happened to be last, and the overflow in particular has to be in the
 * same place on every row or finding it on your own comment is a search.
 * [CommentRowActions] is the half that works that out, including what the bar does
 * when a threaded comment has no room for the words "Reply" and "Quote".
 *
 * ## The two rails
 *
 * A vertical line runs down the gutter left of every nested comment, in the colour of
 * whatever the row is: [isReplyTarget] paints it in primary and tints the block,
 * [isOwnComment] paints it in a faded primary, and an ordinary reply gets the surface
 * grey. One element carrying three meanings is deliberate - a thread is read by
 * following lines, and a reader who can see which line theirs is does not have to
 * remember their own name in a list of forty.
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
    /**
     * Whether this is the comment the reader is answering.
     *
     * Set while the composer is open against it. A thread of forty comments with a
     * "Replying to YoruC" chip above a field does not tell the reader *which* YoruC
     * comment they are threading, and the one they quoted three screens up has
     * already scrolled away - so the row it belongs to is drawn with a rail and a
     * tint, the same way a comment section marks the parent of a reply in progress.
     */
    isReplyTarget: Boolean = false,
    /**
     * Whether the viewer wrote this comment.
     *
     * The rail is the only difference: a thread of a hundred comments in which you
     * wrote four is a search, and the search had no cue in it. Not a tint - your own
     * rows tinted like the reply target would make the two indistinguishable, and
     * one of them is a temporary state.
     */
    isOwnComment: Boolean = false,
    /**
     * Whether the heart is drawn at all.
     *
     * False for a message thread's head row: a message is likeable, but as an
     * *activity*, and the store only speaks the reply mutation. Drawing the heart
     * there would offer a control whose request is the wrong one. Defaulted to true
     * so every reply row is unchanged.
     */
    showLikes: Boolean = true,
    onLike: () -> Unit,
    onShowLikers: () -> Unit,
    onReply: (ActivityReply) -> Unit,
    onQuote: () -> Unit,
    onManage: () -> Unit,
    nowEpochSecond: Long? = null,
    /**
     * The lazy list's own modifier, handed in so the row can animate its placement.
     *
     * `CommentRow` is the item root of the thread's `LazyColumn`, and
     * `Modifier.animateItem` has to sit on that root: a comment that arrives while
     * the sheet is open slid into place at nothing before, and the row below the
     * one that was deleted jumped up instead of closing the gap.
     */
    modifier: Modifier = Modifier
) {
    val rowStart = 20.dp + 18.dp * indent
    val guide = if (isReplyTarget) {
        MaterialTheme.colorScheme.primary
    } else if (isOwnComment) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    // Animated toward a settled colour, and read only by the draw phase. The tint
    // used to be `clip` + `background` + 16dp of horizontal padding on the content
    // column, which did two things at once: the body reflowed sideways the moment
    // the reader tapped Reply, and - because the padded column is what
    // [CommentRowActions] measures - the bar lost its labels and dropped to glyphs
    // on exactly the row the reader was answering. Drawing the pill behind the
    // column changes no measurement at all.
    val highlight by animateColorAsState(
        targetValue = if (isReplyTarget) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f)
        } else {
            Color.Transparent
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "commentReplyTarget"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The thread line. An indent alone says a comment is nested; it does not
            // say *what* it is nested under, and two levels in, on a phone, the four
            // steps of indentation are the whole relationship a reader has to work
            // from. The line runs down the gutter the avatar would have used.
            .drawBehind {
                if (indent > 0) {
                    val x = (rowStart - 14.dp).toPx()
                    drawLine(
                        color = guide,
                        start = Offset(x, 6.dp.toPx()),
                        end = Offset(x, size.height - 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            .padding(
                // One step per threaded level, capped in [ReplyThreader]. The
                // mention itself is still in the body, so the indent is an aid
                // rather than the only record of who is being answered.
                start = rowStart,
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
        Column(
            modifier = Modifier
                .weight(1f)
                .drawBehind {
                    if (highlight.alpha > 0f) {
                        drawRoundRect(
                            color = highlight,
                            // Into the gutter and the sheet's end margin, drawn
                            // outside the measured bounds so nothing moves.
                            topLeft = Offset(-10.dp.toPx(), 0f),
                            size = Size(size.width + 16.dp.toPx(), size.height),
                            cornerRadius = CornerRadius(
                                x = ExpressiveShapes.SmallCorner.toPx(),
                                y = ExpressiveShapes.SmallCorner.toPx()
                            )
                        )
                    }
                }
        ) {
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
                showLikes = showLikes,
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
 * The time, after the name on a header row and at the end of the controls on a
 * continuation row.
 *
 * A continuation still needs it: "they said this, then this, then this" is one turn
 * of a conversation, and "an hour later, again" is a different thing. It is the
 * header that is redundant, not the timestamp.
 */
@Composable
internal fun CommentTimestamp(createdAt: Int?, nowEpochSecond: Long?) {
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
    onClick: () -> Unit,
    /**
     * The grid's item modifier, so an Older row that arrives with a refreshed window
     * slides into place rather than popping in above rows that then jump. Every other
     * row in this list animates its placement; this one and the thread's head row were
     * the two that simply appeared on the frame.
     */
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 40.dp)
                // A wide, short row is a capsule, not a circle: `CircleShape` is
                // percent-50 and happens to agree today, but the token says what
                // this is meant to be.
                .clip(ExpressiveShapes.pill)
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
            .clip(ExpressiveShapes.pill)
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
