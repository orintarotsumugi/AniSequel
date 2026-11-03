package com.anisequel.ui.screens.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.ActivityReply
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.EngagementAction
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The row's controls: heart, Reply, Quote, a continuation's time, and the manage
 * dot.
 *
 * Split out of the row because the row is about *a comment* - who, when, what -
 * and this is about what the reader can do about it, which is the half that grows
 * every time a control is added.
 *
 * ## One line, with the overflow pinned to the right
 *
 * This was a `FlowRow`, which put the manage dot wherever the line ran out: on a
 * wide top-level comment it sat beside Quote, and on a threaded one it fell to a
 * second line under the timestamp, so the control that edits or deletes *your own*
 * comment was in a different place on every row and the row that had it looked
 * different from the row that did not. A comment section does not work that way -
 * the overflow is always the last thing on the line - so the bar is one `Row` now,
 * with the dot pinned to the trailing edge and the continuation's timestamp
 * immediately left of it.
 *
 * What gives way when the line runs out is decided by [commentActionBarForm], and it
 * is never the position of a control: first the two *words* "Reply" and "Quote" go
 * (the glyphs stay, inside their 48dp targets), and only on a row too narrow even for
 * that does a continuation's timestamp go - the one piece of information on this line
 * that the row above already carries, because a continuation is by definition somebody
 * speaking again within a few minutes.
 *
 * Reply and Quote are drawn as capsules rather than as bare label-and-glyph pairs: a
 * sentence of grey text under a comment reads as part of the comment, and these two
 * are the only controls in the row a reader is meant to press. The capsule is drawn
 * inside a 48dp target, so the visual weight is small while the touch area stays the
 * platform minimum.
 */
@Composable
internal fun CommentRowActions(
    reply: ActivityReply,
    isContinuation: Boolean,
    likePending: Boolean,
    canManage: Boolean,
    canReply: Boolean,
    canQuote: Boolean,
    showLikes: Boolean,
    nowEpochSecond: Long?,
    onLike: () -> Unit,
    onShowLikers: () -> Unit,
    /**
     * The comment itself, not only its author's name.
     *
     * The composer threads by mention, so the mention needs the name - but the row
     * that is lit while the editor is open needs the *id*, or every comment the same
     * person ever wrote is marked as the one being answered.
     */
    onReply: (ActivityReply) -> Unit,
    onQuote: () -> Unit,
    onManage: () -> Unit
) {
    val liked = reply.isLiked == true
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val form = commentActionBarForm(
            // `maxWidth` here is already a `Dp` - the constraints scope converts it
            // - so the number the arithmetic wants is its `value`, not another
            // `toDp()` round trip.
            availableDp = maxWidth.value,
            showLikes = showLikes,
            canReply = canReply,
            canQuote = canQuote,
            showTimestamp = isContinuation,
            canManage = canManage
        )
        Row(
            modifier = Modifier.animateContentSize(ExpressiveMotion.FastSpatialSize),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CommentPressActions(
                reply = reply,
                liked = liked,
                likePending = likePending,
                showLikes = showLikes,
                canReply = canReply,
                canQuote = canQuote,
                form = form,
                onLike = onLike,
                onShowLikers = onShowLikers,
                onReply = onReply,
                onQuote = onQuote
            )
            // The one thing a `FlowRow` could not do: everything the reader might
            // want is on the left, and the space that is left over belongs to the
            // time and the dot, which are always at the end of the line.
            Spacer(modifier = Modifier.weight(1f))
            CommentMetaActions(
                reply = reply,
                isContinuation = isContinuation,
                canManage = canManage,
                form = form,
                nowEpochSecond = nowEpochSecond,
                onManage = onManage
            )
        }
    }
}

/** The heart, Reply and Quote - everything that goes on the left of the bar. */
@Composable
private fun CommentPressActions(
    reply: ActivityReply,
    liked: Boolean,
    likePending: Boolean,
    showLikes: Boolean,
    canReply: Boolean,
    canQuote: Boolean,
    form: CommentActionBarForm,
    onLike: () -> Unit,
    onShowLikers: () -> Unit,
    onReply: (ActivityReply) -> Unit,
    onQuote: () -> Unit
) {
    if (showLikes) {
        // The same control the activity card draws, shared rather than re-declared
        // here - `EngagementAction`.
        EngagementAction(
            icon = if (liked) AppVectorIcons.ActivityLiked else AppVectorIcons.ActivityNotLiked,
            count = reply.likeCount ?: 0,
            active = liked,
            // Null `isLiked` means AniList did not answer, which is what a signed-out
            // reader gets. Disabled rather than drawn-as-unliked, so the control is
            // never something that looks live and does nothing.
            enabled = reply.isLiked != null && !likePending,
            contentDescription = if (liked) "Unlike comment" else "Like comment",
            onClick = onLike,
            // The hold asks whose. A comment's like count used to be the only answer
            // the row had, while the card above it had always offered the names.
            onLongClick = if ((reply.likeCount ?: 0) > 0) onShowLikers else null,
            modifier = Modifier.testTag("activity_reply_like_button")
        )
        if (canReply || canQuote) Spacer(modifier = Modifier.width(4.dp))
    }
    if (canReply) {
        CommentTextAction(
            label = "Reply",
            icon = AppVectorIcons.ActivityReplies,
            onClickLabel = "Reply to ${reply.user?.name.orEmpty()}",
            tag = "activity_reply_reply_button",
            compact = form != CommentActionBarForm.Full,
            onClick = { onReply(reply) }
        )
    }
    if (canQuote) {
        if (canReply) Spacer(modifier = Modifier.width(4.dp))
        CommentTextAction(
            label = "Quote",
            icon = AppVectorIcons.TextQuote,
            onClickLabel = "Quote this comment",
            tag = "activity_reply_quote_button",
            compact = form != CommentActionBarForm.Full,
            onClick = onQuote
        )
    }
}

