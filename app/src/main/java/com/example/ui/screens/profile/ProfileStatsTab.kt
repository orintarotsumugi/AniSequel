package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.usecase.BucketTally
import com.example.domain.usecase.ListEntryInsights
import com.example.domain.usecase.ListInsights
import com.example.domain.usecase.SegmentTally
import com.example.ui.theme.AniSequelTheme
import com.example.ui.components.AppVectorIcons
import com.example.ui.viewmodel.UserOverviewUiState

/**
 * The Stats tab.
 *
 * ## Two sources, one screen
 *
 * Status, format, score, genre and tag come from AniList's aggregate `User.stats`
 * block. Country, release year, watch year and episode bands do not exist there -
 * AniList publishes no aggregate for any of them - so they are tallied from the
 * media list the app already downloaded. Both halves are on one screen on purpose:
 * a person looking at "how much anime is Japanese" wants the answer next to "what
 * fraction is completed", not three tabs apart.
 *
 * ## Why the counts here and on the dashboard cannot disagree
 *
 * The status and format charts are AniList's own tallies. The dashboard's
 * "Completed" count is tallied from the list. They agree for every account below
 * AniList's 11,000-entry cap on `MediaListCollection`, and the cap is the only
 * case where they do not - so the header card says "from AniList" rather than
 * presenting both as the same number.
 */
