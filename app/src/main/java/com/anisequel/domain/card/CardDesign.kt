package com.anisequel.domain.card

/**
 * The three sizes a card can be drawn at, expressed as how many covers share a row.
 *
 * Size is not a multiplier, because a grid cannot honour one: the columns decide a
 * card's width, so "20% bigger" is not a width the page can draw. What the reader
 * actually feels when a card gets bigger is that fewer of them fit, so the setting
 * asks the same question the view mode does - how many across - and answers it one
 * step either side of the mode's own answer. [coverBias] then stretches the artwork
 * with it, so a two-across card is not the same picture with a wider gap.
 *
 * [columnBias] is applied by [columnsFor] rather than stored on the mode, because a
 * reader who picks a bigger card wants it bigger in every grid - the library, their
 * profile list and any poster grid - and a per-mode table would have to be edited
 * in three places to say so.
 */
enum class CardSize(
    val storageValue: String,
    val displayName: String,
    /** What the reader sees change, in the words they would use for it. */
    val blurb: String,
    /** Columns added to the view mode's own count: positive means smaller cards. */
    val columnBias: Int,
    /** Multiplier on a poster's width/height ratio; below 1 is a taller picture. */
    val coverBias: Float
) {
    COMPACT(
        storageValue = "compact",
        displayName = "Compact",
        blurb = "One more to the row - the most titles per screen.",
        columnBias = 1,
        coverBias = 1.08f
    ),
    STANDARD(
        storageValue = "standard",
        displayName = "Standard",
        blurb = "The shipped size: three across on a phone.",
        columnBias = 0,
        coverBias = 1f
    ),
    ROOMY(
        storageValue = "roomy",
        displayName = "Roomy",
        blurb = "One fewer to the row, so a long name has space to read.",
        columnBias = -1,
        coverBias = 0.9f
    );

    companion object {
        val DEFAULT = STANDARD

        fun fromStorage(value: String?): CardSize =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * Everything the reader chose about how a card looks, in one value.
 *
 * Held together rather than as five DataStore reads because every one of these
 * changes the same composable, and a card drawn from a half-applied set - new title
 * size, old rating size - is a frame nobody asked for. The page that edits them
 * keeps a draft and writes once, so this is also the unit a write is made of.
 *
 * [titleScale] and [ratingScale] multiply the card's own type, not the app's: the
 * app-wide text size on the Type page scales every word, and this scales only the
 * two numbers a card is judged by. That is why they are stored as factors and not as
 * sp values - a factor survives a change to the app's font scale, an sp does not.
 */
data class CardDesign(
    val titleScale: Float = DEFAULT_TITLE_SCALE,
    val ratingScale: Float = DEFAULT_RATING_SCALE,
    val size: CardSize = CardSize.DEFAULT,
    /** Names in a heavier weight; the shipped card is semibold. */
    val boldTitles: Boolean = false,
    /**
     * Whether a name too long for its line scrolls. The speed itself lives on the
     * Type page, with the rest of the app's moving text - this is the switch that
     * says cards take part, because a reader who turned it off did so after seeing a
     * card scroll, and the sample they switched off was not a settings page.
     */
    val scrollTitles: Boolean = true,
    /**
     * Whether the card prints the name itself.
     *
     * The BetterPosters artwork is a finished poster: the title is part of the
     * picture, set in the film's own type over the art. Drawing the card's caption
     * under it repeats the name twice in two typefaces, which is what the reader
     * who asked for "the things the poster already has" was looking at. Both
     * switches exist so that choosing that art does not force the caption off - a
     * reader on the AniList cover still wants a name.
     */
    val showTitles: Boolean = true,
    /** Whether the card prints the score, for the same reason [showTitles] exists. */
    val showScores: Boolean = true
) {
    /**
     * Brought back inside the sliders' span.
     *
     * A value from an older build, a restore or a hand-edited file is still a value
     * the app has to draw: a title at 4x would push every caption off the card, so it
     * is clamped on the way out of the repository and again where it is used.
     */
    fun clamped(): CardDesign = copy(
        titleScale = titleScale.coerceIn(MIN_TITLE_SCALE, MAX_TITLE_SCALE),
        ratingScale = ratingScale.coerceIn(MIN_RATING_SCALE, MAX_RATING_SCALE)
    )

    /**
     * Whether a card at this design prints nothing but its artwork.
     *
     * The two poster switches are one decision made in two places, and a preview that
     * only knew one of them would draw a card that cannot exist. Kept on the value
     * rather than recomputed at each call site so "is this the bare poster?" has one
     * answer.
     */
    val printsText: Boolean get() = showTitles || showScores

    companion object {
        /**
         * 0.85 is the smallest title that still reads as a name at three across; 1.45
         * fills the caption of a two-across card without clipping its first word.
         */
        const val MIN_TITLE_SCALE = 0.85f
        const val MAX_TITLE_SCALE = 1.45f
        const val DEFAULT_TITLE_SCALE = 1f

        /**
         * The rating's span is narrower than the title's: a score is two or three
         * glyphs in a pill, and 1.4x is already a badge that nearly fills the cover's
         * corner while 0.8x is the smallest a percentage stays legible on a phone.
         */
        const val MIN_RATING_SCALE = 0.8f
        const val MAX_RATING_SCALE = 1.4f
        const val DEFAULT_RATING_SCALE = 1f

        val DEFAULT = CardDesign()
    }
}

/**
 * The columns a view mode draws with [size] applied.
 *
 * Modes that are one wide - List, Grouped, Table - are left alone: a row is not a
 * card, and turning a table into two columns would not be a bigger card, it would be
 * a broken table. Everything else is nudged by one and kept inside the range a cover
 * can still show a face in, which is the same floor
 * `com.anisequel.ui.screens.library.libraryColumns` enforces.
 */
fun columnsFor(baseColumns: Int, size: CardSize): Int {
    if (baseColumns <= 1) return baseColumns
    return (baseColumns + size.columnBias).coerceIn(1, 6)
}
