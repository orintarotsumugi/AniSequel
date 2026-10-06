package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.viewmodel.UserOverviewUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The banner, avatar, name, the two list totals, and when the account joined.
 *
 * The banner is a fixed 108dp with the avatar and name directly beneath it rather
 * than overlapping its bottom edge.
 *
 * The overlap is what AniList itself draws, and it is not reproducible here for a
 * reason worth writing down: the avatar has to hang half outside the banner, and
 * anything holding both is either clipped to its own bounds - which cuts the
 * avatar in half - or has to buy the space back with a negative offset. The
 * negative offset costs dead layout space below, because `offset` moves a node
 * without changing what it measures. Stacked reads the same at a glance and does
 * not depend on that arithmetic holding on every screen size.
 */
@Composable
fun ProfileHeaderCard(
    state: UserOverviewUiState.Success,
    modifier: Modifier = Modifier,
    maxWidth: Dp = ProfileMaxContentWidth
) {
    Column(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("profile_header")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            val banner = state.bannerUrl
            if (banner != null) {
                AsyncImage(
                    model = banner,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(AppVectorIcons.AnimeSparkle),
                    error = rememberVectorPainter(AppVectorIcons.AnimeSparkle),
                    fallback = rememberVectorPainter(AppVectorIcons.AnimeSparkle)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            AsyncImage(
                model = state.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(AppVectorIcons.SequelJump),
                error = rememberVectorPainter(AppVectorIcons.SequelJump),
                fallback = rememberVectorPainter(AppVectorIcons.SequelJump)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.username,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "AniList Member",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeaderStat(
                label = "Anime",
                value = state.animeCount.toString(),
                icon = AppVectorIcons.FavouriteAnime,
                modifier = Modifier.weight(1f)
            )
            HeaderStat(
                label = "Manga",
                value = state.mangaCount.toString(),
                icon = AppVectorIcons.FavouriteManga,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.createdAt != null) {
                MetaChip(
                    icon = AppVectorIcons.ProfileJoined,
                    label = "Joined ${formatJoinYear(state.createdAt)}"
                )
            }
            if (state.updatedAt != null) {
                MetaChip(
                    icon = AppVectorIcons.ProfileUpdated,
                    label = "Updated ${formatRelativeSeconds(state.updatedAt)}"
                )
            }
        }
    }
}

@Composable
private fun HeaderStat(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MetaChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("profile_meta_chip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * The year an account was created.
 *
 * Null for accounts AniList does not backfill - the field simply does not exist
 * before 2020 - and the chip is hidden rather than showing "Joined unknown".
 */
internal fun formatJoinYear(createdAt: Int): String =
    Instant.ofEpochSecond(createdAt.toLong())
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy", Locale.ROOT))

/**
 * A Unix timestamp as "5 hours ago".
 *
 * `Locale.ROOT` on the format, so a device set to a locale that writes digits or
 * month names differently does not render a chip in a script the surrounding text
 * is not in - and relative time is the one place where that is most visible,
 * because it sits next to a username.
 */
internal fun formatRelativeSeconds(timestamp: Int, now: Instant = Instant.now()): String {
    val seconds = now.epochSecond - timestamp
    if (seconds < 0) return "just now"
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val years = days / 365
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 365 -> "${days}d ago"
        else -> "${years}y ago"
    }
}

/**
 * Minutes watched as the largest two units that say something.
 *
 * "1,122h" rather than a running clock: the number exists to be compared with
 * other people's, and everybody else's is in hours.
 */
internal fun formatWatchTime(minutesWatched: Long): String {
    if (minutesWatched <= 0) return "0h"
    val hours = minutesWatched / 60
    if (hours < 1) return "${minutesWatched}m"
    if (hours < 24) return "${hours}h"
    val days = hours / 24
    val remainderHours = hours % 24
    return if (remainderHours == 0L) "${days}d" else "${days}d ${remainderHours}h"
}