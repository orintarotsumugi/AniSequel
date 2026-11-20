package com.anisequel

import com.anisequel.domain.ui.DisplayDpi
import com.anisequel.domain.ui.DpiMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Density mode, read as pure functions.
 *
 * The failure this guards is a mode that looks right in the picker but cannot be
 * trusted at the composition root: if Automatic inherited the manual clamp, a
 * tablet reporting 640 dpi would be quietly drawn at 600 and the page would still
 * print the device's real number. The two paths have to stay different.
 */
class DpiSettingsTest {

    @Test
    fun `automatic follows the device even past the manual ceiling`() {
        assertEquals(
            640f / 160f,
            DisplayDpi.densityFor(DpiMode.AUTOMATIC, manualDpi = 447, deviceDpi = 640),
            0.0001f
        )
    }

    @Test
    fun `manual keeps the clamp so a hand-edited value stays drawable`() {
        assertEquals(
            DisplayDpi.clamp(900) / 160f,
            DisplayDpi.densityFor(DpiMode.MANUAL, manualDpi = 900, deviceDpi = 480),
            0.0001f
        )
    }

    @Test
    fun `a device that reports no density falls back to the shipped default`() {
        assertEquals(
            DisplayDpi.DEFAULT_DPI / 160f,
            DisplayDpi.densityFor(DpiMode.AUTOMATIC, manualDpi = 400, deviceDpi = 0),
            0.0001f
        )
    }

    @Test
    fun `an unknown stored mode falls back to manual, not automatic`() {
        assertEquals(DpiMode.MANUAL, DpiMode.fromStorage(null))
        assertEquals(DpiMode.MANUAL, DpiMode.fromStorage("a value from a newer build"))
        assertEquals(DpiMode.AUTOMATIC, DpiMode.fromStorage("automatic"))
        assertEquals(DpiMode.MANUAL, DpiMode.fromStorage("manual"))
    }
}
