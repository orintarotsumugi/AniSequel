package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.SeasonalPreferences
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_PILE_DEPTH
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_WIND_STRENGTH
import com.anisequel.domain.seasonal.MAX_SEASONAL_PILE_DEPTH
import com.anisequel.domain.seasonal.MAX_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.MAX_SEASONAL_WIND_STRENGTH
import com.anisequel.domain.seasonal.MIN_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.SeasonalEffect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the Seasonal effects page remembers.
 *
 * The page writes nine independent keys - on, shape, density, size, speed, settle,
 * depth, wind and wind strength - and the failure worth pinning is one write
 * resetting another. That is exactly what happens when a settings object is read,
 * edited and written back from a stale copy, and it would look like a slider
 * snapping back to its default the moment a different one was touched.
 *
 * The 1.8.2 keys are also pinned as *absent by default*: an install that predates
 * them has no stored values, and the reader's existing snow must **not** wake up
 * settling and leaning on every scroll. Both change how the app feels rather than
 * only what the sky looks like, so the absent value is off - the page that added
 * them also added the switches, and turning the overlay on is still just shapes in
 * the air until one of those is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeasonalPreferencesTest {

    private fun preferences(): SeasonalPreferences =
        SeasonalPreferences(ApplicationProvider.getApplicationContext<Context>())

    /** The preferences file is shared by every test in this class; start clean. */
    @Before
    fun clearStoredPreferences() = runTest {
        preferences().reset()
    }

    @Test
    fun `the overlay is off and snowy out of the box`() = runTest {
        val settings = preferences().settings.first()

        assertFalse("a decorative layer is opted into, not switched on by an update", settings.enabled)
        assertEquals(SeasonalEffect.SNOW, settings.effect)
        assertEquals(DEFAULT_SEASONAL_DENSITY, settings.density, 0f)
        assertEquals(DEFAULT_SEASONAL_SIZE_SCALE, settings.sizeScale, 0f)
        assertEquals(DEFAULT_SEASONAL_SPEED_SCALE, settings.speedScale, 0f)
        assertFalse("settling is an extra, not something an update turns on", settings.settle)
        assertEquals(DEFAULT_SEASONAL_PILE_DEPTH, settings.pileDepth, 0f)
        assertFalse("a sky that leans on every scroll must be asked for", settings.reactToWind)
        assertEquals(DEFAULT_SEASONAL_WIND_STRENGTH, settings.windStrength, 0f)
    }

    @Test
    fun `each control survives being written and read back`() = runTest {
        val preferences = preferences()

        preferences.setEnabled(true)
        assertTrue(preferences.settings.first().enabled)

        preferences.setEffect(SeasonalEffect.SAKURA)
        assertEquals(SeasonalEffect.SAKURA, preferences.settings.first().effect)

        preferences.setDensity(0.8f)
        assertEquals(0.8f, preferences.settings.first().density, 0.0001f)

        preferences.setSizeScale(1.5f)
        assertEquals(1.5f, preferences.settings.first().sizeScale, 0.0001f)

        preferences.setSpeedScale(1.75f)
        assertEquals(1.75f, preferences.settings.first().speedScale, 0.0001f)

        preferences.setSettle(false)
        assertFalse(preferences.settings.first().settle)

        preferences.setPileDepth(0.8f)
        assertEquals(0.8f, preferences.settings.first().pileDepth, 0.0001f)

        preferences.setReactToWind(false)
        assertFalse(preferences.settings.first().reactToWind)

        preferences.setWindStrength(0.75f)
        assertEquals(0.75f, preferences.settings.first().windStrength, 0.0001f)
    }

    @Test
    fun `one control does not overwrite another`() = runTest {
        val preferences = preferences()

        preferences.setEnabled(true)
        preferences.setEffect(SeasonalEffect.OAK)
        preferences.setDensity(0.9f)
        preferences.setSizeScale(1.25f)
        preferences.setSpeedScale(0.6f)
        preferences.setSettle(false)
        preferences.setPileDepth(0.4f)
        preferences.setReactToWind(false)
        preferences.setWindStrength(0.25f)

        val settings = preferences.settings.first()
        assertTrue(settings.enabled)
        assertEquals(SeasonalEffect.OAK, settings.effect)
        assertEquals(0.9f, settings.density, 0.0001f)
        assertEquals(1.25f, settings.sizeScale, 0.0001f)
        assertEquals(0.6f, settings.speedScale, 0.0001f)
        assertFalse(settings.settle)
        assertEquals(0.4f, settings.pileDepth, 0.0001f)
        assertFalse(settings.reactToWind)
        assertEquals(0.25f, settings.windStrength, 0.0001f)
    }

    @Test
    fun `an out-of-range write is clamped on the way in`() = runTest {
        val preferences = preferences()

        preferences.setDensity(-4f)
        assertEquals(MIN_SEASONAL_DENSITY, preferences.settings.first().density, 0.0001f)

        preferences.setSizeScale(40f)
        assertEquals(MAX_SEASONAL_SIZE_SCALE, preferences.settings.first().sizeScale, 0.0001f)

        preferences.setSpeedScale(Float.NaN)
        assertEquals(
            "a NaN written to the file would be a NaN radius in the canvas",
            DEFAULT_SEASONAL_SPEED_SCALE,
            preferences.settings.first().speedScale,
            0.0001f
        )

        preferences.setPileDepth(40f)
        assertEquals(MAX_SEASONAL_PILE_DEPTH, preferences.settings.first().pileDepth, 0.0001f)

        preferences.setWindStrength(Float.NaN)
        assertEquals(
            DEFAULT_SEASONAL_WIND_STRENGTH,
            preferences.settings.first().windStrength,
            0.0001f
        )
        preferences.setWindStrength(40f)
        assertEquals(MAX_SEASONAL_WIND_STRENGTH, preferences.settings.first().windStrength, 0.0001f)
    }

    @Test
    fun `reset forgets every choice at once`() = runTest {
        val preferences = preferences()
        preferences.setEnabled(true)
        preferences.setEffect(SeasonalEffect.GINKGO)
        preferences.setDensity(1f)
        preferences.setSizeScale(2f)
        preferences.setSpeedScale(0.4f)
        preferences.setSettle(false)
        preferences.setPileDepth(1f)
        preferences.setReactToWind(false)
        preferences.setWindStrength(0f)

        preferences.reset()

        val settings = preferences.settings.first()
        assertFalse(settings.enabled)
        assertEquals(SeasonalEffect.SNOW, settings.effect)
        assertEquals(DEFAULT_SEASONAL_DENSITY, settings.density, 0.0001f)
        assertEquals(DEFAULT_SEASONAL_SIZE_SCALE, settings.sizeScale, 0.0001f)
        assertEquals(DEFAULT_SEASONAL_SPEED_SCALE, settings.speedScale, 0.0001f)
        assertFalse(settings.settle)
        assertEquals(DEFAULT_SEASONAL_PILE_DEPTH, settings.pileDepth, 0.0001f)
        assertFalse(settings.reactToWind)
        assertEquals(DEFAULT_SEASONAL_WIND_STRENGTH, settings.windStrength, 0.0001f)
    }
}
