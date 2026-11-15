package com.anisequel.domain.ui

/**
 * How far the main bar floats above the bottom edge.
 *
 * A reader asked to move the bar up and down rather than only having it pinned to
 * the device's navigation inset. The range used to stop at 24dp - roughly one bar
 * height of daylight underneath - and the reader who asked for more meant exactly
 * that: on a tall phone 24dp is barely a fingerprint of separation, and it is not
 * enough to clear a thumb resting on the bottom edge. 96dp is four bar heights of
 * daylight, still short of the halfway point of any supported phone, and it is
 * where the bar stops being a bottom control and starts being a mistake, so that
 * is the ceiling.
 *
 * The value is in whole dp rather than a float so the slider's ticks, the stored
 * preference and the preview all describe the same number. [clamp] is applied on
 * the way in *and* on the way out: a preference written by an older or a buggy
 * build must not push the bar behind the top bar, and `DataStore` has no schema
 * to enforce that.
 */
object NavBarLift {
    const val MIN_DP: Int = 0
    const val MAX_DP: Int = 96
    const val DEFAULT_DP: Int = 0

    /**
     * How far past [MAX_DP] the animated value may travel before it is clamped.
     *
     * The spatial spring overshoots its target. Clamping it to the stored range on
     * every frame - which the per-frame guard has to do at the *bottom* to avoid
     * the shipped `Padding must be non-negative` crash - turned the ceiling into a
     * wall: the bar arrived at 96dp a fraction early, sat still for the remaining
     * frames of a spring that wanted to go further, and came back. A little room
     * above the ceiling keeps the bounce that makes the control feel physical, and
     * 16dp is under the overshoot a 96dp spring can ask for.
     */
    const val OVERSHOOT_DP: Int = 16

    /** The slider's step size, and the unit the preference stores. */
    const val STEP_DP: Int = 4

    fun clamp(value: Int): Int = value.coerceIn(MIN_DP, MAX_DP)

    /**
     * The stored range as a float, for a value that must not leave it.
     *
     * Used where the number describes the *setting* rather than a frame of the
     * spring - a preview's static measurement, a test's expectation. It is not the
     * guard the animation needs; see [clampAnimatedDp].
     */
    fun clampDp(value: Float): Float =
        value.coerceIn(MIN_DP.toFloat(), MAX_DP.toFloat())

    /**
     * The per-frame guard for an animated lift.
     *
     * The slider's spring overshoots its target - that is what makes the lift feel
     * physical - and on the way back to zero an overshooting spring goes **below**
     * zero before it settles. A negative dp is not a smaller lift: Compose rejects
     * it (`Padding must be non-negative`) and the whole frame throws. So zero is a
     * hard floor, on every frame, not only where the preference is written.
     *
     * The ceiling is [MAX_DP] plus [OVERSHOOT_DP] rather than [MAX_DP], so a spring
     * arriving at the top of the range still lands with its own physics instead of
     * stopping dead against the clamp.
     */
    fun clampAnimatedDp(value: Float): Float =
        value.coerceIn(MIN_DP.toFloat(), (MAX_DP + OVERSHOOT_DP).toFloat())
}
