package com.example

import com.example.ui.components.markdown.AniListMarkdownParser
import com.example.ui.components.markdown.InlineToken
import com.example.ui.components.markdown.MarkdownNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
            [![Alt](https://files.catbox.moe/thumb.png)](https://anilist.co/anime/1)
        """.trimIndent()

        val nodes = AniListMarkdownParser.parse(input)
        assertEquals(3, nodes.size)
        assertEquals("https://files.catbox.moe/example.jpg", (nodes[0] as MarkdownNode.ImageBlock).url)
        assertEquals("https://image.tmdb.org/t/p/w500/test.png", (nodes[1] as MarkdownNode.ImageBlock).url)
        assertEquals("Banner", (nodes[1] as MarkdownNode.ImageBlock).alt)

        val linkedImg = nodes[2] as MarkdownNode.ImageBlock
        assertEquals("https://files.catbox.moe/thumb.png", linkedImg.url)
        assertEquals("https://anilist.co/anime/1", linkedImg.targetUrl)
        assertEquals("Alt", linkedImg.alt)
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
        val text = "Check [AniList](https://anilist.co) and `code` and **bold** and *italic* and ~~strike~~ and <u>underlined</u>"
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

        val underline = inlines.filterIsInstance<InlineToken.Underline>().firstOrNull()
        assertEquals("underlined", underline?.text)
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

    @Test
    fun `parses centered block and mentions`() {
        val input = """
            ~~~center
            Welcome to my profile @john_doe!
            ~~~
        """.trimIndent()

        val nodes = AniListMarkdownParser.parse(input)
        assertEquals(1, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.CenteredBlock)

        val inlines = AniListMarkdownParser.parseInlines("Hello @anime_fan and @user-123")
        val mentions = inlines.filterIsInstance<InlineToken.Mention>()
        assertEquals(2, mentions.size)
        assertEquals("anime_fan", mentions[0].username)
        assertEquals("user-123", mentions[1].username)
    }

    @Test
    fun `parses video embeds and checklists`() {
        val input = """
            youtube(dQw4w9WgXcQ)
            - [x] Finished season 1
            - [ ] Waiting for sequel
        """.trimIndent()

        val nodes = AniListMarkdownParser.parse(input)
        assertEquals(2, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.VideoBlock)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", (nodes[0] as MarkdownNode.VideoBlock).url)

        assertTrue(nodes[1] is MarkdownNode.CheckListBlock)
        val checklist = nodes[1] as MarkdownNode.CheckListBlock
        assertEquals(2, checklist.items.size)
        assertTrue(checklist.items[0].checked)
        assertTrue(!checklist.items[1].checked)
    }

    @Test
    fun `parses markdown tables`() {
        val input = """
            | Title | Score | Status |
            | :--- | :---: | ---: |
            | Steins;Gate | 95 | Completed |
            | Frieren | 98 | Watching |
        """.trimIndent()

        val nodes = AniListMarkdownParser.parse(input)
        assertEquals(1, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.TableBlock)
        val table = nodes[0] as MarkdownNode.TableBlock
        assertEquals(3, table.headers.size)
        assertEquals(2, table.rows.size)
        assertEquals(3, table.rows[0].size)
    }

    @Test
    fun `parses a bare image or GIF URL on its own line as an image`() {
        val nodes = AniListMarkdownParser.parse("https://files.catbox.moe/happy.gif")
        assertEquals(1, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.ImageBlock)
        assertEquals("https://files.catbox.moe/happy.gif", (nodes[0] as MarkdownNode.ImageBlock).url)
    }

    @Test
    fun `parses inline images and GIFs inside a paragraph`() {
        val inlines = AniListMarkdownParser.parseInlines(
            "Look ![a cat](https://x.test/cat.gif) and https://x.test/dog.png together"
        )

        val images = inlines.filterIsInstance<InlineToken.Image>()
        assertEquals(2, images.size)
        assertEquals("https://x.test/cat.gif", images[0].url)
        assertEquals("a cat", images[0].alt)
        assertEquals("https://x.test/dog.png", images[1].url)
        assertEquals(null, images[1].alt)

        // The `!` that used to leak out as its own plain token must be gone; it
        // was literally printed in every bio that carried an inline image.
        assertTrue(
            inlines.none { it is InlineToken.Plain && it.text.contains("!") }
        )
    }

    @Test
    fun `an AniList entity URL inside a sentence is recognised as media`() {
        // It used to stay a plain link, so the renderer had no card to draw and
        // the title printed as underlined text - the reported bug.
        val inlines = AniListMarkdownParser.parseInlines("See https://anilist.co/anime/1 for more")

        assertTrue(inlines.none { it is InlineToken.Image })
        val media = inlines.filterIsInstance<InlineToken.MediaLink>().firstOrNull()
        assertEquals("https://anilist.co/anime/1", media?.url)
        assertEquals("ANIME", media?.type)
        assertEquals("1", media?.id)
    }

    @Test
    fun `a non-AniList URL stays an ordinary link`() {
        // Only AniList entity links become cards. Treating every URL as one would
        // put a poster frame on a link to an article.
        val inlines = AniListMarkdownParser.parseInlines("See https://example.com/x for more")

        assertTrue(inlines.none { it is InlineToken.MediaLink })
        assertEquals(
            "https://example.com/x",
            inlines.filterIsInstance<InlineToken.Link>().firstOrNull()?.url
        )
    }

    /**
     * The bug a screenshot caught and no test did: an emphasis run whose text is
     * a link.
     *
     * `**✨ [#1] [Hell's Paradise](url)**` used to flatten the inner link into the
     * bold token's *string*, so the renderer appended the raw characters and the
     * bio printed `[Hell's Paradise](https://anilist.co/anime/128893)` in bold
     * instead of a tappable title. The link has to survive as a child token.
     */
    @Test
    fun `nests a link inside an emphasis run instead of flattening it`() {
        val inlines = AniListMarkdownParser.parseInlines(
            "**✨ [#1] [Hell's Paradise](https://anilist.co/anime/128893)** *Loyal to my first anime.*"
        )

        val bold = inlines.filterIsInstance<InlineToken.Bold>().firstOrNull()
        assertNotNull("the emphasis run must survive as a Bold token", bold)

        // An AniList link, so it is a `MediaLink` rather than a bare `Link` - but
        // the point stands: the inner link survives as a child token instead of
        // being flattened into the bold run's text.
        val nestedLink = bold!!.children.filterIsInstance<InlineToken.MediaLink>().firstOrNull()
        assertEquals("Hell's Paradise", nestedLink?.label)
        assertEquals("https://anilist.co/anime/128893", nestedLink?.url)

        // The flat fallback stays intact, so a renderer that cannot walk children
        // still has the run's own text rather than nothing.
        assertTrue(bold.text.contains("Hell's Paradise"))
    }

    /**
     * The mirror case: a link whose *label* is emphasised.
     *
     * `[**AniList**](url)` used to print the asterisks inside the link text.
     */
    @Test
    fun `nests an emphasis run inside a link label`() {
        val inlines = AniListMarkdownParser.parseInlines("[**AniList**](https://anilist.co)")

        val link = inlines.filterIsInstance<InlineToken.Link>().firstOrNull()
        // `label` stays the raw source (the fallback a renderer that cannot walk
        // children would use); the parsed style lives in the children.
        assertEquals("**AniList**", link?.label)
        assertEquals("https://anilist.co", link?.url)

        val bold = link?.children?.filterIsInstance<InlineToken.Bold>()?.firstOrNull()
        assertEquals("AniList", bold?.text)
    }

    @Test
    fun `nests italic inside bold`() {
        val inlines = AniListMarkdownParser.parseInlines("**bold *and italic* text**")

        val bold = inlines.filterIsInstance<InlineToken.Bold>().firstOrNull()
        assertNotNull(bold)
        val italic = bold!!.children.filterIsInstance<InlineToken.Italic>().firstOrNull()
        assertEquals("and italic", italic?.text)
    }

    /**
     * The depth cap is what keeps a malformed bio from recursing forever, since a
     * run's label is re-parsed as an expression. This pins that the cap is
     * reached rather than the stack being.
     */
    @Test
    fun `a pathological nesting depth terminates instead of recursing forever`() {
        val inlines = AniListMarkdownParser.parseInlines("**".repeat(12) + "deep" + "**".repeat(12))

        assertTrue("parsing must return tokens rather than overflowing", inlines.isNotEmpty())
    }

    @Test
    fun `parses AniList preview links with www and a trailing slash`() {
        val nodes = AniListMarkdownParser.parse("https://www.anilist.co/anime/16498/")
        assertEquals(1, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.AniListPreviewBlock)
        val preview = nodes[0] as MarkdownNode.AniListPreviewBlock
        assertEquals("ANIME", preview.type)
        assertEquals("16498", preview.id)
    }

    /**
     * The bug the reference screenshots show.
     *
     * `**✨ [#1] [Hell's Paradise](url)**` is the shape every "hall of fame" bio
     * uses, and it rendered as plain underlined text because the block-level
     * preview only matched a line that was nothing but the bare URL. The link has
     * to be lifted out of the paragraph and drawn as a card.
     */
    @Test
    fun `a labelled AniList link in a bold run becomes a preview card`() {
        val nodes = AniListMarkdownParser.parse(
            "**✨ [#1] [Hell's Paradise](https://anilist.co/anime/128893)**"
        )

        val preview = nodes.filterIsInstance<MarkdownNode.AniListPreviewBlock>().singleOrNull()
        assertNotNull("the link must be lifted into a preview card", preview)
        assertEquals("https://anilist.co/anime/128893", preview!!.url)
        assertEquals("ANIME", preview.type)
        assertEquals("128893", preview.id)
        // The label is the title, not a slug guessed from the URL - that URL has no
        // readable name in it at all.
        assertEquals("Hell's Paradise", preview.title)

        // The surrounding text survives, and the link's own text is gone from it
        // so the title is not printed twice (once as text, once on the card).
        val paragraph = nodes.filterIsInstance<MarkdownNode.Paragraph>().singleOrNull()
        assertNotNull("the text around the link must survive as a paragraph", paragraph)
        val text = paragraph!!.inlines
            .filterIsInstance<InlineToken.Bold>()
            .flatMap { it.children }
            .filterIsInstance<InlineToken.Plain>()
            .joinToString("") { it.text }
        assertTrue("the leading text must stay: was '$text'", text.contains("#1"))
        assertFalse("the link label must not be printed as well", text.contains("Hell's Paradise"))
    }

    @Test
    fun `a paragraph with only ordinary links produces no cards`() {
        val nodes = AniListMarkdownParser.parse(
            "Just a sentence about anime with a [link](https://example.com)."
        )

        assertTrue(nodes.filterIsInstance<MarkdownNode.AniListPreviewBlock>().isEmpty())
        assertEquals(1, nodes.size)
    }

    @Test
    fun `a media link inside a list item is not turned into a card`() {
        // Cards are a paragraph treatment; a list item stays a line. The link must
        // still survive as a media link so a card-capable renderer can use it, and
        // the inline renderer falls back to a titled link rather than dropping it.
        val nodes = AniListMarkdownParser.parse("- [Hell's Paradise](https://anilist.co/anime/128893)")

        assertTrue(nodes.filterIsInstance<MarkdownNode.AniListPreviewBlock>().isEmpty())
        val list = nodes.filterIsInstance<MarkdownNode.ListBlock>().singleOrNull()
        assertNotNull(list)
        assertNotNull(
            list!!.items.flatten().filterIsInstance<InlineToken.MediaLink>().firstOrNull()
        )
    }

    @Test
    fun `a single tilde-wrapped line is centred, and the tildes are gone`() {
        // AniList's other centring form. It used to print the six tildes and the
        // sentence between them, left-aligned.
        val nodes = AniListMarkdownParser.parse("~~~Here is my list~~~")

        val paragraph = nodes.filterIsInstance<MarkdownNode.Paragraph>().singleOrNull()
        assertNotNull(paragraph)
        assertTrue("the line must be flagged as centred", paragraph!!.centered)
        val text = paragraph.inlines.filterIsInstance<InlineToken.Plain>().joinToString("") { it.text }
        assertEquals("Here is my list", text)
    }

    @Test
    fun `a parenthesised URL is embedded the way AniList embeds it`() {
        val video = AniListMarkdownParser.parse("(https://www.youtube.com/watch?v=dQw4w9WgXcQ)")
        assertEquals(1, video.size)
        assertTrue(video[0] is MarkdownNode.VideoBlock)
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            (video[0] as MarkdownNode.VideoBlock).url
        )
        assertTrue((video[0] as MarkdownNode.VideoBlock).isYoutube)

        val clip = AniListMarkdownParser.parse("(https://cdn.example.test/op.webm)")
        assertEquals(1, clip.size)
        assertTrue(clip[0] is MarkdownNode.VideoBlock)
        assertFalse((clip[0] as MarkdownNode.VideoBlock).isYoutube)
    }

    @Test
    fun `a parenthesised ordinary link stays a link with its parentheses`() {
        // Only a video or an image is an embed. Lifting every bracketed URL out
        // would replace a sentence's parenthetical citation with a play button.
        val nodes = AniListMarkdownParser.parse("My scores (https://example.com/scoring)")
        assertTrue(nodes.none { it is MarkdownNode.VideoBlock })
        assertTrue(nodes.none { it is MarkdownNode.ImageBlock })
        assertEquals(1, nodes.size)
    }

    @Test
    fun `a bare video URL on its own line is played rather than linked`() {
        val nodes = AniListMarkdownParser.parse("https://youtu.be/dQw4w9WgXcQ")

        assertEquals(1, nodes.size)
        assertTrue(nodes[0] is MarkdownNode.VideoBlock)
        assertTrue((nodes[0] as MarkdownNode.VideoBlock).isYoutube)
    }

    @Test
    fun `an equals sign underline makes a heading and is consumed`() {
        val nodes = AniListMarkdownParser.parse("My ratings\n===\n\ntext")

        assertEquals(2, nodes.size)
        val header = nodes[0] as MarkdownNode.Header
        assertEquals(1, header.level)
        assertEquals(
            "My ratings",
            header.inlines.filterIsInstance<InlineToken.Plain>().joinToString("") { it.text }
        )
        assertTrue(nodes[1] is MarkdownNode.Paragraph)
    }

    @Test
    fun `a dash rule is still a rule, not a heading underline`() {
        // `---` is the ambiguity the community markdown guide warns about, and the
        // rule is the reading a bio almost always means.
        val nodes = AniListMarkdownParser.parse("above\n\n---\n\nbelow")

        assertTrue(nodes.any { it is MarkdownNode.Divider })
        assertTrue(nodes.none { it is MarkdownNode.Header })
    }

    @Test
    fun `a URL ending a sentence does not swallow the full stop`() {
        // The full stop used to be part of the link, so the card and the tapped
        // address both pointed at a path one character too long.
        val inlines = AniListMarkdownParser.parseInlines("Watch it at https://anilist.co/anime/1.")

        val media = inlines.filterIsInstance<InlineToken.MediaLink>().singleOrNull()
        assertNotNull(media)
        assertEquals("https://anilist.co/anime/1", media!!.url)
        assertEquals("1", media.id)
        assertTrue(
            "the sentence's full stop must still be printed",
            inlines.any { it is InlineToken.Plain && it.text == "." }
        )
    }

    @Test
    fun `an email address is not a mention`() {
        val inlines = AniListMarkdownParser.parseInlines("mail me at hi@example.com thanks")

        assertTrue(inlines.none { it is InlineToken.Mention })
    }
}
