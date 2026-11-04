package com.anisequel

import com.anisequel.domain.seasonal.MAX_SEASONAL_FRAME_HZ
import com.anisequel.domain.seasonal.MIN_SEASONAL_FRAME_HZ
import com.anisequel.domain.seasonal.SeasonalFrameRate
import com.anisequel.domain.seasonal.availableSeasonalFrameRates
import com.anisequel.domain.seasonal.seasonalFrameHz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind the Seasonal page's frame-rate pills.
 *
 * Two questions are pinned here because both are answered by a number the app only
 * learns from a phone it cannot look at: what rate a choice turns into on a given
 * panel, and which choices a given panel is even allowed to be offered. Getting the
 * first wrong is a sky that asks for frames that never arrive; getting the second
 * wrong is a control that does nothing.
 */
class SeasonalFrameRateTest {

    @Test
    fun `auto follows the tier and never outruns the panel`() {
        assertEquals(60, seasonalFrameHz(SeasonalFrameRate.AUTO, tierHz = 60, deviceHz = 120f))
        // Auto asking for sixty on a sixty-hertz panel is the ceiling, not a request
        // for more than the screen has.
        assertEquals(60, seasonalFrameHz(SeasonalFrameRate.AUTO, tierHz = 90, deviceHz = 60f))
    }

    @Test
    fun `a cap above the panel is pulled down to it`() {
        assertEquals(60, seasonalFrameHz(SeasonalFrameRate.ONE_TWENTY, tierHz = 45, deviceHz = 60f))
        // A 120 Hz panel is reported as 119.81 or 120.01 depending on the ROM, and
        // both are the same screen. Truncating would pace at 119 against a 120
        // budget and skip a step once every second and a half.
        assertEquals(120, seasonalFrameHz(SeasonalFrameRate.ONE_TWENTY, tierHz = 60, deviceHz = 119.81f))
        assertEquals(120, seasonalFrameHz(SeasonalFrameRate.ONE_TWENTY, tierHz = 60, deviceHz = 120.01f))
    }

    @Test
    fun `a cap below the panel is honoured`() {
        // The reason the setting exists: thirty on a 144 panel is a choice, not a
        // failure to reach the ceiling.
        assertEquals(30, seasonalFrameHz(SeasonalFrameRate.THIRTY, tierHz = 60, deviceHz = 144f))
        assertEquals(60, seasonalFrameHz(SeasonalFrameRate.SIXTY, tierHz = 30, deviceHz = 120f))
    }

    @Test
    fun `a device that reports nothing is treated as sixty`() {
        // A context with no display, a stub, a fold mid-transition. The answer must
        // not be "no ceiling" - that is the case where the overlay asks a 60 Hz
        // screen for 144 frames it will never hand over.
        assertEquals(60, seasonalFrameHz(SeasonalFrameRate.ONE_TWENTY, tierHz = 60, deviceHz = 0f))
        assertEquals(60, seasonalFrameHz(SeasonalFrameRate.AUTO, tierHz = 144, deviceHz = -1f))
    }

    @Test
    fun `the resolved rate stays inside the range the loop can be paced at`() {
        listOf(0f, 1f, 240f, 1_000f).forEach { device ->
            SeasonalFrameRate.entries.forEach { rate ->
                val hz = seasonalFrameHz(rate, tierHz = 60, deviceHz = device)
                assertTrue("$rate on a $device panel gave $hz, below the floor", hz >= MIN_SEASONAL_FRAME_HZ)
                assertTrue("$rate on a $device panel gave $hz, above the ceiling", hz <= MAX_SEASONAL_FRAME_HZ)
            }
        }
    }

    @Test
    fun `only the rates this screen can run at are offered`() {
        val sixty = availableSeasonalFrameRates(currentHz = 60f, supportedHz = listOf(60f))
        assertTrue(sixty.contains(SeasonalFrameRate.AUTO))
        assertTrue(sixty.contains(SeasonalFrameRate.SIXTY))
        // The failure this test was written for: a 60 Hz phone with a 120 pill on it.
        assertFalse(sixty.contains(SeasonalFrameRate.ONE_TWENTY))
        assertFalse(sixty.contains(SeasonalFrameRate.ONE_FORTY_FOUR))

        val hundredTwenty = availableSeasonalFrameRates(
            currentHz = 59.94f,
            supportedHz = listOf(59.94f, 119.81f)
        )
        assertTrue(
            "a panel that supports 120 offers it even while the system is set to 60",
            hundredTwenty.contains(SeasonalFrameRate.ONE_TWENTY)
        )

        val gaming = availableSeasonalFrameRates(currentHz = 143.98f, supportedHz = listOf(143.98f))
        assertTrue(gaming.contains(SeasonalFrameRate.ONE_FORTY_FOUR))
        // A cap below the panel is always offered: it is a reduction, and a reader
        // on a 144 Hz screen is allowed to ask for thirty to get their battery back.
        assertTrue(gaming.contains(SeasonalFrameRate.THIRTY))
    }

    @Test
    fun `a screen that reports nothing is offered the plain list`() {
        val none = availableSeasonalFrameRates(currentHz = 0f, supportedHz = emptyList())
        assertEquals(
            listOf(SeasonalFrameRate.AUTO, SeasonalFrameRate.THIRTY, SeasonalFrameRate.SIXTY),
            none
        )
    }

    @Test
    fun `the storage values round-trip and an unknown one falls back`() {
        SeasonalFrameRate.entries.forEach { rate ->
            assertEquals(rate, SeasonalFrameRate.fromStorage(rate.storageValue))
        }
        assertEquals(
            "a value from a future build must not become a rate the app cannot draw",
            SeasonalFrameRate.DEFAULT,
            SeasonalFrameRate.fromStorage("240")
        )
        assertEquals(SeasonalFrameRate.DEFAULT, SeasonalFrameRate.fromStorage(null))
    }
}
