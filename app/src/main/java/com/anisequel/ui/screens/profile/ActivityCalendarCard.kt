package com.anisequel.ui.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.domain.usecase.ActivityCalendar
import com.anisequel.domain.usecase.ActivityDay
import com.anisequel.domain.usecase.ActivityMonthLabel
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

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
        // Which reading of the same data the card shows. Months is the shape of
        // the habit; Week is "how much this week", which the 13dp grid answers
        // only by counting coloured squares. Kept in `rememberSaveable` so a
        // rotation - or a tab switch, now that the tab's state is preserved -
        // does not hand the reader the other view back.
        var showWeek by rememberSaveable { mutableStateOf(false) }
        // The day whose count is spelled out under the grid. Kept as an epoch
        // day rather than an ActivityDay so a refresh that rebuilds the calendar
        // cannot leave a selected cell pointing at the previous list's object.
        var selectedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
        val selectedDay = selectedEpochDay?.let { epochDay ->
            calendar.columns.asSequence()
                .flatMap { it.days.asSequence() }
                .firstOrNull { it.epochDay == epochDay }
        }
        val week = calendar.columns.lastOrNull()?.days.orEmpty()
        val weekChanges = week.filter { it.inRange }.sumOf { it.count }

        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Finished",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    // The week reading says "this week", not "in 26 weeks":
                    // the number beside it is the week's total, and a summary
                    // that kept the old count would disagree with the bars.
                    text = if (showWeek) {
                        "${formatActivityChanges(weekChanges)} this week"
                    } else {
                        "${formatActivityChanges(calendar.totalChanges)} in " +
                            formatWeeks(calendar.weeksShown)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // The two ranges share a card, so a switch between them used to be a
            // hard swap: the grid vanished, seven rows appeared, and the eye lost
            // the place it had been looking at. Cross-fading with a small scale
            // keeps the card's size the thing that stays put, and springs rather
            // than a tween because everything else in this app settles on the
            // shared tokens.
            AnimatedContent(
                targetState = showWeek,
                transitionSpec = {
                    // Fades on `FastEffects`, the no-bouncy token, and only the scales
                    // on `FastSpatial`: an alpha is not a position, so a spring that
                    // overshoots it is invisible at best and a flicker at worst - the
                    // rule `PageTransitions` states for every other cross-fade.
                    fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                        scaleIn(
                            animationSpec = ExpressiveMotion.FastSpatial,
                            initialScale = 0.96f
                        ) togetherWith fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                        scaleOut(
                            animationSpec = ExpressiveMotion.FastSpatial,
                            targetScale = 0.96f
                        )
                },
                label = "calendar_range"
            ) { weekView ->
                if (weekView) {
                    WeekBreakdown(week = week)
                } else {
                    CalendarGrid(
                        calendar = calendar,
                        selectedEpochDay = selectedEpochDay,
                        onSelectDay = { selectedEpochDay = it }
                    )
                }
            }

            if (!showWeek) {
                Spacer(modifier = Modifier.height(10.dp))
                ActivityDaySummary(day = selectedDay)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CalendarLegend()
                Spacer(modifier = Modifier.weight(1f))
                CalendarRangeToggle(showWeek = showWeek, onChange = { showWeek = it })
            }
        }
    }
}

/**
 * The current Sunday-to-Saturday week, one row per day.
 *
 * The same data the grid draws, read at the scale where "three changes" and
 * "eleven changes" are visibly different lengths rather than two shades of the
 * same colour. The last column of the calendar is always the current week - see
 * `BuildActivityCalendarUseCase` - so this needs no second dataset and no second
 * request.
 *
 * Days after today are drawn as an empty track with an em dash: AniList's stats
 * also lag ~48 hours, so the last two days can legitimately read zero, and a
 * blank right-hand side is the honest picture of a week that is still happening.
 */
