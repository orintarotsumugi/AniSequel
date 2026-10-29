package com.anisequel

import com.anisequel.domain.usecase.profileIdentityFromRoute
import com.anisequel.domain.usecase.profileViewModelKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who a profile route is asking for, and the key the three profile ViewModels are
 * built on.
 *
 * The route half exists because of `getInt("userId") ?: 0`. A missing argument is
 * not AniList user 0: the fallback turned a routing bug into a live query for
 * whatever account owns that id, painted as a profile with a real name and bio that
 * nobody asked for. So the two failures pinned here are "a non-positive id is not a
 * person" and "a route with a name and no id is still a person".
 *
 * The key half exists because three profiles - the viewer's, a scanned stranger's,
 * a tapped follower's - share one `ViewModelStore` per nav entry, and one key built
 * from only half an identity hands the second profile the first one's favourites,
 * bio and selected tab.
 */
class ProfileIdentityTest {

    @Test
    fun `an id and a name resolve to that person`() {
        val identity = profileIdentityFromRoute(rawUserId = 42, rawUsername = "kai")
        assertTrue(identity.isResolvable)
        assertEquals(42, identity.userId)
        assertEquals("kai", identity.username)
        assertNull(identity.problem)
    }

    @Test
    fun `an id with no name is still somebody`() {
        // The name is decoration for the bar; the id can fetch everything.
        val identity = profileIdentityFromRoute(rawUserId = 42, rawUsername = null)
        assertTrue(identity.isResolvable)
        assertEquals(42, identity.userId)
        assertNull(identity.username)
    }

    @Test
    fun `a name with no id is somebody fetched by name`() {
        val identity = profileIdentityFromRoute(rawUserId = null, rawUsername = "rin")
        assertTrue(identity.isResolvable)
        assertNull(identity.userId)
        assertEquals("rin", identity.username)
    }

    @Test
    fun `user zero is not a person`() {
        // The exact `?: 0` this replaced: a zero must never reach the network as an
        // id, and must not be reported as a resolvable profile either.
        val identity = profileIdentityFromRoute(rawUserId = 0, rawUsername = null)
        assertFalse(identity.isResolvable)
        assertNull(identity.userId)
        assertNotNull(identity.problem)
    }

    @Test
    fun `a negative id is not a person either`() {
        val identity = profileIdentityFromRoute(rawUserId = -1, rawUsername = null)
        assertFalse(identity.isResolvable)
        assertNull(identity.userId)
    }

    @Test
    fun `a zero id with a name falls back to the name`() {
        // Losing the id is not the same as losing the profile: the overview can
        // still be fetched by name, and the id arrives inside its answer.
        val identity = profileIdentityFromRoute(rawUserId = 0, rawUsername = "kai")
        assertTrue(identity.isResolvable)
        assertNull(identity.userId)
        assertEquals("kai", identity.username)
    }

    @Test
    fun `a blank name is no name`() {
        // The by-id route always writes the query, so an unknown name arrives as
        // "" rather than absent - and "" is not a name to label a bar with.
        val identity = profileIdentityFromRoute(rawUserId = null, rawUsername = "")
        assertFalse(identity.isResolvable)
        assertNull(identity.username)
        assertNotNull(identity.problem)
    }

    @Test
    fun `neither a name nor an id cannot be fetched`() {
        val identity = profileIdentityFromRoute(rawUserId = null, rawUsername = null)
        assertFalse(identity.isResolvable)
        assertNotNull(identity.problem)
    }

    @Test
    fun `two accounts with the same display name do not share a profile key`() {
        // AniList lets two accounts keep the same name, and a follower grid is
        // where you find out. A colliding key shows the first of them twice.
        assertNotEquals(
            profileViewModelKey(username = "kai", userId = 1, isDemo = false),
            profileViewModelKey(username = "kai", userId = 2, isDemo = false)
        )
    }

    @Test
    fun `two scanned profiles differ by name`() {
        // A scan arrives with no id at all, so the name is the only half left to
        // key on - which is why the id alone was never enough either.
        assertNotEquals(
            profileViewModelKey(username = "kai", userId = null, isDemo = false),
            profileViewModelKey(username = "rin", userId = null, isDemo = false)
        )
    }

    @Test
    fun `a name-only profile is not the same key as that profile once its id lands`() {
        // Both halves have to appear: this is the difference between "fetch by name"
        // and "fetch by id", and the two must not be served one ViewModel.
        assertNotEquals(
            profileViewModelKey(username = "kai", userId = null, isDemo = false),
            profileViewModelKey(username = "kai", userId = 1, isDemo = false)
        )
    }

    @Test
    fun `a missing name is still keyed by id`() {
        val key = profileViewModelKey(username = null, userId = 7, isDemo = false)
        assertEquals("profile_unnamed_7", key)
    }

    @Test
    fun `the demo is keyed as the demo whatever it is called`() {
        assertEquals(
            "profile_demo_byname",
            profileViewModelKey(username = "", userId = null, isDemo = true)
        )
    }
}