@Composable
fun ProfileStatsTab(
    state: UserOverviewUiState.Success,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_stats_tab"),
        contentPadding = profileListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "summary") {
            SummaryCard(state = state)
        }

        item(key = "score") {
            ScoreDistributionCard(insights = state.insights)
        }

        item(key = "episodes") {
            BarChartCard(
                title = "Episode Count",
                icon = AppVectorIcons.StatEpisodeCount,
                rows = state.entryInsights.episodeBuckets,
                labelWidth = 48.dp,
                subtitle = episodeBucketSubtitle(state.entryInsights)
            )
        }

        item(key = "status") {
            StatusDistributionCard(distribution = state.insights.status)
        }

        item(key = "format") {
            FormatDistributionCard(insights = state.insights)
        }

        if (state.entryInsights.countries.isNotEmpty()) {
            item(key = "country") {
                BarChartCard(
                    title = "Country Distribution",
                    icon = AppVectorIcons.StatCountry,
                    rows = state.entryInsights.countries,
                    labelWidth = 92.dp,
                    subtitle = "Total entries: ${countryTotal(state.entryInsights)}"
                )
            }
        }

        if (state.insights.releaseYears.isNotEmpty()) {
            item(key = "release_year") {
                BarChartCard(
                    title = "Release Year",
                    icon = AppVectorIcons.StatReleaseYear,
                    rows = state.insights.releaseYears,
                    labelWidth = 48.dp,
                    subtitle = "Total entries: ${state.insights.releaseYears.sumOf { it.count }}",
                    // One hue, not the six-slot ramp: a year chart has too many rows
                    // for a rotating palette to mean anything, and rotating it makes
                    // neighbouring bars look like different categories.
                    barColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.entryInsights.watchYears.isNotEmpty()) {
            item(key = "watch_year") {
                BarChartCard(
                    title = "Watch Year",
                    icon = AppVectorIcons.StatWatchYear,
                    rows = state.entryInsights.watchYears,
                    labelWidth = 48.dp,
                    subtitle = "Finished ${state.entryInsights.watchYears.sumOf { it.count }} entries",
                    barColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.insights.genres.isNotEmpty()) {
            item(key = "genres") {
                BarChartCard(
                    title = "Genres",
                    icon = AppVectorIcons.StatGenre,
                    rows = state.insights.genres,
                    labelWidth = 92.dp,
                    trailing = "${state.insights.genres.size}",
                    subtitle = "By entries on your list"
                )
            }
        }

        if (state.insights.tags.isNotEmpty()) {
            item(key = "tags") {
                BarChartCard(
                    title = "Tags",
                    icon = AppVectorIcons.Tag,
                    rows = state.insights.tags,
                    labelWidth = 116.dp,
                    trailing = "${state.insights.tags.size}",
                    subtitle = "Your most frequent tags"
                )
            }
        }
    }
}

/**
 * The six numbers that summarise a list.
 *
 * Two rows of three rather than one column of six: the reference is meant to be
 * read as three pairs - how much, how long, how highly rated - and a single column
 * turns six short figures into a scroll of its own.
 *
 * "Days Watched" is the one that is not a count of anything and is derived:
 * [formatWatchTime] on the minutes AniList reports, because nobody has ever
 * wanted to know their total minutes and everybody compares hours.
 */
@Composable
private fun SummaryCard(
    state: UserOverviewUiState.Success,
    modifier: Modifier = Modifier
) {
    ChartCard(
        title = "Summary",
        icon = AppVectorIcons.StatSummary,
        modifier = modifier
    ) {
        SummaryRow(
            cells = listOf(
                SummaryCell(state.animeCount.toString(), "Total"),
                SummaryCell(state.episodesWatched.toString(), "Episodes\nWatched"),
                SummaryCell(formatWatchTime(state.minutesWatched), "Time\nWatched")
            )
        )
        Spacer(modifier = Modifier.height(18.dp))
        SummaryRow(
            cells = listOf(
                SummaryCell(
                    state.meanScore?.takeIf { it > 0.0 }?.let { formatMeanScore(it) } ?: "-",
                    "Mean Score"
                ),
                SummaryCell(
                    state.standardDeviation?.takeIf { it > 0 }?.let { "±$it" } ?: "-",
                    "Standard\nDeviation"
                ),
                SummaryCell(state.mangaCount.toString(), "Manga")
            )
        )
    }
}

private data class SummaryCell(val value: String, val label: String)

@Composable
private fun SummaryRow(cells: List<SummaryCell>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = cell.value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cell.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * The score chart, coloured by score band.
 *
 * The colour is the point of this chart and it is why [BarChartCard] takes a
 * `barColor` override and why the years charts use a single hue: 100 should be
 * the app's green, 50 its red, on *every* row of the chart, so that scanning it
 * shows which scores are high without reading a single number. A chart of one hue
 * would show only which scores are common, which is a different and much less
 * interesting question.
 */
@Composable
private fun ScoreDistributionCard(
    insights: ListInsights,
    modifier: Modifier = Modifier
) {
    ChartCard(
        title = "Score Distribution",
        icon = AppVectorIcons.StatScore,
        modifier = modifier,
        trailing = insights.scores.sumOf { it.count }.takeIf { it > 0 }?.toString()
    ) {
        if (insights.scores.isEmpty()) {
            Text(
                text = "Nothing scored on this list yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("chart_empty_note")
            )
            return@ChartCard
        }

        insights.scores.forEach { row ->
            ScoreRow(row = row, maxScore = insights.maxScore)
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Out of 100, as AniList records it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreRow(row: BucketTally, maxScore: Int) {
    val fraction = row.label.toIntOrNull()?.toFloat()?.div(maxScore.coerceAtLeast(100)) ?: 0f
    // Red for the bottom of the scale, amber in the middle, the app's green at the
    // top. Interpolated through the status colours the rest of the app uses, so
    // the "high score" green here is the same green as the success chip.
    val color = scoreColor(fraction)

    BarRow(row = row, labelWidth = 48.dp, color = color)
}

@Composable
private fun scoreColor(fraction: Float): Color {
    val status = AniSequelTheme.statusColors
    val scheme = MaterialTheme.colorScheme
    return when {
        fraction >= 0.85f -> status.success
        fraction >= 0.7f -> scheme.primary
        fraction >= 0.5f -> scheme.tertiary
        else -> status.warning
    }
}

/**
 * Format Distribution as a segmented bar, not a bar chart.
 *
 * Same reasoning as Status Distribution: the four formats of a list are shares of
 * one whole, and the comparison a person makes - "most of my list is TV" - is a
 * comparison between parts.
 */
@Composable
private fun FormatDistributionCard(
    insights: ListInsights,
    modifier: Modifier = Modifier
) {
    val total = insights.formats.sumOf { it.count }

    ChartCard(
        title = "Format Distribution",
        icon = AppVectorIcons.StatFormat,
        modifier = modifier,
        trailing = total.takeIf { it > 0 }?.toString()
    ) {
        if (insights.formats.isEmpty() || total <= 0) {
            Text(
                text = "No formats recorded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("chart_empty_note")
            )
            return@ChartCard
        }

        val segments = insights.formats.mapIndexed { index, bucket ->
            SegmentTally(
                label = bucket.label,
                count = bucket.count,
                share = bucket.count.toFloat() / total,
                slot = index
            )
        }

        SegmentStrip(segments = segments)
        Spacer(modifier = Modifier.height(12.dp))

        insights.formats.forEachIndexed { index, bucket ->
            FormatRow(
                label = bucket.label,
                count = bucket.count,
                share = bucket.count.toFloat() / total,
                color = chartSlotColor(index)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Total entries: $total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FormatRow(label: String, count: Int, share: Float, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "${(share * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(42.dp),
            textAlign = TextAlign.End
        )
    }
}

private fun episodeBucketSubtitle(insights: ListEntryInsights): String? {
    if (insights.entriesWithoutEpisodes <= 0) return null
    return "${insights.entriesWithoutEpisodes} entries have no episode count yet"
}

private fun countryTotal(insights: ListEntryInsights): Int = insights.countries.sumOf { it.count }