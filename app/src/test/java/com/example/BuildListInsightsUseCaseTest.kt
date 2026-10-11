package com.example

import com.example.data.model.FormatAmount
import com.example.data.model.GenreAmount
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaListEntryItem
import com.example.data.model.MediaListGroup
import com.example.data.model.MediaNode
import com.example.data.model.MediaTitle
import com.example.data.model.ScoreAmount
import com.example.data.model.StatusAmount
import com.example.data.model.TagAmount
import com.example.data.model.MediaTag
import com.example.data.model.FuzzyDate
import com.example.domain.usecase.BuildListEntryInsightsUseCase
import com.example.domain.usecase.BuildListInsightsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Stats tab's reshaping of AniList's aggregate statistics.
 *
 * Every failure being pinned here produces a chart that looks correct and is not:
 * a status order that puts a hundred static Planning entries above the three
 * Watching entries that changed this week; a score chart ordered by frequency, so
 * 100 ends up between 60 and 90 and stops being a scale; a segment of 0.3% drawn
 * as nothing at all.
 */
class BuildListInsightsUseCaseTest {

    private val useCase = BuildListInsightsUseCase()

    private fun build(
        statuses: List<StatusAmount>? = null,
        scores: List<ScoreAmount>? = null,
        formats: List<FormatAmount>? = null,
        genres: List<GenreAmount>? = null,
        tags: List<TagAmount>? = null
    ) = useCase(
        animeStatusDistribution = statuses,
        animeScoreDistribution = scores,
        favouredFormats = formats,
        favouredYears = null,
        favouredGenres = genres,
        favouredTags = tags
    )

    @Test
    fun `statuses come out in a fixed order, not in count order`() {
        val insights = build(
            statuses = listOf(
                StatusAmount("PLANNING", 108),
                StatusAmount("COMPLETED", 122),
                StatusAmount("CURRENT", 7),
                StatusAmount("PAUSED", 8),
                StatusAmount("DROPPED", 3)
            )
        )

        assertEquals(
            listOf("Watching", "Completed", "On Hold", "Dropped", "Planning"),
            insights.status.segments.map { it.label }
        )
        assertEquals(248, insights.status.total)
    }

    @Test
    fun `a status AniList sent as zero is left out of the strip`() {
        val insights = build(
            statuses = listOf(
                StatusAmount("CURRENT", 7),
                StatusAmount("COMPLETED", 122),
                StatusAmount("DROPPED", 0)
            )
        )

        assertEquals(listOf("Watching", "Completed"), insights.status.segments.map { it.label })
        assertEquals(129, insights.status.total)
    }

    @Test
    fun `a status this app has never heard of still counts`() {
        // AniList can add one without telling us. Dropping it would make the chips
        // stop summing to the total printed underneath them.
        val insights = build(
            statuses = listOf(
                StatusAmount("CURRENT", 5),
                StatusAmount("SOMETHING_NEW", 4)
            )
        )

        assertEquals(9, insights.status.total)
        assertEquals(2, insights.status.segments.size)
        assertTrue(insights.status.segments.any { it.label == "Something new" })
    }

