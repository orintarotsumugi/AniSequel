package com.example

import com.example.data.model.GraphQLResponse
import com.example.data.model.MediaListCollectionData
import com.example.data.model.UserByNameData
import com.example.data.model.ViewerData
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Proves the JSON layer still works the way the app actually calls it.
 *
 * `NetworkClient` builds a plain `Moshi.Builder()` with no reflective adapter
 * factory and the release build now runs R8, so every model depends on the
 * generated `FooJsonAdapter` being present and on Retrofit being able to resolve
 * the *type argument* of `GraphQLResponse<T>` at runtime. Both are invisible to
 * the compiler: get either one wrong and the app parses nothing, throws at the
 * first screen, and no unit test fails - which is exactly how a minified build
 * ends up broken in release while debug looks fine.
 */
class AniListJsonTest {

    private val moshi = Moshi.Builder().build()

    /** Mirrors how Retrofit's MoshiConverterFactory derives the adapter type. */
    private fun <T> parse(type: Class<T>, json: String): GraphQLResponse<T> {
        val responseType = Types.newParameterizedType(GraphQLResponse::class.java, type)
        val adapter = moshi.adapter<GraphQLResponse<T>>(responseType)
        return adapter.fromJson(json)!!
    }

    @Test
    fun `parses a Viewer response through the generic wrapper`() {
        val json = """
            {
              "data": {
                "Viewer": {
                  "id": 5810535,
                  "name": "Anime",
                  "avatar": { "medium": "https://cdn/medium.jpg", "large": "https://cdn/large.jpg" },
                  "bannerImage": "https://cdn/banner.jpg",
                  "statistics": {
                    "anime": { "count": 476, "episodesWatched": 8123, "minutesWatched": 305400 }
                  }
                }
              }
            }
        """.trimIndent()

        val response = parse(ViewerData::class.java, json)

        assertNull(response.errors)
        val viewer = response.data!!.viewer!!
        assertEquals(5810535, viewer.id)
        assertEquals("Anime", viewer.name)
        assertEquals("https://cdn/large.jpg", viewer.avatar?.large)
        assertEquals(476, viewer.statistics?.anime?.count)
    }

    @Test
    fun `parses a list collection including nested sequel relations`() {
        val json = """
            {
              "data": {
                "MediaListCollection": {
                  "lists": [
                    {
                      "name": "Completed",
                      "status": "COMPLETED",
                      "entries": [
                        {
                          "id": 9001,
                          "status": "COMPLETED",
                          "score": 8,
                          "progress": 26,
                          "media": {
                            "id": 101922,
                            "title": { "english": "Season 1", "romaji": "S1" },
                            "format": "TV",
                            "status": "FINISHED",
                            "episodes": 26,
                            "averageScore": 84,
                            "popularity": 600000,
                            "genres": ["Action"],
                            "coverImage": { "large": "https://cdn/cover.jpg", "color": "#123456" },
                            "startDate": { "year": 2019, "month": 4, "day": 6 },
                            "relations": {
                              "edges": [
                                {
                                  "relationType": "SEQUEL",
                                  "node": {
                                    "id": 142329,
                                    "title": { "english": "Season 2" },
                                    "format": "TV",
                                    "status": "FINISHED",
                                    "episodes": 11,
                                    "averageScore": 88,
                                    "popularity": 340000,
                                    "nextAiringEpisode": { "episode": 5, "airingAt": 1700000000 },
                                    "studios": { "nodes": [ { "id": 43, "name": "ufotable" } ] },
                                    "mediaListEntry": { "id": 1, "status": "PLANNING" }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val collection = parse(MediaListCollectionData::class.java, json)
            .data!!.collection!!
        val media = collection.lists!!.single().entries!!.single().media

        assertEquals(101922, media.id)
        assertEquals("Season 1", media.title?.displayTitle)

        val sequel = media.relations!!.edges!!.single()
        assertEquals("SEQUEL", sequel.relationType)
        assertEquals(142329, sequel.node.id)
        assertEquals("Season 2", sequel.node.title?.displayTitle)
        assertEquals(5, sequel.node.nextAiringEpisode?.episode)
        assertEquals("ufotable", sequel.node.studios?.nodes?.single()?.name)
        assertEquals("PLANNING", sequel.node.mediaListEntry?.status)
    }

    @Test
    fun `parses GraphQL errors instead of pretending the response was empty`() {
        val json = """{"errors":[{"message":"Invalid token","status":401}]}"""

        val response = parse(ViewerData::class.java, json)

        assertNull(response.data)
        assertNotNull(response.errors)
        assertEquals("Invalid token", response.errors!!.single().message)
        assertEquals(401, response.errors!!.single().status)
    }

    @Test
    fun `an unknown user comes back as a null, which the repository turns into a message`() {
        val json = """{"data":{"User":null}}"""

        val user = parse(UserByNameData::class.java, json).data!!.user

        assertNull(user)
    }

    /**
     * The failure that made the app unusable, reproduced exactly.
     *
     * `Media.season` is the `MediaSeason` enum, so AniList answers `"WINTER"`.
     * `MediaNode.season` was declared `Int?`, and Moshi threw:
     *
     *     Expected an int but was WINTER at path $.data.MediaListCollection
     *     .lists[0].entries[1].media.relations.edges[2].node.season
     *
     * It was not one bad entry that failed - the whole list response failed to
     * parse, so the dashboard showed an error screen instead of any sequels.
     * The payload below carries the enum on a relation node, which is where it
     * appeared.
     */
    @Test
    fun `a list whose relations carry an enum season parses`() {
        val json = """
            {
              "data": {
                "MediaListCollection": {
                  "lists": [
                    {
                      "name": "Completed",
                      "status": "COMPLETED",
                      "entries": [
                        {
                          "status": "COMPLETED",
                          "progress": 26,
                          "media": {
                            "id": 101922,
                            "title": { "english": "Season 1" },
                            "episodes": 26,
                            "relations": {
                              "edges": [
                                { "relationType": "SIDE_STORY", "node": { "id": 1, "season": "WINTER", "seasonYear": 2019 } },
                                { "relationType": "SIDE_STORY", "node": { "id": 2, "season": "SPRING", "seasonYear": 2020 } },
                                { "relationType": "SEQUEL", "node": { "id": 3, "season": "FALL", "seasonYear": 2023 } }
                              ]
                            }
                          }
                        },
                        {
                          "status": "COMPLETED",
                          "media": {
                            "id": 102000,
                            "title": { "english": "Another Show" },
                            "episodes": 12,
                            "relations": {
                              "edges": [
                                { "relationType": "SEQUEL", "node": { "id": 4, "season": null, "seasonYear": null } }
                              ]
                            }
                          }
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val entries = parse(MediaListCollectionData::class.java, json)
            .data!!.collection!!.lists!!.single().entries!!

        assertEquals(2, entries.size)

        val edges = entries[0].media.relations!!.edges!!
        assertEquals(listOf("WINTER", "SPRING", "FALL"), edges.map { it.node.season })
        assertEquals(listOf(2019, 2020, 2023), edges.map { it.node.seasonYear })

        // An entry with no airing season is normal, not an error.
        val unknown = entries[1].media.relations!!.edges!!.single().node
        assertNull(unknown.season)
        assertNull(unknown.seasonYear)
    }
}