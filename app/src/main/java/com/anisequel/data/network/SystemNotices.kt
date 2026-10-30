package com.anisequel.data.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The handful of AniList failures that are otherwise invisible, raised once each
 * for the whole app to see.
 *
 * ## Why this is not folded into [RateLimitNotice]
 *
 * A rate limit is its own thing to say - it carries a `Retry-After` number and a
 * specific "wait, do not tap" instruction - and [RateLimitNotice] is built around
 * exactly that one message, tested and shipped. This bus is the *other* cases:
 * the ones where the reader is looking at a screen that has no explanation for
 * what went wrong, because the failed request was a background one, or because
 * the surface that would have explained it is a sheet they have already closed.
 * Splitting them keeps each bus's wording honest rather than bending one
 * channel's message shape to carry the other's.
 *
 * ## Why each kind is deduplicated on its own
 *
 * None of these is ever one request. A screen fires its list, viewer and metadata
 * queries together; a dead token refuses all of them in the same second, and each
 * would otherwise post its own Toast. So a report within the kind's window after
 * the last *accepted* report of that kind is dropped - the reader gets one notice
 * per episode, whichever request is classified first - and a *different* kind is
 * never swallowed by a burst of another. The windows differ per kind because how
 * often a reader should be re-told depends on how fast the situation can change:
 * a server blip can clear in a minute, a dead token needs a sign-in and should not
 * nag every thirty seconds.
 *
 * [report] runs on OkHttp's worker threads (the classification happens there), so
 * the check-and-record is one critical section, exactly as in [RateLimitNotice].
 * The clock is a parameter so the windows are testable without sleeping through
 * them.
 */
object SystemNotices {

    /**
     * The three failure kinds and the one write confirmation, each with the
     * window a repeat of *that* kind waits behind.
     *
     * The window lives on the notice rather than in a lookup the collector
     * maintains, so adding a kind cannot forget to give it one: a new entry with
     * no window would silently inherit the last kind's and start swallowing its
     * reports.
     */
    sealed interface Notice {
        /** AniList refused the stored token - re-authenticating is the only fix. */
        data object SessionExpired : Notice

        /** AniList answered 5xx: their server, not this device. */
        data object ServerDown : Notice

        /** Nothing left this device: no usable connection. */
        data object Offline : Notice

        /**
         * A comment landed after the sheet it was written in had closed.
         *
         * Raised only in that one case, so it does not talk over the sheet's own
         * "your comment is here" scroll while that sheet is still on screen.
         */
        data object CommentPosted : Notice

        /** Milliseconds a repeat of this kind waits behind, after an accepted one. */
        val dedupeWindowMillis: Long
            get() = when (this) {
                // A dead token is a state, not a blip: once it has been said,
                // re-saying it every few seconds is nagging, and the reader's fix
                // (signing in) changes the state rather than retrying past it.
                SessionExpired -> 5 * 60_000L
                // An outage or a dropped connection can genuinely clear within a
                // minute, so a shorter window than the token: a *later* episode
                // during a long flaky stretch should still be able to say so.
                ServerDown -> 60_000L
                Offline -> 60_000L
                // A comment is one deliberate act, guarded upstream so two cannot
                // be in flight at once; nothing to collapse, but a named zero so
                // the shape stays uniform.
                CommentPosted -> 0L
            }
    }

    private val lock = Any()

    /**
     * When each kind was last accepted, keyed by the kind itself.
     *
     * The sealed objects are singletons, so they are valid map keys, and keying on
     * the notice means a burst of one kind cannot push another kind's window.
     */
    private val lastReportedAt = mutableMapOf<Notice, Long>()

    private val _events = MutableSharedFlow<Notice>(extraBufferCapacity = 1)

    /** Accepted notices, for `MainActivity` to turn into Toasts. */
    val events: SharedFlow<Notice> = _events.asSharedFlow()

    /**
     * Raises [notice], unless the same kind was raised within its window.
     *
     * [now] exists for tests; production always takes the system clock.
     */
    fun report(notice: Notice, now: Long = System.currentTimeMillis()) {
        val accepted = synchronized(lock) {
            val last = lastReportedAt[notice]
            // The timestamp moves only on an *accepted* report - the same rule
            // [RateLimitNotice] learned: a dropped report must not extend the
            // window, or a screen retrying once a second keeps the notice quiet
            // forever.
            if (last != null && now - last < notice.dedupeWindowMillis) {
                false
            } else {
                lastReportedAt[notice] = now
                true
            }
        }
        if (accepted) _events.tryEmit(notice)
    }

    /**
     * Forgets every recorded time.
     *
     * Tests only: the object is process-wide, so a report from one test would sit
     * inside the next test's window and silently swallow its first notice.
     */
    internal fun reset() {
        synchronized(lock) { lastReportedAt.clear() }
    }
}
