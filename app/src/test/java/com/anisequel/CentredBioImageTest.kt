package com.anisequel

import com.anisequel.ui.components.novamarkdown.NovaAlign
import com.anisequel.ui.components.novamarkdown.NovaBlock
import com.anisequel.ui.components.novamarkdown.NovaMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A centred image inside a single-line `~~~...~~~` span.
 *
 * The report this came from was a bio that showed as a sentence of markup:
 * AniList's own editor writes a centred banner as
 * `~~~img100%(https://…/card.svg)~~~` and answers the same bio from its API as
 * `<center><img width='100%' src='…card.svg'></center>`. The whole-line image
 * forms are matched with `matchEntire` on the source line, and inside the tildes
 * the line is the whole span - so the image code could never be recognised, and
 * the centred paragraph path printed it.
 */
class CentredBioImageTest {

    @Test
    fun `a centred percent-sized image is an image, centred, with its size kept`() {
        val nodes = NovaMarkdown.parse("~~~img100%(https://example.com/card.svg)~~~")
        val image = nodes.single() as NovaBlock.Image

        assertEquals("https://example.com/card.svg", image.url)
        assertEquals("size must survive as the author set it", "100%", image.width)
        assertEquals(
            "alignment lives on the image itself, so the renderer can centre the widget",
            NovaAlign.Center,
            image.align
        )
    }

    @Test
    fun `a centred fixed-width image keeps its width`() {
        val image = NovaMarkdown
            .parse("~~~img220(https://example.com/a.png)~~~")
            .single() as NovaBlock.Image

        assertEquals(220, image.width?.toIntOrNull())
        assertEquals(NovaAlign.Center, image.align)
    }

    @Test
    fun `a centred markdown image is an image too`() {
        val image = NovaMarkdown
            .parse("~~~![alt](https://example.com/a.png)~~~")
            .single() as NovaBlock.Image

        assertEquals("https://example.com/a.png", image.url)
        assertEquals("alt", image.alt)
        assertEquals(NovaAlign.Center, image.align)
    }

    @Test
    fun `a centred linked image keeps both addresses`() {
        val image = NovaMarkdown
            .parse("~~~[img(https://example.com/a.png)](https://example.com/target)~~~")
            .single() as NovaBlock.Image

        assertEquals("https://example.com/a.png", image.url)
        assertEquals("https://example.com/target", image.targetUrl)
        assertEquals(NovaAlign.Center, image.align)
    }

    @Test
    fun `centred text is still centred text`() {
        val paragraph = NovaMarkdown
            .parse("~~~Just some centred text~~~")
            .single() as NovaBlock.Paragraph

        assertEquals(NovaAlign.Center, paragraph.align)
    }

    @Test
    fun `a centred anilist link is still a media card`() {
        val preview = NovaMarkdown
            .parse("~~~https://anilist.co/anime/1~~~")
            .single() as NovaBlock.Preview

        assertEquals("ANIME", preview.type)
        assertEquals("1", preview.id)
        assertEquals(NovaAlign.Center, preview.align)
    }

    @Test
    fun `an image code that is not the whole line stays a sentence`() {
        // The other half of the rule: this must not turn into an image, because
        // the author wrote a sentence that happens to mention an embed.
        val nodes = NovaMarkdown.parse("~~~text before img(https://example.com/a.png)~~~")

        assertTrue(nodes.single() is NovaBlock.Paragraph)
    }

    @Test
    fun `the multi-line centre block still centres its image`() {
        // The path this fix imitates, checked so the two cannot drift.
        val image = NovaMarkdown
            .parse("~~~\nimg(https://example.com/a.png)\n~~~")
            .single() as NovaBlock.Image

        assertEquals(NovaAlign.Center, image.align)
    }

    @Test
    fun `a nested centre does not run away`() {
        // The depth guard is the reason a `~~~` inside a `~~~` cannot loop; the
        // single-line branch participates in it rather than parsing freely.
        val nodes = NovaMarkdown.parse("~~~inner~~~")

        assertEquals(1, nodes.size)
    }

    @Test
    fun `an html centre centres the image without a wrapper node`() {
        // AniList's API form of the same banner: `<center>` is an alignment
        // property, not a node the renderer has to know how to draw.
        val image = NovaMarkdown
            .parse("<center><img width='100%' src='https://example.com/card.svg'></center>")
            .single() as NovaBlock.Image

        assertEquals("100%", image.width)
        assertEquals(NovaAlign.Center, image.align)
    }
}
