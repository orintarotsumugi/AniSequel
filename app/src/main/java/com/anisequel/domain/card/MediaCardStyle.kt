package com.anisequel.domain.card

/**
 * Where a card puts its title, once the reader has chosen a style.
 *
 * The two answers are the whole difference between the five layouts a reader can
 * pick: the name either sits under the artwork or is laid over its bottom edge, and
 * that single choice is what makes a grid read as posters or as covers.
 */
enum class CardTitlePlacement {
    /** Under the poster, in the card's own body. */
    BELOW_POSTER,

    /** Over the poster's bottom edge, on a gradient scrim. */
    OVERLAY_BOTTOM,

    /** No caption: the artwork is the whole card. */
    HIDDEN
}

/**
 * Where a card puts its rating.
 *
 * Four positions rather than a free-form anchor because these are the four a reader
 * can actually distinguish at the size a grid cell is: under everything, tucked into
 * a corner of the artwork, or stretched across the card as its own band. A corner
 * badge is a fact about the picture; a band is a fact about the card.
 */
enum class CardScorePlacement {
    /** In the body, after the title - a line of context, not a mark on the art. */
    BELOW_TITLE,

    /** Inside the poster's top-start corner. */
    POSTER_TOP_START,

    /** Inside the poster's bottom-end corner, where a rating is read as a verdict on the artwork. */
    POSTER_BOTTOM_END,

    /** A full-width band under the title, which is the loudest of the four. */
    BANNER_BELOW_TITLE
}

/**
 * Which corner of the artwork something sits in.
 *
 * Deliberately not `Alignment`: this is a fact about a layout, and the Android type
 * for it belongs to whoever draws the layout. Keeping the decision here is what lets
 * the placement table - the thing most likely to be got wrong - be tested on a plain
 * JVM.
 */
enum class PosterCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

/**
 * The corner the style gives the rating, or null when the rating is not on the art.
 */
fun scoreCornerOf(style: MediaCardStyle): PosterCorner? = when (style.scorePlacement) {
    CardScorePlacement.POSTER_TOP_START -> PosterCorner.TOP_START
    CardScorePlacement.POSTER_BOTTOM_END -> PosterCorner.BOTTOM_END
    CardScorePlacement.BELOW_TITLE, CardScorePlacement.BANNER_BELOW_TITLE -> null
}

/**
 * The corner a surface's own badge should use, given where the style put the score.
 *
 * The reason this is a function rather than a constant: the Library's progress pill
 * has always lived top-start, and one of the five styles puts the rating there too.
 * Two capsules stacked in one corner is not a layout - one is drawn over the other,
 * and the one that vanishes is whichever the reader was actually looking for. So the
 * score takes the corner the style says, and the surface's own mark moves to the
 * opposite end of the same edge.
 */
fun metaCornerFor(style: MediaCardStyle): PosterCorner =
    when {
        // A style that draws the title over the poster's foot owns that strip. The
        // pill cannot move to bottom-start in that case: it would sit on the title,
        // which is a second collision of the same kind this function exists to
        // prevent. The opposite end of the top edge is free in every style whose
        // score is top-start (MODERN), because no style puts anything at top-end.
        scoreCornerOf(style) == PosterCorner.TOP_START &&
            style.titlePlacement == CardTitlePlacement.OVERLAY_BOTTOM -> PosterCorner.TOP_END
        scoreCornerOf(style) == PosterCorner.TOP_START -> PosterCorner.BOTTOM_START
        else -> PosterCorner.TOP_START
    }

/**
 * The five card layouts the Library grid and the dashboard's sequel rows can draw.
 *
 * ## Why a style is a table of placements rather than five composables
 *
 * The alternative is a `when` over five hand-drawn layouts on each surface, and this
 * app draws media cards in more than one place: the Library grid, the dashboard's
 * rows, and the picker's own preview. Five layouts times three surfaces is fifteen
 * layouts, and the one that goes stale is always the preview - it is the only copy
 * nobody taps, so a reader is shown a card the app does not build.
 *
 * So a style is four facts, and every surface renders those facts. The preview draws
 * the same cell from the same facts, which is what keeps it honest.
 *
 * [storageValue] is what the preferences file keeps, stable across a rename of the
 * display label - the same reason [com.anisequel.domain.profile.ProfileSection] does
 * it this way.
 *
 * [blurb] is the style described rather than named. "Saikou" and "Exotic" are not
 * self-explanatory, and a chip whose meaning has to be guessed is a chip the reader
 * leaves alone.
 */
