package com.example

import com.example.data.model.FavouriteCharacter
import com.example.data.model.FavouriteMediaConnection
import com.example.data.model.FavouriteStaff
import com.example.data.model.MediaCoverImage
import com.example.data.model.MediaNode
import com.example.data.model.MediaTitle
import com.example.data.model.PersonName
import com.example.data.model.StudioNode
import com.example.data.model.UserOverview
import com.example.data.network.GraphQLQueries
import com.example.ui.viewmodel.distinctById
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the profile screen's GraphQL documents against what AniList actually
 * serves.
 *
 * Every assertion here came out of asking the live API rather than reading the
 * schema, which is the only reason the app works: `User.statistics` - the field
 * AniList documents as the replacement for the deprecated one - answers `[]` for
 * every sub-selection that a stats screen needs, while the deprecated `User.stats`
 * answers all of them. A profile screen written from the schema alone would have
 * been a screen full of empty charts.
 */
class UserOverviewQueryTest {

    private val overview = GraphQLQueries.GET_USER_OVERVIEW

    @Test
    fun `asks for the deprecated aggregate stats, because the replacement is empty`() {
        // The assertion is on presence, not absence: this is the one place in the
        // app that reads a field AniList marks for removal, and it is the only
        // source of the status / format / score / genre / tag / year
        // distributions. If this ever goes empty, the Stats tab is the casualty and
        // this test is where somebody finds out why.
        assertTrue(
            "User.stats carries every distribution the Stats tab draws; " +
                "statistics.*.statuses and .genres answer [] on every account tried",
            overview.contains("stats {")
        )
        listOf(
            "activityHistory",
            "animeStatusDistribution",
            "animeScoreDistribution",
            "animeListScores",
            "favouredFormats",
            "favouredYears",
            "favouredGenres",
            "favouredTags"
        ).forEach { field ->
            assertTrue("stats block is missing $field", overview.contains(field))
        }
    }

    @Test
    fun `does not ask for the statistics sub-selections AniList answers with an empty array`() {
        // Each of these is documented, typed, and returns `[]` - checked against
        // accounts with 142 and 258 scored anime and the site's own account. An
        // empty array parses cleanly, so nothing in the app would notice: the
        // screen would just render a chart of zeroes.
        listOf("statuses", "genres", "formats", "releaseYears", "startYears", "lengths", "scores").forEach { field ->
            assertFalse(
                "statistics.$field answers [] and must not be requested",
                overview.contains(field)
            )
        }
    }

    @Test
    fun `asks for the totals that do work on the non-deprecated field`() {
        // `count`, `minutesWatched` and friends are populated on `statistics`, so
        // they are read from there. Asking for them twice would be two sources for
        // one number, which is how the header card and the Stats card end up
        // quoting different totals.
        listOf("episodesWatched", "minutesWatched", "chaptersRead", "volumesRead", "meanScore").forEach { field ->
            assertTrue("statistics block is missing $field", overview.contains(field))
        }
    }

    @Test
    fun `asks for the account dates and the markdown form of the bio`() {
        assertTrue(overview.contains("createdAt"))
        assertTrue(overview.contains("updatedAt"))
        assertTrue(overview.contains("about(asHtml: false)"))
    }

    @Test
    fun `caps each favourites branch at twelve`() {
        // AniList allows 25 and will send all of them. The rows show about four;
        // the rest is image URLs parsed for art that never leaves the screen.
        val perPage = Regex("""perPage:\s*(\d+)""").findAll(overview).map { it.groupValues[1] }.toList()

        assertEquals(5, perPage.size)
        perPage.forEach { assertEquals("12", it) }
    }

    @Test
    fun `is a query, not a mutation, so the repository will coalesce it`() {
        // `AniListRepositoryImpl.isReadOnly` decides this by the document's first
        // keyword. A profile query misfiled as a mutation would never be
        // de-duplicated, which is invisible until two opens in quick succession
        // cost two of AniList's ~30 requests a minute.
        assertTrue(overview.trimStart().startsWith("query"))
    }

    @Test
    fun `the activity feed filters to list updates and selects the union member`() {
        val activity = GraphQLQueries.GET_USER_ACTIVITY

        // Unfiltered, `activities` answers with `MessageActivity` - forum posts by
        // the user - which is not what the feed is for.
        assertTrue(activity.contains("type_in: [ANIME_LIST, MANGA_LIST]"))
        // A union cannot be read by Moshi, so the ListActivity member is selected
        // with an inline fragment; everything else is modelled as unrecognised.
        assertTrue(activity.contains("... on ListActivity"))
        assertTrue(activity.contains("sort: [ID_DESC]"))
    }

