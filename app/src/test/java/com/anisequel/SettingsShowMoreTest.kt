package com.anisequel

import com.anisequel.ui.screens.settings.hasFullLineToReveal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * When a Settings paragraph is allowed to offer "Show more".
 *
 * The bug this pins: every paragraph that did not fit was collapsed, including
 * the ones whose hidden part was a single short line. Tapping "Show more" on
 * those opened the row by that one line - a flicker, not a reveal - and the
 * button itself was drawn as a bare text line with no press area and no shape,
 * so it read as a caption rather than a control.
 *
 * The rule is geometric, and a pure function of line counts, so it is testable
 * without a device: a paragraph may collapse only when at least one whole line
 * stays hidden. That means the text is longer than the collapsed box by two
 * lines, not one - the last line of a paragraph is the one that may be short
 * enough to hide entirely inside the clipped space.
 */
class SettingsShowMoreTest {

    @Test
    fun `a paragraph with one hidden line shows all of it`() {
        // Two visible, three total: the third line is the only hidden one, and it
        // is the line that made the button open the row by a sliver.
        assertEquals(false, hasFullLineToReveal(collapsedLines = 2, totalLines = 3))
        assertEquals(false, hasFullLineToReveal(collapsedLines = 4, totalLines = 5))
    }

    @Test
    fun `a paragraph hides only when a whole line stays hidden`() {
        assertTrue(hasFullLineToReveal(collapsedLines = 2, totalLines = 4))
        assertTrue(hasFullLineToReveal(collapsedLines = 4, totalLines = 6))
    }

    @Test
    fun `a paragraph that already fits never collapses`() {
        assertEquals(false, hasFullLineToReveal(collapsedLines = 2, totalLines = 2))
        assertEquals(false, hasFullLineToReveal(collapsedLines = 0, totalLines = 6))
    }

    @Test
    fun `the toggle is a capsule with a press area`() {
        val source = source(
            "app/src/main/java/com/anisequel/ui/screens/settings/SettingsComponents.kt"
        )

        assertTrue(
            "the button is back to a text line, so it has no shape and no target",
            source.contains("minimumInteractiveComponentSize()") &&
                source.contains("RoundedCornerShape(percent = 50)")
        )
        assertTrue(
            "the row no longer decides from the measured text whether it can collapse",
            source.contains("rememberHasFullLineToReveal(")
        )
    }

    private fun source(path: String): String {
        val file = File(findProjectRoot(), path)
        return file.readText()
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .replace(Regex("//[^\\n]*"), "")
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