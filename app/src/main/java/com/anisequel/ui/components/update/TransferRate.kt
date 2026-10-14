package com.anisequel.ui.components.update

/**
 * A smoothed bytes-per-second reading, for the line under the progress bar.
 *
 * ## Why it is a window and not a division
 *
 * The naive reading is "bytes so far, divided by seconds so far", and it is the
 * number that is wrong on a real download. A phone on a train alternates between
 * fast and stalled; dividing the whole transfer by its whole duration reports
 * the average of that, which on a download that stalled for a minute reads as
 * *slow* even while the bar is visibly moving. Worse, on a resumed download the
 * first progress callback already carries every byte from the previous attempt,
 * so a whole-transfer average opens at a rate no connection has ever achieved.
 *
 * ## What it actually measures
 *
 * Only the samples still inside [windowMillis], and always at least two. The
 * earliest retained sample is the baseline, so a first sample that jumps -
 * because the transfer is resuming, or because the window had emptied - costs
 * nothing and does not become a spike. What is left is the rate over the last
 * couple of seconds, which is what a person watching a progress bar means by
 * "how fast is this going".
 *
 * A new [sample] whose byte count is *lower* than the last one is a transfer
 * that started over, so the history is dropped rather than averaged backwards.
 *
 * Pure arithmetic with the clock passed in: no Android types, so it can be
 * tested against readings a real network never produces on demand.
 */
internal class TransferRate(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {

    private data class Reading(val atMillis: Long, val bytes: Long)

    private val readings = ArrayDeque<Reading>()
    private var lastBytes = 0L

    /** Bytes per second over the window, or null while there is not enough to say. */
    fun sample(bytesRead: Long, nowMillis: Long): Long? {
        if (bytesRead < lastBytes) readings.clear()
        lastBytes = bytesRead

        readings.addLast(Reading(nowMillis, bytesRead))

        // Two samples are always kept, however old: with only one there is no
        // interval to measure a rate over, and reporting null until a second
        // reading arrives would mean the first seconds of a download show no
        // speed at all.
        while (readings.size > 2 && readings.first().atMillis + windowMillis < nowMillis) {
            readings.removeFirst()
        }

        val first = readings.first()
        val last = readings.last()
        val elapsed = last.atMillis - first.atMillis
        if (elapsed <= 0L) return null

        return (last.bytes - first.bytes) * 1000L / elapsed
    }

    /** Forgets everything, for a transfer that is starting from zero. */
    fun reset() {
        readings.clear()
        lastBytes = 0L
    }

    private companion object {
        /**
         * Two seconds.
         *
         * Long enough that a momentary stall does not read as "stopped", short
         * enough that the number is still about the connection the user has now
         * rather than the one they had a minute ago.
         */
        const val DEFAULT_WINDOW_MILLIS = 2_000L
    }
}