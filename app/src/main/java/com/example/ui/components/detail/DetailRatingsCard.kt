package com.example.ui.components.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.theme.AniSequelTheme

/**
 * Community scores, popularity rank, top ranking award, and trailer action.
 */
@Composable
fun DetailRatingsCard(
    sequel: MissedSequel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statusColors = AniSequelTheme.statusColors
    val avgScore = sequel.sequelMedia.averageScore
    val meanScore = sequel.sequelMedia.meanScore
    val popularity = sequel.sequelMedia.popularity
    val topRank = sequel.topRanking
    val trailerUrl = sequel.trailerUrl

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Community Ratings Banner
        if (avgScore != null || meanScore != null || popularity != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (avgScore != null) {
                        ScoreColumn(
                            label = "Average Score",
                            score = "$avgScore%",
                            color = if (avgScore >= 80) statusColors.success else MaterialTheme.colorScheme.primary
                        )
                    }
                    if (meanScore != null) {
                        ScoreColumn(
                            label = "Mean Rating",
                            score = "$meanScore%",
                            color = if (meanScore >= 80) statusColors.success else MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (popularity != null) {
                        ScoreColumn(
                            label = "Popularity",
                            score = "#$popularity",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Top Ranking Badge from Provider
        if (!topRank.isNullOrBlank()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Trophy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = topRank,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Official Trailer Banner if available
        if (!trailerUrl.isNullOrBlank()) {
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = AppVectorIcons.Trailer,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Watch Official Trailer", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScoreColumn(
    label: String,
    score: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = score,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
