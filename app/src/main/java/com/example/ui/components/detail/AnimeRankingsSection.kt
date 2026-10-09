package com.example.ui.components.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MediaRanking
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveShapes

/**
 * Community & all-time AniList rankings section.
 * Renders prominent ranking badges with trophy iconography and expressive pill geometry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeRankingsSection(
    sequel: MissedSequel,
    modifier: Modifier = Modifier
) {
    val allRankings = sequel.rankings.filter { it.rank != null }
    val topRankFallback = sequel.topRanking

    if (allRankings.isEmpty() && topRankFallback == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spec_rankings_section"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (allRankings.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allRankings.take(4).forEach { ranking ->
                    RankingPillBadge(ranking = ranking)
                }
            }
        } else if (topRankFallback != null) {
            Surface(
                shape = ExpressiveShapes.pill,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
                        text = topRankFallback,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingPillBadge(
    ranking: MediaRanking,
    modifier: Modifier = Modifier
) {
    val rankText = "#${ranking.rank} ${ranking.context ?: "Ranked"}"
    val isTopTier = (ranking.rank ?: 100) <= 10

    Surface(
        shape = ExpressiveShapes.pill,
        color = if (isTopTier) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isTopTier) AppVectorIcons.Trophy else AppVectorIcons.AnimeSparkle,
                contentDescription = null,
                tint = if (isTopTier) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = rankText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isTopTier) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
