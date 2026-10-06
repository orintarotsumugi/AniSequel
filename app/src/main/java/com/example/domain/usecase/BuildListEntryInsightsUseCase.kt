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

        val releaseYears = buckets(
            entries.mapNotNull { entry ->
                entry.media.startDate?.year?.let { BucketTally(twoDigitYear(it), 1, 0f) }
            },
            countBy = { it.label }
        ).sortedBy { it.yearSortKey }

        val watchYears = buckets(
            entries.mapNotNull { entry ->
                entry.completedAt?.year?.let { BucketTally(twoDigitYear(it), 1, 0f) }
            },
            countBy = { it.label }
        ).sortedBy { it.yearSortKey }

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
     * Count rows into one tally per distinct label.
     *
     * [countBy] is where the input rows and the output labels can differ - the
     * years chart labels "2019" as "'19" - so the grouping key is passed in rather
     * than derived from the label.
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
         * Pull the sort key back out of a "'19" label.
         *
         * The label is a display decision and sorting on it would sort "06" after
         * "26" for the wrong reason. Two-digit years only cover 2000-2099, which is
         * every anime AniList has, so prefixing a century constant is exact rather
         * than a guess.
         */
        val BucketTally.yearSortKey: Int
            // Parenthesised deliberately. `+` binds tighter than `?:`, so the
            // unparenthesised form parses as `(CENTURY + year) ?: 0` and the
            // sum's nullability leaks out of a getter declared `Int` - a compile
            // error about types in an expression that reads as arithmetic.
            get() = CENTURY + (label.removePrefix(YEAR_PREFIX).toIntOrNull() ?: 0)

        /**
         * A year as the chart shows it: `'19`.
         *
         * A function rather than `"'$${...}"` written inline, because that is not
         * an apostrophe followed by an interpolation. `$` followed by `$` is not a
         * template expression, so Kotlin reads the first as a literal dollar sign
         * and interpolates the second - the label came out as `$19`, every year on
         * both charts, and the tests caught it by expecting `'19`.
         */
        fun twoDigitYear(year: Int): String = YEAR_PREFIX + year.toString().takeLast(2)

        const val CENTURY = 2000

        /** What [twoDigitYear] puts in front, and what [yearSortKey] strips off. */
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