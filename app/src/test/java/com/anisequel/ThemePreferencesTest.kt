package com.anisequel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.RetainedTint
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.data.repository.themeDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `the retained tint strength defaults to full and round-trips`() = runTest {
        val preferences = preferences()
        assertEquals(RetainedTint.FULL, preferences.settings.first().retainedTint)

        preferences.setRetainedTint(RetainedTint.SUBTLE)
        assertEquals(RetainedTint.SUBTLE, preferences.settings.first().retainedTint)

        preferences.setRetainedTint(RetainedTint.BALANCED)
        assertEquals(RetainedTint.BALANCED, preferences.settings.first().retainedTint)

        preferences.reset()
        assertEquals(RetainedTint.FULL, preferences.settings.first().retainedTint)
    }

    @Test
    fun `retained tint strengths are ordered and stable for storage`() {
        assertEquals("subtle", RetainedTint.SUBTLE.storageValue)
        assertEquals("balanced", RetainedTint.BALANCED.storageValue)
        assertEquals("full", RetainedTint.FULL.storageValue)
        assertTrue(RetainedTint.SUBTLE.strength < RetainedTint.BALANCED.strength)
        assertTrue(RetainedTint.BALANCED.strength < RetainedTint.FULL.strength)
        assertEquals(RetainedTint.FULL, RetainedTint.fromStorage("sepia"))
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

    /**
     * Retention is the accent, not the layer.
     *
     * The immersive layer - the one that tints the surfaces - is the sheet's, and
     * it is process state on purpose: the old build persisted it, so a restart left
     * a title's hue in every surface forever and a temporary colour that was waiting
     * on an in-memory timer came back still applied with nothing left to clear it.
     */
    @Test
    fun `retaining a cover colour writes the accent and keeps the layer in memory`() = runTest {
        val preferences = preferences()

        preferences.applyAnimeColorLayer("#7FA9D4", retain = true)

        val settings = preferences.settings.first()
        assertTrue("the layer is applied", settings.animeThemeActive)
        assertEquals("#7FA9D4", settings.animeThemeColorHex)
        assertEquals(
            "Retain has to outlive the sheet, so the cover colour must also be " +
                "stored as the app's own accent",
            "#7FA9D4",
            settings.customColorHex
        )
        assertEquals(
            "and the app has to know that *this* accent came from the anime, so " +
                "turning the feature off can take it back",
            "#7FA9D4",
            settings.animeThemeRetainedColorHex
        )

        // The sheet closing ends the immersive layer. The accent is what stays.
        preferences.clearAnimeColorLayer()

        val afterClose = preferences.settings.first()
        assertFalse("the layer belongs to the sheet", afterClose.animeThemeActive)
        assertEquals("#7FA9D4", afterClose.customColorHex)
        assertEquals(
            "and the tinted scheme has to keep a colour to be built from, or the " +
                "background snaps back to the palette the moment the sheet closes",
            "#7FA9D4",
            afterClose.retainedAnimeColorHex
        )
    }

    @Test
    fun `a temporary cover colour leaves the custom accent alone`() = runTest {
        val preferences = preferences()
        preferences.setCustomColorHex("#00BFA5")

        preferences.applyAnimeColorLayer("#7FA9D4", retain = false)

        assertEquals(
            "the layer is a preview of one title, not a rewrite of the reader's accent",
            "#00BFA5",
            preferences.settings.first().customColorHex
        )
        assertEquals(
            null,
            preferences.settings.first().animeThemeRetainedColorHex
        )
    }

    /**
     * Another control taking the theme has to be able to take it back.
     *
     * A retained colour now drives the background as well as the accent, so a
     * control that should own the scheme - the wallpaper switch is the one - would
     * otherwise be silently overruled by a title the reader opened earlier. The
     * release removes the marker and the accent only because the marker proves the
     * anime wrote the accent.
     */
    @Test
    fun `releasing the anime claim clears what the anime wrote`() = runTest {
        val preferences = preferences()
        preferences.applyAnimeColorLayer("#D64B4B", retain = true)
        preferences.clearAnimeColorLayer()

        preferences.releaseAnimeRetainedColor()

        val settings = preferences.settings.first()
        assertNull(settings.animeThemeRetainedColorHex)
        assertNull(
            "the accent this feature wrote has to go with the marker, or the " +
                "reader is left with a colour they cannot explain",
            settings.customColorHex
        )
    }

    @Test
    fun `releasing the anime claim leaves a reader's own accent alone`() = runTest {
        val preferences = preferences()
        preferences.applyAnimeColorLayer("#D64B4B", retain = true)
        preferences.clearAnimeColorLayer()
        // An explicit pick · the marker goes with it, and the hue is the reader's.
        preferences.setCustomColorHex("#00BFA5")

        preferences.releaseAnimeRetainedColor()

        assertEquals("#00BFA5", preferences.settings.first().customColorHex)
    }

    /**
     * The switch and the layer are different questions.
     *
     * "May a sheet recolour the app" is stored; "a sheet is recolouring the app"
     * is not. Collapsing them into one flag is what made a temporary colour
     * permanent after a restart, and let a retained colour keep tinting surfaces
     * the reader never asked to move.
     */
    @Test
    fun `the stored switch survives while the applied layer does not`() = runTest {
        val preferences = preferences()
        preferences.setAnimeThemeEnabled(true)
        preferences.applyAnimeColorLayer("#D64B4B", retain = false)

        assertTrue(preferences.settings.first().animeThemeActive)
        assertTrue(preferences.settings.first().animeThemeEnabled)

        // What a new process sees: the feature is still on, the colour is gone.
        preferences.clearAnimeColorLayer()

        val settings = preferences.settings.first()
        assertFalse("no layer may outlive the process that applied it", settings.animeThemeActive)
        assertEquals(null, settings.animeThemeColorHex)
        assertTrue("and the switch the reader set stays set", settings.animeThemeEnabled)
    }

    @Test
    fun `turning the feature off takes back only the colour it wrote`() = runTest {
        val preferences = preferences()
        preferences.setAnimeThemeEnabled(true)
        preferences.applyAnimeColorLayer("#7FA9D4", retain = true)

        preferences.setAnimeThemeEnabled(false)

        val settings = preferences.settings.first()
        assertFalse(settings.animeThemeEnabled)
        assertFalse(settings.animeThemeActive)
        assertEquals(
            "the accent the anime retained is the feature's, so the feature ends it",
            null,
            settings.customColorHex
        )
        assertEquals(null, settings.animeThemeRetainedColorHex)
    }

    @Test
    fun `turning the feature off keeps an accent the reader chose`() = runTest {
        val preferences = preferences()
        preferences.setCustomColorHex("#00BFA5")
        preferences.setAnimeThemeEnabled(true)

        preferences.setAnimeThemeEnabled(false)

        assertEquals(
            "a hue picked in Settings is not this feature's to clear, even when a " +
                "cover happens to share it",
            "#00BFA5",
            preferences.settings.first().customColorHex
        )
    }

    /**
     * An upgrade keeps working.
     *
     * Older builds stored the switch in `anime_theme_active` next to a stored layer
     * colour. The switch is read from the old key until the new one is written; the
     * layer is not, which is the point - nobody upgraded expecting the last title
     * they had opened to still be painting their surfaces.
     */
    @Test
    fun `the legacy stored flag reads as the switch being on`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.themeDataStore.edit { preferences ->
            preferences[androidx.datastore.preferences.core.booleanPreferencesKey("anime_theme_active")] = true
            preferences[androidx.datastore.preferences.core.stringPreferencesKey("anime_theme_color_hex")] = "#D64B4B"
        }

        val settings = preferences().settings.first()
        assertTrue(settings.animeThemeEnabled)
        assertFalse(
            "a layer applied by an older build does not carry into this one",
            settings.animeThemeActive
        )
        assertEquals(null, settings.animeThemeColorHex)

        preferences().setAnimeThemeEnabled(true)

        val rewritten = preferences().settings.first()
        assertTrue(rewritten.animeThemeEnabled)
        assertEquals(
            "the first write moves the value to the key this build reads, so the " +
                "meaning never depends on which of two keys a later build looks at",
            true,
            context.themeDataStore.data.first()[
                androidx.datastore.preferences.core.booleanPreferencesKey("anime_theme_enabled")
            ]
        )
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