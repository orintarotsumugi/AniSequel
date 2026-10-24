package com.anisequel

import com.anisequel.domain.tier.StandardTiers
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierDisplay
import com.anisequel.domain.tier.TierFilter
import com.anisequel.domain.tier.TierListDocument
import com.anisequel.domain.tier.TierTitlePosition
import com.anisequel.domain.tier.decodeDisplay
import com.anisequel.domain.tier.decodeTierListDocument
import com.anisequel.domain.tier.decodeTiers
import com.anisequel.domain.tier.encodeDisplay
import com.anisequel.domain.tier.encodeTierListDocument
import com.anisequel.domain.tier.encodeTiers
import com.anisequel.domain.tier.onlyFor
import com.anisequel.domain.tier.scoreFloorsOf
import com.anisequel.domain.tier.seedFromScores
import com.anisequel.domain.tier.tierTileCornerRadius
import com.anisequel.ui.screens.tier.TIER_EXPORT_WIDTH_STANDARD
import com.anisequel.ui.screens.tier.TIER_EXPORT_WIDTH_ULTRA
import com.anisequel.ui.screens.tier.TIER_TRAY_BOTTOM_GAP
import com.anisequel.ui.screens.tier.TIER_TRAY_HEADER_HEIGHT
import com.anisequel.ui.screens.tier.TIER_TRAY_HEIGHT
import com.anisequel.ui.screens.tier.TIER_TRAY_ITEM_HEIGHT
import com.anisequel.ui.screens.tier.estimateTierExportSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the board redesign's domain rules.
 *
 * These are the parts of "filters, rating bands and display options" that can be
 * wrong without looking wrong: a filter that hides the wrong side of a status, a
 * band that moves with the wrong tier, a zoom that comes back as a different
 * number after a save. The drawing itself is checked in CI by the compiler and by
 * eye; the rules that decide what is drawn are checked here.
 */
class TierBoardRedesignTest {

    private fun candidate(
        id: Int,
        listStatus: String? = null,
        airingStatus: String? = null,
        score: Int? = null
    ) = TierCandidate(
        mediaId = id,
        title = "Title $id",
        averageScore = score,
        listStatus = listStatus,
        airingStatus = airingStatus
    )

    // ---- Filters -----------------------------------------------------------

    @Test
    fun `planning is hidden by default and everything else is shown`() {
        // The default is the whole feature: a board that opens on the titles the
        // person has formed an opinion about, not on the longest list they keep.
        val filter = TierFilter.Default

        assertFalse(filter.allows(candidate(1, listStatus = "PLANNING")))
        assertTrue(filter.allows(candidate(2, listStatus = "CURRENT")))
        assertTrue(filter.allows(candidate(3, listStatus = "COMPLETED")))
        assertTrue(filter.allows(candidate(4, listStatus = "DROPPED")))
        assertTrue(filter.isDefault)
    }

    @Test
    fun `an entry with no status is kept rather than hidden`() {
        // A missing status is "unknown", not "planning". Hiding it would silently
        // drop a title from somebody's board for a field AniList did not send.
        assertTrue(TierFilter.Default.allows(candidate(1)))
    }

    @Test
    fun `the two status dimensions are independent`() {
        val filter = TierFilter.Default.withAiringStatusHidden("FINISHED", true)

        assertFalse(filter.allows(candidate(1, airingStatus = "FINISHED")))
        assertTrue(filter.allows(candidate(2, airingStatus = "RELEASING")))
        // Hiding finished does not unhide planning.
        assertFalse(filter.allows(candidate(3, listStatus = "PLANNING")))
    }

    @Test
    fun `a hidden status can be shown again`() {
        val filter = TierFilter.Default.withListStatusHidden("PLANNING", false)

        assertTrue(filter.allows(candidate(1, listStatus = "PLANNING")))
        // Showing planning is *not* the default state - the default hides it - so
        // the badge and the sheet's "Default" button both have something to say.
        assertFalse(filter.isDefault)
    }

    @Test
    fun `the default is restored by toggling the default back on`() {
        val changed = TierFilter.Default
            .withListStatusHidden("COMPLETED", true)
            .withAiringStatusHidden("RELEASING", true)

        assertFalse(changed.isDefault)
        assertTrue(
            changed
                .withListStatusHidden("COMPLETED", false)
                .withAiringStatusHidden("RELEASING", false)
                .isDefault
        )
    }

    @Test
    fun `filtering an assignment keeps its order and only its visible ids`() {
        // Order is the row: a filter must never reshuffle the posters that stay.
        val assignment = TierAssignment(linkedMapOf(1 to "s", 2 to "a", 3 to "s", 4 to "b"))

        val visible = assignment.onlyFor(setOf(1, 3, 4))

        assertEquals(listOf(1, 3, 4), visible.tierIdByMediaId.keys.toList())
        assertEquals("s", visible.tierOf(1))
        assertEquals("b", visible.tierOf(4))
        assertNull(visible.tierOf(2))
    }

