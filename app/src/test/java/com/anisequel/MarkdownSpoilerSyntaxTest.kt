package com.anisequel

import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.InlineToken
import com.anisequel.ui.components.markdown.MarkdownNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The HTML spoiler forms a bio and a comment can carry.
 *
 * `~!…!~` is AniList's own shorthand and it only works inside AniList's editor. The
 * site's rich-text output also writes real tags -
 * `<span class="markdown-spoiler" data-label="…">` inside a sentence and
 * `<div class="markdown-spoiler" data-label="…">` around a block - and older posts
 * carry `<div rel="spoiler">`. None of those reached a spoiler before this: the
 * block walk saw a `<div>` as a paragraph of prose and the inline walk printed the
 * tag with the hidden text in plain sight.
 *
 * These are parser tests rather than renderer tests because the contract is the AST:
 * the renderer is one consumer, and the preview/cover walk is another.
 */
class MarkdownSpoilerSyntaxTest {

    private fun blockSpoilers(nodes: List<MarkdownNode>): List<MarkdownNode.SpoilerBlock> =
        nodes.filterIsInstance<MarkdownNode.SpoilerBlock>()

    @Test
    fun `a span spoiler inside a sentence is an inline spoiler`() {
        val inlines = AniListMarkdownParser.parseInlines(
            "Before <span class=\"markdown-spoiler\" data-label=\" Episode 4 \">**the twist**</span> after"
        )

        assertEquals(3, inlines.size)
        assertTrue(inlines[0] is InlineToken.Plain)
        assertTrue(inlines[2] is InlineToken.Plain)
        val spoiler = inlines[1] as InlineToken.Spoiler
        assertEquals("Episode 4", spoiler.label)
        assertEquals("**the twist**", spoiler.text)
        assertTrue(
            "the payload is parsed as markup, so bold is bold after the lid lifts",
            spoiler.children.any { it is InlineToken.Bold }
        )
    }

    @Test
    fun `an empty label is no label and an invalid one is dropped`() {
        fun labelOf(attributes: String): String? {
            val inlines = AniListMarkdownParser.parseInlines(
                "<span class=\"markdown-spoiler\" $attributes>hidden</span>"
            )
            return (inlines.single() as InlineToken.Spoiler).label
        }

        assertNull(labelOf("data-label=\"\""))
        assertNull(labelOf("data-label=\"   \""))
        // These are the three characters that could close the tag they sit in or
        // start markup of their own. A spoiler with no label is still a spoiler;
        // one with half a label is a parsing bug, so the label is dropped whole.
        assertNull(labelOf("data-label=\"Bad {label}\""))
        assertNull(labelOf("data-label=\"A <b>\""))
        assertEquals("Episode 4", labelOf("data-label=\"  Episode 4  \""))
    }

    @Test
    fun `a div spoiler is a block with full markdown inside it`() {
        val nodes = AniListMarkdownParser.parse(
            """
            <div class="markdown-spoiler" data-label="The ending">
            ## The twist

            - the ship sinks
            - nobody believes it
            </div>
            """.trimIndent()
        )

        val spoiler = blockSpoilers(nodes).single()
        assertEquals("The ending", spoiler.label)
        assertTrue(
            "the heading behind the lid has to be a heading, not a paragraph of hash marks",
            spoiler.blocks.any { it is MarkdownNode.Header }
        )
        assertTrue(
            "a list behind the lid has to be a list",
            spoiler.blocks.any { it is MarkdownNode.ListBlock }
        )
    }

    @Test
    fun `the older div rel spoiler is read too`() {
        val spoiler = blockSpoilers(
            AniListMarkdownParser.parse("<div rel=\"spoiler\">the villain survives</div>")
        ).single()

        assertNull(spoiler.label)
        assertTrue(spoiler.blocks.any { it is MarkdownNode.Paragraph })
    }

    @Test
    fun `two spoilers pair with their own closes`() {
        // The failure this pins is greedy matching: one opener reading to the last
        // closing tag on the page would swallow the paragraph between the two
        // spoilers and both lids would open on the same text.
        val nodes = AniListMarkdownParser.parse(
            """
            <div class="markdown-spoiler">first</div>

            the sentence between them

            <div class="markdown-spoiler">second</div>
            """.trimIndent()
        )

        assertEquals(3, nodes.size)
        assertEquals(
            "the first spoiler carries only its own text",
            "first",
            ((nodes[0] as MarkdownNode.SpoilerBlock).blocks.single() as MarkdownNode.Paragraph)
                .inlines.joinToString("") { (it as InlineToken.Plain).text }
        )
        assertTrue(nodes[1] is MarkdownNode.Paragraph)
        assertEquals(
            "second",
            ((nodes[2] as MarkdownNode.SpoilerBlock).blocks.single() as MarkdownNode.Paragraph)
                .inlines.joinToString("") { (it as InlineToken.Plain).text }
        )
    }

