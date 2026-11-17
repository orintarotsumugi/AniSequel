package com.anisequel

import com.anisequel.data.network.betterPosterUrl
import com.anisequel.data.network.btttrVariantOverlays
import com.anisequel.domain.ui.PosterOverlays
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every render btttr has, and the viewer that shows them.
 *
 * ## Why the four renders are pinned
 *
 * The viewer used to keep one URL per title - the reader's configured overlay -
 * so three of btttr's four renders were unreachable from inside the app that
 * already knew their addresses. The gallery is the fix, and it is easy to undo by
 * accident: `posterVariants` is a second question to the same service as
 * `posterUrl`, so a later edit can reroute the viewer through the card's single
 * URL and every test still passes while three quarters of the art disappears
 * again.
 *
 * The ordering is a requirement rather than a preference because it is what the
 * reader sees first: the render their cards use has to be page one, not whatever
 * the service happened to answer with.
 *
 * ## Why the geometry is pinned as source
 *
 * The dialog's window flags, the label-based source selection and the artwork's
 * own tap handler are not observable in a JVM test - they are a window and a
 * gesture. Each one fails the same quiet way when it is removed (the viewer is a
 * letterboxed card again, or a late-arriving btttr gallery silently swaps the
 * picture under the reader's finger), so the lines that connect them are
 * asserted here in the spirit of `InfoBackdropWiringTest`.
 */
class CoverViewerGalleryTest {

    private fun source(path: String): String =
        Regex("""/[*][\s\S]*?[*]/""")
            .replace(File(findProjectRoot(), path).readText()) { "" }
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    private val viewer =
        "app/src/main/java/com/anisequel/ui/components/detail/FullScreenCoverViewer.kt"
    private val artwork =
        "app/src/main/java/com/anisequel/ui/components/detail/CoverViewerArtwork.kt"
    private val posterArt =
        "app/src/main/java/com/anisequel/ui/components/cards/PosterArt.kt"
    private val betterPosters =
        "app/src/main/java/com/anisequel/data/network/BetterPostersApi.kt"

    @Test
    fun `the reader's render is the first page of the gallery`() {
        val chosen = PosterOverlays(showRating = true, showGenre = false)
        val gallery = btttrVariantOverlays(chosen)

        assertEquals(
            "the gallery does not open on the render the reader's cards draw, so " +
                "the one picture they asked for is not the one they land on",
            chosen,
            gallery.first()
        )
    }

    @Test
    fun `all four overlay combinations are offered exactly once`() {
        val gallery = btttrVariantOverlays(PosterOverlays(showRating = true, showGenre = true))

        assertEquals(
            "btttr renders four combinations of score and genre; anything else is " +
                "either art the service does not have or the same page twice",
            4,
            gallery.size
        )
        assertEquals(
            "two pages ask btttr for the same render, so a swipe can land on a " +
                "duplicate and read as a broken pager",
            4,
            gallery.map { betterPosterUrl("tt1480925", it) }.distinct().size
        )
        assertEquals(
            "the four combinations are no longer score-on/off x genre-on/off",
            listOf(true to true, true to false, false to true, false to false).toSet(),
            gallery.map { it.showRating to it.showGenre }.toSet()
        )
    }

    @Test
    fun `the trend strip is the reader's setting on every page`() {
        val gallery = btttrVariantOverlays(PosterOverlays(showTrendTags = true))

        assertTrue(
            "a gallery page dropped the trend strip: the overlay flags differ by " +
                "score and genre only, and the trend strip is a query parameter on " +
                "the same render, set once in Settings",
            gallery.all { it.showTrendTags }
        )
        assertFalse(
            "a gallery page invented a trend strip the reader turned off",
            btttrVariantOverlays(PosterOverlays(showTrendTags = false))
                .any { it.showTrendTags }
        )
    }

    @Test
    fun `the viewer asks btttr for every render rather than the card's one URL`() {
        assertTrue(
            "there is no multi-render lookup, so the viewer can only ever show the " +
                "overlay the reader's cards use",
            source(betterPosters).contains("suspend fun posterVariants(")
        )
        assertTrue(
            "the reusable gallery composable is gone, so only this dialog can show " +
                "btttr's other renders",
            source(posterArt).contains("fun betterPosterGallery(")
        )
        assertTrue(
            "the viewer never collects the gallery",
            source(viewer).contains("betterPosterGallery(media)")
        )
        assertFalse(
            "the viewer is back on the card's single URL, which resolves one " +
                "overlay and drops the other three",
            source(viewer).contains("cardPosterUrl(")
        )
    }

    @Test
    fun `the viewer window owns the whole display`() {
        val file = source(viewer)

        assertTrue(
            "a platform-width dialog is a card in the middle of the screen, not a " +
                "full-screen viewer",
            file.contains("usePlatformDefaultWidth = false")
        )
        assertTrue(
            "the decor still fits the system bars, so the \"full screen\" artwork " +
                "stops at the bar edges",
            file.contains("decorFitsSystemWindows = false")
        )
        assertTrue(
            "the artwork no longer fills the window, so it cannot run under the " +
                "bars and the sheet",
            file.contains("CoverViewerPager(") && file.contains("Modifier.fillMaxSize()")
        )
    }

    @Test
    fun `the source is chosen by label so a late arrival cannot swap the picture`() {
        val file = source(viewer)

        assertTrue(
            "the selected source is no longer resolved by label. btttr's gallery and " +
                "LiveChart's arrive after the dialog opens and are inserted into the " +
                "list, so an index points at a different picture once one lands",
            file.contains("indexOfFirst { it.label == selectedLabel }")
        )
        assertTrue(
            "the pills have to select by label too, or the highlighted pill and the " +
                "artwork can disagree",
            file.contains("sources.getOrNull(index)?.label?.let")
        )
    }

    @Test
    fun `the artwork itself closes the viewer, not just the space around it`() {
        assertTrue(
            "the pager is not handed a tap handler, so tapping the picture - which " +
                "now covers the screen - does nothing",
            source(viewer).contains("onTap = { if (zoom.isZoomed) zoom.reset() else requestDismiss() }")
        )
        assertTrue(
            "the artwork does not detect the tap, and the dialog's background is " +
                "underneath a full-bleed picture",
            source(artwork).contains("onTap = { onTap() }")
        )
        assertTrue(
            "a single tap and a double tap are no longer distinguished, so opening " +
                "the zoom would also close the viewer",
            source(artwork).contains("onDoubleTap")
        )
    }

    @Test
    fun `a saved render is named after the overlay it was rendered with`() {
        assertTrue(
            "four renders saved under one name are four unlabelled files in the " +
                "gallery, indistinguishable from duplicates",
            source(viewer).contains("image.variant.lowercaseFirst()")
        )
        assertTrue(
            "nothing names a btttr render in words, so the sheet cannot say which " +
                "of the four is on screen",
            source("app/src/main/java/com/anisequel/ui/components/detail/CoverViewerDetails.kt")
                .contains("fun PosterOverlays.variantName()")
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