enum class MediaCardStyle(
    val storageValue: String,
    val displayName: String,
    val blurb: String,
    val titlePlacement: CardTitlePlacement,
    val scorePlacement: CardScorePlacement,
    /** A hairline around the artwork, which is what separates a cover from a tile. */
    val bordered: Boolean,
    /** A shadow tinted from the cover's own colour, rather than a neutral elevation. */
    val glowing: Boolean
) {
    DEFAULT(
        storageValue = "default",
        displayName = "Default",
        blurb = "Poster, then the title and one line of context beneath it.",
        titlePlacement = CardTitlePlacement.BELOW_POSTER,
        scorePlacement = CardScorePlacement.BELOW_TITLE,
        bordered = false,
        glowing = false
    ),
    SAIKOU(
        storageValue = "saikou",
        displayName = "Saikou",
        blurb = "Clean poster with the rating tucked into its bottom corner.",
        titlePlacement = CardTitlePlacement.BELOW_POSTER,
        scorePlacement = CardScorePlacement.POSTER_BOTTOM_END,
        bordered = false,
        glowing = false
    ),
    EXOTIC(
        storageValue = "exotic",
        displayName = "Exotic",
        blurb = "Bordered poster, lit by its own cover colour, with the rating as a band.",
        titlePlacement = CardTitlePlacement.BELOW_POSTER,
        scorePlacement = CardScorePlacement.BANNER_BELOW_TITLE,
        bordered = true,
        glowing = true
    ),
    MINIMAL_EXOTIC(
        storageValue = "minimal_exotic",
        displayName = "Minimal Exotic",
        blurb = "The title laid over the poster's foot, rating band underneath.",
        titlePlacement = CardTitlePlacement.OVERLAY_BOTTOM,
        scorePlacement = CardScorePlacement.BANNER_BELOW_TITLE,
        bordered = true,
        glowing = false
    ),
    MODERN(
        storageValue = "modern",
        displayName = "Modern",
        blurb = "Full cover, title over the bottom, rating on the top corner.",
        titlePlacement = CardTitlePlacement.OVERLAY_BOTTOM,
        scorePlacement = CardScorePlacement.POSTER_TOP_START,
        bordered = false,
        glowing = false
    ),
    COVER_ONLY(
        storageValue = "cover_only",
        displayName = "Cover only",
        blurb = "Bigger artwork with no repeated title underneath.",
        titlePlacement = CardTitlePlacement.HIDDEN,
        scorePlacement = CardScorePlacement.POSTER_BOTTOM_END,
        bordered = false,
        glowing = true
    );

    /**
     * True when the rating belongs to the artwork rather than to the card's body, so
     * the body must not print it a second time.
     *
     * This is the question each surface has to answer about its own existing score
     * line, and answering it from the table rather than from a `when (style)` at each
     * call site is what keeps a sixth style from needing every card file reopened.
     */
    val scoreOnPoster: Boolean
        get() = scorePlacement == CardScorePlacement.POSTER_TOP_START ||
            scorePlacement == CardScorePlacement.POSTER_BOTTOM_END

    companion object {
        /** The layout a build with nothing stored draws. */
        val DEFAULT_STYLE = DEFAULT

        /**
         * The stored value back to a style, falling back to [DEFAULT_STYLE].
         *
         * A value this build does not know is not an error to surface: it is a style
         * written by a newer build, and the honest answer is the plain one rather than
         * a card that refuses to draw.
         */
        fun fromStorage(value: String?): MediaCardStyle =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT_STYLE
    }
}