    @Test
    fun `spoilers nest three deep and the fourth is left as text`() {
        val source = """
            <div class="markdown-spoiler">one
            <div class="markdown-spoiler">two
            <div class="markdown-spoiler">three
            <div class="markdown-spoiler">four</div>
            </div>
            </div>
            </div>
        """.trimIndent()
        val outer = blockSpoilers(AniListMarkdownParser.parse(source)).single()

        val levelTwo = blockSpoilers(outer.blocks).single()
        val levelThree = blockSpoilers(levelTwo.blocks).single()
        assertTrue(
            "a fourth lid has nowhere to put its control, so its tags are plain text",
            blockSpoilers(levelThree.blocks).isEmpty()
        )
        val visible = levelThree.blocks
            .filterIsInstance<MarkdownNode.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<InlineToken.Plain>()
            .joinToString("") { it.text }
        assertTrue(
            "the fourth spoiler's words still reach the reader",
            visible.contains("four")
        )
        assertTrue(
            "and so does the markup, which is what says it was left alone",
            visible.contains("markdown-spoiler")
        )
    }

    @Test
    fun `an unclosed spoiler tag stays literal text`() {
        val nodes = AniListMarkdownParser.parse(
            "<div class=\"markdown-spoiler\">the text never ends"
        )

        assertTrue(blockSpoilers(nodes).isEmpty())
        val text = (nodes.single() as MarkdownNode.Paragraph)
            .inlines
            .filterIsInstance<InlineToken.Plain>()
            .joinToString("") { it.text }
        assertTrue(text.contains("<div class=\"markdown-spoiler\">"))
    }

    @Test
    fun `a span that wraps a line becomes a block spoiler`() {
        val spoiler = blockSpoilers(
            AniListMarkdownParser.parse(
                "<span class=\"markdown-spoiler\">line one\nline two</span>"
            )
        ).single()

        assertTrue(spoiler.blocks.any { it is MarkdownNode.Paragraph })
    }

    @Test
    fun `a spoiler tag inside a code fence stays quoted`() {
        // A fenced block is example text, not markup. Splitting the spoiler out of
        // it cuts the fence in half and turns the author's example into a working
        // lid; the scan has to skip what is between backticks.
        val nodes = AniListMarkdownParser.parse(
            "```\n<div class=\"markdown-spoiler\">quoted</div>\n```"
        )

        val code = nodes.single() as MarkdownNode.CodeBlock
        assertTrue(code.code.contains("<div class=\"markdown-spoiler\">"))
        assertTrue(blockSpoilers(nodes).isEmpty())
    }

    @Test
    fun `the class is read as tokens and tolerates the split spelling`() {
        fun isSpoiler(className: String): Boolean {
            val inlines = AniListMarkdownParser.parseInlines(
                "<span class=\"$className\">hidden</span>"
            )
            return inlines.singleOrNull() is InlineToken.Spoiler
        }

        assertTrue(isSpoiler("markdown-spoiler"))
        assertTrue("a second class does not stop it being a spoiler", isSpoiler("markdown-spoiler wide"))
        // The site's own mobile output has been seen with the hyphen separated by
        // whitespace. Reading the class as one string treated that as two classes
        // and printed the tag.
        assertTrue(isSpoiler("markdown- spoiler"))
        assertTrue(
            "an unrelated class is not a spoiler",
            !isSpoiler("markdown-quote")
        )
    }

    @Test
    fun `a block closer pairs by tag depth rather than by the first close`() {
        // An arbitrary <div> inside the spoiler has to close before the spoiler
        // does. Without the depth count the spoiler ends at the inner close and the
        // rest of its content - including the real close - prints as text.
        val spoiler = blockSpoilers(
            AniListMarkdownParser.parse(
                """
                <div class="markdown-spoiler">
                before
                <div>an inner box</div>
                after
                </div>
                """.trimIndent()
            )
        ).single()

        val visible = spoiler.blocks
            .filterIsInstance<MarkdownNode.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<InlineToken.Plain>()
            .joinToString(" ") { it.text }
        assertTrue("the text after the inner div is still behind the lid", visible.contains("after"))
        assertTrue(
            "and the outer close is not printed, only the inner div's own tag",
            !visible.contains("markdown-spoiler")
        )
    }
}
