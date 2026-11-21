package com.anisequel

import com.anisequel.ui.components.novamarkdown.NovaAlign
import com.anisequel.ui.components.novamarkdown.NovaBlock
import com.anisequel.ui.components.novamarkdown.NovaInline
import com.anisequel.ui.components.novamarkdown.NovaMarkdown
import com.anisequel.ui.components.novamarkdown.novaPlainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The media half of NovaMarkdown: the four ways AniList writes a picture, the
 * two embed shorthands, and the preview cards that replace bare entity links.
 */
class NovaMarkdownMediaTest {

    // ---- images ---------------------------------------------------------------

    @Test
    fun `a standalone markdown image is a block image`() {
        val image = NovaMarkdown.parse("![cover](https://x/a.png)").single() as NovaBlock.Image

        assertEquals("https://x/a.png", image.url)
        assertEquals("cover", image.alt)
        assertNull(image.width)
    }

    @Test
    fun `an anilist sized image keeps its width`() {
        val fixed = NovaMarkdown.parse("img220(https://x/a.png)").single() as NovaBlock.Image
        val percent = NovaMarkdown.parse("img100%(https://x/a.png)").single() as NovaBlock.Image

        assertEquals("220", fixed.width)
        assertEquals("100%", percent.width)
    }

    @Test
    fun `a linked image keeps the picture and the target`() {
        val image = NovaMarkdown
            .parse("[img(https://x/a.png)](https://anilist.co/anime/1)")
            .single() as NovaBlock.Image

        assertEquals("https://x/a.png", image.url)
        assertEquals("https://anilist.co/anime/1", image.targetUrl)
    }

    @Test
    fun `a bare image url is an image embed`() {
        assertTrue(NovaMarkdown.parse("https://x/a.gif").single() is NovaBlock.Image)
        assertTrue(NovaMarkdown.parse("(https://x/a.webp)").single() is NovaBlock.Image)
    }

    @Test
    fun `a bare non-image url is a link paragraph, not an image`() {
        val blocks = NovaMarkdown.parse("https://example.com/article")

        assertTrue(blocks.single() is NovaBlock.Paragraph)
    }

    @Test
    fun `an inline image survives a sentence and keeps its size`() {
        val paragraph = NovaMarkdown.parse("before <img width='220' src='https://x/a.png'> after")
            .single() as NovaBlock.Paragraph
        val image = paragraph.inlines.filterIsInstance<NovaInline.Image>().single()

        assertEquals("https://x/a.png", image.url)
        assertEquals("220", image.width)
    }

    @Test
    fun `an image inside a link label stays an image`() {
        val link = NovaMarkdown
            .parseInlines("[![cover](https://x/a.png)](https://anilist.co/anime/1)")
            .single() as NovaInline.Link
        val image = link.children.single() as NovaInline.Image

        assertEquals("https://x/a.png", image.url)
        assertEquals("https://anilist.co/anime/1", link.url)
    }

    @Test
    fun `an image block carries its alignment`() {
        val image = NovaMarkdown.parse("<center>img(https://x/a.png)</center>")
            .single() as NovaBlock.Image

        assertEquals(NovaAlign.Center, image.align)
    }

    // ---- video ----------------------------------------------------------------

    @Test
    fun `youtube shorthand becomes a watch url`() {
        val video = NovaMarkdown.parse("youtube(abc1234)").single() as NovaBlock.Video

        assertEquals("https://www.youtube.com/watch?v=abc1234", video.url)
        assertTrue(video.youtube)
    }

    @Test
    fun `a full youtube url is recognised`() {
        val video = NovaMarkdown.parse("https://www.youtube.com/watch?v=abc1234")
            .single() as NovaBlock.Video

        assertTrue(video.youtube)
    }

    @Test
    fun `webm shorthand becomes a clip card`() {
        val video = NovaMarkdown.parse("webm(https://x/a.webm)").single() as NovaBlock.Video

        assertEquals("https://x/a.webm", video.url)
        assertEquals(false, video.youtube)
    }

    @Test
    fun `a parenthesised video url is an embed`() {
        assertTrue(NovaMarkdown.parse("(https://x/a.mp4)").single() is NovaBlock.Video)
    }

    // ---- previews -------------------------------------------------------------

    @Test
    fun `a bare entity url becomes a preview card`() {
        val preview = NovaMarkdown.parse("https://anilist.co/anime/128893")
            .single() as NovaBlock.Preview

        assertEquals("https://anilist.co/anime/128893", preview.url)
        assertEquals("ANIME", preview.type)
        assertEquals("128893", preview.id)
    }

    @Test
    fun `a labelled entity link becomes a preview card with its label`() {
        val preview = NovaMarkdown
            .parse("[Spike](https://anilist.co/character/1)")
            .single() as NovaBlock.Preview

        assertEquals("Spike", preview.title)
        assertEquals("CHARACTER", preview.type)
    }

    @Test
    fun `a card is lifted out of a bold run without leaving an empty wrapper`() {
        // The standard AniList bio shape: **#1 [Title](https://anilist.co/anime/1)**.
        // The card is drawn after the sentence, and the bold wrapper is not redrawn
        // around nothing once its link is gone.
        val blocks = NovaMarkdown.parse("**#1 [Title](https://anilist.co/anime/1)**")

        assertEquals(2, blocks.size)
        assertEquals("#1", (blocks[0] as NovaBlock.Paragraph).inlines.novaPlainText().trim())
        assertEquals("Title", (blocks[1] as NovaBlock.Preview).title)
    }

    @Test
    fun `a bold run that held only a card leaves no paragraph behind`() {
        val blocks = NovaMarkdown.parse("**[Title](https://anilist.co/anime/1)**")

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is NovaBlock.Preview)
    }

    @Test
    fun `two links in one sentence become two cards after the text`() {
        val blocks = NovaMarkdown.parse(
            "See [A](https://anilist.co/anime/1) and [B](https://anilist.co/manga/2)."
        )

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is NovaBlock.Paragraph)
        assertEquals("A", (blocks[1] as NovaBlock.Preview).title)
        assertEquals("B", (blocks[2] as NovaBlock.Preview).title)
    }

    @Test
    fun `a media link inside a spoiler is still a card when the spoiler opens`() {
        val spoiler = NovaMarkdown.parse("~![A](https://anilist.co/anime/1)!~")
            .single() as NovaBlock.Spoiler

        assertTrue(spoiler.blocks.single() is NovaBlock.Preview)
    }

    @Test
    fun `a link inside an inline spoiler is not lifted out of it`() {
        // Lifting the card would draw it outside the lid and reveal the one thing
        // the author hid. The token stays a spoiler; the link is drawn as a link
        // only once the run is opened.
        val paragraph = NovaMarkdown
            .parse("before ~![A](https://anilist.co/anime/1)!~ after")
            .single() as NovaBlock.Paragraph

        assertTrue(paragraph.inlines.any { it is NovaInline.Spoiler })
        assertTrue(paragraph.inlines.none { it is NovaInline.MediaLink })
    }
}
