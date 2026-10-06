package com.example.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppVectorIcons
import com.example.ui.viewmodel.UserOverviewUiState

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
internal fun SummaryCard(
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
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cell.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