    @Test
    fun `followers and following are separate documents`() {
        // `Page` accepts exactly one data field. Asking for both in one document is
        // a GraphQL validation error ("Page query can only accept 1 child field"),
        // not a merged result - checked, not assumed.
        val followers = GraphQLQueries.GET_USER_FOLLOWERS
        val following = GraphQLQueries.GET_USER_FOLLOWING

        assertTrue(followers.contains("followers(userId:"))
        assertFalse(followers.contains("following(userId:"))
        assertTrue(following.contains("following(userId:"))
        assertFalse(following.contains("followers(userId:"))
    }

    @Test
    fun `the social queries take a non-null id`() {
        // `Page.followers(userId: Int!)`. Declaring the variable nullable compiles
        // and fails at runtime with "used in position expecting type Int!".
        assertTrue(GraphQLQueries.GET_USER_FOLLOWERS.contains("Int!"))
        assertTrue(GraphQLQueries.GET_USER_FOLLOWING.contains("Int!"))
    }

    @Test
    fun `the list query gained only country of origin`() {
        // The Country Distribution needs it and AniList exposes no aggregate for it
        // anywhere, so the entry is the only source. Measured on a real 142-entry
        // list: 480,034 bytes without it, 483,990 with - about 0.8%.
        val list = GraphQLQueries.GET_USER_ANIME_LIST
        assertTrue(list.contains("countryOfOrigin"))
        assertTrue(list.contains("episodes"))
        assertTrue(list.contains("startDate"))
    }
}

/**
 * The favourites connections are not de-duplicated by AniList, and the profile's
 * rows are `LazyRow`s keyed on the media id.
 *
 * A `LazyRow` handed the same key twice throws
 * `IllegalArgumentException: Key "79" was already used` while it is laid out. That
 * is a crash on somebody's device, in a screen with no error handling around it,
 * for an account that is entirely ordinary - a live response for one user
 * contained the same anime six times.
 *
 * This exercises the real function the ViewModel applies, not a copy of the rule.
 */
class FavouritesDeduplicationTest {

    @Test
    fun `the same media id repeated is one row, not six`() {
        val nodes = List(6) { mediaNode(79) }

        assertEquals(1, nodes.distinctById().size)
    }

    @Test
    fun `distinct media ids are all kept, in the order AniList sent them`() {
        val nodes = listOf(mediaNode(3), mediaNode(1), mediaNode(2))

        assertEquals(listOf(3, 1, 2), nodes.distinctById().map { it.id })
    }

    @Test
    fun `an interleaved repeat collapses without reordering`() {
        val nodes = listOf(mediaNode(1), mediaNode(2), mediaNode(1), mediaNode(3), mediaNode(2))

        assertEquals(listOf(1, 2, 3), nodes.distinctById().map { it.id })
    }

    @Test
    fun `characters and staff dedupe on their own ids`() {
        // Same helper, four different list types. A `LazyRow` keyed on the id
        // throws on a duplicate in the character and staff rows too.
        val characters = listOf(characterNode(5), characterNode(5), characterNode(6))
        val staff = listOf(staffNode(7), staffNode(7))

        assertEquals(listOf(5, 6), characters.distinctById().map { it.id })
        assertEquals(listOf(7), staff.distinctById().map { it.id })
    }

    @Test
    fun `studios without an id are all kept rather than collapsed into one`() {
        // `StudioNode.id` is nullable and studio rows are rendered from the name
        // rather than keyed on anything. Treating "no id" as one shared bucket
        // would throw away real studios.
        val studios = listOf(StudioNode(id = null, name = "A"), StudioNode(id = null, name = "B"))

        assertEquals(listOf("A", "B"), studios.distinctById().map { it.name })
    }

    @Test
    fun `a user with no favourites at all produces empty rows rather than a crash`() {
        val overview = UserOverview(id = 1, name = "nobody")

        val favourites = overview.favourites

        assertEquals(0, favourites?.anime?.nodes.orEmpty().distinctById().size)
        assertEquals(0, favourites?.manga?.nodes.orEmpty().distinctById().size)
        assertEquals(0, favourites?.characters?.nodes.orEmpty().distinctById().size)
        assertEquals(0, favourites?.staff?.nodes.orEmpty().distinctById().size)
        assertEquals(0, favourites?.studios?.nodes.orEmpty().distinctById().size)
    }

    @Test
    fun `an empty favourites branch stays empty`() {
        val connection = FavouriteMediaConnection(nodes = emptyList())

        assertEquals(0, connection.nodes.orEmpty().distinctById().size)
    }

    private fun mediaNode(id: Int) = MediaNode(
        id = id,
        title = MediaTitle(english = "Show $id"),
        coverImage = MediaCoverImage(large = "https://example.test/$id.png")
    )

    private fun characterNode(id: Int) = FavouriteCharacter(
        id = id,
        name = PersonName(full = "Person $id")
    )

    private fun staffNode(id: Int) = FavouriteStaff(
        id = id,
        name = PersonName(full = "Person $id")
    )
}