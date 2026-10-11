package com.example

import com.example.data.model.ActivityLikesData
import com.example.data.model.GraphQLResponse
import com.example.data.network.FlexibleStringAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses the likers response for one activity.
 *
 * The fixture shapes are taken from what AniList serves for `GET_ACTIVITY_LIKES`.
 * The mistakes here are silent: a liker whose `avatar` key is spelled differently
 * decodes to no image, and the sheet draws an initial where a portrait belongs,
 * with no error reaching the screen. `likes` is also the one field on this
 * response that a stale or wrong-union id can leave out entirely, so the empty
 * cases are pinned here rather than discovered as a blank sheet.
 */
class ActivityLikesJsonTest {

    private val moshi = Moshi.Builder()
        .add(String::class.java, FlexibleStringAdapter)
        .build()

    private inline fun <reified T> parse(json: String): T {
        val responseType = Types.newParameterizedType(GraphQLResponse::class.java, T::class.java)
        return moshi.adapter<GraphQLResponse<T>>(responseType).fromJson(json)!!.data!!
    }

    @Test
    fun `reads the likers with their names and avatars`() {
        val json = """
            {"data":{"Activity":{
              "id":790618779,
              "likeCount":2,
              "likes":[
                {"id":7,"name":"Josh","avatar":{"large":"https://s4.anilist.co/l.png","medium":"https://s4.anilist.co/m.png"}},
                {"id":9,"name":"Aster","avatar":{"large":"https://s4.anilist.co/2l.png","medium":"https://s4.anilist.co/2m.png"}}
              ]
            }}}
        """.trimIndent()

        val activity = parse<ActivityLikesData>(json).activity!!
        assertEquals(790618779, activity.id)
        assertEquals(2, activity.likeCount)

        val likers = activity.likes!!
        assertEquals(2, likers.size)
        assertEquals("Josh", likers[0].name)
        assertEquals("https://s4.anilist.co/l.png", likers[0].avatar!!.large)
        assertEquals("Aster", likers[1].name)
        assertEquals(9, likers[1].id)
    }

    @Test
    fun `an absent or null likes list reads as empty`() {
        // `likes` is a plain list, and `Activity(id:)` can resolve to a union
        // member the `ListActivity` fragment does not match - which arrives with
        // no selected fields - or to nothing at all. Both must reduce to "no
        // likers" through the repository's `likes.orEmpty()`, opening the sheet
        // on its empty state instead of throwing.
        val absent = parse<ActivityLikesData>("""{"data":{"Activity":{"id":1,"likeCount":0}}}""")
        assertNull(absent.activity?.likes)
        assertTrue(absent.activity?.likes.orEmpty().isEmpty())

        val missingActivity = parse<ActivityLikesData>("""{"data":{"Activity":null}}""")
        assertTrue(missingActivity.activity?.likes.orEmpty().isEmpty())
    }
}
