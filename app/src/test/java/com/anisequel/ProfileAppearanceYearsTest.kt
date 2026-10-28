package com.anisequel

import com.anisequel.data.model.FuzzyDate
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.ProfileAppearance
import com.anisequel.data.model.ProfileAppearanceEdge
import com.anisequel.data.model.ProfileAppearances
import com.anisequel.data.network.GraphQLQueries
import com.anisequel.ui.components.detail.UNKNOWN_YEAR_LABEL
import com.anisequel.ui.components.detail.appearancesByYear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A creator's filmography, read by year.
 *
 * The failure this guards is the one a screenshot cannot show: AniList answers
 * `staffMedia` by popularity, so the list arrives out of chronological order and
 * the *grouping* is what turns it into a career. A year dropped, two groups
 * swapped, or an undated title sorted as year zero all still render as a tidy
 * list of posters - they just say the wrong thing about the person.
 *
 * The other half is the query. `startDate` is not on [ProfileAppearance] by
 * accident and it is not derivable from anything else on the node: if the
 * document stops asking for it every group collapses into "Year unknown", which
 * is a regression that compiles.
 */
class ProfileAppearanceYearsTest {

    private fun appearance(
        id: Int,
        year: Int?,
        type: String = "ANIME",
        title: String = "Title $id"
    ) = ProfileAppearance(
        id = id,
        type = type,
        title = MediaTitle(romaji = title),
        startDate = year?.let { FuzzyDate(year = it) }
    )

    /** The whole point of the view: newest first, no matter what order arrived. */
    @Test
    fun `years descend regardless of the order the connection sent`() {
        val years = appearancesByYear(
            listOf(
                appearance(1, 1998),
                appearance(2, 2019),
                appearance(3, 2011)
            )
        )

        assertEquals(
            "a career reads newest first",
            listOf("2019", "2011", "1998"),
            years.map { it.label }
        )
    }

    /** A title AniList has not dated is not a year, so it must not sort as one. */
    @Test
    fun `undated titles come last under their own label`() {
        val years = appearancesByYear(
            listOf(
                appearance(1, null),
                appearance(2, 2004)
            )
        )

        assertEquals(listOf("2004", UNKNOWN_YEAR_LABEL), years.map { it.label })
        assertEquals(
            "the undated title is still drawn, not dropped",
            listOf(1),
            years.last().appearances.map { it.id }
        )
    }

    /** Inside a year the connection's own - popularity - order is kept. */
    @Test
    fun `titles inside a year keep the order they arrived in`() {
        val years = appearancesByYear(
            listOf(
                appearance(7, 2015),
                appearance(4, 2015),
                appearance(9, 2015)
            )
        )

        assertEquals(listOf(7, 4, 9), years.single().appearances.map { it.id })
    }

    /**
     * The id collision `MediaCoverKey` documents, in the list this view draws: an
     * anime and a manga can share an id, and grouping must not merge them.
     */
    @Test
    fun `an anime and a manga sharing an id stay separate entries`() {
        val years = appearancesByYear(
            listOf(
                appearance(1, 2016, type = "ANIME"),
                appearance(1, 2016, type = "MANGA")
            )
        )

        assertEquals(
            "both media are one group, but two entries",
            2,
            years.single().appearances.size
        )
    }

    /**
     * The year has to survive the edges-to-titles flattening, which is where the
     * credit and the node meet. `copy(staffRole = ...)` is the only thing that
     * touches a node there, and a rewrite that built a fresh [ProfileAppearance]
     * would silently drop `startDate` with it.
     */
    @Test
    fun `startDate survives the edges to titles mapping`() {
        val titles = ProfileAppearances(
            edges = listOf(
                ProfileAppearanceEdge(
                    staffRole = "Director",
                    node = appearance(3, 2009)
                )
            )
        ).titles

        assertEquals(2009, titles.single().startDate?.year)
        assertEquals("Director", titles.single().staffRole)
    }

    /**
     * Pinned on the document rather than the mapper: the mapper can only group what
     * arrived, and a query that stopped asking for `startDate` would leave every
     * group undated without failing anything else.
     */
    @Test
    fun `both person queries ask for the year`() {
        listOf(
            "character" to GraphQLQueries.getCharacterProfileQuery(),
            "staff" to GraphQLQueries.getStaffProfileQuery()
        ).forEach { (kind, query) ->
            assertTrue(
                "the $kind query must ask for startDate { year }, or nothing is dated",
                query.contains("startDate { year }")
            )
        }
    }
}
