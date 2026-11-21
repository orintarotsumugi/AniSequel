package com.anisequel

import com.anisequel.ui.components.novamarkdown.NovaAlign
import com.anisequel.ui.components.novamarkdown.NovaBlock
import com.anisequel.ui.components.novamarkdown.NovaInline
import com.anisequel.ui.components.novamarkdown.NovaMarkdown
import com.anisequel.ui.components.novamarkdown.novaPlainText
import com.anisequel.ui.components.novamarkdown.novaYouTubeId
import com.anisequel.ui.components.novamarkdown.novaYouTubeThumbnail
import com.anisequel.ui.components.novamarkdown.isNovaSvgUrl
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
    fun `svg paths and format query URLs use the dynamic renderer`() {
        assertTrue(isNovaSvgUrl("https://cdn.example/card.svg"))
        assertTrue(isNovaSvgUrl("https://cdn.example/card.SVG?width=600"))
        assertTrue(isNovaSvgUrl("https://cdn.example/image?format=svg&width=600"))
        assertTrue(isNovaSvgUrl("https://cdn.example/image?width=600&format=svg"))
        assertTrue(!isNovaSvgUrl("https://cdn.example/card.png?format=png"))
    }

    @Test
    fun `a bare non-image url is a link paragraph, not an image`() {
        val blocks = NovaMarkdown.parse("https://example.com/article")

        assertTrue(blocks.single() is NovaBlock.Paragraph)
    }

    @Test
    fun `a row of linked icons stays in one paragraph`() {
        val paragraph = NovaMarkdown.parse(
            """
            <a href="https://a"><img src="https://x/a.png" width="50" height="60"></a>
            <a href="https://b"><img src="https://x/b.png" width="40" height="60"></a>
            """.trimIndent()
        ).single() as NovaBlock.Paragraph
        val links = paragraph.inlines.filterIsInstance<NovaInline.Link>()

        // The renderer draws these side by side; the parser's job is to keep them
        // as one paragraph instead of turning each linked image into a block.
        assertEquals(2, links.size)
        assertEquals(
            listOf("50", "40"),
            links.map { link ->
                link.children.filterIsInstance<NovaInline.Image>().single().width
            }
        )
    }

    @Test
    fun `an anchor that opens on its own line keeps its image inside the link`() {
        val blocks = NovaMarkdown.parse(
            """
            <a href="https://discord.com/users/1">
            <img src="https://x/history.png" width="500" height="60">
            </a>
            """.trimIndent()
        )

        // A line-based parser used to end the paragraph at the image, leaving
        // the opening `<a href=…>` as literal text in the bio.
        assertEquals(1, blocks.size)
        val link = (blocks.single() as NovaBlock.Paragraph)
            .inlines.filterIsInstance<NovaInline.Link>().single()
        val image = link.children.filterIsInstance<NovaInline.Image>().single()

        assertEquals("https://discord.com/users/1", link.url)
        assertEquals("500", image.width)
        assertEquals("60", image.height)
    }

    @Test
    fun `a stray anchor does not swallow the blocks after it`() {
        val blocks = NovaMarkdown.parse(
            """
            <a href="https://x">
            some text

            # Next heading
            """.trimIndent()
        )

        // Keeping a paragraph open for every unclosed `<a>` ate the blank line,
        // the heading and everything after it, printing them as one literal run.
        // Only a picture line may cross an anchor's paragraph boundary.
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is NovaBlock.Paragraph)
        assertTrue(blocks[1] is NovaBlock.Heading)
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

    /**
     * The poster frame the video widget draws needs the id, and the three forms a
     * reader pastes carry it in three different places.
     */
    @Test
    fun `a youtube id is read from every address shape`() {
        assertEquals("abc1234", novaYouTubeId("https://www.youtube.com/watch?v=abc1234"))
        // A timestamp after the id is not part of it, and would 404 the poster.
        assertEquals(
            "abc1234",
            novaYouTubeId("https://www.youtube.com/watch?v=abc1234&t=42s")
        )
        assertEquals("abc1234", novaYouTubeId("https://youtu.be/abc1234"))
        assertEquals("abc1234", novaYouTubeId("https://www.youtube.com/embed/abc1234"))
        assertEquals("abc1234", novaYouTubeId("https://www.youtube.com/shorts/abc1234"))
        assertNull("a non-youtube clip has no poster to ask for", novaYouTubeId("https://x/a.webm"))
    }

    @Test
    fun `the poster frame comes from the id, not the address`() {
        val video = NovaMarkdown.parse("youtube(abc1234)").single() as NovaBlock.Video

        assertEquals(
            "https://i.ytimg.com/vi/abc1234/hqdefault.jpg",
            novaYouTubeThumbnail(video.url)
        )
        assertNull(novaYouTubeThumbnail("https://x/a.webm"))
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
