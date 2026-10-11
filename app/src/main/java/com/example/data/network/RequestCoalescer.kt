package com.example.data.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Collapses identical in-flight requests into one network call.
 *
 * ## Why this exists rather than an HTTP cache
 *
 * The obvious fix for "the same list is downloaded again" is OkHttp's disk
 * cache. It cannot work here: AniList is a GraphQL POST endpoint and **rejects
 * GET**, answering `404 "Use POST request to access graphql subdomain"`, and it
 * sends `cache-control: no-cache, private` with no `ETag`. OkHttp will not
 * store a POST response, so a cache interceptor here compiles, passes review,
 * and re-downloads the entire list every time. That was verified against the
 * live API and against OkHttp rather than assumed.
 *
 * What *is* fixable is duplicate work: several identical queries racing at once
 * when the user rotates the device, re-enters the app, or opens several detail
 * sheets. One response serves all of them.
 */
class RequestCoalescer<K : Any> {

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, CompletableDeferred<Any>>()

    /**
     * Runs [block] unless an identical request is already running, in which
     * case this caller awaits the one already in progress.
     *
     * The result is shared, so a failure is delivered to every waiter rather
     * than being retried once per waiter - a rate-limit response must not turn
     * into N parallel requests.
     *
     * ## Cancellation is not a shared result
     *
     * A leader that is cancelled mid-request completes its deferred with a
     * `CancellationException`, and that exception describes *its* job, not the
     * jobs waiting on it. Handing it to them made every waiter die as
     * cancelled too - so a second load cancelling the first (which is what
     * every refresh, rotation and re-entry does) also silently killed the load
     * that replaced it, because that one had the bad luck of arriving while
     * the first was still in flight. The screen sat on its spinner with no
     * error and no retry, because the coroutine that would have reported one
     * believed it had been cancelled.
     *
     * A follower therefore retries instead: it rethrows only if *it* was
     * cancelled, reclaims a slot the dead leader may never have released, and
     * issues the request itself. A real failure - a 429, a malformed response -
     * is still shared verbatim; only cancellation is refused.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <V : Any> coalesce(key: K, block: suspend () -> V): V {
        while (true) {
            // Claiming the slot is one critical section, not two.
            //
            // Reading `inFlight[key]` and then *separately* writing the new
            // deferred left a window between the two lock acquisitions in which
            // a second coroutine also read null, also decided it was first, and
            // also ran `block()`. Two callers asking for the same request both
            // issued it - precisely the duplicate this class exists to prevent,
            // and it only showed up under real concurrency rather than in the
            // single-threaded tests.
            val deferred = CompletableDeferred<Any>()
            val existing = mutex.withLock { inFlight.getOrPut(key) { deferred } }

            if (existing !== deferred) {
                try {
                    return existing.await() as V
                } catch (e: CancellationException) {
                    // Rethrows if this coroutine is the one being cancelled,
                    // which is the single case that must not be swallowed.
                    currentCoroutineContext().ensureActive()

                    // The leader is gone, but its slot can outlive it: the
                    // removal below runs in its `finally`, and a coroutine
                    // already cancelled will throw out of that `withLock`
                    // rather than waiting for the lock. A completed deferred
                    // left in the map is a permanent dead end - every later
                    // caller would be handed it and die on it - so the waiter
                    // that notices takes it out. `===` is what stops it from
                    // evicting a successor's slot if a new leader has already
                    // claimed the key.
                    mutex.withLock {
                        if (inFlight[key] === existing) inFlight.remove(key)
                    }
                    continue
                }
            }

            return try {
                val value = block()
                deferred.complete(value)
                value
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
                throw e
            } finally {
                // Removed in a finally rather than on success only: a failure
                // that left the entry behind would make the next caller await a
                // permanently failed deferred instead of retrying.
                //
                // Only the leader removes it, and only if the entry is still its
                // own deferred - a leader whose slot was replaced must not evict
                // the successor's request.
                //
                // `NonCancellable` is load-bearing for the *successful* completion
                // that loses the race to a cancellation. The no-waiter cleanup
                // above only fires when `await` throws, and a deferred completed
                // with a value does not throw: a leader cancelled after
                // `deferred.complete(value)` but with the mutex momentarily held
                // would throw out of this `withLock` (a cancelled coroutine does
                // not wait for a lock) and leave a *completed* deferred in the map.
                // Every later caller - a sequential refresh included - would then
                // `getOrPut` and `await` it and be served the first response
                // forever, breaking the "sequential requests are never collapsed"
                // guarantee. `NonCancellable` makes this removal happen regardless.
                withContext(NonCancellable) {
                    mutex.withLock {
                        if (inFlight[key] === deferred) inFlight.remove(key)
                    }
                }
            }
        }
    }

    /** Requests currently sharing a response. Exposed for tests and diagnostics. */
    suspend fun inFlightCount(): Int = mutex.withLock { inFlight.size }
}