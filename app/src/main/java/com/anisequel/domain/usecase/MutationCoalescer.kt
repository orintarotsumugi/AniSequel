package com.anisequel.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Collapses a burst of writes for one key into the settled value.
 *
 * ## Why a write path needs this even though the editor stages edits
 *
 * The title editor sends one request on "Save changes", so it has no burst to
 * collapse. The controls this exists for are the ones that come next: a `+`
 * stepper on a progress row and a score slider. A reader who is eight episodes
 * behind taps `+` five times in a second, and without a coalescer that is five
 * `SaveMediaListEntry` mutations against an API that allows about thirty
 * requests a minute - spent to end at `progress: 9`, which the first request
 * could have carried.
 *
 * Keyed per media id, not globally: two different titles being edited at once
 * are two writes, and merging them would drop one.
 *
 * ## The suppress rules
 *
 *  - A value equal to the last **committed** value and with no write pending is
 *    dropped. A like -> unlike -> like sequence inside one window therefore
 *    sends zero requests: the third tap restores what the server already has.
 *  - A value equal to the value a pending write is already going to send is
 *    dropped. Re-tapping `+` to the same number does not extend the window.
 *  - Anything else cancels the pending write and reschedules with the new
 *    value, so the window is a debounce rather than a fixed 600ms sampler.
 *
 * ## Failure
 *
 * A failed [commit] is not retried here. Retrying a mutation automatically is
 * the one thing this class must not do: the request may have landed, and a
 * second one would apply the change twice. The caller's result handling owns
 * that decision.
 */
class MutationCoalescer<K : Any, V>(
    private val scope: CoroutineScope,
    /** How long the taps must settle before one write is sent. */
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    /** Performs the write. Throwing leaves no committed baseline behind. */
    private val commit: suspend (key: K, value: V) -> Unit
) {

    private val lock = Any()

    /** The write waiting out the window, per key. */
    private val pending = HashMap<K, Job>()

    /** The value each pending write will send, used to ignore a repeat. */
    private val pendingValues = HashMap<K, V>()

    /** The job currently sending a value, per key. */
    private val inFlight = HashMap<K, Job>()

    /** The value that in-flight job is sending. */
    private val inFlightValues = HashMap<K, V>()

    /** The last value the server accepted, used to suppress a no-op restore. */
    private val committed = HashMap<K, V>()

    /**
     * Records what the server already holds for [key].
     *
     * Callers seed this from the entry they loaded. Without it, the first tap
     * that returns a control to its original value would still send a write -
     * the coalescer has no other way to know the original value.
     */
    fun seed(key: K, value: V) {
        synchronized(lock) { committed[key] = value }
    }

    /**
     * Queues [value] for [key], replacing any write still inside the window.
     *
     * Returns whether this call changed the pending work. `false` means the
     * value was already the committed one, or already the one about to be sent.
     */
    fun submit(key: K, value: V): Boolean = synchronized(lock) {
        if (pendingValues[key] == value) return false

        if (inFlightValues[key] == value) {
            // The same value is already on its way. A compensating write queued
            // while it was in flight (the reader moved away and came back) would
            // now undo the value that is about to land, so drop it.
            pending.remove(key)?.cancel()
            pendingValues.remove(key)
            return false
        }

        if (committed[key] == value && inFlightValues[key] == null) {
            // The reader reverted the control to what the server already has,
            // and no write is in flight that could land after this. Cancelling a
            // pending write here is the like -> unlike -> like sequence sending
            // zero requests, not one request for the original value.
            //
            // When a different value *is* in flight the revert cannot be
            // dropped: that write is going to leave the server holding it, so a
            // compensating write has to follow it. This is the race that lost a
            // revert typed while the earlier request was still on the wire.
            pending.remove(key)?.cancel()
            pendingValues.remove(key)
            return false
        }

        pending.remove(key)?.cancel()
        pendingValues[key] = value
        pending[key] = scope.launch {
            delay(windowMillis)

            val job = coroutineContext[Job] ?: return@launch
            // Read the value this job was scheduled with before the maps are
            // touched: a later submit may have replaced it, and this job is
            // cancelled in that case, but a cancellation that loses the race
            // must not commit the newer value under the older job.
            synchronized(lock) {
                if (pending[key] !== job) return@launch
                pending.remove(key)
                pendingValues.remove(key)
                inFlight[key] = job
                inFlightValues[key] = value
            }

            try {
                commit(key, value)
            } finally {
                synchronized(lock) {
                    if (inFlight[key] === job) {
                        inFlight.remove(key)
                        inFlightValues.remove(key)
                    }
                }
            }

            synchronized(lock) {
                // Only the newest write may become the baseline. A slow older
                // request can finish after a newer one has already recorded its
                // value; letting the older one write `committed` here would make
                // the coalescer suppress the next tap against a value the server
                // no longer holds.
                if (pending[key] == null && inFlight[key] == null) {
                    committed[key] = value
                }
            }
        }
        true
    }

    /**
     * Waits for the pending write for [key], if there is one.
     *
     * Used by a screen that is closing and wants the last tap to land before it
     * reports the save complete. A no-op when nothing is queued.
     *
     * An in-flight write counts too: before this, closing the screen while the
     * debounce had already elapsed returned immediately even though the request
     * was still on the wire.
     */
    suspend fun flush(key: K) {
        val pendingJob = synchronized(lock) { pending[key] }
        pendingJob?.join()
        val inFlightJob = synchronized(lock) { inFlight[key] }
        inFlightJob?.join()
    }

    /** Drops a pending write, for a control the reader reverted themselves. */
    fun cancel(key: K) {
        synchronized(lock) {
            pending.remove(key)?.cancel()
            pendingValues.remove(key)
        }
    }

    /** Drops every pending write. Call from the owner's `onCleared`. */
    fun cancelAll() {
        synchronized(lock) {
            pending.values.forEach { it.cancel() }
            pending.clear()
            pendingValues.clear()
        }
    }

    companion object {
        /**
         * Six hundred milliseconds, the same window AniSync uses.
         *
         * Long enough to absorb a burst of stepper taps and short enough that a
         * reader who taps once and puts the phone down sees the value saved
         * before they look away.
         */
        const val DEFAULT_WINDOW_MILLIS = 600L
    }
}
