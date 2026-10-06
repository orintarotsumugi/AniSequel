package com.example.domain.usecase

import com.example.data.model.FormatAmount
import com.example.data.model.GenreAmount
import com.example.data.model.ScoreAmount
import com.example.data.model.StatusAmount
import com.example.data.model.TagAmount
import com.example.data.model.YearAmount

/** One row of a horizontal bar chart: a label, a count, and a share of the largest. */
data class BucketTally(
    val label: String,
    val count: Int,
    /**
     * `count` as a fraction of the largest count in this same chart, 0f..1f.
     *
     * Computed here rather than in the UI so every chart on the screen scales the
     * same way. Scaling each chart against its own maximum rather than an absolute
     * number is what lets a status chart and a years chart sit under each other
     * without one of them being a flat line.
     */
    val share: Float
)

/**
 * One row of a segmented bar: a share of a whole, with a colour slot.
 *
 * Separate from [BucketTally] because the two charts are not the same shape. A
 * bar chart has one row per label and scales by its own maximum; a segmented bar
 * is one strip whose segments are shares of the total, so the widths have to sum
 * to 1 and the labels have to wrap rather than stack.
 */
data class SegmentTally(
    val label: String,
    val count: Int,
    val share: Float,
    /** Which colour slot to use, by position, so two cards can be compared. */
    val slot: Int
)

/** The Status Distribution card: chips plus the strip they describe. */
data class StatusDistribution(
    val segments: List<SegmentTally>,
    val total: Int
) {
    val isEmpty: Boolean get() = segments.isEmpty() || total <= 0
}

/**
 * Everything on the Stats tab that AniList's `User.stats` block answers for.
 *
 * All of it is one round trip on the profile query, and all of it is pure
 * reshaping - the arithmetic is here so it can be tested without an emulator.
 */
data class ListInsights(
    val status: StatusDistribution,
    val formats: List<BucketTally>,
    val countries: List<BucketTally>,
    val scores: List<BucketTally>,
    val genres: List<BucketTally>,
    val tags: List<BucketTally>,
    val releaseYears: List<BucketTally>,
    /** Distinct AniList levels on [scores]; the chart colours by score band. */
    val maxScore: Int
)

/**
 * Shapes AniList's aggregate statistics into the rows the Stats tab draws.
 *
 * ## Ordering, and why it is not the order AniList sent
 *
 *  - Status: a fixed order, watching first. AniList sends them by descending
 *    count, which on a large account puts "Planning" above "Watching" - and the
 *    one number that changes week to week ends up below a hundred static ones.
 *  - Formats: by count, so the biggest is at the top.
 *  - Scores: ascending by score. A score chart that is ordered by *frequency*
 *    interleaves 100 between 60 and 90 and stops being a scale.
 *  - Years: ascending by year. Same reason, and it makes "which years am I behind
 *    on" a glance rather than a hunt.
 */
class BuildListInsightsUseCase {

    operator fun invoke(
        animeStatusDistribution: List<StatusAmount>?,
        animeScoreDistribution: List<ScoreAmount>?,
        favouredFormats: List<FormatAmount>?,
        favouredYears: List<YearAmount>?,
        favouredGenres: List<GenreAmount>?,
        favouredTags: List<TagAmount>?,
        countries: List<BucketTally>? = null,
        releaseYears: List<BucketTally>? = null,
        topGenres: Int = DEFAULT_TOP_GENRES,
        topTags: Int = DEFAULT_TOP_TAGS
    ): ListInsights {
        val scores = animeScoreDistribution.orEmpty()
            .filter { it.score != null && (it.amount ?: 0) > 0 }
            .sortedBy { it.score }
            .map { BucketTally(it.score.toString(), it.amount ?: 0, 0f) }

        return ListInsights(
            status = statusDistribution(animeStatusDistribution),
            formats = buckets(
                favouredFormats.orEmpty().mapNotNull { row ->
                    row.format?.let { BucketTally(it.toDisplayLabel(), row.amount ?: 0, 0f) }
                },
                sortByCountDescending = true
            ),
            countries = countries.orEmpty().sortedByDescending { it.count },
            scores = withShares(scores),
            genres = buckets(
                favouredGenres.orEmpty().mapNotNull { row ->
                    row.genre?.let { BucketTally(it, row.amount ?: 0, 0f) }
                },
                sortByCountDescending = true,
                limit = topGenres
            ),
            tags = buckets(
                favouredTags.orEmpty().mapNotNull { row ->
                    row.tag?.name?.let { BucketTally(it, row.amount ?: 0, 0f) }
                },
                sortByCountDescending = true,
                limit = topTags
            ),
            releaseYears = releaseYears.orEmpty().sortedBy { it.label },
            // The top of the score scale as AniList reports it, so the chart can
            // colour a band by its position on the scale rather than by how common
            // that score is. A person whose best entry is 85 should see their
            // darkest green at 85, not at 100.
            maxScore = scores.maxOfOrNull { bucket -> bucket.label.toIntOrNull() ?: 0 }
                ?.takeIf { it > 0 } ?: 100
        )
    }

