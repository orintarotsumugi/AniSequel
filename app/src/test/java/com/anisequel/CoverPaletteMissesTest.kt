package com.anisequel

import com.anisequel.domain.color.CoverPaletteMisses
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The negative half of the cover palette cache.
 *
 * The palette cache only remembered successes, so a grey cover - the one case
 * where the work is guaranteed to produce nothing - was decoded and quantized
 * again on every open, and the detail page asks under two URLs. These tests pin
 * the two properties that make remembering a miss safe: it is bounded, and it is
 * least-recently-used, so a title opened once does not keep a cover that was
 * opened since from ever being read.
 */
class CoverPaletteMissesTest {

    @Test
    fun `a remembered cover stays remembered`() {
        val misses = CoverPaletteMisses(capacity = 4)
        assertFalse(misses.contains("https://img/1.jpg"))
        misses.remember("https://img/1.jpg")
        assertTrue(misses.contains("https://img/1.jpg"))
    }

    @Test
    fun `the cache is bounded and drops the oldest answer`() {
        val misses = CoverPaletteMisses(capacity = 2)
        misses.remember("a")
        misses.remember("b")
        misses.remember("c")

        assertFalse("a is the oldest and must have been evicted", misses.contains("a"))
        assertTrue(misses.contains("b"))
        assertTrue(misses.contains("c"))
    }

    @Test
    fun `reading a miss keeps it from being evicted`() {
        val misses = CoverPaletteMisses(capacity = 2)
        misses.remember("a")
        misses.remember("b")
        assertTrue(misses.contains("a"))
        misses.remember("c")

        assertTrue("a was read most recently, so b is the one that goes", misses.contains("a"))
        assertFalse(misses.contains("b"))
    }
}
