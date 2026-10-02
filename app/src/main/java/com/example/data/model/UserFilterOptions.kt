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
    val includedRelations: Set<RelationKind> = setOf(RelationKind.SEQUEL)
)
