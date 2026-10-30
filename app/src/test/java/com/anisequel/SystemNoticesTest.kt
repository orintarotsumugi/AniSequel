package com.anisequel

import com.anisequel.data.model.GraphQLError
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.RateLimitNotice
import com.anisequel.data.network.SystemNotices
import com.anisequel.data.network.aniListHttpError
import com.anisequel.data.network.toAniListException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The app-wide notice bus: what raises a popup, and how often.
 *
 * ## The failures these tests prevent
 *
 *  1. **A toast stack.** A screen does not make one request, so an AniList 500
 *     fails its list, viewer and metadata queries in the same second. One notice
 *     per classified failure would be three identical toasts over one episode -
 *     the exact failure [RateLimitNotice] was built for, and why the dedupe is
 *     per kind rather than global.
 *  2. **A dead session that says nothing.** A background refresh refused on a
 *     rejected token has no sheet to draw "sign in again" on, so the reader sees
 *     a spinner and a stale page. The notice has to come from the *classification*,
 *     not from a screen that happens to render it - which is why these tests drive
 *     [aniListHttpError] and [GraphQLError.toAniListException] rather than a
 *     ViewModel.
 *  3. **A second road that forgets to notify.** Both the HTTP status and a GraphQL
 *     error entry can carry a refused token. Pinning both roads is the guard
 *     against a future edit that wires one and leaves the other quiet.
 *
 * ## Why the clock is passed in
 *
 * The windows are minutes of wall time; [SystemNotices.report]'s `now` parameter
 * lets these drive the timestamps instead of sleeping through them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemNoticesTest {

    @Before
    fun forgetTheLastReport() {
        // Both buses are process-wide, and a 429 is classified on the same road
        // as everything else, so an earlier test's rate-limit timestamp could
        // swallow this file's first notice.
        SystemNotices.reset()
        RateLimitNotice.reset()
    }

    /**
     * Subscribes for the rest of this test, before any report.
     *
     * The flow has no replay - deliberately, so a rotation cannot re-toast the
     * last notice - which means the collector has to be up first.
     */
    private fun TestScope.collectNotices(): MutableList<SystemNotices.Notice> {
        val seen = mutableListOf<SystemNotices.Notice>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SystemNotices.events.collect { seen += it }
        }
        return seen
    }

    @Test
    fun `a burst of the same kind is one notice`() = runTest {
        val seen = collectNotices()

        SystemNotices.report(SystemNotices.Notice.ServerDown, now = 1_000L)
        SystemNotices.report(SystemNotices.Notice.ServerDown, now = 1_200L)
        SystemNotices.report(SystemNotices.Notice.ServerDown, now = 5_000L)
        testScheduler.advanceUntilIdle()

        assertEquals(
            "three parallel 5xx answers are one outage, not three toasts",
            listOf(SystemNotices.Notice.ServerDown),
            seen
        )
    }

    @Test
    fun `one kind's burst does not swallow another kind`() = runTest {
        val seen = collectNotices()

        // An offline device fails reads and then the reader's own write: two
        // episodes worth saying, from two kinds, inside each other's windows.
        SystemNotices.report(SystemNotices.Notice.Offline, now = 1_000L)
        SystemNotices.report(SystemNotices.Notice.SessionExpired, now = 1_100L)
        SystemNotices.report(SystemNotices.Notice.Offline, now = 1_200L)
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(SystemNotices.Notice.Offline, SystemNotices.Notice.SessionExpired),
            seen
        )
    }

    @Test
    fun `a later episode of the same kind is heard again`() = runTest {
        val seen = collectNotices()

        SystemNotices.report(SystemNotices.Notice.ServerDown, now = 1_000L)
        SystemNotices.report(
            notice = SystemNotices.Notice.ServerDown,
            now = 1_000L + SystemNotices.Notice.ServerDown.dedupeWindowMillis * 2
        )
        testScheduler.advanceUntilIdle()

        assertEquals(2, seen.size)
    }

    @Test
    fun `an http 5xx raises the server notice from the classification alone`() = runTest {
        // No screen, no ViewModel, no fetch: the reader is told because the
        // failure was recognised, which is the property the composition-root
        // Toast depends on.
        val seen = collectNotices()

        val error = aniListHttpError(code = 500)
        testScheduler.advanceUntilIdle()

        assertEquals(AniListErrorKind.SERVER, error.kind)
        assertEquals(
            "classifying a 5xx is enough to raise the notice",
            listOf(SystemNotices.Notice.ServerDown),
            seen
        )
    }

    @Test
    fun `both roads that refuse a token raise one session notice`() = runTest {
        val seen = collectNotices()

        // The HTTP road: 401 with no body to read.
        aniListHttpError(code = 401)
        // The GraphQL road: AniList's own wording, arriving on an HTTP status
        // that could be anything.
        GraphQLError(message = "Invalid token", status = 400).toAniListException()

        testScheduler.advanceUntilIdle()
        assertEquals(
            "two roads, one refused token, one notice",
            listOf(SystemNotices.Notice.SessionExpired),
            seen
        )
    }

    @Test
    fun `a rate limit stays on its own bus`() = runTest {
        val seen = collectNotices()
        val rateLimited = mutableListOf<Int?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            RateLimitNotice.events.collect { rateLimited += it }
        }

        // 429 carries a wait, which is a message shape of its own; it must not
        // become a generic system notice.
        aniListHttpError(code = 429, retryAfterSeconds = 30)
        testScheduler.advanceUntilIdle()

        assertTrue("a 429 is not a system notice", seen.isEmpty())
        assertEquals("the rate-limit bus still answers", listOf(30), rateLimited)
    }
}
