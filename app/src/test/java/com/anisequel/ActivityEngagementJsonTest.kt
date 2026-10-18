package com.anisequel

import com.anisequel.data.model.ActivityDetailData
import com.anisequel.data.model.ActivityFeedData
import com.anisequel.data.model.DeleteActivityReplyData
import com.anisequel.data.model.GraphQLResponse
import com.anisequel.data.model.SaveActivityReplyData
import com.anisequel.data.model.ToggleLikeData
import com.anisequel.data.network.FlexibleStringAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses the engagement responses: a comment thread, a like toggle, a posted
 * reply, a deleted one, and the feed's own like state.
 *
 * The fixture shapes are taken from what AniList actually serves for the new
 * documents, because the mistakes here are silent - a field named slightly
 * differently decodes to null, and the heart renders empty or the thread renders
 * blank without any error reaching the screen.
 */
class ActivityEngagementJsonTest {

    private val moshi = Moshi.Builder()
        .add(String::class.java, FlexibleStringAdapter)
        .build()

    private inline fun <reified T> parse(json: String): T {
        val responseType = Types.newParameterizedType(GraphQLResponse::class.java, T::class.java)
        return moshi.adapter<GraphQLResponse<T>>(responseType).fromJson(json)!!.data!!
    }

    @Test
    fun `reads an activity thread with its replies and authors`() {
        val json = """
            {"data":{"Activity":{
              "id":790618779,
              "isLiked":false,
              "likeCount":8,
              "replyCount":1,
              "replies":[
                {"id":99,"text":"Nice!","likeCount":2,"isLiked":true,"createdAt":1726929500,
                 "user":{"id":7,"name":"Josh","avatar":{"large":"https://s4.anilist.co/l.png","medium":"https://s4.anilist.co/m.png"}}}
              ]
            }}}
        """.trimIndent()

        val activity = parse<ActivityDetailData>(json).activity!!
        assertEquals(790618779, activity.id)
        assertEquals(1, activity.replies!!.size)
        val reply = activity.replies[0]
        assertEquals("Nice!", reply.text)
        assertEquals(2, reply.likeCount)
        assertEquals(true, reply.isLiked)
        assertEquals("Josh", reply.user!!.name)
        assertEquals("https://s4.anilist.co/l.png", reply.user.avatar!!.large)
    }

    @Test
    fun `an unmodelled union member or a missing id is an empty thread`() {
        // `Activity(id:)` returns ActivityUnion and the document fragments only
        // `ListActivity`. A member the fragment did not match comes back as an
        // object with no selected fields, and an id that no longer exists as null.
        // Both must read as "no comments", not as a crash or a blank screen with
        // no explanation - the repository reduces both with `replies.orEmpty()`.
        val unmodelled = parse<ActivityDetailData>("""{"data":{"Activity":{}}}""")
        assertTrue(unmodelled.activity?.replies.orEmpty().isEmpty())

        val missing = parse<ActivityDetailData>("""{"data":{"Activity":null}}""")
        assertTrue(missing.activity?.replies.orEmpty().isEmpty())
    }

    @Test
    fun `reads the like toggle result`() {
        val json = """{"data":{"ToggleLikeV2":{"id":790618779,"isLiked":true,"likeCount":9}}}"""

        val likeable = parse<ToggleLikeData>(json).likeable!!
        assertEquals(true, likeable.isLiked)
        assertEquals(9, likeable.likeCount)
    }

    @Test
    fun `reads the created reply from the save mutation`() {
        val json = """
            {"data":{"SaveActivityReply":{
              "id":100,"text":"Well said","likeCount":0,"isLiked":false,"createdAt":1726930000,
              "user":{"id":7,"name":"Josh","avatar":null}
            }}}
        """.trimIndent()

        val reply = parse<SaveActivityReplyData>(json).reply!!
        assertEquals(100, reply.id)
        assertEquals("Well said", reply.text)
        assertEquals("Josh", reply.user!!.name)
    }

    @Test
    fun `reads the delete confirmation`() {
        // `DeleteActivityReply` returns the shared `Deleted` object: the reply is
        // not echoed back, so the sheet removes it by the id it sent. `false` is a
        // refusal and must stay distinguishable from a parse that never happened.
        val deleted = parse<DeleteActivityReplyData>(
            """{"data":{"DeleteActivityReply":{"deleted":true}}}"""
        )
        assertEquals(true, deleted.deleted)

        val refused = parse<DeleteActivityReplyData>(
            """{"data":{"DeleteActivityReply":{"deleted":false}}}"""
        )
        assertEquals(false, refused.deleted)
    }

    @Test
    fun `the feed carries the viewer's like state and the activity url`() {
        val json = """
            {"data":{"Page":{"activities":[
              {"id":1,"status":"current","isLiked":true,"likeCount":4,"siteUrl":"https://anilist.co/activity/1","media":{"id":10}}
            ]}}}
        """.trimIndent()

        val activity = parse<ActivityFeedData>(json).page!!.activities!!.single()
        assertEquals(true, activity.isLiked)
        assertEquals("https://anilist.co/activity/1", activity.siteUrl)
    }

    @Test
    fun `an activity with no like state parses as unknown rather than unliked`() {
        // The demo feed and a signed-out response omit `isLiked`. Null is "AniList
        // did not answer", which is what hides the heart. If it decoded to false
        // the app would show an empty heart next to a count the user cannot
        // change - a claim about their account that is not true.
        val json = """{"data":{"Page":{"activities":[{"id":1,"status":"current","likeCount":4,"media":{"id":10}}]}}}"""

        val activity = parse<ActivityFeedData>(json).page!!.activities!!.single()
        assertNull(activity.isLiked)
    }
}
