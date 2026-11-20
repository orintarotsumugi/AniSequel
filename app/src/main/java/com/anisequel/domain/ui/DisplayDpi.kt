package com.anisequel.domain.ui

/**
 * The display density the app draws at, as a dpi number rather than a multiplier.
 *
 * Written in dpi because that is the unit Android and every reader already use for
 * this: the value matches what `wm density` prints, what a phone's spec sheet calls
 * its panel, and what the Settings page shows. Compose's own unit is dpi/160, and
 * [density] is the one place that division happens.
 *
 * ## Why the app overrides the device's number at all
 *
 * Every length in this app is a dp, and a dp is only as large as the device's dpi
 * says it is: the same layout is chunkier on a 440 dpi phone and finer on a 480 dpi
 * one, and a reader who wants more or less on screen has no setting for it. Overriding
 * the density is the same move as Android's own developer density switch, offered as
 * a preference, so a reader can make the whole app - cards, text, spacing - larger or
 * denser without a per-screen "zoom".
 *
 * [DEFAULT] is the requested baseline rather than the device's own number: the point
 * of the setting is that the app looks the same everywhere until the reader says
 * otherwise. The range is deliberately wide enough to be useful and narrow enough to
 * stay drawable - below [MIN_DPI] the Library's three-column grid stops fitting a
 * title, and past [MAX_DPI] a card caption is a single word.
 */
object DisplayDpi {
    const val MIN_DPI: Int = 320
    const val MAX_DPI: Int = 600

    /** The density every install starts at, whatever the panel reports. */
    const val DEFAULT_DPI: Int = 447

    /** The slider's step; a whole dpi, because the label prints a whole number. */
    const val STEP_DPI: Int = 8

    fun clamp(value: Int): Int = value.coerceIn(MIN_DPI, MAX_DPI)

    /**
     * The Compose density for [value].
     *
     * Clamped on the way through: the preference is a plain integer in a DataStore
     * file a reader can edit and an older build can write, and a density of 0 or
     * 2000 does not draw a wrong page - it throws.
     */
    fun density(value: Int): Float = clamp(value) / 160f

    /**
     * The density scale a given mode resolves to.
     *
     * [DpiMode.AUTOMATIC] deliberately skips the manual range clamp: a tablet or a
     * foldable can report a density above [MAX_DPI], and clamping it would make
     * "follow the device" quietly draw at a different size than the device asked
     * for. A device that reports nothing usable falls back to [DEFAULT_DPI].
     * [DpiMode.MANUAL] keeps the clamp, because its value can come from a
     * hand-edited preferences file as well as from the slider.
     */
    fun densityFor(mode: DpiMode, manualDpi: Int, deviceDpi: Int): Float =
        if (mode == DpiMode.AUTOMATIC) {
            (deviceDpi.takeIf { it > 0 } ?: DEFAULT_DPI) / 160f
        } else {
            density(manualDpi)
        }
}

/**
 * Whether the app follows the device's density or draws at a chosen one.
 *
 * Android's own display-density override is a number, but its natural state is
 * "whatever this panel reports" - and device-management tools expose the same
 * choice as Default/Automatic versus a custom value. [MANUAL] is the shipped
 * default here so an install that upgrades keeps drawing at the size it already
 * had; [AUTOMATIC] is opt-in and stops the app forcing a phone-sized number on a
 * tablet or a foldable.
 */
enum class DpiMode(
    val storageValue: String,
    val displayName: String,
    val blurb: String
) {
    AUTOMATIC(
        storageValue = "automatic",
        displayName = "Automatic",
        blurb = "Follow the density your device reports. Nothing is forced, so a " +
            "tablet or a foldable draws at the size its own panel asks for."
    ),
    MANUAL(
        storageValue = "manual",
        displayName = "Manual",
        blurb = "Draw at one size everywhere, whatever the device reports. " +
            "This is what a fresh install has always done."
    );

    companion object {
        /** The shipped mode: an upgrade must not silently resize every screen. */
        val DEFAULT: DpiMode = MANUAL

        fun fromStorage(value: String?): DpiMode =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * The three stops [DisplayDpi] is usually asked for, in words.
 *
 * A dpi slider is precise and unreadable: 447 is not a size anybody pictures, and a
 * reader who wants "a bit bigger" should not have to know which way the number goes.
 * These name the three sizes people actually choose between, and the slider stays under
 * them for the reader who wants the value in between.
 *
 * [DEFAULT] is not the midpoint of the other two on purpose - it is the density every
 * install starts at, and the preset a reader returns to after experimenting has to be
 * the same number as the shipped default or the two would disagree about "normal".
 */
enum class UiSize(
    val displayName: String,
    val dpi: Int,
    val blurb: String
) {
    COMPACT(
        displayName = "Small",
        dpi = 400,
        blurb = "More on screen: the same layout at a finer density."
    ),
    DEFAULT(
        displayName = "Default",
        dpi = DisplayDpi.DEFAULT_DPI,
        blurb = "The shipped size, on every phone."
    ),
    LARGE(
        displayName = "Large",
        dpi = 512,
        blurb = "Fewer, larger controls - for a reader who wants to aim less."
    );

    companion object {
        /**
         * The preset a stored dpi belongs to, or null between two of them.
         *
         * Null rather than the nearest preset: the slider can land anywhere in the
         * range, and highlighting "Large" for a value that is not large would be the
         * page agreeing with itself rather than with the preference.
         */
        fun of(dpi: Int): UiSize? = entries.firstOrNull { it.dpi == dpi }
    }
}
