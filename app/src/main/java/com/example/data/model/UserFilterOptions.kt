package com.example.data.model

enum class SequelSortOption(val displayName: String) {
    RELEASE_DATE_DESC("Newest First"),
    RELEASE_DATE_ASC("Oldest First"),
    TITLE_ASC("Title (A-Z)"),
    POPULARITY("Most Popular"),
    SCORE("Highest Rated")
}

enum class StatusFilter(val displayName: String) {
    ALL("All Statuses"),
    FINISHED("Finished Airing"),
    RELEASING("Currently Airing"),
    NOT_YET_RELEASED("Upcoming / Unreleased")
}

data class FilterCriteria(
    val searchQuery: String = "",
    val sortOption: SequelSortOption = SequelSortOption.RELEASE_DATE_DESC,
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val includeUnreleased: Boolean = true,
    val selectedFormat: String? = null,
    val hideAlreadyPlanned: Boolean = true,
    /**
     * Which kinds of franchise gap to surface. Defaults to sequels only, which
     * is what the app has always meant; prequels, side stories and spin-offs are
     * opt-in because they widen the result set considerably.
     */
    val includedRelations: Set<RelationKind> = setOf(RelationKind.SEQUEL),

    /**
     * AniList media ids the user has chosen not to be reminded about again.
     *
     * Distinct from every other field here, because those are *how* to narrow
     * the list and this is *which entries to remove from it*. Nothing else in
     * this class can express "I do not want to ever see this one again" - a
     * search query is forgotten when you type something else, and a status or
     * format filter is a property of the anime rather than a decision about it.
     *
     * Matched on the sequel's id, not the parent's: the same franchise can be
     * reached from several watched entries, and hiding it once has to hide it
     * from all of them.
     */
    val hiddenMediaIds: Set<Int> = emptySet(),

    /**
     * Only sequels whose own release falls in the current calendar year.
     *
     * Derived from the sequel's `startDate`, not from the airing season: a
     * show that starts in December and runs into spring is "this year" by the
     * date the user can point at.
     */
    val sequelReleasedThisYear: Boolean = false,

    /**
     * Only sequels to something the *viewer* marked completed this year.
     *
     * Measured from the entry's `completedAt`, which AniList records when a
     * user sets a status of Completed - not from when the show finished
     * airing. An old show picked up in January counts; last year's season
     * finished this week does not.
     */
    val parentCompletedThisYear: Boolean = false
) {
    /**
     * True when any criterion here is actively narrowing the result list.
     *
     * The one definition, used for the filter badge on the top bar, for the
     * "Filtered" row under the quick filters, and for deciding whether the
     * empty state is a "no results" or a "nothing here to begin with". Three
     * copies of this expression had drifted apart: the quick filter's version
     * forgot `hideAlreadyPlanned` and the relation set, so a user who hid
     * already-planned entries saw an unfiltered-looking dashboard with no way
     * to tell why results were missing.
     *
     * [hiddenMediaIds] is deliberately excluded. Hiding an anime is a standing
     * decision about that anime rather than a view of the list, so it must not
     * light a "filters active" badge that never goes away.
     */
    val isNarrowing: Boolean
        get() = statusFilter != StatusFilter.ALL ||
            selectedFormat != null ||
            searchQuery.isNotBlank() ||
            !includeUnreleased ||
            !hideAlreadyPlanned ||
            includedRelations != setOf(RelationKind.SEQUEL) ||
            sequelReleasedThisYear ||
            parentCompletedThisYear
}
