package com.anisequel

import com.anisequel.domain.color.ArgbHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one string format the anime layer stores.
 *
 * The sheet writes the extracted seed as a preference; the theme reads it back.
 * A parser that accepted different spellings to the formatter would make a
 * colour work until the app restarted, which is the kind of bug that looks like
 * "the preference did not save".
 */
class ArgbHexTest {

    @Test
    fun `formats six digits with a leading hash`() {
        assertEquals("#6750A4", ArgbHex.format(0xFF6750A4.toInt()))
        assertEquals("#000000", ArgbHex.format(0xFF000000.toInt()))
        assertEquals("#FFFFFF", ArgbHex.format(0xFFFFFFFF.toInt()))
    }

    @Test
    fun `parses the formatter's output back to the same argb`() {
        val values = listOf(
            0xFF6750A4.toInt(),
            0xFF00BFA5.toInt(),
            0xFFFF9100.toInt(),
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt()
        )
        values.forEach { value ->
            assertEquals(
                "round trip of ${ArgbHex.format(value)}",
                ArgbHex.parse(ArgbHex.format(value)),
                value
            )
        }
    }

    @Test
    fun `accepts alpha and rejects junk`() {
        assertEquals(0xFF123456.toInt(), ArgbHex.parse("123456"))
        assertEquals(0x80123456.toInt(), ArgbHex.parse("#80123456"))
        assertNull(ArgbHex.parse(null))
        assertNull(ArgbHex.parse(""))
        assertNull(ArgbHex.parse("#12345"))
        assertNull(ArgbHex.parse("#GGGGGG"))
        assertNull(ArgbHex.parse("0x123456"))
    }
}
