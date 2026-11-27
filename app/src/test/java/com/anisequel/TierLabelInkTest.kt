package com.anisequel

import com.anisequel.domain.tier.TIER_LABEL_INK_DARK
import com.anisequel.domain.tier.TIER_LABEL_INK_LIGHT
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierColorPalette
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierListSvg
import com.anisequel.domain.tier.contrastRatio
import com.anisequel.domain.tier.layoutTierList
import com.anisequel.domain.tier.tierLabelInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the gutter label's ink.
 *
 * The board and both exporters used to draw white on every tier colour. On the
 * palette's amber that is 1.6:1, which is not a quiet label - it is a missing one.
 * The requirement is the WCAG small-text floor: whichever ink is chosen has to be
 * at least 4.5:1 against the fill it is drawn on, on every colour the palette can
 * hand a tier and on the pale custom colours the picker allows.
 */
class TierLabelInkTest {

    @Test
    fun `every palette colour is legible in the ink it is given`() {
        TierColorPalette.forEach { fill ->
            val ink = tierLabelInk(fill)
            val ratio = contrastRatio(fill, ink)
            assertTrue(
                "fill #${fill.toString(16)} gets ink #${ink.toString(16)} at $ratio:1",
                ratio >= 4.5
            )
        }
    }

    @Test
    fun `amber takes the dark ink and brown keeps the light one`() {
        // The two ends of the palette: 13.3:1 black on amber, 7.4:1 white on brown.
        assertEquals(TIER_LABEL_INK_DARK, tierLabelInk(0xFFFFC53DL))
        assertEquals(TIER_LABEL_INK_LIGHT, tierLabelInk(0xFF6F4E37L))
    }

    @Test
    fun `the export draws the same ink as the board`() {
        // The export is meant to be the screen as a file. A white label here would
        // be the exporter disagreeing with the board about exactly the thing this
        // change is for.
        val tiers = listOf(TierDefinition("b", "B", 0xFFFFC53DL))
        val candidates = listOf(TierCandidate(mediaId = 1, title = "Title"))
        val svg = TierListSvg.write(
            layout = layoutTierList(
                tiers = tiers,
                assignment = TierAssignment(mapOf(1 to "b")),
                candidates = candidates,
                title = "Ink"
            ),
            tiers = tiers,
            candidates = candidates
        )

        assertTrue("the label on amber should be black", svg.contains("fill=\"#000000\""))
    }
}
