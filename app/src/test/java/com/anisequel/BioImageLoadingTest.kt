package com.anisequel

import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.InlineToken
import com.anisequel.ui.components.markdown.MarkdownNode
import com.anisequel.ui.components.markdown.containsEmbeddedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The images a profile bio carries, and the size the author gave each one.
 *
 * ## The three failures this pins
 *
 * A bio is the one piece of AniList content that is *about* its pictures: a banner
 * strip, a GIF between two headings, a spoilered reveal. Three separate defects made
 * the app the worst place to read them, and none of the three shows up as an error -
 * the bio renders, just wrong.
 *
 *  1. **The size was thrown away.** AniList's API returns a sized image as HTML -
 *     `<img width='100%' src='…'>` - and the parser read `src` and `alt` only. Every
 *     sized image therefore fell to the renderer's size-less default, so a bio built
 *     from a 100% banner and two 220px strips drew three full-column slabs.
 *  2. **An image inside formatting was not an image.** `**![banner](x.gif)**` is what
 *     a bold heading line of a bio produces, and the token tree puts the image one
 *     level below the emphasis run. The question "does this paragraph hold a bitmap"
 *     only looked at top-level tokens and at links, so it answered *no*, the paragraph
 *     took its clamped-`Text` branch, and the reader saw the alt text as a link.
 *  3. **An image in a block could be collapsed away.** The about card refuses to
 *     collapse a bio that carries an image - a clamped paragraph cannot hold a bitmap,
 *     so collapsing one deletes the thing the bio was written around - but the check
 *     walked only the top-level blocks. A banner inside a `~~~` centred span, a
 *     `~!…!~` spoiler or a quoted comment lives in a *child* list, so those bios were
 *     collapsed and the picture landed below the clip.
 *
 * All three are parser and predicate questions, which is why this is a JVM test:
 * there is no Android SDK in this environment, and "the banner is the width the author
 * asked for" is a fact about the AST, not about pixels.
 */
class BioImageLoadingTest {

    // region the size the author gave

    @Test
    fun `an HTML image keeps the width the API wrote`() {
        val nodes = AniListMarkdownParser.parse(
            "<img width='220' src='https://s4.anilist.co/file/a.jpg' alt='card'>"
        )
        val image = nodes.single() as MarkdownNode.ImageBlock
        assertEquals("https://s4.anilist.co/file/a.jpg", image.url)
        assertEquals("card", image.alt)
        assertEquals("220", image.width)
    }

    @Test
    fun `a centred HTML banner keeps its percent`() {
        // The multi-line form the API uses for a profile banner. Losing `100%` is what
        // made a full-bleed banner arrive at whatever the size-less default is.
        val nodes = AniListMarkdownParser.parse(
            """
            <center>
            <img width="100%" src="https://s4.anilist.co/file/card.svg">
            </center>
            """.trimIndent()
        )
        val block = nodes.first() as MarkdownNode.CenteredBlock
        val image = block.children.single() as MarkdownNode.ImageBlock
        assertEquals("100%", image.width)
    }

    @Test
    fun `a banner written on one line keeps its size inside the sentence`() {
        // `<center>` and `</center>` flanking the tag on the *same* line is not the
        // block form above, so the image arrives as an inline token instead. The size
        // still has to survive, because inline images are drawn through the same width
        // rule and a lost size here is the same full-column slab.
        val nodes = AniListMarkdownParser.parse(
            "<center><img width='100%' src='https://s4.anilist.co/file/card.jpg'></center>"
        )
        val paragraph = nodes.first { it is MarkdownNode.Paragraph } as MarkdownNode.Paragraph
        assertEquals("100%", paragraph.inlines.images().single().width)
    }

    @Test
    fun `a sized image between two words keeps its size too`() {
        val nodes = AniListMarkdownParser.parse("Look <img width=220px src='https://x/a.gif'> here")
        val paragraph = nodes.single() as MarkdownNode.Paragraph
        val image = paragraph.inlines.images().single()
        // `px` is the browser's spelling and this app's sizes are dp numbers, so the
        // unit is stripped rather than carried into a clamp that cannot read it.
        assertEquals("220", image.width)
        assertEquals("https://x/a.gif", image.url)
    }

    @Test
    fun `each image in one bio keeps its own size`() {
        val bio = """
            img100%(https://x/banner.jpg)

            Look ![strip](https://x/a.gif) and <img width='220' src='https://x/b.jpg'>

            img220(https://x/c.gif)
        """.trimIndent()
        val widths = imagesIn(AniListMarkdownParser.parse(bio)).map { it.width }
        // The block banner at 100%, then the two in the sentence - the markdown image
        // with no size asked for, which is the file's own size, and the HTML one at
        // 220 - then the trailing strip. Four images, four answers.
        assertEquals(listOf("100%", null, "220", "220"), widths)
    }

    @Test
    fun `a width inside the image URL is not the image's size`() {
        // Query strings carry `width=` too, and an attribute search that takes the
        // first match in the tag reads the CDN's parameter as the author's size.
        val nodes = AniListMarkdownParser.parse(
            "<img src='https://s4.anilist.co/file/a.jpg?width=100' width='220'>"
        )
        val image = nodes.single() as MarkdownNode.ImageBlock
        assertEquals("220", image.width)
        assertEquals("https://s4.anilist.co/file/a.jpg?width=100", image.url)
    }

