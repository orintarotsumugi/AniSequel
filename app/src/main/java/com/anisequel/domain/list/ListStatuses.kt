package com.anisequel.domain.list

import java.util.Locale

/**
 * AniList's `MediaListStatus`, in one place.
 *
 * Pure Kotlin - no Android imports - because four surfaces read it: the entry
 * editor's picker, the Library's filter chips, the Stats tab's tally labels and
 * the tier board's filter sheet. Each of those used to carry its own copy of the
 * same six tokens with its own spelling of the same words, and the copies drifted:
 * the editor and the chips said "Paused" while the insights tally said "On-Hold",
 * and a reader who had just set a status in one place could not find it under that
 * name in another. The tokens themselves are the API's (`MediaListStatus` has
 * exactly these six values and no more), so anything that looks like a seventh
 * status is a *label* question, not a data question.
 *
 * Both labels are carried per option because AniList models one enum for anime and
 * manga and nobody says they are watching a book. Callers pick with
 * [labelFor]; there is no third spelling to keep in step.
 */
data class ListStatusOption(
    val apiValue: String,
    val animeLabel: String,
    val mangaLabel: String
) {
    fun labelFor(isManga: Boolean): String = if (isManga) mangaLabel else animeLabel
}

object ListStatuses {
    const val CURRENT = "CURRENT"
    const val REPEATING = "REPEATING"
    const val COMPLETED = "COMPLETED"
    const val PLANNING = "PLANNING"
    const val PAUSED = "PAUSED"
    const val DROPPED = "DROPPED"

    /**
     * The six, in the order a person reaches for them: what is happening now,
     * what happened again, what ended, what has not started, and the two ways a
     * title stopped.
     *
     * `PLANNING` sits after `COMPLETED` rather than second because the editor's
     * picker is read top-down by somebody deciding what a show *is to them*, and
     * "Planning" competes with nothing else on the list.
     */
    val options: List<ListStatusOption> = listOf(
        ListStatusOption(CURRENT, "Watching", "Reading"),
        ListStatusOption(REPEATING, "Rewatching", "Rereading"),
        ListStatusOption(COMPLETED, "Completed", "Completed"),
        ListStatusOption(PLANNING, "Plan to Watch", "Plan to Read"),
        ListStatusOption(PAUSED, "On-Hold", "On-Hold"),
        ListStatusOption(DROPPED, "Dropped", "Dropped")
    )

    /** Every token the API can answer with, upper-cased. */
    val tokens: Set<String> = options.map { it.apiValue }.toSet()

    private val byToken: Map<String, ListStatusOption> =
        options.associateBy { it.apiValue.lowercase(Locale.ROOT) }

    /**
     * Tokens that are not in [MediaListStatus](https://docs.anilist.co/reference/enum/medialiststatus)
     * but have turned up in real payloads: API v1 spelled three of these
     * differently and had a seventh status, `CLEARED`, that v2 folded into
     * `DROPPED`. An entry cached under an old name, a scanlation-era export, or a
     * fixture lifted from v1 docs all reach the UI with these.
     *
     * They are aliases rather than a validation reject because the app displays
     * other people's data it cannot rewrite: dropping a row whose token is one
     * generation old is a lie about the account, and capitalising the raw token
     * into a label ("Somethingnew") is the bug this replaced.
     */
    private val aliases: Map<String, String> = mapOf(
        "WATCHING" to CURRENT,
        "READING" to CURRENT,
        "FINISHED" to COMPLETED,
        "ON_HOLD" to PAUSED,
        "HOLD" to PAUSED,
        "PLAN_TO_WATCH" to PLANNING,
        "PLAN_TO_READ" to PLANNING,
        "CLEARED" to DROPPED
    )

    /** The canonical token for anything this build has seen, or null. */
    fun normalize(status: String?): String? {
        if (status.isNullOrBlank()) return null
        val upper = status.trim().uppercase(Locale.ROOT)
        return if (upper in tokens) upper else aliases[upper]
    }

    /**
     * The label for a status string as it came off the wire.
     *
     * An unknown token is returned in words rather than filed under its nearest
     * neighbour: AniList can add a status without telling this build, and the row
     * still has one. Underscores are word breaks - `SOMETHING_NEW` is "Something
     * new", not "Something_new", which is a database leaking into a label in place
     * of the one place a person would ever learn the status exists.
     */
    fun label(status: String?, isManga: Boolean): String {
        if (status.isNullOrBlank()) return "Not on your list"
        val token = normalize(status)
            ?: return status.trim().lowercase(Locale.ROOT)
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
        return byToken.getValue(token.lowercase(Locale.ROOT)).labelFor(isManga)
    }
}
