package com.anisequel

import com.anisequel.domain.usecase.ContentLimits
import com.anisequel.domain.usecase.ContentLimits.ReplyVerdict
import com.anisequel.ui.screens.profile.DraftEdit
import com.anisequel.ui.screens.profile.insertSnippet
import com.anisequel.ui.screens.profile.isAttachableImageUrl
import com.anisequel.ui.screens.profile.startReplyTo
import com.anisequel.ui.screens.profile.wrapSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composer's two rules that are not drawing: what AniList will accept, and
 * what the format bar does to the text.
 *
 * These live on a plain JVM because both are pure. The three crash-shaped
 * mistakes they exist to prevent - a cursor outside the markers, a reversed drag
 * dropping the selection, an index from before a truncation pointing past the
 * end - are invisible in a screenshot and expensive to catch by hand.
 */
class ContentLimitsTest {

    @Test
    fun `a one-character comment is too short for the API`() {
        // `SaveActivityReply.text` is Min: 2. The composer used to enable on
        // `isNotBlank()`, so this was sent and refused.
        assertEquals(ReplyVerdict.TooShort, ContentLimits.checkReply("a"))
        assertEquals(ReplyVerdict.TooShort, ContentLimits.checkReply("  a  "))
    }

    @Test
    fun `white space is blank rather than short`() {
        assertEquals(ReplyVerdict.Blank, ContentLimits.checkReply(""))
        assertEquals(ReplyVerdict.Blank, ContentLimits.checkReply("   \n "))
    }

    @Test
    fun `two trimmed characters are sendable`() {
        assertEquals(ReplyVerdict.Valid, ContentLimits.checkReply("ok"))
        assertEquals(ReplyVerdict.Valid, ContentLimits.checkReply("  ok  "))
    }

    @Test
    fun `the ceiling is measured on the trimmed text and clips the field`() {
        val atCeiling = "x".repeat(ContentLimits.REPLY_MAX)
        assertEquals(ReplyVerdict.Valid, ContentLimits.checkReply(atCeiling))
        assertEquals(ReplyVerdict.TooLong, ContentLimits.checkReply(atCeiling + "x"))
        // The field clips on every keystroke, so this is the number the counter
        // and the send button both read.
        assertEquals(ContentLimits.REPLY_MAX, ContentLimits.clipReply(atCeiling + "xxx").length)
    }
}

class CommentDraftEditsTest {

    @Test
    fun `bolding a selection keeps the selection over the words`() {
        assertEquals(
            DraftEdit("**hello**", 2, 7),
            wrapSelection("hello", 0, 5, "**")
        )
    }

    @Test
    fun `with no selection the cursor lands between the markers`() {
        // Pressing Bold and then typing is the normal order; a cursor after both
        // markers would put the first keystroke outside the emphasis.
        assertEquals(
            DraftEdit("****", 2, 2),
            wrapSelection("", 0, 0, "**")
        )
        assertEquals(
            DraftEdit("a****b", 3, 3),
            wrapSelection("ab", 1, 1, "**")
        )
    }

    @Test
    fun `a right-to-left drag is still a selection`() {
        // Android reports a backwards drag with end before start. Dropping it
        // would make the button do nothing for half the gestures.
        assertEquals(
            DraftEdit("__hello__", 2, 7),
            wrapSelection("hello", 5, 0, "__")
        )
    }

    @Test
    fun `a selection from a longer draft is clamped, not thrown`() {
        assertEquals(
            DraftEdit("**hi**", 2, 4),
            wrapSelection("hi", -3, 99, "**")
        )
    }

    @Test
    fun `a spoiler is wrapped in AniList's own markers`() {
        // `~!text!~` is what the parser reads back as a spoiler; a composer that
        // wrote anything else would post a comment whose preview disagrees.
        assertEquals(
            DraftEdit("~!x!~", 2, 3),
            wrapSelection("x", 0, 1, "~!", "!~")
        )
    }

    @Test
    fun `an attachment replaces the selection and leaves the cursor after it`() {
        assertEquals(
            DraftEdit("hello ![image](https://x/y.png)", 31, 31),
            insertSnippet("hello world", 6, 11, "![image](https://x/y.png)")
        )
    }

    @Test
    fun `replying adds the mention once and keeps typing at the end`() {
        assertEquals(DraftEdit("@Bild ", 6, 6), startReplyTo("", "Bild"))
        assertEquals(DraftEdit("@Bild nice", 10, 10), startReplyTo("nice", "Bild"))
        assertEquals(
            "An existing mention is not doubled",
            DraftEdit("@Bild nice", 10, 10),
            startReplyTo("@Bild nice", "Bild")
        )
        assertEquals(
            "AniList sends handles with the @ attached in some payloads",
            DraftEdit("@Bild hi", 8, 8),
            startReplyTo("hi", "@Bild")
        )
        assertEquals(
            "A reply with no author cannot produce a mention",
            DraftEdit("hi", 2, 2),
            startReplyTo("hi", "   ")
        )
    }

    @Test
    fun `only absolute http images can be attached`() {
        assertTrue(isAttachableImageUrl("https://s4.anilist.co/file/x.png"))
        assertTrue(isAttachableImageUrl("  http://example.test/a.jpg  "))
        assertFalse(isAttachableImageUrl("https://"))
        assertFalse(isAttachableImageUrl("s4.anilist.co/file/x.png"))
        assertFalse(isAttachableImageUrl("data:image/png;base64,AAAA"))
        assertFalse(isAttachableImageUrl("https://example.test/a b.png"))
    }
}
