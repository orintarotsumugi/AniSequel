package com.anisequel

import com.anisequel.ui.components.novamarkdown.NOVA_SVG_RASTER_MAX_PIXELS
import com.anisequel.ui.components.novamarkdown.NovaByteCache
import com.anisequel.ui.components.novamarkdown.novaSvgAspect
import com.anisequel.ui.components.novamarkdown.sanitizeNovaSvg
import com.anisequel.ui.components.novamarkdown.novaSvgRasterKey
import com.anisequel.ui.components.novamarkdown.novaSvgRasterSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two decisions behind a bio that scrolls: what is kept, and how big a kept
 * card is.
 *
 * ## What this pins
 *
 * Scrolling a profile used to lag because every dynamic SVG card kept a Chromium
 * `WebView` alive - a renderer with its own surface that repaints on each animated
 * frame, and that a `LazyColumn` destroys and rebuilds whenever the bio leaves the
 * viewport. The card is now drawn once and kept as a bitmap, so both caches became
 * *byte* budgets:
 *
 *  1. **A count is not a size.** Twelve sanitised documents could be 24 MiB, and a
 *     `String` is UTF-16, so 48 MiB of heap held for a bio nobody was looking at.
 *     The eviction order and the budget are asserted here because a cache that
 *     silently grows past its ceiling is the bug that comes back as jank, not as a
 *     wrong picture - nothing on screen says which one it is.
 *  2. **A raster is only valid for the width it was captured at.** The bitmap is
 *     drawn back at the exact slot size, so the key has to carry that size: keyed
 *     on the URL alone, a rotation would stretch the old capture instead of
 *     re-rendering at the new width.
 *
 * The capture itself needs a WebView and a frame, which is why it is not here; the
 * sizing rule it obeys is a pure function, so it is.
 */
class NovaSvgCacheTest {

    // region the byte budget

    @Test
    fun `the oldest entry is evicted when the budget is exceeded`() {
        val cache = NovaByteCache<Int>(maxBytes = 100) { it }
        cache.put("a", 40)
        cache.put("b", 40)
        cache.put("c", 40)

        assertNull("the first entry should be gone", cache.get("a"))
        assertNotNull(cache.get("b"))
        assertNotNull(cache.get("c"))
        assertEquals("the budget is what is left", 80, cache.usedBytes)
    }

    @Test
    fun `reading an entry protects it from eviction`() {
        val cache = NovaByteCache<Int>(maxBytes = 100) { it }
        cache.put("a", 40)
        cache.put("b", 40)
        cache.get("a")
        cache.put("c", 40)

        assertNotNull("a was read, so it is the most recent", cache.get("a"))
        assertNull("b became the eldest", cache.get("b"))
    }

    @Test
    fun `the newest entry is kept even when it alone is over budget`() {
        val cache = NovaByteCache<Int>(maxBytes = 100) { it }
        cache.put("huge", 5_000)

        assertEquals("throwing away what was just paid for would re-render forever", 5_000, cache.usedBytes)
        assertNotNull(cache.get("huge"))

        cache.put("small", 10)

        assertNull("the next write is what evicts it", cache.get("huge"))
        assertEquals(10, cache.usedBytes)
    }

    @Test
    fun `replacing a key does not count its bytes twice`() {
        val cache = NovaByteCache<Int>(maxBytes = 100) { it }
        cache.put("a", 60)
        cache.put("a", 30)

        assertEquals(30, cache.usedBytes)
        assertEquals(1, cache.size)
    }

    // endregion

    // region the raster size

    @Test
    fun `a slot inside the budget is captured at its own size`() {
        assertEquals(1080 to 540, novaSvgRasterSize(1080, 540))
    }

    @Test
    fun `an oversized slot is scaled down and keeps its shape`() {
        val (width, height) = novaSvgRasterSize(1080, 3085)

        assertTrue("the raster must fit the budget", width.toLong() * height <= NOVA_SVG_RASTER_MAX_PIXELS)
        assertEquals(
            "the aspect ratio survives the scale",
            1080f / 3085f,
            width.toFloat() / height,
            0.001f
        )
    }

    @Test
    fun `a degenerate slot never asks for a zero-sized bitmap`() {
        assertEquals(1 to 1, novaSvgRasterSize(0, 0))
        assertEquals(1 to 1, novaSvgRasterSize(-10, 40))
    }

    @Test
    fun `the key carries the URL and the slot size`() {
        val key = novaSvgRasterKey("https://s4.anilist.co/file/x.svg", 1080, 540)

        assertTrue(key.contains("https://s4.anilist.co/file/x.svg"))
        assertTrue(key.contains("1080"))
        assertTrue(key.contains("540"))
        assertTrue(
            "a different width is a different card",
            key != novaSvgRasterKey("https://s4.anilist.co/file/x.svg", 720, 360)
        )
    }

    // endregion

    // region SVG document handling

    @Test
    fun `SVG sanitizer removes scripts handlers active embeds and javascript links`() {
        val sanitized = sanitizeNovaSvg(
            """<svg onload="run()"><script>bad()</script><a href=javascript:bad()><text>safe</text></a><foreignObject><iframe src="x"></iframe></foreignObject></svg>"""
        )!!

        assertTrue(sanitized.contains("<text>safe</text>"))
        assertTrue(!sanitized.contains("script", ignoreCase = true))
        assertTrue(!sanitized.contains("onload", ignoreCase = true))
        assertTrue(!sanitized.contains("javascript:", ignoreCase = true))
        assertTrue(!sanitized.contains("foreignObject", ignoreCase = true))
        assertTrue(!sanitized.contains("iframe", ignoreCase = true))
    }

    @Test
    fun `SVG aspect rejects non-finite and degenerate dimensions`() {
        assertEquals(2f, novaSvgAspect("<svg viewBox=\"0 0 NaN 10\"></svg>"))
        assertEquals(2f, novaSvgAspect("<svg viewBox=\"0 0 10 0\"></svg>"))
        assertEquals(2f, novaSvgAspect("<svg width=\"Infinity\" height=\"10\"></svg>"))
        assertEquals(2f, novaSvgAspect("<svg width=\"10\" height=\"0\"></svg>"))
    }

    // endregion
}
