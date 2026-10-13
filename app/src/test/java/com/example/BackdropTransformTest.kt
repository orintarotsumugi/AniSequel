package com.example

import com.example.ui.components.backdropCoverScale
import com.example.ui.components.backdropTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a hero image sits, and the two ways that can go wrong.
 *
 * ## The failure that started this
 *
 * The backdrop handed `ContentScale.FillWidth` to Coil, which scales to the
 * *width*. A 1920x1080 banner in a 360x280dp hero therefore drew 360x202 - and the
 * missing 78dp is not padding, it is the hero showing through as bare background
 * under a dark scrim. Close to a third of the top of every media sheet was an empty
 * rectangle, which reads as "this screen has no image on it".
 *
 * `ContentScale.Crop` covers, but it is one static fit: the left and right thirds of
 * the artwork were gone for good, and nothing could bring them back.
 *
 * So the fit is computed here, and the picture is then free to move within whatever
 * slack that left.
 */
class BackdropTransformTest {

    // A phone-width hero holding a standard AniList banner.
    private val boxWidth = 360f
    private val boxHeight = 280f
    private val bannerWidth = 1920f
    private val bannerHeight = 1080f

    private fun cover() = backdropCoverScale(boxWidth, boxHeight, bannerWidth, bannerHeight)

    private fun at(zoom: Float, x: Float = 0f, y: Float = 0f) =
        backdropTransform(boxWidth, boxHeight, bannerWidth, bannerHeight, zoom, x, y)

    @Test
    fun `the cover scale is whichever axis runs out first`() {
        // Wide image, short box: the height is the binding constraint.
        assertEquals(boxHeight / bannerHeight, cover(), 0.0001f)
        // Tall image, wide box: the width is.
        // Note: the image dims are swapped in the call, so the function receives
        // imageWidth=bannerHeight (1080), imageHeight=bannerWidth (1920).
        // cover = max(360/1080, 280/1920) = max(0.333, 0.1458) = 0.333...
        assertEquals(
            boxWidth / bannerHeight, // 360/1080 = 0.333...
            backdropCoverScale(boxWidth, boxHeight, bannerHeight, bannerWidth),
            0.0001f
        )
    }

    @Test
    fun `at full zoom-out the picture covers the box on both axes`() {
        // The bug, pinned: FillWidth left the hero 78dp short of its own height.
        val t = at(zoom = 1f)
        assertEquals(boxHeight / bannerHeight, t.scale, 0.0001f)
        assertTrue(
            "the drawn height must reach the box height, was ${t.scale * bannerHeight}",
            t.scale * bannerHeight >= boxHeight - 0.01f
        )
        assertTrue(
            "the drawn width must reach the box width",
            t.scale * bannerWidth >= boxWidth - 0.01f
        )
    }

    @Test
    fun `an exactly fitting image cannot be dragged`() {
        val t = backdropTransform(360f, 280f, 360f, 280f, 1f, 9999f, 9999f)
        assertEquals(0f, t.offsetX, 0.0001f)
        assertEquals(0f, t.offsetY, 0.0001f)
    }

    @Test
    fun `the pan range is half the overflow on each side`() {
        // Symmetric, because the transform origin is the centre. A non-symmetric
        // bound would let the picture slide off on one side and stick out on the
        // other.
        val overflow = bannerWidth * cover() - boxWidth
        val limit = overflow / 2f

        assertEquals(limit, at(1f, x = 9999f).offsetX, 0.0001f)
        assertEquals(-limit, at(1f, x = -9999f).offsetX, 0.0001f)
        assertEquals(20f, at(1f, x = 20f).offsetX, 0.0001f)
    }

    @Test
    fun `a wide banner over-covers the height, so there is nothing to drag vertically`() {
        // 1920x1080 scaled to cover a 280dp-tall box comes out 498 wide and exactly
        // 280 tall - the height is the binding axis, so vertically it is a perfect
        // fit and panning up or down would only show background.
        assertEquals(0f, at(1f, y = 500f).offsetY, 0.0001f)
    }

    @Test
    fun `zooming in opens both axes`() {
        val doubled = at(zoom = 2f)
        assertEquals(2f * cover(), doubled.scale, 0.0001f)

        // Now the height overflows too, so it can be dragged.
        val verticalLimit = (bannerHeight * doubled.scale - boxHeight) / 2f
        assertTrue("zooming in must allow a vertical drag", verticalLimit > 0f)
        assertEquals(verticalLimit, at(zoom = 2f, y = 9999f).offsetY, 0.0001f)
    }

    @Test
    fun `zooming back out re-centres the picture`() {
        // The clamp is computed from the *current* zoom, so dropping to fit recentres
        // rather than stranding the picture wherever it was left.
        assertEquals(0f, at(zoom = 1f, x = 60f, y = 300f).offsetY, 0.0001f)
    }

    @Test
    fun `zoom is bounded at both ends`() {
        assertEquals(4f * cover(), at(zoom = 99f).scale, 0.0001f)
        // Below 1 there would be a gap, which is the whole thing this guards.
        assertEquals(cover(), at(zoom = 0.1f).scale, 0.0001f)
    }

    @Test
    fun `nothing measured is an identity, not a scaled guess`() {
        val noBox = backdropTransform(0f, 0f, bannerWidth, bannerHeight, 2f, 50f, 50f)
        assertEquals(1f, noBox.scale, 0.0001f)
        assertEquals(0f, noBox.offsetX, 0.0001f)

        val noImage = backdropTransform(boxWidth, boxHeight, 0f, 0f, 2f, 50f, 50f)
        assertEquals(1f, noImage.scale, 0.0001f)
        assertEquals(0f, noImage.offsetX, 0.0001f)
        assertEquals(0f, noImage.offsetY, 0.0001f)
    }
}