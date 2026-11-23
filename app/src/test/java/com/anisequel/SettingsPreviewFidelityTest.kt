package com.anisequel

import com.anisequel.domain.ui.LibraryViewMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Settings preview promises the page the app draws.
 *
 * ## Why this is a test and not a review note
 *
 * A preview is the only copy of a layout nobody taps, so when it drifts nothing
 * looks broken: the reader chooses Shelf after being shown names under every cover,
 * or picks Mosaic after being shown the flat two-up grid it sits beside. Both had
 * happened by 2.5.8 - the card preview printed a caption under Shelf and drew
 * Mosaic and Grouped covers as Big cards, and the layout preview drew the Compact
 * grid without the names the real grid prints.
 *
 * The three surfaces now read the same answers on [LibraryViewMode], and this pins
 * both halves: the answers themselves, and that each surface asks the mode for them
 * rather than writing a fourth copy. A source check is the right shape here because
 * none of these surfaces can be composed in a JVM test, and the failure the test
 * exists to catch - "the preview is a picture of a different page" - is invisible to
 * a compile, a lint pass and a screenshot.
 */
class SettingsPreviewFidelityTest {

    private fun source(path: String): String {
        val file = File(findProjectRoot(), path)
        assertTrue(
            "could not read ${file.path}; a guard passing on a missing file is the " +
                "same failure it exists to prevent",
            file.isFile
        )
        return file.readText()
    }

    private val cardPreview
        get() = source(
            "app/src/main/java/com/anisequel/ui/screens/settings/CardDesignPreview.kt"
        )

    private val layoutPreview
        get() = source(
            "app/src/main/java/com/anisequel/ui/screens/settings/LibraryLayoutPreview.kt"
        )

    private val libraryGrid
        get() = source(
            "app/src/main/java/com/anisequel/ui/screens/library/NovaLibraryCards.kt"
        )

    /**
     * Shelf and Cover wall print no name; every other mode prints one.
     *
     * That is the Library's rule, and it is the rule both previews have to draw.
     * The Compact grid used to be drawn caption-free in the layout preview while the
     * Library printed its names, and the card preview printed a name under Shelf
     * while the Library printed none - two different wrong answers to one question.
     */
    @Test
    fun `the caption policy is one answer for the grid and both previews`() {
        assertFalse(
            "Shelf never prints a caption in the Library, so no preview may draw one",
            LibraryViewMode.SHELF.showsCaption
        )
        assertFalse(
            "Cover wall is caption-free by definition",
            LibraryViewMode.COVERS.showsCaption
        )
        LibraryViewMode.entries
            .filter { it != LibraryViewMode.SHELF && it != LibraryViewMode.COVERS }
            .forEach { mode ->
                assertTrue(
                    "${mode.displayName} prints a caption in the Library, so the " +
                        "previews must draw one",
                    mode.showsCaption
                )
            }

        assertTrue(
            "the Library grid stopped asking the mode whether it prints a caption, " +
                "so the previews have a second rule to drift from",
            libraryGrid.contains("showTitle = viewMode.showsCaption")
        )
        assertTrue(
            "the card-design preview wrote its own caption rule again - it used to " +
                "print a name under Shelf",
            cardPreview.contains("showTitle = mode.showsCaption")
        )
        assertTrue(
            "the layout preview wrote its own caption rule again - Compact was drawn " +
                "caption-free while the Library prints its names",
            layoutPreview.contains("current.showsCaption")
        )
    }

    /**
     * Mosaic's hero, Grouped covers' heading and the three row modes.
     *
     * Mosaic and Grouped covers used to fall into the card preview's plain-grid arm,
     * which made them look like Big cards. The previews now branch on these facts, so
     * a mode that gains a shape gains its preview with it.
     */
    @Test
    fun `a mode's arrangement is previewed as itself, not as its neighbour`() {
        assertEquals(
            "only Mosaic breaks its rows with a full-width cover",
            listOf(LibraryViewMode.MOSAIC),
            LibraryViewMode.entries.filter { it.hasHeroCells }
        )
        assertEquals(
            "only Grouped covers sorts a poster grid under headings",
            listOf(LibraryViewMode.GROUPED_COVERS),
            LibraryViewMode.entries.filter { it.hasStatusHeadings }
        )
        assertEquals(
            "the row modes are drawn by the Library's list row, not a poster grid",
            setOf(LibraryViewMode.LIST, LibraryViewMode.GROUPED, LibraryViewMode.TABLE),
            LibraryViewMode.entries.filter { it.drawsRows }.toSet()
        )

        assertTrue(
            "the card-design preview no longer draws Mosaic as a hero over a pair",
            cardPreview.contains("viewMode.hasHeroCells -> PreviewMosaicShape(")
        )
        assertTrue(
            "the card-design preview no longer draws Grouped covers' heading",
            cardPreview.contains(
                "viewMode.hasStatusHeadings -> PreviewGroupedCoversShape("
            )
        )
        assertTrue(
            "the Mosaic hero lost the landscape proportion the Library gives it",
            cardPreview.contains("MOSAIC_HERO_ASPECT")
        )
    }

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }
}
