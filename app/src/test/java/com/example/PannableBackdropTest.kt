package com.example

import com.example.ui.components.pannedBackdropTranslation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How far a hero image may be dragged sideways.
 *
 * ## The failure
 *
 * Both hero images - a profile banner and a media backdrop - were drawn with
 * `ContentScale.Crop` into a fixed-height box. Cropping a wide image to a short box
 * throws its left and right edges away permanently, and the composition is authored
 * for the whole frame: whoever put a face at the left third was never going to be
 * seen on a phone. There was no gesture that would ever reveal it.
 *
 * The replacement scales to cover and lets the reader drag to the rest. This pins
 * the arithmetic, because a picture that slides off its own edge and leaves a gap
 * where the background should be is the one thing a "fill the box" image must never
 * do - and that failure looks like a rendering bug rather than a maths one.
 */
class PannableBackdropTest {

    private fun translation(
        containerWidth: Float,
        containerHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
        current: Float
    ) = pannedBackdropTranslation(containerWidth, containerHeight, imageWidth, imageHeight, current)

    @Test
    fun `an image that fits does not move`() {
        // Exactly the box: nothing to reveal.
        assertEquals(0f, translation(400f, 200f, 400f, 200f, 0f))
        // Narrower: scaled up to cover, so the sides run out and there is still
        // nothing to reveal.
        assertEquals(0f, translation(400f, 200f, 300f, 200f, 0f))
        assertEquals(0f, translation(400f, 200f, 1600f, 200f, 0f))
    }

    @Test
    fun `a wide image pans by exactly the excess`() {
        // Cover the height: 200/400 = 0.5, so a 1600-wide image is 800 wide in a
        // 400 box, and 400px of it is off-screen at any one time.
        assertEquals(-400f, translation(400f, 200f, 1600f, 400f, -400f))
        assertEquals(-250f, translation(400f, 200f, 1600f, 400f, -250f))
    }

    @Test
    fun `a drag past either edge is clamped rather than obeyed`() {
        // Past the left edge would leave a gap on the right, which is the failure.
        assertEquals(-400f, translation(400f, 200f, 1600f, 400f, -900f))
        // Past the right edge would leave a gap on the left.
        assertEquals(0f, translation(400f, 200f, 1600f, 400f, 500f))
    }

    @Test
    fun `an unmeasured image does not move`() {
        // Before `onSuccess` has fired there is no intrinsic size, and a pan driven
        // off a guess would jump the moment the real size arrived.
        assertEquals(0f, translation(400f, 200f, 0f, 0f, -100f))
        // Nor before the container has been measured.
        assertEquals(0f, translation(0f, 0f, 1600f, 400f, -100f))
    }

    @Test
    fun `a tall image scaled to cover does not pan`() {
        // Covering a short wide box with a tall image makes it narrower than the
        // box, so there is nothing sideways to reveal.
        assertEquals(0f, translation(400f, 200f, 100f, 800f, -50f))
    }

    @Test
    fun `an image one pixel wider than the box pans by one pixel`() {
        // 802 * (200/400) = 401, so 1px is off-screen. Not 0: the excess is real,
        // and rounding it away here would leave a hairline gap at the edge.
        assertEquals(-1f, translation(400f, 200f, 802f, 400f, -1f))
    }
}