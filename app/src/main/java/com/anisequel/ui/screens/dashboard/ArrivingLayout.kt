package com.anisequel.ui.screens.dashboard

import com.anisequel.data.model.ArrivingEntry

/**
 * One horizontal strip of the "Currently arriving" section.
 *
 * [label] is null for the combined strip: a row that carries both groups does
 * not need a heading, and drawing "Airing now / Upcoming" above it would take
 * back the height the combined layout exists to save. The cards themselves
 * already say which group they belong to through their status chip and colour.
 */
data class ArrivingRow(
    /** True when the strip is the airing half, which keys its colour and test tag. */
    val airing: Boolean,
    val label: String?,
    val entries: List<ArrivingEntry>
)

/**
 * How the arriving entries are split into strips.
 *
 * Combined is the shipped default: one row, airing first, so the section above
 * search is one line tall. Separating the groups is the reader's choice, and it
 * is expressed here rather than in the composable so the two layouts cannot
 * disagree about order or about which group an entry belongs to.
 *
 * Empty groups are dropped rather than drawn as an empty strip - a reader with
 * only upcoming titles should not get an "Airing now" heading with nothing under
 * it. The combined row keeps the use case's own order, which is already airing
 * before upcoming and soonest first inside each half.
 */
fun arrivingRows(
    entries: List<ArrivingEntry>,
    separateGroups: Boolean
): List<ArrivingRow> {
    if (entries.isEmpty()) return emptyList()

    val airing = entries.filter { it.isAiring }
    val upcoming = entries.filterNot { it.isAiring }

    if (!separateGroups) {
        return listOf(
            ArrivingRow(
                airing = airing.isNotEmpty(),
                label = null,
                entries = airing + upcoming
            )
        )
    }

    return listOfNotNull(
        airing.takeIf { it.isNotEmpty() }?.let {
            ArrivingRow(airing = true, label = "Airing now", entries = it)
        },
        upcoming.takeIf { it.isNotEmpty() }?.let {
            ArrivingRow(airing = false, label = "Upcoming", entries = it)
        }
    )
}
