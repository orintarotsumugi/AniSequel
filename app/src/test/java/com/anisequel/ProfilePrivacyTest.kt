package com.anisequel

import com.anisequel.data.model.GraphQLError
import com.anisequel.data.model.UserOverview
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.GraphQLQueries
import com.anisequel.data.network.aniListHttpError
import com.anisequel.data.network.reportsPrivateProfile
import com.anisequel.data.network.toAniListException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AniList's 2026-11-23 privacy overhaul, as the app has to honour it.
 *
 * The account setting is now `User.privacy` (`PUBLIC`, `SEMI_PUBLIC`, `PRIVATE`)
 * and **a block behaves as private**. Before that, a private account leaked parts
 * of its list under some conditions; now the server withholds the list and the
 * statistics, and the app must not paint that absence as "0 watched" - nor may it
 * mistake the refusal for a dead session.
 *
 * The second half of the file is the classification rule. AniList overloads 403
 * for both a rejected token and a withheld profile, and this app signs the reader
 * out on the first. Treating a stranger's private page as an auth failure offered
 * to end the reader's working session from somebody else's profile.
 */
class ProfilePrivacyTest {

    @Test
    fun `a private account is withheld and a public one is not`() {
        assertTrue(ViewerProfile(id = 1, name = "a", privacy = "PRIVATE").isPrivateProfile)
        assertFalse(ViewerProfile(id = 1, name = "a", privacy = "PUBLIC").isPrivateProfile)
        assertFalse(ViewerProfile(id = 1, name = "a", privacy = "SEMI_PUBLIC").isPrivateProfile)
        assertFalse(ViewerProfile(id = 1, name = "a").isPrivateProfile)
    }

    @Test
    fun `a block is as private as the setting`() {
        // The changelog's wording: "Blocks now also behave as if the blocking user
        // was private." The app only needs the one question answered.
        assertTrue(ViewerProfile(id = 1, name = "a", isBlocked = true).isPrivateProfile)
        assertTrue(UserOverview(id = 1, name = "a", isBlocked = true).isPrivateProfile)
        assertTrue(UserOverview(id = 1, name = "a", privacy = "PRIVATE").isPrivateProfile)
        assertFalse(UserOverview(id = 1, name = "a", privacy = "PUBLIC", isBlocked = false).isPrivateProfile)
    }

    @Test
    fun `a withheld profile is recognised whatever the case of the enum`() {
        // Moshi reads the enum as a string; a lowercased value from a future
        // payload must not silently read as public.
        assertTrue(ViewerProfile(id = 1, name = "a", privacy = "private").isPrivateProfile)
    }

    @Test
    fun `a private refusal is not a dead session`() {
        val error = GraphQLError(message = "User is private", status = 403).toAniListException()

        assertEquals(AniListErrorKind.PRIVATE_PROFILE, error.kind)
        assertEquals("This AniList profile is private.", error.message)
    }

    @Test
    fun `the HTTP road agrees with the GraphQL road on a 403`() {
        // Both roads classify a refused token, so the privacy wording has to win
        // before the auth branch on both - otherwise one screen signs the reader
        // out and the other shows the private notice for the same refusal.
        assertEquals(
            AniListErrorKind.PRIVATE_PROFILE,
            aniListHttpError(
                code = 403,
                reportedError = GraphQLError(message = "User is private", status = 403)
            ).kind
        )
        assertEquals(
            AniListErrorKind.INVALID_SESSION,
            aniListHttpError(
                code = 403,
                reportedError = GraphQLError(message = "Invalid token", status = 403)
            ).kind
        )
    }

    @Test
    fun `a block reads as private and an unrelated private phrase does not`() {
        assertTrue(reportsPrivateProfile("This user has blocked you"))
        assertTrue(reportsPrivateProfile("User is private"))
        assertTrue(reportsPrivateProfile("This profile is private."))
        // A private *message* is a different field and must not be read as an
        // account setting - otherwise posting one would show the private-profile
        // page.
        assertFalse(reportsPrivateProfile("Private message not found"))
        assertFalse(reportsPrivateProfile("Profile not found"))
        assertFalse(reportsPrivateProfile(""))
    }

    @Test
    fun `both user queries ask AniList how private the account is`() {
        // Without these two fields the app can only infer privacy from a refusal,
        // which is too late: it has already spent one of thirty requests a minute
        // on a list that was never coming.
        listOf(
            "dashboard" to GraphQLQueries.GET_USER_BY_NAME,
            "profile" to GraphQLQueries.GET_USER_OVERVIEW
        ).forEach { (screen, query) ->
            assertTrue("$screen query does not ask for privacy", query.contains("privacy"))
            assertTrue("$screen query does not ask for isBlocked", query.contains("isBlocked"))
        }
    }
}
