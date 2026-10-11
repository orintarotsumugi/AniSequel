package com.example.domain.usecase

import com.example.data.model.MediaListCollection
import com.example.data.model.MediaListEntryItem

/**
 * The Stats tab's three charts that AniList's aggregate statistics do not cover.
 *
 * `User.stats` answers status, format, score, genre, tag and year - and has
 * nothing at all for country, episode count or the year a thing was *watched*
 * rather than released. Those three come from the media list the app already
 * downloads whole for the sequel walk, which is why they cost no request.
 *
 * ## Why this is a use case and not a `groupBy` in the ViewModel
 *
 * Because the bucket boundaries are decisions. "7-16 episodes" is not a fact
 * about AniList; it is a choice about which shows a 12-episode cour counts as,
 * and it is the kind of choice that has to be written down, argued about and
 * pinned by a test rather than buried in a `when` next to a coroutine.
 */
data class ListEntryInsights(
    val countries: List<BucketTally>,
    val releaseYears: List<BucketTally>,
    val watchYears: List<BucketTally>,
    val episodeBuckets: List<BucketTally>,
    /** Entries with no episode count at all, which land in no bucket. */
    val entriesWithoutEpisodes: Int,
    val entriesCounted: Int
)

/**
 * Country, release year, watch year and episode-count distribution for a list.
 *
 * Only complete entries are counted. `MediaListCollection` also returns entries
 * with no status - the ones a user explicitly removed from every status list -
 * and counting those would add rows for things nobody has chosen to track. The
 * status distributions come from AniList's own aggregate, which excludes them too,
 * so the two halves of the Stats tab add up to the same list.
 */
class BuildListEntryInsightsUseCase {

    operator fun invoke(collection: MediaListCollection?): ListEntryInsights {
        val entries = collection?.lists
            ?.flatMap { it.entries.orEmpty() }
            .orEmpty()
            .filter { it.status != null && it.media != null }

        val countries = buckets(
            entries.mapNotNull { entry ->
                entry.media.countryOfOrigin?.let { BucketTally(it.toCountryLabel(), 1, 0f) }
            },
            countBy = { it.label }
        )

        val releaseYears = yearTallies(
            entries.mapNotNull { entry -> entry.media.startDate?.year }
        )

        val watchYears = yearTallies(
            entries.mapNotNull { entry -> entry.completedAt?.year }
        )

        val (bucketed, withoutEpisodes) = episodeBuckets(entries)

        return ListEntryInsights(
            countries = countries,
            releaseYears = releaseYears,
            watchYears = watchYears,
            episodeBuckets = bucketed,
            entriesWithoutEpisodes = withoutEpisodes,
            entriesCounted = entries.size
        )
    }

    /**
     * One tally per year, in chronological order.
     *
     * Grouped on the real year rather than on the chart label, because two
     * different years can share one label: "'19" is 1999 and 2019 at once.
     * Grouping on the label merged those rows, and sorting on a century
     * reconstructed from two digits re-read 1995 as 2095, so a pre-2000 show
     * landed *after* a 2019 one on both year charts. Keeping the year through
     * the grouping counts and orders correctly; it is down-coded to a label only
     * at the end.
     */
    private fun yearTallies(years: List<Int>): List<BucketTally> {
        val counts = LinkedHashMap<Int, Int>()
        years.forEach { year -> counts[year] = (counts[year] ?: 0) + 1 }
        return withShares(
            counts.entries
                .sortedBy { it.key }
                .map { (year, count) -> BucketTally(yearLabel(year), count, 0f) }
        )
    }

    /**
     * Count rows into one tally per distinct label.
     *
     * [countBy] is the grouping key, kept separate from each row's own label so a
     * caller can group on a value the label only represents. It used to be how
     * the years chart grouped without re-reading a truncated two-digit label;
     * that chart now groups on the real year in [yearTallies].
     */
    private fun buckets(
        rows: List<BucketTally>,
        countBy: (BucketTally) -> String
    ): List<BucketTally> {
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        rows.forEach { row ->
            val key = countBy(row)
            val existing = counts[key]
            counts[key] = key to (existing?.second ?: 0) + row.count
        }
        val tallies = counts.map { (_, labelAndCount) ->
            BucketTally(labelAndCount.first, labelAndCount.second, 0f)
        }
        return withShares(tallies)
    }