@Composable
private fun WeekBreakdown(week: List<ActivityDay>) {
    val maxCount = week.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    // The configuration's locale, not `Locale.getDefault()`: the latter is a plain
    // static read, so a reader who changes the app's language while it is open kept
    // last week's language in this card until something else forced a
    // recomposition. `LocalConfiguration` is the observable one, and it is what
    // recomposes this row when the locale changes.
    val locale = LocalConfiguration.current.locales[0]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_calendar_week"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        week.forEach { day ->
            val label = LocalDate.ofEpochDay(day.epochDay).dayOfWeek
                .getDisplayName(TextStyle.SHORT, locale)
            Row(
                modifier = Modifier.semantics {
                    // The bar is a length and a shade of one colour, and a screen
                    // reader got neither - the row was three bare strings. The
                    // sentence is the day and its count.
                    contentDescription = "$label: " +
                        if (day.inRange) formatActivityChanges(day.count) else "not yet"
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(34.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (day.inRange && day.count > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                // A floor of 12%: one change beside a fifteen-change
                                // day would otherwise be a sliver indistinguishable
                                // from an empty track.
                                .fillMaxWidth((day.count.toFloat() / maxCount).coerceIn(0.12f, 1f))
                                .clip(RoundedCornerShape(percent = 50))
                                .background(legendColourFor(day.intensity))
                        )
                    }
                }
                Text(
                    text = if (day.inRange) day.count.toString() else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.inRange) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(32.dp)
                )
            }
        }
    }
}

/**
 * Months or Week, as two filter chips.
 *
 * A toggle rather than a third chart section: both readings describe the same
 * seven-day columns, and the card is small enough that making the reader leave
 * it to see the other scale would hide the useful half.
 */
@Composable
private fun CalendarRangeToggle(showWeek: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = !showWeek,
            onClick = { onChange(false) },
            label = { Text("Months", style = MaterialTheme.typography.labelSmall) }
        )
        FilterChip(
            selected = showWeek,
            onClick = { onChange(true) },
            label = { Text("Week", style = MaterialTheme.typography.labelSmall) }
        )
    }
}

