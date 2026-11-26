package com.anisequel

import com.anisequel.domain.notifications.NotificationAlertPreferences
import com.anisequel.domain.notifications.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The background switches and the interval floor.
 *
 * The mapping is pinned because it is the only thing standing between "I turned
 * likes off" and a phone that still buzzes for a like: the notification kinds are
 * finer-grained than the eight switches, and every kind has to land somewhere.
 */
class NotificationAlertPreferencesTest {

    @Test
    fun `background alerts are opt-in and default to thirty minutes`() {
        val defaults = NotificationAlertPreferences()

        assertFalse(defaults.enabled)
        assertEquals(NotificationAlertPreferences.DEFAULT_INTERVAL_MINUTES, defaults.intervalMinutes)
        assertEquals(30, defaults.intervalMinutes)
        assertTrue(defaults.anyCategoryEnabled)
    }

    @Test
    fun `every kind maps to exactly one switch`() {
        val onlyLikes = NotificationAlertPreferences(likes = true, airing = false, replies = false, mentions = false, messages = false, follows = false, mediaUpdates = false, submissions = false)
        assertTrue(onlyLikes.allows(NotificationKind.ACTIVITY_LIKE))
        assertTrue(onlyLikes.allows(NotificationKind.ACTIVITY_REPLY_LIKE))
        assertTrue(onlyLikes.allows(NotificationKind.THREAD_LIKE))
        assertTrue(onlyLikes.allows(NotificationKind.THREAD_COMMENT_LIKE))
        assertFalse(onlyLikes.allows(NotificationKind.ACTIVITY_REPLY))
        assertFalse(onlyLikes.allows(NotificationKind.FOLLOWING))

        val onlyReplies = NotificationAlertPreferences(replies = true, airing = false, likes = false, mentions = false, messages = false, follows = false, mediaUpdates = false, submissions = false)
        assertTrue(onlyReplies.allows(NotificationKind.ACTIVITY_REPLY))
        assertTrue(onlyReplies.allows(NotificationKind.ACTIVITY_REPLY_SUBSCRIBED))
        assertTrue(onlyReplies.allows(NotificationKind.THREAD_COMMENT_REPLY))
        assertTrue(onlyReplies.allows(NotificationKind.THREAD_COMMENT_SUBSCRIBED))

        val onlyMedia = NotificationAlertPreferences(mediaUpdates = true, airing = false, likes = false, replies = false, mentions = false, messages = false, follows = false, submissions = false)
        assertTrue(onlyMedia.allows(NotificationKind.RELATED_MEDIA_ADDITION))
        assertTrue(onlyMedia.allows(NotificationKind.MEDIA_DATA_CHANGE))
        assertTrue(onlyMedia.allows(NotificationKind.MEDIA_MERGE))
        assertTrue(onlyMedia.allows(NotificationKind.MEDIA_DELETION))
    }

    @Test
    fun `an unknown kind is never allowed`() {
        assertFalse(NotificationAlertPreferences(enabled = true).allows(NotificationKind.UNKNOWN))
    }

    @Test
    fun `the interval cannot fall below WorkManager's floor`() {
        assertEquals(15, NotificationAlertPreferences.clampInterval(1))
        assertEquals(15, NotificationAlertPreferences.clampInterval(14))
        assertEquals(15, NotificationAlertPreferences.clampInterval(15))
        assertEquals(60, NotificationAlertPreferences.clampInterval(60))
        assertEquals(
            NotificationAlertPreferences.MAX_INTERVAL_MINUTES,
            NotificationAlertPreferences.clampInterval(Int.MAX_VALUE)
        )
    }

    @Test
    fun `turning every category off is visible so Settings can say so`() {
        val allOff = NotificationAlertPreferences(
            airing = false,
            likes = false,
            replies = false,
            mentions = false,
            messages = false,
            follows = false,
            mediaUpdates = false,
            submissions = false
        )

        assertFalse(allOff.anyCategoryEnabled)
    }
}