    @Test
    fun `filtering every id out leaves an empty assignment`() {
        val assignment = TierAssignment(linkedMapOf(1 to "s"))

        assertTrue(assignment.onlyFor(emptySet()).isEmpty)
    }

    @Test
    fun `a placement the library does not know is kept out of the filter's count`() {
        // The failure this pins: a saved placement whose title is no longer in the
        // library was dropped by the filter and then counted as "hidden by
        // filters", which blamed the filter for a title it never saw.
        val assignment = TierAssignment(linkedMapOf(1 to "s", 2 to "s"))

        val visible = assignment.onlyFor(visibleIds = setOf(1), keepIds = setOf(2))

        assertEquals(listOf(1, 2), visible.tierIdByMediaId.keys.toList())
    }

    // ---- Rating bands ------------------------------------------------------

    @Test
    fun `the standard tiers carry the conventional bands`() {
        assertEquals(
            mapOf("s" to 85, "a" to 75, "b" to 65, "c" to 55, "d" to 45, "f" to 0),
            scoreFloorsOf(StandardTiers)
        )
    }

    @Test
    fun `seeding reads the bands off the tiers it is given`() {
        // The failure this pins: bands used to live in a module-level map keyed by
        // the six standard ids, so editing a tier's band changed nothing and a
        // custom tier could never have one.
        val tiers = listOf(
            TierDefinition("best", "Best", 0xFFE5484D, scoreFloor = 90),
            TierDefinition("good", "Good", 0xFF30A46C, scoreFloor = 70),
            TierDefinition("unbanded", "Unbanded", 0xFF8E8E93)
        )

        val assignment = seedFromScores(
            candidates = listOf(
                candidate(1, score = 95),
                candidate(2, score = 80),
                candidate(3, score = 65)
            ),
            tiers = tiers
        )

        assertEquals("best", assignment.tierOf(1))
        assertEquals("good", assignment.tierOf(2))
        // 65 clears no band, and the tier with no band never collects strays.
        assertNull(assignment.tierOf(3))
    }

    @Test
    fun `a band of zero is not the same as no band`() {
        // Zero accepts every *scored* title; null accepts none. Collapsing the two
        // would make a tier with no band swallow the bottom of the list.
        val tiers = listOf(
            TierDefinition("everything", "Everything", 0xFFE5484D, scoreFloor = 0),
            TierDefinition("none", "None", 0xFF8E8E93)
        )

        val assignment = seedFromScores(
            candidates = listOf(candidate(1, score = 1)),
            tiers = tiers
        )

        assertEquals("everything", assignment.tierOf(1))
        assertNull(assignment.tierOf(2))
    }

    // ---- Display options ---------------------------------------------------

    @Test
    fun `display options survive a round trip`() {
        val display = TierDisplay(
            showTitles = false,
            titlePosition = TierTitlePosition.ON_COVER,
            zoom = 1.5f
        )

        assertEquals(display, decodeDisplay(encodeDisplay(display)))
    }

    @Test
    fun `a display line from an older build reads as the old board`() {
        // No fifth line at all: titles under the cover at 100%, which is exactly
        // what that build drew.
        val decoded = decodeDisplay(null)

        assertTrue(decoded.showTitles)
        assertEquals(TierTitlePosition.UNDER_COVER, decoded.titlePosition)
        assertEquals(1f, decoded.zoom, 0f)
        assertTrue(decoded.hasCaptionBand)
    }

    @Test
    fun `a name on the cover does not reserve a caption band`() {
        // The layout recomputes its rows from this, so getting it wrong leaves a
        // strip of empty background under every poster.
        assertFalse(
            TierDisplay(titlePosition = TierTitlePosition.ON_COVER).hasCaptionBand
        )
        assertFalse(TierDisplay(showTitles = false).hasCaptionBand)
        assertTrue(TierDisplay().hasCaptionBand)
    }

    @Test
    fun `a non-finite zoom is not restored`() {
        // NaN in the layout maths draws nothing at all, so the one value that can
        // produce it is treated as absent.
        assertEquals(1f, decodeDisplay("1,ON_COVER,NaN").zoom, 0f)
        assertEquals(1f, decodeDisplay("1,ON_COVER,Infinity").zoom, 0f)
    }

    @Test
    fun `a document keeps its display options`() {
        val document = TierListDocument(
            id = "list1",
            name = "Mine",
            display = TierDisplay(
                showTitles = false,
                titlePosition = TierTitlePosition.ON_COVER,
                zoom = 1.25f
            )
        )

        assertEquals(
            document,
            decodeTierListDocument("list1", encodeTierListDocument(document))
        )
    }

