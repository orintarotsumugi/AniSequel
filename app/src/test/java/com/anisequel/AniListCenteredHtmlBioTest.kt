package com.anisequel

import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.MarkdownNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListCenteredHtmlBioTest {
    @Test
    fun `center tags sharing lines with bio text preserve centered content and punctuation`() {
        val source = """<center> WELCOME TO MY ANILIST

NO DROPS 100 ANIME IN 228 DAYS

\"I watch whatever catches my eye, and I finish everything I start.\"

#1 - HELL'S PARADISE

\"Hell's Paradise\" () \"Hell's Paradise\" ()</center>"""

        val nodes = AniListMarkdownParser.parse(source)
        assertEquals(1, nodes.size)
        val centered = nodes.single() as MarkdownNode.CenteredBlock
        val text = centered.children.filterIsInstance<MarkdownNode.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<com.anisequel.ui.components.markdown.InlineToken.Plain>()
            .joinToString("") { it.text }
        assertTrue(text.contains("WELCOME TO MY ANILIST"))
        assertTrue(text.contains("Hell's Paradise"))
        assertTrue(text.contains("()"))
        assertTrue(text.contains("\""))
    }
}