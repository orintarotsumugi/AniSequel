package com.anisequel

import com.anisequel.ui.theme.CoverColors
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The session cache keyed by poster URL. */
class CoverColorsTest {

    @Test
    fun `an accent is kept only for a url that has a seed`() {
        CoverColors.clear()
        CoverColors.putAccent("no-seed", 0xFF7FA9D4.toInt())

        assertNull(
            "an accent-only URL used to take a place in the eviction order and " +
                "could push a real seed out of a full cache",
            CoverColors.accent("no-seed")
        )

        CoverColors.put("with-seed", 0xFFD64B4B.toInt())
        CoverColors.putAccent("with-seed", 0xFF7FA9D4.toInt())
        assertNotNull(CoverColors.color("with-seed"))
        assertNotNull(CoverColors.accent("with-seed"))
        CoverColors.clear()
    }
}
