package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two halves of the 1.8.5 artwork work, read off the source.
 *
 * Neither is observable in a unit test: one is a dialog window whose entrance is
 * driven by the first frame it lays out, the other is a backdrop behind a
 * `LazyColumn`. Both fail in the same quiet way when a wire is missing - the page
 * simply looks like it did before - so the calls that connect them are pinned
 * here, in the same spirit as `AnimeCoverThemeWiringTest`.
 */
class InfoBackdropWiringTest {

    private val root = findProjectRoot()

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val infoPage =
        "app/src/main/java/com/anisequel/ui/components/detail/info/InfoPage.kt"
    private val backdrop =
        "app/src/main/java/com/anisequel/ui/components/detail/info/InfoPageBackdrop.kt"
    private val sheet =
        "app/src/main/java/com/anisequel/ui/components/detail/SequelDetailSheet.kt"
    private val viewer =
        "app/src/main/java/com/anisequel/ui/components/detail/FullScreenCoverViewer.kt"
    private val reveal =
        "app/src/main/java/com/anisequel/ui/components/detail/CoverViewerReveal.kt"
    private val artwork =
        "app/src/main/java/com/anisequel/ui/components/detail/CoverViewerArtwork.kt"
    private val poster =
        "app/src/main/java/com/anisequel/ui/components/cards/SequelPoster.kt"

    @Test
    fun `the info page draws the backdrop behind the list`() {
        val page = source(infoPage)

        assertTrue(
            "nothing calls InfoPageBackdrop, so the page below the hero is still " +
                "the flat container colour",
            page.contains("InfoPageBackdrop(")
        )
        assertTrue(
            "the backdrop is not fed the stored source and blur, so Settings " +
                "changes cannot reach it",
            page.contains("source = infoBackdropSource") &&
                page.contains("blur = infoBackdropBlur")
        )
        assertTrue(
            "the backdrop has to be a sibling of the LazyColumn - inside the list " +
                "it would scroll away with the hero instead of staying under the page",
            page.indexOf("InfoPageBackdrop(") < page.indexOf("LazyColumn(")
        )
    }

    @Test
    fun `the backdrop can be a banner, a cover, or nothing`() {
        val file = source(backdrop)

        assertTrue(
            "the banner choice does not read bannerImage",
            file.contains("media.bannerImage")
        )
        assertTrue(
            "the backdrop ignores the source switch",
            file.contains("InfoBackdropSource.BANNER") &&
                file.contains("InfoBackdropSource.COVER") &&
                file.contains("InfoBackdropSource.OFF")
        )
        assertTrue(
            "the blur choice never becomes a blur radius",
            file.contains(".blur(radius)")
        )
        assertTrue(
            "the wash behind the cards uses a hard-coded colour rather than the " +
                "scheme's surface, which breaks in the light theme",
            file.contains("surface.copy(alpha") &&
                !file.contains("Color.Black.copy")
        )
    }

    @Test
    fun `the sheet hands the stored backdrop to the page`() {
        val file = source(sheet)

        assertTrue(
            "SequelDetailSheet collects ThemeSettings but never passes the " +
                "backdrop choice to AnimeInfoPage",
            file.contains("infoBackdropSource = themeSettings.infoBackdropSource") &&
                file.contains("infoBackdropBlur = themeSettings.infoBackdropBlur")
        )
    }

    @Test
    fun `the theme page offers the backdrop controls`() {
        val theme = source("app/src/main/java/com/anisequel/ui/screens/settings/ThemeTab.kt")
        val section = source(
            "app/src/main/java/com/anisequel/ui/screens/settings/InfoBackdropSection.kt"
        )

        assertTrue("the Theme page never draws the section", theme.contains("InfoBackdropSection("))
        assertTrue(
            "a changed backdrop is not offered for Reset appearance",
            theme.contains("infoBackdropSource != InfoBackdropSource.DEFAULT") &&
                theme.contains("infoBackdropBlur != InfoBackdropBlur.DEFAULT")
        )
        assertTrue(
            "the section has no source picker",
            section.contains("info_backdrop_source_row") &&
                section.contains("setInfoBackdropSource(")
        )
        assertTrue(
            "the section has no blur picker",
            section.contains("info_backdrop_blur_row") &&
                section.contains("setInfoBackdropBlur(")
        )
    }

    /**
     * The entrance and the exit are one mechanism: a travelling copy of the
     * artwork that starts at the tapped poster's rectangle and ends at the frame
     * the pager measured. The old code faded and scaled in place, then waited a
     * fixed 220ms before tearing the dialog down - which is why the exit could
     * delete the artwork mid-bounce.
     */
    @Test
    fun `the cover viewer grows out of the tapped poster and shrinks back into it`() {
        val file = source(viewer)

        assertTrue(
            "FullScreenCoverViewer has no origin rectangle, so it cannot know where " +
                "the poster was",
            file.contains("origin: Rect? = null")
        )
        assertTrue(
            "there is no travelling artwork laid out at the root of the dialog",
            source(reveal).contains("fun CoverRevealOverlay(") &&
                source(reveal).contains("cover_reveal_artwork")
        )
        assertTrue(
            "the overlay never reads the measured pager frame",
            file.contains("onFrameBounds") &&
                source(artwork).contains("boundsInWindow")
        )
        assertTrue(
            "the exit does not run the spring back to the origin",
            file.contains("reveal.animateTo(0f, ExpressiveMotion.FastSpatial)")
        )
        assertFalse(
            "the fixed teardown delay is back, so the dialog can be removed before " +
                "the artwork has finished shrinking",
            file.contains("COVER_VIEWER_EXIT_MILLIS")
        )
        assertTrue(
            "the platform's dim is still drawn over the app's own scrim, which " +
                "makes the entrance a cut instead of a fade",
            file.contains("setDimAmount(0f)")
        )
        assertTrue(
            "the poster call site does not pass its measured rectangle",
            source(poster).contains("origin = posterBounds") &&
                source(poster).contains("boundsInWindow")
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
