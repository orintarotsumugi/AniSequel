package com.anisequel

import com.anisequel.ui.components.novamarkdown.NovaAlign
import com.anisequel.ui.components.novamarkdown.NovaBlock
import com.anisequel.ui.components.novamarkdown.NovaInline
import com.anisequel.ui.components.novamarkdown.NovaMarkdown
import com.anisequel.ui.components.novamarkdown.novaPlainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The core of the NovaMarkdown parser, written against AniList's dialect.
 *
 * The old suite tested a different AST that no longer exists. These tests pin the
 * behaviours the app depends on rather than field-for-field parser internals:
 * centered blocks, single-newline line breaks, emphasis, lists, tables, HTML
 * sanitising, and the alignment property every block carries.
 */
class NovaMarkdownParserTest {

    // ---- blocks ---------------------------------------------------------------

    @Test
    fun `atx headings keep their level and text`() {
        val heading = NovaMarkdown.parse("### A title").single() as NovaBlock.Heading

        assertEquals(3, heading.level)
        assertEquals("A title", heading.inlines.novaPlainText())
    }

    @Test
    fun `setext headings are headings, not rules`() {
        val h1 = NovaMarkdown.parse("Title\n===").single() as NovaBlock.Heading
        val h2 = NovaMarkdown.parse("Title\n---").single() as NovaBlock.Heading

        assertEquals(1, h1.level)
        assertEquals(2, h2.level)
    }

    @Test
    fun `a horizontal rule is a block of its own`() {
        assertEquals(NovaBlock.Rule, NovaMarkdown.parse("***").single())
        assertEquals(NovaBlock.Rule, NovaMarkdown.parse("<hr>").single())
    }

    @Test
    fun `a single newline is a line break, not a paragraph break`() {
        // The single most important difference from GitHub-flavoured Markdown:
        // AniList keeps the author's line breaks.
        val paragraph = NovaMarkdown.parse("one\ntwo").single() as NovaBlock.Paragraph

        assertEquals("one\ntwo", paragraph.inlines.novaPlainText())
    }

    @Test
    fun `a blank line separates paragraphs`() {
        val blocks = NovaMarkdown.parse("one\n\ntwo")

        assertEquals(2, blocks.size)
        assertEquals("one", (blocks[0] as NovaBlock.Paragraph).inlines.novaPlainText())
        assertEquals("two", (blocks[1] as NovaBlock.Paragraph).inlines.novaPlainText())
    }

    @Test
    fun `a quote keeps its nested blocks`() {
        val quote = NovaMarkdown.parse("> one\n> two").single() as NovaBlock.Quote

        assertEquals(1, quote.blocks.size)
        assertEquals("one\ntwo", (quote.blocks.single() as NovaBlock.Paragraph).inlines.novaPlainText())
    }

