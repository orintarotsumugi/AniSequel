package com.anisequel

import com.anisequel.domain.tier.StandardTiers
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierFilter
import com.anisequel.domain.tier.TierLayoutOptions
import com.anisequel.domain.tier.TierListSvg
import com.anisequel.domain.tier.layoutTierList
import com.anisequel.domain.tier.seedFromScores
import com.anisequel.domain.tier.studioFacetOptions
import com.anisequel.domain.tier.unseedableCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the tier list's rules, its geometry and its export.
 *
 * The seeding rules matter because a tier list is presented as a person's
 * opinion in the same typography as every other row, and a default that quietly
 * files a title under F because AniList had no score for it is not an opinion
 * anybody formed.
 *
 * The SVG assertions matter because the failure is total and silent: one
 * unescaped `&` in one title makes the document not well-formed, and a renderer
 * shows nothing at all rather than the part that was valid.
 */
class TierListTest {

    /**
     * Defaulted to null so a test that is about layout does not have to state a
     * score it does not care about. Every call that omitted it was written
     * assuming the parameter had a default, which is the kind of assumption the
     * compiler is there to catch.
     */
    private fun candidate(id: Int, score: Int? = null, title: String = "Title $id") =
        TierCandidate(mediaId = id, title = title, averageScore = score)

    // ---- Seeding -----------------------------------------------------------

    @Test
    fun `scores land in the conventional bands`() {
        val assignment = seedFromScores(
            candidates = listOf(
                candidate(1, 90), candidate(2, 80), candidate(3, 70),
                candidate(4, 60), candidate(5, 50), candidate(6, 30)
            )
        )

        assertEquals("s", assignment.tierOf(1))
        assertEquals("a", assignment.tierOf(2))
        assertEquals("b", assignment.tierOf(3))
        assertEquals("c", assignment.tierOf(4))
        assertEquals("d", assignment.tierOf(5))
        assertEquals("f", assignment.tierOf(6))
    }

    @Test
    fun `an unrated title is left unfiled rather than sent to the bottom`() {
        // AniList answers 0 for nobody-has-scored-it. Filing those into F would
        // assert that a planning-list entry is bad, in the same typeface as the
        // judgements the person actually made.
        val assignment = seedFromScores(
            candidates = listOf(candidate(1, null), candidate(2, 0), candidate(3, 88))
        )

        assertNull(assignment.tierOf(1))
        assertNull(assignment.tierOf(2))
        assertEquals("s", assignment.tierOf(3))
        assertEquals(2, unseedableCount(listOf(candidate(1, null), candidate(2, 0))))
    }

    @Test
    fun `a tier with no floor is never used`() {
        // A tier the person added but has not given a band to stays empty rather
        // than collecting everything that failed to match elsewhere. It used to
        // inherit one, because the floors were positional.
        val tiers = listOf(
            TierDefinition("top", "Favourite", 0xFFE5484D),
            TierDefinition("rest", "Rest", 0xFF8E8E93)
        )
        val assignment = seedFromScores(candidates = listOf(candidate(1, 95)), tiers = tiers)

        assertNull(assignment.tierOf(1))
    }

    @Test
    fun `seeding into a custom tier list uses its own bands`() {
        val tiers = listOf(
            TierDefinition("mid", "Mid", 0xFF30A46C),
            TierDefinition("high", "High", 0xFFE5484D)
        )
        val assignment = seedFromScores(
            candidates = listOf(candidate(1, 95), candidate(2, 20)),
            tiers = tiers,
            floors = mapOf("high" to 80, "mid" to 10)
        )

        assertEquals("high", assignment.tierOf(1))
        assertEquals("mid", assignment.tierOf(2))
    }

    @Test
    fun `a deleted tier stops collecting titles rather than passing them down`() {
        // The standard list's bands are keyed by id, so deleting "F" means a
        // title that would have been F is now unfiled. Handing it to "D" would
        // assert a judgement nobody made.
        val withoutF = StandardTiers.filter { it.id != "f" }
        val assignment = seedFromScores(
            candidates = listOf(candidate(1, 10)),
            tiers = withoutF
        )

        assertNull(assignment.tierOf(1))
    }

