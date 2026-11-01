package com.anisequel

import com.anisequel.domain.usecase.CommentGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which comments a chat view may draw without a header of their own.
 *
 * A merged row claims "this is the same person, still speaking". Getting that
 * wrong hides who said something in a conversation the reader is trying to follow,
 * which is worse than a repeated avatar - so the cases here are the ones where a
 * simpler rule would have merged wrongly.
 */
class CommentGroupingTest {

    private fun row(id: Int, author: Int?, at: Int?, depth: Int = 0) =
        CommentGrouping.Row(id = id, authorId = author, createdAt = at, depth = depth)

    @Test
    fun `the first comment never continues anything`() {
        assertTrue(
            CommentGrouping.continuationIds(listOf(row(1, 7, 1_000))).isEmpty()
        )
        assertTrue(CommentGrouping.continuationIds(emptyList()).isEmpty())
    }

    @Test
    fun `the same author again inside the window continues`() {
        assertEquals(
            setOf(2),
            CommentGrouping.continuationIds(
                listOf(row(1, 7, 1_000), row(2, 7, 1_060))
            )
        )
    }

    @Test
    fun `another author in between breaks the run`() {
        assertEquals(
            setOf(2, 4),
            CommentGrouping.continuationIds(
                listOf(row(1, 7, 1_000), row(2, 7, 1_020), row(3, 8, 1_030), row(4, 8, 1_040))
            )
        )
    }

    @Test
    fun `the same author an hour later starts a new turn`() {
        // Without a window, one person's next-day reply would be drawn as if it
        // were still the message before it.
        assertEquals(
            emptySet<Int>(),
            CommentGrouping.continuationIds(
                listOf(row(1, 7, 1_000), row(2, 7, 1_000 + 3_600))
            )
        )
    }

    @Test
    fun `a missing timestamp or a missing author keeps the header`() {
        // AniList can answer a deleted or blocked account without a `user`, and a
        // row with an unknown speaker is not evidence that the speaker is the same.
        assertEquals(
            emptySet<Int>(),
            CommentGrouping.continuationIds(listOf(row(1, null, 1_000), row(2, null, 1_010)))
        )
        assertEquals(
            emptySet<Int>(),
            CommentGrouping.continuationIds(listOf(row(1, 7, null), row(2, 7, 1_010)))
        )
    }

    @Test
    fun `a different thread depth is not a continuation`() {
        // ReplyThreader indents an answer under what it answers. Merging across the
        // indent would draw somebody's answer as if it went on with the wrong
        // person's message.
        assertEquals(
            emptySet<Int>(),
            CommentGrouping.continuationIds(
                listOf(row(1, 7, 1_000, depth = 0), row(2, 7, 1_010, depth = 1))
            )
        )
    }

    @Test
    fun `a comment older than the one above it keeps its header`() {
        // A list rebuilt from two pages can briefly hold rows out of order. A
        // negative gap is not "seconds later", and hiding the header there would
        // claim a speaker that the timestamps do not support.
        assertEquals(
            emptySet<Int>(),
            CommentGrouping.continuationIds(listOf(row(1, 7, 2_000), row(2, 7, 1_000)))
        )
    }
}