    @Test
    fun `a fenced block keeps its language and code`() {
        val code = NovaMarkdown.parse("```kotlin\nval x = 1\n```").single() as NovaBlock.Code

        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun `bullets and numbers start where the author wrote them`() {
        val bullets = NovaMarkdown.parse("- one\n- two").single() as NovaBlock.BulletList
        val numbers = NovaMarkdown.parse("3. three\n4. four").single() as NovaBlock.BulletList

        assertEquals(2, bullets.items.size)
        assertEquals(false, bullets.ordered)
        assertEquals(true, numbers.ordered)
        assertEquals(3, numbers.start)
    }

    @Test
    fun `task items keep their checked state`() {
        val list = NovaMarkdown.parse("- [x] done\n- [ ] todo").single() as NovaBlock.BulletList

        assertEquals(listOf(true, false), list.items.map { it.checked })
    }

    @Test
    fun `a nested bullet stays inside its parent item`() {
        val list = NovaMarkdown.parse("- one\n  - two").single() as NovaBlock.BulletList
        val nested = list.items.single().blocks.filterIsInstance<NovaBlock.BulletList>().single()

        assertEquals(1, nested.items.size)
    }

    @Test
    fun `a table keeps its header, rows and per-column alignment`() {
        val table = NovaMarkdown.parse(
            """
            | Name | Score |
            | :--- | ---: |
            | A | 1 |
            """.trimIndent()
        ).single() as NovaBlock.Table

        assertEquals(listOf("Name", "Score"), table.headers.map { it.inlines.novaPlainText() })
        assertEquals(NovaAlign.Start, table.headers[0].align)
        assertEquals(NovaAlign.End, table.headers[1].align)
        assertEquals("A", table.rows.single()[0].inlines.novaPlainText())
        assertEquals("1", table.rows.single()[1].inlines.novaPlainText())
    }

    @Test
    fun `an escaped pipe inside a cell is text`() {
        val table = NovaMarkdown.parse(
            "| A |\n| --- |\n| one \\| two |"
        ).single() as NovaBlock.Table

        assertEquals("one | two", table.rows.single().single().inlines.novaPlainText())
    }

    @Test
    fun `a pipe line without a separator is a paragraph`() {
        val blocks = NovaMarkdown.parse("| just | text |")

        assertTrue(blocks.single() is NovaBlock.Paragraph)
    }

    @Test
    fun `an empty document has no blocks`() {
        assertTrue(NovaMarkdown.parse("").isEmpty())
        assertTrue(NovaMarkdown.parse("   \n\n  ").isEmpty())
    }

    // ---- alignment -------------------------------------------------------------

    @Test
    fun `multi-line tildes centre every block inside`() {
        val blocks = NovaMarkdown.parse("~~~\n# Title\n\ntext\n~~~")

        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it.align == NovaAlign.Center })
    }

    @Test
    fun `a one-line tilde span centres its text`() {
        val paragraph = NovaMarkdown.parse("~~~centred~~~").single() as NovaBlock.Paragraph

        assertEquals(NovaAlign.Center, paragraph.align)
    }

    @Test
    fun `html center and align produce the same alignment property`() {
        val center = NovaMarkdown.parse("<center>centred</center>").single()
        val right = NovaMarkdown.parse("<p align=\"right\">right</p>").single()
        val justify = NovaMarkdown.parse("<div style=\"text-align: justify\">justify</div>").single()

        assertEquals(NovaAlign.Center, center.align)
        assertEquals(NovaAlign.End, right.align)
        assertEquals(NovaAlign.Justify, justify.align)
    }

    @Test
    fun `alignment is on the block, not on a wrapper`() {
        // The Dantotsu bug this replaces: `<align center>` is not an HTML element,
        // and a wrapper that only sets TextAlign leaves images left-aligned. Every
        // block carries its own alignment so each renderer can honour it.
        val blocks = NovaMarkdown.parse("<center>text</center>")

        assertTrue(blocks.none { it is NovaBlock.Quote && it.blocks.isEmpty() })
        assertEquals(NovaAlign.Center, blocks.single().align)
    }

    // ---- inline ----------------------------------------------------------------

    @Test
    fun `asterisks and underscores both make bold`() {
        val stars = NovaMarkdown.parseInlines("**bold**").single() as NovaInline.Bold
        val underscores = NovaMarkdown.parseInlines("__bold__").single() as NovaInline.Bold

        assertEquals("bold", stars.children.novaPlainText())
        assertEquals("bold", underscores.children.novaPlainText())
    }

    @Test
    fun `single markers make italic and double tildes strike`() {
        assertTrue(NovaMarkdown.parseInlines("*italic*").single() is NovaInline.Italic)
        assertTrue(NovaMarkdown.parseInlines("_italic_").single() is NovaInline.Italic)
        assertTrue(NovaMarkdown.parseInlines("~~gone~~").single() is NovaInline.Strike)
    }

    @Test
    fun `backticks are literal`() {
        val code = NovaMarkdown.parseInlines("`**not bold**`").single() as NovaInline.Code

        assertEquals("**not bold**", code.code)
    }

    @Test
    fun `an escaped marker is text`() {
        val tokens = NovaMarkdown.parseInlines("\\*not italic\\*")

        assertEquals("*not italic*", tokens.single().let { (it as NovaInline.Text).text })
    }

    @Test
    fun `links keep their label and address`() {
        val link = NovaMarkdown.parseInlines("[label](https://example.com/a_(b))")
            .single() as NovaInline.Link

        assertEquals("https://example.com/a_(b)", link.url)
        assertEquals("label", link.children.novaPlainText())
    }

    @Test
    fun `a bare url becomes a link`() {
        val link = NovaMarkdown.parseInlines("https://example.com").single() as NovaInline.Link

        assertEquals("https://example.com", link.url)
    }

    @Test
    fun `a bare anilist address inside prose becomes a media card`() {
        val media = NovaMarkdown
            .parseInlines("see anilist.co/anime/1 for more")
            .filterIsInstance<NovaInline.MediaLink>()
            .single()

        assertEquals("https://anilist.co/anime/1", media.url)
        assertEquals("ANIME", media.type)
    }

    @Test
    fun `an anilist host inside a word is not a link`() {
        val tokens = NovaMarkdown.parseInlines("myanilist.co/anime/1")

        assertEquals("myanilist.co/anime/1", tokens.novaPlainText())
        assertTrue(tokens.none { it is NovaInline.Link || it is NovaInline.MediaLink })
    }

    @Test
    fun `www entity addresses are recognised`() {
        val preview = NovaMarkdown.parse("https://www.anilist.co/anime/1").single() as NovaBlock.Preview

        assertEquals("ANIME", preview.type)
        assertEquals("1", preview.id)
    }

    @Test
    fun `a mention is not a link but the renderer makes it one`() {
        val mention = NovaMarkdown.parseInlines("hello @rintarotsumugi").last() as NovaInline.Mention

        assertEquals("rintarotsumugi", mention.username)
    }

    @Test
    fun `an inline spoiler keeps its children`() {
        val spoiler = NovaMarkdown.parseInlines("before ~!secret!~ after")
            .filterIsInstance<NovaInline.Spoiler>()
            .single()

        assertEquals("secret", spoiler.text)
        assertEquals("secret", spoiler.children.novaPlainText())
    }

    @Test
    fun `a one-line html spoiler stays inline`() {
        val spoiler = NovaMarkdown
            .parse("before <span class=\"markdown-spoiler\">secret</span> after")
            .single() as NovaBlock.Paragraph
        val token = spoiler.inlines.filterIsInstance<NovaInline.Spoiler>().single()

        assertEquals("secret", token.text)
    }

    @Test
    fun `a div spoiler is a block and keeps its label`() {
        val spoiler = NovaMarkdown
            .parse("<div class=\"markdown-spoiler\" data-label=\"Plot\">hidden</div>")
            .single() as NovaBlock.Spoiler

        assertEquals("Plot", spoiler.label)
        assertEquals("hidden", (spoiler.blocks.single() as NovaBlock.Paragraph).inlines.novaPlainText())
    }

    @Test
    fun `an escape wrapper keeps its AniList address literal`() {
        val blocks = NovaMarkdown.parse(
            "<escape>\"A title\" (https://anilist.co/anime/1)</escape>"
        )
        val paragraph = blocks.single() as NovaBlock.Paragraph

        // `<escape>` means "show this as written". Lifting the URL into a card
        // left the surrounding parentheses behind as an empty `()` above it.
        assertEquals(1, blocks.size)
        assertTrue(paragraph.inlines.none { it is NovaInline.MediaLink })
        assertEquals(
            "\"A title\" (https://anilist.co/anime/1)",
            paragraph.inlines.novaPlainText()
        )
    }

    @Test
    fun `a bare entity URL inside escape is still literal on its own line`() {
        val blocks = NovaMarkdown.parse(
            """
            <escape>
            https://anilist.co/anime/1
            </escape>
            """.trimIndent()
        )
        val paragraph = blocks.single() as NovaBlock.Paragraph

        // A bare AniList URL is a block starter. Without a block-level reader it
        // peeled off into a preview card and the wrapper collapsed to nothing,
        // leaving the card the author asked not to have.
        assertTrue(paragraph.inlines.none { it is NovaInline.MediaLink })
        assertEquals(
            "https://anilist.co/anime/1",
            paragraph.inlines.novaPlainText().trim()
        )
    }

    @Test
    fun `an image inside escape is still literal on its own line`() {
        val blocks = NovaMarkdown.parse(
            """
            <escape>
            ![cover](https://x/a.png)
            </escape>
            """.trimIndent()
        )
        val paragraph = blocks.single() as NovaBlock.Paragraph

        assertTrue(paragraph.inlines.none { it is NovaInline.Image })
        assertEquals(
            "![cover](https://x/a.png)",
            paragraph.inlines.novaPlainText().trim()
        )
    }

    @Test
    fun `an anchor spanning an escape line still makes a link`() {
        val blocks = NovaMarkdown.parse(
            """
            <a href="https://discord.com/users/1">
            <escape>literal</escape>
            </a>
            """.trimIndent()
        )

        // `<escape>` is a block start, but inside an open anchor its line belongs
        // to the link. Dropping it used to leave the `<a>` as dead text and the
        // literal body as a separate paragraph.
        val link = (blocks.single() as NovaBlock.Paragraph)
            .inlines.filterIsInstance<NovaInline.Link>().single()

        assertEquals("https://discord.com/users/1", link.url)
        assertEquals("literal", link.children.novaPlainText().trim())
    }

    @Test
    fun `text after an escape close keeps its leading space`() {
        val blocks = NovaMarkdown.parse("<escape>literal</escape> and more")

        assertEquals(
            "literal and more",
            (blocks.single() as NovaBlock.Paragraph).inlines.novaPlainText()
        )
    }

    @Test
    fun `a lookalike close tag does not cut escape content short`() {
        val inlines = NovaMarkdown.parseInlines("<escape>a</escaped> b</escape>")

        assertEquals("a</escaped> b", inlines.novaPlainText())
    }

    @Test
    fun `an anchor without href is accent text, not a dead link`() {
        val accent = NovaMarkdown.parseInlines("<a>coloured</a>").single() as NovaInline.Accent

        assertEquals("coloured", accent.children.novaPlainText())
    }

    @Test
    fun `an entity link becomes a media link token`() {
        val media = NovaMarkdown
            .parseInlines("[Spike](https://anilist.co/character/1)")
            .single() as NovaInline.MediaLink

        assertEquals("Spike", media.label)
        assertEquals("CHARACTER", media.type)
        assertEquals("1", media.id)
    }

    // ---- sanitising ------------------------------------------------------------

    @Test
    fun `script and style payloads never reach the renderer`() {
        val blocks = NovaMarkdown.parse("<script>alert(1)</script>Hello<style>*{}</style>")

        assertEquals("Hello", (blocks.single() as NovaBlock.Paragraph).inlines.novaPlainText())
    }

    @Test
    fun `encoded script tags are stripped after decoding`() {
        val blocks = NovaMarkdown.parse("&lt;script&gt;alert(1)&lt;/script&gt;Hello")

        assertEquals("Hello", (blocks.single() as NovaBlock.Paragraph).inlines.novaPlainText())
    }

    @Test
    fun `html entities are decoded once`() {
        val text = NovaMarkdown.parseInlines("a &amp; b &lt; c &#65;").single() as NovaInline.Text

        assertEquals("a & b < c A", text.text)
    }

    @Test
    fun `br is a line break`() {
        val paragraph = NovaMarkdown.parse("one<br>two").single() as NovaBlock.Paragraph

        assertEquals("one\ntwo", paragraph.inlines.novaPlainText())
    }

    @Test
    fun `a malformed document still terminates and keeps its text`() {
        // Every loop in the parser has to advance. This is the shape that used to
        // hang regex parsers: an opener with no closer.
        val blocks = NovaMarkdown.parse("~~~\n![unclosed](https://x/a.png\n**bold")

        assertTrue(blocks.isNotEmpty())
    }

    @Test
    fun `the parse cache returns the same immutable document`() {
        // Documents are re-read on every scroll frame; reparsing is the cost the
        // LRU exists to remove. Immutability is what makes sharing them safe.
        val first = NovaMarkdown.parse("**cached**")
        val second = NovaMarkdown.parse("**cached**")

        assertSame(first, second)
    }
}
