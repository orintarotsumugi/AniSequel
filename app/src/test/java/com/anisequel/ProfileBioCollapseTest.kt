package com.anisequel

import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.MARKDOWN_COLLAPSED_BLOCK_LIMIT
import com.anisequel.ui.screens.profile.isBioCollapsible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the bio card's collapse rule.
 *
 * The rule exists because the card and the markdown renderer were asking two
 * different questions about the same bio. `AniListMarkdownView` drops whole
 * blocks past [MARKDOWN_COLLAPSED_BLOCK_LIMIT] when it is asked to collapse, and
 * the card decided whether to offer a "Show more" toggle from the raw character
 * count. Those disagree in both directions, and both failures are silent:
 *
 *  - A long bio with few blocks clipped nothing, so the toggle offered to reveal
 *    a change that was not there.
 *  - A short bio with many blocks *was* clipped - whole paragraphs vanished with
 *    no cue - while the toggle that would have explained it stayed hidden.
 *
 * The second is the one that reached a build: a person writing six short lines
 * of bio saw the last two disappear and had no way to reach them.
 */
class ProfileBioCollapseTest {

    @Test
    fun `a short bio with more blocks than the renderer keeps is collapsible`() {
        // Six one-line paragraphs is about 120 characters - comfortably under the
        // character hint - and still six blocks, so the renderer drops two of
        // them. This is the case the character-count rule missed entirely.
        val bio = listOf(
            "I watch far too much of it.",
            "Ask me about the 2020s.",
            "Currently: whatever is airing.",
            "Slowly working through the backlog.",
            "I will always ship a CL ship.",
            "Expect a very messy profile."
        ).joinToString("\n\n")

        assertTrue(
            "a bio with ${6} blocks should be collapsible, its length is only ${bio.length}",
            isBioCollapsible(bio, blockCount = 6)
        )
    }

    @Test
    fun `a short bio with few blocks is not collapsible`() {
        // The mirror of the case above, and the one that used to put a "Show
        // more" in front of a bio that had nothing hidden behind it.
        val bio = "I watch far too much of it.\n\nAsk me about the 2020s."

        assertFalse(
            "a two-block bio of ${bio.length} characters has nothing to expand",
            isBioCollapsible(bio, blockCount = 2)
        )
    }

    @Test
    fun `a long single paragraph is still collapsible`() {
        // The character hint is not redundant with the block rule: however many
        // words this is, it is one block, so the block count alone would leave a
        // long paragraph with no toggle and no line clamp at all.
        val bio = "a".repeat(COLLAPSED_CHAR_HINT + 1)

        assertTrue(isBioCollapsible(bio, blockCount = 1))
    }

    @Test
    fun `the card collapses whenever the renderer would drop a block`() {
        // The invariant the bug violated: whenever the parsed bio has more blocks
        // than the collapsed renderer keeps, the card must offer the toggle.
        // This goes through the real parser rather than a hand-passed count so
        // that a change to how blocks are split shows up here.
        val bio = listOf("one", "two", "three", "four", "five").joinToString("\n\n")
        val blocks = AniListMarkdownParser.parse(bio).size

        assertTrue(
            "expected five one-line paragraphs to parse as separate blocks, got $blocks",
            blocks > MARKDOWN_COLLAPSED_BLOCK_LIMIT
        )
        assertTrue(isBioCollapsible(bio, blockCount = blocks))
    }

    @Test
    fun `a one line bio is never collapsible`() {
        assertFalse(isBioCollapsible("hi", blockCount = 1))
    }
}

/**
 * The character hint, restated rather than imported.
 *
 * [isBioCollapsible] keeps it private because it is an implementation detail of
 * the rule; the test needs it only to build an input unambiguously past the
 * threshold, and duplicating the number here means a change to the hint fails
 * this test loudly instead of quietly testing a boundary that moved.
 */
private const val COLLAPSED_CHAR_HINT = 280