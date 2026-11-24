package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The profile header's scroll-linked motion.
 *
 * ## Why this is a source scan
 *
 * The whole feature is two `graphicsLayer` blocks fed by one lambda: there is no
 * JVM-visible arithmetic to assert, and the failure modes are all silent. A header
 * that reads the list's scroll *while composing* looks identical in a screenshot
 * and recomposes the banner, the avatar request and every stat tile on every
 * scrolled frame. A tab that never passes the offset draws a still header that
 * looks exactly like the one before the feature. A parallax that does not zoom
 * with its own lag exposes a strip of background above the artwork as soon as the
 * page moves. All three are pinned here, the same way [AnimationWiringTest] pins
 * the other draw-phase animations.
 *
 * The values themselves (how far, how fast) are not pinned: they are a feel, not a
 * requirement, and a test that fixes them stops anyone improving the motion.
 */
class ProfileHeaderMotionTest {

    private val root = findProjectRoot()

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val header =
        "app/src/main/java/com/anisequel/ui/screens/profile/ProfileHeaderCard.kt"
    private val home =
        "app/src/main/java/com/anisequel/ui/screens/profile/ProfileHomeTab.kt"
    private val screen =
        "app/src/main/java/com/anisequel/ui/screens/profile/UserProfileScreen.kt"

    @Test
    fun `the header takes the list's scroll as a draw-phase lambda`() {
        val code = source(header)

        assertTrue(
            "the header cannot move with a list it cannot see; pass the offset in",
            code.contains("scrollOffset: () -> Float")
        )
        // The banner's transform must be inside the layer block. Reading the
        // offset above it would subscribe the header's composition to the scroll.
        val bannerLayer = code.substringAfter("modifier = Modifier.graphicsLayer {")
        assertTrue(
            "the parallax is not drawn through a graphicsLayer, so it relayouts the " +
                "header on every scrolled frame",
            bannerLayer.contains("scrollOffset()")
        )
        assertTrue(
            "the artwork does not grow with its own lag, so its top edge lifts off " +
                "the banner and exposes the background",
            bannerLayer.contains("val zoom = 1f + 2f * lag / boxHeight")
        )
        assertTrue(
            "the identity block does not move at all, so the header only scrolls " +
                "away like it always did",
            code.contains("translationX = sweepShift()")
        )
    }

    @Test
    fun `the home tab hands the header its own list offset`() {
        val code = source(home)

        assertTrue(
            "the header is drawn without the list's scroll, so the motion never runs",
            code.contains("scrollOffset = { listState.firstVisibleItemScrollOffset.toFloat() }")
        )
    }

    @Test
    fun `the layout editor is available on every profile`() {
        val code = source(screen)

        assertFalse(
            "the arrangement is the reader's view of a profile, not a property of " +
                "owning it; hiding the editor off your own page makes the preference " +
                "look like it did nothing",
            code.contains("profileLayout = if (isOwnProfile)")
        )
        assertTrue(
            "the page does not draw the reader's arrangement",
            code.contains("profileLayout = profileLayout,")
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