    @Test
    fun `a width that is not a size falls back to no size`() {
        // `auto`, zero, negative and nonsense all mean "the author did not mean a
        // number here", and the renderer's size-less branch is what the site does with
        // them. Clamping `0` to `1` would draw a line of nothing and call it a picture.
        assertNull(AniListMarkdownParser.htmlImageWidth("auto"))
        assertNull(AniListMarkdownParser.htmlImageWidth("0"))
        assertNull(AniListMarkdownParser.htmlImageWidth("-5"))
        assertNull(AniListMarkdownParser.htmlImageWidth("wide"))
        assertNull(AniListMarkdownParser.htmlImageWidth(null))
        assertNull(AniListMarkdownParser.htmlImageWidth("   "))
        assertEquals("220", AniListMarkdownParser.htmlImageWidth(" 220 "))
        assertEquals("220", AniListMarkdownParser.htmlImageWidth("220PX"))
        assertEquals("50%", AniListMarkdownParser.htmlImageWidth("50%"))
    }

    // endregion
    // region an image inside formatting is still an image

    @Test
    fun `a GIF inside a bold run is an embedded image`() {
        // The shape a bio's bold heading line produces. Answering false here is what
        // made the paragraph print the alt text instead of the animation.
        val nodes = AniListMarkdownParser.parse("**![banner](https://x/a.gif)**")
        assertTrue(nodes.containsEmbeddedImage())
        val paragraph = nodes.single() as MarkdownNode.Paragraph
        assertTrue(paragraph.inlines.containsEmbeddedImage())
        val bold = paragraph.inlines.single() as InlineToken.Bold
        assertTrue(
            "the image is one level down, inside the run",
            bold.children.any { it is InlineToken.Image }
        )
    }

    @Test
    fun `a GIF inside an italic run, a strike, a link label or beside words is an embedded image`() {
        listOf(
            "_![a](https://x/a.gif)_",
            "~~![a](https://x/a.gif)~~",
            "[![cover](https://x/a.gif)](https://anilist.co/anime/1)",
            "**![a](https://x/a.gif)** and words"
        ).forEach { bio ->
            val nodes = AniListMarkdownParser.parse(bio)
            assertTrue("'$bio' carries a bitmap at some depth", nodes.containsEmbeddedImage())
        }
    }

    @Test
    fun `text without an image is still text`() {
        val nodes = AniListMarkdownParser.parse("**just words**, and [a link](https://x)")
        assertFalse(nodes.containsEmbeddedImage())
    }

    // endregion
    // region an image in a block is not a top-level paragraph

    @Test
    fun `an image in a centred span, a spoiler or a quote is found by the document walk`() {
        listOf(
            "~~~\nimg220(https://x/a.gif)\n~~~",
            "~!img(https://x/a.gif)!~",
            "> ![quoted](https://x/a.gif)",
            "~~~![span](https://x/a.gif)~~~"
        ).forEach { bio ->
            val nodes = AniListMarkdownParser.parse(bio)
            assertTrue(
                "'$bio' hides its image in a child list, not in a top-level paragraph",
                nodes.containsEmbeddedImage()
            )
        }
    }

    @Test
    fun `a preview card or a clip line is not a bitmap the bio has to show whole`() {
        // The walk answers "is there a bitmap". A media card is a link whose poster
        // arrives later through the cover loader, and a clip is a tap-through row -
        // neither is a `Text` that cannot hold a picture, and reporting them would drop
        // the "Show more" from a long hall-of-fame bio for no reason.
        assertFalse(
            AniListMarkdownParser.parse("https://anilist.co/anime/128893").containsEmbeddedImage()
        )
        assertFalse(AniListMarkdownParser.parse("youtube(abc1234)").containsEmbeddedImage())
    }

    // endregion

    /**
     * Every image in the document - block or inline - in reading order.
     *
     * A bio mixes the two: a banner on its own line and a GIF between two words. The
     * size has to survive down both paths, so this walks both. Writing the walk here
     * rather than reusing the production one is the point: if the two disagree about
     * where an image can hide, [containsEmbeddedImage] is wrong too, and a second walk
     * is what notices.
     */
    private fun imagesIn(nodes: List<MarkdownNode>): List<InlineToken.Image> {
        val out = mutableListOf<InlineToken.Image>()
        nodes.forEach { node ->
            when (node) {
                // A block image is normalised into the inline shape here, so one list
                // holds both kinds and the sizes can be compared across them.
                is MarkdownNode.ImageBlock ->
                    out += InlineToken.Image(node.url, node.alt, node.width)
                is MarkdownNode.CenteredBlock -> out += imagesIn(node.children)
                is MarkdownNode.Blockquote -> {
                    out += node.inlines.images()
                    out += imagesIn(node.blocks)
                }
                is MarkdownNode.SpoilerBlock -> {
                    out += node.inlines.images()
                    out += imagesIn(node.blocks)
                }
                is MarkdownNode.Paragraph -> out += node.inlines.images()
                is MarkdownNode.Header -> out += node.inlines.images()
                is MarkdownNode.ListBlock -> node.items.forEach { out += it.images() }
                is MarkdownNode.CheckListBlock -> node.items.forEach { out += it.inlines.images() }
                else -> Unit
            }
        }
        return out
    }

    /** The images in a token list, at any nesting depth, in order. */
    private fun List<InlineToken>.images(): List<InlineToken.Image> =
        flatMap { token ->
            when (token) {
                is InlineToken.Image -> listOf(token)
                else -> token.nested().images()
            }
        }

    private fun InlineToken.nested(): List<InlineToken> = when (this) {
        is InlineToken.Bold -> children
        is InlineToken.Italic -> children
        is InlineToken.BoldItalic -> children
        is InlineToken.Underline -> children
        is InlineToken.Strikethrough -> children
        is InlineToken.Link -> children
        else -> emptyList()
    }
}
