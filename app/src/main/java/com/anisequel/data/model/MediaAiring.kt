package com.anisequel.data.model

/**
 * Where the viewer is in a title, and when the next episode arrives.
 *
 * Split out of `MediaNodeDisplay.kt` when the airing line grew its schedule
 * fallback: that file had passed the 900-line ceiling in AGENTS.md, and this
 * section has a different reason to change from the rest of it - the airing
 * line moves with AniList's schedule semantics, not with the labels and rows
 * the rest of the file spells.
 */

/**
 * How many episodes of this entry the viewer has already watched.
 *
 * AniList returns it as `mediaListEntry { progress }` for anything on their
 * list; for an entry the app is offering to add, that is only ever
 * `progress`/`current`, which is what makes a card able to say "you're 4
 * episodes in" rather than only "not on your list".
 */
val MediaNode.watchedEpisodeCount: Int? get() = mediaListEntry?.progress
val MediaNode.airingEpisodeNumber: Int? get() = nextAiringEpisode?.episode

/** AniList's `airingAt`: seconds since the epoch, not milliseconds. */
val MediaNode.airingAtSeconds: Long? get() = nextAiringEpisode?.airingAt

/** How long until the next episode, or null when nothing is scheduled. */
fun MediaNode.airingCountdown(nowMillis: Long = System.currentTimeMillis()): String? =
    airingLineAt(nowMillis)?.countdown

/**
 * The next episode the app can name, and when it airs.
 *
 * ## Why this is not just `nextAiringEpisode`
 *
 * AniList documents [nextAiringEpisode] as the media's next episode, and the API
 * docs promise that *future* airing data is present and accurate. It is still
 * null on some currently airing shows - the weekly slot is known, the episode
 * count is not, and the field never gets filled - which is the "Dragon Ball Super
 * airing, no episode total" complaint: the Info page printed nothing for a show
 * that is visibly airing.
 *
 * [airingSchedule] is the documented fallback, asked for with `notYetAired: true`
 * so it only ever returns slots in the future. The explicit field wins when it is
 * still ahead, because it is the one the API maintains per request; the schedule
 * is consulted when it is absent, or when it points at an episode that has
 * already aired and the node was served from the detail cache.
 */
fun MediaNode.upcomingAiring(nowMillis: Long = System.currentTimeMillis()): UpcomingAiring? {
    val explicit = nextAiringEpisode
    val explicitAt = explicit?.airingAt
    if (explicitAt != null && explicitAt.isStillAhead(nowMillis)) {
        return UpcomingAiring(explicit.episode, explicitAt)
    }
    val scheduled = airingSchedule?.nodes.orEmpty()
        .filter { node -> node.airingAt?.let { it.isStillAhead(nowMillis) } == true }
        .minByOrNull { it.airingAt ?: Long.MAX_VALUE }
        ?: return null
    return UpcomingAiring(scheduled.episode, scheduled.airingAt ?: return null)
}

/**
 * What the Info page says about the next episode, already formatted.
 *
 * One value rather than an episode number and a countdown string fetched
 * separately, because the two must describe the *same* airing: the number is
 * chosen from whichever field is nearest, and a caller that re-derived it from
 * `nextAiringEpisode` would label the schedule's episode with the other one's
 * number.
 */
data class AiringLine(
    /** "12 of 131", "12", or null when the episode number is not known. */
    val episodeDetail: String?,
    /** "in 5h 20m", "in 2d 4h", or "now". */
    val countdown: String
) {
    /** "Episode 12 of 131 airs in 5h 20m" - the whole sentence. */
    val sentence: String
        get() = (episodeDetail?.let { "Episode $it" } ?: "Next episode") + " airs " + countdown

    /**
     * "12 of 131 · in 5h 20m", for the details grid.
     *
     * The grid labels the row "Next episode", so repeating the noun in the value
     * would read "Next episode  Episode 12 ...".
     */
    val specValue: String
        get() = listOfNotNull(episodeDetail, countdown).joinToString(" · ")
}

/** [upcomingAiring], formatted, or null when nothing is scheduled. */
fun MediaNode.airingLineAt(nowMillis: Long = System.currentTimeMillis()): AiringLine? {
    val upcoming = upcomingAiring(nowMillis) ?: return null
    val countdown = AiringCountdown.format(upcoming.airingAtSeconds, nowMillis) ?: return null
    val episode = upcoming.episode
    // Zero is how AniList spells "not announced yet", so it is not a total: the
    // unguarded label printed "Episode 12 of 0" on exactly those titles.
    val total = episodes?.takeIf { it > 0 }
    val detail = when {
        episode == null -> null
        total != null -> "$episode of $total"
        else -> episode.toString()
    }
    return AiringLine(detail, countdown)
}

/** The next episode and when, whichever AniList field carried it. */
data class UpcomingAiring(val episode: Int?, val airingAtSeconds: Long)

/**
 * Within [AIRING_GRACE_MILLIS] of now, rather than strictly after it.
 *
 * The grace is the same one [AiringCountdown] uses to keep saying "now" for a
 * quarter of an hour, so the sentence does not disappear at the instant it
 * reaches zero and reappear as the next week's episode.
 */
private fun Long.isStillAhead(nowMillis: Long): Boolean =
    this * 1000L + AIRING_GRACE_MILLIS >= nowMillis

/**
 * How long an airing stays "the next" one after its time has passed.
 *
 * An episode airs at an instant and is watched over the following minutes; a
 * countdown that vanishes on the second makes the page look like it lost the
 * episode. Fifteen minutes is [AiringCountdown]'s own grace, kept here so both
 * sides of "is this still the next one" agree.
 */
private const val AIRING_GRACE_MILLIS = 15 * 60_000L

/**
 * Whether the progress card states the countdown itself.
 *
 * The progress card owns the sentence for a title the reader is tracking, and the
 * details grid draws it only for the titles it does not. That split is what keeps
 * one clock per page - but the split used to be `hasProgressCard`, which is
 * "a wave can be drawn", and a wave needs the episode *total* as well as the
 * viewer's progress. So a tracked show whose length is unannounced fell into
 * neither card: no wave to draw, and the grid had been told the progress card was
 * speaking. The card now asks whether the sentence will actually be printed.
 */
val MediaNode.progressCardStatesAiring: Boolean
    get() = hasProgressCard && airingLineAt() != null

/**
 * How far through this entry the viewer is, as a 0..1 fraction.
 *
 * Null when there is nothing meaningful to show - no total episode count, or no
 * progress recorded - so callers can skip the bar entirely rather than drawing
 * an empty one.
 */
fun MediaNode.watchFraction(): Float? {
    if (isManga) {
        val total = chapters ?: return null
        if (total <= 0) return null
        val read = watchedEpisodeCount ?: return null
        return (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
    val total = episodes ?: return null
    if (total <= 0) return null
    val watched = watchedEpisodeCount ?: return null
    return (watched.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/** "6 / 12 eps watched" or "14 / 32 chs read" - null when the viewer has not started it. */
fun MediaNode.watchFractionLabel(): String? {
    // `total > 0`, like [watchFraction] and [progressCountLabel]. AniList reports
    // a zero episode count for a show whose length is not announced yet, and the
    // unguarded label printed "3 / 0 eps" for exactly those titles.
    if (isManga) {
        val total = chapters ?: return null
        if (total <= 0) return null
        val read = watchedEpisodeCount ?: return null
        if (read <= 0) return null
        return "$read / $total chs read"
    }
    val total = episodes ?: return null
    if (total <= 0) return null
    val watched = watchedEpisodeCount ?: return null
    if (watched <= 0) return null
    return "$watched / $total eps"
}

