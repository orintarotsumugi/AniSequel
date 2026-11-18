package com.anisequel.domain.ui

/**
 * How the Info page draws a title's progress.
 *
 * The wave is the shipped mark and stays the default: it is the shape readers
 * already associate with "how far in am I". The two additions answer the other
 * two readings of the same number - a plain bar when the reader wants the
 * quantity without the flourish, and the rocket when the journey is the point.
 *
 * Stored by [storageValue] rather than ordinal, for the same reason every other
 * interface enum is: reordering the entries must not silently change an install's
 * bar, and a value written by a newer build falls back to the wave instead of
 * crashing.
 */
enum class ProgressBarStyle(
    val storageValue: String,
    val displayName: String,
    val blurb: String
) {
    WAVE(
        storageValue = "wave",
        displayName = "Wave",
        blurb = "The bar rises and travels as you make your way through the title."
    ),
    BAR(
        storageValue = "bar",
        displayName = "Simple bar",
        blurb = "A clean determinate bar with the watched share filled in."
    ),
    ROCKET(
        storageValue = "rocket",
        displayName = "Rocket",
        blurb = "A rocket crosses the track toward the moon as your progress grows."
    );

    companion object {
        val DEFAULT = WAVE

        fun fromStorage(value: String?): ProgressBarStyle =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
