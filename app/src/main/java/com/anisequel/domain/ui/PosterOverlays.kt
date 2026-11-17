package com.anisequel.domain.ui

/**
 * What btttr.cc composites onto a BetterPosters poster.
 *
 * btttr renders a finished poster: the artwork, the show's name set into it, and -
 * depending on the URL it is asked for - a score, a genre line and a trend label
 * such as "New Series" or "Limited Series". Those are part of the picture, so the
 * app cannot hide them the way it hides its own caption; it has to ask for a
 * different poster. The service names the variants in the path: `poster` carries
 * the genre and the score, `poster-r` only the score, `poster-g` only the genre and
 * `poster-n` neither, and a `?tag=none` query drops the trend label. This value is
 * those three switches, and [pathCode] is the only place that spelling lives.
 *
 * All three default to **off**, which is the clean poster: artwork and the show's
 * name, nothing else. btttr's own default is the full poster, but the name is the
 * only thing it always prints and the rest is noise a reader did not ask for in a
 * grid - so the app starts from "poster only" and lets the three switches add the
 * score, the genre and the trend strip back.
 *
 * The name cannot be switched off: it is part of the rendered artwork in every
 * btttr variant, including `poster-n`. The app's own caption is the control for
 * that question, and it already has its own switch under the artwork.
 */
data class PosterOverlays(
    /** btttr's star score on the artwork. Off asks for the `g`/`n` variants. */
    val showRating: Boolean = false,
    /** The genre line btttr prints at the bottom of the poster. */
    val showGenre: Boolean = false,
    /** The trend label ("New Series", "Limited Series") above the artwork. */
    val showTrendTags: Boolean = false
) {
    /**
     * The letters btttr's path carries for the genre/score pair.
     *
     * Empty is the full poster - the URL the app already used - so a reader who
     * leaves every switch alone keeps the exact request 2.3.0 made rather than a
     * variant that happens to render the same.
     */
    val pathCode: String
        get() = when {
            showGenre && showRating -> ""
            !showGenre && showRating -> "r"
            showGenre && !showRating -> "g"
            else -> "n"
        }

    companion object {
        /** The clean poster: the artwork and its name, with no score, genre or trend. */
        val DEFAULT = PosterOverlays()
    }
}
