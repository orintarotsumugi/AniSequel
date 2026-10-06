package com.example

import com.example.data.model.ActivityHistoryDay
import com.example.domain.usecase.BuildActivityCalendarUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Guards the profile screen's activity grid.
 *
 * The layout is the thing worth pinning, because a heatmap can look entirely
 * plausible while being laid out a week off, and the two mistakes below are the
 * ones a screenshot review does not catch:
 *
 *  - **Weekday rows are Sunday-first.** The gutter prints M, W and F. If the
 *    grid were built Monday-first those letters would land on Tuesday, Thursday
 *    and Saturday, and the whole grid would be shifted by one row.
 *  - **The last column is the week containing today.** Anchoring on "today's
 *    week" rather than on "today minus N weeks" is what makes the newest cell
 *    today; anchoring on a rolling day count makes the right edge a Sunday
 *    somewhere in the future and every grid looks a week stale.
 */
class BuildActivityCalendarUseCaseTest {

    private val useCase = BuildActivityCalendarUseCase()

    /** 2026-06-10 is a Wednesday, which makes the weekday arithmetic checkable. */
    private val today = LocalDate.of(2026, 6, 10)

    /** AniList's day timestamp: midnight UTC on [date]. */
    private fun day(
        date: LocalDate,
        amount: Int = 1,
        level: Int = 3
    ) = ActivityHistoryDay(
        date = date.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond().toInt(),
        amount = amount,
        level = level
    )

    @Test
    fun `the grid has one column per week and seven rows per column`() {
        val calendar = useCase(null, weeks = 12, today = today)

        assertEquals(12, calendar.columns.size)
        calendar.columns.forEach { assertEquals(7, it.days.size) }
    }

    @Test
    fun `the last column is the week containing today and today is in it`() {
        val calendar = useCase(null, weeks = 4, today = today)

        assertTrue(
            "today should appear in the final column",
            calendar.columns.last().days.map { it.epochDay }.contains(today.toEpochDay())
        )
        // Sunday-first: 2026-06-10 is a Wednesday, so the column starts three
        // days before it.
        assertEquals(
            LocalDate.of(2026, 6, 7).toEpochDay(),
            calendar.columns.last().days.first().epochDay
        )
    }

    @Test
    fun `each column starts on a Sunday`() {
        val calendar = useCase(null, weeks = 8, today = today)

        calendar.columns.forEach { column ->
            val date = LocalDate.ofEpochDay(column.days.first().epochDay)
            assertEquals(
                "column starting $date is not a Sunday",
                java.time.DayOfWeek.SUNDAY,
                date.dayOfWeek
            )
        }
    }

    @Test
    fun `the tail of the last column is the only part of the grid that is out of range`() {
        val calendar = useCase(null, weeks = 4, today = today)

        val outOfRange = calendar.columns.flatMapIndexed { index, column ->
            column.days.mapIndexedNotNull { row, day ->
                if (!day.inRange) "$index.$row" else null
            }
        }
        // The last column runs Sunday 2026-06-07 to Saturday 2026-06-13 and today
        // is the Wednesday of it, so rows 4, 5 and 6 are days that have not
        // happened.
        assertEquals(listOf("3.4", "3.5", "3.6"), outOfRange)
    }

    @Test
    fun `a day after today is neither counted nor allowed to colour the ramp`() {
        // Friday 2026-06-12. Not the Sunday after it: the last column of a
        // four-week grid ends on Saturday 2026-06-13, so the 14th is not in the
        // grid at all and looking for it throws rather than testing anything.
        val laterThisWeek = LocalDate.of(2026, 6, 12)
        val calendar = useCase(
            listOf(day(laterThisWeek, amount = 40, level = 7)),
            weeks = 4,
            today = today
        )

        val future = calendar.columns.last().days.first { it.epochDay == laterThisWeek.toEpochDay() }
        assertEquals(false, future.inRange)
        assertEquals(0, future.count)
        assertEquals(0f, future.intensity, 0.0001f)
        // A future-dated entry - a typo in someone's list, or a wrong device
        // clock - must not set the colour scale for every real cell.
        assertEquals(0, calendar.totalChanges)
    }

