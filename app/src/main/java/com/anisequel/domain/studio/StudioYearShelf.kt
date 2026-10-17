package com.anisequel.domain.studio

import com.anisequel.data.model.MediaNode

/**
 * One year of a studio's output, as the studio page draws it.
 *
 * The page is a list of these rather than a flat list of titles, because "what did
 * this studio make" is almost never the question - "when did they make *this*" is,
 * and a studio with two hundred titles in one column answers neither. Grouping is
 * the whole shape of the screen, so it is decided here, in pure Kotlin, instead of
 * inside a `LazyColumn` where it can only be checked by looking at it.
 */
data class StudioYearShelf(
    /** What the heading says: a year, or [STUDIO_UNDATED_SHELF]. */
    val label: String,
    /** The year, or null for the shelf that has no date to name it by. */
    val year: Int?,
    val titles: List<MediaNode>
)

/**
 * The label for the shelf holding titles with no date.
 *
 * `TBA` rather than a year that does not exist: an announced continuation is a
 * different kind of thing from a 2027 release, and printing it under a guessed year
 * would be the screen inventing a fact. AniList publishes no null-ordering for
 * `START_DATE_DESC`, so this shelf is also *sorted* here rather than trusted to the
 * server.
 */
const val STUDIO_UNDATED_SHELF = "TBA"

/**
 * The year a title belongs to, or null when AniList has not said.
 *
 * Prefers the start date's year over `seasonYear`, which is the same preference
 * `MediaRelationEdge.metaLabel` uses and for the same reason: `seasonYear` is set on
 * anything AniList has *scheduled*, while a title that has already aired reliably
 * carries a full start date. A title with neither is undated, which is a fact about
 * the answer rather than about the title.
 */
val MediaNode.studioYear: Int? get() = startDate?.year ?: seasonYear

/**
 * Groups a studio's titles into the shelves the page draws.
 *
 * Order is: the undated shelf first, then years descending. Undated first is the
 * order the reference draws and it is also the useful one - what a studio has
 * *announced* is what a reader opens the page to see, and it would otherwise be
 * sorted to the bottom by a number it does not have.
 *
 * Duplicates are dropped by media id, keeping the first. Paging is what makes that
 * necessary rather than hypothetical: this is the one list in the app that is built
 * by appending pages fetched at different moments, and AniList re-orders
 * `START_DATE_DESC` as new releases are announced - so a title can appear on the
 * page boundary twice, and without this the page shows it twice too, in two
 * different year shelves.
 */
fun studioYearShelves(titles: List<MediaNode>): List<StudioYearShelf> {
    val seen = mutableSetOf<Int>()
    val unique = titles.filter { seen.add(it.id) }

    val undated = unique.filter { it.studioYear == null }
    val dated = unique.filterNot { it.studioYear == null }

    return buildList {
        if (undated.isNotEmpty()) {
            add(StudioYearShelf(label = STUDIO_UNDATED_SHELF, year = null, titles = undated))
        }
        // `groupBy` keeps encounter order inside each year, which is AniList's own
        // `START_DATE_DESC` order - so two titles in the same year stay newest
        // first instead of being re-sorted by a field this code does not have.
        dated.groupBy { it.studioYear!! }
            .entries
            .sortedByDescending { it.key }
            .forEach { (year, group) ->
                add(StudioYearShelf(label = year.toString(), year = year, titles = group))
            }
    }
}
