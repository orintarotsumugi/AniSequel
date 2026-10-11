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
import com.example.data.model.MediaNode
import com.example.data.model.airingCountdown
import com.example.data.model.airingEpisodeNumber
import com.example.data.model.durationLabel
import com.example.data.model.episodesLabel
import com.example.data.model.formatLabel
import com.example.data.model.isAiringNow
import com.example.data.model.isManga
import com.example.data.model.isOnHiatus
import com.example.data.model.isUpcoming
import com.example.data.model.releaseLabel
import com.example.data.model.seasonLabel
import com.example.data.model.sourceLabel
import com.example.data.model.statusLabel
import com.example.data.model.studioName
import com.example.data.model.watchFraction
import com.example.data.model.watchFractionLabel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.WavyProgressBar
import com.example.ui.theme.AniSequelTheme

/**
 * Specifications 2x2 / 2x3 grid showcasing anime details, status, studio,
 * source material, runtime duration, and airing schedule.
 */
@Composable
fun DetailSpecsGrid(
    media: MediaNode,
    isDetailLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColors = AniSequelTheme.statusColors
    val statusValue = when {
        media.isAiringNow -> "Currently Airing"
        media.isUpcoming -> "Upcoming"
        media.isOnHiatus -> "On Hiatus"
        media.statusLabel.equals("CANCELLED", ignoreCase = true) -> "Cancelled"
        else -> "Finished"
    }

    val statusColor = when {
        media.isAiringNow -> statusColors.success
        media.isUpcoming -> statusColors.info
        media.isOnHiatus -> statusColors.warning
        else -> null
    }

    val isManga = media.isManga
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Row 1: Status & Length / Chapters
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpecTile(
                icon = AppVectorIcons.CheckCircle,
                title = "Status",
                value = statusValue,
                valueColor = statusColor,
                modifier = Modifier.weight(1f)
            )
            SpecTile(
                icon = if (isManga) AppVectorIcons.SourceBook else AppVectorIcons.Tv,
                title = if (isManga) "Chapters & Volumes" else "Episodes & Length",
                value = if (isManga) {
                    val chaptersStr = media.chapters?.let { "$it chs" } ?: "Chapters TBA"
                    val volumesStr = media.volumes?.let { " · $it vols" }.orEmpty()
                    chaptersStr + volumesStr
                } else {
                    buildString {
                        append(media.episodesLabel)
                        media.durationLabel?.let { append(" · $it") }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Studio / Publisher & Source Material
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpecTile(
                icon = if (isManga) AppVectorIcons.FavouriteManga else AppVectorIcons.Studio,
                title = if (isManga) "Publisher / Serialization" else "Animation Studio",
                value = media.studioName ?: if (isDetailLoading) "Loading..." else if (isManga) "Manga Serialization" else "TBA",
                modifier = Modifier.weight(1f)
            )
            SpecTile(
                icon = AppVectorIcons.SourceBook,
                title = "Source Material",
                value = media.sourceLabel ?: if (isDetailLoading) "Loading..." else if (isManga) "Original" else "Original / Other",
                modifier = Modifier.weight(1f)
            )
        }

        // Row 3: Release info
        if (isManga) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpecTile(
                    icon = AppVectorIcons.Calendar,
                    title = "Release Year",
                    value = media.releaseLabel,
                    modifier = Modifier.weight(1f)
                )
                SpecTile(
                    icon = AppVectorIcons.Schedule,
                    title = "Format",
                    value = media.formatLabel,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            val nextEpisode = media.airingEpisodeNumber
            val countdown = media.airingCountdown()
            val airingSeason = media.seasonLabel

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
                            value = media.formatLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Watch Progress if in progress
        val progress = media.watchFraction()
        val progressLabel = media.watchFractionLabel()
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
