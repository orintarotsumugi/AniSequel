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
 * A few starting grains are seeded per column, and the starting profile is a
 * gentle bank rather than independent grains per column. A drift that begins at
 * exactly zero looks like a bug for the first half-minute, and a drift of
 * forty-eight independent heights looks like a comb once it is drawn.
 *
 * ## Why a landing obeys headroom, and why the heap compacts
 *
 * Deposits used to add the same mass every time until a column hit the ceiling,
 * and the smoothing pass only ran after a landing. At the busiest density a
 * column reaches the ceiling in about a minute, after which the whole field is a
 * flat white wall the full depth of the slider - the pile stops looking like
 * something that accumulated and starts covering the page. A deposit now adds
 * less the fuller the column already is, and every landing frame lets the field
 * compact by a small fraction (a real snowpack settles under its own weight).
 * The two together find a stable depth instead of a wall, and the variation the
 * particles drop stays visible because the field is never pinned at the cap.
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
        // One slow swell plus a little grain. The swell is what makes the first
        // frame read as a bank; the grain is what stops it reading as a sine wave.
        val swellPhase = random.nextFloat() * 6.2831855f
        val swellRate = 0.11f + random.nextFloat() * 0.08f
        for (index in 0 until columns) {
            val swell = 0.026f + 0.012f * kotlin.math.sin(index * swellRate + swellPhase)
            heights[index] = swell + random.nextFloat() * 0.018f
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
            // The closer a column is to its ceiling, the less of this landing it
            // keeps. Without the headroom the field saturates and the drift becomes
            // a flat band at the slider's maximum depth.
            val headroom = (PILE_HEIGHT_CEILING - heights[index])
                .coerceIn(0f, PILE_HEIGHT_CEILING)
            val gain = PILE_MASS_PER_LANDING * strength * weight *
                (0.42f + 0.58f * headroom / PILE_HEIGHT_CEILING)
            heights[index] = (heights[index] + gain).coerceAtMost(PILE_HEIGHT_CEILING)
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
     *
     * [amount] may be zero: a low-end sky skips the diffusion pass but still needs the
     * compaction, or it reaches the ceiling and stays there. The compaction runs first
     * so a heap that is already at the cap has room for the diffusion to move into.
     */
    fun relax(amount: Float = 0.16f) {
        if (columns < 3) return
        val blend = amount.coerceIn(0f, 0.45f)
        // The scratch buffer is reused because this runs on the frame loop. Building a
        // new field every landing was a hundred-odd floats a second of pure garbage.
        for (index in 0 until columns) {
            val compacted = heights[index] * (1f - PILE_COMPACTION_PER_LANDING)
            val left = heights[(index - 1).coerceAtLeast(0)]
            val right = heights[(index + 1).coerceAtMost(columns - 1)]
            scratch[index] = (
                compacted * (1f - blend) + (left + right) * 0.5f * blend
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

        /**
         * How much a drift settles on each landing frame.
         *
         * Steady-state snow: the deposit rate at the busiest sky is about a unit of
         * height a second spread across the field, so a small per-pass loss is what
         * holds the average near half depth instead of at the cap. Tuned by arithmetic,
         * not by eye - there is no phone in this environment - and deliberately tiny:
         * a compaction a reader could see between frames would read as the snow sinking.
         */
        const val PILE_COMPACTION_PER_LANDING = 0.006f
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