    @Test
    fun `an empty tier list files nothing and does not divide by zero`() {
        val assignment = seedFromScores(candidates = listOf(candidate(1, 90)), tiers = emptyList())

        assertTrue(assignment.isEmpty)
    }

    // ---- Assignment --------------------------------------------------------

    @Test
    fun `members come back in insertion order so a drag cannot reshuffle a row`() {
        val assignment = TierAssignment(
            mapOf(5 to "b", 3 to "b", 9 to "b", 1 to "a")
        )

        assertEquals(listOf(5, 3, 9), assignment.membersOf("b"))
    }

    // ---- Layout ------------------------------------------------------------

    @Test
    fun `an empty tier list still lays out every row`() {
        val layout = layoutTierList(
            tiers = StandardTiers,
            assignment = TierAssignment(),
            candidates = emptyList(),
            title = "My Tier List"
        )

        assertEquals(StandardTiers.size, layout.rows.size)
        assertTrue(layout.isEmpty)
        // A zero-height drawing is an export no viewer can show.
        assertTrue("height must be positive", layout.height > 0f)
    }

    @Test
    fun `rows stack downward and never overlap`() {
        val layout = layoutTierList(
            tiers = StandardTiers,
            assignment = TierAssignment(mapOf(1 to "s", 2 to "a", 3 to "a")),
            candidates = listOf(candidate(1), candidate(2), candidate(3)),
            title = "T"
        )

        layout.rows.zipWithNext { upper, lower ->
            assertTrue(
                "row ${upper.tierId} ends at ${upper.bottom} which overlaps ${lower.tierId} at ${lower.y}",
                upper.bottom <= lower.y + 0.01f
            )
        }
    }

    @Test
    fun `items stay inside the row that owns them`() {
        val options = TierLayoutOptions(width = 720f, itemWidth = 60f)
        val assignment = TierAssignment((1..40).associateWith { "s" })
        val layout = layoutTierList(
            tiers = listOf(StandardTiers.first()),
            assignment = assignment,
            candidates = (1..40).map { candidate(it) },
            title = "T",
            options = options
        )

        val row = layout.rows.single()
        layout.items.forEach { box ->
            assertTrue("item ${box.mediaId} starts left of the row", box.x >= row.x - 0.01f)
            assertTrue(
                "item ${box.mediaId} runs past the row's right edge",
                box.right <= row.x + row.width + 0.01f
            )
            assertTrue("item ${box.mediaId} is not inside its row vertically", box.bottom <= row.bottom + 0.01f)
        }
    }

    @Test
    fun `a tall row grows rather than clipping its last line`() {
        val layout = layoutTierList(
            tiers = StandardTiers,
            assignment = TierAssignment((1..60).associateWith { "s" }),
            candidates = (1..60).map { candidate(it) },
            title = "T",
            options = TierLayoutOptions(width = 720f, itemWidth = 60f)
        )

        val crowded = layout.rows.first { it.tierId == "s" }
        val empty = layout.rows.first { it.tierId == "a" }

        assertTrue("a 60-item row must be taller than an empty one", crowded.height > empty.height)
        assertTrue(layout.items.count { it.y >= crowded.y && it.bottom <= crowded.bottom } == 60)
    }

    @Test
    fun `a tile carries its poster height, not just its full height`() {
        // Found by reading the SVG writer against the layout: it recovered the
        // poster by multiplying `height` by 1.5, but `height` is poster *plus
        // caption*. At the default options that inflated every poster by 86% and
        // put the caption baseline inside the artwork.
        val options = TierLayoutOptions(itemWidth = 72f, captionHeight = 26f)
        val layout = layoutTierList(
            tiers = StandardTiers,
            assignment = TierAssignment(mapOf(1 to "s")),
            candidates = listOf(candidate(1)),
            title = "T",
            options = options
        )

        val box = layout.boxByMediaId.getValue(1)
        assertEquals(options.posterHeight, box.posterHeight, 0.001f)
        assertEquals(
            "the poster must not be taller than the whole tile",
            true,
            box.posterHeight < box.height
        )
        assertEquals(
            "tile height is poster + caption",
            options.posterHeight + options.captionHeight,
            box.height,
            0.001f
        )
    }

