package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.anisequel.ui.components.AppVectorIcons

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
    modifier: Modifier = Modifier
) {
    if (friends.isEmpty()) return
    val isManga = media.isManga
    // The friend's entry carries no length; the media does. Unknown reads as
    // "12 eps" rather than "12 / null".
    val total = if (isManga) media.chapters else media.episodes

    InfoCard(
        modifier = modifier.testTag("info_friends_card"),
        title = if (isManga) "Friends who read this" else "Friends who watched this",
        icon = AppVectorIcons.ProfileSocial
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(friends, key = { it.userId }) { friend ->
                FriendEntry(friend = friend, isManga = isManga, total = total)
            }
        }
    }
}

/**
 * One friend, as a column: face, name, rating, progress.
 *
 * The whole column is one semantics node. Read as its three `Text`s, TalkBack
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

    Column(
        modifier = Modifier
            .width(88.dp)
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
