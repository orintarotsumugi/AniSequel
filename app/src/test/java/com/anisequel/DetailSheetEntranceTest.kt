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
    private val pageFile =
        "app/src/main/java/com/anisequel/ui/components/detail/SequelDetailPage.kt"

    /** The sheet owns the state; the page file owns the layout it hands to it. */
    private fun pageSource(): String = source(sheet) + source(pageFile)
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

    /**
     * The scrim's black has to be *inside* its alpha layer.
     *
     * `background` and `graphicsLayer` are both draw modifiers, and the chain's
     * order is the direction: a background placed before the layer paints under
     * it, outside the alpha. The scrim was written that way round, so the 60%
     * never followed the page at all - it sat over the list for the page's whole
     * life and vanished when the window was removed, which is the cut it replaced,
     * moved to the close. Order is the entire fix, and the entire check.
     */
    @Test
    fun `the scrim's alpha layer wraps the black it fades`() {
        val scrim = source(entrance)
            .substringAfter("fun SheetScrim(")
            .substringBefore("fun Modifier.sheetEntrance(")
        val layer = scrim.indexOf(".graphicsLayer")
        val ink = scrim.indexOf(".background(")

        assertTrue("the scrim lost its alpha layer or its ink:\n$scrim", layer >= 0 && ink >= 0)
        assertTrue(
            "the black is painted outside the alpha layer, so the dim never fades " +
                "and the close ends with it switching off",
            layer < ink
        )
    }

    @Test
    fun `the entrance and the close belong to the sheet, not the subject`() {
        val page = pageSource()

        assertTrue(
            "the page does not take the shared entrance",
            page.contains("rememberSheetEntrance(")
        )
        assertTrue(
            "the page does not take the shared close",
            page.contains("rememberSheetClose(") && page.contains("onDismissed = finishDismiss")
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

    @Test
    fun `the page's entrance stands aside while a poster flies`() {
        val page = pageSource()

        assertTrue(
            "the page starts fading in before the copy has its picture, so the two " +
                "halves of the open arrive at different times",
            page.contains("rememberSheetEntrance(released = coverFlight.entranceGate)")
        )
        assertTrue(
            "the page still scales and rises under the flying poster, so the " +
                "copy lands on a rectangle that has moved",
            page.contains("sheetEntrance(entrance, travels = !coverFlight.travels)")
        )
        assertTrue(
            "the close does not reverse the flight, so closing cuts instead of " +
                "putting the cover back where it came from",
            page.contains("travel = coverFlight.travel") &&
                page.contains("onTravelStart = coverFlight::begin")
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
