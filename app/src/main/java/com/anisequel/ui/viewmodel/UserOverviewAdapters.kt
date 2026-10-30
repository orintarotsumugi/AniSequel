package com.anisequel.ui.viewmodel

import com.anisequel.data.model.AnimeStats
import com.anisequel.data.model.FavouriteCharacter
import com.anisequel.data.model.FavouriteStaff
import com.anisequel.data.model.FormatAmount
import com.anisequel.data.model.GenreAmount
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.StudioNode
import com.anisequel.data.model.TagAmount
import com.anisequel.data.model.YearAmount
import kotlin.math.roundToInt

/**
 * The profile screen's row-level translations.
 *
 * Split out of `UserOverviewViewModel`: that class owns which requests are in
 * flight, what a failure leaves on screen and when a section is stale. These are
 * pure mappings from one payload shape to another - AniList's stat rows into the
 * chart builder's buckets, a status-grouped collection into a flat list - and they
 * change when the API's field names change, which is not the same reason at all.
 */

/**
 * Every list entry on a collection, flattened and de-duplicated by media id.
 *
 * AniList returns a list per *status group*, and a custom list can overlap
 * the standard ones, so the same anime can arrive more than once - across
 * *different* groups too. The Library grid is keyed on the media id, and a
 * repeated key throws `IllegalArgumentException` while it lays out, so this is a
 * crash guard rather than tidying.
 */
internal fun flattenLibrary(collection: MediaListCollection?): List<MediaListEntryItem> =
    collection?.lists
        ?.flatMap { it.entries.orEmpty() }
        ?.distinctBy { it.media.id }
        .orEmpty()

/**
 * `statistics.anime` distributions, in the shapes `BuildListInsightsUseCase`
 * already takes.
 *
 * The alternative was to change the chart builder and its tests to the API's own
 * field names. That is a bigger change than the bug asks for: the builder's job is
 * "turn tallies into sorted chart buckets", it already has a tested contract, and
 * a rename would make the diff about naming rather than about the four fields that
 * were returning HTTP 500. The translation is here, in one place, so the mapping
 * (`count` -> `amount`, `Float` -> `Int`) is visible exactly once.
 *
 * A null `meanScore` becomes null rather than 0: a tally with no scores behind it
 * has no mean, and zero is a score a person could plausibly have given.
 */
internal fun AnimeStats.formatAmounts(): List<FormatAmount> =
    formats.orEmpty().mapNotNull { row ->
        val format = row.format ?: return@mapNotNull null
        FormatAmount(format = format, amount = row.count ?: 0)
    }

internal fun AnimeStats.releaseYearAmounts(): List<YearAmount> =
    releaseYears.orEmpty().mapNotNull { row ->
        val year = row.releaseYear ?: return@mapNotNull null
        YearAmount(year = year, amount = row.count ?: 0, meanScore = row.meanScore?.roundToInt())
    }

internal fun AnimeStats.genreAmounts(): List<GenreAmount> =
    genres.orEmpty().mapNotNull { row ->
        val genre = row.genre ?: return@mapNotNull null
        GenreAmount(
            genre = genre,
            amount = row.count ?: 0,
            meanScore = row.meanScore?.roundToInt(),
            timeWatched = row.minutesWatched?.toInt()
        )
    }

internal fun AnimeStats.tagAmounts(): List<TagAmount> =
    tags.orEmpty().mapNotNull { row ->
        val tag = row.tag ?: return@mapNotNull null
        TagAmount(tag = tag, amount = row.count ?: 0, meanScore = row.meanScore?.roundToInt())
    }

/**
 * Drops repeated entries from a favourites connection.
 *
 * Not defensive coding against an impossible response - AniList returns them. A
 * real captured response for a user with six favourited anime contained the same
 * media six times, and another returned the same twelve twice. The `favourites`
 * connection is evidently not de-duplicated server-side.
 *
 * That is not a cosmetic problem. The favourites rows are `LazyRow`s keyed on the
 * media id, and a `LazyRow` handed the same key twice throws
 * `IllegalArgumentException: Key "79" was already used` while it is being laid
 * out - so one person's favourites would crash the screen, on a device, with no way
 * to see what went wrong. De-duplicating here also makes the rows honest: twelve
 * covers of six shows is not what anybody pinned.
 *
 * First occurrence wins, so the order AniList chose is preserved.
 *
 * `internal` rather than private so `FavouritesDeduplicationTest` can exercise the
 * real function. A test that re-implements the rule next to it proves the test,
 * not the screen.
 */
internal fun <T> List<T>.distinctById(): List<T> {
    val seen = HashSet<Int>()
    return filter { item ->
        val id = when (item) {
            is MediaNode -> item.id
            is FavouriteCharacter -> item.id
            is FavouriteStaff -> item.id
            is StudioNode -> item.id
            else -> null
        }
        id == null || seen.add(id)
    }
}
