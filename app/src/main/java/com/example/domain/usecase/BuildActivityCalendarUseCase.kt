package com.example.domain.usecase

import com.example.data.model.ActivityHistoryDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One cell of the activity grid. Seven of these stack into an [ActivityColumn],
 * and a row of columns is the heatmap.
 */
data class ActivityDay(
    val epochDay: Long,
    /**
     * 0f to 1f, or 0f for a day with no activity.
     *
     * A fraction rather than AniList's own 1-to-7 [ActivityHistoryDay.level] so
     * the colour ramp does not have to know how many steps that scale has. The
     * division and its clamp live in [BuildActivityCalendarUseCase]; the UI only
     * ever sees 0f..1f.
     */
    val intensity: Float,
    /** How many list changes happened. Zero on a day that has not happened yet. */
    val count: Int,
    /**
     * Whether this day has already happened.
     *
     * False for the tail of the last column, past today. The grid's first column
     * starts on the Sunday that opens the window, so there are no gaps at the
     * front - but the last column is a whole week and today is somewhere inside
     * it, so the days after today are not days.
     *
     * They matter more than they look: an entry dated in the future - a typo in
     * someone's list, or a device whose clock is wrong - would otherwise paint a
     * cell on the right-hand edge with activity that has not happened.
     */
    val inRange: Boolean
)

data class ActivityColumn(val days: List<ActivityDay>)

/** A month name, and which column of the grid it starts at. */
data class ActivityMonthLabel(val label: String, val columnIndex: Int)

data class ActivityCalendar(
    val columns: List<ActivityColumn>,
    val monthLabels: List<ActivityMonthLabel>,
    /**
     * Monday, Wednesday and Friday, in Sunday-first row order 1, 3 and 5.
     *
     * Three of seven, not all seven: seven letters in a 13dp column overlap each
     * other, and the rows that were never labelled are the ones people read as "a
     * gap in the data" rather than as Sunday.
     */
    val weekdayLabels: List<String>,
    /** How many list changes the window covers in total. */
    val totalChanges: Int,
    val weeksShown: Int
) {
    val isEmpty: Boolean get() = totalChanges == 0
}

/**
 * Turns AniList's per-day activity history into a GitHub-shaped grid.
 *
 * ## Where the data comes from
 *
 * `User.stats.activityHistory`: one entry per day the person touched their list,
 * with a timestamp, a count and AniList's own intensity level from 1 to 7. That
 * is the same shape GitHub's contribution graph is built from, and it is *better*
 * than anything derivable from the list entries - bumping progress on a show you
 * are watching is activity and produces no `completedAt` anywhere, so a calendar
 * derived from completions shows an empty grid for somebody who watches daily and
 * has not finished anything in months.
 *
 * ## Why UTC
 *
 * AniList buckets these days by its own boundary, and the timestamp lands at
 * roughly 20:00-21:00 UTC. Converting with the device's zone would move a day's
 * cells around whenever the user travels, and would make the grid different on two
 * phones showing the same account. UTC keeps a given cell stable. The cost is that
 * near midnight the newest cell can be labelled a day behind on a device far from
 * Greenwich, which is a far smaller lie than a heatmap that reshuffles itself.
 */
class BuildActivityCalendarUseCase {

