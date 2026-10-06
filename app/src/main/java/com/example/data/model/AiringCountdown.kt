package com.example.data.model

/**
 * How long until an AniList `airingAt`, as a short human string.
 *
 * Shared by [MissedSequel.nextAiringCountdown] and [ArrivingEntry], because the
 * two render the same number in the same units and one of them having drifted
 * to "in 3.5d" while the other still said "in 3d" would be a bug nobody would
 * think to look for.
 */
object AiringCountdown {

    /**
     * "in 3d" / "in 5h" / "in 30m" / "now", or null when the episode has
     * already aired or nothing is scheduled.
     *
     * [airingAtSeconds] is seconds since the epoch, from AniList; the
     * arithmetic below is in milliseconds.
     */
    fun format(airingAtSeconds: Long?, nowMillis: Long = System.currentTimeMillis()): String? {
        val airingAt = airingAtSeconds ?: return null
        val remaining = airingAt * 1000L - nowMillis
        if (remaining <= 0L) return null

        val minutes = remaining / 60_000
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days >= 1 -> "in ${days}d"
            hours >= 1 -> "in ${hours}h"
            minutes >= 1 -> "in ${minutes}m"
            else -> "now"
        }
    }
}
