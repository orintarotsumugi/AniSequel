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
        if (committed[key] == value) {
            // The reader reverted the control to what the server already has.
            // Cancelling a pending write here is the like -> unlike -> like
            // sequence sending zero requests, not one request for the original
            // value.
            pending.remove(key)?.cancel()
            pendingValues.remove(key)
            return false
        }
        if (pendingValues[key] == value) return false

        pending.remove(key)?.cancel()
        pendingValues[key] = value
        pending[key] = scope.launch {
            delay(windowMillis)

            // Read the value this job was scheduled with before the maps are
            // touched: a later submit may have replaced it, and this job is
            // cancelled in that case, but a cancellation that loses the race
            // must not commit the newer value under the older job.
            synchronized(lock) {
                if (pending[key] !== coroutineContext[Job]) return@launch
                pending.remove(key)
                pendingValues.remove(key)
            }

            commit(key, value)

            synchronized(lock) { committed[key] = value }
        }
        true
    }

    /**
     * Waits for the pending write for [key], if there is one.
     *
     * Used by a screen that is closing and wants the last tap to land before it
     * reports the save complete. A no-op when nothing is queued.
     */
    suspend fun flush(key: K) {
        val job = synchronized(lock) { pending[key] } ?: return
        job.join()
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
