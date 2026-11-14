package com.anisequel.domain.ui

/**
 * Which presentation the app draws.
 *
 * Two layouts rather than a rewrite: [CLASSIC] is the app as it shipped, and
 * [NOVA] is the newer shelf-and-rails layout. The choice is a preference, so a
 * reader who does not like the new one loses nothing - and, more importantly, a
 * screen the new look does not cover yet keeps working exactly as it did.
 *
 * Stored by [storageValue] rather than by ordinal. A reordering of these entries
 * would otherwise silently repaint every install, and a value written by a build
 * that knew a layout this one does not falls back to the shipped one instead of
 * crashing.
 */
enum class AppLayout(val storageValue: String, val displayName: String) {
    CLASSIC("classic", "Classic"),
    NOVA("nova", "New look");

    companion object {
        /** The layout the app opens in, so an update never moves a reader's pages. */
        val DEFAULT = CLASSIC

        fun fromStorage(value: String?): AppLayout =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * How the new Home draws its missed sequels.
 *
 * Two answers to the same question, because the two readers are real: [ROWS]
 * fits nine or ten titles on a screen and is the layout for working through a
 * backlog, while [CARDS] gives each recommendation a poster large enough to
 * recognise at a glance and is the layout for browsing what to start next.
 *
 * The choice lives on the enum rather than in the composable so the Settings
 * preview and the real list cannot describe different things - the same reason
 * `LibraryViewMode` carries its own column count.
 */
enum class HomeSequelStyle(
    val storageValue: String,
    val displayName: String,
    val blurb: String,
    /** Whether a row of this style draws the artwork as a full poster. */
    val showsPoster: Boolean
) {
    ROWS(
        storageValue = "rows",
        displayName = "Rows",
        blurb = "A thumbnail, the title and the add button - more titles per screen.",
        showsPoster = false
    ),
    CARDS(
        storageValue = "cards",
        displayName = "Cards",
        blurb = "A large cover with the details underneath - one title at a time.",
        showsPoster = true
    ),
    BANNER(
        storageValue = "banner",
        displayName = "Banners",
        blurb = "Wide artwork with a compact action strip for quick scanning.",
        showsPoster = true
    ),
    SHELF(
        storageValue = "shelf",
        displayName = "Shelf",
        blurb = "A poster shelf for browsing several missed sequels at once.",
        showsPoster = true
    );

    companion object {
        val DEFAULT = ROWS

        fun fromStorage(value: String?): HomeSequelStyle =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * How dense the new Library's list is.
 *
 * The same entries, drawn three ways for three jobs: [GRID] for browsing covers,
 * [COMPACT] for finding something in a long list quickly, and [LIST] for reading
 * progress and status in one column. The column count lives on the enum so the
 * grid and the Settings preview cannot disagree about what "compact" means.
 */
enum class LibraryViewMode(
    val storageValue: String,
    val displayName: String,
    val blurb: String,
    val columns: Int
) {
    GRID(
        storageValue = "grid",
        displayName = "Poster grid",
        blurb = "Three covers across with room for the caption.",
        columns = 3
    ),
    COMPACT(
        storageValue = "compact",
        displayName = "Compact grid",
        blurb = "Four covers across - more of the list per screen.",
        columns = 4
    ),
    LIST(
        storageValue = "list",
        displayName = "List",
        blurb = "One title per row, with progress, status and score.",
        columns = 1
    ),
    /**
     * A list with a heading per status instead of one flat run of rows.
     *
     * The other modes answer "what is on my list"; this one answers "where is
     * it" without a filter. AniList's own list page draws the same grouping, and
     * a reader with a long backlog otherwise has to apply a status filter six
     * times to see the same thing. [columns] is one because a heading is the
     * layout: the grid arm is never used for this mode.
     */
    GROUPED(
        storageValue = "grouped",
        displayName = "Grouped",
        blurb = "A section per status, with the count for each one.",
        columns = 1
    ),
    /**
     * A dense table, the shape AniList's website and AniTrend use for triage.
     *
     * The row carries the four things a reader scanning a long backlog compares -
     * cover, title, status/progress and score - in columns that line up down the
     * page. It is deliberately not another version of [LIST]: a list is for
     * reading a title, a table is for comparing twenty of them at once.
     */
    TABLE(
        storageValue = "table",
        displayName = "Table",
        blurb = "Dense rows with status, progress and score lined up in columns.",
        columns = 1
    ),
    /**
     * A caption-free cover wall.
     *
     * The other five modes all spend vertical space on a name or a progress line.
     * This one spends it on artwork and lets the cover's own typography do the
     * identifying, the way AniList's grid and the reference clients' "covers" view
     * do. Three across rather than [SHELF]'s five: the point is a large picture,
     * not the most posters on a phone.
     */
    COVERS(
        storageValue = "covers",
        displayName = "Cover wall",
        blurb = "Large caption-free covers with only the progress badge.",
        columns = 3
    ),
    CARDS(
        storageValue = "cards",
        displayName = "Big cards",
        blurb = "Two larger posters with status and progress easy to scan.",
        columns = 2
    ),
    SHELF(
        storageValue = "shelf",
        displayName = "Shelf",
        blurb = "Five compact covers across for fast visual browsing.",
        columns = 5
    );

    companion object {
        val DEFAULT = GRID

        fun fromStorage(value: String?): LibraryViewMode =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