    // ---- The tier format's fourth field ------------------------------------

    @Test
    fun `a tier's band survives a round trip`() {
        val tier = TierDefinition("s", "Peak", 0xFFE5484D, scoreFloor = 88)

        assertEquals(tier, decodeTiers(encodeTiers(listOf(tier))).single())
    }

    @Test
    fun `a tier written before bands existed keeps its row and gets no band`() {
        // Three parts: label, colour, id. The row is the placements' anchor, so it
        // has to survive even though the fourth field is absent.
        val decoded = decodeTiers("Peak|4294906125|s").single()

        assertEquals("Peak", decoded.label)
        assertEquals("s", decoded.id)
        assertNull(decoded.scoreFloor)
    }

    @Test
    fun `an unreadable band loses the band, not the row`() {
        val decoded = decodeTiers("Peak|4294906125|s|not-a-number").single()

        assertEquals("Peak", decoded.label)
        assertNull(decoded.scoreFloor)
    }

    // ---- The tray's geometry ----------------------------------------------

    @Test
    fun `the tray's declared height is its three parts`() {
        // The board's bottom inset starts with `TIER_TRAY_HEIGHT` and adds the
        // floating toolbar; the tray itself draws its posters at the offsets that
        // add up to this number. A mismatch is a clipped poster or a strip of dead
        // space under the board.
        assertEquals(
            TIER_TRAY_HEIGHT,
            TIER_TRAY_HEADER_HEIGHT + TIER_TRAY_ITEM_HEIGHT + TIER_TRAY_BOTTOM_GAP
        )
    }

    // ---- Tile corners and the export ---------------------------------------

    @Test
    fun `a tile's corner is a share of its width, not a fixed radius`() {
        // The failure this pins: a constant 12dp radius on a 25dp tile at the 35%
        // zoom floor turned every cover into a circle. The radius has to shrink
        // with the tile, or the overview is a tray of pills.
        val default = tierTileCornerRadius(72f)
        val zoomedOut = tierTileCornerRadius(25f)

        assertEquals(72f * 0.12f, default, 0.001f)
        assertEquals(25f * 0.12f, zoomedOut, 0.001f)
        // Under a quarter of the width is what keeps it reading as a square.
        assertTrue(zoomedOut / 25f < 0.25f)
        // And a zero or negative width draws nothing rather than a NaN rect.
        assertEquals(0f, tierTileCornerRadius(0f), 0f)
        assertEquals(0f, tierTileCornerRadius(-10f), 0f)
    }

    @Test
    fun `a very wide tile stops growing its radius`() {
        // A 200% zoom on a tablet would otherwise ask for a 24dp radius on a
        // 200dp tile, which is a different shape from the one the design uses.
        assertEquals(12f, tierTileCornerRadius(200f), 0f)
    }

    @Test
    fun `the export estimate reaches the requested width on a short board`() {
        // The sheet's caption is a promise: this is the same plan and scale maths
        // the renderer runs, so a 4K chip has to report 3840 px (the heap budget
        // only takes width back from boards too tall to hold).
        val assignment = TierAssignment(linkedMapOf(1 to "s"))
        val candidates = listOf(candidate(1, score = 88))

        val ultra = estimateTierExportSize(
            listName = "Mine",
            tiers = StandardTiers,
            assignment = assignment,
            candidates = candidates,
            display = TierDisplay(),
            targetWidth = TIER_EXPORT_WIDTH_ULTRA
        )
        val standard = estimateTierExportSize(
            listName = "Mine",
            tiers = StandardTiers,
            assignment = assignment,
            candidates = candidates,
            display = TierDisplay(),
            targetWidth = TIER_EXPORT_WIDTH_STANDARD
        )

        assertEquals(TIER_EXPORT_WIDTH_ULTRA, ultra?.width)
        assertEquals(TIER_EXPORT_WIDTH_STANDARD, standard?.width)
        assertTrue((ultra?.height ?: 0) > 0)
        // Both qualities describe the same board, so the taller file is the wider
        // one - a mismatch would mean the height was not derived from the layout.
        assertTrue((ultra?.height ?: 0) > (standard?.height ?: 0))
    }

    @Test
    fun `an empty board has no export size to promise`() {
        assertNull(
            estimateTierExportSize(
                listName = "Mine",
                tiers = StandardTiers,
                assignment = TierAssignment(emptyMap()),
                candidates = emptyList(),
                display = TierDisplay(),
                targetWidth = TIER_EXPORT_WIDTH_ULTRA
            )
        )
    }
}
