package com.anisequel

import com.anisequel.data.model.NotificationPage
import com.anisequel.data.notifications.NotificationCheckOutcome
import com.anisequel.data.notifications.NotificationCheckRunner
import com.anisequel.data.notifications.NotificationCursorStore
import com.anisequel.data.notifications.NotificationDeliverySink
import com.anisequel.data.notifications.NotificationPageSource
import com.anisequel.domain.notifications.FollowNotification
import com.anisequel.domain.notifications.NotificationActor
import com.anisequel.domain.notifications.NotificationAlertDraft
import com.anisequel.domain.notifications.NotificationAlertPreferences
import com.anisequel.domain.notifications.NotificationDeliveryState
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The delivery pipeline end to end, without Android or AniList.
 *
 * The fakes below stand in for the repository, DataStore and the tray. Every
 * case here is a requirement of the background feature rather than a taste:
 * baseline silence, no duplicates on a retry, a failed request preserving the
 * cursor, and a blocked permission not being allowed to quietly discard events.
 */
class NotificationCheckRunnerTest {

    private fun follow(id: Int): FollowNotification = FollowNotification(
        id = id,
        createdAt = id,
        actor = NotificationActor(id, "user$id", null),
        context = null
    )

    private class FakeSource : NotificationPageSource {
        val requested = mutableListOf<Int>()
        var pages: List<NotificationPage> = emptyList()
        var failure: Throwable? = null

        override suspend fun page(page: Int, forceRefresh: Boolean): Result<NotificationPage> {
            requested += page
            failure?.let { return Result.failure(it) }
            return Result.success(
                pages.getOrNull(page - 1)
                    ?: NotificationPage(emptyList(), hasMore = false, serverUnreadCount = null)
            )
        }
    }

    private class FakeCursors(initial: NotificationDeliveryState = NotificationDeliveryState()) :
        NotificationCursorStore {
        var state: NotificationDeliveryState = initial
        var saves = 0

        override suspend fun load(accountId: Int): NotificationDeliveryState = state

        override suspend fun save(accountId: Int, state: NotificationDeliveryState) {
            this.state = state
            saves += 1
        }
    }

    private class FakeSink(var allowed: Boolean = true) : NotificationDeliverySink {
        val delivered = mutableListOf<NotificationAlertDraft>()

        override fun canDeliver(): Boolean = allowed

        override fun deliver(drafts: List<NotificationAlertDraft>): Int {
            delivered += drafts
            return drafts.size
        }
    }

    private val prefs = NotificationAlertPreferences(enabled = true)

    private fun page(ids: List<Int>, hasMore: Boolean = false) =
        NotificationPage(ids.map(::follow), hasMore, serverUnreadCount = null)

    @Test
    fun `the first check establishes a baseline and posts no history`() = runBlocking {
        val source = FakeSource().apply { pages = listOf(page((900 downTo 876).toList(), hasMore = true)) }
        val cursors = FakeCursors()
        val sink = FakeSink()
        val runner = NotificationCheckRunner(source, cursors, sink)

        val outcome = runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 10_000)

