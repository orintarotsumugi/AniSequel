package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.ArrivingPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the "Arriving rows" card remembers.
 *
 * The three switches answer different questions - whether the section exists,
 * whether it costs poster loads, and whether it draws two labelled strips - so
 * they are stored as three keys. The test that matters is that turning one on
 * does not silently rewrite another: a single edit that wrote a whole settings
 * object from a stale copy is exactly how "compact" would reset "separate".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArrivingPreferencesTest {

    private fun preferences(): ArrivingPreferences =
        ArrivingPreferences(ApplicationProvider.getApplicationContext<Context>())

    /** The preferences file is shared by every test in this class; start clean. */
    @Before
    fun clearStoredPreferences() = runTest {
        preferences().reset()
    }

    @Test
    fun `the shipped layout is one combined row of cards`() = runTest {
        val settings = preferences().settings.first()

        assertTrue("the section is shown out of the box", settings.showArrivingSection)
        assertFalse("cards are the default, not compact rows", settings.compactArrivingCards)
        assertFalse("airing and upcoming share one row by default", settings.separateArrivingGroups)
    }

    @Test
    fun `each switch survives being written and read back`() = runTest {
        val preferences = preferences()

        preferences.setShowArrivingSection(false)
        assertFalse(preferences.settings.first().showArrivingSection)
        preferences.setShowArrivingSection(true)
        assertTrue(preferences.settings.first().showArrivingSection)

        preferences.setCompactArrivingCards(true)
        assertTrue(preferences.settings.first().compactArrivingCards)
        preferences.setCompactArrivingCards(false)
        assertFalse(preferences.settings.first().compactArrivingCards)

        preferences.setSeparateArrivingGroups(true)
        assertTrue(preferences.settings.first().separateArrivingGroups)
        preferences.setSeparateArrivingGroups(false)
        assertFalse(preferences.settings.first().separateArrivingGroups)
    }

    @Test
    fun `the three choices do not overwrite each other`() = runTest {
        val preferences = preferences()

        preferences.setShowArrivingSection(false)
        preferences.setCompactArrivingCards(true)
        preferences.setSeparateArrivingGroups(true)

        val settings = preferences.settings.first()
        assertFalse(settings.showArrivingSection)
        assertTrue(settings.compactArrivingCards)
        assertTrue(settings.separateArrivingGroups)
    }

    @Test
    fun `reset forgets every choice at once`() = runTest {
        val preferences = preferences()
        preferences.setShowArrivingSection(false)
        preferences.setCompactArrivingCards(true)
        preferences.setSeparateArrivingGroups(true)

        preferences.reset()

        val settings = preferences.settings.first()
        assertTrue(settings.showArrivingSection)
        assertFalse(settings.compactArrivingCards)
        assertFalse(settings.separateArrivingGroups)
    }
}
