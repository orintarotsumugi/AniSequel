package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The poster that flies between a list card and the title page.
 *
 * None of this is observable in a JVM test: the flight is two rectangles in two
 * windows, joined by a draw-phase transform. What can be pinned is the shape of
 * the join, and the three ways it used to break: a press listener that swallowed
 * the card's click, a copy that started from a rectangle measured elsewhere, and
 * a page poster still drawn under it, which turned the landing into a seam.
 */
class DetailCoverFlightTest {

    private val root = findProjectRoot()

    private val flight =
        "app/src/main/java/com/anisequel/ui/components/detail/DetailCoverFlight.kt"
    private val sheet =
        "app/src/main/java/com/anisequel/ui/components/detail/SequelDetailSheet.kt"
    private val novaHero =
        "app/src/main/java/com/anisequel/ui/components/detail/info/NovaInfoHero.kt"
    private val animeHero =
        "app/src/main/java/com/anisequel/ui/components/detail/info/InfoHero.kt"

    @Test
    fun `the list records on the press without consuming it`() {
        val source = source(flight)

        assertTrue(
            "the rectangle is recorded on a pass that runs after the card, so the " +
                "press is gone by the time the click needs it",
            source.contains("awaitPointerEvent(PointerEventPass.Initial)")
        )
        assertFalse(
            "the listener consumes the event, so the card never opens",
            source.contains("consume()")
        )
        assertTrue(
            "the rectangle is measured against the screen rather than a parent, " +
                "so the dialog's window can compare it with its own",
            source.contains("boundsInWindow()")
        )
    }

    @Test
    fun `the copy is drawn at the page's rectangle and scaled in the draw phase`() {
        val source = source(flight)

        assertTrue(
            "the transform is animated through layout, so the page relayouts every " +
                "frame of the flight",
            source.contains("transformOrigin = TransformOrigin(0f, 0f)") &&
                source.contains("scaleX = scale")
        )
        assertTrue(
            "one scale per axis stretches a square card's thumbnail into the " +
                "portrait poster for the whole flight",
            source.contains("val startScale = min(origin.width / targetWidth, ") &&
                source.contains("scaleY = scale")
        )
        assertTrue(
            "the copy is anchored to the card's corner, so a wider card leaves it " +
                "hanging outside the rectangle the reader pressed",
            source.contains("translationX = lerp(origin.center.x - ")
        )
        assertTrue(
            "the corner radius is applied before the scale, so the corners stay " +
                "the page's size while the copy is still small",
            source.contains(".clip(target.shape)")
        )
        assertTrue(
            "the copy carries the page's own poster shadow at the landing, so the " +
                "hand-off drops a halo off a poster that had one a frame earlier",
            source.contains("target.elevation.toPx() * p + 6.dp.toPx() * sin(p * PI).toFloat()")
        )
    }

    @Test
    fun `a url that does not match still finds the press that opened the page`() {
        val source = source(flight)

        assertTrue(
            "only exact URLs are matched, so every btttr cover opens with a fade " +
                "instead of a flight",
            source.contains("RECENT_MILLIS") && source.contains("maxByOrNull { it.value.atMillis }")
        )
        assertTrue(
            "the fallback rectangle is left behind, so a second title re-flies from it",
            source.contains("origins.remove(recent.key)")
        )
    }

    @Test
    fun `the page hides its own poster only while the copy is on stage`() {
        val page = source(sheet)

        assertTrue(
            "the page's poster is drawn under the copy, so the landing shows two " +
                "copies of the same picture and a visible seam",
            source(novaHero).contains("LocalDetailCoverReveal") &&
                source(animeHero).contains("LocalDetailCoverReveal")
        )
        assertTrue(
            "the page's own poster is not faded in the draw phase",
            source(novaHero).contains("alpha = coverReveal()") &&
                source(animeHero).contains("alpha = coverReveal()")
        )
        assertTrue(
            "the copy outlives the page the reader is closing, or it cannot be " +
                "put back where it came from",
            page.contains("coverFlight.Overlay(posterUrl.orEmpty())")
        )
        // The copy ends the flight at the page's own shadow, so both heroes have
        // to hand it the number their own poster is drawn with - spelled once.
        assertTrue(
            "a hero draws its poster's shadow with a literal the copy never sees, " +
                "so the landing swaps a shadowed poster for a flat one",
            source(novaHero).contains("elevation = HERO_POSTER_ELEVATION") &&
                source(animeHero).contains("elevation = HERO_POSTER_ELEVATION")
        )
    }

    /**
     * A flight that never measured a landing must leave the page's poster alone.
     *
     * The hero reports its rectangle once, and the copy waits a bounded number of
     * frames for it. If it never arrives there is no copy to land, so the page has
     * to keep drawing its own poster: hiding it for a copy that never comes puts a
     * hole where the picture should be for as long as the page is open, which is a
     * worse failure than not animating at all.
     */
    @Test
    fun `a flight that cannot start gives the page its poster back`() {
        val text = source(flight)

        assertTrue(
            "the reveal keeps the page's poster hidden even when no copy will ever " +
                "land, so a page that never measured one shows a hole",
            text.contains("abandoned.value")
        )
        assertTrue(
            "the four-frame wait gives up without telling the page, so the poster " +
                "it was hiding is never drawn again",
            text.contains("state.abandon()")
        )
    }

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//[^\\n]*"), "")

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