package com.anisequel

import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.statusLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards how AniList's `status` enum is shown to a reader.
 *
 * The failure this replaces is the most literal one in the app: `statusLabel`
 * returned the field unchanged, so the Info tab printed **RELEASING**,
 * **NOT_YET_RELEASED**, **HIATUS** and **UNKNOWN** in the same typeface as
 * "24 episodes · Winter 2023" directly beside them. Those are names in AniList's
 * API vocabulary, not words anybody writes.
 *
 * `formatLabel` already solved exactly this for `format` - it is why the grid
 * says "TV Short" and not "TV_SHORT". Status was never given the same treatment,
 * which is why the two can disagree in the same card.
 */
class MediaStatusLabelTest {

    private fun media(status: String?) = MediaNode(id = 1, status = status)

    @Test
    fun `a finished show reads finished`() {
        assertEquals("Finished", media("FINISHED").statusLabel)
    }

    @Test
    fun `a releasing show reads airing rather than releasing`() {
        // "Releasing" is AniList's word; a person says "Airing".
        assertEquals("Airing", media("RELEASING").statusLabel)
    }

    @Test
    fun `an unreleased show reads upcoming`() {
        assertEquals("Upcoming", media("NOT_YET_RELEASED").statusLabel)
    }

    @Test
    fun `a cancelled show and a hiatus read as a reader would write them`() {
        assertEquals("Cancelled", media("CANCELLED").statusLabel)
        assertEquals("On hiatus", media("HIATUS").statusLabel)
    }

    @Test
    fun `matching ignores case, because this is an enum name on the wire`() {
        assertEquals("Airing", media("releasing").statusLabel)
        assertEquals("Finished", media("finished").statusLabel)
    }

    @Test
    fun `an absent status reads unknown`() {
        assertEquals("Unknown", media(null).statusLabel)
    }

    @Test
    fun `a status this app has never heard of is shown as-is`() {
        // Deliberately not rewritten into "Unknown". Showing an unfamiliar word
        // is the honest failure; silently labelling it "Unknown" is a confident
        // answer that is not true, and it would hide the day AniList adds one.
        assertEquals("SOME_NEW_STATUS", media("SOME_NEW_STATUS").statusLabel)
    }

    @Test
    fun `no status label leaks its raw wire name`() {
        // The whole point, asserted over every value this maps: none of AniList's
        // enum constants may reach the screen verbatim. Each of these reads as two
        // words where the constant is one underscore-joined word, so a reversal of
        // the mapping would show up here as an exact match.
        val wireNames = listOf(
            "FINISHED", "RELEASING", "NOT_YET_RELEASED", "CANCELLED", "HIATUS"
        )
        wireNames.forEach { status ->
            val shown = media(status).statusLabel
            assertEquals("$status must not be shown verbatim", false, shown == status)
            assertEquals("$status should contain no underscore", false, shown.contains('_'))
        }
    }
}