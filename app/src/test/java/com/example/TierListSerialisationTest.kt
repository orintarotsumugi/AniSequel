package com.example

import com.example.data.repository.TierListPreferences.Companion.decodeAssignment
import com.example.data.repository.TierListPreferences.Companion.decodeTiers
import com.example.data.repository.TierListPreferences.Companion.encodeAssignment
import com.example.data.repository.TierListPreferences.Companion.encodeTiers
import com.example.domain.tier.StandardTiers
import com.example.domain.tier.TierAssignment
import com.example.domain.tier.TierDefinition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the tier list's on-disk format.
 *
 * This file holds somebody's afternoon. A parse that throws on one bad value
 * does not fail loudly - it fails by loading an *empty* list, and the person
 * finds out when the tier list they built is gone and there is nothing in the
 * UI that says why. So the behaviour that matters is: skip the bad part, keep
 * the rest, and never silently return "nothing".
 */
class TierListSerialisationTest {

    @Test
    fun `an unwritten list loads the standard tiers`() {
        assertEquals(StandardTiers, decodeTiers(null))
        assertEquals(StandardTiers, decodeTiers(""))
        assertEquals(StandardTiers, decodeTiers("   "))
    }

    @Test
    fun `tiers survive a round trip`() {
        val custom = listOf(
            TierDefinition("s", "Peak", 0xFFE5484D),
            TierDefinition("fav", "Favourites", 0xFF30A46C)
        )

        assertEquals(custom, decodeTiers(encodeTiers(custom)))
    }

    @Test
    fun `a rename keeps the id, so nothing filed under it is orphaned`() {
        // This is the whole reason the id is stored separately from the label:
        // renaming "A" to "Excellent" must not detach every entry filed under A.
        val renamed = StandardTiers.map { if (it.id == "a") it.copy(label = "Excellent") else it }

        val decoded = decodeTiers(encodeTiers(renamed))
        assertEquals("Excellent", decoded.first { it.id == "a" }.label)
    }

    @Test
    fun `a colour survives as the packed value it was stored from`() {
        val decoded = decodeTiers(encodeTiers(listOf(TierDefinition("x", "X", 0xFF0091FF))))

        assertEquals(0xFF0091FFL, decoded.single().colorArgb)
    }

    @Test
    fun `one unreadable field does not lose the others`() {
        // A single corrupt entry must not cost the whole list - that is the
        // difference between a bad row and a deleted afternoon.
        val good = "Peak|4294906125|s"
        val bad = "this line has no separators at all"

        val decoded = decodeTiers("$good${','}$bad")

        assertEquals(1, decoded.size)
        assertEquals("Peak", decoded.single().label)
    }

    @Test
    fun `a file with no readable tiers falls back to the standard ones`() {
        // Falling back to nothing would look exactly like "your list was
        // deleted". Showing the template is the recoverable outcome.
        assertEquals(StandardTiers, decodeTiers("garbage|||,,,|||"))
    }

    @Test
    fun `an empty label or id is dropped rather than drawn blank`() {
        // "Good" first, so a reader of the failure sees that only the bad rows
        // were lost - and the assertion is "the good one survived", which fails
        // if the whole file is rejected, rather than the weaker "nothing is
        // blank", which passes on an empty list.
        val decoded = decodeTiers("Peak|123|s,|123|x,,y|123|")

        assertEquals(listOf("Peak"), decoded.map { it.label })
    }

    @Test
    fun `an assignment survives a round trip`() {
        val assignment = TierAssignment(mapOf(21 to "s", 30013 to "fav", 7 to "s"))

        assertEquals(assignment, decodeAssignment(encodeAssignment(assignment)))
    }

    @Test
    fun `an empty assignment is an empty assignment`() {
        assertEquals(TierAssignment(), decodeAssignment(null))
        assertEquals(TierAssignment(), decodeAssignment(""))
        assertEquals(TierAssignment(), decodeAssignment("   "))
    }

    @Test
    fun `a truncated entry is skipped, not guessed at`() {
        // "21:s" then a dangling "30013" with no tier. Guessing the tier would
        // file a title somewhere the person never put it.
        val decoded = decodeAssignment("21:s;30013")

        assertEquals(mapOf(21 to "s"), decoded.tierIdByMediaId)
    }

    @Test
    fun `a non-numeric media id is skipped`() {
        assertEquals(TierAssignment(), decodeAssignment("abc:s"))
    }
}