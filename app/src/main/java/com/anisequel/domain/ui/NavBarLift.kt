package com.anisequel.domain.ui

/**
 * How far the main bar floats above the bottom edge.
 *
 * A reader asked to move the bar up and down rather than only having it pinned to
 * the device's navigation inset. The range is deliberately small: the bar is a
 * floating control, not a shelf, and lifting it far enough to cover the middle of
 * a page would trade a gesture for a hole in the layout. 24dp is roughly one bar
 * height of daylight under it.
 *
 * The value is in whole dp rather than a float so the slider's ticks, the stored
 * preference and the preview all describe the same number. [clamp] is applied on
 * the way in *and* on the way out: a preference written by an older or a buggy
 * build must not push the bar behind the top bar, and `DataStore` has no schema
 * to enforce that.
 */
object NavBarLift {
    const val MIN_DP: Int = 0
    const val MAX_DP: Int = 24
    const val DEFAULT_DP: Int = 0

    /** The slider's step size, and the unit the preference stores. */
    const val STEP_DP: Int = 2

    fun clamp(value: Int): Int = value.coerceIn(MIN_DP, MAX_DP)
}
