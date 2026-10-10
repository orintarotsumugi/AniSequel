package com.example.ui.screens.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.usecase.ActivityCalendar
import com.example.domain.usecase.ActivityMonthLabel
import com.example.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.flow.first

private val CellSize = 13.dp
private val CellGap = 3.dp
private val GutterWidth = 14.dp
private val GutterGap = 6.dp

/** One column's width, including the gutter that follows it. */
private val ColumnPitch = CellSize + CellGap

/**
 * The days-with-finished-things grid.
 *
 * Five intensity steps off the app's `primary` role rather than a green ramp
 * borrowed from somewhere else: AniSequel already has a status palette that is
 * readable in both themes and under Material You, and a heatmap is exactly the
 * place where a hardcoded colour ramp shows up as unreadable on a light theme
 * with a wallpaper-derived palette.
 *
 * The grid scrolls horizontally rather than compressing. 26 columns of 13dp plus
 * 3dp gutters is 410dp, which fits the app's 640dp content width with the
 * weekday gutter beside it - so it does not scroll at that width, but it does on
 * a small phone at a large font scale, and a heatmap that silently drops its
 * oldest weeks to fit is worse than one that scrolls.
 *
 * ## Why the gutter is inside the same Column as the cells
 *
 * Month labels, weekday letters and the cells all have to line up on the same
 * row boundaries, and three sibling rows aligned by hand is three chances to be
 * off by one - the weekday gutter would have to know the height of the month
 * label line to start its own rows level. Nesting them means the alignment is
 * the layout: the month line is one row above the cells row, and the gutter is
 * the cells row's first column.
 */
@Composable
fun ActivityCalendarCard(
    calendar: ActivityCalendar,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("activity_calendar_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Finished",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${calendar.totalChanges} changes in ${calendar.weeksShown} weeks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            CalendarGrid(calendar = calendar)

            Spacer(modifier = Modifier.height(14.dp))

            CalendarLegend()
        }
    }
}

@Composable
private fun CalendarGrid(calendar: ActivityCalendar) {
    val scrollState = rememberScrollState()

    // Opened at the newest column: the grid runs oldest to newest and the end is
    // the interesting part. Waiting for a non-zero `maxValue` rather than
    // scrolling to it directly, because a scroll to a zero maximum happens before
    // the grid has been measured and lands at the left of the oldest column.
    LaunchedEffect(calendar) {
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier.horizontalScroll(scrollState)) {
        MonthHeaderRow(
            labels = calendar.monthLabels,
            columnCount = calendar.columns.size
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row {
            WeekdayGutter(labels = calendar.weekdayLabels)
            Spacer(modifier = Modifier.width(GutterGap))
            ActivityCellsCanvas(calendar = calendar)
        }
    }
}

/**
 * Month names over the columns they start in.
 *
 * Each label is given every column up to the next label's start, so "Sep" spans
 * the four weeks it covers instead of being clipped into one 13dp cell - and the
 * spans sum to exactly the width of the cells row below, because the last one
 * runs to the end. That is what keeps a label from ending up over the wrong
 * month when one is short (February) and its neighbour long.
 */
@Composable
private fun MonthHeaderRow(
    labels: List<ActivityMonthLabel>,
    columnCount: Int
) {
    Row(modifier = Modifier.padding(start = GutterWidth + GutterGap)) {
        if (labels.isEmpty()) return@Row

        labels.forEachIndexed { index, label ->
            val nextColumn = labels.getOrNull(index + 1)?.columnIndex ?: columnCount
            val spanColumns = (nextColumn - label.columnIndex).coerceAtLeast(1)
            Text(
                text = label.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier.width(ColumnPitch * spanColumns - CellGap)
            )
        }
    }
}

/**
 * Monday, Wednesday and Friday beside their rows.
 *
 * Rows are Sunday-first, so the letters go at indices 1, 3 and 5. Rendering them
 * at indices 0, 1 and 2 - which is what drawing them from the label list
 * directly would do - puts "M" beside Tuesday, which a screenshot review does not
 * catch.
 */
@Composable
private fun WeekdayGutter(labels: List<String>) {
    val lettersByRow = mapOf(1 to "M", 3 to "W", 5 to "F")
    Column(
        modifier = Modifier.width(GutterWidth),
        verticalArrangement = Arrangement.spacedBy(CellGap)
    ) {
        repeat(DAYS_PER_WEEK) { row ->
            Text(
                text = lettersByRow[row] ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.height(CellSize)
            )
        }
    }
}

private const val DAYS_PER_WEEK = 7

@Composable
private fun ActivityCellsCanvas(calendar: ActivityCalendar) {
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val columnCount = calendar.columns.size
    val totalWidth = (ColumnPitch * columnCount - CellGap).coerceAtLeast(0.dp)
    val totalHeight = CellSize * DAYS_PER_WEEK + CellGap * (DAYS_PER_WEEK - 1)

    Canvas(
        modifier = Modifier
            .size(width = totalWidth, height = totalHeight)
            .testTag("activity_calendar_cells")
            .semantics {
                contentDescription = "${calendar.totalChanges} changes in ${calendar.weeksShown} weeks"
            }
    ) {
        val cellSizePx = CellSize.toPx()
        val cellGapPx = CellGap.toPx()
        val pitchPx = cellSizePx + cellGapPx
        val cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

        calendar.columns.forEachIndexed { colIdx, column ->
            val left = colIdx * pitchPx
            column.days.forEachIndexed { rowIdx, day ->
                val top = rowIdx * pitchPx
                val target = if (day.inRange) day.intensity.coerceIn(0f, 1f) else 0f
                val color = when {
                    target <= 0f -> empty
                    target >= 1f -> base
                    else -> base.copy(alpha = 0.22f + target * 0.78f)
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(cellSizePx, cellSizePx),
                    cornerRadius = cornerRadius
                )
            }
        }
    }
}

@Composable
private fun colorFor(fraction: Float): Color {
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    return when {
        fraction <= 0f -> empty
        fraction >= 1f -> base
        else -> base.copy(alpha = 0.22f + fraction * 0.78f)
    }
}

@Composable
private fun CalendarLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Less",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        LEGEND_STOPS.forEach { fraction ->
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(CellSize)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colorFor(fraction))
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = "More",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The five steps the legend shows, and the ramp the cells interpolate across.
 *
 * One declaration because a legend that disagrees with the ramp it explains is
 * worse than no legend: it says "darker means more" and then demonstrates
 * something else.
 */
private val LEGEND_STOPS = listOf(0f, 0.14f, 0.28f, 0.43f, 1f)