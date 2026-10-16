package com.anisequel.domain.motion

import kotlin.math.PI
import kotlin.math.sin

/**
 * The arithmetic behind the Info page's wavy progress bar.
 *
 * It lives in `domain` rather than beside the `Canvas` that draws it because the
 * arithmetic is the only part of that card a test can hold: the drawing is not
 * assertable, and every visible mistake in this control is a mistake in the
 * numbers - a wave that does not reach its full height at 100% hides the last
 * episode, and one that *is* full height at 0% draws a wobble on a title nobody
 * has started.
 */
object WaveGeometry {

    /**
     * How tall the wave stands at [progress], as a fraction of [maxAmplitude].
     *
     * Never zero, not even at rest: the track is drawn as this same wave, so a
     * `0` here would make an untouched title's track a ruled line while every
     * other title's is a wave. [floor] is that resting height.
     */
    fun amplitude(maxAmplitude: Float, progress: Float, floor: Float = 0.45f): Float =
        maxAmplitude * (floor + (1f - floor) * progress.coerceIn(0f, 1f))

    /**
     * The cross-axis offset at [x], in the same units as [amplitude].
     *
     * A sine rather than a chain of cubics: a cubic approximation of one period
     * drifts a few percent at the crests, and the crests are the only part of the
     * curve the eye actually follows.
     */
    fun offset(x: Float, wavelength: Float, amplitude: Float, phase: Float): Float =
        amplitude * sin(TWO_PI * (x / wavelength) + phase)

    /**
     * The phase for a wave [cycleFraction] of the way through one period.
     *
     * The caller animates the fraction (0..1 on a loop) and this turns it into
     * radians, so the travelling motion is a function of the animation rather than
     * of a frame clock the test would have to fake.
     */
    fun phase(cycleFraction: Float): Float = TWO_PI * cycleFraction

    private const val TWO_PI = (2.0 * PI).toFloat()
}
