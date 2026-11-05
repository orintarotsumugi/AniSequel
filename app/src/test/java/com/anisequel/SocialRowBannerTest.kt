package com.anisequel

import com.anisequel.data.model.ActivityLikesData
import com.anisequel.data.model.GraphQLResponse
import com.anisequel.data.network.FlexibleStringAdapter
import com.anisequel.data.network.GraphQLQueries
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The banner each person chose, all the way from the query to the row behind them.
 *
 * ## What was missing
 *
 * A followers list, a following list and a "Liked by" sheet are all columns of
 * near-identical rows: an avatar and a name. AniList gives every account a cover
 * image, and the rows already know whose they are, but none of the four documents
 * asked for `bannerImage` and `FollowUser` had nowhere to put it even if they had.
 *
 * ## Why both halves are pinned
 *
 * Selecting the field without carrying it is a query that costs a round trip and
 * draws the same list as before. Carrying it without selecting it is a field that
 * is always null, which is the same bug wearing a different hat - and neither one
 * fails a build, a test or a screen.
 */
class SocialRowBannerTest {

    private val moshi = Moshi.Builder()
        .add(String::class.java, FlexibleStringAdapter)
        .build()

    private inline fun <reified T> parse(json: String): T {
        val responseType = Types.newParameterizedType(GraphQLResponse::class.java, T::class.java)
        return moshi.adapter<GraphQLResponse<T>>(responseType).fromJson(json)!!.data!!
    }

    @Test
    fun `every social list selects the banner`() {
        listOf(
            "GET_USER_FOLLOWERS" to GraphQLQueries.GET_USER_FOLLOWERS,
            "GET_USER_FOLLOWING" to GraphQLQueries.GET_USER_FOLLOWING,
            "GET_ACTIVITY_LIKES" to GraphQLQueries.GET_ACTIVITY_LIKES,
            "GET_ACTIVITY_REPLY_LIKERS" to GraphQLQueries.GET_ACTIVITY_REPLY_LIKERS
        ).forEach { (name, document) ->
            assertTrue(
                "$name selects an avatar and a name but not the cover, so the row " +
                    "has nothing to draw behind the person",
                document.contains("bannerImage")
            )
        }
    }

    @Test
    fun `a liker's banner is carried on the person`() {
        val json = """
            {"data":{"Activity":{
              "id":790618779,
              "likeCount":1,
              "likes":[
                {"id":7,"name":"Josh","avatar":{"large":"https://s4.anilist.co/l.png"},"bannerImage":"https://s4.anilist.co/b7.jpg"}
              ]
            }}}
        """.trimIndent()

        val liker = parse<ActivityLikesData>(json).activity!!.likes!!.single()
        assertEquals("https://s4.anilist.co/b7.jpg", liker.bannerImage)
    }

    @Test
    fun `an account with no banner still parses, and the row falls back`() {
        // AniList's default accounts have no cover at all. A missing key must not
        // drop the person from the list, and the rows must not be left transparent.
        val json = """
            {"data":{"Activity":{
              "id":1,"likeCount":1,
              "likes":[{"id":7,"name":"Josh","avatar":{"large":"https://s4.anilist.co/l.png"}}]
            }}}
        """.trimIndent()

        assertNull(parse<ActivityLikesData>(json).activity!!.likes!!.single().bannerImage)

        val rows = listOf(
            "app/src/main/java/com/anisequel/ui/screens/profile/ActivityLikersSheet.kt",
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileSocialPieces.kt"
        )
        for (path in rows) {
            val source = File(findProjectRoot(), path).readText()
            assertTrue(
                "$path draws its person without a banner layer behind them",
                source.contains("PersonBannerBackdrop(")
            )
        }
    }

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }
}
