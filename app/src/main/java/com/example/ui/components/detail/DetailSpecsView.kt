package com.example.ui.components.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.theme.AniSequelTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailSpecsView(
    sequel: MissedSequel,
    isDetailLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statusColors = AniSequelTheme.statusColors

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Official Trailer Banner if available
        val trailerUrl = sequel.trailerUrl
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

        // Top Ranking Badge from Provider
        val topRank = sequel.topRanking
        if (topRank != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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

        // Specs 2x2 Grid
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpecCard(
                icon = AppVectorIcons.Studio,
                title = "Studio",
                value = sequel.studioName ?: if (isDetailLoading) "Loading..." else "TBA",
                modifier = Modifier.weight(1f)
            )
            SpecCard(
                icon = AppVectorIcons.SourceBook,
                title = "Source Material",
                value = sequel.source ?: if (isDetailLoading) "Loading..." else "Original / Other",
                modifier = Modifier.weight(1f)
            )
        }

        val romaji = sequel.romajiTitle
        val english = sequel.englishTitle
        if (!romaji.isNullOrBlank() || !english.isNullOrBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!romaji.isNullOrBlank()) {
                    SpecCard(
                        icon = AppVectorIcons.SourceBook,
                        title = "Romaji Title",
                        value = romaji,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!english.isNullOrBlank()) {
                    SpecCard(
                        icon = AppVectorIcons.Tv,
                        title = "English Title",
                        value = english,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpecCard(
                icon = AppVectorIcons.Schedule,
                title = "Duration",
                value = sequel.duration ?: if (isDetailLoading) "Loading..." else "Standard",
                modifier = Modifier.weight(1f)
            )
            SpecCard(
                icon = AppVectorIcons.Calendar,
                title = "Season & Year",
                value = sequel.airingSeason ?: "TBA",
                modifier = Modifier.weight(1f)
            )
        }

        // Community Ratings Banner
        val avgScore = sequel.sequelMedia.averageScore
        val meanScore = sequel.sequelMedia.meanScore
        if (avgScore != null || meanScore != null) {
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
                    sequel.sequelMedia.popularity?.let { popularity ->
                        ScoreColumn(
                            label = "Popularity",
                            score = "#$popularity",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Thematic Tags from Provider
        val tags = sequel.topTags
        if (tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppVectorIcons.Tag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Thematic Tags",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tags.forEach { tag ->
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Synonyms / Alternative Names
        if (sequel.synonyms.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Alternative Titles",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sequel.synonyms.take(5).forEach { synonym ->
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = synonym,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecCard(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
