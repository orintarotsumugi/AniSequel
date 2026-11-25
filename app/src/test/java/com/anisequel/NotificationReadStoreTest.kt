package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.NotificationPreferencesStore
import com.anisequel.data.repository.NotificationReadStore
import com.anisequel.domain.notifications.NotificationFilter
import com.anisequel.domain.notifications.NotificationKind
import com.anisequel.domain.notifications.NotificationReadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The notification preferences and the per-account read mark, through the real
 * DataStore file.
 *
 * The read state is the part that has to survive process death: AniList has no
 * per-item read flag, so an in-memory-only mark would make every launch present
 * the same history as new. Each test uses its own account id because Robolectric
 * hands the class one application context and the preferences file is shared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationReadStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() = runTest {
        val store = NotificationPreferencesStore(context)
        store.setTrackingEnabled(true)
        store.setMarkReadOnOpen(true)
        store.setAiring(true)
        store.setLikes(true)
        store.setReplies(true)
        store.setMentions(true)
        store.setMessages(true)
        store.setFollows(true)
        store.setMediaUpdates(true)
        store.setSubmissions(true)
    }

    @Test
    fun `the defaults choose every category and track locally`() = runTest {
        val preferences = NotificationPreferencesStore(context).preferences.first()

        assertTrue(preferences.trackingEnabled)
        assertTrue(preferences.markReadOnOpen)
        assertEquals(NotificationFilter.entries.toList(), preferences.visibleFilters())
    }

    @Test
    fun `switching a category off hides its chip`() = runTest {
        val store = NotificationPreferencesStore(context)
        store.setLikes(false)

        val preferences = store.preferences.first()
        assertFalse(preferences.visibleFilters().contains(NotificationFilter.LIKES))
        assertFalse(preferences.isEnabled(NotificationKind.ACTIVITY_LIKE))
        // Activity stays because replies and mentions are still on.
        assertTrue(preferences.visibleFilters().contains(NotificationFilter.ACTIVITY))
    }

    @Test
    fun `turning every category off is reported as such`() = runTest {
        val store = NotificationPreferencesStore(context)
        store.setAiring(false)
        store.setLikes(false)
        store.setReplies(false)
        store.setMentions(false)
        store.setMessages(false)
        store.setFollows(false)
        store.setMediaUpdates(false)
        store.setSubmissions(false)

        val preferences = store.preferences.first()
        assertTrue(preferences.allCategoriesOff)
        assertEquals(listOf(NotificationFilter.ALL), preferences.visibleFilters())
    }

    @Test
    fun `a read mark survives a new store instance for the same account`() = runTest {
        val account = 901
        val first = NotificationReadStore(context)
        first.reset(account)
        first.anchor(account, NotificationReadState.anchorFor(listOf(100, 99), 2))
        first.markRead(account, listOf(100))

        // A new object is what a relaunch has: the state must come from disk.
        val afterRestart = NotificationReadStore(context).load(account)
        assertTrue(afterRestart.anchored)
        assertFalse(afterRestart.isUnread(100))
        assertTrue(afterRestart.isUnread(99))
    }

    @Test
    fun `read state is keyed by account`() = runTest {
        val store = NotificationReadStore(context)
        store.reset(902)
        store.reset(903)
        store.anchor(902, NotificationReadState.anchorFor(listOf(100), 0))

        assertTrue(store.load(902).anchored)
        assertFalse(store.load(903).anchored)
    }

    @Test
    fun `mark all read persists and a new arrival stays unread`() = runTest {
        val account = 904
        val store = NotificationReadStore(context)
        store.reset(account)
        store.anchor(account, NotificationReadState.anchorFor(listOf(100, 99), 2))
        store.markAllRead(account, 100)

        val reloaded = NotificationReadStore(context).load(account)
        assertFalse(reloaded.isUnread(100))
        assertFalse(reloaded.isUnread(99))
        assertTrue(reloaded.isUnread(101))
    }

    @Test
    fun `reset forgets the mark for one account only`() = runTest {
        val store = NotificationReadStore(context)
        store.reset(905)
        store.reset(906)
        store.anchor(905, NotificationReadState.anchorFor(listOf(100), 0))
        store.anchor(906, NotificationReadState.anchorFor(listOf(200), 0))

        store.reset(905)

        assertFalse(store.load(905).anchored)
        assertTrue(store.load(906).anchored)
    }
}
