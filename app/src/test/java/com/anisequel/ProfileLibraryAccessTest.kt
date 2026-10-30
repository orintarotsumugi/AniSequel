package com.anisequel

import com.anisequel.domain.usecase.canBrowseProfileLibrary
import com.anisequel.domain.usecase.fetchProfileListEagerly
import com.anisequel.domain.usecase.mangaLibraryProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which profile may be read as a library, what has to be downloaded to read it, and
 * when a half of it cannot be read at all.
 *
 * These are the three rules the borrowed-list tab rests on, and each was a
 * user-visible failure first:
 *
 *  - Opening somebody's profile downloaded their whole anime list - up to one
 *    request per 500 entries, run sequentially, against a 30-a-minute ceiling - for
 *    two tallies on a tab the reader might never scroll to.
 *  - The Manga filter on that list set a loading flag and `return`ed, so the grid
 *    spun on a half that was never arriving: no list, no error, no reason.
 *  - The reader's own list has lived on the bar's Library page since it left the
 *    profile, so a profile tab for it would be a second door with a different
 *    remembered view behind it.
 *
 * The invariant at the bottom is the one a future change is most likely to break: a
 * profile that carries the tab is exactly a profile whose list is *not* fetched when
 * it opens, so the tab has to be able to ask for it itself.
 */
class ProfileLibraryAccessTest {

    @Test
    fun `a follower's profile can be read as a list`() {
        assertTrue(canBrowseProfileLibrary(isOwnProfile = false, isDemo = false))
    }

    @Test
    fun `your own profile does not carry somebody else's list tab`() {
        // The reader's list is the bar's page; offering it here too puts two doors
        // on one thing with two different views behind them.
        assertFalse(canBrowseProfileLibrary(isOwnProfile = true, isDemo = false))
    }

    @Test
    fun `the demo carries no list tab`() {
        assertFalse(canBrowseProfileLibrary(isOwnProfile = false, isDemo = true))
    }

    @Test
    fun `your own list comes down with the profile`() {
        // The bar's Library page has no way to ask for it, so nothing else will.
        assertTrue(fetchProfileListEagerly(isOwnProfile = true, isDemo = false))
    }

    @Test
    fun `the demo's list comes down with the profile`() {
        // A local fixture: it costs the rate limit nothing.
        assertTrue(fetchProfileListEagerly(isOwnProfile = false, isDemo = true))
    }

    @Test
    fun `a stranger's list waits for the tab that shows it`() {
        assertFalse(fetchProfileListEagerly(isOwnProfile = false, isDemo = false))
    }

    @Test
    fun `a manga list can be asked for once the id is known`() {
        assertNull(mangaLibraryProblem(isDemo = false, userId = 42))
    }

    @Test
    fun `a scanned profile says why its manga cannot be read yet`() {
        // `getUserMangaList` only takes an id, and a scan arrives with a name. The
        // guard used to swallow this; a list nobody can fetch is not an empty list.
        val problem = mangaLibraryProblem(isDemo = false, userId = null)
        assertNotNull(problem)
    }

    @Test
    fun `the demo has no manga to read`() {
        val problem = mangaLibraryProblem(isDemo = true, userId = 42)
        assertNotNull(problem)
        // The id is irrelevant here: the fixture has no manga half at all.
        assertEquals(problem, mangaLibraryProblem(isDemo = true, userId = null))
    }

    @Test
    fun `a profile carries the list tab exactly when its list is not fetched on open`() {
        for (isOwnProfile in listOf(true, false)) {
            for (isDemo in listOf(true, false)) {
                val offersTab = canBrowseProfileLibrary(isOwnProfile, isDemo)
                val fetchedOnOpen = fetchProfileListEagerly(isOwnProfile, isDemo)
                assertEquals(
                    "own=$isOwnProfile demo=$isDemo: a tab the open does not feed has " +
                        "to ask for the list itself, and a list the open already feeds " +
                        "needs no tab",
                    offersTab,
                    !fetchedOnOpen
                )
            }
        }
    }
}
