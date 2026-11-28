package com.anisequel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The activity card's two pieces of reader-facing state: its ring and its title.
 *
 * ## Why the ring is pinned at source level
 *
 * The ring marks the one row an activity notification opened. It has to outlive
 * the arrival, and it ends when the reader touches that row - both are facts about
 * a composed screen, and this module has no Compose test runtime in the unit-test
 * source set (`androidx.compose.ui.test.junit4` is an `androidTestImplementation`).
 * The failure it replaces was invisible to every other check: the ring faded on a
 * 2.6-second timer, so it was already leaving while the feed was still scrolling
 * to the row. Nothing failed; the mark simply did not do its only job. A guard
 * that reads the wiring is the cheapest thing that can fail if a timer comes back.
 *
 * Same approach as `InfoFloatingBarKeyingTest` and `CoverViewerGalleryTest`.
 */
class ProfileActivityCardTest {

    private val glow = source("app/src/main/java/com/anisequel/ui/screens/profile/ActivityGlow.kt")
    private val tab = source("app/src/main/java/com/anisequel/ui/screens/profile/ProfileActivityTab.kt")
    private val card = source("app/src/main/java/com/anisequel/ui/screens/profile/ProfileActivityCard.kt")
    private val textCard =
        source("app/src/main/java/com/anisequel/ui/screens/profile/ProfileTextActivityCard.kt")

    @Test
    fun `the ring holds its strength while the row is the target`() {
        assertTrue(
            "the glow no longer animates toward a value chosen by `highlighted`; a " +
                "single fixed target is how the one-shot pulse came back",
            glow.contains("val target = if (highlighted) 1f else 0f")
        )
        assertFalse(
            "the ring fades itself out again: something animates it to 0 without " +
                "asking whether the row is still the target",
            glow.contains("animateTo(0f,")
        )
        assertFalse(
            "the ring arrives by snapping to full strength, which is the pulse that " +
                "started fading on the next frame",
            glow.contains("snapTo(1f)")
        )
    }

    @Test
    fun `the mark ends on a press on the row, not on a timer`() {
        assertFalse(
            "the tab still clears the mark on a delay instead of on the reader's touch",
            tab.contains("ACTIVITY_GLOW")
        )
        assertTrue(
            "a press on the glowing card is not forwarded to the tab that owns the mark",
            tab.contains("onGlowTouched = { id -> if (glowActivityId.value == id)")
        )
        assertTrue(
            "the dismiss helper no longer listens to the card's own press",
            glow.contains("interactions.first { it is PressInteraction.Press }")
        )
        // Both shapes draw the ring, so both must be able to end it: the text post
        // and the list row are the same mark on two layouts.
        assertTrue(
            "a title card no longer ends the ring on a press",
            card.contains("DismissGlowOnTouch(")
        )
        assertTrue(
            "a text post no longer ends the ring on a press",
            textCard.contains("DismissGlowOnTouch(")
        )
    }

    @Test
    fun `a row with no title object still gets a name`() {
        assertTrue(
            "the card stopped using the app's title fallback for a media node",
            card.contains("val titleLabel = media.displayName")
        )
        // `title?.displayTitle.orEmpty()` drew an empty line and read "Open  details"
        // with two spaces where the name belongs.
        assertFalse(
            "a node with no title object is back to an empty label",
            card.contains("displayTitle.orEmpty()")
        )
    }

    /** Comments stripped first: every assertion here is about code, not about prose. */
    private fun source(path: String): String =
        Regex("""/[*][\s\S]*?[*]/""")
            .replace(File(findProjectRoot(), path).readText()) { "" }
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

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
