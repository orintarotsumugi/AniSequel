package com.anisequel

import com.anisequel.data.network.GraphQLQueries
import com.anisequel.domain.usecase.ContentLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A new activity is a text activity, and its text has a floor of its own.
 *
 * ## Why the sibling mutations are pinned out
 *
 * `SaveTextActivity`, `SaveActivityReply` and `SaveActivityMessage` are one word
 * apart and all three take `text` or `message`. A new post sent through the reply
 * mutation needs a parent activity it does not have; sent through the message
 * mutation it becomes a private message to somebody. Both are silent: the request
 * succeeds and the wrong thing is written. The document has to name exactly the
 * one field that creates a top-level post.
 *
 * ## Why the character window is here rather than in the composer
 *
 * The floor is a server rule - `SaveTextActivity.text` is documented Min 5, Max
 * 10000 on the live mutation reference - and a composer that enables Post on four
 * characters discovers it by spending a request and showing a server error to a
 * reader who did nothing wrong. `ContentLimits.checkStatus` is the check, so it is
 * what this pins.
 */
class TextActivityTest {

    @Test
    fun `a new activity is text activity, never a reply or a message`() {
        val mutation = GraphQLQueries.SAVE_TEXT_ACTIVITY

        assertTrue(mutation.trimStart().startsWith("mutation SaveTextActivity"))
        assertTrue(mutation.contains("SaveTextActivity(text:"))
        // A reply needs `activityId`; a message needs a `recipientId`. Neither
        // belongs in a top-level post.
        assertFalse(mutation.contains("activityId"))
        assertFalse(mutation.contains("recipientId"))
        assertFalse(mutation.contains("SaveActivityReply"))
        assertFalse(mutation.contains("SaveActivityMessage"))
    }

    @Test
    fun `the created post is selected as a feed row`() {
        // Every field the card draws has to come back in the mutation's answer,
        // or the inserted row is a blank card until a refetch. `text` is what makes
        // it a text post; the counts are what its two buttons print.
        val mutation = GraphQLQueries.SAVE_TEXT_ACTIVITY
        listOf("id", "text", "createdAt", "likeCount", "replyCount", "isLiked", "siteUrl")
            .forEach { field ->
                assertTrue("the mutation must select $field", mutation.contains(field))
            }
    }

    @Test
    fun `a text activity is not accepted before five characters`() {
        // `SaveTextActivity`'s documented floor, not a preference.
        assertEquals(ContentLimits.StatusVerdict.Blank, ContentLimits.checkStatus("   "))
        assertEquals(ContentLimits.StatusVerdict.TooShort, ContentLimits.checkStatus("a"))
        assertEquals(ContentLimits.StatusVerdict.TooShort, ContentLimits.checkStatus("abcd"))
        assertEquals(ContentLimits.StatusVerdict.Valid, ContentLimits.checkStatus("abcde"))
        // Trimmed before measuring: the store sends the trimmed text, so four
        // characters wrapped in spaces are still four characters to AniList.
        assertEquals(ContentLimits.StatusVerdict.TooShort, ContentLimits.checkStatus("  abcd  "))
        assertEquals(ContentLimits.StatusVerdict.Valid, ContentLimits.checkStatus("  abcde  "))
    }

    @Test
    fun `a draft longer than the ceiling is clipped rather than sent`() {
        val long = "x".repeat(ContentLimits.STATUS_MAX + 25)
        assertEquals(ContentLimits.STATUS_MAX, ContentLimits.clipStatus(long).length)
        assertEquals(
            ContentLimits.StatusVerdict.Valid,
            ContentLimits.checkStatus(ContentLimits.clipStatus(long))
        )
    }
}
