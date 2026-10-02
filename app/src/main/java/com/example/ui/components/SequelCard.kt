package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.MissedSequel
import com.example.ui.theme.AniSequelTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SequelCard(
    sequel: MissedSequel,
    onClick: () -> Unit,
    onAddToPlanning: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = AniSequelTheme.statusColors

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
            pressedElevation = 1.dp
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Which show you already finished led here.
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppVectorIcons.SequelArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        // "Sequel to", "Prequel to", "Side story of"... derived from
                        // AniList's own relation type so a prequel is never
                        // described as a sequel.
                        text = sequel.relationLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Poster(sequel = sequel)

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(142.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = sequel.sequelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            StatusChip(status = sequel.status)
                            InfoChip(
                                icon = AppVectorIcons.List,
                                label = sequel.episodes
                            )
                            if (sequel.score != "N/A") {
                                InfoChip(
                                    icon = AppVectorIcons.Star,
                                    label = sequel.score
                                )
                            }
                        }

                        // The one thing the card did not say before: for something
                        // mid-season, *when* the next episode lands.
                        val nextEpisode = sequel.nextEpisodeNumber
                        if (sequel.isAiring && nextEpisode != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = AppVectorIcons.Calendar,
                                    contentDescription = null,
                                    tint = status.success,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Next: Episode $nextEpisode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = status.success,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (sequel.genres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = sequel.genres.take(3).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    PlanningButton(
                        sequel = sequel,
                        onAddToPlanning = onAddToPlanning
                    )
                }
            }
        }
    }
}

@Composable
private fun Poster(sequel: MissedSequel) {
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(142.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (sequel.sequelCoverUrl != null) {
            AsyncImage(
                model = sequel.sequelCoverUrl,
                contentDescription = null, // the title is right next to it
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppVectorIcons.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(bottomEnd = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = sequel.format,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun PlanningButton(
    sequel: MissedSequel,
    onAddToPlanning: () -> Unit
) {
    // The button is the card's primary action, so its label doubles as the
    // card's action description for screen readers rather than being announced
    // as an unlabelled control inside a clickable card.
    val label = if (sequel.isAddedToPlanning) {
        "On Planning List"
    } else {
        "Add to Planning"
    }
    val statusColors = AniSequelTheme.statusColors

    AnimatedContent(
        targetState = sequel.isAddedToPlanning,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
        label = "planning_button_state"
    ) { isPlanned ->
        if (isPlanned) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clearAndSetSemantics { contentDescription = "Already on your Planning list" }
                    .testTag("planned_button_${sequel.sequelId}"),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    disabledContentColor = statusColors.success,
                    disabledContainerColor = statusColors.successContainer
                )
            ) {
                Icon(
                    imageVector = AppVectorIcons.BookmarkDone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = onAddToPlanning,
                enabled = !sequel.isAddingToPlanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("add_planning_button_${sequel.sequelId}"),
                shape = MaterialTheme.shapes.small
            ) {
                if (sequel.isAddingToPlanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = AppVectorIcons.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val statusColors = AniSequelTheme.statusColors
    val scheme = MaterialTheme.colorScheme

    val (bgColor, textColor, label) = when (status.uppercase()) {
        "RELEASING" -> Triple(
            statusColors.successContainer,
            statusColors.onSuccessContainer,
            "Airing"
        )
        "NOT_YET_RELEASED" -> Triple(
            statusColors.warningContainer,
            statusColors.onWarningContainer,
            "Upcoming"
        )
        "FINISHED" -> Triple(
            scheme.surfaceContainerHighest,
            scheme.onSurfaceVariant,
            "Finished"
        )
        "CANCELLED" -> Triple(
            scheme.errorContainer,
            scheme.onErrorContainer,
            "Cancelled"
        )
        else -> Triple(
            scheme.surfaceContainerHighest,
            scheme.onSurfaceVariant,
            status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        )
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(150),
        label = "chip_tint"
    )

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}