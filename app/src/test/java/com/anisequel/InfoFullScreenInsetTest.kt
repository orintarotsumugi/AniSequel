package com.anisequel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Info page's edge-to-edge inset ownership.
 *
 * The page is hosted by a full-screen dialog. Applying navigation padding to the
 * page shell and to the floating bar consumed the same bottom band twice, while
 * immersive mode made the system inset APIs return zero and left the hero actions
 * against the glass. These are source-level invariants because the failure is the
 * modifier tree, not a domain value.
 */
class InfoFullScreenInsetTest {

    private val root = findProjectRoot()

    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun `the info list does not consume the navigation band twice`() {
        val page = source(
            "app/src/main/java/com/anisequel/ui/components/detail/info/InfoPage.kt"
        )

        assertFalse(
            "the Info page shell must not add navigationBarsPadding; the dialog " +
                "owns the bar clearance and the list receives it as content padding",
            page.contains(".navigationBarsPadding()")
        )
        assertTrue(
            "the docked top bar needs a cutout-safe inset when system bars are hidden",
            page.contains(".immersiveDisplayInsets(immersive)")
        )
    }

    @Test
    fun `hero actions and the detail capsule lift in immersive mode`() {
        val hero = source(
            "app/src/main/java/com/anisequel/ui/components/detail/info/InfoHero.kt"
        )
        val sheet = source(
            "app/src/main/java/com/anisequel/ui/components/detail/SequelDetailSheet.kt"
        )

        assertTrue(
            "hero controls must move below a cutout when statusBarsPadding is zero",
            hero.contains(".immersiveDisplayInsets(immersive)")
        )
        assertTrue(
            "the bottom capsule needs a physical lift when navigation insets are zero",
            sheet.contains("padding(bottom = if (immersive) 12.dp else 0.dp)")
        )
        assertTrue(
            "content clearance must include the immersive-only lift",
            sheet.contains("if (immersive) 12.dp else 0.dp")
        )
    }

    private fun findProjectRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir") ?: ".")
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties")
    }
}
