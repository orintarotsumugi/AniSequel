package com.anisequel

import com.anisequel.domain.usecase.MediaAttachment
import com.anisequel.ui.screens.profile.EmbedStyle
import org.junit.Assert.assertEquals
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
