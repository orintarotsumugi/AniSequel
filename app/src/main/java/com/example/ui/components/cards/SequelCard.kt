package com.example.ui.components.cards

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.WavyProgressBar
import com.example.ui.theme.AniSequelTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SequelCard(
    sequel: MissedSequel,
    onClick: () -> Unit,
    onAddToPlanning: () -> Unit,
    /** Stops offering this entry again. Persisted by the ViewModel. */
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColors = AniSequelTheme.statusColors

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("sequel_card_${sequel.sequelId}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val relationColor = if (sequel.isEarlierInFranchise) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            }

            // Relation banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppVectorIcons.SequelJump,
                    contentDescription = null,
                    tint = relationColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = sequel.relationLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = relationColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = sequel.parentTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                SequelPoster(sequel = sequel)

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(152.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = sequel.sequelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

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
                                    imageVector = AppVectorIcons.Calendar,
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

                    // Stacked rather than side by side: the planning button already fills its slot,
                    // so a second sibling in this Row would have had to share the
                    // width with it and squeeze both.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SequelPlanningButton(
                            sequel = sequel,
                            onAddToPlanning = onAddToPlanning
                        )

                        // Secondary, and deliberately quiet: most people will
                        // never press this, and the AniList action is the one
                        // that belongs at a glance.
                        TextButton(
                            onClick = onHide,
                            modifier = Modifier.testTag("hide_sequel_${sequel.sequelId}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Not interested",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
