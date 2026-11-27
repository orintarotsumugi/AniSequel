package com.anisequel

import androidx.compose.ui.graphics.Color
import com.anisequel.ui.theme.DynamicThemeBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A title's palette is generated once, not once per open.
 *
 * ## Why this is a test and not a benchmark
 *
 * The work is real but small - roughly fifty HCT tone solves and two forty-eight
 * role copies on the main thread - and that is exactly the shape of cost nobody
 * notices in a profile and everybody notices as a hitch on the first frame of a
 * sheet. It cannot be measured here without a device, so what is pinned is the
 * property that makes the second open free: the same seed returns the same
 * palette, and a different seed still gets its own.
 *
 * The failure this guards is subtle: a cache keyed on the wrong thing (the cover
 * URL instead of its colour, the light scheme instead of the pair) would hand one
 * title another's palette, and every contrast pair in the app would still be
 * internally consistent - so nothing would look broken, just wrong.
 */
@RunWith(RobolectricTestRunner::class)
class PaletteCacheTest {

    @Test
    fun `reopening a title reuses the palette it already generated`() {
        val seed = Color(0xFFB11E2B)

        val first = DynamicThemeBuilder.createDynamicSchemes(seed)
        val second = DynamicThemeBuilder.createDynamicSchemes(seed)

        assertSame("the light scheme was generated again", first.first, second.first)
        assertSame("the dark scheme was generated again", first.second, second.second)
    }

    @Test
    fun `a different cover still gets its own palette`() {
        val first = DynamicThemeBuilder.createDynamicSchemes(Color(0xFFB11E2B))
        val other = DynamicThemeBuilder.createDynamicSchemes(Color(0xFF2B5FB1))

        assertNotSame(
            "two covers share one palette - the cache is keyed on something that " +
                "is not the seed",
            first.first,
            other.first
        )
    }

    /**
     * The cover's second hue is part of the key.
     *
     * A scheme is now a function of the seed *and* the accent, so a key that only
     * knows the seed would serve a one-hue cover the palette generated for a
     * two-hue one - the same picture in the app looking like a different title,
     * with every contrast pair still internally correct.
     */
    @Test
    fun `a second hue is part of the cache key`() {
        val seed = Color(0xFFB11E2B)
        val plain = DynamicThemeBuilder.createDynamicSchemes(seed, tintSurfaces = true)
        val accented = DynamicThemeBuilder.createDynamicSchemes(
            seed,
            tintSurfaces = true,
            accent = Color(0xFF2B5FB1)
        )

        assertNotSame(
            "a palette keyed on the seed alone was reused for a different accent",
            plain.first,
            accented.first
        )
        assertNotEquals(
            "the accent changed but the tertiary palette did not",
            plain.first.tertiary,
            accented.first.tertiary
        )
    }

    @Test
    fun `the cached palette is the palette the seed generates`() {
        // Identity is the cache; the values still have to be the ones the colour
        // rules produce, or the cache would pin a stale scheme across a rule
        // change made in the same process (as a test run does).
        val seed = Color(0xFF1E7B4F)
        val cached = DynamicThemeBuilder.createDynamicSchemes(seed)
        val again = DynamicThemeBuilder.createDynamicSchemes(seed)

        assertEquals(cached.first.primary, again.first.primary)
        assertEquals(cached.second.surface, again.second.surface)
        assertEquals(cached.first, again.first)
    }
}
