package com.anisequel.data.model

enum class SequelSortOption(val displayName: String) {
    RELEASE_DATE_DESC("Newest First"),
    RELEASE_DATE_ASC("Oldest First"),
    TITLE_ASC("Title (A-Z)"),
    POPULARITY("Most Popular"),
    SCORE("Highest Rated")
}

/**
 * The airing states a sequel can be narrowed to.
 *
 * ## Why Hiatus and Cancelled are here
 *
 * AniList has five media statuses, and this enum had three of them plus `ALL`.
 * The two missing ones were not invisible - `SequelStatusChip` has drawn "Hiatus"
 * and "Cancelled" chips, with their own colours and icons, since it existed - so
 * a reader could see a cancelled entry on a card and had no way to ask for the
 * cancelled ones. A status the UI can display and the filter cannot select is a
 * dead end: the only route to those entries was scrolling the unfiltered list.
 *
 * The order is the order the filter sheet draws, so Upcoming stays last and the
 * two in-between states sit beside `RELEASING`: an entry is on hiatus *having*
 * been airing, and cancelled *having* been announced.
 */
enum class StatusFilter(val displayName: String) {
    ALL("All Statuses"),
    FINISHED("Finished Airing"),
    RELEASING("Currently Airing"),
    HIATUS("On Hiatus"),
    CANCELLED("Cancelled"),
    NOT_YET_RELEASED("Upcoming / Unreleased")
}

/**
 * Every format chip the filter surfaces draw, in the order they draw them,
 * paired with AniList's raw enum key.
 *
 * One list because the sheet and the quick filter bar name the same choices.
 * The sheet kept its own copy and the quick bar kept a two-entry copy, so a
 * list narrowed to OVA, ONA or Specials said only "Filtered" underneath the
 * segmented bar - the one row whose job is to say *which* filter is active
 * could not name half of them.
 *
 * `TV short` is here because it was the one format no chip could reach: the
 * chips held `TV` and `SPECIAL`, and a `TV_SHORT` equals neither, so a TV
 * special stayed in the unfiltered list and disappeared from every filtered
 * one. [FilterCriteria.selectedFormat] compares against these raw keys, not
 * against the readable label the card prints.
 */
val filterFormatOptions: List<Pair<String?, String>> = listOf(
    null to "All",
    "TV" to "TV series",
    "MOVIE" to "Movies",
    "TV_SHORT" to "TV short",
    "OVA" to "OVA",
    "ONA" to "ONA",
    "SPECIAL" to "Special"
)

/** The readable name for a raw format key, or null when the key is unknown. */
fun filterFormatLabel(formatKey: String?): String? =
    filterFormatOptions.firstOrNull { (key, _) -> key == formatKey }?.second

/**
 * AniList's fixed genre set, as the advanced filter offers it.
 *
 * Copied rather than fetched with `GenreCollection`: the list is a closed enum
 * on AniList's side, and asking for it would spend a request every time the
 * sheet opened to redraw chips whose labels never change. Matched
 * case-insensitively by [FilterCriteria.selectedGenres], because a genre
 * reached through an older cached response could differ in case.
 */
val animeGenreOptions: List<String> = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
    "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological",
    "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller"
)

/**
 * The minimum-rating chips, paired with the score they require in AniList's
 * 0-100 average scale. `null` is "Any".
 *
 * A threshold is a *lower bound*, not a band: "70+" keeps everything at 70 or
 * above. Titles AniList has not rated have no score to prove they clear the
 * bar and are dropped while one is set - quietly keeping them would put
 * unrated entries above rated ones the viewer explicitly asked to see.
 */
val filterScoreOptions: List<Pair<Int?, String>> = listOf(
    null to "Any",
    60 to "60+",
    70 to "70+",
    80 to "80+",
    90 to "90+"
)

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
    val parentCompletedThisYear: Boolean = false,

    /**
     * Finds sequels to anime the viewer is currently watching, or sequels
     * the viewer has already marked as currently watching in their list.
     * Off by default so the sequel feed defaults to missed continuations.
     */
    val includeCurrentlyWatching: Boolean = false,

    /**
     * Finds sequels that are already present in the viewer's anime list
     * (Completed, Paused, Dropped, etc.). Off by default to keep the focus
     * on gaps and unwatched continuations.
     */
    val includeInList: Boolean = false,

    /**
     * Genres the sequel must carry at least one of. Empty means every genre.
     *
     * Matched with OR across the set: selecting Action and Comedy asks for
     * titles that are *either*, which is how every streaming catalogue's genre
     * checkboxes behave. Requiring all of them would make two selections
     * almost always empty.
     */
    val selectedGenres: Set<String> = emptySet(),

    /**
     * The lowest AniList average score (0-100) a sequel may have, or null for
     * no minimum. See [filterScoreOptions] for why unrated entries drop out
     * while this is set.
     */
    val minScore: Int? = null
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
            parentCompletedThisYear ||
            includeCurrentlyWatching ||
            includeInList ||
            selectedGenres.isNotEmpty() ||
            minScore != null
}
