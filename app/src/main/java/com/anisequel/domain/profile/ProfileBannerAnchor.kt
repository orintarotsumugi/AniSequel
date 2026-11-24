package com.anisequel.domain.profile

/**
 * Which part of the banner the header keeps when the picture is wider than the card.
 *
 * The banner is a box with a picture covering it, and the cover fit crops by
 * definition: a 4:1 AniList panorama in a 2.4:1 card loses the outer edges, and the
 * reader never gets to choose which ones. The shape setting answers "how tall",
 * this one answers "which part" - and because the picture is wider than the card,
 * the answer is a horizontal position, not a resize.
 *
 * [fraction] is the value the backdrop transform takes: 0 keeps the left edge of
 * the artwork, 1 the right, 0.5 centres the crop. It is a fraction of the reachable
 * range rather than a pixel offset, so the same choice means the same thing on a
 * 360dp phone and in the 640dp column.
 *
 * [CENTER] is the default because it is what the header already did: a reader who
 * never opens the editor must not find their banner moved by an update.
 */
enum class ProfileBannerAnchor(val storageValue: String, val displayName: String) {
    LEFT("left", "Left"),
    CENTER("center", "Center"),
    RIGHT("right", "Right");

    /** 0 keeps the left edge, 1 the right; the backdrop transform's anchor. */
    val fraction: Float
        get() = when (this) {
            LEFT -> 0f
            CENTER -> 0.5f
            RIGHT -> 1f
        }

    companion object {
        val DEFAULT = CENTER

        /** Unknown or missing storage is [DEFAULT], never an error. */
        fun fromStorage(value: String?): ProfileBannerAnchor =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
