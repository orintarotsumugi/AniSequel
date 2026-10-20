package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
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
 * The appearance preferences.
 *
 * The value here is entirely in what happens *after* the switch is flipped. The
 * theme wraps the entire app, so a preference that is written but not observed
 * leaves the user staring at an unchanged UI with a toggle that looks like it
 * worked - which is exactly what happened before: `dynamicColor` existed as a
 * parameter on `AniSequelTheme` and nothing in the app ever passed anything but
 * the default, so "theme from my wallpaper" could not be reached at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePreferencesTest {

    private fun preferences(): ThemePreferences =
        ThemePreferences(ApplicationProvider.getApplicationContext<Context>())

    /**
     * Robolectric hands every test in a class the same application context, and
     * the preferences file lives on disk - so a test that sets DARK leaves it
     * set for the next one. JUnit's method order is not guaranteed, which makes
     * a shared file produce failures that depend on which method ran first.
     */
    @Before
    fun clearStoredPreferences() = runTest {
        preferences().reset()
    }

    @Test
    fun `defaults to following the system with wallpaper theming off`() = runTest {
        val settings = preferences().settings.first()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertFalse(
            "an upgrade must not repaint the app's colours unasked",
            settings.useDynamicColor
        )
    }

    @Test
    fun `a chosen theme mode survives being read back`() = runTest {
        val preferences = preferences()

        preferences.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, preferences.settings.first().themeMode)
    }

    @Test
    fun `every theme mode round-trips through storage`() = runTest {
        val preferences = preferences()

        ThemeMode.entries.forEach { mode ->
            preferences.setThemeMode(mode)
            assertEquals(
                "$mode must survive a round trip",
                mode,
                preferences.settings.first().themeMode
            )
        }
    }

    @Test
    fun `the wallpaper toggle survives being read back`() = runTest {
        val preferences = preferences()

        preferences.setUseDynamicColor(true)

        assertTrue(preferences.settings.first().useDynamicColor)
    }

    @Test
    fun `reset returns appearance to the shipped defaults`() = runTest {
        val preferences = preferences()
        preferences.setThemeMode(ThemeMode.LIGHT)
        preferences.setUseDynamicColor(true)
        preferences.setAnimeThemeRetainColor(false)
        preferences.setCustomColorHex("#00BFA5")

        preferences.reset()

        val settings = preferences.settings.first()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertFalse(settings.useDynamicColor)
        assertEquals(null, settings.customColorHex)
        assertTrue(settings.animeThemeRetainColor)
    }

    @Test
    fun `custom accent color round-trips through storage`() = runTest {
        val preferences = preferences()
        preferences.setCustomColorHex("#00BFA5")
        assertEquals("#00BFA5", preferences.settings.first().customColorHex)

        preferences.setCustomColorHex(null)
        assertEquals(null, preferences.settings.first().customColorHex)
    }

    @Test
    fun `retain anime colour defaults on and round-trips through storage`() = runTest {
        val preferences = preferences()
        // An upgrade that has never written the key keeps the shipped default of
        // on, so Retain is never silently weaker for existing installs.
        assertTrue(preferences.settings.first().animeThemeRetainColor)

        preferences.setAnimeThemeRetainColor(false)
        assertFalse(preferences.settings.first().animeThemeRetainColor)

        preferences.setAnimeThemeRetainColor(true)
        assertTrue(preferences.settings.first().animeThemeRetainColor)
    }

    @Test
    fun `retaining a cover colour writes the layer and the accent together`() = runTest {
        val preferences = preferences()

        preferences.setAnimeThemeActive(true, "#7FA9D4", retainColor = true)

        val settings = preferences.settings.first()
        assertTrue(settings.animeThemeActive)
        assertEquals("#7FA9D4", settings.animeThemeColorHex)
        assertEquals(
            "Retain has to outlive the sheet, so the cover colour must also be " +
                "stored as the app's own accent",
            "#7FA9D4",
            settings.customColorHex
        )
    }

    @Test
    fun `turning the layer off always clears the stored cover colour`() = runTest {
        val preferences = preferences()
        preferences.setAnimeThemeActive(true, "#7FA9D4", retainColor = true)

        // A caller that passes the old hex "for reference" must not leave the
        // layer half-off: the next enable would silently restore a colour the
        // reader had already dismissed.
        preferences.setAnimeThemeActive(false, "#7FA9D4")

        val settings = preferences.settings.first()
        assertFalse(settings.animeThemeActive)
        assertEquals(null, settings.animeThemeColorHex)
        assertEquals(
            "the retained accent survives turning the temporary layer off",
            "#7FA9D4",
            settings.customColorHex
        )
    }

    /**
     * A temporary layer must not replace the reader's standing accent.
     *
     * Only Retain is allowed to write `customColorHex`; the "just show me this
     * show's colour" path has to leave it exactly as it was.
     */
    @Test
    fun `a temporary cover colour leaves the custom accent alone`() = runTest {
        val preferences = preferences()
        preferences.setCustomColorHex("#00BFA5")

        preferences.setAnimeThemeActive(true, "#7FA9D4", retainColor = false)

        assertEquals("#00BFA5", preferences.settings.first().customColorHex)
    }

    /**
     * The stored value is a string in a file that outlives the build that wrote
     * it. A value this build has never heard of must not be fatal - a theme
     * preference is not worth refusing to start the app over.
     */
    @Test
    fun `an unrecognised stored value falls back to following the system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("sepia"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(""))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("dark"))
    }

    /**
     * The stored form is part of the contract with the preferences file, not a
     * detail free to change: changing it would silently reset everyone's choice.
     */
    @Test
    fun `storage values are stable`() {
        assertEquals("system", ThemeMode.SYSTEM.storageValue)
        assertEquals("light", ThemeMode.LIGHT.storageValue)
        assertEquals("dark", ThemeMode.DARK.storageValue)
    }
}