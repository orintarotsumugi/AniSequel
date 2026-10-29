package com.anisequel

import com.anisequel.domain.usecase.ReplyThreader
import com.anisequel.domain.usecase.ReplyThreader.Reply
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mention-only threading the comment sheet draws.
 *
 * These pin the two halves of the choice: a leading `@name` that matches an
 * author already in the thread indents, and everything else does not. The second
 * half is the one a future "improvement" is most likely to break - threading on
 * any mention, not just a resolvable one, would indent replies against people
 * who are not in the thread at all.
 */
class ReplyThreaderTest {

    @Test
    fun `a reply to somebody already in the thread gets a level`() {
        val depths = ReplyThreader.depths(
            listOf(
                Reply(1, "kai", "great update"),
                Reply(2, "rin", "@kai agreed!"),
                Reply(3, "ana", "same here")
            )
        )
        assertEquals(0, depths.getValue(1))
        assertEquals(1, depths.getValue(2))
        assertEquals(0, depths.getValue(3))
    }

    @Test
    fun `a mention of somebody not in the thread stays at root`() {
        // `@tobi` never posted here - a reader answering the activity's author,
        // not the person above them. Indenting it would claim a reply that the
        // thread does not contain.
        val depths = ReplyThreader.depths(
            listOf(
                Reply(1, "kai", "great update"),
                Reply(2, "rin", "@tobi what do you think?")
            )
        )
        assertEquals(0, depths.getValue(2))
    }

    @Test
    fun `a nested mention indents one further and the cap holds`() {
        val depths = ReplyThreader.depths(
            listOf(
                Reply(1, "kai", "root"),
                Reply(2, "rin", "@kai first level"),
                Reply(3, "ana", "@rin second level"),
                Reply(4, "bob", "@ana third level would be too deep"),
                Reply(5, "eve", "@bob and a fifth")
            )
        )
        assertEquals(0, depths.getValue(1))
        assertEquals(1, depths.getValue(2))
        assertEquals(2, depths.getValue(3))
        assertEquals(ReplyThreader.MAX_DEPTH, depths.getValue(4))
        assertEquals(ReplyThreader.MAX_DEPTH, depths.getValue(5))
    }

    @Test
    fun `a mention answers the author's most recent comment`() {
        // `@kai` at the end answers reply 3, which sits at level 1, so the reply
        // lands at level 2 - not at level 1 against the root comment.
        val depths = ReplyThreader.depths(
            listOf(
                Reply(1, "kai", "root"),
                Reply(2, "rin", "@kai level 1"),
                Reply(3, "kai", "@rin level 2"),
                Reply(4, "ana", "@kai answers the level 2 one")
            )
        )
        assertEquals(2, depths.getValue(4))
    }

    @Test
    fun `a reply does not become its own parent`() {
        val depths = ReplyThreader.depths(
            listOf(
                Reply(1, "kai", "@kai to myself"),
                Reply(2, "kai", "still @kai")
            )
        )
        assertEquals(0, depths.getValue(1))
        assertEquals(1, depths.getValue(2))
    }

    @Test
    fun `mentions are matched case insensitively and past leading whitespace`() {
        val depths = ReplyThreader.depths(
            listOf(
                Reply(1, "Kai", "root"),
                Reply(2, "rin", "\n@kai after a newline"),
                // `@KAI` now resolves to reply 2, the most recent comment by Kai,
                // so it threads one deeper than the mention that matched by case.
                Reply(3, "ana", "@KAI after a different case"),
                Reply(4, "bob", "not a mention @kai mid-sentence")
            )
        )
        assertEquals(1, depths.getValue(2))
        assertEquals(2, depths.getValue(3))
        assertEquals(0, depths.getValue(4))
    }

    @Test
    fun `an empty thread threads to nothing`() {
        assertEquals(emptyMap<Int, Int>(), ReplyThreader.depths(emptyList()))
    }
}
