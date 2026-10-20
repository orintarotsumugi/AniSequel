package com.anisequel.ui.components.detail

import kotlin.math.roundToInt

/**
 * The five score formats an AniList account can have, with the scale each one
 * actually holds.
 *
 * ## Why the editor needs this at all
 *
 * `SaveMediaListEntry` takes the score in the account's own format, and the
 * editor deliberately sends it back unscaled (see [EntryDraft.score]). That
 * leaves the *control* with two questions it cannot answer from the number
 * alone: how far the slider goes, and how fine its ticks are.
 *
 * It used to answer the first by guessing - `score > 10` meant a hundred-point
 * account - and never asked the second, so every scale got one tick per whole
 * point. Both halves were wrong for real accounts:
 *
 *  - a **POINT_100** account whose entry is scored 8 or 10 got a 0-10 slider,
 *    with no way to reach the 47 it actually wanted;
 *  - a **POINT_5** or **POINT_3** account got a 0-10 slider too, which offers
 *    scores the server rejects;
 *  - a **POINT_10_DECIMAL** account could not enter `8.5` even though the row
 *    above the slider was already printing it, because one tick per point snaps
 *    to 8 or 9.
 *
 * The format is not on the entry, but it is on `Viewer.mediaListOptions`, so the
 * load query asks for it in the same request. See `ScoreScale`.
 */
internal enum class ScoreFormat(
    /** The enum's wire spelling, which is what `Viewer` answers with. */
    val apiValue: String,
    /** The top of the scale, in the account's own units. */
    val max: Int,
    /**
     * The smallest step the format accepts.
     *
     * Only [POINT_10_DECIMAL] is fractional: the whole point of that format is
     * that `POINT_10` is integer-only, so a half score exists on AniList only for
     * accounts that chose the decimal one. An integer step for the others is
     * what stops the app offering a value the server will refuse.
     */
    val step: Double
) {
    POINT_100("POINT_100", 100, 1.0),
    POINT_10_DECIMAL("POINT_10_DECIMAL", 10, 0.1),
    POINT_10("POINT_10", 10, 1.0),
    POINT_5("POINT_5", 5, 1.0),
    POINT_3("POINT_3", 3, 1.0);

    companion object {
        /**
         * A wire value as a format, or null when this build has never heard of it.
         *
         * Case-insensitive for the same reason every other API token comparison
         * here is: the spelling is the server's, and a format AniList adds later
         * must degrade to the guess in [scoreScaleFor] rather than crash the
         * editor.
         */
        fun fromApi(value: String?): ScoreFormat? =
            value?.let { raw -> entries.firstOrNull { it.apiValue.equals(raw, ignoreCase = true) } }
    }
}

/**
 * How a score slider is drawn: its ceiling and the size of one step.
 *
 * Derived once when the entry loads and carried in the draft, so moving the
 * thumb cannot change the scale mid-drag - the same reason [EntryDraft.scoreMax]
 * is captured rather than recomputed.
 */
internal data class ScoreScale(val max: Int, val step: Double) {

    /**
     * The `steps` argument for Material's `Slider`: the gaps between ticks.
     *
     * Material counts gaps rather than ticks, so a half-point slider over ten is
     * 19. Above [MAX_DRAWN_TICKS] the slider is drawn *continuous* (0) and
     * [quantise] does the snapping instead: a hundred-point scale would draw 100
     * tick marks 3dp apart, which reads as a dotted rule rather than as a scale,
     * and a 0.1-step ten-point scale would do the same. Everything this control
     * has to guarantee is that the value it writes is one the format holds, and
     * that holds on a continuous slider too.
     */
    val steps: Int
        get() {
            val gaps = (max / step).roundToInt() - 1
            return if (gaps > MAX_DRAWN_TICKS) 0 else gaps.coerceAtLeast(0)
        }

    /**
     * [value] moved onto the nearest tick the format actually holds.
     *
     * Applied on every drag rather than only on save: the slider reports floats,
     * so a 0.1-step scale would otherwise write `8.299999999999999` into the
     * draft and the row above it would print that. Rounding to three decimals is
     * enough for every format here and leaves `8.3` as `8.3`.
     */
    fun quantise(value: Double): Double {
        val snapped = (value / step).roundToInt() * step
        val clean = (snapped * 1000).roundToInt() / 1000.0
        return clean.coerceIn(0.0, max.toDouble())
    }
}

/** Beyond this many steps the ticks are dropped in favour of snapping. */
private const val MAX_DRAWN_TICKS = 20

/**
 * The scale to draw for [format], falling back to the old guess when it is
 * unknown.
 *
 * The fallback keeps the pre-format behaviour for everything that cannot answer
 * the question - a save response carries no `Viewer`, and a caller that never
 * loaded through the repository has no format either. Its step is halved to
 * 0.5 rather than left at 1: a loaded `8.5` was already being printed by the
 * row while the slider could not reach it, and a value the form shows must be a
 * value the form can set.
 */
internal fun scoreScaleFor(format: ScoreFormat?, loadedScore: Double): ScoreScale = when {
    format != null -> ScoreScale(format.max, format.step)
    loadedScore > 10.0 -> ScoreScale(100, 1.0)
    else -> ScoreScale(10, 0.5)
}
