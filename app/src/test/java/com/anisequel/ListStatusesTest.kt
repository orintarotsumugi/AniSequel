package com.anisequel

import com.anisequel.domain.list.ListStatuses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one list-status vocabulary.
 *
 * Four surfaces print these words - the entry editor's picker, the Library's
 * filter chips, the Stats tab's status strip and the tier board's filter sheet -
 * and each of them used to hold its own copy of the six tokens with its own
 * spelling. That is the failure this pins: the same `MediaListStatus` named two
 * things a screen apart, so a reader who had just set "On-Hold" could not find
 * their entries under that name in the filter row.
 *
 * The enum itself is AniList's ([docs](https://docs.anilist.co/reference/enum/medialiststatus))
 * and has exactly six values. What is *required*, rather than chosen, is that
 * every one of those six maps to a label, that the two media types differ only
 * where English differs, and that a token this build has never seen is shown
 * rather than swallowed.
 */
class ListStatusesTest {

    @Test
    fun `every status AniList can answer with has a label`() {
        val apiTokens = setOf(
            "CURRENT", "PLANNING", "COMPLETED", "DROPPED", "PAUSED", "REPEATING"
        )
        assertEquals(
            "AniList's MediaListStatus enum has exactly these six values; a token " +
                "in this app that is not one of them cannot be saved, and a missing " +
                "one leaves a real entry with no filter to find it under.",
            apiTokens,
            ListStatuses.tokens
        )
        apiTokens.forEach { token ->
            assertTrue(
                "$token has no label",
                ListStatuses.label(token, isManga = true).isNotBlank()
            )
        }
    }

    @Test
    fun `the two media types are worded apart only where english is`() {
        // Completed, On-Hold and Dropped describe the title, not the medium; the
        // other three are verbs a person does not do to a book.
        assertEquals(
            listOf("Completed", "On-Hold", "Dropped"),
            listOf("COMPLETED", "PAUSED", "DROPPED").map { ListStatuses.label(it, isManga = true) }
        )
        assertEquals(
            listOf("Completed", "On-Hold", "Dropped"),
            listOf("COMPLETED", "PAUSED", "DROPPED").map { ListStatuses.label(it, isManga = false) }
        )
        assertEquals("Reading", ListStatuses.label("CURRENT", isManga = true))
        assertEquals("Watching", ListStatuses.label("CURRENT", isManga = false))
        assertEquals("Plan to Read", ListStatuses.label("PLANNING", isManga = true))
        assertEquals("Plan to Watch", ListStatuses.label("PLANNING", isManga = false))
        assertTrue(
            "a manga list must never ask whether you watched a book",
            ListStatuses.options.none { it.mangaLabel.contains("Watch") }
        )
    }

    @Test
    fun `a legacy token is filed under the status it became`() {
        // API v1 spelled three of them differently and had a seventh, CLEARED,
        // that v2 folded into DROPPED. Old fixtures, old exports and cached
        // snapshots still carry those names.
        assertEquals("CURRENT", ListStatuses.normalize("WATCHING"))
        assertEquals("COMPLETED", ListStatuses.normalize("FINISHED"))
        assertEquals("PAUSED", ListStatuses.normalize("on_hold"))
        assertEquals("DROPPED", ListStatuses.normalize("CLEARED"))
        assertNull("an invented token must not silently become a real one", ListStatuses.normalize("SOMETHINGNEW"))
    }

    @Test
    fun `a status this build predates is shown in words rather than dropped`() {
        assertEquals("Something new", ListStatuses.label("SOMETHING_NEW", isManga = false))
        assertEquals("Somethingnew", ListStatuses.label("SOMETHINGNEW", isManga = false))
        // The absence of an entry is not a status, and the editor draws it.
        assertEquals("Not on your list", ListStatuses.label(null, isManga = false))
        assertEquals("Not on your list", ListStatuses.label("   ", isManga = false))
    }

    @Test
    fun `the wire form is the uppercase enum name the mutation expects`() {
        // `normalize` is what a comparison against a status string should go
        // through: AniList's enum names are uppercase, and the values written back
        // in `SaveMediaListEntry` have to be those names rather than a label or a
        // fixture's lower-case spelling.
        assertEquals("REPEATING", ListStatuses.normalize("repeating"))
        assertEquals("REPEATING", ListStatuses.normalize(" Repeating "))
    }
}
