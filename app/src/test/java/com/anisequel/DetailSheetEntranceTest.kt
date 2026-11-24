package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The title page's dim, and who owns its entrance and exit.
 *
 * Neither bug is observable in a JVM test: the dim is a window-manager attribute
 * that is applied and removed outside the composition, and the replay only shows
 * when a relation replaces the subject while the dialog is still on screen. What
 * can be pinned is the shape of the fix - the platform dim is zeroed before the
 * page's own scrim is drawn, and neither the entrance nor the close is keyed by
 * the subject any more.
 */
class DetailSheetEntranceTest {

    private val root = findProjectRoot()

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val sheet =
        "app/src/main/java/com/anisequel/ui/components/detail/SequelDetailSheet.kt"
    private val entrance =
        "app/src/main/java/com/anisequel/ui/components/detail/SequelDetailEntrance.kt"

    @Test
    fun `the page draws its own scrim and removes the platform one`() {
        val helper = source(entrance)

        assertTrue(
            "the platform dim is never zeroed, so the window still cuts to black " +
                "before the page has faded in",
            helper.contains("setDimAmount(0f)")
        )
        assertTrue(
            "nothing draws the scrim the removed window dim used to draw",
            helper.contains("SCRIM_ALPHA") && helper.contains("Color.Black")
        )
        assertTrue(
            "the scrim does not follow the page's own progress",
            helper.contains("alpha = SCRIM_ALPHA * entrance.value")
        )
    }

    @Test
    fun `the entrance and the close belong to the sheet, not the subject`() {
        val page = source(sheet)

        assertTrue(
            "the page does not take the shared entrance",
            page.contains("rememberSheetEntrance()")
        )
        assertTrue(
            "the page does not take the shared close",
            page.contains("rememberSheetClose(entrance, finishDismiss)")
        )
        assertTrue("the scrim is not composed behind the page", page.contains("SheetScrim(entrance)"))
        assertFalse(
            "the entrance is keyed by the subject again, so following a relation " +
                "replays the whole open",
            page.contains("remember(subject.mediaId, subject.media.type) { Animatable(0f) }")
        )
        assertTrue(
            "the close is not keyed by its own flag, so a relation tapped during " +
                "the exit can cancel it",
            source(entrance).contains("LaunchedEffect(closing)")
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
