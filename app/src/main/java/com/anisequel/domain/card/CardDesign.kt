package com.anisequel.domain.card

/**
 * The three shapes a cover can be drawn at.
 *
 * Size is deliberately **not** a column count. The Library view already answers how
 * many covers share a row - "Big cards" is two across, "Cover wall" three - and a
 * card size that also nudged that count silently overrode the view the reader had
 * just chosen: picking Big cards and Compact together produced three across, which
 * is neither. (That is what `columnBias` did before 2.3.2, and why it is gone.)
 *
 * What a reader feels when a card grows is the artwork, so [coverBias] stretches the
 * poster's own proportions: a compact card is a little wider and shorter, a roomy one
 * taller. Every grid keeps the columns its own view asked for.
 */
enum class CardSize(
    val storageValue: String,
    val displayName: String,
    /** What the reader sees change, in the words they would use for it. */
    val blurb: String,
    /** Multiplier on a poster's width/height ratio; below 1 is a taller picture. */
    val coverBias: Float
) {
    COMPACT(
        storageValue = "compact",
        displayName = "Compact",
        blurb = "A wider, shorter cover - the artwork crops to its middle band.",
        coverBias = 1.08f
    ),
    STANDARD(
        storageValue = "standard",
        displayName = "Standard",
        blurb = "The shipped cover's proportions.",
        coverBias = 1f
    ),
    ROOMY(
        storageValue = "roomy",
        displayName = "Roomy",
        blurb = "A taller cover, so the poster shows more of its own art.",
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
     * Whether the card prints the title under the artwork.
     *
     * **On by default.** 2.3.1 shipped this off while its own release notes promised
     * "both are on by default, so no card changes until you ask" - so every grid
     * that used to draw a caption silently stopped, and a reader who never opened
     * Settings had no way to know a switch existed. On is also the behaviour every
     * build before the switch had. Turn it off when the art already carries the name
     * (BetterPosters sets it on the image) and a second line reads as a duplicate.
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
