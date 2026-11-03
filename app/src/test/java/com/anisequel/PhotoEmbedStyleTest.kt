package com.anisequel

import com.anisequel.domain.usecase.MediaAttachment
import com.anisequel.ui.screens.profile.EmbedStyle
import com.anisequel.ui.screens.profile.replaceEmbedSpan
import com.anisequel.ui.screens.profile.showsPhotoEmbedRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The size and framing a photo is attached at.
 *
 * ## The asymmetry this closes
 *
 * The GIF pane has always had a width row, so a picked animation could go in at 220,
 * at 50%, or as the original. A picked *photo* went in at the site's full width with
 * no control at all - same composer, same `img()` markup, one of the two paths with
 * the commands and the other without. A screenshot of a title card is the image a
 * reader most wants small and behind a lid, and there was no way to ask.
 *
 * The rules pinned here are the two that a UI-only review would miss: `""` really is
 * "Original" all the way down to the markup, and the photo path really does read the
 * row the reader touched.
 */
class PhotoEmbedStyleTest {

    @Test
    fun `the empty width is Original and survives the round trip into markup`() {
        // The chip's spelling of "no width" is the empty string, because the state is
        // a saveable String and `embedMarkdown` already reads anything it cannot parse
        // as "no width". If the two disagree, picking Original leaves every chip in the
        // row looking unchosen - which is exactly how this row behaved before.
        val style = EmbedStyle()
        assertEquals("", style.width)
        assertEquals(MediaAttachment.EmbedFraming.Plain, style.framing)
        assertEquals(
            "img(https://i.ibb.co/x/a.png)",
            MediaAttachment.embedMarkdown("https://i.ibb.co/x/a.png", style.width, style.framing)
        )
    }

    @Test
    fun `a chosen size and framing reach the markup the site reads back`() {
        val sized = EmbedStyle("220", MediaAttachment.EmbedFraming.Spoiler)
        assertEquals(
            "~!img220(https://i.ibb.co/x/a.png)!~",
            MediaAttachment.embedMarkdown("https://i.ibb.co/x/a.png", sized.width, sized.framing)
        )
        val centred = EmbedStyle("100%", MediaAttachment.EmbedFraming.Centred)
        assertEquals(
            "~~~\nimg100%(https://i.ibb.co/x/a.png)\n~~~",
            MediaAttachment.embedMarkdown("https://i.ibb.co/x/a.png", centred.width, centred.framing)
        )
    }