    @Test
    fun `AniList's level is normalised against seven rather than left raw`() {
        // Levels run 1 to 7. Left raw, a level-1 day and a level-7 day would both
        // be "full colour" to a clamp, and every active day would look identical.
        val calendar = useCase(
            listOf(
                day(LocalDate.of(2026, 6, 8), amount = 1, level = 1),
                day(LocalDate.of(2026, 6, 9), amount = 9, level = 7)
            ),
            weeks = 4,
            today = today
        )

        val low = calendar.columns.flatMap { it.days }
            .first { it.epochDay == LocalDate.of(2026, 6, 8).toEpochDay() }
        val high = calendar.columns.flatMap { it.days }
            .first { it.epochDay == LocalDate.of(2026, 6, 9).toEpochDay() }

        assertEquals(1f / 7f, low.intensity, 0.0001f)
        assertEquals(1f, high.intensity, 0.0001f)
    }

    @Test
    fun `the busiest day reaches the top of the ramp`() {
        val calendar = useCase(
            listOf(day(LocalDate.of(2026, 6, 8), amount = 11, level = 7)),
            weeks = 4,
            today = today
        )

        val busiest = calendar.columns.flatMap { it.days }
            .first { it.epochDay == LocalDate.of(2026, 6, 8).toEpochDay() }
        assertEquals(1f, busiest.intensity, 0.0001f)
    }

    @Test
    fun `two entries on the same day add up rather than overwriting`() {
        // `activityHistory` is daily server-side, so two buckets landing on one day
        // is not expected - but overwriting would silently lose activity, and the
        // whole claim of this card is that it loses none.
        val calendar = useCase(
            listOf(
                day(LocalDate.of(2026, 6, 8), amount = 2, level = 1),
                day(LocalDate.of(2026, 6, 8), amount = 3, level = 5)
            ),
            weeks = 4,
            today = today
        )

        val cell = calendar.columns.flatMap { it.days }
            .first { it.epochDay == LocalDate.of(2026, 6, 8).toEpochDay() }
        assertEquals(5, cell.count)
        // The stronger of the two wins, so a quiet and a busy entry on one day
        // still read as a busy day.
        assertEquals(5f / 7f, cell.intensity, 0.0001f)
        assertEquals(5, calendar.totalChanges)
    }

    @Test
    fun `a day with no activity is present and reads as nothing`() {
        val empty = useCase(emptyList(), weeks = 4, today = today)
            .columns.flatMap { it.days }
            .first { it.epochDay == LocalDate.of(2026, 6, 8).toEpochDay() }

        assertTrue(empty.inRange)
        assertEquals(0, empty.count)
        assertEquals(0f, empty.intensity, 0.0001f)
    }

    @Test
    fun `entries with no amount or no date are skipped rather than drawn as days`() {
        val calendar = useCase(
            listOf(
                ActivityHistoryDay(date = null, amount = 3, level = 3),
                ActivityHistoryDay(date = 1_600_000_000, amount = null, level = null),
                ActivityHistoryDay(date = 1_600_000_000, amount = 0, level = 0),
                day(LocalDate.of(2026, 6, 8), amount = 2, level = 3)
            ),
            weeks = 4,
            today = today
        )

        assertEquals(2, calendar.totalChanges)
    }

    @Test
    fun `a month label is emitted wherever the month changes between columns`() {
        val calendar = useCase(null, weeks = 9, today = today)

        // Nine columns from Sunday 2026-06-07 open on Sunday 2026-04-12, so the
        // columns run Apr, Apr, Apr, May, May, May, May, Jun, Jun - three changes,
        // at columns 0, 3 and 8.
        assertEquals(
            listOf("Apr" to 0, "May" to 3, "Jun" to 8),
            calendar.monthLabels.map { it.label to it.columnIndex }
        )
    }

    @Test
    fun `an empty history still produces a grid rather than nothing`() {
        val calendar = useCase(emptyList(), weeks = 6, today = today)

        assertEquals(6, calendar.columns.size)
        assertEquals(0, calendar.totalChanges)
        assertTrue(calendar.isEmpty)
    }

    @Test
    fun `the in-range days are exactly the days from the opening Sunday to today`() {
        val weeks = 10
        val calendar = useCase(null, weeks = weeks, today = today)

        assertEquals(weeks, calendar.weeksShown)
        val inRange = calendar.columns.flatMap { it.days }.count { it.inRange }
        // Ten whole weeks of columns, seven rows each, minus the tail of the last
        // one. Everything before the opening Sunday would be padding, and the
        // implementation deliberately draws none.
        assertEquals(weeks * 7 - 3, inRange)
        assertEquals(weeks * 7, calendar.columns.sumOf { it.days.size })
    }

    @Test
    fun `a zero week count still yields a drawable grid`() {
        // The UI passes a constant, but a use case that divides by its own window
        // length should not be one config change away from an empty screen.
        val calendar = useCase(null, weeks = 0, today = today)

        assertEquals(1, calendar.columns.size)
        assertEquals(7, calendar.columns.first().days.size)
    }
}