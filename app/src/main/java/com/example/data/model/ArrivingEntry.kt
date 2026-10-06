package com.example.data.model

/**
 * One of the viewer's own list entries that is arriving: airing right now, or
 * announced and not started yet.
 *
 * Deliberately its own shape rather than a [MissedSequel]. A missed sequel is a
 * *recommendation* assembled from a relation edge, with a parent the user
 * watched and a child they have not; an arriving entry is a row that is already
 * on their list, and the only thing it has in common with a card is a poster and
 * a title. Stuffing it into [MissedSequel] would have given it a parentId and a
 * relationType that mean nothing, and every consumer a null to ignore.
 */
data class ArrivingEntry(
    val mediaId: Int,
    val title: String,
    val coverUrl: String?,
    /** AniList's dominant colour for the key visual, for the loading placeholder. */
    val coverColor: String?,
    /** AniList's media status. Only ever `RELEASING` or `NOT_YET_RELEASED`. */
    val status: String,
    val startDate: FuzzyDate?,
    /** `airingAt` in seconds since the epoch, when the next episode is known. */
    val nextAiringAt: Long?,
    val nextEpisodeNumber: Int?,
    val episodes: Int?,
    val progress: Int?
) {
    val isAiring: Boolean
        get() = status.equals("RELEASING", ignoreCase = true)

    /**
     * The entry's page on AniList.
     *
     * Built rather than read: the list query does not ask for `siteUrl` on the
     * parent (one value per entry on the hottest request, for a URL that is
     * derivable from the id it already sends), and there is no in-app detail
     * view for a show that is already on the list - so the card opens the page
     * that has one, the same way the detail sheet's own "View on AniList"
     * action does.
     */
    val siteUrl: String
        get() = "https://anilist.co/anime/$mediaId"

    fun nextAiringCountdown(nowMillis: Long = System.currentTimeMillis()): String? =
        AiringCountdown.format(nextAiringAt, nowMillis)

    /**
     * The announced start, readable: "5 Oct 2026", "Oct 2026" or "2026".
     *
     * AniList only ever knows as much of the date as it knows - a show
     * announced for "Fall 2026" arrives as a year and a month - so each level
     * falls back rather than padding with zeros.
     */
    val startLabel: String?
        get() {
            val date = startDate ?: return null
            val year = date.year ?: return null
            val monthName = date.month?.let { MONTHS.getOrNull(it - 1) }
            val day = date.day
            return when {
                monthName != null && day != null -> "$day $monthName $year"
                monthName != null -> "$monthName $year"
                else -> "$year"
            }
        }

    private companion object {
        val MONTHS = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
    }
}
