package com.example.data.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <V : Any> coalesce(key: K, block: suspend () -> V): V {
        val existing = mutex.withLock { inFlight[key] }

        if (existing != null) return existing.await() as V

        val deferred = CompletableDeferred<Any>()
        mutex.withLock { inFlight[key] = deferred }

        return try {
            val value = block()
            deferred.complete(value)
            value
        } catch (e: Throwable) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            // Removed in a finally rather than on success only: a failure that
            // left the entry behind would make the next caller await a
            // permanently failed deferred instead of retrying.
            mutex.withLock { inFlight.remove(key) }
        }
    }

    /** Requests currently sharing a response. Exposed for tests and diagnostics. */
    suspend fun inFlightCount(): Int = mutex.withLock { inFlight.size }
}