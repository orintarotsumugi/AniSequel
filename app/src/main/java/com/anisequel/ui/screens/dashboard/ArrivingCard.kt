package com.anisequel.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.ArrivingEntry
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.SequelStatusChip
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * Standard card for an airing or announced anime in "Currently arriving".
 *
 * Sits in the horizontal LazyRow when arriving section is in regular mode.
 */
@Composable
fun ArrivingCard(
    entry: ArrivingEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The clock the countdown pill is read against.
     *
     * Passed in rather than read here so every card in the row shares one
     * minute ticker. The section owns it; a per-card clock would let two pills
     * print different minutes for the same episode.
     */
    nowMillis: Long = System.currentTimeMillis()
) {
    val placeholderTint = entry.coverColor.toCoverColorOrNull()
    // Shared with the Card's own clickable: without it `bouncyPress` installs a
    // second pointer loop that does not know a fling from a press, so a finger
    // resting on a card while the row scrolls left it squashed for the whole
    // gesture. See `Modifier.bouncyPress`.
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(148.dp)
            .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
            .testTag("arriving_card_${entry.mediaId}"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            // The group, not the card type: an airing card and an upcoming card are
            // the same card at the same cover height. The background is what tells
            // the two groups apart, so the eye never has to compare layouts.
            containerColor = if (entry.isAiring) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        interactionSource = interactionSource
    ) {
        // A square cover. The 148x188 strip read as a 1x2 tile beside the
        // dashboard's own square cards, and its extra height was what made a row
        // of them look ragged next to the text under each one.
        //
        // The Card already clips its content to `extraLarge`; this box used to
        // clip itself to the same shape, which rounded the *bottom* corners of
        // the artwork as well and left a floating tile above the title. One clip,
        // on the container, gives the cover the card's top corners and a straight
        // bottom edge for the text block.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
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
                        .aspectRatio(1f),
                    contentScale = ContentScale.Crop
                )
            }

            // The countdown / next episode pill rides on the artwork.
            val countdown = entry.nextAiringCountdown(nowMillis)
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
                // Two lines reserved on every card, so a one-line title does not
                // make its tile shorter than the one beside it.
                minLines = 2,
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
 *
 * Takes the same [ArrivingRow]s as the card layout, so the "separate groups"
 * choice reaches this mode too. Compact used to flatten the section, which made
 * the setting silently stop applying the moment the reader turned compact on.
 */
@Composable
fun CompactArrivingList(
    rows: List<ArrivingRow>,
    onEntryClick: (ArrivingEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = rows.sumOf { it.entries.size }
    var drawn = 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("arriving_compact_list")
    ) {
        rows.forEach { row ->
            if (row.label != null) {
                ArrivingGroupLabel(
                    label = row.label,
                    airing = row.airing,
                    count = row.entries.size
                )
            }
            row.entries.forEach { entry ->
                CompactArrivingRow(
                    entry = entry,
                    onClick = { onEntryClick(entry) }
                )
                drawn++
                if (drawn != total) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

/** One artwork-free row: the title, its status chip and the schedule line. */
@Composable
private fun CompactArrivingRow(
    entry: ArrivingEntry,
    onClick: () -> Unit
) {
    val placeholderTint = entry.coverColor.toCoverColorOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            total != null -> "Airing · $total eps"
            else -> "Airing now"
        }
    }
    else -> entry.startLabel?.let { "Starts $it" } ?: "Start date TBA"
}
