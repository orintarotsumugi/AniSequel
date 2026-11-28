package com.anisequel.data.model

/**
 * How long until an episode airs, in the units the answer is actually read in.
 *
 * ## Why the last day is counted in hours and minutes
 *
 * Every branch below used to be truncated to one unit - "in 3d", "in 5h", "in
 * 30m" - and the truncation is invisible at the far end but wrong at the near
 * one. An episode 5h 20m away printed "in 5h", which is off by twenty minutes
 * for an hour, and then changed to nothing at all for the next hour: the one
 * line on the page whose whole job is "how long until the next one" was at its
 * least precise when the reader cared most. The same held for days: a show three
 * days and twenty hours out said "in 3d" and then sat still for a day.
 *
 * Under a day the minutes are kept, and inside a week the days carry their hours.
 * Past that the extra digit is noise - "in 12d 6h" is not a thing anybody plans
 * around - so a week out the days stay whole.
 */
object AiringCountdown {

    /**
     * "in 2d 4h" / "in 5h 20m" / "in 40m" / "now", or null when nothing is
     * scheduled.
     *
     * [airingAtSeconds] is seconds since the epoch, from AniList; the arithmetic
     * below is in milliseconds. A trailing zero unit is dropped rather than
     * printed - "in 5h 0m" reads as a stopped clock, and "in 5h" is the same
     * fact.
     */
    fun format(airingAtSeconds: Long?, nowMillis: Long = System.currentTimeMillis()): String? {
        val airingAt = airingAtSeconds ?: return null
        val remaining = airingAt * 1000L - nowMillis
        // Keep the release moment visible briefly instead of making the countdown
        // vanish on the exact minute it reaches zero.
        if (remaining <= 0L) return if (remaining >= -15 * 60_000L) "now" else null

        val minutes = remaining / 60_000
        val hours = minutes / 60
        val days = hours / 24
        val hoursIntoDay = hours % 24
        val minutesIntoHour = minutes % 60

        return when {
            days >= 7 -> "in ${days}d"
            days >= 1 -> if (hoursIntoDay >= 1) "in ${days}d ${hoursIntoDay}h" else "in ${days}d"
            hours >= 1 -> if (minutesIntoHour >= 1) "in ${hours}h ${minutesIntoHour}m" else "in ${hours}h"
            minutes >= 1 -> "in ${minutes}m"
            else -> "now"
        }
    }
}
