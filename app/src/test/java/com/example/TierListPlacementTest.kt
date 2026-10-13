package com.example

import com.example.domain.tier.StandardTiers
import com.example.domain.tier.TierAssignment
import com.example.domain.tier.TierDefinition
import com.example.domain.tier.orphanedCount
import com.example.domain.tier.withPlacement
import com.example.domain.tier.withoutOrphans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Guards where a dropped title actually lands.
 *
 * The board can say two things about a drop: which row, and which tile it went
 * on top of. Only the first existed before, so "drop it between these two" put the
 * title wherever its media id happened to sit in the map - which for an entry
 * filed under that row months ago is the top of the row, nowhere near the finger.
 *
 * The rule the tests pin is that the assignment's iteration order *is* the order of
 * the row: there is no position field, so a reorder is a rebuild of the map, and
 * both the board and the exporter read the same sequence.
 */
class TierListPlacementTest {

    private val tiers = listOf(
        TierDefinition("s", "S", 0xFFE5484D),
        TierDefinition("a", "A", 0xFFF76B15)
    )

    /** Three in S and two in A, interleaved, so tier order and map order differ. */
    private fun assignment() = TierAssignment(
        linkedMapOf(1 to "s", 2 to "a", 3 to "s", 4 to "a", 5 to "s")
    )

    // ---- Moving into a tier ------------------------------------------------

    @Test
    fun `a drop with no anchor goes to the end of that tier`() {
        // Not the end of the map. Both look identical on the board - each row
        // filters by tier - and only one of them keeps the stored order honest
        // for everything that walks it, which the exports and the "add titles"
        // list both do.
        val moved = assignment().withPlacement(mediaId = 2, tierId = "s")

        assertEquals(listOf(1, 3, 5, 2), moved.membersOf("s"))
        assertEquals(listOf(4), moved.membersOf("a"))
    }

    @Test
    fun `the stored order is the row order`() {
        val moved = assignment().withPlacement(mediaId = 2, tierId = "s")

        assertEquals(listOf(1, 3, 4, 5, 2), moved.tierIdByMediaId.keys.toList())
    }

    @Test
    fun `a drop on another tile goes immediately above it`() {
        val moved = assignment().withPlacement(mediaId = 2, tierId = "s", beforeMediaId = 5)

        assertEquals(listOf(1, 3, 2, 5), moved.membersOf("s"))
    }

    @Test
    fun `a drop on the first tile of a row makes it the first tile`() {
        val moved = assignment().withPlacement(mediaId = 5, tierId = "s", beforeMediaId = 1)

        assertEquals(listOf(5, 1, 3), moved.membersOf("s"))
    }

    @Test
    fun `an entry already in the tier is moved rather than duplicated`() {
        val moved = assignment().withPlacement(mediaId = 1, tierId = "s", beforeMediaId = 5)

        assertEquals(listOf(3, 1, 5), moved.membersOf("s"))
        assertEquals(5, moved.tierIdByMediaId.size)
    }

    @Test
    fun `an anchor in another tier is not used as a position`() {
        // The finger was over a tile in S but the drop resolved to A: honouring
        // that anchor would file the title into a row it is not in, or silently
        // into the other one. The end of the named tier is the honest answer.
        val moved = assignment().withPlacement(mediaId = 3, tierId = "a", beforeMediaId = 5)

        assertEquals(listOf(2, 4, 3), moved.membersOf("a"))
    }

    @Test
    fun `a tier with nothing in it takes the entry without a position`() {
        val intoEmptyTier = assignment().withPlacement(mediaId = 1, tierId = "c")

        assertEquals(listOf(1), intoEmptyTier.membersOf("c"))
        // And it left the tier it came from, rather than being in both.
        assertEquals(listOf(3, 5), intoEmptyTier.membersOf("s"))
    }

    @Test
    fun `dropping a tile onto its own slot changes nothing`() {
        // The board can name the tile itself as the anchor for a frame while the
        // finger is still over it. Reordering under a stationary finger reads as
        // the board rearranging itself - and the same instance is returned, so
        // the store writes nothing at all.
        val original = assignment()

        assertSame(original, original.withPlacement(mediaId = 1, tierId = "s", beforeMediaId = 1))
    }

    // ---- Taking one out ----------------------------------------------------

    @Test
    fun `a drop off the rows takes the title out of the list`() {
        val cleared = assignment().withPlacement(mediaId = 3, tierId = null)

        assertEquals(listOf(1, 5), cleared.membersOf("s"))
        assertEquals(4, cleared.tierIdByMediaId.size)
    }

    @Test
    fun `taking out a title that was never in a tier changes nothing`() {
        val original = assignment()

        assertSame(original, original.withPlacement(mediaId = 99, tierId = null))
    }

    // ---- The live-tier invariant -------------------------------------------

    @Test
    fun `placements naming a tier that no longer exists are dropped`() {
        // Reachable from storage: a document written before a row was removed.
        // An entry filed under a row that is not drawn is an entry that is
        // invisible and, in an export, missing.
        val document = assignment().let { it.copy(tierIdByMediaId = it.tierIdByMediaId + (6 to "gone")) }

        val live = document.withoutOrphans(tiers)

        assertEquals(5, live.tierIdByMediaId.size)
        assertEquals(1, document.orphanedCount(tiers))
    }

    @Test
    fun `a list with nothing to drop is handed back unchanged`() {
        // Same instance, so the board does not recompose a whole arrangement for
        // an operation that could not have changed it.
        val live = assignment().withoutOrphans(StandardTiers)

        assertSame(live, live.withoutOrphans(StandardTiers))
        assertEquals(0, live.orphanedCount(StandardTiers))
    }
}
