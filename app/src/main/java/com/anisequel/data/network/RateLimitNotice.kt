package com.anisequel.data.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The one place a rate limit becomes something the reader can see.
 *
 * ## Why this exists
 *
 * A 429 used to reach the UI only as an [AniListException] that the screen it
 * belonged to rendered as its own error state. That is invisible exactly when
 * it matters: a title page's detail request fails, the sheet shows a generic
 * retry button, and nothing tells the reader that retrying immediately is
 * guaranteed to fail too - that the thing to do is wait a minute. This object
 * is the app-wide channel for that message; `MainActivity` collects it and
 * shows a Toast, wherever in the app the request came from.
 *
 * ## Why reports are deduplicated
 *
 * A rate limit is never one request. A screen fires its list, viewer and
 * metadata queries together, every one of them comes back 429 in the same
 * second, and each would separately report a notice. The reader gets a stack of
 * identical toasts and can read none of them - the one fact worth delivering,
 * "wait a minute", becomes the noise they are trying to get past. A report
 * arriving within [DEDUPE_WINDOW_MILLIS] of the previous *accepted* report is
 * therefore dropped, and the reader gets one notice per episode, whichever
 * request happens to be classified first.
 *
 * [report] is called from the network layer, which runs on OkHttp's worker
 * threads, so the check-and-record is one critical section: two parallel 429s
 * racing a plain `var` would both read "no report recently" and both emit.
 *
 * The clock is a parameter (defaulting to the system clock) so the window can
 * be tested without sleeping through it.
 */
object RateLimitNotice {

    /**
     * Twenty seconds.
     *
     * Long enough to cover the burst one screen produces: its requests are all
     * in flight together, so their failures arrive within a second or two of
     * each other. Short enough that a genuinely new episode is never swallowed:
     * when AniList is really throttling it asks for a minute or more, and the
     * earliest a reader can meaningfully retry inside that is still far past
     * this window.
     */
    internal const val DEDUPE_WINDOW_MILLIS = 20_000L

    private val lock = Any()

    /** When the last notice was accepted, or null before the first one. */
    private var lastReportedAtMillis: Long? = null

    private val _events = MutableSharedFlow<Int?>(extraBufferCapacity = 1)

    /**
     * Accepted reports, as the server's `Retry-After` seconds - or null when
     * the rate limit arrived without a usable one.
     */
    val events: SharedFlow<Int?> = _events.asSharedFlow()

    /**
     * Raises a notice, unless one was raised within [DEDUPE_WINDOW_MILLIS].
     *
     * A null [retryAfterSeconds] still emits: the fact that the reader is rate
     * limited is worth saying even when the server did not say for how long.
     *
     * [now] exists for tests; production always takes the system clock.
     */
    fun report(retryAfterSeconds: Int?, now: Long = System.currentTimeMillis()) {
        val accepted = synchronized(lock) {
            val last = lastReportedAtMillis
            // The timestamp is only moved by *accepted* reports. If a dropped
            // report also moved it, a screen that retries once a second - the
            // exact behaviour a stuck user produces - would push the window
            // forward forever and the notice would never appear at all.
            if (last != null && now - last < DEDUPE_WINDOW_MILLIS) {
                false
            } else {
                lastReportedAtMillis = now
                true
            }
        }
        if (accepted) _events.tryEmit(retryAfterSeconds)
    }

    /**
     * Forgets the last report time.
     *
     * Tests only: the object is process-wide, so without this a report from an
     * earlier test sits inside the next test's window and silently swallows its
     * first event.
     */
    internal fun reset() {
        synchronized(lock) { lastReportedAtMillis = null }
    }
}
