package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.MainTabPreferences
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
 * Whether the bottom bar offers the tier list.
 *
 * The value is entirely in what happens after the switch: `AppNavigation` builds the
 * bar's destination list from this flow, and the index the bar reports is an index
 * into *that* list rather than into `MainTab.entries`. A preference that is stored
 * but never observed would leave the switch looking alive and the bar unchanged,
 * which is the failure this class exists to pin.
 *
 * The default is part of the contract too: off, because the trophy in the Library
 * and the row in the profile are two visible doors into the same page, and the bar
 * is the app's most contended strip of screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainTabPreferencesTest {

    private fun preferences(): MainTabPreferences =
        MainTabPreferences(ApplicationProvider.getApplicationContext<Context>())

    /**
     * Robolectric hands every test in a class the same application context and this
     * preferences file lives on disk, so a test that shows the tab leaves it shown
     * for the next one. JUnit's method order is not guaranteed, which makes a shared
     * file produce failures that depend on which test ran first.
     */
    @Before
    fun clearStoredPreferences() = runTest {
        preferences().reset()
    }

    @Test
    fun `the tier tab is off out of the box`() = runTest {
        assertFalse(MainTabPreferences.DEFAULT_TIER_LIST_TAB)
        assertFalse(preferences().tierListTab.first())
    }

    @Test
    fun `the switch survives being written and read back`() = runTest {
        val preferences = preferences()

        preferences.setTierListTab(true)
        assertTrue(
            "showing the tab has to reach the flow the bar is built from",
            preferences.tierListTab.first()
        )

        preferences.setTierListTab(false)
        assertFalse(
            "and hiding it again has to travel the same way, or the switch is a " +
                "one-way door",
            preferences.tierListTab.first()
        )
    }

    @Test
    fun `reset brings the tab back to hidden`() = runTest {
        val preferences = preferences()
        preferences.setTierListTab(true)

        preferences.reset()

        assertFalse(
            "reset is the shipped default, which is off; a reset that turned the " +
                "tab on would be a second, hidden way to change the setting",
            preferences.tierListTab.first()
        )
    }
}
