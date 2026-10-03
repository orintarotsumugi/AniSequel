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
    val hiddenMediaIds: Set<Int> = emptySet()
)