    /**
     * Episodes per show, in bands.
     *
     * The bands are the ones the reference profiles use, and they are chosen so
     * that the four common shapes land in four different rows: a movie or short
     * (1), a mid-length OVA or two-cour (2-6), a standard season (7-16), and a
     * long or split-cour show (17-28). Everything longer goes in "29+" rather than
     * being dropped, because a 40-episode gintama is somebody's most-watched show
     * and hiding it would make the chart wrong in the direction that matters.
     *
     * An entry with no episode count at all - an ongoing show AniList has not
     * counted yet - lands in no bucket and is reported separately.
     */
    private fun episodeBuckets(entries: List<MediaListEntryItem>): Pair<List<BucketTally>, Int> {
        var withoutEpisodes = 0
        val counts = LinkedHashMap<String, Int>()
        EPISODE_BANDS.forEach { band -> counts[band.label] = 0 }

        entries.forEach { entry ->
            // A plain non-null local rather than a smart cast on
            // `entry.media.episodes`. The cast is legal here but fragile: it
            // depends on `episodes` being a local `val` rather than a property,
            // and the failure it would produce is a nullable `Int` reaching
            // `<=`, which says nothing about what was wrong.
            val episodes: Int? = entry.media.episodes
            if (episodes == null || episodes <= 0) {
                withoutEpisodes++
            } else {
                val count = episodes
                val band = EPISODE_BANDS.firstOrNull { count <= it.until }
                    // Past the last band rather than dropped.
                    ?: EPISODE_BANDS.last()
                counts[band.label] = (counts[band.label] ?: 0) + 1
            }
        }

        // Fixed band order, not by count. The bands are a scale from one episode to
        // many, and re-ordering them by size would make a chart of them a second,
        // less useful copy of the Format chart.
        return withShares(EPISODE_BANDS.map { band ->
            BucketTally(band.label, counts[band.label] ?: 0, 0f)
        }) to withoutEpisodes
    }

    private fun withShares(rows: List<BucketTally>): List<BucketTally> {
        val largest = rows.maxOfOrNull { it.count } ?: 0
        if (largest <= 0) return rows.map { it.copy(share = 0f) }
        return rows.map { it.copy(share = (it.count.toFloat() / largest).coerceIn(0f, 1f)) }
    }

    private companion object {
        class Band(val label: String, val until: Int)

        val EPISODE_BANDS = listOf(
            Band("1", 1),
            Band("2-6", 6),
            Band("7-16", 16),
            Band("17-28", 28),
            Band("29+", Int.MAX_VALUE)
        )

        /**
         * A year as the chart shows it.
         *
         * `'19` for the century where the two-digit form is unambiguous, and the
         * full year outside it. Truncation stops being a display choice and
         * starts being a collision at the century boundary - 1999 and 2019 are
         * both "'19" if the first is truncated - so a pre-2000 title keeps its
         * full year and stays distinguishable from the 20xx one beside it.
         *
         * Concatenation rather than an inline `"'$${...}"`. That is not an
         * apostrophe followed by an interpolation: `$` before another `$` is a
         * literal dollar, so the label came out `$19` on both charts and pinned
         * the mistake.
         */
        fun yearLabel(year: Int): String =
            if (year in CENTURY until CENTURY + 100) {
                YEAR_PREFIX + year.toString().takeLast(2)
            } else {
                year.toString()
            }

        const val CENTURY = 2000

        /** What [yearLabel] puts in front of a two-digit year. */
        const val YEAR_PREFIX = "'"

        /**
         * Country codes as the countries, not the codes.
         *
         * AniList's values are two-letter codes and "Jp" on a chart is a database
         * leaking. A code this app does not recognise is passed through rather than
         * hidden - a new country should be visible, just not invented.
         */
        fun String.toCountryLabel(): String = when (this) {
            "JP" -> "Japan"
            "KR" -> "South Korea"
            "CN" -> "China"
            "US" -> "United States"
            else -> uppercase()
        }
    }
}