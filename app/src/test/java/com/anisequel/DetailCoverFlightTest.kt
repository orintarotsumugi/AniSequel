package com.anisequel

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.detail.DetailCoverFlightState
import com.anisequel.ui.components.detail.DetailCoverTarget
import com.anisequel.ui.components.detail.toScreenRect
import com.anisequel.ui.components.detail.toWindowRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The poster that flies between a list card and the title page.
 *
 * The flight itself is two rectangles in two windows joined by a draw-phase
 * transform, and a JVM test cannot see it. What can be pinned is the shape of the
 * join - the press listener, the rectangles, the reveal, and the state machine
 * that decides which side draws - and the ways it used to break: a press listener
 * that swallowed the card's click, a copy that started from a rectangle measured
 * elsewhere, a page poster still drawn under it, and a landing that took away the
 * copy the close needed.
 */
class DetailCoverFlightTest {

    private val root = findProjectRoot()

    private val flight =
        "app/src/main/java/com/anisequel/ui/components/detail/DetailCoverFlight.kt"
    private val sheet =
        "app/src/main/java/com/anisequel/ui/components/detail/SequelDetailSheet.kt"
    private val pageFile =
        "app/src/main/java/com/anisequel/ui/components/detail/SequelDetailPage.kt"

    /** The sheet owns the state; the page file owns the layout it hands to it. */
    private fun pageSource(): String = source(sheet) + source(pageFile)
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
        val page = pageSource()

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
     * The page may not move until the copy can actually draw its picture.
     *
     * Two failures shared one cause - the copy was only composed once the flight
     * had already started. A poster that took a few frames to decode therefore
     * appeared halfway through the open, and the page had already hidden its own
     * poster for it, so the hero was a flat tint until the image landed. The copy
     * is composed invisibly first, its picture is given a bounded moment to
     * arrive, and only then do the page and the copy leave together. A request
     * that never lands calls the flight off and the page opens on its own poster.
     */
    @Test
    fun `the open waits for the copy's picture before the page moves`() {
        val text = source(flight)
        val page = pageSource()

        assertTrue(
            "the copy is only composed once it is already meant to be visible, so " +
                "its picture has no frame to decode before the flight starts",
            text.contains("private val showable") && text.contains("armed.value")
        )
        assertTrue(
            "the page's own poster is hidden from the first frame, before there is a " +
                "copy to cover it - a slow decode leaves a hole where the artwork is",
            text.contains("if (onStage.value && painting.value && !abandoned.value) 0f else 1f")
        )
        assertTrue(
            "the flight never waits for the copy's picture",
            text.contains("while (!state.copyIsReady") &&
                text.contains("COPY_WAIT_FRAMES")
        )
        assertTrue(
            "a copy that never gets a picture still starts the flight and slides a " +
                "tinted rectangle across the page",
            text.contains("if (!state.copyIsReady) {") &&
                text.contains("state.abandon()")
        )
        assertTrue(
            "the page's entrance is not held for the flight, so it fades in behind " +
                "a rectangle that is still decoding",
            page.contains("rememberSheetEntrance(released = coverFlight.entranceGate)")
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

    /**
     * A close after a landing must still have the picture to fly home.
     *
     * v2.6.8 took the copy out of the composition at the landing to stop a second
     * poster living for the whole session, but the close re-staged a copy that no
     * longer existed: the page hid its own poster, the sheet dismissed, and the
     * poster never travelled back to the card. The copy now stays composed at alpha
     * zero and shares the card's decoded bitmap (it asks for the card's rectangle),
     * so the landing does not remove it and [DetailCoverFlightState.begin] can put
     * it back on the close's first frame.
     */
    @Test
    fun `the close puts the same copy back after the landing`() {
        val target = DetailCoverTarget().apply {
            record(
                rect = Rect(16f, 64f, 132f, 238f),
                shape = RoundedCornerShape(12.dp),
                tint = Color.DarkGray,
                elevation = 18.dp,
                borderColor = Color.White,
                borderWidth = 1.dp
            )
        }
        val state = DetailCoverFlightState(
            // Screen pixels; the page converts it into its own window in `anchor`.
            screenOrigin = Rect(40f, 400f, 140f, 550f),
            target = target,
            progress = Animatable(1f)
        )

        state.arm()
        state.onCopyReady()
        assertTrue(
            "the copy is not the one drawing after it reports its picture",
            state.reveal() == 0f
        )

        state.land()
        assertTrue(
            "the landing leaves the page's poster hidden, so the page shows a hole",
            state.reveal() == 1f
        )

        state.begin()
        assertTrue(
            "the close has no copy to stage, so the poster is hidden for a flight " +
                "that cannot happen and the sheet dismisses around a missing picture",
            state.reveal() == 0f
        )
    }

    /**
     * The poster's frame is part of the poster.
     *
     * The copy drew the artwork, the tint and the shadow but not the one dp white
     * hairline the page draws around it, so the frame appeared a frame after the
     * landing - the only edge the flight did not make continuous.
     */
    @Test
    fun `the copy carries the poster's frame instead of landing without one`() {
        val text = source(flight)

        assertTrue(
            "the copy lands without the page's hairline, so the frame pops in after " +
                "the flight has already handed over",
            text.contains(".border(target.borderWidth, target.borderColor, target.shape)")
        )
        assertTrue(
            "the target does not carry the frame, so the copy has no number to draw",
            text.contains("var borderColor: Color") && text.contains("var borderWidth: Dp")
        )
        assertTrue(
            "a hero draws a frame literal the copy never sees",
            source(novaHero).contains("HERO_POSTER_BORDER_WIDTH, posterBorder") &&
                source(animeHero).contains("HERO_POSTER_BORDER_WIDTH, posterBorder")
        )
    }

    /**
     * The copy has to ask for the picture the card already has.
     *
     * A plain `rememberAsyncImagePainter` has no layout size resolver, so the copy
     * decoded the poster at its original size: a different Coil cache key from the
     * card on screen a frame earlier, and a decode that often outlived the six-frame
     * wait. The open then abandoned the flight and cut to the page. Asking for the
     * card's rectangle is a memory hit; growing that picture is the flight.
     */
    @Test
    fun `the copy asks for the picture the card already decoded`() {
        val text = source(flight)

        assertTrue(
            "the copy requests the poster at its original size, so it misses the " +
                "card's cache entry and gives up before the second decode lands",
            text.contains("val decodeWidth = origin.width.roundToInt().coerceAtLeast(1)") &&
                text.contains(".size(decodeWidth, decodeHeight)")
        )
    }

    /**
     * A poster that has not decoded is a loading state, not a flat rectangle.
     *
     * The cards have drawn [com.anisequel.ui.components.cards.PosterPlaceholder]
     * behind their artwork since the grid's first cold start; the two heroes drew a
     * plain cover colour until the picture arrived, which reads as a missing image
     * and leaves the same blank on a request that never resolves.
     */
    @Test
    fun `a poster that is still loading shows the title`() {
        assertTrue(
            "the page's poster has no loading frame, so a cold open is a flat tint",
            source(novaHero).contains("PosterPlaceholder(") &&
                source(animeHero).contains("PosterPlaceholder(")
        )
    }

    /**
     * The two windows do not share a frame of reference, and the flight does.
     *
     * The person sheet is anchored to the bottom edge of the display, so its window
     * origin is most of a screen below the activity's. A poster 200px down that
     * window is 1600px down the display, and the title page - spending the number as
     * if it were already screen space - started the copy's flight near the top of the
     * display instead of from the poster under the reader's finger. This is the
     * "sometimes", too: how wrong it was depended on how tall the sheet was.
     *
     * The conversion is a pure function for that reason - a JVM test cannot open two
     * windows, but it can pin what each one does with a rectangle at its edge.
     */
    @Test
    fun `a rectangle from a sheet's window is converted, not spent as screen space`() {
        val inSheetWindow = Rect(24f, 200f, 136f, 358f)
        // A phone's height of sheet, bottom anchored: the window starts at y = 1400.
        val sheetWindowOnScreen = IntOffset(0, 1400)

        val onScreen = toScreenRect(inSheetWindow, sheetWindowOnScreen)

        assertEquals("the x is already screen space in a full-width sheet", 24f, onScreen.left, 0.001f)
        assertEquals("the y did not move with its window", 1600f, onScreen.top, 0.001f)
        assertEquals("the size of the rectangle is not a position", inSheetWindow.width, onScreen.width, 0.001f)

        // The page's dialog sits at the top of the display, so it subtracts nothing and
        // the copy starts at the screen rectangle itself.
        // Edge by edge rather than rect against rect: `Rect` is a value class over a
        // `FloatArray`, and two rects built from equal numbers are not equal objects.
        val inTopWindow = toWindowRect(onScreen, IntOffset(0, 0))
        assertEquals(24f, inTopWindow.left, 0.001f)
        assertEquals(1600f, inTopWindow.top, 0.001f)
        assertEquals(136f, inTopWindow.right, 0.001f)
        assertEquals(1758f, inTopWindow.bottom, 0.001f)
        // A page in a window of its own converts into that window's place instead.
        val inPageWindow = toWindowRect(onScreen, IntOffset(0, 1480))
        assertEquals(24f, inPageWindow.left, 0.001f)
        assertEquals(120f, inPageWindow.top, 0.001f)
        assertEquals(136f, inPageWindow.right, 0.001f)
        assertEquals(278f, inPageWindow.bottom, 0.001f)
    }

    /**
     * Both edges of the flight convert, and neither one keeps its own window's space.
     *
     * The recording half is the one that regressed: the rectangle was stored exactly
     * as `boundsInWindow()` returned it, which is right only when the card is in the
     * same window as the page - true for the dashboard, wrong for every poster inside
     * the character sheet and for the studio page's floating sheet rows.
     */
    @Test
    fun `the press is recorded in screen space and anchored into the page's window`() {
        val text = source(flight)

        assertTrue(
            "the press's rectangle is stored in the recording window's space, so a " +
                "card inside a sheet flies in from the wrong place",
            text.contains("toScreenRect(rect, view.screenOffset())")
        )
        assertTrue(
            "the page spends the screen rectangle without moving it into its own " +
                "window",
            text.contains("state.anchor(view.screenOffset())")
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
