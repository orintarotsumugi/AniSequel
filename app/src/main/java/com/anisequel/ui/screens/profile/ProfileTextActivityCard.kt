package com.anisequel.ui.screens.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.ProfileActivity
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.EngagementAction
import com.anisequel.ui.components.cards.SequelInfoChip
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownWithCovers
import java.time.Instant

/**
 * The text-post half of the activity feed.
 *
 * Apart from the list card on purpose, not by line count: a list update is a
 * poster, a progress line and a medium chip, while a post is user-written
 * Markdown with its own expand/collapse state. The two share the author row and
 * the engagement controls, and nothing else changes for the same reason.
 */

/**
 * How many lines of a text post a closed card shows.
 *
 * A feed row is a summary, not the post: a five-paragraph status would push
 * every list update below it off the screen. The card offers "Read more" when
 * the clamped render actually hid something - the clamp and the block limit
 * both report through `onOverflowChange`, so a cut in either is announced.
 */
private const val TEXT_COLLAPSED_LINES = 9

/**
 * The author row both card shapes start with.
 *
 * [avatarSize] is the one thing that differs - a text post has no poster to
 * anchor the eye, so its avatar is drawn a little larger - and everything else
 * must stay identical, or a feed of mixed cards stops reading as one feed.
 *
 * [onOpenAuthor] is non-null only in the friends feed, where the author is a
 * different person on every row and tapping their name is the way to their
 * profile. The profile feed leaves it null: the author is the person whose page
 * is already open, so the row is not a link and does not ripple.
 */
@Composable
internal fun ActivityAuthorRow(
    userName: String?,
    userAvatar: String?,
    createdAt: Int?,
    nowEpochSecond: Long?,
    avatarSize: Dp,
    onOpenAuthor: (() -> Unit)? = null
) {
    if (userName == null && userAvatar == null && createdAt == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onOpenAuthor != null) {
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onOpenAuthor)
                    } else {
                        Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (userAvatar != null) {
                AsyncImage(
                    model = userAvatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            if (userName != null) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (createdAt != null) {
            Text(
                text = formatRelativeSeconds(
                    timestamp = createdAt,
                    now = Instant.ofEpochSecond(nowEpochSecond ?: Instant.now().epochSecond)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A text post: the author's own words, without a show attached.
 *
 * The feed used to ask AniList only for list updates, so this member of
 * `ActivityUnion` never arrived at all - the person's status posts were simply
 * absent from their profile. The body is AniList Markdown, rendered with the
 * same parser as the bio and replies, and clamped to [TEXT_COLLAPSED_LINES]
 * until the reader asks for the rest. Tapping the card opens the thread, which
 * is the closest thing this app has to the post's own page.
 */
@Composable
internal fun ActivityTextCard(
    activity: ProfileActivity,
    modifier: Modifier,
    userName: String?,
    userAvatar: String?,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onOpenLikers: () -> Unit,
    likePending: Boolean,
    nowEpochSecond: Long?,
    /** Opens the author's profile; null on the profile's own feed. See [ActivityAuthorRow]. */
    onOpenAuthor: (() -> Unit)? = null
) {
    val text = activity.text ?: return
    val interactionSource = remember { MutableInteractionSource() }
    var expanded by remember(activity.id) { mutableStateOf(false) }
    // Latched rather than read live: `onOverflowChange` describes the *collapsed*
    // render, so an expanded card reports `false` - and a control bound to the
    // live value would delete its own "Show less" the moment it was pressed.
    var everOverflowed by remember(activity.id) { mutableStateOf(false) }

    Card(
        modifier = modifier
            .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                onClick = onComments
            )
            .testTag("activity_text_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(14.dp)
        ) {
            ActivityAuthorRow(
                userName = userName,
                userAvatar = userAvatar,
                createdAt = activity.createdAt,
                nowEpochSecond = nowEpochSecond,
                avatarSize = 40.dp,
                onOpenAuthor = onOpenAuthor
            )

            AniListMarkdownWithCovers(
                markdown = text,
                maxLines = if (expanded) Int.MAX_VALUE else TEXT_COLLAPSED_LINES,
                onOverflowChange = { hidden -> if (hidden) everOverflowed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("activity_text_body")
            )

            if (everOverflowed) {
                Text(
                    text = if (expanded) "Show less" else "Read more",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("activity_read_more")
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                SequelInfoChip(
                    icon = AppVectorIcons.ProfileAbout,
                    label = "Post"
                )
                EngagementAction(
                    icon = if (activity.isLiked == true) {
                        AppVectorIcons.ActivityLiked
                    } else {
                        AppVectorIcons.ActivityNotLiked
                    },
                    count = activity.likeCount ?: 0,
                    active = activity.isLiked == true,
                    enabled = activity.isLiked != null && !likePending,
                    contentDescription = if (activity.isLiked == true) "Unlike" else "Like",
                    onClick = onLike,
                    onLongClick = onOpenLikers,
                    modifier = Modifier.testTag("activity_like_button")
                )
                EngagementAction(
                    icon = AppVectorIcons.ActivityReplies,
                    count = activity.replyCount ?: 0,
                    active = false,
                    enabled = true,
                    contentDescription = "Comments",
                    onClick = onComments,
                    modifier = Modifier.testTag("activity_comments_button")
                )
            }
        }
    }
}
