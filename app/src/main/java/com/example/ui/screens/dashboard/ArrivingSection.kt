package com.example.ui.screens.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ArrivingEntry
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.cards.SequelStatusChip
import com.example.ui.components.cards.toCoverColorOrNull
import com.example.ui.components.expressive.ExpressiveCountBadge
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.bouncyPress

/**
 * The viewer's own list, split out into what is airing now and what has not
 * started yet.
 *
 * Deliberately its own section rather than a group at the top of the missed
 * list: the two answer different questions. Missed sequels are gaps the app
 * found by walking relation edges and every filter in the sheet applies to
 * them; these rows are things the viewer already tracks, and filtering them
 * would be answering "what am I missing?" with "here is something you are not
 * missing". So this section ignores the filter criteria entirely and hides
 * itself when there is nothing to report.
 */
@Composable
fun ArrivingSection(
    entries: List<ArrivingEntry>,
    maxWidth: Dp,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    Column(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .testTag("arriving_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Currently arriving",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                ExpressiveCountBadge(count = entries.size)
            }
            Text(
                text = "From your list",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        val context = LocalContext.current
        if (compact) {
            // Compact rows draw no artwork and carry no live countdown: the
            // poster fetch is the whole data and battery cost of these cards,
            // and the countdown pill is the one number that goes stale while
            // the screen is up. A compact section therefore loads nothing and
            // recomposes nothing on a schedule.
            CompactArrivingList(
                entries = entries,
                onEntryClick = { openOnAniList(context, it) }
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = entries, key = { it.mediaId }) { entry ->
                    ArrivingCard(
                        entry = entry,
                        onClick = { openOnAniList(context, entry) },
                        modifier = Modifier.animateItem(
                            placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                            fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                            fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                        )
                    )
                }
            }
        }
    }
}

/**
 * The destination of every arriving row: there is no in-app view for a show
 * that is already on the list, so all of them hand off to AniList's page -
 * the same one the detail sheet's "View on AniList" opens.
 */
private fun openOnAniList(context: Context, entry: ArrivingEntry) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(entry.siteUrl))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/**
 * The same list as [ArrivingCard] but without the artwork.
 *
 * One line of title over a status chip and the episode/start text, on a
 * colour swatch that stands in for the poster. Deliberately no
 * [AsyncImage]: the section's compact switch is what "uses less battery"
 * means in this app.
 */
@Composable
private fun CompactArrivingList(
    entries: List<ArrivingEntry>,
    onEntryClick: (ArrivingEntry) -> Unit
) {
    Column(
        modifier = Modifier
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
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            placeholderTint ?: MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp)
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

@Composable
private fun ArrivingCard(
    entry: ArrivingEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val placeholderTint = entry.coverColor.toCoverColorOrNull()

    Card(
        onClick = onClick,
        modifier = modifier
            .width(140.dp)
            .bouncyPress(pressedScale = 0.97f)
            .testTag("arriving_card_${entry.mediaId}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.large)
                .background(placeholderTint ?: MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(28.dp)
            )

            if (entry.coverUrl != null) {
                AsyncImage(
                    model = entry.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop
                )
            }

            // The countdown rides on the artwork rather than in the text
            // below, because it is the one number that goes stale: an episode
            // landing while the card is on screen changes this pill and not
            // the rest of the row.
            val countdown = entry.nextAiringCountdown()
            if (countdown != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = countdown,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SequelStatusChip(status = entry.status)
            }
            Spacer(modifier = Modifier.height(4.dp))
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
 * The line under the chip: how far along an airing entry is, or when an
 * upcoming one starts.
 *
 * Null-safe in both directions rather than showing a half-finished string -
 * an entry with no announced episode count gets no "/ 12", and one with no
 * announced date says so instead of rendering a blank line.
 */
private fun arrivalMeta(entry: ArrivingEntry): String = when {
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
