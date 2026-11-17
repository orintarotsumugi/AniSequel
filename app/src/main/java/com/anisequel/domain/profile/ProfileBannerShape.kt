package com.anisequel.domain.profile

/**
 * The shape of the picture at the top of a profile.
 *
 * AniList banners are whatever the owner uploaded - a 16:9 key visual, a square
 * avatar-like crop, a vertical illustration - and the profile header used to be one
 * fixed height for all of them, so every picture that was not close to that height
 * was cropped to its middle band. [ADAPT] is the default and gives the banner the
 * picture's own ratio; the other three are the reader's override when the automatic
 * answer is not the one they want.
 *
 * [storageValue] is the preferences key and stays stable across a rename of the
 * label, for the same reason [ProfileSection] has one.
 */
enum class ProfileBannerShape(val storageValue: String, val displayName: String) {
    /** The banner is as tall as its own picture needs, within the clamps below. */
    ADAPT("adapt", "Adapt"),
    LANDSCAPE("landscape", "Landscape"),
    SQUARE("square", "Square"),
    PORTRAIT("portrait", "Portrait");

    /** The width-to-height ratio this shape asks for. Meaningless for [ADAPT]. */
    val aspect: Float
        get() = when (this) {
            ADAPT -> ProfileBannerSizing.LANDSCAPE
            LANDSCAPE -> ProfileBannerSizing.LANDSCAPE
            SQUARE -> ProfileBannerSizing.SQUARE
            PORTRAIT -> ProfileBannerSizing.PORTRAIT
        }

    companion object {
        val DEFAULT = ADAPT

        /** Unknown or missing storage is [DEFAULT], never an error. */
        fun fromStorage(value: String?): ProfileBannerShape =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * How tall the profile banner is, from the shape and the picture it is drawing.
 *
 * Pure, and in the domain layer, because it is the whole of the "adapt to the
 * banner" decision: the composable measures a width and an image, and this answers
 * with a height. Two clamps keep the answer usable rather than literal:
 *
 * - **A floor of [MIN_HEIGHT_DP].** AniList user banners are commonly around 4:1,
 *   and the honest height for one at a phone's width is under 100dp - a strip that
 *   the avatar, the name and the scrim would not fit inside. The floor crops the
 *   sides of a panorama and keeps the header a header.
 * - **A ceiling of [MAX_HEIGHT_DP].** A vertical illustration is legitimately
 *   taller than it is wide, but a banner that eats most of the screen pushes the
 *   bio and the favourites so far down that the page reads as a gallery of one
 *   image. The ceiling lets a portrait banner be noticeably tall without becoming
 *   the page.
 *
 * The clamps are on the *height*, not the ratio, so they behave the same on a
 * 360dp phone and on a 640dp tablet.
 */
object ProfileBannerSizing {

    /** 16:9, the shape most banners are uploaded at and the one the presets name. */
    const val LANDSCAPE = 16f / 9f
    const val SQUARE = 1f
    /** 4:5 - a portrait crop, the shape a phone photograph of cover art usually is. */
    const val PORTRAIT = 4f / 5f

    /**
     * The ratios an adaptive banner is allowed to take before the clamps apply.
     *
     * 0.72 is just taller than 4:5 and 2.4 is wider than a cinema frame. Both are
     * only reached by unusual uploads; ordinary 16:9 and 4:5 pictures pass through
     * untouched, which is what makes the default feel like nothing happened.
     */
    private const val ADAPTIVE_MIN_ASPECT = 0.72f
    private const val ADAPTIVE_MAX_ASPECT = 2.4f

    const val MIN_HEIGHT_DP = 200f

    /**
     * 640dp, the width the profile column is capped at (see `ProfileMaxContentWidth`).
     *
     * The ceiling is what stops a vertical upload from taking the page, and it also
     * has to leave the [SQUARE] preset square: a square banner on a tablet is 640dp
     * wide and 640dp tall, so a ceiling below the column's own width would silently
     * turn the one preset that is defined by equality into a rectangle.
     */
    const val MAX_HEIGHT_DP = 640f

    /**
     * The ratio to draw [shape] at, given the picture's own pixel size.
     *
     * [imageWidth] and [imageHeight] may be zero before the image loads, and may be
     * NaN for a format whose intrinsic size the painter does not know - both answer
     * with the landscape ratio, which is the "nothing measured yet" shape rather
     * than a divide that would poison the layout.
     */
    fun aspectFor(
        shape: ProfileBannerShape,
        imageWidth: Float = 0f,
        imageHeight: Float = 0f
    ): Float {
        if (shape != ProfileBannerShape.ADAPT) return shape.aspect
        if (imageWidth <= 0f || imageHeight <= 0f) return LANDSCAPE
        val measured = imageWidth / imageHeight
        if (!measured.isFinite() || measured <= 0f) return LANDSCAPE
        return measured.coerceIn(ADAPTIVE_MIN_ASPECT, ADAPTIVE_MAX_ASPECT)
    }

    /** The banner's height in dp for a container [widthDp] wide at [aspect]. */
    fun heightDp(widthDp: Float, aspect: Float): Float {
        if (widthDp <= 0f) return MIN_HEIGHT_DP
        if (aspect <= 0f || !aspect.isFinite()) return MIN_HEIGHT_DP
        return (widthDp / aspect).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
    }
}
