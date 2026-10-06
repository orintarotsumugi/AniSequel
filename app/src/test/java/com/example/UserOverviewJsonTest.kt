package com.example

import com.example.data.model.ActivityFeedData
import com.example.data.model.FollowersData
import com.example.data.model.GraphQLResponse
import com.example.data.model.UserOverviewData
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses real captured AniList responses for the profile screen.
 *
 * Three things this is here for that a hand-written fixture would not have caught,
 * each of which is a silent wrong answer rather than a crash:
 *
 *  1. **`alternative` is sometimes `[]` and sometimes a list of seven.** A model
 *     typed as `String` where the server sends an array throws a
 *     `JsonDataException` out of the only call that loads the profile, so the
 *     screen would fail for exactly the people with the most-pinned characters.
 *  2. **The favourites connection is not de-duplicated.** A captured account
 *     returned the same media six times in one response. That is de-duplicated in
 *     `UserOverviewViewModel` because the rows are keyed on the id; this asserts
 *     the *parse* stays faithful, so the dedupe remains a deliberate step.
 *  3. **An activity's `status` is lowercase** - `"completed"`, not `"COMPLETED"` -
 *     unlike every other status in the API. Compared against `"COMPLETED"` the
 *     feed would label every completed anime "Completed" by way of the `else`
 *     branch and would never show "Watched" at all.
 *
 * `NetworkClient` builds a plain `Moshi.Builder()` with no reflective adapter
 * factory, and release runs R8, so this also proves the generated adapters survive
 * - which the compiler cannot check and a minified build discovers at the first
 * screen.
 */
class UserOverviewJsonTest {

    private val moshi = Moshi.Builder().build()

    private inline fun <reified T> parse(json: String): T {
        val responseType = Types.newParameterizedType(GraphQLResponse::class.java, T::class.java)
        return moshi.adapter<GraphQLResponse<T>>(responseType).fromJson(json)!!.data!!
    }

