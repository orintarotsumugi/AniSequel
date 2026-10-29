package com.anisequel

import com.anisequel.ui.screens.tier.tierAutoScrollDeltaPx
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How far a held drag scrolls the board in one frame.
 *
 * The edge auto-scroll used to add a *fixed* dp per frame, which is exactly twice
 * the speed on a 120Hz screen - so the same drag that was controllable on one phone
 * overshot half a row per tick on another. The distance is now paced against the
 * frame's own duration, and these tests pin the property that matters: a second of
 * dragging covers the same distance at any refresh rate.
 */
class TierAutoScrollPacingTest {

    // One frame at 60Hz, and one at 120Hz, in nanoseconds.
    private val sixtyHz = 16_666_667L
    private val oneTwentyHz = 8_333_333L

    private fun delta(elapsedNanos: Long, strength: Float = 1f, perFramePx: Float = 9f) =
        tierAutoScrollDeltaPx(strength, perFramePx, elapsedNanos)

    @Test
    fun `a 120Hz frame scrolls half what a 60Hz frame does`() {
        assertEquals(delta(sixtyHz), delta(oneTwentyHz) * 2f, 0.01f)
    }

    @Test
    fun `a second of dragging is the same distance at either rate`() {
        val atSixty = (0 until 60).sumOf { delta(sixtyHz).toDouble() }.toFloat()
        val atOneTwenty = (0 until 120).sumOf { delta(oneTwentyHz).toDouble() }.toFloat()

        // Within a pixel over a whole second: the rates differ only by the
        // rounding of their frame lengths.
        assertEquals(atSixty, atOneTwenty, 1f)
    }

    @Test
    fun `the direction is the strength's sign`() {
        assertEquals(-9f, delta(sixtyHz, strength = -1f), 0.01f)
        assertEquals(4.5f, delta(sixtyHz, strength = 0.5f), 0.01f)
    }

    @Test
    fun `no elapsed time moves nothing`() {
        assertEquals(0f, delta(0L), 0f)
    }

    @Test
    fun `a stalled frame is capped instead of teleporting the board`() {
        // A one-second stall must not scroll a second's worth of travel in one
        // step; four frames is a pause, not a jump.
        val capped = delta(1_000_000_000L, perFramePx = 9f)
        assertEquals(36f, capped, 0.01f)
    }
}
