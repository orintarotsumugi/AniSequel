package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaScoreAmount
import com.anisequel.data.model.compactCount
import com.anisequel.data.model.displayName
import com.anisequel.data.model.isManga
import com.anisequel.data.model.rankingList
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.theme.AniSequelTheme
import java.util.Locale

/**
 * The drawer behind one number of the summary strip.
 *
 * Split from `NovaInfoCards.kt` when that file passed the size ceiling: the strip is
 * the page's *summary* - four numbers and a countdown, read in one glance - and this
 * is what a tap on one of them opens, which is a different surface with a different
 * job. The strip draws a number; this explains it, with the distribution behind the
 * score, the ranking of the popularity, the favourites, or the length of an episode.
 *
 * Everything here is drawn inside a `ModalBottomSheet` the strip owns, which is why
 * the file has no sheet of its own and no state to keep: what the reader sees is
 * entirely a function of the number they tapped.
 */

@Composable
internal fun NovaStatDetails(media: MediaNode, selectedLabel: String) {
    // Derived once per stats payload rather than once per pass. The drawer is a
    // scrolling `Column`, so opening it, dragging the sheet or a countdown above it
    // re-running would otherwise re-filter, re-sort and re-sum four lists on every
    // recomposition - for data that cannot have changed.
    val stats = media.stats
    val scoreRows = remember(stats) {
        stats?.scoreDistribution.orEmpty()
            .filter { (it.score ?: 0) > 0 && (it.amount ?: 0) > 0 }
            .sortedBy { it.score }
    }
    val statusRows = remember(stats) {
        stats?.statusDistribution.orEmpty()
            .filter { !it.status.isNullOrBlank() && (it.amount ?: 0) > 0 }
            .sortedByDescending { it.amount }
    }
    val totalRatings = remember(scoreRows) { scoreRows.sumOf { it.amount ?: 0 } }
    val maxScoreCount = remember(scoreRows) { scoreRows.maxOfOrNull { it.amount ?: 0 } ?: 0 }
    val totalStatuses = remember(statusRows) { statusRows.sumOf { it.amount ?: 0 } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                selectedStatTitle(selectedLabel),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                media.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        when (selectedLabel) {
            "Score" -> NovaScoreDetails(
                media = media,
                scoreRows = scoreRows,
                totalRatings = totalRatings,
                maxScoreCount = maxScoreCount
            )
            "Popularity" -> NovaPopularityDetails(media)
            "Favourites" -> NovaFavouritesDetails(media)
            else -> NovaLengthDetails(media)
        }

        if (statusRows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "List status breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        compactCount(totalStatuses),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                statusRows.forEachIndexed { index, row ->
                    val amount = row.amount ?: 0
                    DistributionBarRow(
                        // Enum labels are protocol text. Folding them with the
                        // device locale turned `HIATUS` into `Hıatus` on a
                        // Turkish device, which is visible in the statistics card.
                        label = row.status.orEmpty().replace('_', ' ').lowercase(Locale.ROOT)
                            .replaceFirstChar { it.uppercase(Locale.ROOT) },
                        value = compactCount(amount),
                        fraction = if (totalStatuses > 0) amount.toFloat() / totalStatuses else 0f,
                        accent = chartAccent(index)
                    )
                }
            }
        }

        media.description?.takeIf { it.isNotBlank() }?.let { synopsis ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Synopsis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        synopsis.replace(Regex("<[^>]*>"), "").replace("&mdash;", "—").replace("&quot;", "\""),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}