@Composable
private fun CalendarGrid(
    calendar: ActivityCalendar,
    selectedEpochDay: Long?,
    onSelectDay: (Long?) -> Unit
) {
    val scrollState = rememberScrollState()
    // One placement per composition of this card, not one per calendar object.
    //
    // The effect used to be keyed on `calendar`, and the Activity tab rebuilds that
    // object whenever the feed changes - so every refresh yanked a reader who had
    // scrolled back through the year to the newest column. Saveable so a rotation
    // keeps the place too, and set only once the scroll has actually landed.
    var openedAtNewest by rememberSaveable { mutableStateOf(false) }

    // Opened at the newest column: the grid runs oldest to newest and the end is
    // the interesting part. Waiting for a non-zero `maxValue` rather than
    // scrolling to it directly, because a scroll to a zero maximum happens before
    // the grid has been measured and lands at the left of the oldest column.
    LaunchedEffect(calendar.columns.size) {
        if (openedAtNewest || calendar.columns.isEmpty()) return@LaunchedEffect
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        scrollState.scrollTo(scrollState.maxValue)
        openedAtNewest = true
    }

    Column(modifier = Modifier.horizontalScroll(scrollState)) {
        MonthHeaderRow(
            labels = calendar.monthLabels,
            columnCount = calendar.columns.size
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row {
            WeekdayGutter()
            Spacer(modifier = Modifier.width(GutterGap))
            ActivityCellsCanvas(
                calendar = calendar,
                selectedEpochDay = selectedEpochDay,
                onSelectDay = onSelectDay
            )
        }
    }
}

/**
 * Month names over the columns they start in.
 *
 * Each label is given every column up to the next label's start, so "Sep" spans
 * the four weeks it covers instead of being clipped into one 13dp cell - and the
 * spans plus their gutters sum to exactly the width of the cells row below,
 * because the last one runs to the end. That is what keeps a label from ending up
 * over the wrong month when one is short (February) and its neighbour long.
 *
 * The `spacedBy(CellGap)` is load-bearing: a label's width stops one gutter
 * short of the next column's start, so the *gap* is what puts each label on its
 * column's left edge. Without it every label after the first sat three dp to the
 * left of its month and the error accumulated across the grid.
 */
@Composable
private fun MonthHeaderRow(
    labels: List<ActivityMonthLabel>,
    columnCount: Int
) {
    Row(
        modifier = Modifier.padding(start = GutterWidth + GutterGap),
        horizontalArrangement = Arrangement.spacedBy(CellGap)
    ) {
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
private fun WeekdayGutter() {
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
private fun ActivityCellsCanvas(
    calendar: ActivityCalendar,
    selectedEpochDay: Long?,
    onSelectDay: (Long?) -> Unit
) {
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val selectedOutline = MaterialTheme.colorScheme.onSurface
    val columnCount = calendar.columns.size
    val totalWidth = (ColumnPitch * columnCount - CellGap).coerceAtLeast(0.dp)
    val totalHeight = CellSize * DAYS_PER_WEEK + CellGap * (DAYS_PER_WEEK - 1)

    Canvas(
        modifier = Modifier
            .size(width = totalWidth, height = totalHeight)
            .testTag("activity_calendar_cells")
            // Keyed on the selection as well as the calendar: the handler reads it
            // to decide whether this tap selects or clears, and a lambda captured
            // once would keep answering with the selection it was built with.
            .pointerInput(calendar, selectedEpochDay) {
                val pitchPx = (CellSize + CellGap).toPx()
                val cellPx = CellSize.toPx()
                detectTapGestures { offset ->
                    val column = (offset.x / pitchPx).toInt()
                    val row = (offset.y / pitchPx).toInt()
                    // A tap in the 3dp gutter between two cells used to select the
                    // cell after it, so aiming at a gap answered about a day the
                    // reader did not touch. The gutter is not a target.
                    if (offset.x - column * pitchPx > cellPx) return@detectTapGestures
                    if (offset.y - row * pitchPx > cellPx) return@detectTapGestures
                    val day = calendar.columns.getOrNull(column)?.days?.getOrNull(row)
                    if (day?.inRange == true) {
                        // Tapping the selected day again clears it: the summary row
                        // otherwise had no way back to the invitation it opens as.
                        onSelectDay(day.epochDay.takeIf { it != selectedEpochDay })
                    }
                }
            }
            .semantics {
                contentDescription = "${formatActivityChanges(calendar.totalChanges)} in " +
                    "${formatWeeks(calendar.weeksShown)}. Tap a day to see its count."
            }
    ) {
        val cellSizePx = CellSize.toPx()
        val cellGapPx = CellGap.toPx()
        val pitchPx = cellSizePx + cellGapPx
        val cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        val selectedStroke = Stroke(width = 2.dp.toPx())

        calendar.columns.forEachIndexed { colIdx, column ->
            val left = colIdx * pitchPx
            column.days.forEachIndexed { rowIdx, day ->
                val top = rowIdx * pitchPx
                val target = if (day.inRange) day.intensity.coerceIn(0f, 1f) else 0f
                drawRoundRect(
                    color = heatColour(target, base = base, empty = empty),
                    topLeft = Offset(left, top),
                    size = Size(cellSizePx, cellSizePx),
                    cornerRadius = cornerRadius
                )
                if (day.epochDay == selectedEpochDay) {
                    drawRoundRect(
                        color = selectedOutline,
                        topLeft = Offset(left, top),
                        size = Size(cellSizePx, cellSizePx),
                        cornerRadius = cornerRadius,
                        style = selectedStroke
                    )
                }
            }
        }
    }
}

/**
 * The day a tap selected, or the invitation to tap one.
 *
 * The grid answers "how much" with colour; a cell is 13dp and two shades of the
 * same accent are not a number. This row is the exact count, and it is part of
 * the card rather than a tooltip because a tooltip would disappear before the
 * reader could read it on a dense grid.
 */
@Composable
private fun ActivityDaySummary(day: ActivityDay?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("activity_calendar_selected_day"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (day == null) {
            Text(
                text = "Tap a day to see how many changes it had",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // The same observable locale `WeekBreakdown` uses; see the note there.
            val locale = LocalConfiguration.current.locales[0]
            Text(
                text = LocalDate.ofEpochDay(day.epochDay)
                    .format(DateTimeFormatter.ofPattern("EEE, MMM d", locale)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatActivityChanges(day.count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

internal fun formatActivityChanges(count: Int): String = when (count) {
    0 -> "No changes"
    1 -> "1 change"
    else -> "$count changes"
}

internal fun formatWeeks(count: Int): String = if (count == 1) "1 week" else "$count weeks"

/**
 * The heat ramp, in one place.
 *
 * The legend used to interpolate its own copy of this, so the swatches under the
 * grid could drift from the cells above it without either looking wrong in
 * isolation - a legend that explains a ramp it does not draw is worse than no
 * legend. `0.22 + 0.78 * fraction` is here rather than at two call sites for the
 * same reason.
 */
private fun heatColour(fraction: Float, base: Color, empty: Color): Color = when {
    fraction <= 0f -> empty
    fraction >= 1f -> base
    else -> base.copy(alpha = 0.22f + fraction * 0.78f)
}

@Composable
private fun legendColourFor(fraction: Float): Color = heatColour(
    fraction = fraction,
    base = MaterialTheme.colorScheme.primary,
    empty = MaterialTheme.colorScheme.surfaceContainerHighest
)

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
                    .background(legendColourFor(fraction))
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
