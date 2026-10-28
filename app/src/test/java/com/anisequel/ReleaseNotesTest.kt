package com.anisequel

import com.anisequel.data.update.ReleaseNote
import com.anisequel.data.update.parseReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update prompt's reading of `update.json`'s `notes`.
 *
 * ## Why the format is parsed at all
 *
 * The field used to be skipped: it held the GitHub release body, which was the
 * same "Find the anime sequels you finished..." paragraph in every release, so the
 * prompt showed a link to the release page instead of the notes. The workflow now
 * writes it from `assets/changelog.json` - one `- Title: detail` line per entry -
 * which means the prompt can show the real notes, and also that this parser is the
 * only thing between those notes and the person deciding whether to update.
 *
 * A parser that quietly returns nothing looks exactly like a release with no
 * notes: the dialog falls back to the link and nobody can tell the difference. So
 * every shape the workflow can actually produce is pinned here, including the
 * truncated final line that `head -c 1200` creates.
 */
class ReleaseNotesTest {

    @Test
    fun `the workflow's format parses into titled entries`() {
        val notes = """
            - A manga tier list: Rankings are separate by default.
            - More jokes: Every tone has new lines.
            - A shorter one.
        """.trimIndent()

        assertEquals(
            listOf(
                ReleaseNote("A manga tier list", "Rankings are separate by default."),
                ReleaseNote("More jokes", "Every tone has new lines."),
                ReleaseNote("A shorter one.", null)
            ),
            parseReleaseNotes(notes)
        )
    }

    @Test
    fun `the build footer is not an entry`() {
        // Appended by the workflow after a blank line. Drawn as a card it would
        // read as a change called "Build 372 from fcf229f." - a commit hash in the
        // one place a person is deciding whether to update.
        val notes = """
            - A real change: With a real detail.

            Build 372 from fcf229f.
        """.trimIndent()

        assertEquals(listOf(ReleaseNote("A real change", "With a real detail.")), parseReleaseNotes(notes))
    }

    @Test
    fun `an older manifest that held markdown has no entries`() {
        // The shape that shipped before the workflow read the changelog. It is not
        // an error: the prompt falls back to the release page, which is where the
        // same notes are. Inventing entries out of prose would be worse.
        val markdown = """
            ## AniSequel
            Find the anime sequels you finished the first season of and never got
            around to watching.

            ## What's new
            - Fixed a crash
        """.trimIndent()

        // The `- Fixed a crash` line is a bullet, so it *is* an entry; the prose is
        // not. That is the honest reading of a mixed document.
        assertEquals(listOf(ReleaseNote("Fixed a crash", null)), parseReleaseNotes(markdown))
    }

    @Test
    fun `a truncated final line is kept`() {
        // `head -c 1200` cuts mid-sentence, and sometimes mid-word. Half an entry
        // about what changed is still more than a link.
        val notes = "- The whole list now scrolls: It used to cut off after the fir"

        assertEquals(
            listOf(ReleaseNote("The whole list now scrolls", "It used to cut off after the fir")),
            parseReleaseNotes(notes)
        )
    }

    @Test
    fun `a bullet cut before its colon is a title with no detail`() {
        assertEquals(
            listOf(ReleaseNote("A change with no detail yet", null)),
            parseReleaseNotes("- A change with no detail yet")
        )
    }

    @Test
    fun `only the first separator splits title from detail`() {
        // The format has no escaping, so the first ": " is the seam. A colon later
        // in the sentence belongs to the detail - splitting on the last one would
        // put the tail of the detail into the heading.
        assertEquals(
            listOf(ReleaseNote("Tier lists", "the sequel: Anime and manga: separate by default.")),
            parseReleaseNotes("- Tier lists: the sequel: Anime and manga: separate by default.")
        )
    }

    @Test
    fun `a bullet with no title is dropped rather than drawn empty`() {
        assertEquals(
            listOf(ReleaseNote("Kept", null)),
            parseReleaseNotes("- \n- : a detail with no title\n- Kept")
        )
    }

    @Test
    fun `blank and absent notes are no entries, not an error`() {
        assertTrue(parseReleaseNotes(null).isEmpty())
        assertTrue(parseReleaseNotes("").isEmpty())
        assertTrue(parseReleaseNotes("   \n\n  ").isEmpty())
    }

    @Test
    fun `carriage returns and indentation do not survive into the entries`() {
        // The workflow strips `\r`, but a hand-edited manifest or a Windows
        // checkout can still carry them, and a trailing `\r` in a title would
        // render as a stray space in the card.
        assertEquals(
            listOf(ReleaseNote("A change", "A detail")),
            parseReleaseNotes("  - A change: A detail  \r\n")
        )
    }
}