    @Test
    fun `every item is reachable through the id map`() {
        val layout = layoutTierList(
            tiers = StandardTiers,
            assignment = TierAssignment(mapOf(42 to "b")),
            candidates = listOf(candidate(42)),
            title = "T"
        )

        assertEquals(42, layout.boxByMediaId.getValue(42).mediaId)
    }

    // ---- SVG ---------------------------------------------------------------

    private fun sampleSvg(
        assignment: TierAssignment,
        candidates: List<TierCandidate>,
        posters: Map<Int, String> = emptyMap(),
        tiers: List<TierDefinition> = StandardTiers
    ): String = TierListSvg.write(
        layout = layoutTierList(
            tiers = tiers,
            assignment = assignment,
            candidates = candidates,
            title = "Test Tier List"
        ),
        tiers = tiers,
        candidates = candidates,
        embeddedPosters = posters
    )

    @Test
    fun `the export is a complete svg document`() {
        val svg = sampleSvg(
            assignment = TierAssignment(mapOf(1 to "s")),
            candidates = listOf(candidate(1))
        )

        assertTrue(svg.startsWith("<?xml"))
        assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""))
        assertTrue(svg.trimEnd().endsWith("</svg>"))
    }

    @Test
    fun `an ampersand in a title is escaped`() {
        // The failure this guards is total, not partial: one unescaped `&` makes
        // the whole document not well-formed and the renderer shows nothing.
        val svg = sampleSvg(
            assignment = TierAssignment(mapOf(1 to "s")),
            candidates = listOf(candidate(1, title = "Bungo & Beef"))
        )

        assertTrue(svg.contains("Bungo &amp; Beef"))
        assertFalse("a raw ampersand would break the document", svg.contains("Bungo & Beef"))
    }

    @Test
    fun `angle brackets and quotes in a title are escaped`() {
        val svg = sampleSvg(
            assignment = TierAssignment(mapOf(1 to "s")),
            candidates = listOf(candidate(1, title = "<b>\"Bold\"</b>"))
        )

        assertTrue(svg.contains("&lt;b&gt;"))
        assertTrue(svg.contains("&quot;Bold&quot;"))
    }

    @Test
    fun `an embedded poster becomes a data uri image`() {
        val svg = sampleSvg(
            assignment = TierAssignment(mapOf(1 to "s")),
            candidates = listOf(candidate(1)),
            posters = mapOf(1 to "data:image/jpeg;base64,AAAA")
        )

        assertTrue(svg.contains("<image"))
        assertTrue(svg.contains("data:image/jpeg;base64,AAAA"))
    }

    @Test
    fun `a missing poster leaves a tile rather than a hole`() {
        val svg = sampleSvg(
            assignment = TierAssignment(mapOf(1 to "s")),
            candidates = listOf(candidate(1))
        )

        assertFalse("no image element without a poster", svg.contains("<image"))
        assertTrue("the caption is still drawn", svg.contains("Title 1"))
    }

    @Test
    fun `a row whose tier was deleted is not drawn`() {
        // The layout and the tier list can disagree mid-edit. Resurrecting a
        // deleted tier is worse than omitting its row.
        val layout = layoutTierList(
            tiers = StandardTiers,
            assignment = TierAssignment(),
            candidates = emptyList(),
            title = "T"
        )
        val svg = TierListSvg.write(
            layout = layout,
            tiers = listOf(StandardTiers.first()),
            candidates = emptyList()
        )

        assertTrue(svg.contains(">S<"))
        assertFalse(svg.contains(">A<"))
    }

    @Test
    fun `coordinates never carry a locale decimal comma`() {
        // A comma inside an attribute value makes every conforming XML parser
        // reject the file, so an export would work in the country it was made in
        // and nowhere else.
        val svg = sampleSvg(
            assignment = TierAssignment(mapOf(1 to "s")),
            candidates = listOf(candidate(1))
        )

        // Numeric attributes only. A comma in an attribute *value* is only fatal
        // where the value is a number; `font-family` is a comma-separated fallback
        // list by definition, and asserting on every attribute failed on the one
        // string in this document that is supposed to contain commas.
        val numericAttribute = Regex("""\b(x|y|x1|x2|y1|y2|width|height|rx|ry|font-size)="([^"]*)"""")
        numericAttribute.findAll(svg).forEach { match ->
            val value = match.groupValues[2]
            assertFalse(
                "'${match.groupValues[1]}' carries a locale comma: $value",
                value.contains(",")
            )
        }
        // The viewBox is two numbers and is the other place a locale leaks in.
        val viewBox = Regex("""viewBox="([^"]*)"""").find(svg)?.groupValues?.get(1)
        if (viewBox != null) {
            assertFalse("viewBox carries a locale comma: $viewBox", viewBox.contains(","))
        }
    }

    // ---- Genre and studio filters ------------------------------------------

    @Test
    fun `a genre filter keeps the union of the selected genres`() {
        val action = TierCandidate(mediaId = 1, title = "Action", genres = listOf("Action"))
        val comedy = TierCandidate(mediaId = 2, title = "Comedy", genres = listOf("Comedy"))
        val both = TierCandidate(
            mediaId = 3,
            title = "Both",
            genres = listOf("Action", "Comedy")
        )

        val comedyOnly = TierFilter.Default.copy(genres = setOf("Comedy"))
        assertFalse(comedyOnly.allows(action))
        assertTrue(comedyOnly.allows(comedy))
        assertTrue(comedyOnly.allows(both))

        val either = comedyOnly.copy(genres = setOf("Action", "Comedy"))
        assertTrue(either.allows(action))
        assertTrue(either.allows(comedy))
    }

    @Test
    fun `a title with no genres only passes when no genre is selected`() {
        val unknown = TierCandidate(mediaId = 1, title = "Unknown")

        assertTrue(TierFilter.Default.allows(unknown))
        assertFalse(TierFilter.Default.copy(genres = setOf("Action")).allows(unknown))
    }

    @Test
    fun `the studio filter is off by default and ignores a stored selection`() {
        val mappa = TierCandidate(mediaId = 1, title = "Mappa", studios = listOf("MAPPA"))
        val filter = TierFilter.Default.copy(studios = setOf("Bones"))

        // Off is the default, and a selection made while the section was open
        // must not keep filtering after it is switched off.
        assertFalse(filter.studiosEnabled)
        assertTrue(filter.allows(mappa))

        val enabled = filter.withStudiosEnabled(true)
        assertFalse(enabled.allows(mappa))
        assertTrue(enabled.copy(studios = setOf("MAPPA")).allows(mappa))
    }

    @Test
    fun `studio options are ordered by how many titles each studio made`() {
        val options = studioFacetOptions(
            listOf(
                TierCandidate(mediaId = 1, title = "One", studios = listOf("Bones")),
                TierCandidate(mediaId = 2, title = "Two", studios = listOf("MAPPA")),
                TierCandidate(mediaId = 3, title = "Three", studios = listOf("MAPPA")),
                TierCandidate(mediaId = 4, title = "Four", studios = listOf(" MAPPA "))
            )
        )

        assertEquals(listOf("MAPPA", "Bones"), options.map { it.value })
        assertEquals(3, options.first().count)
        assertEquals("MAPPA", options.first().label)
    }

    @Test
    fun `a genre or studio listed twice on one title is counted once`() {
        val options = studioFacetOptions(
            listOf(
                TierCandidate(
                    mediaId = 1,
                    title = "Duplicated",
                    studios = listOf("MAPPA", "MAPPA")
                )
            )
        )

        assertEquals(1, options.single().count)
    }

    @Test
    fun `show everything clears genre and studio choices`() {
        val filtered = TierFilter.Default
            .withGenre("Action", true)
            .withStudio("MAPPA", true)
            .withStudiosEnabled(true)

        val everything = TierFilter.ShowEverything
        assertTrue(everything.genres.isEmpty())
        assertTrue(everything.studios.isEmpty())
        assertFalse(everything.studiosEnabled)
        assertFalse(everything.isDefault)
        assertTrue(everything.hiddenListStatuses.isEmpty())
        assertTrue(filtered.genres.isNotEmpty())
    }
}