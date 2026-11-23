package com.anisequel

import com.anisequel.ui.components.shimmerBandLeft
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shimmer's sweep, pinned as geometry because it cannot be seen from a unit
 * test.
 *
 * What it fixes: the band used to be a **square** of `0.7 x max(width, height)`
 * travelling diagonally. That is only correct on a box that is roughly square.
 * Its vertical extent was the same `0.7 x max(...)`, so on a wide skeleton - a
 * comment's 12dp text line, the full-width bar inside a card, a load-more strip -
 * the highlight left the box through the top and bottom for a part of every
 * loop. Measured on a phone-width 340x40 bar it was visible for about half the
 * cycle, which reads as a highlight that stops in the middle of the skeleton and
 * starts again: not a slow animation, a broken one.
 *
 * A stripe as tall as the box cannot do that at any aspect ratio, so the current
 * geometry has no height input at all - that absence is the fix - and the sweep
 * starts and ends exactly at the two edges instead of somewhere beyond them.
 */
class ShimmerGeometryTest {

    private val source = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/components/ShimmerLoading.kt"
    ).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    /** Phone-width skeleton blocks, and the tall/narrow case, in pixels. */
    private val widths = listOf(24f, 88f, 340f, 1080f)

    @Test
    fun `the band enters and leaves exactly at the edges`() {
        widths.forEach { width ->
            val band = width * 0.6f

            assertEquals(
                "at phase 0 the band's right edge must sit on the box's left edge, " +
                    "so the highlight is already crossing when the loop starts",
                0f,
                shimmerBandLeft(0f, width, band) + band,
                0.001f
            )
            assertEquals(
                "at phase 1 the band's left edge must sit on the box's right edge, " +
                    "so the highlight does not spend the tail of the loop off-screen",
                width,
                shimmerBandLeft(1f, width, band),
                0.001f
            )
        }
    }

    @Test
    fun `the highlight crosses the whole box in one loop`() {
        widths.forEach { width ->
            val band = width * 0.6f
            val travel = shimmerBandLeft(1f, width, band) - shimmerBandLeft(0f, width, band)

            assertEquals(
                "the sweep must travel a box plus its own width, which is what makes " +
                    "every skeleton take the same time to cross",
                width + band,
                travel,
                0.001f
            )
        }
    }

    @Test
    fun `the sweep is monotonic and never jumps`() {
        widths.forEach { width ->
            val band = width * 0.6f
            var previous = shimmerBandLeft(0f, width, band)
            (1..100).forEach { step ->
                val next = shimmerBandLeft(step / 100f, width, band)
                assertTrue("the band must not move backwards mid-loop", next >= previous)
                previous = next
            }
        }
    }

    @Test
    fun `a phase outside the loop is clamped rather than thrown off the box`() {
        val band = 60f
        assertEquals(shimmerBandLeft(0f, 100f, band), shimmerBandLeft(-0.5f, 100f, band), 0.001f)
        assertEquals(shimmerBandLeft(1f, 100f, band), shimmerBandLeft(1.5f, 100f, band), 0.001f)
    }

    /**
     * The structural half: the band is the box's full height.
     *
     * This is the assertion the old square band fails - it drew a `Size(bleed * 2,
     * bleed * 2)` rectangle whose height had nothing to do with the box - and it is
     * also the reason no height parameter appears in [shimmerBandLeft].
     */
    @Test
    fun `the band is as tall as the box it sweeps`() {
        assertTrue(
            "the band must be drawn at the box's own height",
            source.contains("Size(bandWidth, size.height)")
        )
        assertTrue(
            "a horizontal gradient is what makes the band a vertical stripe",
            source.contains("Brush.horizontalGradient")
        )
        assertFalse(
            "the square band's bleed is the geometry that lost the highlight on " +
                "wide skeletons; it must not come back",
            source.contains("SHIMMER_BLEED_FRACTION")
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
