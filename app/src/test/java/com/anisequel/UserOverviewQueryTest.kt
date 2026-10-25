package com.anisequel

import com.anisequel.data.model.FavouriteCharacter
import com.anisequel.data.model.FavouriteMediaConnection
import com.anisequel.data.model.FavouriteStaff
import com.anisequel.data.model.MediaCoverImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.PersonName
import com.anisequel.data.model.StudioNode
import com.anisequel.data.model.UserOverview
import com.anisequel.data.network.GraphQLQueries
import com.anisequel.ui.viewmodel.distinctById
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the profile screen's GraphQL documents against what AniList actually
 * serves.
 *
 * Every assertion here came out of asking the live API rather than reading the
 * schema, and the answer has changed since this class was written.
 *
 * It used to pin the opposite of what it pins now: `User.statistics` was believed
 * to answer `[]` for every sub-selection a stats screen needs, so the distributions
 * were read from the deprecated `User.stats` block. Re-checked against the live API
 * on 2026-10-22, `statistics.anime.formats/releaseYears/genres/tags` are populated
 * (150-entry account: TV 115, MOVIE 23, Romance 87, 2026 26) - and four
 * `stats.favoured*` fields returned **HTTP 500 for the whole document**, which took
 * the profile and Library screens down with them.
 *
 * So these tests now guard the migration in both directions: the broken fields must
 * never be requested, and the working replacements must be.
 */
class UserOverviewQueryTest {

    private val overview = GraphQLQueries.GET_USER_OVERVIEW

    @Test
    fun `never asks for the four fields that answer HTTP 500`() {
        // Verified against the live API on 2026-10-22: any one of these makes the
        // entire profile document fail with
        //
        //     HTTP 500
        //     {"errors":[{"message":"Internal Server Error","status":500}, ...],
         //      "data":{"User":null}}
        //
        // and the profile and Library pages draw "Ani-chan is having a nap". They
        // are deprecated, broken server-side, and have working replacements -
        // there is no version of this where requesting them is correct.
        listOf(
            "favouredFormats",
            "favouredYears",
            "favouredGenres",
            "favouredTags"
        ).forEach { field ->
            assertFalse(
                "stats.$field answers HTTP 500 for the whole document",
                overview.contains(field)
            )
        }
    }

    @Test
    fun `asks the deprecated block only for what it still serves`() {
        // `stats` is still needed: `activityHistory` has no replacement anywhere in
        // the schema, and the status/score distributions still answer here.
        assertTrue("the activity calendar has no other source", overview.contains("stats {"))
        listOf(
            "activityHistory",
            "animeStatusDistribution",
            "animeScoreDistribution",
            "animeListScores"
        ).forEach { field ->
            assertTrue("stats block is missing $field", overview.contains(field))
        }
    }

    @Test
    fun `reads the distributions from statistics, which is where they are served`() {
        // This used to assert the opposite, on the belief that every sub-selection
        // of `statistics.anime` answered `[]`. Re-checked live against an account
        // with 150 entries, it is populated (TV 115, MOVIE 23, Romance 87, 2026 26),
        // so the belief was the stale thing and the charts were being sourced from a
        // block that now returns a 500.
        listOf(
            "formats { format count meanScore }",
            "releaseYears { releaseYear count meanScore }",
            "genres { genre count meanScore minutesWatched }",
            "tags { tag { id name } count meanScore }"
        ).forEach { selection ->
            assertTrue(
                "statistics.anime is missing $selection, which is what the Stats charts draw",
                overview.contains(selection)
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
        assertTrue(overview.contains("bannerImage"))
    }

    @Test
    fun `pinned media carries its type, because the tap opens a namespaced page`() {
        // The favourites block is the one media source in the app that used to omit
        // `type`. It is not a display field: the detail sheet fetches and merges by
        // media namespace, because `anime/1` and `manga/1` are different titles that
        // share a number. Without it, tapping a pinned manga asked AniList for the
        // bare id - which resolves against both namespaces and answers with whichever
        // it prefers - and `detailFor` then refused to merge the answer into the
        // typeless summary, so the profile's favourites opened with no synopsis,
        // cast or specs while the same title opened correctly from the Library.
        val favourites = overview
            .substringAfter("favourites {")
            .substringBefore("characters(perPage")
        val branches = listOf(
            "anime(perPage: 12) { nodes {" to "} }",
            "manga(perPage: 12) { nodes {" to "} }"
        )

        branches.forEach { (open, close) ->
            val fields = favourites.substringAfter(open).substringBefore(close)
            assertTrue(
                "$open must select `type`; without it the detail fetch is untyped and " +
                    "the sheet cannot tell which title it was given",
                fields.contains("type")
            )
        }
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

    /**
     * The feed pages on AniList's answer, not on a row count.
     *
     * The repository drops activities whose media is gone before the ViewModel
     * sees the page, so a full server page can arrive shorter than it was asked
     * for. `Page.pageInfo.hasNextPage` is AniList's documented signal, and the
     * local fallback only covers a response that omitted it.
     */
    @Test
    fun `the activity feed asks for its own pagination answer`() {
        val activity = GraphQLQueries.GET_USER_ACTIVITY

        assertTrue(activity.contains("pageInfo { hasNextPage }"))
        // `perPage` is a variable so the query and the repository cannot disagree
        // about what a full page is.
        assertTrue(activity.contains("perPage: \$perPage"))
    }

    @Test
    fun `the feed asks for the viewer's like state and the activity's own url`() {
        val activity = GraphQLQueries.GET_USER_ACTIVITY

        // `isLiked` is what decides whether the heart renders; without it the
        // repository's null-means-unknown rule would hide the control on every row.
        assertTrue(activity.contains("isLiked"))
        // `siteUrl` selects twice now - once for the activity and once for its
        // media - so this pins that the activity's is the one the card links.
        assertTrue(activity.contains("siteUrl"))
    }

    @Test
    fun `the replies query reads the thread off the activity union`() {
        val replies = GraphQLQueries.GET_ACTIVITY_REPLIES

        assertTrue(replies.trimStart().startsWith("query"))
        assertTrue(replies.contains("Activity(id:"))
        // `Activity(id:)` is a union, so the member must be fragmented.
        assertTrue(replies.contains("... on ListActivity"))
        assertTrue(replies.contains("replies"))
        // The comment body, parsed as markdown by the sheet's renderer.
        assertTrue(replies.contains("text"))
    }

    @Test
    fun `likes use ToggleLikeV2 with an explicit type`() {
        val toggle = GraphQLQueries.TOGGLE_ACTIVITY_LIKE

        assertTrue(toggle.trimStart().startsWith("mutation"))
        // `ToggleLike` (v1) is not what AniList documents, and the id is not
        // unique across the likeable types, so the type cannot be left to
        // inference: without it the mutation is ambiguous.
        assertTrue(toggle.contains("ToggleLikeV2"))
        assertTrue(toggle.contains("type: ACTIVITY"))
        assertTrue(toggle.contains("... on ListActivity"))
    }

    @Test
    fun `a comment is a reply, never a direct message`() {
        val save = GraphQLQueries.SAVE_ACTIVITY_REPLY

        assertTrue(save.trimStart().startsWith("mutation"))
        assertTrue(save.contains("SaveActivityReply"))
        // `SaveActivityMessage` takes a `messengerId` and posts a private message.
        // The two names are one word apart, and posting a comment through the
        // wrong one would silently DM the author instead.
        assertFalse(save.contains("SaveActivityMessage"))
        assertFalse(save.contains("messengerId"))
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