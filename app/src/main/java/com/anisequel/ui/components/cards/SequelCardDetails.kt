package com.anisequel.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MissedSequel
import com.anisequel.domain.card.CardScorePlacement
import com.anisequel.domain.card.CardTitlePlacement
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.MarqueeText
import com.anisequel.ui.components.expressive.WavyProgressBar
import com.anisequel.ui.theme.AniSequelTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SequelCardDetails(
    sequel: MissedSequel,
    modifier: Modifier = Modifier
) {
    val style = LocalMediaCardStyle.current
    val titleOverlaid = style.titlePlacement == CardTitlePlacement.OVERLAY_BOTTOM
    val titleHidden = style.titlePlacement == CardTitlePlacement.HIDDEN
    // The rating the chip row shows today, as the number the banner needs.
    val scoreValue = sequel.sequelMedia.averageScore

    Column(modifier = modifier) {
        // A marquee rather than a two-line ellipsis: the sequel's name is the one
        // fact the card exists for, and "…" on the second line was cutting the part
        // that distinguishes it from the show the reader already finished.
        //
        // Skipped when the style lays the name over the artwork instead: the poster
        // already draws it, and a card that says its own title twice is not showing
        // the reader a style, it is showing a bug.
        if (!titleOverlaid && !titleHidden) {
            MarqueeText(
                text = sequel.sequelTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // The rating as a band is the one placement that belongs to this column
        // rather than to the chip row: it is the width of the card, not one of its
        // facts, and it is drawn where the title used to leave off.
        if (style.scorePlacement == CardScorePlacement.BANNER_BELOW_TITLE && scoreValue != null) {
            Spacer(modifier = Modifier.height(if (titleOverlaid || titleHidden) 0.dp else 6.dp))
            ScoreBanner(score = scoreValue)
        }

        // Romaji and English title tiles
        AliasTiles(sequel)

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SequelStatusChip(status = sequel.status)
            SequelInfoChip(
                icon = AppVectorIcons.List,
                label = sequel.episodes
            )
            // The rating is drawn once. Two of the five styles put it on the artwork
            // and one stretches it across the card as a band, and in all three the
            // chip would be the same number a second time.
            if (sequel.score != "N/A" &&
                !style.scoreOnPoster &&
                style.scorePlacement != CardScorePlacement.BANNER_BELOW_TITLE
            ) {
                SequelInfoChip(
                    icon = AppVectorIcons.AnimeSparkle,
                    label = sequel.score
                )
            }
            sequel.studioName?.let { studio ->
                SequelInfoChip(
                    icon = AppVectorIcons.Studio,
                    label = studio
                )
            }
            sequel.source?.let { source ->
                SequelInfoChip(
                    icon = AppVectorIcons.SourceBook,
                    label = source
                )
            }
        }

        // Airing Countdown ticker
        AiringCountdownRow(sequel)

        // Watch Progress Meter
        WatchProgressRow(sequel)

        if (sequel.genres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = sequel.genres.take(3).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The Romaji and English title tiles.
 *
 * Private rather than inlined because the calling function is the details column -
 * one Column, three chips, two rows - and the aliated tiles are three nested rows
 * of their own. Keeping them together here keeps the column's own branches down
 * to "is there art for this".
 */
@Composable
private fun AliasTiles(sequel: MissedSequel) {
    val romaji = sequel.romajiTitle
    val english = sequel.englishTitle

    if (romaji.isNullOrBlank() && english.isNullOrBlank()) return
    Spacer(modifier = Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (!romaji.isNullOrBlank() && romaji != sequel.sequelTitle) {
            AliasRow(label = "Romaji", text = romaji, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        if (!english.isNullOrBlank() && english != sequel.sequelTitle) {
            AliasRow(label = "English", text = english, container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** One alias line: the small language tile, and the name beside it. */
@Composable
private fun AliasRow(
    label: String,
    text: String,
    container: Color,
    content: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = container,
            modifier = Modifier.padding(end = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The airing countdown line, when the sequel is airing and has one. */
@Composable
private fun AiringCountdownRow(sequel: MissedSequel) {
    val statusColors = AniSequelTheme.statusColors
    val countdown = sequel.nextAiringCountdown()
    if (!sequel.isAiring || countdown == null) return
    Spacer(modifier = Modifier.height(5.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = AppVectorIcons.CalendarClock,
            contentDescription = null,
            tint = statusColors.success,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = sequel.nextEpisodeNumber?.let { "Next: Ep $it · $countdown" }
                ?: "Airing · $countdown",
            style = MaterialTheme.typography.labelSmall,
            color = statusColors.success,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** The progress bar and its "3 / 12" label, when the entry carries both. */
@Composable
private fun WatchProgressRow(sequel: MissedSequel) {
    val statusColors = AniSequelTheme.statusColors
    val progressLabel = sequel.watchProgressLabel()
    val progress = sequel.watchProgress()
    if (progressLabel == null || progress == null) return
    Spacer(modifier = Modifier.height(5.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        WavyProgressBar(
            progress = { progress },
            color = statusColors.info,
            modifier = Modifier
                .width(54.dp)
                .height(6.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = progressLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
