package com.anisequel.data.repository

/**
 * How the app picks between light and dark.
 *
 * [SYSTEM] follows the device's own setting, which is also what happens when
 * nothing has been chosen. [LIGHT] and [DARK] pin it, for the people who want
 * one regardless of what their phone is set to.
 */
enum class ThemeMode(val storageValue: String, val displayName: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        val DEFAULT = SYSTEM

        /**
         * Reads a stored value, falling back to [DEFAULT].
         *
         * Falls back rather than throwing: a preference file written by a newer
         * build (or hand-edited) can hold a value this build has never heard of,
         * and a theme preference is not worth refusing to start over.
         */
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * How far a retained cover colour reaches into the background.
 *
 * Retention used to be all-or-nothing: the show's hue either repainted every
 * surface or stopped at the accent. Both are honest answers to different readers -
 * a night-sky show at full tint is a very dark app, while its accent alone is a
 * navy app with one blue button - so the choice is a setting rather than a guess.
 *
 * The strength only applies after the sheet closes. While a title is open the
 * immersive layer is the point, and dialling it down would make the preview
 * disagree with the page the reader is looking at.
 */
enum class RetainedTint(
    val storageValue: String,
    val displayName: String,
    /** 0 = the palette's own surfaces, 1 = the cover's tinted scheme. */
    val strength: Float
) {
    SUBTLE("subtle", "Subtle", 0.35f),
    BALANCED("balanced", "Balanced", 0.7f),
    FULL("full", "Full", 1f);

    companion object {
        val DEFAULT = FULL

        fun fromStorage(value: String?): RetainedTint =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * How the app animates, as one of five characters.
 *
 * [SNAPPY] and [FLOATY] were added beside the original three so the choice spans a
 * real range rather than "off / plain / expressive": Snappy is for readers who want
 * the app to feel instant without losing its transitions, Floaty for the opposite -
 * slow, soft movement on everything. The token mappings in `ExpressiveMotion` give
 * each one its own physics; the Appearance tab previews them on the spot, because a
 * name is not a feel.
 *
 * Order is the order the picker draws, so a style added at the end stays where the
 * reader expects it rather than reshuffling the row.
 */
enum class MotionStyle(val storageValue: String, val displayName: String) {
    INSTANT("instant", "Instant"),
    SMOOTH("smooth", "Smooth"),
    SNAPPY("snappy", "Snappy"),
    CHILL("chill", "Chill"),
    FLOATY("floaty", "Floaty");

    companion object {
        val DEFAULT = CHILL

        fun fromStorage(value: String?): MotionStyle =
            entries.firstOrNull { it.storageValue == value }
                // Storage values written before the rename: "expressive" and
                // "standard" both existed in the old two-way toggle. Map them rather
                // than resetting silently - the old labels meant "a bit more bounce"
                // and "a bit more calm", which is Chill and Smooth respectively.
                ?: when (value) {
                    "expressive" -> CHILL
                    "standard" -> SMOOTH
                    else -> DEFAULT
                }
    }
}

/**
 * What sits behind the Info page once a title is open.
 *
 * The page had one backdrop before this: the hero's own artwork, which scrolled
 * away with it. Below the hero the sheet was the plain container colour, and a
 * title whose art is the reason the reader opened it became a grey list of cards
 * halfway down the page.
 *
 * [BANNER] is the default because AniList's wide key visual is the one image the
 * sheet otherwise never shows at size, and it is already being decoded for the
 * hero. [COVER] is the poster for titles with no banner, and for readers who
 * prefer the shape they tapped in the grid. [OFF] keeps the page on its surface,
 * which is still an honest choice: the backdrop is decoration, the cards are what
 * the page is for.
 */
enum class InfoBackdropSource(val storageValue: String, val displayName: String) {
    BANNER("banner", "Banner"),
    COVER("cover", "Cover"),
    OFF("off", "Off");

    companion object {
        val DEFAULT = BANNER

        fun fromStorage(value: String?): InfoBackdropSource =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * How much the Info page's backdrop is softened.
 *
 * The backdrop sits *under* the cards, not behind the hero, and the cards are
 * translucent enough that their text is read against it. Sharp artwork there
 * competes with every caption on the page; the blur is what turns the image into
 * a colour and an impression while leaving it recognisable. [CRISP] exists for
 * readers who want the artwork itself, [HEAVY] for the ones who want only its
 * palette.
 *
 * The radius is in dp and lives on the enum so the preference file stores one
 * stable word and the composable owns the one conversion to a blur radius.
 */
enum class InfoBackdropBlur(
    val storageValue: String,
    val displayName: String,
    val radiusDp: Float
) {
    CRISP("crisp", "Crisp", 0f),
    SOFT("soft", "Soft", 18f),
    HEAVY("heavy", "Heavy", 36f);

    companion object {
        val DEFAULT = SOFT

        fun fromStorage(value: String?): InfoBackdropBlur =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
