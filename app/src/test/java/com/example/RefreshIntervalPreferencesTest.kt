package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.RefreshInterval
import com.example.data.repository.RefreshIntervalPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The persisted refresh interval.
 *
 * The value is entirely in what happens *after* the tap. Nothing here is a
 * preference that could be written and ignored: `AniListRepositoryImpl` reads it
 * for the list cache window and `DashboardViewModel` reads it for the staleness
 * check, so a choice that is stored but never observed looks exactly like the
 * bug this setting was added to fix - a dashboard that never gets fresher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefreshIntervalPreferencesTest {

    private fun preferences(): RefreshIntervalPreferences =
        RefreshIntervalPreferences(ApplicationProvider.getApplicationContext<Context>())

    /**
     * Robolectric hands every test in a class the same application context and
     * this preferences file lives on disk, so a test that stores ONE_HOUR leaves
     * it stored for the next one. JUnit's method order is not guaranteed, which
     * makes a shared file produce failures that depend on which ran first.
     */
    @Before
    fun clearStoredPreferences() = runTest {
        preferences().reset()
    }

    @Test
    fun `defaults to thirty minutes`() = runTest {
        assertEquals(RefreshInterval.THIRTY_MINUTES, preferences().interval.first())
    }

    @Test
    fun `every interval survives being written and read back`() = runTest {
        val preferences = preferences()

        RefreshInterval.entries.forEach { interval ->
            preferences.setInterval(interval)
            assertEquals(
                "$interval must survive a round trip",
                interval,
                preferences.interval.first()
            )
        }
    }

    @Test
    fun `reset returns the shipped default`() = runTest {
        val preferences = preferences()
        preferences.setInterval(RefreshInterval.ALWAYS)

        preferences.reset()

        assertEquals(RefreshInterval.DEFAULT, preferences.interval.first())
    }

    /**
     * `AniListRepositoryImpl.cachedList` is not a suspend function - it runs on
     * the path that answers a list query out of memory - so it reads this plain
     * value instead of the Flow. Nothing has been observed yet at this point, and
     * it has to report the shipped interval rather than anything it guessed.
     */
    @Test
    fun `the synchronous read reports the default before anything is observed`() {
        assertEquals(
            "an unobserved store must fall back to the shipped default, not to a guess",
            RefreshInterval.DEFAULT.staleAfterMillis,
            preferences().currentStalenessMillis()
        )
    }

    /**
     * The wiring between the two: a choice made in Settings has to reach the value
     * the repository reads, or "15 min" keeps meaning whatever the last build's
     * constant said.
     *
     * Driven by awaiting the flow rather than by polling the plain value.
     *
     * The first version of this test started a background collector and spun on
     * `currentStalenessMillis()` until it changed. That is a race with no
     * happens-before edge between DataStore's thread and the thread reading the
     * mirror, so it passed on the pull-request run and timed out on the run to
     * main: the same commit, green once and red once. Awaiting the emission that
     * carries the value makes the ordering explicit, so the test says what it
     * means instead of how long it is willing to wait for it.
     */
    @Test
    fun `the synchronous read follows the stored interval`() = runTest {
        val preferences = preferences()

        preferences.setInterval(RefreshInterval.ONE_HOUR)

        assertEquals(
            RefreshInterval.ONE_HOUR,
            preferences.interval.first()
        )
        assertEquals(
            "a stored interval must reach the value the repository's cache reads, " +
                    "or the setting changes nothing the user can see",
            RefreshInterval.ONE_HOUR.staleAfterMillis,
            preferences.currentStalenessMillis()
        )
    }

    /**
     * The mirror follows whatever the flow last reported, including backwards.
     *
     * This is what makes the mirror safe to read from a non-suspending cache
     * lookup: it can only ever report a value the store has already emitted, never
     * one inferred from a stale copy of it.
     */
    @Test
    fun `the synchronous read follows an interval changed back`() = runTest {
        val preferences = preferences()

        preferences.setInterval(RefreshInterval.FIFTEEN_MINUTES)
        preferences.interval.first()
        assertEquals(
            RefreshInterval.FIFTEEN_MINUTES.staleAfterMillis,
            preferences.currentStalenessMillis()
        )

        preferences.setInterval(RefreshInterval.MANUAL_ONLY)
        preferences.interval.first()
        assertEquals(
            "a mirror that only ever grows would report a stale window forever",
            RefreshInterval.MANUAL_ONLY.staleAfterMillis,
            preferences.currentStalenessMillis()
        )
    }
}