    /**
     * The status chips and the strip under them.
     *
     * A status this app has never seen - AniList can add one without telling us -
     * is labelled by breaking its enum name apart rather than by dropping it.
     * `SOMETHING_NEW` becomes "Something new", not "Something_new" and not nothing
     * at all: the label is the only place a person would ever have learnt the
     * status exists, and an underscore is a database leaking in place of it.
     */
    private fun statusDistribution(distribution: List<StatusAmount>?): StatusDistribution {
        val present = distribution.orEmpty()
            .filter { it.status != null }
            .associateBy { it.status!! }
            .toMutableMap()

        // Anything AniList sent as zero is dropped from the strip but kept in the
        // `withShares` denominator, so "128 completed" is not 128 out of a total
        // that silently shrank.
        val rows = present.values
            .filter { (it.amount ?: 0) > 0 }
            .sortedBy { statusOrder(it.status!!) }

        val total = rows.sumOf { it.amount ?: 0 }
        if (total <= 0) return StatusDistribution(emptyList(), 0)

        return StatusDistribution(
            segments = rows.mapIndexed { index, row ->
                val count = row.amount ?: 0
                SegmentTally(
                    label = row.status!!.toStatusLabel(),
                    count = count,
                    share = count.toFloat() / total,
                    slot = index
                )
            },
            total = total
        )
    }

    /**
     * Fixed display order, with anything AniList adds appended rather than lost.
     *
     * Appending matters: a status this app has never heard of still belongs in the
     * total, and dropping it would make the chips' sum disagree with the total
     * printed underneath them.
     */
    private fun statusOrder(status: String): Int {
        val index = KNOWN_STATUS_ORDER.indexOf(status)
        return if (index >= 0) index else KNOWN_STATUS_ORDER.size
    }

    private fun String.toStatusLabel(): String = when (this) {
        "CURRENT" -> "Watching"
        "COMPLETED" -> "Completed"
        "PAUSED" -> "On Hold"
        "DROPPED" -> "Dropped"
        "PLANNING" -> "Planning"
        "REPEATING" -> "Rewatching"
        // Underscores are word breaks here, not characters. See the note on
        // `statusDistribution`.
        else -> lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /**
     * AniList's format enums as people write them.
     *
     * The screen's own chips say "Tv", "Movie", "Ona" - not `TV_SHORT`,
     * `TV`, `MOVIE`. The raw enums on one chart with "104 TV" and "2 TV_SHORT"
     * beside each other is a distinction almost nobody has to make.
     */
    private fun String.toDisplayLabel(): String = when (this) {
        "TV" -> "Tv"
        "TV_SHORT" -> "Tv Short"
        "ONA" -> "Ona"
        "OVA" -> "Ova"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "MUSIC" -> "Music"
        "MANGA" -> "Manga"
        "NOVEL" -> "Novel"
        "ONE_SHOT" -> "One Shot"
        else -> lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun buckets(
        rows: List<BucketTally>,
        sortByCountDescending: Boolean,
        limit: Int = 0
    ): List<BucketTally> {
        val ordered = if (sortByCountDescending) {
            // Ties broken alphabetically so the chart does not reshuffle between
            // two loads of the same data - two genres with 12 entries each should
            // not swap places because the JSON order changed.
            rows.sortedWith(compareByDescending<BucketTally> { it.count }.thenBy { it.label })
        } else {
            rows
        }
        val limited = if (limit > 0) ordered.take(limit) else ordered
        return withShares(limited)
    }

    private fun withShares(rows: List<BucketTally>): List<BucketTally> {
        val largest = rows.maxOfOrNull { it.count } ?: 0
        if (largest <= 0) return rows.map { it.copy(share = 0f) }
        return rows.map { it.copy(share = (it.count.toFloat() / largest).coerceIn(0f, 1f)) }
    }

    companion object {
        val KNOWN_STATUS_ORDER = listOf(
            "CURRENT", "COMPLETED", "PAUSED", "DROPPED", "PLANNING", "REPEATING"
        )

        /**
         * Genres are a fixed list of about twenty on AniList, so every one of them
         * is shown. Tags are not - there are thousands, and a person can have
         * hundreds favourited - so the chart shows the strongest.
         */
        const val DEFAULT_TOP_GENRES = 0
        const val DEFAULT_TOP_TAGS = 12
    }
}