    /**
     * Trimmed from a live `GET_USER_OVERVIEW` response, keeping everything
     * load-bearing: an empty manga branch, an `alternative` of `[]`, one of seven
     * entries, a null english title, a repeated media id, and the `stats` block
     * with both a populated and an empty distribution.
     */
    private val capturedOverview = """
        {
          "data": {
            "User": {
              "id": 2,
              "name": "matchai",
              "about": "I play [visual novels](https://vndb.org/u57774) instead of watching anime these days.",
              "siteUrl": "https://anilist.co/user/2",
              "avatar": {
                "medium": "https://s4.anilist.co/avatar/medium/b2.png",
                "large": "https://s4.anilist.co/avatar/large/b2.png"
              },
              "createdAt": 1397079561,
              "updatedAt": 1788149467,
              "statistics": {
                "anime": { "count": 258, "episodesWatched": 3152, "minutesWatched": 78350, "meanScore": 76.41 },
                "manga": { "count": 0, "chaptersRead": 0, "volumesRead": 0, "meanScore": 0 }
              },
              "stats": {
                "watchedTime": 79918,
                "chaptersRead": 0,
                "activityHistory": [
                  { "date": 1775430000, "amount": 2, "level": 1 },
                  { "date": 1775948400, "amount": 8, "level": 5 }
                ],
                "animeStatusDistribution": [
                  { "status": "CURRENT", "amount": 8 },
                  { "status": "PLANNING", "amount": 61 },
                  { "status": "COMPLETED", "amount": 202 },
                  { "status": "DROPPED", "amount": 23 },
                  { "status": "PAUSED", "amount": 3 }
                ],
                "mangaStatusDistribution": [],
                "animeScoreDistribution": [
                  { "score": 40, "amount": 2 },
                  { "score": 100, "amount": 7 }
                ],
                "animeListScores": { "meanScore": 74, "standardDeviation": 17 },
                "mangaListScores": null,
                "favouredFormats": [
                  { "format": "TV", "amount": 121 },
                  { "format": "MOVIE", "amount": 42 }
                ],
                "favouredYears": [ { "year": 2014, "amount": 6, "meanScore": 74 } ],
                "favouredGenres": [ { "genre": "Drama", "amount": 59, "meanScore": 75, "timeWatched": 15064 } ],
                "favouredTags": [
                  { "tag": { "id": 1, "name": "Male Protagonist" }, "amount": 12, "meanScore": 70 }
                ]
              },
              "favourites": {
                "anime": {
                  "nodes": [
                    {
                      "id": 10087,
                      "title": { "english": "Fate/Zero", "romaji": "Fate/Zero", "native": "Fate/Zero" },
                      "format": "TV",
                      "status": "FINISHED",
                      "averageScore": 81,
                      "coverImage": { "large": "https://s4.anilist.co/bx10087.png" },
                      "siteUrl": "https://anilist.co/anime/10087"
                    },
                    {
                      "id": 79,
                      "title": { "english": null, "romaji": "SHUFFLE!", "native": "SHUFFLE!" },
                      "format": "TV",
                      "status": "FINISHED",
                      "averageScore": 65,
                      "coverImage": { "large": "https://s4.anilist.co/nx79.png" },
                      "siteUrl": "https://anilist.co/anime/79"
                    },
                    {
                      "id": 79,
                      "title": { "english": null, "romaji": "SHUFFLE!", "native": "SHUFFLE!" },
                      "format": "TV",
                      "status": "FINISHED",
                      "averageScore": 65,
                      "coverImage": { "large": "https://s4.anilist.co/nx79.png" },
                      "siteUrl": "https://anilist.co/anime/79"
                    }
                  ]
                },
                "manga": { "nodes": [] },
                "characters": {
                  "nodes": [
                    {
                      "id": 120581,
                      "name": { "full": "Kameoka", "native": "龜岡", "alternative": [] },
                      "image": { "large": "https://s4.anilist.co/120581.png" },
                      "favourites": 1,
                      "siteUrl": "https://anilist.co/character/120581"
                    },
                    {
                      "id": 34470,
                      "name": {
                        "full": "Kurisu Makise",
                        "native": "牧瀬紅莉栖",
                        "alternative": ["Christina", "Assistant", "American Virgin", "Perverted Genius Girl", "Celeb 17", "The Zombie", "Kuu-meow"]
                      },
                      "image": { "large": "https://s4.anilist.co/34470.png" },
                      "favourites": 22354,
                      "siteUrl": "https://anilist.co/character/34470"
                    }
                  ]
                },
                "staff": {
                  "nodes": [
                    {
                      "id": 100077,
                      "name": { "full": "Yuki Kajiura", "native": "梶浦由記", "alternative": ["かじうら ゆき"] },
                      "image": { "large": "https://s4.anilist.co/100077.jpg" },
                      "primaryOccupations": ["Composer", "Lyricist"],
                      "favourites": 4368,
                      "siteUrl": "https://anilist.co/staff/100077"
                    }
                  ]
                },
                "studios": {
                  "nodes": [
                    { "id": 43, "name": "ufotable", "isAnimationStudio": true, "siteUrl": "https://anilist.co/studio/43" }
                  ]
                }
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `reads the identity, the bio, the dates and the site url`() {
        val overview = parse<UserOverviewData>(capturedOverview).user!!

        assertEquals(2, overview.id)
        assertEquals("matchai", overview.name)
        assertEquals("https://anilist.co/user/2", overview.siteUrl)
        assertTrue(overview.about!!.contains("visual novels"))
        assertEquals(1397079561, overview.createdAt)
        assertEquals(1788149467, overview.updatedAt)
    }

    @Test
    fun `reads the populated half of the statistics and leaves the manga zeros alone`() {
        val statistics = parse<UserOverviewData>(capturedOverview).user!!.statistics!!

        assertEquals(258, statistics.anime!!.count)
        assertEquals(78350L, statistics.anime!!.minutesWatched)
        // AniList sends a Float; it has to land in a Double without being lost.
        assertEquals(76.41, statistics.anime!!.meanScore!!, 0.001)
        // Genuinely zero - a person with no manga, not a failed request.
        assertEquals(0, statistics.manga!!.count)
    }

    @Test
    fun `reads the deprecated stats block, which is where the distributions live`() {
        val stats = parse<UserOverviewData>(capturedOverview).user!!.stats!!

        assertEquals(79918, stats.watchedTime)
        assertEquals(2, stats.activityHistory!!.size)
        assertEquals(1, stats.activityHistory!![0].level)
        assertEquals(5, stats.activityHistory!![1].level)
        assertEquals(5, stats.animeStatusDistribution!!.size)
        assertEquals(202, stats.animeStatusDistribution!!.first { it.status == "COMPLETED" }.amount)
        assertEquals(74, stats.animeListScores!!.meanScore)
        assertEquals(17, stats.animeListScores!!.standardDeviation)
        assertEquals(121, stats.favouredFormats!!.first().amount)
        assertEquals(2014, stats.favouredYears!!.first().year)
        assertEquals("Drama", stats.favouredGenres!!.first().genre)
        // Tags nest a MediaTag object, not a name string.
        assertEquals("Male Protagonist", stats.favouredTags!!.first().tag!!.name)
    }

    @Test
    fun `an empty distribution parses to an empty list rather than a failure`() {
        // Captured: this account has no manga, and AniList sent `[]`. A model that
        // treated it as required would throw here.
        val stats = parse<UserOverviewData>(capturedOverview).user!!.stats!!

        assertEquals(0, stats.mangaStatusDistribution!!.size)
        assertNull(stats.mangaListScores)
    }

    @Test
    fun `reads the three media fields the card draws`() {
        val nodes = parse<UserOverviewData>(capturedOverview).user!!.favourites!!.anime!!.nodes!!

        assertEquals("Fate/Zero", nodes[0].title!!.displayTitle)
        assertEquals("TV", nodes[0].format)
        assertEquals(81, nodes[0].averageScore)
        assertEquals("https://s4.anilist.co/bx10087.png", nodes[0].coverImage!!.bestUrl)
    }

    @Test
    fun `falls back through the titles when english is null`() {
        val shuffle = parse<UserOverviewData>(capturedOverview).user!!.favourites!!.anime!!.nodes!![1]

        assertNull(shuffle.title!!.english)
        assertEquals("SHUFFLE!", shuffle.title!!.displayTitle)
    }

    @Test
    fun `keeps repeated media ids rather than collapsing them`() {
        val nodes = parse<UserOverviewData>(capturedOverview).user!!.favourites!!.anime!!.nodes!!

        assertEquals(listOf(10087, 79, 79), nodes.map { it.id })
    }

    @Test
    fun `an empty alternative array is not the same as a missing name`() {
        val kameoka = parse<UserOverviewData>(capturedOverview).user!!.favourites!!.characters!!.nodes!![0]

        assertEquals("Kameoka", kameoka.name!!.displayName)
        assertEquals(1, kameoka.favourites)
    }

    @Test
    fun `reads a seven-entry alternative array without throwing`() {
        // The failure this pins: `alternative` typed as a String. Moshi throws
        // `Expected a string but was BEGIN_ARRAY`, and that exception leaves the
        // only call that loads a profile.
        val kurisu = parse<UserOverviewData>(capturedOverview).user!!.favourites!!.characters!!.nodes!![1]

        assertEquals(7, kurisu.name!!.alternative!!.size)
        assertEquals("Christina", kurisu.name!!.alternative!![0])
    }

    @Test
    fun `reads staff occupations and takes the first as the caption`() {
        val kajiura = parse<UserOverviewData>(capturedOverview).user!!.favourites!!.staff!!.nodes!![0]

        assertEquals("Composer", kajiura.occupation)
        assertEquals(listOf("Composer", "Lyricist"), kajiura.primaryOccupations)
    }

    @Test
    fun `a user with no favourites at all parses to empty branches`() {
        val json = """{"data":{"User":{"id":9,"name":"empty","statistics":{"anime":{"count":0}}}}}"""

        val overview = parse<UserOverviewData>(json).user!!

        assertEquals("empty", overview.name)
        assertNull(overview.favourites)
        assertNull(overview.avatar)
        assertNull(overview.createdAt)
        assertNull(overview.stats)
    }

    @Test
    fun `the generated adapter survives without a reflective factory`() {
        // The release build ships R8 with no `androidx.compose` keep rules and no
        // reflective Moshi adapter, so this is the assertion that matters for the
        // minified APK specifically.
        assertNotNull(moshi.adapter(UserOverviewData::class.java))
    }
}

/**
 * The activity feed and the two social lists, from captured responses.
 *
 * The union handling is the point. `Page.activities` answers `[ActivityUnion]`
 * and Moshi cannot discriminate a union, so the model is the `ListActivity` member
 * alone and anything that is not one has to be recognisable and droppable.
 */
class UserActivityJsonTest {

    private val moshi = Moshi.Builder().build()

    private inline fun <reified T> parse(json: String): T {
        val responseType = Types.newParameterizedType(GraphQLResponse::class.java, T::class.java)
        return moshi.adapter<GraphQLResponse<T>>(responseType).fromJson(json)!!.data!!
    }

    /** Captured, including the `__typename` field the inline fragment leaves behind. */
    private val capturedFeed = """
        {
          "data": {
            "Page": {
              "activities": [
                {
                  "__typename": "ListActivity",
                  "id": 790618779,
                  "status": "completed",
                  "progress": null,
                  "createdAt": 1726929411,
                  "likeCount": 8,
                  "replyCount": 0,
                  "media": {
                    "id": 100465,
                    "type": "ANIME",
                    "format": "MOVIE",
                    "episodes": 1,
                    "title": { "english": "Attack on Titan: The Roar of Awakening", "romaji": "Shingeki no Kyojin" },
                    "coverImage": { "large": "https://s4.anilist.co/bx100465.png", "color": "#4a90d9" },
                    "siteUrl": "https://anilist.co/anime/100465"
                  }
                },
                {
                  "__typename": "ListActivity",
                  "id": 790618699,
                  "status": "current",
                  "progress": 7,
                  "createdAt": 1726929396,
                  "likeCount": 6,
                  "replyCount": 1,
                  "media": {
                    "id": 20692,
                    "type": "ANIME",
                    "format": "MOVIE",
                    "episodes": 1,
                    "title": { "english": "Attack on Titan Part II", "romaji": "Shingeki no Kyojin Kouhen" },
                    "coverImage": { "large": "https://s4.anilist.co/bx20692.png" },
                    "siteUrl": "https://anilist.co/anime/20692"
                  }
                },
                {
                  "__typename": "MessageActivity",
                  "id": 790600000,
                  "text": "a forum post that slipped past the type filter"
                }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun `reads a list activity with its media, likes and replies`() {
        val activity = parse<ActivityFeedData>(capturedFeed).page!!.activities!![0]

        assertEquals(790618779, activity.id)
        assertEquals("completed", activity.status)
        assertEquals(8, activity.likeCount)
        assertEquals(0, activity.replyCount)
        assertEquals(1726929411, activity.createdAt)
        assertEquals("ANIME", activity.media!!.type)
        assertEquals(1, activity.media!!.episodes)
        assertEquals("https://s4.anilist.co/bx100465.png", activity.media!!.coverImage!!.large)
    }

    @Test
    fun `the union typename field is ignored rather than failing the parse`() {
        val activities = parse<ActivityFeedData>(capturedFeed).page!!.activities!!

        assertEquals(3, activities.size)
    }

    @Test
    fun `an activity with no media and no status is recognisable as unmodelled`() {
        // A forum post or a site message parses into the ListActivity shape with
        // every field absent. The repository drops these rather than rendering a
        // blank card.
        val activities = parse<ActivityFeedData>(capturedFeed).page!!.activities!!
        val message = activities[2]

        assertTrue(message.isUnrecognised)
        assertFalse(activities[0].isUnrecognised)
        assertFalse(activities[1].isUnrecognised)
    }

    @Test
    fun `the feed's status is lowercase, unlike every other status in the API`() {
        // Compared against "COMPLETED" this would fall through to the else branch
        // and the feed would never say "Watched" at all.
        val activities = parse<ActivityFeedData>(capturedFeed).page!!.activities!!

        assertEquals("Completed", activities[0].displayStatus)
        // The captured item is "current", not "planning" - "Plans to watch" is
        // PLANNING's label, and asserting it here would have passed for the wrong
        // reason if the fixture had been changed underneath.
        assertEquals("Watched", activities[1].displayStatus)
        assertEquals("Watched", activities[1].displayStatusFor(isManga = false))
        // The same CURRENT status means something else for manga.
        assertEquals("Read", activities[1].displayStatusFor(isManga = true))
    }

    @Test
    fun `an unknown status still gets a readable label rather than a blank pill`() {
        val activity = parse<ActivityFeedData>(
            """{"data":{"Page":{"activities":[{"id":1,"status":"repeating","createdAt":1,"media":{"id":1}}]}}}"""
        ).page!!.activities!!.single()

        assertEquals("Rewatching", activity.displayStatus)
    }

    @Test
    fun `followers parse with the id and name the Social tab navigates by`() {
        val json = """
            {"data":{"Page":{"followers":[
              {"id":1,"name":"Josh","avatar":{"medium":"https://s4.anilist.co/b1.png"}},
              {"id":102,"name":"Alexiz","avatar":null}
            ]}}}
        """.trimIndent()

        val followers = parse<FollowersData>(json).page!!.followers!!

        assertEquals(listOf(1, 102), followers.map { it.id })
        assertEquals(listOf("Josh", "Alexiz"), followers.map { it.name })
        assertNull(followers[1].avatar)
    }

    @Test
    fun `an account with no followers is an empty list rather than a null`() {
        val json = """{"data":{"Page":{"followers":[]}}}"""

        assertEquals(0, parse<FollowersData>(json).page!!.followers!!.size)
    }
}