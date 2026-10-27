package com.anisequel.ui.components.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaRanking
import com.anisequel.data.model.rankingList
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.detail.info.InfoCard
import com.anisequel.ui.components.expressive.ExpressiveShapes

/**
 * Community & all-time AniList rankings, as pills.
 *
 * ## Why this is a card now
 *
 * It used to be a bare `Column` of pills dropped into a `cardItem` slot, which
 * padded and staggered it but drew no surface: the rankings appeared as loose
 * capsules floating on the page background, between two cards, with no heading.
 * Every other section on the page is a card with a title, so this one read as a
 * rendering accident rather than a section. The heading is also what gives
 * TalkBack a landmark - `InfoCard` marks its title as one.
 *
 * ## The dead branch that used to be here
 *
 * A `topRankFallback` branch drew a single full-width pill from
 * `MediaNode.topRankingLabel` when no ranking had a rank. That branch could never
 * run: `topRankingLabel` is itself "the first ranking whose rank is not null", so
 * the only way it was non-null was with a rank present, which the first branch
 * had already taken. It was deleted rather than kept as a fallback for a case
 * that cannot exist.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeRankingsSection(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    // A ranking with no rank has nothing to print, and `hasRankings` on the page
    // applies the same filter before adding this item.
    val allRankings = media.rankingList.filter { it.rank != null }
    if (allRankings.isEmpty()) return

    InfoCard(
        modifier = modifier.testTag("info_rankings_card"),
        title = "Rankings",
        icon = AppVectorIcons.Trophy
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allRankings.take(4).forEach { ranking ->
                RankingPillBadge(ranking = ranking)
            }
        }
    }
}

@Composable
private fun RankingPillBadge(
    ranking: MediaRanking,
    modifier: Modifier = Modifier
) {
    // A blank `context` used to print "#12 " with a trailing space and nothing
    // after it. "Ranked" is what AniList's own page says for a ranking with no
    // context.
    val context = ranking.context?.takeIf { it.isNotBlank() } ?: "Ranked"
    val rankText = "#${ranking.rank} $context"
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
