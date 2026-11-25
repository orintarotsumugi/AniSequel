package com.anisequel

import com.anisequel.domain.notifications.NotificationReadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The local read watermark.
 *
 * AniList sends one count and no per-row read flag, so this arithmetic is the
 * only thing standing between a reader and a full inbox resurrecting as new on
 * every launch. The cases below are the ones that produced that bug in the
 * reference screens: a missing server count, a refresh after a local read, and
 * an arrival above a watermark that already claimed everything older.
 */
class NotificationReadStateTest {

    @Test
    fun `an unanchored state reports nothing unread`() {
        assertFalse(NotificationReadState().isUnread(100))
    }

    @Test
    fun `the server count decides how many of the newest rows start unread`() {
        val state = NotificationReadState.anchorFor(listOf(100, 99, 98), 2)

        assertTrue(state.isUnread(100))
        assertTrue(state.isUnread(99))
        assertFalse(state.isUnread(98))
    }

    @Test
    fun `a zero count marks the whole first page read`() {
        val state = NotificationReadState.anchorFor(listOf(100, 99, 98), 0)

        assertFalse(state.isUnread(100))
        assertFalse(state.isUnread(98))
    }

    @Test
    fun `a count larger than the page leaves every row unread`() {
        val state = NotificationReadState.anchorFor(listOf(100, 99, 98), 25)

        assertTrue(state.isUnread(100))
        assertTrue(state.isUnread(99))
        assertTrue(state.isUnread(98))
    }

    @Test
    fun `a missing count is conservative and presents nothing as new`() {
        val state = NotificationReadState.anchorFor(listOf(100, 99, 98), null)

        assertFalse(state.isUnread(100))
        assertFalse(state.isUnread(98))
    }

    @Test
    fun `an empty page still anchors so history is not guessed as new`() {
        val state = NotificationReadState.anchorFor(emptyList(), 9)

        assertTrue(state.anchored)
        assertEquals(0, state.readThroughId)
        assertTrue(state.isUnread(1))
    }

    @Test
    fun `an arrival above the watermark stays unread`() {
        val anchored = NotificationReadState.anchorFor(listOf(100, 99), 0)

        assertFalse(anchored.isUnread(100))
        assertTrue(anchored.isUnread(101))
    }

    @Test
    fun `marking one row read does not disturb its neighbours`() {
        val anchored = NotificationReadState.anchorFor(listOf(100, 99, 98), 3)
        val read = anchored.markRead(100)

        assertFalse(read.isUnread(100))
        assertTrue(read.isUnread(99))
        assertTrue(read.isUnread(98))
    }

    @Test
    fun `marking an already-old row read changes nothing`() {
        val anchored = NotificationReadState.anchorFor(listOf(100, 99), 0)
        val read = anchored.markRead(98)

        assertEquals(anchored, read)
    }

    @Test
    fun `mark all read covers rows below the newest loaded id`() {
        val anchored = NotificationReadState.anchorFor(listOf(100, 99), 2)
        val read = anchored.markAllRead(100)

        assertFalse(read.isUnread(100))
        assertFalse(read.isUnread(99))
        assertFalse(read.isUnread(50))
        assertTrue(read.isUnread(101))
    }

    @Test
    fun `mark all read keeps a read mark above the loaded page`() {
        val state = NotificationReadState(
            anchored = true,
            readThroughId = 100,
            readIds = setOf(105)
        )

        val read = state.markAllRead(104)

        assertFalse(read.isUnread(105))
        assertFalse(read.isUnread(100))
        assertTrue(read.isUnread(106))
    }

    @Test
    fun `mark all read never moves the watermark backwards`() {
        val state = NotificationReadState(anchored = true, readThroughId = 100)
        val read = state.markAllRead(50)

        assertEquals(100, read.readThroughId)
    }

    @Test
    fun `a grouped set is read only after every member is marked`() {
        val anchored = NotificationReadState.anchorFor(listOf(100, 99, 98), 3)
        val firstTwo = listOf(100, 99).fold(anchored) { state, id -> state.markRead(id) }

        assertFalse(firstTwo.isUnread(100))
        assertFalse(firstTwo.isUnread(99))
        assertTrue(firstTwo.isUnread(98))
    }

    @Test
    fun `a late DataStore snapshot cannot undo a row just read`() {
        val anchored = NotificationReadState.anchorFor(listOf(100, 99), 2)
            .copy(resetGeneration = 4)
        val locallyRead = anchored.markRead(100)

        val reconciled = locallyRead.reconcile(anchored)

        assertEquals(locallyRead, reconciled)
        assertFalse(reconciled.isUnread(100))
        assertTrue(reconciled.isUnread(99))
    }

    @Test
    fun `a reset from Settings wins over a mark already on screen`() {
        val marked = NotificationReadState(
            anchored = true,
            readThroughId = 100,
            resetGeneration = 2
        )
        val reset = NotificationReadState(resetGeneration = 3)

        assertEquals(reset, marked.reconcile(reset))
    }

    @Test
    fun `an older generation cannot clear a newer read mark`() {
        val current = NotificationReadState(
            anchored = true,
            readThroughId = 100,
            resetGeneration = 5
        )
        val stale = NotificationReadState(resetGeneration = 4)

        assertEquals(current, current.reconcile(stale))
    }
}