    @Test
    fun `segment shares are shares of the total and sum to one`() {
        val insights = build(
            statuses = listOf(
                StatusAmount("COMPLETED", 122),
                StatusAmount("PLANNING", 108),
                StatusAmount("PAUSED", 8)
            )
        )

        val total = insights.status.segments.sumOf { it.count }
        insights.status.segments.forEach { segment ->
            assertEquals(segment.count.toFloat() / total, segment.share, 0.0001f)
        }
        assertEquals(1f, insights.status.segments.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `an empty or all-zero status distribution is not a crash`() {
        assertTrue(build(statuses = null).status.isEmpty)
        assertTrue(build(statuses = listOf(StatusAmount("CURRENT", 0))).status.isEmpty)
        assertEquals(0, build(statuses = null).status.total)
    }

    @Test
    fun `scores are ordered by score, not by how common they are`() {
        val insights = build(
            scores = listOf(
                ScoreAmount(100, 5),
                ScoreAmount(50, 90),
                ScoreAmount(80, 30)
            )
        )

        assertEquals(listOf("50", "80", "100"), insights.scores.map { it.label })
        // The top of the scale is the highest score actually present, so a
        // person's best entry is the darkest colour rather than a 100 that is not
        // on their list.
        assertEquals(100, insights.maxScore)
    }

    @Test
    fun `the score scale falls back to 100 when nothing was scored`() {
        assertEquals(100, build(scores = null).maxScore)
        assertEquals(85, build(scores = listOf(ScoreAmount(85, 2))).maxScore)
    }

    @Test
    fun `formats are ordered by count and labelled the way people write them`() {
        val insights = build(
            formats = listOf(
                FormatAmount("TV", 104),
                FormatAmount("MOVIE", 21),
                FormatAmount("ONA", 6),
                FormatAmount("TV_SHORT", 2)
            )
        )

        assertEquals(
            listOf("Tv", "Movie", "Ona", "Tv Short"),
            insights.formats.map { it.label }
        )
    }

    @Test
    fun `equal counts are broken alphabetically so the chart does not reshuffle`() {
        val insights = build(
            formats = listOf(
                FormatAmount("TV", 10),
                FormatAmount("ONA", 10),
                FormatAmount("OVA", 10)
            )
        )

        assertEquals(listOf("Ona", "Ova", "Tv"), insights.formats.map { it.label })
    }

    @Test
    fun `a bar's share is its fraction of the largest bar`() {
        val insights = build(formats = listOf(FormatAmount("TV", 100), FormatAmount("MOVIE", 25)))

        assertEquals(1f, insights.formats[0].share, 0.0001f)
        assertEquals(0.25f, insights.formats[1].share, 0.0001f)
    }

    @Test
    fun `tags are capped and genres are not`() {
        val manyTags = (1..40).map { TagAmount(MediaTag(id = it, name = "Tag $it"), amount = 40 - it) }
        val insights = useCase(
            animeStatusDistribution = null,
            animeScoreDistribution = null,
            favouredFormats = null,
            favouredYears = null,
            favouredGenres = listOf(GenreAmount("Action", 10), GenreAmount("Drama", 5)),
            favouredTags = manyTags
        )

        // AniList's genre list is fixed at about twenty, so all of them are shown;
        // tags number in the thousands and are not.
        assertEquals(2, insights.genres.size)
        assertEquals(BuildListInsightsUseCase.DEFAULT_TOP_TAGS, insights.tags.size)
        assertEquals("Tag 1", insights.tags.first().label)
    }

    @Test
    fun `an absent distribution is an empty chart rather than a failure`() {
        val insights = build()

        assertTrue(insights.formats.isEmpty())
        assertTrue(insights.scores.isEmpty())
        assertTrue(insights.genres.isEmpty())
        assertTrue(insights.tags.isEmpty())
    }
}

/**
 * The three Stats charts AniList has no aggregate for.
 *
 * They are tallied from the media list the app already downloads, which is free -
 * and which is why the interesting part of these tests is what gets *excluded*.
 */
class BuildListEntryInsightsUseCaseTest {

    private val useCase = BuildListEntryInsightsUseCase()

    private fun entry(
        id: Int,
        country: String? = "JP",
        episodes: Int? = 12,
        releaseYear: Int? = 2019,
        completedYear: Int? = 2024,
        status: String? = "COMPLETED"
    ) = MediaListEntryItem(
        status = status,
        completedAt = completedYear?.let { FuzzyDate(year = it) },
        media = MediaNode(
            id = id,
            title = MediaTitle(english = "Show $id"),
            episodes = episodes,
            countryOfOrigin = country,
            startDate = releaseYear?.let { FuzzyDate(year = it) }
        )
    )

    private fun collection(vararg entries: MediaListEntryItem) = MediaListCollection(
        lists = listOf(MediaListGroup(name = "All", status = "COMPLETED", entries = entries.toList()))
    )

    @Test
    fun `countries are counted and named, not shown as codes`() {
        val insights = useCase(
            collection(
                entry(1, country = "JP"),
                entry(2, country = "JP"),
                entry(3, country = "KR")
            )
        )

        assertEquals(listOf("Japan", "South Korea"), insights.countries.map { it.label })
        assertEquals(listOf(2, 1), insights.countries.map { it.count })
    }

    @Test
    fun `an unknown country code is passed through rather than hidden`() {
        // A new country should be visible, even if it is only its code.
        val insights = useCase(collection(entry(1, country = "XX")))

        assertEquals("XX", insights.countries.single().label)
    }

    @Test
    fun `an entry with no country lands in no bucket`() {
        val insights = useCase(collection(entry(1, country = null), entry(2, country = "JP")))

        assertEquals(1, insights.countries.sumOf { it.count })
    }

    @Test
    fun `release years are two-digit and in ascending order`() {
        val insights = useCase(
            collection(
                entry(1, releaseYear = 2019),
                entry(2, releaseYear = 2006),
                entry(3, releaseYear = 2021)
            )
        )

        assertEquals(listOf("'06", "'19", "'21"), insights.releaseYears.map { it.label })
    }

    @Test
    fun `two-digit year labels sort by year, not by their characters`() {
        // Sorting on the label would put '06 after '26, because "0" > "2".
        val insights = useCase(
            collection(
                entry(1, releaseYear = 2026),
                entry(2, releaseYear = 2006),
                entry(3, releaseYear = 2019)
            )
        )

        assertEquals(listOf("'06", "'19", "'26"), insights.releaseYears.map { it.label })
    }

    @Test
    fun `the watch year is the year the person finished, not the release year`() {
        val insights = useCase(
            collection(entry(1, releaseYear = 2004, completedYear = 2025))
        )

        assertEquals("'25", insights.watchYears.single().label)
        assertEquals("'04", insights.releaseYears.single().label)
    }

    @Test
    fun `a pre-2000 year keeps its full digits and sorts before the 2000s`() {
        // Every year used to be truncated to two digits and read back as 20xx.
        // A 1995 show became "'95" and sorted as 2095 - after a 2019 one - so the
        // chart claimed the wrong chronological order.
        val insights = useCase(
            collection(
                entry(1, releaseYear = 2019),
                entry(2, releaseYear = 1995),
                entry(3, releaseYear = 2006)
            )
        )

        assertEquals(listOf("1995", "'06", "'19"), insights.releaseYears.map { it.label })
    }

    @Test
    fun `1999 and 2019 are two buckets, not one shared two-digit label`() {
        // Truncation made both of these "'19" and the label-keyed grouping merged
        // them into a single row, hiding that a 1999 title and a 2019 one are
        // different years.
        val insights = useCase(
            collection(
                entry(1, releaseYear = 1999),
                entry(2, releaseYear = 2019),
                entry(3, releaseYear = 1999)
            )
        )

        assertEquals(listOf("1999", "'19"), insights.releaseYears.map { it.label })
        assertEquals(listOf(2, 1), insights.releaseYears.map { it.count })
    }

    @Test
    fun `episodes fall into the four bands plus everything longer`() {
        val insights = useCase(
            collection(
                entry(1, episodes = 1),
                entry(2, episodes = 6),
                entry(3, episodes = 7),
                entry(4, episodes = 16),
                entry(5, episodes = 17),
                entry(6, episodes = 28),
                entry(7, episodes = 40)
            )
        )

        assertEquals(listOf("1", "2-6", "7-16", "17-28", "29+"), insights.episodeBuckets.map { it.label })
        // 1 -> 1, 6 -> 2-6, 7 and 16 -> 7-16, 17 and 28 -> 17-28, 40 -> 29+.
        assertEquals(listOf(1, 1, 2, 2, 1), insights.episodeBuckets.map { it.count })
    }

    @Test
    fun `an ongoing show with no episode count is reported, not hidden`() {
        // A 40-episode gintama is somebody's most-watched show; dropping it would
        // make the chart wrong in the direction that matters.
        val insights = useCase(
            collection(
                entry(1, episodes = 40),
                entry(2, episodes = null),
                entry(3, episodes = 0)
            )
        )

        assertEquals(1, insights.episodeBuckets.first { it.label == "29+" }.count)
        assertEquals(2, insights.entriesWithoutEpisodes)
    }

    @Test
    fun `entries with no status are excluded`() {
        // `MediaListCollection` returns the entries a user removed from every
        // status list. Counting them would add rows for things nobody tracks, and
        // the status charts come from AniList's aggregate which excludes them too.
        val insights = useCase(
            collection(
                entry(1),
                entry(2, status = null),
                entry(3, status = "PLANNING")
            )
        )

        assertEquals(2, insights.entriesCounted)
        assertEquals(2, insights.countries.sumOf { it.count })
    }

    @Test
    fun `the episode bands keep their order and are scaled against the largest`() {
        val entries = (1..10).map { entry(it, episodes = 12) } + entry(11, episodes = 1)
        val insights = useCase(
            MediaListCollection(
                lists = listOf(
                    MediaListGroup(name = "All", status = "COMPLETED", entries = entries)
                )
            )
        )

        assertEquals(listOf("1", "2-6", "7-16", "17-28", "29+"), insights.episodeBuckets.map { it.label })
        assertEquals(1f, insights.episodeBuckets.first { it.label == "7-16" }.share, 0.0001f)
        assertEquals(0.1f, insights.episodeBuckets.first { it.label == "1" }.share, 0.0001f)
    }

    @Test
    fun `an absent list produces empty charts rather than a failure`() {
        val insights = useCase(null)

        assertTrue(insights.countries.isEmpty())
        assertTrue(insights.releaseYears.isEmpty())
        assertTrue(insights.watchYears.isEmpty())
        assertEquals(5, insights.episodeBuckets.size)
        assertEquals(0, insights.entriesCounted)
    }
}