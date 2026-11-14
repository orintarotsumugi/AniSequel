package com.anisequel

import com.anisequel.domain.card.CardScorePlacement
import com.anisequel.domain.card.CardTitlePlacement
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.card.PosterCorner
import com.anisequel.domain.card.metaCornerFor
import com.anisequel.domain.card.scoreCornerOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five card layouts, as the table every surface reads.
 *
 * ## Why this is worth pinning on a JVM
 *
 * A card style is not a string that gets drawn somewhere; it is four facts that the
 * Library grid, the dashboard's rows and the settings preview all act on. The two
 * failures that matter are invisible in a screenshot of one screen: two styles that
 * turn out to mean the same thing (so the picker offers a choice with no difference),
 * and a style that puts its rating in the same corner the Library already occupies
 * (so one of the two capsules is drawn under the other, and which one vanishes
 * depends on the surface rather than on the style).
 *
 * Both are properties of the table, so both are checked here.
 */
class MediaCardStyleTest {

    @Test
    fun `the five styles are five different layouts`() {
        val signatures = MediaCardStyle.entries.map {
            Triple(it.titlePlacement, it.scorePlacement, it.bordered to it.glowing)
        }
        assertEquals(
            "two styles with the same placement and the same frame are one style " +
                "drawn twice, and the picker offers a choice with no difference",
            MediaCardStyle.entries.size,
            signatures.distinct().size
        )
    }

    @Test
    fun `every style is described by something the reader can judge`() {
        MediaCardStyle.entries.forEach { style ->
            assertTrue("${style.name} has no display name", style.displayName.isNotBlank())
            assertTrue(
                "${style.name} has no blurb, so the chip is a word and not a description",
                style.blurb.isNotBlank()
            )
            assertTrue(
                "${style.name} stores a label rather than a stable key",
                style.storageValue == style.storageValue.lowercase() &&
                    !style.storageValue.contains(' ')
            )
        }
    }

    @Test
    fun `the rating is on the artwork in exactly the two styles that say so`() {
        listOf(
            MediaCardStyle.DEFAULT to false,
            MediaCardStyle.SAIKOU to true,
            MediaCardStyle.EXOTIC to false,
            MediaCardStyle.MINIMAL_EXOTIC to false,
            MediaCardStyle.MODERN to true
        ).forEach { (style, onPoster) ->
            assertEquals("${style.name} as a poster rating", onPoster, style.scoreOnPoster)
            assertEquals(
                "${style.name} corner agrees with the flag",
                onPoster,
                scoreCornerOf(style) != null
            )
        }
    }

    /**
     * The collision this whole function exists to prevent.
     *
     * The Library's progress pill has always been top-start. A style that puts the
     * rating there has to move the pill, or one of the two numbers is hidden - and
     * the hidden one is the one the reader was scanning the grid for.
     */
    @Test
    fun `a style that takes the pill's corner moves the pill`() {
        assertEquals(PosterCorner.TOP_START, metaCornerFor(MediaCardStyle.DEFAULT))
        assertEquals(PosterCorner.TOP_START, metaCornerFor(MediaCardStyle.SAIKOU))
        assertEquals(PosterCorner.TOP_START, metaCornerFor(MediaCardStyle.EXOTIC))
        assertEquals(PosterCorner.TOP_START, metaCornerFor(MediaCardStyle.MINIMAL_EXOTIC))
        assertEquals(
            "Modern puts the rating top-start and its title on the bottom edge, so " +
                "the progress pill belongs at top-end",
            PosterCorner.TOP_END,
            metaCornerFor(MediaCardStyle.MODERN)
        )
        // Bottom-start was the old answer and it was wrong for the one style that
        // overlays its title: the pill landed on the words it was meant to sit
        // beside. Pinned so the simpler "opposite end of the same edge" rule does
        // not come back.
        assertNotEquals(PosterCorner.BOTTOM_START, metaCornerFor(MediaCardStyle.MODERN))

        MediaCardStyle.entries.forEach { style ->
            assertFalse(
                "${style.name} would stack the rating and the surface's own badge",
                scoreCornerOf(style) != null && scoreCornerOf(style) == metaCornerFor(style)
            )
        }
    }

    @Test
    fun `only the two overlaid styles put the title on the artwork`() {
        assertEquals(
            "the name belongs to the picture in exactly these two, and in no others",
            listOf(MediaCardStyle.MINIMAL_EXOTIC, MediaCardStyle.MODERN),
            MediaCardStyle.entries.filter {
                it.titlePlacement == CardTitlePlacement.OVERLAY_BOTTOM
            }
        )
    }

    @Test
    fun `an unknown stored value falls back rather than dropping the card`() {
        // A style written by a newer build, or a value edited into the file by
        // something that is not this app. Throwing would take the Library down for
        // one bad string; the plain layout is the honest answer.
        assertEquals(MediaCardStyle.DEFAULT, MediaCardStyle.fromStorage("a_style_this_build_does_not_have"))
        assertEquals(MediaCardStyle.DEFAULT, MediaCardStyle.fromStorage(null))
        MediaCardStyle.entries.forEach { style ->
            assertEquals(style, MediaCardStyle.fromStorage(style.storageValue))
        }
        assertEquals(CardScorePlacement.BELOW_TITLE, MediaCardStyle.DEFAULT.scorePlacement)
    }
}