        assertEquals(NotificationCheckOutcome.BaselineEstablished, outcome)
        assertTrue(sink.delivered.isEmpty())
        assertTrue(cursors.state.baselineEstablished)
        assertEquals(900, cursors.state.deliveredThroughId)
    }

    @Test
    fun `a later check posts only genuinely new events`() = runBlocking {
        val source = FakeSource().apply { pages = listOf(page(listOf(903, 902, 900))) }
        val cursors = FakeCursors(
            NotificationDeliveryState(baselineEstablished = true, deliveredThroughId = 900)
        )
        val sink = FakeSink()
        val runner = NotificationCheckRunner(source, cursors, sink)

        val outcome = runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 10_000)

        assertEquals(NotificationCheckOutcome.Posted(2), outcome)
        assertEquals(listOf(903, 902), sink.delivered.map { it.notificationIds.single() })
        assertEquals(903, cursors.state.deliveredThroughId)
    }

    @Test
    fun `a retry of the same page posts nothing twice`() = runBlocking {
        val source = FakeSource().apply { pages = listOf(page(listOf(903, 902))) }
        val cursors = FakeCursors(
            NotificationDeliveryState(baselineEstablished = true, deliveredThroughId = 900)
        )
        val sink = FakeSink()
        val runner = NotificationCheckRunner(source, cursors, sink)

        runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 1_000)
        val second = runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 2_000)

        assertEquals(2, sink.delivered.size)
        assertEquals(NotificationCheckOutcome.NoNewEvents, second)
    }

    @Test
    fun `a failed page request preserves the cursor and asks for a retry`() = runBlocking {
        val source = FakeSource().apply { failure = IOException("offline") }
        val cursors = FakeCursors(
            NotificationDeliveryState(baselineEstablished = true, deliveredThroughId = 900)
        )
        val sink = FakeSink()
        val runner = NotificationCheckRunner(source, cursors, sink)

        val outcome = runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 1_000)

        assertTrue(outcome is NotificationCheckOutcome.Failed)
        assertTrue((outcome as NotificationCheckOutcome.Failed).retryable)
        assertEquals(0, cursors.saves)
        assertEquals(900, cursors.state.deliveredThroughId)
        assertTrue(sink.delivered.isEmpty())
    }

    @Test
    fun `a blocking permission keeps the events pending instead of discarding them`() = runBlocking {
        val source = FakeSource().apply { pages = listOf(page(listOf(901))) }
        val cursors = FakeCursors(
            NotificationDeliveryState(baselineEstablished = true, deliveredThroughId = 900)
        )
        val sink = FakeSink(allowed = false)
        val runner = NotificationCheckRunner(source, cursors, sink)

        val outcome = runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 1_000)

        assertEquals(NotificationCheckOutcome.PermissionBlocked, outcome)
        assertEquals(0, cursors.saves)
        assertEquals(900, cursors.state.deliveredThroughId)
        assertTrue(sink.delivered.isEmpty())
    }

    @Test
    fun `a disabled category posts nothing and does not replay later`() = runBlocking {
        val source = FakeSource().apply { pages = listOf(page(listOf(901))) }
        val cursors = FakeCursors(
            NotificationDeliveryState(baselineEstablished = true, deliveredThroughId = 900)
        )
        val sink = FakeSink()
        val runner = NotificationCheckRunner(source, cursors, sink)

        val off = prefs.copy(follows = false)
        val first = runner.run(accountId = 7, preferences = off, nowEpochMillis = 1_000)
        val second = runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 2_000)

        assertEquals(NotificationCheckOutcome.NoNewEvents, first)
        assertEquals(NotificationCheckOutcome.NoNewEvents, second)
        assertTrue(sink.delivered.isEmpty())
        assertEquals(901, cursors.state.deliveredThroughId)
    }

    @Test
    fun `paging stops as soon as the previous cursor is covered`() = runBlocking {
        val source = FakeSource().apply {
            pages = listOf(
                page(listOf(905, 904), hasMore = true),
                page(listOf(903, 902), hasMore = true),
                page(listOf(901), hasMore = true)
            )
        }
        val cursors = FakeCursors(
            NotificationDeliveryState(baselineEstablished = true, deliveredThroughId = 900)
        )
        val runner = NotificationCheckRunner(source, cursors, FakeSink())

        runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 1_000)

        assertEquals(listOf(1, 2, 3), source.requested)
    }

    @Test
    fun `a page containing the cursor stops the walk without asking for more`() = runBlocking {
        val source = FakeSource().apply {
            pages = listOf(
                page(listOf(905, 904), hasMore = true),
                page(listOf(901, 900, 899), hasMore = true)
            )
        }
        val cursors = FakeCursors(
            NotificationDeliveryState(baselineEstablished = true, deliveredThroughId = 900)
        )
        val runner = NotificationCheckRunner(source, cursors, FakeSink())

        runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 1_000)

        assertEquals(listOf(1, 2), source.requested)
    }

    @Test
    fun `a walk bounded before the cursor posts the newest and remembers them`() = runBlocking {
        val pageOne = page((300 downTo 276).toList(), hasMore = true)
        val pageTwo = page((275 downTo 251).toList(), hasMore = true)
        val pageThree = page((250 downTo 226).toList(), hasMore = true)
        val source = FakeSource().apply { pages = listOf(pageOne, pageTwo, pageThree) }
        val cursors = FakeCursors(
            NotificationDeliveryState(baselineEstablished = true, deliveredThroughId = 100)
        )
        val sink = FakeSink()
        val runner = NotificationCheckRunner(source, cursors, sink)

        val outcome = runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 1_000)

        assertTrue(outcome is NotificationCheckOutcome.Posted)
        assertEquals(75, sink.delivered.sumOf { it.notificationIds.size })
        assertEquals("the cursor must not jump over unseen rows", 100, cursors.state.deliveredThroughId)
        assertTrue(cursors.state.deliveredIds.isNotEmpty())

        // A second run over the same window must not repeat a single alert.
        val second = runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 2_000)
        assertEquals(NotificationCheckOutcome.NoNewEvents, second)
        assertEquals(75, sink.delivered.sumOf { it.notificationIds.size })
    }

    @Test
    fun `a backlog bigger than one run resumes at the page it stopped on`() = runBlocking {
        val pages = listOf(
            page((300 downTo 276).toList(), hasMore = true),
            page((275 downTo 251).toList(), hasMore = true),
            page((250 downTo 226).toList(), hasMore = true),
            page((225 downTo 201).toList(), hasMore = true),
            page((200 downTo 176).toList(), hasMore = true),
            page((175 downTo 151).toList(), hasMore = true),
            page((150 downTo 101).toList(), hasMore = false)
        )
        val source = FakeSource().apply { this.pages = pages }
        val cursors = FakeCursors(
            NotificationDeliveryState(baselineEstablished = true, deliveredThroughId = 100)
        )
        val sink = FakeSink()
        val runner = NotificationCheckRunner(source, cursors, sink)

        runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 1_000)
        assertEquals(listOf(1, 2, 3), source.requested)
        assertEquals(4, cursors.state.resumePage)
        assertEquals(100, cursors.state.deliveredThroughId)
        assertEquals(75, sink.delivered.sumOf { it.notificationIds.size })

        source.requested.clear()
        runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 2_000)
        // Page one for new arrivals, then the page the previous run stopped on.
        assertEquals(listOf(1, 4, 5), source.requested)
        assertEquals(6, cursors.state.resumePage)
        assertEquals(125, sink.delivered.sumOf { it.notificationIds.size })

        source.requested.clear()
        val last = runner.run(accountId = 7, preferences = prefs, nowEpochMillis = 3_000)
        assertEquals(listOf(1, 6, 7), source.requested)
        assertEquals(NotificationCheckOutcome.Posted(75), last)
        assertEquals(200, sink.delivered.sumOf { it.notificationIds.size })
        assertEquals(300, cursors.state.deliveredThroughId)
        assertEquals(1, cursors.state.resumePage)
    }

    @Test
    fun `a disabled master switch does nothing at all`() = runBlocking {
        val source = FakeSource()
        val cursors = FakeCursors()
        val runner = NotificationCheckRunner(source, cursors, FakeSink())

        val outcome = runner.run(
            accountId = 7,
            preferences = prefs.copy(enabled = false),
            nowEpochMillis = 1_000
        )

        assertEquals(NotificationCheckOutcome.Disabled, outcome)
        assertTrue(source.requested.isEmpty())
        assertEquals(0, cursors.saves)
    }
}
