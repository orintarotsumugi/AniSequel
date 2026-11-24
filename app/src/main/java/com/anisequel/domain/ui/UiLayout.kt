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
 * Which title page the app draws.
 *
 * [CLASSIC] is the page as it shipped: one card per section under a collapsing
 * hero. [NOVA] is the redesigned page: full-bleed key art, a poster lockup, one
 * stats strip and the reading sections laid over the same artwork.
 *
 * A separate switch from [AppLayout] on purpose. The two are different questions -
 * the Home/Library look and the title page - and a reader may want one and not the
 * other. Tying them together would take that choice away, and a screen the new
 * title page does not cover would be forced into it by a Home-layout tap.
 *
 * [NOVA] is the default now. Classic remains a real, supported page rather than a
 * fallback: a reader who prefers the original cards can choose it and the app
 * remembers that choice. The previous default was Classic because the redesign was
 * new; every feature it shares with Classic is in place, so keeping existing
 * installs on the older shell only makes the first thing they see the one the app
 * is no longer designed around.
 *
 * Stored by [storageValue] for the same reason the other enums are: a reordering
 * must not repaint every install, and a value written by a newer build falls back
 * to Nova rather than crashing.
 */
enum class InfoPageStyle(
    val storageValue: String,
    val displayName: String,
    val blurb: String
) {
    CLASSIC(
        storageValue = "classic",
        displayName = "Classic",
        blurb = "Cards under the collapsing hero - the title page as it shipped."
    ),
    NOVA(
        storageValue = "nova",
        displayName = "Nova",
        blurb = "Full-bleed key art, a poster lockup and a stats strip, " +
            "with the synopsis, details and categories over the artwork."
    );

    companion object {
        /**
         * The page every install opens in unless it has chosen otherwise.
         *
         * This deliberately moved from Classic to Nova: the redesign is the page
         * the app is built around now, and an install that has never answered the
         * question should see it. An explicit "classic" still wins, so this is a
         * default rather than a migration that rewrites anyone's choice.
         */
        val DEFAULT = NOVA

        fun fromStorage(value: String?): InfoPageStyle =
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
    ),
    TICKET(
        storageValue = "ticket",
        displayName = "Tickets",
        blurb = "An admission stub: the artwork on one end, a perforated tear-off on the other.",
        showsPoster = false
    ),
    POLAROID(
        storageValue = "polaroid",
        displayName = "Polaroids",
        blurb = "An instant print with the title written in the white margin underneath.",
        showsPoster = true
    ),
    /**
     * A stack of prints with the sequel on the card on top.
     *
     * Every other style draws one surface; this one draws a pile, so the feed reads
     * as a hand of recommendation cards rather than as a list of rows. The two
     * sheets behind the top card are the whole idea and cost nothing but two
     * offset shapes - which is why this is a style and not a new page.
     */
    DECK(
        storageValue = "deck",
        displayName = "Deck",
        blurb = "A stack of prints: two sheets peek out behind the card on top.",
        showsPoster = true
    ),
    /**
     * One frame of a film strip, sprockets and all.
     *
     * A row is the densest style and a card the tallest; this one is the one that
     * says the sequels are a *reel* - a wide frame with the perforations down both
     * sides, so the artwork is the thing and the card still fits four on a screen.
     */
    FILMSTRIP(
        storageValue = "filmstrip",
        displayName = "Film strip",
        blurb = "One frame of film: sprocket holes down the sides and the artwork wide.",
        showsPoster = true
    ),
    /**
     * One wide still per sequel, with its actions on the card rather than on the art.
     *
     * The feed already has a dense row, a poster card, an overlaid banner, a shelf,
     * a stub, a print, a pile and a film frame. What it did not have is the shape a
     * featured row asks for: a single landscape image big enough to read, with the
     * controls in their own strip below it instead of over the picture. That is this
     * one - the artwork is the thing being shown, and nothing is drawn on top of it
     * except the title the image has to carry.
     *
     * Shares the reader's own `HomeSequelStyle` storage key, so it is a chosen style
     * like the other eight rather than a second Home.
     */
    SPOTLIGHT(
        storageValue = "spotlight",
        displayName = "Spotlight",
        blurb = "One wide still per sequel, with the title on the art and the actions underneath.",
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
    ),
    /**
     * Two across, with every third cover spanning the whole width.
     *
     * The flat poster grid makes every title the same size, which is right for
     * judging a list and wrong for looking at it: nothing in a wall of equal covers
     * says where to start. The wide tile is the anchor, and the pattern repeats so
     * the page still scrolls predictably. It is a *span* mode rather than a new
     * drawing - the same cell, a different share of the row - which is why it costs
     * one branch in each grid instead of a third set of cards.
     */
    MOSAIC(
        storageValue = "mosaic",
        displayName = "Mosaic",
        blurb = "Two across, with every third cover a full-width banner.",
        columns = 2
    ),
    /**
     * Grouped is the list with a heading per status; this is the grid with one.
     *
     * The two answer different questions about the same grouping. A reader scanning
     * a long backlog wants rows (Grouped); a reader browsing covers wants the wall
     * to be sorted into the same six piles rather than one flat run of posters.
     * `columns` is three, the poster grid's own count, because the caption has to
     * keep its room under a cover.
     */
    GROUPED_COVERS(
        storageValue = "grouped_covers",
        displayName = "Grouped covers",
        blurb = "A section per status, each drawn as a poster grid.",
        columns = 3
    ),
    /**
     * One title per row, drawn as a wide still rather than a poster.
     *
     * The grid modes answer "how many covers share a row"; this one answers the
     * opposite question - what a single title looks like when it is the row. The
     * cover is a landscape crop across the whole page, the caption underneath keeps
     * the name, status and progress the Big cards caption carries, and the column
     * count is one so the mode's own arrangement is the answer rather than a second
     * density of the grid.
     *
     * It is a grid mode, not a row mode: it still prints the poster, the progress
     * pill and a caption, and it is drawn by the same cell as every other poster - a
     * wide cover, one per line. `columns = 1` is the arrangement the grid reads, and
     * [usesWideStill] is the proportion the cell and both previews read, so the page
     * and its miniature cannot describe different shapes.
     */
    SPOTLIGHT(
        storageValue = "spotlight",
        displayName = "Spotlight",
        blurb = "One wide still per title, with the name, status and progress underneath.",
        columns = 1
    );

    /**
     * Whether a cover in this mode prints a caption under the artwork.
     *
     * ## Why this is on the enum
     *
     * It was written three times - once in the Library grid, once in the layout
     * preview and once in the card-design preview - and the three had already
     * drifted: the previews printed a caption under a Shelf cover, and the layout
     * preview drew the Compact grid caption-free while the Library prints its
     * names. A preview that promises a page the app does not draw is worse than no
     * preview, because the reader chooses the mode by looking at it. The grid and
     * both previews read this one answer now.
     */
    val showsCaption: Boolean
        get() = this != SHELF && this != COVERS

    /**
     * Whether the mode is a run of rows rather than a grid of posters.
     *
     * The three row modes are drawn by the Library's own list row, so the
     * card-design preview has to draw rows for them too - a poster grid there is a
     * picture of a page the mode never produces.
     */
    val drawsRows: Boolean
        get() = this == LIST || this == GROUPED || this == TABLE

    /**
     * Whether every third cell is a full-width banner, as [MOSAIC] arranges it.
     *
     * The card-design preview needs it for the same reason the Library does: a
     * flat two-up grid under the Mosaic name is the mode's own choice being
     * previewed as its neighbour.
     */
    val hasHeroCells: Boolean
        get() = this == MOSAIC

    /**
     * Whether the poster grid is sorted under status headings.
     *
     * Only [GROUPED_COVERS] among the grid modes; [GROUPED] is sorted the same way
     * but draws rows, which [drawsRows] already claims.
     */
    val hasStatusHeadings: Boolean
        get() = this == GROUPED_COVERS

    /**
     * Whether the poster is a landscape still across the page, as [SPOTLIGHT] draws it.
     *
     * The grid and the two Settings previews need the same answer for the same
     * reason [hasHeroCells] exists: the mode's proportion is part of its arrangement,
     * and a preview that drew a 2:3 poster under a one-column name would be showing a
     * page the Library never produces.
     */
    val usesWideStill: Boolean
        get() = this == SPOTLIGHT

    companion object {
        // AniSequel 2.4 opens the Library on expressive two-column poster cards.
        // An explicit stored choice still wins, so existing readers keep their layout.
        val DEFAULT = CARDS

        fun fromStorage(value: String?): LibraryViewMode =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
