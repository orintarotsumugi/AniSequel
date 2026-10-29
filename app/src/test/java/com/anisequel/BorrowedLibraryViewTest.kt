package com.anisequel

import com.anisequel.data.repository.LibraryPreferences
import com.anisequel.ui.screens.profile.LibraryFilter
import com.anisequel.ui.screens.profile.LibrarySort
import com.anisequel.ui.screens.profile.LibraryType
import com.anisequel.ui.screens.profile.borrowedLibraryView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The view a *stranger's* list is drawn with.
 *
 * `ProfileLibraryTab` became the browsing surface for two owners, and the one bug
 * worth dying on is sharing the reader's own remembered view with a borrowed list:
 * it would read the viewer's Manga-only, by-score choices onto a follower's grid -
 * choices they never made for this person - and every chip tapped there would write
 * into the store the viewer's own Library page opens from, so leaving somebody's
 * profile silently re-arranged the reader's page.
 *
 * So the store a borrowed view carries is deliberately not that store, and
 * [answered] is `true` rather than the tab's "DataStore has not answered yet"
 * spinner, which for a store that never answers would spin forever.
 */
class BorrowedLibraryViewTest {

    @Test
    fun `a borrowed list starts on an answer, never on the resolving spinner`() {
        // `answered = false` is not "no preference yet" - it is the tab's one
        // permanent loading state, because nothing is coming to flip it.
        assertTrue(borrowedLibraryView().answered)
    }

    @Test
    fun `a borrowed list is not wired to the reader's own preferences`() {
        val view = borrowedLibraryView()
        assertFalse(
            "a borrowed grid writing through LibraryPreferences would rewrite the " +
                "reader's own Library page",
            view.preferences is LibraryPreferences
        )
    }

    @Test
    fun `a borrowed list opens on the half the profile already fetched`() {
        // `ALL` would draw one type's entries under the other type's label until
        // the reader tapped the chip that asks: their manga is a separate download.
        val view = borrowedLibraryView()
        assertEquals(LibraryType.ANIME, view.type)
        assertEquals(LibraryFilter.ALL, view.filter)
        assertEquals(LibrarySort.LIST_ORDER, view.sort)
        assertEquals(true, view.sortAscending)
    }
}
