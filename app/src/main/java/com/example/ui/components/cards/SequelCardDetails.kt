package com.example.ui.components.cards

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.WavyProgressBar
import com.example.ui.theme.AniSequelTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SequelCardDetails(
    sequel: MissedSequel,
    modifier: Modifier = Modifier
) {
    val statusColors = AniSequelTheme.statusColors

    Column(modifier = modifier) {
        Text(
            text = sequel.sequelTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Romaji and English title tiles
        val romaji = sequel.romajiTitle
        val english = sequel.englishTitle

        if (!romaji.isNullOrBlank() || !english.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (!romaji.isNullOrBlank() && romaji != sequel.sequelTitle) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "Romaji",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = romaji,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!english.isNullOrBlank() && english != sequel.sequelTitle) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "English",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = english,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

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
            if (sequel.score != "N/A") {
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
        val countdown = sequel.nextAiringCountdown()
        if (sequel.isAiring && countdown != null) {
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

        // Watch Progress Meter
        val progressLabel = sequel.watchProgressLabel()
        val progress = sequel.watchProgress()
        if (progressLabel != null && progress != null) {
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
