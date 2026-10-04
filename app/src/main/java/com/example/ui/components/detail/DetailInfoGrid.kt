package com.example.ui.components.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.data.model.MissedSequel
import com.example.ui.components.expressive.WavyProgressBar
import com.example.ui.theme.AniSequelTheme

@Composable
fun DetailInfoGrid(sequel: MissedSequel, modifier: Modifier = Modifier) {
    val statusColors = AniSequelTheme.statusColors
    val statusValue = when {
        sequel.isAiring -> "Currently Airing"
        sequel.isUnreleased -> "Upcoming"
        sequel.isHiatus -> "On Hiatus"
        sequel.status.equals("CANCELLED", ignoreCase = true) -> "Cancelled"
        else -> "Finished"
    }

    val statusColor = when {
        sequel.isAiring -> statusColors.success
        sequel.isUnreleased -> statusColors.info
        sequel.isHiatus -> statusColors.warning
        else -> null
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoBlock(
                label = "Status",
                value = statusValue,
                valueColor = statusColor,
                modifier = Modifier.weight(1f)
            )
            InfoBlock(
                label = "Episodes",
                value = sequel.episodes,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoBlock(
                label = "Score",
                value = sequel.score,
                modifier = Modifier.weight(1f)
            )

            val nextEpisode = sequel.nextEpisodeNumber
            val countdown = sequel.nextAiringCountdown()
            InfoBlock(
                label = "Next Episode",
                value = when {
                    nextEpisode == null -> "—"
                    countdown != null -> "Episode $nextEpisode · $countdown"
                    else -> "Episode $nextEpisode"
                },
                valueColor = if (nextEpisode != null) statusColors.info else null,
                modifier = Modifier.weight(1f)
            )
        }

        val progress = sequel.watchProgress()
        val progressLabel = sequel.watchProgressLabel()
        if (progress != null && progressLabel != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColors.info,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    WavyProgressBar(
                        progress = { progress },
                        color = statusColors.info,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        val airingSeason = sequel.airingSeason
        if (airingSeason != null) {
            InfoBlock(
                label = "Aired in",
                value = airingSeason,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val romaji = sequel.romajiTitle
        val english = sequel.englishTitle
        if (!romaji.isNullOrBlank() || !english.isNullOrBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!romaji.isNullOrBlank()) {
                    InfoBlock(
                        label = "Romaji Title",
                        value = romaji,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!english.isNullOrBlank()) {
                    InfoBlock(
                        label = "English Title",
                        value = english,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
