package com.anisequel

import com.anisequel.data.network.GraphQLQueries
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two reply documents chat view depends on, read as text.
 *
 * These are assertions about a query string rather than about behaviour, and that
 * is deliberate: both were shipped failures in this app's history. A reply document
 * that fragments only `ListActivity` answers a text post's id with an empty object,
 * which is not an error - it is an open sheet saying "No comments yet" over a post
 * that has hundreds of them. Nothing else in the build would notice.
 */
class CommentWindowQueryTest {

    private val window = GraphQLQueries.GET_ACTIVITY_REPLY_WINDOW

    @Test
    fun `the window document fragments both feed members the sheet can open`() {
        assertTrue(window.contains("... on ListActivity"))
        assertTrue("a text post must not answer an empty thread", window.contains("... on TextActivity"))
    }

    @Test
    fun `the window document takes a page and a size`() {
        // The `${'$'}` form because a GraphQL variable is a dollar sign in a Kotlin
        // string, and an unescaped one here would interpolate a test property
        // instead of matching the document.
        assertTrue(window.contains("query GetActivityReplyWindow(${'$'}activityId: Int, ${'$'}page: Int, ${'$'}perPage: Int)"))
        assertTrue(window.contains("Page(page: ${'$'}page, perPage: ${'$'}perPage)"))
        assertTrue(
            "replies are asked for by the activity, not by the page",
            window.contains("activityReplies(activityId: ${'$'}activityId)")
        )
        // The count that places the page has to travel with the page: without it
        // the sheet cannot know whether the rows it holds are the whole thread.
        assertTrue(window.contains("replyCount"))
    }

    @Test
    fun `the window document is a query and not a mutation`() {
        // Reads are coalesced and mutations must never be, on the same client.
        // A document mislabelled here would let two taps on Older share one
        // response, or a post be silently deduplicated.
        assertTrue(window.trimStart().startsWith("query"))
        assertFalse(GraphQLQueries.GET_ACTIVITY_REPLY_LIKERS.contains("mutation"))
    }

    @Test
    fun `the reply likers document reads one comment`() {
        val likers = GraphQLQueries.GET_ACTIVITY_REPLY_LIKERS
        assertTrue(likers.contains("ActivityReply(id: ${'$'}replyId)"))
        assertTrue(likers.contains("likes"))
        assertTrue(likers.contains("avatar { large medium }"))
    }

    @Test
    fun `the window selects the fields the row draws`() {
        listOf("text", "likeCount", "isLiked", "createdAt", "user").forEach { field ->
            assertTrue("$field must be selected for a comment row", window.contains(field))
        }
    }

}
