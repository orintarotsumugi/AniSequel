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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("sequel_card_${sequel.sequelId}"),
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 1.dp
        ),
        border = androidx.compose.material3.CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Which show you already finished led here.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
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

                        // An airing entry: when the next episode lands, not just
                        // that one exists. Previously this said only "Next:
                        // Episode 14" with no date attached, which is the one
                        // piece of information someone deciding whether to start
                        // a show mid-season actually wants.
                        val countdown = sequel.nextAiringCountdown()
                        if (sequel.isAiring && countdown != null) {
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
                                    text = sequel.nextEpisodeNumber?.let { "Next: Episode $it $countdown" }
                                        ?: "Airing $countdown",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = status.success,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Partly-watched entries say how far in they are. The app
                        // is for finding things you have *started and left*, and
                        // "6 / 12 eps" is the difference between resuming a show
                        // and restarting it.
                        val progressLabel = sequel.watchProgressLabel()
                        val progress = sequel.watchProgress()
                        if (progressLabel != null && progress != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WavyProgressBar(
                                    progress = { progress },
                                    color = status.info,
                                    modifier = Modifier
                                        .width(52.dp)
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

/**
 * The poster.
 *
 * Three things the card did not do before, each for a reason visible on screen:
 *
 *  - The placeholder is tinted with AniList's own `coverImage.color`. A grey
 *    box reads as a broken image; the right hue reads as loading.
 *  - The image is given an explicit `size`, so Coil can pick a source that
 *    matches 96dp instead of the `extraLarge` original.
 *  - The format badge sits at the top *trailing* edge, over the scrim, so it
 *    never collides with the title that wraps underneath it.
 */
@Composable
private fun Poster(sequel: MissedSequel) {
    // AniList's dominant colour for this entry, used to tint the placeholder.
    val coverColor = sequel.coverColor.toCoverColorOrNull()

    Box(
        modifier = Modifier
            .width(96.dp)
            .height(142.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        // Under the image, so art that has loaded covers it and art that has not
        // leaves the show's own colour showing rather than a grey rectangle.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    if (coverColor != null) {
                        Brush.verticalGradient(listOf(coverColor, coverColor.copy(alpha = 0.55f)))
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f)
            )
        }

        if (sequel.sequelCoverUrl != null) {
            AsyncImage(
                model = sequel.sequelCoverUrl,
                contentDescription = null, // the title is right next to it
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
        }

        // Scrim behind the badge only, so the badge stays readable on a bright
        // poster without darkening the artwork as a whole.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.5f)
                .height(28.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
        )

        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(bottomStart = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Text(
                text = sequel.format,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        // A score the viewer would weigh highly is worth seeing on the card.
        val score = sequel.sequelMedia.averageScore
        if (score != null && score >= 80) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(topStart = 8.dp),
                color = AniSequelTheme.statusColors.success,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Parses AniList's `#RRGGBB` cover colour.
 *
 * Returns null rather than a wrong colour on anything unexpected: a malformed
 * value must fall back to the theme surface, never to a colour that failed to
 * parse and was then treated as opaque black.
 */
private fun String?.toCoverColorOrNull(): Color? {
    val hex = this?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val value = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
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
            androidx.compose.material3.OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clearAndSetSemantics { contentDescription = "Already on your Planning list" }
                    .testTag("planned_button_${sequel.sequelId}"),
                shape = MaterialTheme.shapes.small,
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
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
            androidx.compose.material3.Button(
                onClick = onAddToPlanning,
                enabled = !sequel.isAddingToPlanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("add_planning_button_${sequel.sequelId}"),
                shape = MaterialTheme.shapes.small
            ) {
                if (sequel.isAddingToPlanning) {
                    ExpressiveContainedLoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                        indicatorColor = MaterialTheme.colorScheme.onPrimary
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

    androidx.compose.material3.Surface(
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

    androidx.compose.material3.Surface(
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