    /**
     * The sheet writes the row's choice into the draft, not a bare `img(url)`.
     *
     * A source scan, because the assertion is about which argument the upload hands to
     * the markup builder, and there is no device here to pick a photo with. Dropping
     * the two arguments compiles - `embedMarkdown` defaults them - and the row keeps
     * looking selected while every photo goes in full width.
     */
    @Test
    fun `the photo upload embeds at the style the reader chose`() {
        val sheet = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/screens/profile/ActivityCommentsSheet.kt"
        ).readText()
        assertTrue(
            "the new-comment draft must embed with photoStyle",
            sheet.contains("MediaAttachment.embedMarkdown(url, photoStyle.width, photoStyle.framing)")
        )
        assertTrue(
            "the edit draft has its own style, so an edit cannot inherit the other field's size",
            sheet.contains("MediaAttachment.embedMarkdown(url, editPhotoStyle.width, editPhotoStyle.framing)")
        )
    }

    /**
     * The row appears with the file, not with the pane.
     *
     * Eight chips above an empty field read as a photo already being attached, and as
     * the editor asking what size to make a file the reader has not picked yet. The
     * rule is the file, and the half that a `hasPhoto` flag would have missed is the
     * deletion: the upload writes `img220(url)` into the draft, the reader can strike
     * it out, and the chips then describe nothing.
     */
    @Test
    fun `the photo commands appear only while the file they describe is in the draft`() {
        val url = "https://i.ibb.co/x/a.png"
        val embed = "look at this\nimg220($url)\nor here"

        assertFalse(showsPhotoEmbedRows(draftText = "", attachedUrl = null, isUploading = false))
        // In flight: the row is up with its "Uploading…" caption, before any URL
        // exists to search for.
        assertTrue(showsPhotoEmbedRows(draftText = "", attachedUrl = null, isUploading = true))
        assertTrue(showsPhotoEmbedRows(draftText = embed, attachedUrl = url, isUploading = false))
        // The reader deleted the embed - or posted, which clears the field.
        assertFalse(
            "a URL that is no longer in the draft has no row describing it",
            showsPhotoEmbedRows(draftText = "look at this", attachedUrl = url, isUploading = false)
        )
        assertFalse(showsPhotoEmbedRows(draftText = "", attachedUrl = url, isUploading = false))
        // The address is still in the text, but it is no longer the picture: a link
        // and a misspelled token are things the parser does not render as this
        // embed, so the chips would be describing nothing.
        assertFalse(
            "a bare address is not the embed the chips describe",
            showsPhotoEmbedRows(draftText = "the file is $url", attachedUrl = url, isUploading = false)
        )
        assertFalse(
            "and neither is a token the parser does not know",
            showsPhotoEmbedRows(draftText = "imgwibble($url)", attachedUrl = url, isUploading = false)
        )
    }

    /**
     * Moving a chip rewrites the photo that is already in the draft.
     *
     * The row appears with the file, so it has to describe *this* file: the upload
     * wrote `img220(url)` with whatever was selected at that moment, and a reader who
     * taps 400 afterwards would otherwise be setting the size of the next photo while
     * looking at this one. The embed is found by its URL, wrapper included, so
     * changing a spoilered image to a plain one does not leave `~!` and `!~` around
     * the new markup.
     */
    @Test
    fun `a chip move restyles the embed in place, wrapper and all`() {
        val url = "https://i.ibb.co/x/a.png"
        assertEquals(
            "look\nimg400($url)\nhere",
            restyle("look\nimg220($url)\nhere", url, EmbedStyle("400", MediaAttachment.EmbedFraming.Plain))
        )
        // Spoiler -> centred: the whole `~!…!~` goes, not just the token inside it.
        assertEquals(
            "head\n~~~\nimg($url)\n~~~\ntail",
            restyle(
                "head\n~!img220($url)!~\ntail",
                url,
                EmbedStyle("", MediaAttachment.EmbedFraming.Centred)
            )
        )
        // Centred -> spoiler, the other direction, with the surrounding text intact.
        assertEquals(
            "head\n~!img100%($url)!~\ntail",
            restyle(
                "head\n~~~\nimg100%($url)\n~~~\ntail",
                url,
                EmbedStyle("100%", MediaAttachment.EmbedFraming.Spoiler)
            )
        )
        // The address was mentioned before it was embedded. The first occurrence is
        // not the embed, and stopping there left the picture at its old size while
        // the chip said otherwise.
        assertEquals(
            "the file is at $url\nimg400($url)",
            restyle(
                "the file is at $url\nimg220($url)",
                url,
                EmbedStyle("400", MediaAttachment.EmbedFraming.Plain)
            )
        )
        // Same shape, with a misspelled token before the real one.
        assertEquals(
            "imgwibble($url)\nimg400($url)",
            restyle(
                "imgwibble($url)\nimg220($url)",
                url,
                EmbedStyle("400", MediaAttachment.EmbedFraming.Plain)
            )
        )
    }

    /**
     * Touching a chip must not move the reader out of the sentence they are typing.
     *
     * The first version put the caret after the rewritten markup unconditionally, so
     * asking for a smaller picture while writing the paragraph *below* it jumped the
     * caret up above that paragraph. The selection is mapped through the replacement
     * now: untouched before the span, shifted when it follows it, and moved to the
     * end of the new markup only when it was inside the span that disappeared.
     */
    @Test
    fun `restyling the embed keeps the selection where the reader left it`() {
        val url = "https://i.ibb.co/x/a.png"
        val text = "head\n~!img220($url)!~\ntail"
        val span = MediaAttachment.embedSpanFor(text, url)!!
        val markup = MediaAttachment.embedMarkdown(url, "400", MediaAttachment.EmbedFraming.Plain)
        val expected = "head\nimg400($url)\ntail"

        // Caret in the trailing word: it moves with that word, not to the embed.
        val after = text.indexOf("tail") + 2
        val afterEdit = replaceEmbedSpan(text, span, markup, after, after)
        assertEquals(expected, afterEdit.text)
        assertEquals(expected.indexOf("tail") + 2, afterEdit.selectionStart)

        // Caret before the embed: the rewrite does not move it.
        val beforeEdit = replaceEmbedSpan(text, span, markup, 2, 2)
        assertEquals(2, beforeEdit.selectionStart)

        // Caret inside the span that is being replaced: it lands after the new markup.
        val inside = text.indexOf(url)
        val insideEdit = replaceEmbedSpan(text, span, markup, inside, inside)
        assertEquals(span.first + markup.length, insideEdit.selectionStart)
    }

    @Test
    fun `a URL that is not an embed is left alone`() {
        val url = "https://i.ibb.co/x/a.png"
        // A link's address, and a bare one: neither is `img(…)`, and rewriting the
        // text around a URL the reader typed themselves is not this row's business.
        assertNull(MediaAttachment.embedSpanFor("[title]($url)", url))
        assertNull(MediaAttachment.embedSpanFor("see $url", url))
        assertNull(MediaAttachment.embedSpanFor("nothing here", url))
        // `imgwibble(url)` is not an image to the parser either, so it is not one here.
        assertNull(MediaAttachment.embedSpanFor("imgwibble($url)", url))
    }

    /** The composer's edit, as the draft would end up. */
    private fun restyle(text: String, url: String, style: EmbedStyle): String? =
        MediaAttachment.embedSpanFor(text, url)?.let { (from, to) ->
            text.replaceRange(
                from until to,
                MediaAttachment.embedMarkdown(url, style.width, style.framing)
            )
        }

    /**
     * The repo root, walked up from wherever the tests were started.
     *
     * A Gradle unit test's working directory is the module (`app/`) in some setups
     * and the repository root in others, so a relative `File("app/src/...")` is a test
     * that passes or fails depending on how it was launched. `findProjectRoot` is what
     * every other source scan in this directory does.
     */
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
