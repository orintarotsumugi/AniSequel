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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.theme.AniSequelTheme

/**
 * Community scores, average reception gauge, and popularity rating.
 * Employs Material 3 Expressive status colors, progress indicators, and typography.
 */
@Composable
fun AnimeRatingsSection(
    sequel: MissedSequel,
    modifier: Modifier = Modifier
) {
    val avgScore = sequel.sequelMedia.averageScore
    val meanScore = sequel.sequelMedia.meanScore
    val popularity = sequel.sequelMedia.popularity
    val statusColors = AniSequelTheme.statusColors

    if (avgScore == null && meanScore == null && popularity == null) return

    val scoreColor = when {
        avgScore != null && avgScore >= 80 -> statusColors.success
        avgScore != null && avgScore >= 70 -> MaterialTheme.colorScheme.primary
        avgScore != null -> statusColors.warning
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spec_ratings_section"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (avgScore != null) {
                    ScoreStatColumn(
                        label = "Average Score",
                        score = "$avgScore%",
                        color = scoreColor,
                        icon = AppVectorIcons.Star
                    )
                }

                if (meanScore != null) {
                    ScoreStatColumn(
                        label = "Mean Rating",
                        score = "$meanScore%",
                        color = MaterialTheme.colorScheme.secondary,
                        icon = AppVectorIcons.AnimeSparkle
                    )
                }

                if (popularity != null) {
                    ScoreStatColumn(
                        label = "Popularity",
                        score = "#$popularity",
                        color = MaterialTheme.colorScheme.tertiary,
                        icon = AppVectorIcons.Trending
                    )
                }
            }

            if (avgScore != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val fraction = (avgScore / 100f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = scoreColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun ScoreStatColumn(
    label: String,
    score: String,
    color: Color,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = score,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