    operator fun invoke(
        history: List<ActivityHistoryDay>?,
        weeks: Int = DEFAULT_WEEKS,
        today: LocalDate = LocalDate.now()
    ): ActivityCalendar {
        val weekCount = weeks.coerceAtLeast(1)
        val byDay = tallyByDay(history)

        // Sunday-first indexing: DayOfWeek numbers Monday as 1 and Sunday as 7,
        // and the modulo turns Sunday into 0. The weekday gutter in
        // [ActivityCalendar.weekdayLabels] is written against the same rows, and
        // the two had to be decided together - a Monday-first grid with
        // Sunday-first labels puts "M" under Tuesday, which is the kind of wrong
        // that survives review.
        val lastColumnStart = today.minusDays((today.dayOfWeek.value % 7).toLong())
        val firstColumnStart = lastColumnStart.minusWeeks((weekCount - 1).toLong())

        val columns = (0 until weekCount).map { week ->
            val columnStart = firstColumnStart.plusWeeks(week.toLong())
            ActivityColumn(
                days = (0..6).map { row ->
                    val date = columnStart.plusDays(row.toLong())
                    val epochDay = date.toEpochDay()
                    val entry = byDay[epochDay]
                    // A day that has not happened cannot have activity, and one
                    // AniList recorded in the future is read but not trusted for
                    // intensity.
                    val inRange = !date.isAfter(today)
                    ActivityDay(
                        epochDay = epochDay,
                        intensity = if (inRange) {
                            entry?.intensity ?: 0f
                        } else {
                            0f
                        },
                        count = if (inRange) entry?.amount ?: 0 else 0,
                        inRange = inRange
                    )
                }
            )
        }

        return ActivityCalendar(
            columns = columns,
            monthLabels = monthLabels(columns.size, firstColumnStart),
            weekdayLabels = listOf("M", "W", "F"),
            // Summed from the drawn cells rather than from the whole history.
            //
            // The header says how much activity this grid covers, so it has to be
            // the same number the grid is showing: summing the raw history would
            // count a future-dated entry - a typo in someone's list, or a device
            // with the wrong clock - that no cell draws, and the card would read
            // "41 changes in 26 weeks" above a grid with nothing coloured in it.
            totalChanges = columns.asSequence()
                .flatMap { it.days.asSequence() }
                .filter { it.inRange }
                .sumOf { it.count },
            weeksShown = weekCount
        )
    }

    /**
     * Fold AniList's day buckets into one entry per calendar day.
     *
     * `activityHistory` is daily already, so this is normally a one-to-one map -
     * but two entries landing on the same day are *added* rather than one
     * overwriting the other, and the intensity takes the stronger of the two.
     * Overwriting would silently lose activity, and the calendar's whole claim is
     * that it is not losing any.
     */
    private fun tallyByDay(history: List<ActivityHistoryDay>?): Map<Long, FoldedDay> {
        val folded = HashMap<Long, FoldedDay>()
        history?.forEach { entry ->
            val date = entry.date?.let { toLocalDate(it) } ?: return@forEach
            val amount = entry.amount ?: 0
            if (amount <= 0 && (entry.level ?: 0) <= 0) return@forEach
            val epochDay = date.toEpochDay()
            val existing = folded[epochDay]
            folded[epochDay] = FoldedDay(
                amount = (existing?.amount ?: 0) + amount,
                intensity = maxOf(existing?.intensity ?: 0f, entry.level.toIntensity())
            )
        }
        return folded
    }

    private class FoldedDay(val amount: Int, val intensity: Float)

    /**
     * AniList's level as a 0f..1f fraction.
     *
     * Levels run 1 to 7 with 0 meaning "no activity", and the ramp has to end at
     * 1f or the busiest day on the grid never reaches the top of the colour
     * scale - which reads as "there is a higher level this app cannot show".
     */
    private fun Int?.toIntensity(): Float {
        val level = this ?: return 0f
        if (level <= 0) return 0f
        return (level.toFloat() / MAX_ANILIST_LEVEL).coerceIn(0f, 1f)
    }

    private fun toLocalDate(unixSeconds: Int): LocalDate? =
        runCatching { Instant.ofEpochSecond(unixSeconds.toLong()).atOffset(ZoneOffset.UTC).toLocalDate() }
            .getOrNull()

    /**
     * A label wherever the month changes between one column and the next.
     *
     * Deliberately not one label per month: a 26-week grid that begins mid-month
     * would otherwise print a month name in its first column and then again one
     * column later, because "this column's first day" and "this month's first
     * day" are different things.
     */
    private fun monthLabels(columnCount: Int, firstColumnStart: LocalDate): List<ActivityMonthLabel> {
        var previous: java.time.Month? = null
        return (0 until columnCount).mapNotNull { index ->
            val month = firstColumnStart.plusWeeks(index.toLong()).month
            if (month == previous) {
                null
            } else {
                previous = month
                ActivityMonthLabel(
                    label = month.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                    columnIndex = index
                )
            }
        }
    }

    companion object {
        /**
         * Half a year of columns.
         *
         * Long enough for a daily-watching habit to show a shape, short enough
         * that a 13dp cell and 3dp gutter still fit 26 columns inside a 640dp
         * content width with the weekday gutter beside them.
         */
        const val DEFAULT_WEEKS = 26

        /** The top of AniList's own intensity scale. See [toIntensity]. */
        private const val MAX_ANILIST_LEVEL = 7f
    }
}