/** The time and the overflow - the two things pinned to the trailing edge. */
@Composable
private fun CommentMetaActions(
    reply: ActivityReply,
    isContinuation: Boolean,
    canManage: Boolean,
    form: CommentActionBarForm,
    nowEpochSecond: Long?,
    onManage: () -> Unit
) {
    if (isContinuation && form != CommentActionBarForm.Minimal) {
        CommentTimestamp(reply.createdAt, nowEpochSecond)
    }
    if (canManage) CommentManageButton(onClick = onManage)
}

/**
 * The action bar's parts, in dp, at the sizes the row actually draws them.
 *
 * A `BoxWithConstraints` cannot ask Compose how wide a `Row` *would* be, so the bar
 * measures itself with this arithmetic instead. The numbers are the drawn ones: the
 * heart's 48dp target, a 48dp capsule with its label inside, the same capsule without
 * it, and the 4dp the row puts between controls.
 *
 * The heart was measured at 44, which is not a size it is ever drawn at:
 * `EngagementAction` sets a 48dp minimum. The bar therefore believed it had four dp
 * more than it did on every row, and the one row where that matters is the narrow,
 * nested one this arithmetic exists for.
 */
private const val LIKE_TARGET_DP = 48f
private const val ACTION_GAP_DP = 4f
private const val LABELED_ACTION_DP = 80f
private const val ICON_ONLY_ACTION_DP = 48f
private const val TIMESTAMP_DP = 46f
private const val MANAGE_TARGET_DP = 48f

/** How wide the bar is with Reply and Quote labelled, or with both dropped to glyphs. */
internal fun commentActionBarWidthDp(
    showLikes: Boolean,
    canReply: Boolean,
    canQuote: Boolean,
    showTimestamp: Boolean,
    canManage: Boolean,
    labelled: Boolean
): Float {
    val action = (if (labelled) LABELED_ACTION_DP else ICON_ONLY_ACTION_DP) + ACTION_GAP_DP
    var width = 0f
    if (showLikes) width += LIKE_TARGET_DP
    if (canReply) width += action
    if (canQuote) width += action
    if (showTimestamp) width += TIMESTAMP_DP
    if (canManage) width += MANAGE_TARGET_DP
    return width
}

/**
 * How the bar draws itself when the row is narrower than the bar wants to be.
 *
 * Three answers because there are three things a row can give up, and only two of
 * them are information: the words "Reply" and "Quote" are redundant with their
 * glyphs, and a continuation's timestamp is redundant with the header above it.
 * A *control* is never the thing that goes.
 */
internal enum class CommentActionBarForm {
    /** Reply and Quote carry their words. */
    Full,

    /** Glyphs only, inside the same 48dp targets; the continuation's time stays. */
    Compact,

    /** Glyphs only, and a continuation's time is not drawn. */
    Minimal
}

/**
 * The form this row's bar takes.
 *
 * The labelled bar is tried first: a reader who has room gets words, because "Reply"
 * and a bare arrow are not the same affordance next to a heart that is already a
 * glyph. Only a row that cannot fit it - a threaded comment on a narrow phone - pays
 * with icons, and only a row that cannot fit *that* loses a timestamp, which is the
 * one thing on the line the group's own header has already said.
 */
internal fun commentActionBarForm(
    availableDp: Float,
    showLikes: Boolean,
    canReply: Boolean,
    canQuote: Boolean,
    showTimestamp: Boolean,
    canManage: Boolean
): CommentActionBarForm {
    val labelled = commentActionBarWidthDp(
        showLikes = showLikes, canReply = canReply, canQuote = canQuote,
        showTimestamp = showTimestamp, canManage = canManage, labelled = true
    )
    if (labelled <= availableDp) return CommentActionBarForm.Full
    val iconOnly = commentActionBarWidthDp(
        showLikes = showLikes, canReply = canReply, canQuote = canQuote,
        showTimestamp = showTimestamp, canManage = canManage, labelled = false
    )
    if (iconOnly <= availableDp) return CommentActionBarForm.Compact
    return CommentActionBarForm.Minimal
}

/**
 * A labelled row control - Reply, Quote.
 *
 * A labelled control rather than a second icon: on this row the heart is a bare
 * icon with a count, and a bare arrow beside it would be another glyph to guess.
 * The label is also what makes the row's taps distinguishable to a screen reader
 * without a description that says "reply to Bild's comment" on every row.
 *
 * The capsule sits inside the 48dp target rather than being it: a filled 48dp block
 * beside the heart's bare glyph read as a button next to a caption. The press spring
 * scales the target (the touch area does not move), so the chip still answers the
 * finger.
 */
@Composable
private fun CommentTextAction(
    label: String,
    icon: ImageVector,
    onClickLabel: String,
    tag: String,
    compact: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .bouncyPress(pressedScale = 0.9f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = onClickLabel,
                onClick = onClick
            )
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(ExpressiveShapes.pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                // A glyph alone has to say what it is. The label is dropped for the
                // narrow row, not the name of the control: with `null` here the
                // compact bar's two buttons are announced by their `onClickLabel`
                // only, which a screen reader uses for the action it offers rather
                // than for what is on screen.
                contentDescription = if (compact) label else null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            if (!compact) {
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
