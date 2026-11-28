package com.anisequel

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Nova page's artwork and its waiting states, read off the source.
 *
 * None of these is observable in a unit test: the first is a draw transform that
 * is clipped or not, the second is which component draws a banner, the third is
 * which value feeds an amplitude, and the fourth is whether a list item exists
 * before its data does. All four failed in the same quiet way - the page simply
 * looked wrong - so the calls that fix them are pinned here, in the same spirit
 * as [InfoBackdropWiringTest] and [AnimationWiringTest].
 */
class NovaArtworkLoadingTest {

    private val root = findProjectRoot()

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val hero =
        "app/src/main/java/com/anisequel/ui/components/detail/info/NovaInfoHero.kt"
    private val preview =
        "app/src/main/java/com/anisequel/ui/components/detail/info/InfoAppearanceEditor.kt"
    private val wave =
        "app/src/main/java/com/anisequel/ui/components/detail/info/WavyProgressBar.kt"
    private val page =
        "app/src/main/java/com/anisequel/ui/components/detail/info/NovaInfoPage.kt"
    private val cards =
        "app/src/main/java/com/anisequel/ui/components/detail/info/NovaInfoCards.kt"

    /**
     * The hero's picture is over-covered and translated for the parallax, and a
     * `graphicsLayer` transform does not clip. Without this, the bottom of the
     * banner painted up to ~47dp below the hero's item and showed between the
     * hero and the first card: artwork from the top of the page appearing in the
     * page body, at a different zoom from the backdrop behind it.
     */
    @Test
    fun `the hero clips its over-covered artwork`() {
        val code = source(hero)

        assertTrue(
            "the hero does not clip its artwork, so the scaled and parallaxed " +
                "picture draws outside its own item and between the cards",
            code.contains(".clipToBounds()")
        )
        assertTrue(
            "the artwork is no longer over-covered, so the parallax would expose " +
                "the hero's background along an edge as the page scrolls",
            code.contains("ART_SCALE")
        )
    }

    /**
     * A wide key visual drawn with `Crop` into a portrait preview loses its left
     * and right thirds by definition, which is what "Banner" previewed as. The
     * pannable artwork computes the cover fit itself and sweeps across the
     * overflow, so the whole picture is reachable without a gap.
     */
    @Test
    fun `a banner preview goes through the pannable artwork`() {
        val code = source(preview)

        assertTrue(
            "the banner preview is a plain Crop again, so the sides of the key " +
                "visual are cropped out of the picture the reader is choosing",
            code.contains("PannableBackdrop(")
        )
        assertTrue(
            "the preview does not use the cover fit, so the pannable layer would " +
                "letterbox the banner instead of filling the card",
            code.contains("BackdropFit.COVER")
        )
        assertTrue(
            "the sweep is what reaches the cropped sides of a banner without a gesture",
            code.contains("drift = true")
        )
    }

    /**
     * Taking the amplitude from the animating fill made the whole curve - the
     * unwatched track included - inflate from a flat line to full height on every
     * entrance. The wave is the progress, so it is drawn at the progress.
     */
    @Test
    fun `the wave stands at the progress rather than at the animating fill`() {
        val code = source(wave)

        assertTrue(
            "the wave's amplitude is read from the animating fill again, so the " +
                "entire track bobs up on every entrance",
            code.contains("WaveGeometry.amplitude(maxAmplitude, progress = target)")
        )
    }

    /**
     * The strip was gated on the data alone, so a summary node without a score
     * dropped the item and every card below it sat one strip higher - then the
     * detail landed and the page jumped down by the strip's height.
     */
    @Test
    fun `the stats strip keeps its slot while the page loads`() {
        assertTrue(
            "the stats item is gated on the data again, so it can appear late and " +
                "push the rest of the page down",
            source(page).contains("visible = novaHasStats(media) || isDetailLoading")
        )
        val code = source(cards)
        assertTrue(
            "the stats card has no waiting state, so its reserved slot would draw " +
                "an empty rectangle",
            code.contains("NovaStatsPlaceholder(media = media)")
        )
        assertTrue(
            "the card cannot be told that the detail is still in flight",
            code.contains("isLoading: Boolean = false")
        )
    }

    /**
     * The countdown was gated on the progress card, and the progress card needs the
     * episode *total* to draw its wave - so a currently airing show whose length
     * AniList has not announced had no countdown anywhere on this page, and neither
     * did any show the reader had not added.
     */
    @Test
    fun `the countdown is drawn for any airing anime, not only a tracked one`() {
        val code = source(cards)

        assertTrue(
            "the stats strip is gated on the progress card again, so a show with no " +
                "announced episode count loses its countdown:",
            code.contains("val airing = if (media.isManga) null else rememberAiringLine(media)")
        )
        assertTrue(
            "the strip no longer prints the sentence the model builds",
            code.contains("targetState = airing?.sentence.orEmpty()")
        )
        assertTrue(
            "the page does not tell the details grid to skip its own airing row, so " +
                "the same countdown is printed twice, ticking out of step:",
            source(page).contains("showAiring = false")
        )
    }

    private fun findProjectRoot(): File {
        var candidate = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(candidate, "gradle.properties").isFile) {
            candidate = candidate.parentFile ?: error("could not locate the project root")
        }
        return candidate
    }
}
