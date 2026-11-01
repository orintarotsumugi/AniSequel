package com.anisequel

import com.anisequel.ui.screens.profile.DraftEdit
import com.anisequel.ui.screens.profile.InsertKind
import com.anisequel.ui.screens.profile.embedMarkdown
import com.anisequel.ui.screens.profile.insertBlock
import com.anisequel.ui.screens.profile.isUsableInsert
import com.anisequel.ui.screens.profile.linkMarkdown
import com.anisequel.ui.screens.profile.prefixLines
import com.anisequel.ui.screens.profile.quoteBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composer bar's edits, as text.
 *
 * Every one of these decides where the caret ends up, and the mistakes are
 * off-by-one: a block tag inserted at the end of a sentence is stored as literal
 * markup, a prefix landing on a blank line is a quote mark that quotes nothing.
 */
class CommentMarkupEditsTest {

    @Test
    fun `a block insert gets its own line on both sides`() {
        // `img(url)` is matched only when the whole line is the tag, so an embed
        // dropped beside a word would post as the text `img(...)`.
        val edit = insertBlock("hello", 5, 5, "img(https://x/y.png)")
        assertEquals("hello\nimg(https://x/y.png)", edit.text)
        assertEquals(edit.text.length, edit.selectionStart)

        // Already on a line of its own: no second newline.
        val onOwnLine = insertBlock("hello\n", 6, 6, "img(https://x/y.png)")
        assertEquals("hello\nimg(https://x/y.png)", onOwnLine.text)
    }

    @Test
    fun `a block insert in the middle of the text does not swallow the tail`() {
                val edit = insertBlock("ab", 1, 1, "img(u)")
        assertEquals("a\nimg(u)\nb", edit.text)
        assertEquals(9, edit.selectionStart)
    }

    @Test
    fun `prefixing hits every selected line and leaves blanks alone`() {
        val edit = prefixLines("one\ntwo\nthree", 0, 11, "> ")
        assertEquals("> one\n> two\n> three", edit.text)
        // Three characters of prefix went in ahead of the selection, so the caret
        // ends up three further along than it started and still covers the same
        // three lines.
        assertEquals(18, edit.selectionEnd)

        val blanks = prefixLines("a\n\nb", 0, 3, "> ")
        assertEquals("> a\n\n> b", blanks.text)
    }

    @Test
    fun `a prefix with a bare caret marks the current line`() {
        val edit = prefixLines("abc\ndef", 5, 5, "# ")
        assertEquals("abc\n# def", edit.text)
        assertEquals(7, edit.selectionStart)
    }

    @Test
    fun `a link labels the selection, or the address when there is none`() {
        assertEquals(
            "[watch it](https://youtu.be/x)end",
            linkMarkdown("watch itend", 0, 8, "https://youtu.be/x").text
        )
        assertEquals(
            "[https://a/b](https://a/b)",
            linkMarkdown("", 0, 0, "https://a/b").text
        )
    }

    @Test
    fun `each kind writes the markup the site documents`() {
        assertEquals("img(https://x/y.png)", embedMarkdown(InsertKind.IMAGE, "https://x/y.png"))
        assertEquals("webm(https://x/y.webm)", embedMarkdown(InsertKind.WEBM, "https://x/y.webm"))
        // An id and a full link are the same embed to AniList, so the composer
        // accepts both and the reader is not made to strip a URL by hand.
        assertEquals("youtube(dQw4w9WgXcQ)", embedMarkdown(InsertKind.YOUTUBE, "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("youtube(dQw4w9WgXcQ)", embedMarkdown(InsertKind.YOUTUBE, "dQw4w9WgXcQ"))
        assertEquals("youtube(abc123)", embedMarkdown(InsertKind.YOUTUBE, "https://youtu.be/abc123?t=40"))
    }

    @Test
    fun `a youtube value needs only to be something, everything else needs a url`() {
        assertTrue(isUsableInsert(InsertKind.YOUTUBE, "dQw4w9WgXcQ"))
        assertTrue(isUsableInsert(InsertKind.IMAGE, "https://x/y.png"))
        assertFalse("a relative path is stored as literal text", isUsableInsert(InsertKind.IMAGE, "/y.png"))
        assertFalse(isUsableInsert(InsertKind.WEBM, "data:image/png;base64,AAA"))
        assertFalse(isUsableInsert(InsertKind.LINK, "   "))
    }

    @Test
    fun `a quote prefixes every line and leaves a blank for the answer`() {
        val block = quoteBlock("first line\nsecond line", "Bild")
        assertEquals("Bild wrote\n> first line\n> second line\n\n", block)
    }

    @Test
    fun `a quote does not credit a name the comment already mentions`() {
        // Quoting a reply whose text opens with `@Bild` would say Bild twice: once
        // as prose and once as the mention that threads the reply.
        val block = quoteBlock("@Bild I agree", "Bild")
        assertEquals("> @Bild I agree\n\n", block)
    }

    @Test
    fun `a quote without a known author is still a quote`() {
        assertEquals("> hello\n\n", quoteBlock("hello", null))
        assertEquals("> hello\n\n", quoteBlock("hello", "  "))
    }

    @Test
    fun `an edit is a text and a range, nothing else`() {
        // Guards the shape the composer's `withDraftEdit` clamp depends on.
        val edit = DraftEdit("abc", 1, 2)
        assertEquals(3, edit.text.length)
        assertTrue(edit.selectionStart <= edit.selectionEnd)
    }
}
