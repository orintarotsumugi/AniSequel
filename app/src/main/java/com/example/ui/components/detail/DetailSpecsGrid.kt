package com.example.ui.components.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.WavyProgressBar
import com.example.ui.theme.AniSequelTheme

/**
 * Specifications 2x2 / 2x3 grid showcasing anime details, status, studio,
 * source material, runtime duration, and airing schedule.
 */
@Composable
fun DetailSpecsGrid(
    sequel: MissedSequel,
    isDetailLoading: Boolean,
    modifier: Modifier = Modifier
) {
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
        // Row 1: Status & Episodes
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpecTile(
                icon = AppVectorIcons.CheckCircle,
                title = "Status",
                value = statusValue,
                valueColor = statusColor,
                modifier = Modifier.weight(1f)
            )
            SpecTile(
                icon = AppVectorIcons.Tv,
                title = "Episodes & Length",
                value = buildString {
                    append(sequel.episodes)
                    sequel.duration?.let { append(" · $it") }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Studio & Source Material
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpecTile(
                icon = AppVectorIcons.Studio,
                title = "Animation Studio",
                value = sequel.studioName ?: if (isDetailLoading) "Loading..." else "TBA",
                modifier = Modifier.weight(1f)
            )
            SpecTile(
                icon = AppVectorIcons.SourceBook,
                title = "Source Material",
                value = sequel.source ?: if (isDetailLoading) "Loading..." else "Original / Other",
                modifier = Modifier.weight(1f)
            )
        }

        // Row 3: Airing Season & Next Episode (or Countdown)
        val nextEpisode = sequel.nextEpisodeNumber
        val countdown = sequel.nextAiringCountdown()
        val airingSeason = sequel.airingSeason

        if (airingSeason != null || nextEpisode != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (airingSeason != null) {
                    SpecTile(
                        icon = AppVectorIcons.Calendar,
                        title = "Season & Year",
                        value = airingSeason,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (nextEpisode != null) {
                    SpecTile(
                        icon = AppVectorIcons.Schedule,
                        title = "Next Episode",
                        value = if (countdown != null) "Ep $nextEpisode · $countdown" else "Episode $nextEpisode",
                        valueColor = statusColors.info,
                        modifier = Modifier.weight(1f)
                    )
                } else if (airingSeason == null) {
                    SpecTile(
                        icon = AppVectorIcons.Schedule,
                        title = "Format",
                        value = sequel.format,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Watch Progress if in progress
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
    }
}

@Composable
private fun SpecTile(
    icon: ImageVector,
    title: String,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
