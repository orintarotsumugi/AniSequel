package com.example

import com.example.ui.components.markdown.AniListMarkdownParser
import com.example.ui.components.markdown.InlineToken
import com.example.ui.components.markdown.MarkdownNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListMarkdownTest {

    @Test
    fun `parses headings of multiple levels`() {
        val input = """
            # Heading 1
            ## Heading 2
            ### Heading 3
        """.trimIndent()

        val nodes = AniListMarkdownParser.parse(input)
        assertEquals(3, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.Header && (nodes[0] as MarkdownNode.Header).level == 1)
        assertTrue(nodes[1] is MarkdownNode.Header && (nodes[1] as MarkdownNode.Header).level == 2)
        assertTrue(nodes[2] is MarkdownNode.Header && (nodes[2] as MarkdownNode.Header).level == 3)
    }

    @Test
    fun `parses code blocks and language`() {
        val input = """
            ```kotlin
            fun hello() = "world"
            ```
        """.trimIndent()

        val nodes = AniListMarkdownParser.parse(input)
        assertEquals(1, nodes.size)
        val codeNode = nodes[0] as MarkdownNode.CodeBlock
        assertEquals("kotlin", codeNode.language)
        assertEquals("fun hello() = \"world\"", codeNode.code)
    }

    @Test
    fun `parses unordered and ordered lists`() {
        val input = """
            - First item
            - Second item with **bold**

            1. Numbered one
            2. Numbered two
        """.trimIndent()

        val nodes = AniListMarkdownParser.parse(input)
        assertEquals(2, nodes.size)

        val unord = nodes[0] as MarkdownNode.ListBlock
        assertEquals(false, unord.ordered)
        assertEquals(2, unord.items.size)

        val ord = nodes[1] as MarkdownNode.ListBlock
        assertEquals(true, ord.ordered)
        assertEquals(2, ord.items.size)
    }

    @Test
    fun `parses AniList image tags and markdown images`() {
        val input = """
            img220(https://files.catbox.moe/example.jpg)
            ![Banner](https://image.tmdb.org/t/p/w500/test.png)
        """.trimIndent()

        val nodes = AniListMarkdownParser.parse(input)
        assertEquals(2, nodes.size)
        assertEquals("https://files.catbox.moe/example.jpg", (nodes[0] as MarkdownNode.ImageBlock).url)
        assertEquals("https://image.tmdb.org/t/p/w500/test.png", (nodes[1] as MarkdownNode.ImageBlock).url)
        assertEquals("Banner", (nodes[1] as MarkdownNode.ImageBlock).alt)
    }

    @Test
    fun `parses AniList spoiler blocks and inlines`() {
        val input = "~!This is a secret spoiler!~"
        val nodes = AniListMarkdownParser.parse(input)
        assertEquals(1, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.SpoilerBlock)

        val inlines = AniListMarkdownParser.parseInlines("Watch out: ~!Darth Vader is father!~")
        assertEquals(2, inlines.size)
        assertTrue(inlines[0] is InlineToken.Plain)
        assertTrue(inlines[1] is InlineToken.Spoiler)
        assertEquals("Darth Vader is father", (inlines[1] as InlineToken.Spoiler).text)
    }

    @Test
    fun `parses markdown links and inline styles`() {
        val text = "Check [AniList](https://anilist.co) and `code` and **bold** and *italic* and ~~strike~~"
        val inlines = AniListMarkdownParser.parseInlines(text)

        val link = inlines.filterIsInstance<InlineToken.Link>().firstOrNull()
        assertEquals("AniList", link?.label)
        assertEquals("https://anilist.co", link?.url)

        val code = inlines.filterIsInstance<InlineToken.Code>().firstOrNull()
        assertEquals("code", code?.code)

        val bold = inlines.filterIsInstance<InlineToken.Bold>().firstOrNull()
        assertEquals("bold", bold?.text)

        val italic = inlines.filterIsInstance<InlineToken.Italic>().firstOrNull()
        assertEquals("italic", italic?.text)

        val strike = inlines.filterIsInstance<InlineToken.Strikethrough>().firstOrNull()
        assertEquals("strike", strike?.text)
    }

    @Test
    fun `parses blockquotes`() {
        val input = "> Life is what happens when you're busy making other plans."
        val nodes = AniListMarkdownParser.parse(input)
        assertEquals(1, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.Blockquote)
    }

    @Test
    fun `parses horizontal divider`() {
        val input = """
            Above
            ---
            Below
        """.trimIndent()

        val nodes = AniListMarkdownParser.parse(input)
        assertEquals(3, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.Paragraph)
        assertTrue(nodes[1] is MarkdownNode.Divider)
        assertTrue(nodes[2] is MarkdownNode.Paragraph)
    }
}