@Composable
private fun NovaScoreDetails(
    media: MediaNode,
    scoreRows: List<MediaScoreAmount>,
    totalRatings: Int,
    maxScoreCount: Int
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(AppVectorIcons.Star, contentDescription = null, modifier = Modifier.size(30.dp))
            Column {
                Text(
                    media.averageScore?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f", it / 10f) } ?: "—",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("Average score · out of 10", style = MaterialTheme.typography.labelLarge)
                if (totalRatings > 0) Text("${compactCount(totalRatings)} ratings", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text("Score distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (scoreRows.isEmpty()) {
        EmptyStatMessage("AniList has not supplied a score distribution for this title.")
    } else {
        scoreRows.forEach { row ->
            val amount = row.amount ?: 0
            DistributionBarRow(
                label = String.format(Locale.US, "%.1f", (row.score ?: 0) / 10f),
                value = compactCount(amount),
                fraction = if (maxScoreCount > 0) amount.toFloat() / maxScoreCount else 0f,
                accent = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NovaPopularityDetails(media: MediaNode) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("POPULARITY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                media.popularity?.takeIf { it > 0 }?.let(::compactCount) ?: "—",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text("AniList popularity count", style = MaterialTheme.typography.bodySmall)
        }
    }
    Text("Catalogue rankings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (media.rankingList.isEmpty()) {
        EmptyStatMessage("No catalogue ranking is available for this title.")
    } else {
        media.rankingList.forEach { ranking ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        ranking.rank?.let { "#$it" } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(ranking.context ?: "Catalogue ranking", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NovaFavouritesDetails(media: MediaNode) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(AppVectorIcons.ActivityLiked, contentDescription = null, modifier = Modifier.size(30.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    media.favourites?.takeIf { it > 0 }?.let(::compactCount) ?: "—",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("AniList favourites", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    EmptyStatMessage(
        "This is the total number of AniList favourites. AniList does not provide a public list of every account that favourited this title."
    )
}

@Composable
private fun NovaLengthDetails(media: MediaNode) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (media.isManga) "CHAPTERS" else "EPISODE LENGTH", style = MaterialTheme.typography.labelMedium)
            Text(
                if (media.isManga) media.chapters?.takeIf { it > 0 }?.toString() ?: "—"
                else media.duration?.let { "$it min" } ?: "—",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (media.isManga) "Published chapters" else "Average runtime per episode",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DistributionBarRow(
    label: String,
    value: String,
    fraction: Float,
    accent: Color
) {
    // The State object, not the delegated value. The fill is drawn below, so a
    // frame of the spring invalidates this row's draw pass and nothing else. The
    // row used to read the value in composition and paint it with
    // `fillMaxWidth(fraction)`, which recomposed, re-measured and re-laid-out
    // every bar in the drawer on every frame of every spring - a statistics
    // sheet animating up to a dozen bars at once is exactly where that shows.
    val animatedFraction = animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "nova_stat_distribution_bar"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val filled = size.width * animatedFraction.value
                        if (filled > 0f) {
                            drawRect(color = accent, size = Size(filled, size.height))
                        }
                    }
            )
        }
    }
}

@Composable
private fun EmptyStatMessage(message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The bar for one status row: a series colour from the scheme, not a pastel.
 *
 * These were five fixed hex values - a blue, a green, an amber, a violet and a
 * rose - picked on the dark theme and never re-checked. On the light theme's
 * `surfaceContainerHighest` they measure 2.16, 2.20, 1.88, 2.18 and 2.35:1: every
 * one under the 3:1 a graphical object owes, and the amber under 2:1, so the
 * longest bar in the card was the faintest. They were also the same five colours
 * in both themes, which is the giveaway that they were never derived from
 * anything.
 *
 * [com.anisequel.ui.theme.EmphasisColors.charts] is the same idea done from the
 * scheme: primary, secondary, tertiary and error, each strengthened until it
 * clears 3:1 against the worst surface the app draws on, so a palette change
 * carries the chart with it.
 */
@Composable
private fun chartAccent(index: Int): Color {
    val charts = AniSequelTheme.emphasis.charts
    if (charts.isEmpty()) return MaterialTheme.colorScheme.primary
    return charts[index % charts.size]
}

private fun selectedStatTitle(label: String): String = when (label) {
    "Duration" -> "Episode details"
    "Chapters" -> "Reading details"
    else -> label
}