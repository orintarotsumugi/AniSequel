package com.anisequel.domain.seasonal

import kotlin.random.Random

/**
 * The drift that builds along the bottom edge while a settling sky is on.
 *
 * ## Why this is not a simulation of every flake
 *
 * The sky is a closed-form field: a particle's position is `f(seed, clock)`, and
 * nothing accumulates. Making each flake genuinely land would mean per-particle
 * state, collision with other flakes, and a simulation that could disagree with
 * the drawing. Instead the layer notices each time a particle's cycle wraps at
 * the bottom - the one moment the reader can see it reach the floor - and
 * deposits a small amount of mass at that lateral position here. The result is
 * driven by the same particles the reader watches, without a second simulation.
 *
 * The field is normalised: 0 is bare ground, 1 is the tallest the pile can get
 * at this depth. The caller supplies pixels, so depth is a drawing choice and a
 * slider change never has to throw the accumulation away.
 *
 * A few starting grains are seeded per column. A drift that begins at exactly
 * zero looks like a bug for the first half-minute, and this is decoration - it
 * should look right the moment the switch is flipped.
 */
class SeasonalPile(
    seed: Int = 0,
    val columns: Int = SEASONAL_PILE_COLUMNS
) {
    private val heights = FloatArray(columns)

    /** Second buffer for [relax], kept so a smoothing pass allocates nothing. */
    private val scratch = FloatArray(columns)

    init {
        val random = Random(seed)
        for (index in 0 until columns) {
            heights[index] = 0.018f + random.nextFloat() * 0.05f
        }
    }

    /** The normalised height of one column, clamped to the array. */
    fun column(index: Int): Float = heights[index.coerceIn(0, columns - 1)]

    /**
     * The normalised height at a horizontal fraction, linearly interpolated.
     *
     * Interpolated rather than sampled by nearest column because the drawing
     * samples the surface at more points than there are columns, and a staircase
     * is the difference between snow and a bar chart.
     */
    fun heightAt(fraction: Float): Float {
        val clamped = fraction.coerceIn(0f, 1f)
        val position = clamped * (columns - 1)
        val low = position.toInt()
        val high = (low + 1).coerceAtMost(columns - 1)
        val blend = position - low
        return heights[low] + (heights[high] - heights[low]) * blend
    }

    /**
     * Adds one landing's worth of mass at [xFraction].
     *
     * [mass] is the particle's own size multiplier, so a big leaf builds a
     * bigger local mound than a small petal without a second rate constant.
     * Deposits are a small kernel rather than one column: a pile with 48 perfect
     * spikes and 48 perfect gaps is a comb, not a drift. The weights sum to more
     * than one on purpose so a column visibly reacts to its own landing.
     */
    fun deposit(xFraction: Float, mass: Float = 1f, spread: Int = 2) {
        val center = (xFraction.coerceIn(0f, 0.9999f) * columns).toInt()
        val strength = mass.coerceIn(0.2f, 1.6f)
        val radius = spread.coerceIn(1, 4)
        for (offset in -radius..radius) {
            val index = center + offset
            if (index !in 0 until columns) continue
            val distance = kotlin.math.abs(offset)
            val weight = when (distance) {
                0 -> 0.62f
                1 -> 0.22f
                2 -> 0.07f
                else -> 0.035f
            }
            heights[index] = (heights[index] + PILE_MASS_PER_LANDING * strength * weight)
                .coerceAtMost(PILE_HEIGHT_CEILING)
        }
    }

    /**
     * Softens neighbouring columns so a mound spreads instead of growing a spike.
     *
     * Called only on a frame in which something actually landed, not every frame: a
     * diffusion stepped at sixty hertz a second smoothed the whole drift into one flat
     * band within a minute, which is a pile that has stopped looking like snow.
     * Averaging inside [deposit] would flatten a fresh flake before the reader saw it
     * arrive, which is the other way this gets it wrong.
     */
    fun relax(amount: Float = 0.16f) {
        if (columns < 3) return
        val blend = amount.coerceIn(0f, 0.45f)
        // The scratch buffer is reused because this runs on the frame loop. Building a
        // new field every landing was a hundred-odd floats a second of pure garbage.
        for (index in 0 until columns) {
            val left = heights[(index - 1).coerceAtLeast(0)]
            val right = heights[(index + 1).coerceAtMost(columns - 1)]
            scratch[index] = (
                heights[index] * (1f - blend) + (left + right) * 0.5f * blend
                ).coerceAtMost(PILE_HEIGHT_CEILING)
        }
        scratch.copyInto(heights)
    }

    /** Every column at the ceiling, for a test that needs a finished drift. */
    fun fill() {
        heights.fill(PILE_HEIGHT_CEILING)
    }

    private companion object {
        /**
         * How much one landing raises the column it lands in.
         *
         * Tuned against the loop: at 64 particles each cycle takes roughly seven
         * seconds, so about nine landings a second land across 48 columns. A
         * drift should look settled within a minute and never reach a wall.
         */
        const val PILE_MASS_PER_LANDING = 0.11f
        const val PILE_HEIGHT_CEILING = 0.98f
    }
}

/**
 * The drift's resolution.
 *
 * 48 is enough for a soft silhouette across the narrowest phone (about seven dp
 * per column at 360dp) and small enough that depositing is a handful of array
 * writes. It is not a function of the display density because the shape is drawn
 * smooth between columns.
 */
const val SEASONAL_PILE_COLUMNS = 48
