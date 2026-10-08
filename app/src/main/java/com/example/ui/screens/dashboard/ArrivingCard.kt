package com.example.ui.screens.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ArrivingEntry
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.cards.SequelStatusChip
import com.example.ui.components.cards.toCoverColorOrNull
import com.example.ui.components.expressive.bouncyPress

/**
 * Standard card for an airing or announced anime in "Currently arriving".
 *
 * Sits in the horizontal LazyRow when arriving section is in regular mode.
 */
@Composable
fun ArrivingCard(
    entry: ArrivingEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val placeholderTint = entry.coverColor.toCoverColorOrNull()

    Card(
        onClick = onClick,
        modifier = modifier
            .width(148.dp)
            .bouncyPress(pressedScale = 0.97f)
            .testTag("arriving_card_${entry.mediaId}"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(placeholderTint ?: MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(32.dp)
            )

            if (entry.coverUrl != null) {
                AsyncImage(
                    model = entry.coverUrl,
                    contentDescription = "Poster for ${entry.title}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp),
                    contentScale = ContentScale.Crop
                )
            }

            // The countdown / next episode pill rides on the artwork.
            val countdown = entry.nextAiringCountdown()
            if (countdown != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = countdown,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SequelStatusChip(status = entry.status)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = arrivalMeta(entry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The same list as [ArrivingCard] but without artwork, for lower data/battery consumption.
 */
@Composable
fun CompactArrivingList(
    entries: List<ArrivingEntry>,
    onEntryClick: (ArrivingEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("arriving_compact_list")
    ) {
        entries.forEachIndexed { index, entry ->
            val placeholderTint = entry.coverColor.toCoverColorOrNull()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEntryClick(entry) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("arriving_compact_row_${entry.mediaId}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            placeholderTint ?: MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SequelStatusChip(status = entry.status)
                        Text(
                            text = arrivalMeta(entry),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (index != entries.lastIndex) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

/**
 * Meta label under status chip describing next episode or start schedule.
 */
fun arrivalMeta(entry: ArrivingEntry): String = when {
    entry.isAiring -> {
        val episode = entry.nextEpisodeNumber
        val total = entry.episodes
        when {
            episode != null && total != null -> "Ep $episode / $total"
            episode != null -> "Ep $episode"
            total != null -> "Airing of $total eps"
            else -> "Airing now"
        }
    }
    else -> entry.startLabel?.let { "Starts $it" } ?: "Start date TBA"
}

/**
 * Open external destination on AniList safely.
 */
fun openOnAniList(context: Context, entry: ArrivingEntry) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(entry.siteUrl))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
