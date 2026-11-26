package com.anisequel

import com.anisequel.domain.notifications.NotificationActor
import com.anisequel.domain.notifications.NotificationDestinations
import com.anisequel.domain.notifications.NotificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The route a system-notification tap carries.
 *
 * This is the half of the feature that is easiest to get almost right: the
 * notification posts, the app opens, and the reader lands somewhere else. The
 * strings below are the exact formats the NavHost matches, and the rejected
 * inputs are the reason an exported Activity cannot be steered by an arbitrary
 * extra.
 */
class NotificationDestinationsTest {

    @Test
    fun `media keeps the type so anime and manga open their own namespace`() {
        assertEquals(
            "notifications/media/12?type=MANGA",
            NotificationDestinations.media(12, "MANGA")
        )
        assertEquals(
            "notifications/media/12?type=",
            NotificationDestinations.media(12, null)
        )
    }

    @Test
    fun `each target maps to its own destination`() {
        assertEquals(
            "notifications/activity/44",
            NotificationDestinations.forTarget(NotificationTarget.Activity(44, null))
        )
        assertEquals(
            "notifications/thread/7?comment=9",
            NotificationDestinations.forTarget(
                NotificationTarget.Thread(7, 9, null, null)
            )
        )
        assertEquals(
            "notifications/thread/7?comment=-1",
            NotificationDestinations.forTarget(NotificationTarget.Thread(7, null))
        )
        assertEquals(
            "profile_id/8?name=Marco",
            NotificationDestinations.forTarget(NotificationTarget.User(8, "Marco"))
        )
        assertEquals(
            NotificationDestinations.MESSAGES,
            NotificationDestinations.forTarget(NotificationTarget.Message(1, 2, "x"))
        )
        assertEquals(
            NotificationDestinations.INBOX,
            NotificationDestinations.forTarget(NotificationTarget.None)
        )
    }

    @Test
    fun `a display name with route characters is encoded`() {
        val route = NotificationDestinations.user(8, "a/b?c#d e")
        assertEquals("profile_id/8?name=a%2Fb%3Fc%23d%20e", route)
        assertTrue(NotificationDestinations.isSafe(route))
    }

    @Test
    fun `only routes this app builds are safe to navigate`() {
        assertTrue(NotificationDestinations.isSafe(NotificationDestinations.INBOX))
        assertTrue(NotificationDestinations.isSafe(NotificationDestinations.MESSAGES))
        assertTrue(NotificationDestinations.isSafe(NotificationDestinations.media(1, "ANIME")))
        assertTrue(NotificationDestinations.isSafe(NotificationDestinations.activity(1)))
        assertTrue(NotificationDestinations.isSafe(NotificationDestinations.thread(1, 2)))
        assertTrue(NotificationDestinations.isSafe(NotificationDestinations.user(1, "a")))
    }

    @Test
    fun `unknown and malformed routes are rejected`() {
        assertFalse(NotificationDestinations.isSafe(null))
        assertFalse(NotificationDestinations.isSafe(""))
        assertFalse(NotificationDestinations.isSafe("settings/notifications"))
        assertFalse(NotificationDestinations.isSafe("profile?tab=activity"))
        assertFalse(NotificationDestinations.isSafe("notifications/media/abc?type=ANIME"))
        assertFalse(NotificationDestinations.isSafe("notifications/thread/1?comment=1&next=2"))
        assertFalse(NotificationDestinations.isSafe("profile_id/1?name=a/b"))
        assertFalse(
            "an over-long extra must not be copied into navigation state",
            NotificationDestinations.isSafe("profile_id/1?name=" + "a".repeat(600))
        )
    }

    @Test
    fun `the message route is the one the inbox itself uses`() {
        assertEquals("profile?tab=activity&segment=messages", NotificationDestinations.MESSAGES)
    }

    @Test
    fun `a user target keeps the name so the bar is filled on frame one`() {
        val target = NotificationTarget.User(3, "Ana")
        assertEquals("profile_id/3?name=Ana", NotificationDestinations.forTarget(target))
        // A notification actor is not needed for the route, but this pins that
        // the type used by the inbox and the payload builder stay compatible.
        val actor = NotificationActor(3, "Ana", null)
        assertEquals(target.userId, actor.id)
    }
}
