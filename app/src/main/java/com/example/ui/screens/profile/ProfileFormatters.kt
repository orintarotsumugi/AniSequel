package com.example.ui.screens.profile

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared date, time, and score formatters for Profile screens.
 */

/**
 * The year an account was created.
 *
 * Null for accounts AniList does not backfill - the field simply does not exist
 * before 2020 - and the chip is hidden rather than showing "Joined unknown".
 */
internal fun formatJoinYear(createdAt: Int): String =
    Instant.ofEpochSecond(createdAt.toLong())
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy", Locale.ROOT))

/**
 * A Unix timestamp as "5 hours ago".
 *
 * `Locale.ROOT` on the format, so a device set to a locale that writes digits or
 * month names differently does not render a chip in a script the surrounding text
 * is not in.
 */
internal fun formatRelativeSeconds(timestamp: Int, now: Instant = Instant.now()): String {
    val seconds = now.epochSecond - timestamp
    if (seconds < 0) return "just now"
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val years = days / 365
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 365 -> "${days}d ago"
        else -> "${years}y ago"
    }
}

/**
 * Minutes watched as the largest two units that say something.
 */
internal fun formatWatchTime(minutesWatched: Long): String {
    if (minutesWatched <= 0) return "0h"
    val hours = minutesWatched / 60
    if (hours < 1) return "${minutesWatched}m"
    if (hours < 24) return "${hours}h"
    val days = hours / 24
    val remainderHours = hours % 24
    return if (remainderHours == 0L) "${days}d" else "${days}d ${remainderHours}h"
}

/**
 * AniList's mean score out of 100, as the out-of-ten number people quote.
 *
 * One decimal, because a mean is a mean: 82.3 and 82 describe different lists.
 *
 * `Locale.ROOT` on the format rather than the platform default, so a device set
 * to a locale that writes decimals with a comma does not render "8,2/10" - which
 * is not a score, and reads as a formatting bug on the one card quoting a number
 * somebody chose.
 */
internal fun formatMeanScore(meanScore: Double): String {
    val outOfTen = meanScore / 10.0
    return if (outOfTen % 1.0 == 0.0) {
        "${outOfTen.toInt()}/10"
    } else {
        String.format(Locale.ROOT, "%.1f/10", outOfTen)
    }
}
