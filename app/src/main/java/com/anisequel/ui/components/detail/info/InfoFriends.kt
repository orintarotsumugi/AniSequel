package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.isManga
import com.anisequel.data.model.progressLabel
import com.anisequel.data.model.scoreLabel
import com.anisequel.data.model.spokenLabel
import com.anisequel.data.model.statusLabel
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import java.util.Locale

/**
 * The friends who have this title on their list: avatar, rating, progress.
 *
 * ## What it answers
 *
 * "Has anyone I follow seen this, and did they like it?" - which is the one
 * question about a title that AniList's own page cannot answer for the reader,
 * and the reason the card sits next to the viewer's own progress rather than at
 * the end of the page.
 *
 * ## Why it is not clickable
 *
 * Opening a friend's profile from here would mean threading a user-navigation
 * callback through four hosts that each own their own profile sheet, for a row
 * that is a glance rather than a destination. The row is drawn as content, not as
 * a button, so it does not promise a tap it cannot honour.
 *
 * ## Why the card is never drawn empty
 *
 * The page adds the item only when [friends] is non-empty - an item that renders
 * nothing still costs the page's `spacedBy` gap. The early return here is the
 * second half of that contract, so the two cannot disagree if the call site
 * changes.
 */
@Composable
fun InfoFriendsCard(
    friends: List<MediaFriend>,
    media: MediaNode,
    modifier: Modifier = Modifier,
    /**
     * Whether the follow-up request is still in flight.
     *
     * Used only when [friends] is empty: a card that waits for its first row in
     * silence loses the reader to the next section, and this one sits directly
     * under the progress bar on purpose. Once one row is here the list is the
     * answer, and a spinner beside it would only say "there may be more" about a
     * request AniList caps at fifty entries anyway.
     */
    isLoading: Boolean = false
) {
    if (friends.isEmpty() && !isLoading) return
    val isManga = media.isManga
    // The friend's entry carries no length; the media does. Unknown reads as
    // "12 eps" rather than "12 / null".
    val total = if (isManga) media.chapters else media.episodes

    InfoCard(
        modifier = modifier.testTag("info_friends_card"),
        // "watched" was wrong as soon as a plan or a drop was drawn here: neither
        // is watching, and the card is no longer only about people who have seen
        // it. The neutral wording is the same promise for every status.
        title = "Friends on this title",
        icon = AppVectorIcons.ProfileSocial,
        trailing = if (friends.isEmpty()) {
            null
        } else {
            {
                Text(
                    text = friends.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        if (friends.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                ExpressiveLoadingIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Checking who you follow…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(friends, key = { it.userId }) { friend ->
                    FriendEntry(friend = friend, isManga = isManga, total = total)
                }
            }
        }
    }
}

/**
 * One friend, as a column: face, what they are doing with the title, name, rating,
 * progress.
 *
 * The whole column is one semantics node. Read as its `Text`s, TalkBack
 * announces "Mika, 8.5, 12 / 24 eps" - a slash and a pile of fragments - so
 * [spokenLabel] replaces them with one sentence, and the avatar (which is
 * decorative next to the name) stays silent.
 *
 * The width is fixed rather than wrapping: a `LazyRow` of columns that each grew
 * to their own name would be a ragged row of faces at different offsets, and the
 * name is the least important line on the column - it ellipsises, the rating and
 * the progress do not.
 */
@Composable
private fun FriendEntry(
    friend: MediaFriend,
    isManga: Boolean,
    total: Int?
) {
    val rating = friend.scoreLabel

    // Watching leads, a plan is a quieter promise, and a dropped entry is the one
    // the row should not make look like a failure of the title - it is a fact
    // about that friend. `error` is a role, not a verdict, and it is the only tone
    // in the scheme that reads as "this one stopped" at label size.
    val statusColor = when (friend.status?.uppercase(Locale.ROOT)) {
        "CURRENT", "REPEATING" -> MaterialTheme.colorScheme.primary
        "PLANNING" -> MaterialTheme.colorScheme.secondary
        "DROPPED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .width(92.dp)
            .clearAndSetSemantics {
                contentDescription = friend.spokenLabel(isManga, total)
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = friend.avatarUrl,
            // The name below is the accessible identity; a description here would
            // announce the person twice.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(AppVectorIcons.ProfileSocial),
            error = rememberVectorPainter(AppVectorIcons.ProfileSocial),
            fallback = rememberVectorPainter(AppVectorIcons.ProfileSocial),
            modifier = Modifier
                .size(52.dp)
                // A circle, because that is what a face reads as; the app's
                // `shapes.large` squares are for artwork, and a 52dp square with a
                // 12dp radius looks like a thumbnail rather than a person.
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = friend.statusLabel(isManga),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = friend.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        if (rating != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppVectorIcons.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = rating,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            // "Not rated" rather than an empty slot: a friend who finished it
            // without scoring is a different fact from a friend who has not
            // opened it, and the row would otherwise look like missing data.
            Text(
                text = "Not rated",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = friend.progressLabel(isManga = isManga, total = total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
