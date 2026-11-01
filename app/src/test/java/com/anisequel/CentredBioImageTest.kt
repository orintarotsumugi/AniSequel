package com.anisequel

import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.MarkdownNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A centred image inside a single-line `~~~...~~~` span.
 *
 * The report this came from was a bio that showed as a sentence of markup: AniList's
 * own editor writes a centred banner as `~~~img100%(https://…/card.svg)~~~` and
 * answers the same bio from its API as
 * `<center><img width='100%' src='…card.svg'></center>`. The whole-line image forms
 * are matched with `matchEntire` on the source line, and inside the tildes the line
 * is the whole span - so the image code could never be recognised, and the centred
 * paragraph path printed it.
 */
class CentredBioImageTest {

    @Test
    fun `a centred percent-sized image is an image, centred, with its size kept`() {
        val nodes = AniListMarkdownParser.parse(
            "~~~img100%(https://example.com/card.svg)~~~"
        )
        val image = nodes.single() as MarkdownNode.ImageBlock
        assertEquals("https://example.com/card.svg", image.url)
        assertTrue("the renderer only centres an image it is told to centre", image.centered)
        assertEquals("size must survive as the author set it", "100%", image.width)
    }

    @Test
    fun `a centred fixed-width image keeps its width`() {
        val image = AniListMarkdownParser
            .parse("~~~img220(https://example.com/a.png)~~~")
            .single() as MarkdownNode.ImageBlock
        assertEquals(220, image.width?.toIntOrNull())
        assertTrue(image.centered)
    }

    @Test
    fun `a centred markdown image is an image too`() {
        val image = AniListMarkdownParser
            .parse("~~~![alt](https://example.com/a.png)~~~")
            .single() as MarkdownNode.ImageBlock
        assertEquals("https://example.com/a.png", image.url)
        assertEquals("alt", image.alt)
        assertTrue(image.centered)
    }

    @Test
    fun `a centred linked image keeps both addresses`() {
        val image = AniListMarkdownParser
            .parse("~~~[img(https://example.com/a.png)](https://example.com/target)~~~")
            .single() as MarkdownNode.ImageBlock
        assertEquals("https://example.com/a.png", image.url)
        assertEquals("https://example.com/target", image.targetUrl)
        assertTrue(image.centered)
    }

    @Test
    fun `centred text is still centred text`() {
        val nodes = AniListMarkdownParser.parse("~~~Just some centred text~~~")
        val paragraph = nodes.single() as MarkdownNode.Paragraph
        assertTrue(paragraph.centered)
    }

    @Test
    fun `a centred anilist link is still a media card`() {
        val nodes = AniListMarkdownParser.parse("~~~https://anilist.co/anime/1~~~")
        assertTrue(
            "expected a preview card, got ${nodes.first()}",
            nodes.single() is MarkdownNode.AniListPreviewBlock
        )
    }

    @Test
    fun `an image code that is not the whole line stays a sentence`() {
        // The other half of the rule: this must not turn into an image, because the
        // author wrote a sentence that happens to mention an embed.
        val nodes = AniListMarkdownParser.parse("~~~text before img(https://example.com/a.png)~~~")
        assertTrue(nodes.single() is MarkdownNode.Paragraph)
    }

    @Test
    fun `the multi-line centre block still centres its image`() {
        // The path this fix imitates, checked so the two cannot drift.
        val nodes = AniListMarkdownParser.parse("~~~\nimg(https://example.com/a.png)\n~~~")
        val block = nodes.single() as MarkdownNode.CenteredBlock
        val image = block.children.single() as MarkdownNode.ImageBlock
        assertTrue(image.centered)
    }

    @Test
    fun `a nested centre does not run away`() {
        // The depth guard is the reason a `~~~` inside a `~~~` cannot loop; the
        // single-line branch participates in it rather than parsing freely.
        val nodes = AniListMarkdownParser.parse("~~~inner~~~")
        assertEquals(1, nodes.size)
    }
}
