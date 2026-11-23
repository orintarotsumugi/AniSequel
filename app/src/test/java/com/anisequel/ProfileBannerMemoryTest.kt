package com.anisequel

import androidx.compose.ui.geometry.Size
import com.anisequel.ui.screens.profile.BannerAspectMemory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The remembered banner measurement.
 *
 * The header's height comes from the decoded picture, which means the first frame
 * of an open only knows the placeholder ratio. That is an honest animation the
 * first time a banner is seen; on the tenth visit to a profile whose banner is on
 * disk it is a header that jumps for no reason. These pin the two halves of the
 * fix: a measured size is available next time, and an unusable measurement is
 * never remembered (a `Size.Zero` hint would freeze the header at the placeholder
 * height, which is worse than the jump it replaced).
 */
class ProfileBannerMemoryTest {

    @Before
    fun setUp() {
        BannerAspectMemory.clear()
    }

    @After
    fun tearDown() {
        BannerAspectMemory.clear()
    }

    @Test
    fun `a measured banner is laid out at the same height next time`() {
        val url = "https://s4.anilist.co/file/anilistcdn/user/banner/b1-landscape.jpg"
        BannerAspectMemory.remember(url, Size(1920f, 1080f))

        assertEquals(Size(1920f, 1080f), BannerAspectMemory.of(url))
    }

    @Test
    fun `an unusable measurement is not remembered`() {
        val url = "https://s4.anilist.co/file/anilistcdn/user/banner/unreadable.svg"
        BannerAspectMemory.remember(url, Size.Zero)
        BannerAspectMemory.remember(url, Size(Float.NaN, 400f))

        assertNull(
            "a zero or NaN measurement must not become the header's height forever",
            BannerAspectMemory.of(url)
        )
    }

    @Test
    fun `an unknown banner has no remembered size`() {
        assertNull(BannerAspectMemory.of("https://example.test/never-drawn.jpg"))
        assertNull(BannerAspectMemory.of(null))
    }

    @Test
    fun `the memory is bounded and drops the least recently used banner`() {
        repeat(BannerAspectMemory.MAX_ENTRIES + 2) { index ->
            BannerAspectMemory.remember("https://example.test/banner-$index.jpg", Size(800f, 400f))
        }

        assertNull(
            "the oldest banner must be evicted rather than growing forever",
            BannerAspectMemory.of("https://example.test/banner-0.jpg")
        )
        assertEquals(
            Size(800f, 400f),
            BannerAspectMemory.of("https://example.test/banner-${BannerAspectMemory.MAX_ENTRIES + 1}.jpg")
        )
    }
}
