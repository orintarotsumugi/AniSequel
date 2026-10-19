package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.LibraryPreferences
import com.anisequel.ui.screens.profile.LibraryFilter
import com.anisequel.ui.screens.profile.LibrarySort
import com.anisequel.ui.screens.profile.LibraryType
import com.anisequel.ui.screens.profile.rememberedSelectionFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the Library tab remembers between visits.
 *
 * ## The failure this exists for
 *
 * The profile screen switches tabs through an `AnimatedContent`, which disposes
 * the tab it animates away from. Only scroll position was hoisted out of the
 * tab, so the status filter and sort - plain `rememberSaveable` state inside it
 * - died with every trip to Activity and back: a narrowed "Completed, newest
 * first" view came back as "All, list order", a decision the reader had already
 * made and the app silently unmade. Only the media *type* was persisted, which
 * is why the bug looked half-fixed: the Anime chip stayed selected above a grid
 * that had forgotten everything under it.
 *
 * The values are written on the tap (there is no confirm step to miss), so what
 * is pinned here is the whole round trip each choice takes through DataStore,
 * and that the three are independent writes rather than one overwriting key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryPreferencesTest {

    private fun preferences(): LibraryPreferences =
        LibraryPreferences(ApplicationProvider.getApplicationContext<Context>())

    /**
     * Robolectric hands every test in a class the same application context and
     * this preferences file lives on disk, so a stored choice would leak into the
     * next test. JUnit's method order is not guaranteed; a shared file makes the
     * failures depend on which test ran first.
     */
    @Before
    fun clearStoredPreferences() = runTest {
        preferences().reset()
    }

    @Test
    fun `nothing is remembered before anything is chosen`() = runTest {
        val preferences = preferences()
        assertNull(preferences.lastType.first())
        assertNull(preferences.lastFilter.first())
        assertNull(preferences.lastSort.first())
        assertNull(preferences.lastSortAscending.first())
    }

    @Test
    fun `every media type survives being written and read back`() = runTest {
        val preferences = preferences()

        LibraryType.entries.forEach { type ->
            preferences.setType(type.name)
            assertEquals("$type must survive a round trip", type.name, preferences.lastType.first())
        }
    }

    @Test
    fun `every status filter survives being written and read back`() = runTest {
        val preferences = preferences()

        LibraryFilter.entries.forEach { filter ->
            preferences.setFilter(filter.name)
            assertEquals(
                "$filter must survive a round trip",
                filter.name,
                preferences.lastFilter.first()
            )
        }
    }

    /**
     * Sort and direction as one choice.
     *
     * They are written in a single edit, and the direction deliberately alternates
     * per iteration: a store that wrote the name but not the flag (or cached one
     * pair across the loop) would fail here rather than restore a sort whose
     * direction the reader never picked.
     */
    @Test
    fun `sort and its direction are restored together`() = runTest {
        val preferences = preferences()

        LibrarySort.entries.forEachIndexed { index, sort ->
            val ascending = index % 2 == 0
            preferences.setSort(sort.name, ascending)

            assertEquals(sort.name, preferences.lastSort.first())
            assertEquals(
                "the direction must match the sort it was written with",
                ascending,
                preferences.lastSortAscending.first()
            )
        }
    }

    @Test
    fun `the three choices do not overwrite each other`() = runTest {
        val preferences = preferences()

        preferences.setType(LibraryType.MANGA.name)
        preferences.setFilter(LibraryFilter.COMPLETED.name)
        preferences.setSort(LibrarySort.SCORE.name, ascending = false)

        assertEquals(LibraryType.MANGA.name, preferences.lastType.first())
        assertEquals(LibraryFilter.COMPLETED.name, preferences.lastFilter.first())
        assertEquals(LibrarySort.SCORE.name, preferences.lastSort.first())
        assertEquals(false, preferences.lastSortAscending.first())
    }

    @Test
    fun `reset forgets every choice at once`() = runTest {
        val preferences = preferences()
        preferences.setType(LibraryType.ANIME.name)
        preferences.setFilter(LibraryFilter.WATCHING.name)
        preferences.setSort(LibrarySort.UPDATED_AT.name, ascending = false)

        preferences.reset()

        assertNull(preferences.lastType.first())
        assertNull(preferences.lastFilter.first())
        assertNull(preferences.lastSort.first())
        assertNull(preferences.lastSortAscending.first())
    }

    /**
     * The Library tab's remembered view, resolved once per visit.
     *
     * The resolver's whole job is the two-way distinction the tab draws on:
     * `answered = true` with nulls is "never chosen" - draw the defaults - and
     * `answered = false` is the one state that draws the resolving spinner. A
     * resolver that emitted nothing until something was stored, or that reported
     * `answered = false` on its first value, would put the spinner back on screen
     * permanently for exactly the accounts that had never chosen a view.
     */
    @Test
    fun `the first remembered view is answered even when nothing is stored`() = runTest {
        val resolved = rememberedSelectionFlow(preferences()).first()

        assertTrue("the first emission is the answer, not a pending state", resolved.answered)
        assertNull(resolved.type)
        assertNull(resolved.filter)
        assertNull(resolved.sort)
        assertNull(resolved.sortAscending)
    }

    @Test
    fun `the remembered view resolves every stored choice at once`() = runTest {
        val preferences = preferences()
        preferences.setType(LibraryType.ANIME.name)
        preferences.setFilter(LibraryFilter.COMPLETED.name)
        preferences.setSort(LibrarySort.SCORE.name, ascending = true)

        val resolved = rememberedSelectionFlow(preferences).first()

        assertEquals(LibraryType.ANIME, resolved.type)
        assertEquals(LibraryFilter.COMPLETED, resolved.filter)
        assertEquals(LibrarySort.SCORE, resolved.sort)
        assertEquals(true, resolved.sortAscending)
        // The store travels with the values because the tab must be able to *write*
        // a choice before the first answer arrives.
        assertSame(preferences, resolved.preferences)
    }

    /**
     * A stored name this build no longer knows resolves to the default view.
     *
     * This is what makes adding or renaming an enum entry a recoverable change
     * rather than a crashed tab: the preference is a string in DataStore, and a
     * build that reads a name it does not have must land on its own default rather
     * than an empty list or a crash.
     */
    @Test
    fun `a stored name this build does not know resolves to the default`() = runTest {
        val preferences = preferences()
        preferences.setType("WATCHING")
        preferences.setFilter("NOT_A_FILTER")

        val resolved = rememberedSelectionFlow(preferences).first()

        assertTrue(resolved.answered)
        assertNull("an unknown type falls back to the tab's default", resolved.type)
        assertNull(resolved.filter)
    }
}
