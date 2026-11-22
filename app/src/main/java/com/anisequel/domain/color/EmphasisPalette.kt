package com.anisequel.domain.color

/**
 * The emphasis roles the UI was previously faking with alpha.
 *
 * ## The bug this replaces
 *
 * Material 3 gives every role a designed partner - `onSurface` on `surface`,
 * `onSurfaceVariant` on `surfaceVariant` - and nothing for "the same text, one
 * step quieter". So the app dimmed the partner: `onSurfaceVariant.copy(alpha =
 * 0.62f)` for a disabled chip label, `onSurface.copy(alpha = 0.38f)` for a
 * format button, `onSurfaceVariant.copy(alpha = 0.3f)` for a skeleton line.
 *
 * An alpha is measured against whatever is behind it, which made every one of
 * those a *different* colour on a card than on the page, and made them all fail
 * at once in the light theme: the light scheme's secondary text is #51637A, so
 * 0.62 over white is 2.7:1 and 0.3 over white is 1.5:1. The disabled label was
 * the least readable text on the screen, and the skeleton was a sweep of
 * nothing.
 *
 * These roles are computed instead: each is the quietest tone of the scheme that
 * still clears a *named* floor against `surfaceContainerHighest`, the worst
 * surface any of them is drawn on. A caption is 3.5:1, body-adjacent text 4.5:1,
 * a control's own parts 3:1, a divider 2:1 - a ladder of measured contrast
 * rather than a ladder of invented alphas.
 *
 * ## Why it follows the scheme
 *
 * Everything here is derived from the [MaterialScheme] handed in, so the roles
 * survive an anime cover's palette, a picked canvas, the platform's dynamic
 * scheme and both light and dark - the four ways this app's palette moves. A
 * hardcoded pair would be right in exactly one of them.
 */
data class EmphasisPalette(
    /** Secondary body text: 4.5:1 on the worst surface, any text size. */
    val secondary: Int,
    /** Captions, row under-lines and icons that carry meaning: 3.5:1. */
    val muted: Int,
    /** A control that is present but cannot be used: 3:1, not the 1.7:1 it was. */
    val disabled: Int,
    /** A hairline between two rows of one card: 2:1 - quiet, but a drawn line. */
    val divider: Int,
    /** The edge of a card that has to be seen as an edge: 3:1. */
    val boundary: Int,
    /** A progress bar's unfilled track: 3:1, so the track is visible at all. */
    val track: Int,
    /** A loading skeleton's lines: 2.2:1 - furniture, but visible furniture. */
    val skeleton: Int,
    /** Categorical series for a chart, each 3:1 on the worst surface. */
    val charts: List<Int>
) {
    companion object {

        /**
         * The floors, named once.
         *
         * They are a ladder on purpose: [Contrast.AA_TEXT] for text that has to
         * be read, [Contrast.AA_CAPTION] for text that qualifies a control,
         * [Contrast.AA_LARGE] for anything that is part of a control's shape, and
         * two quieter steps for decoration that still has to exist.
         */
        const val FLOOR_SECONDARY = Contrast.AA_TEXT
        const val FLOOR_MUTED = Contrast.AA_CAPTION
        const val FLOOR_CONTROL = Contrast.AA_LARGE
        const val FLOOR_SKELETON = 2.2
        const val FLOOR_DIVIDER = 2.0

        /**
         * The emphasis roles for one scheme.
         *
         * The backdrop is `surfaceContainerHighest` rather than `surface`: it is
         * the furthest the surface ramp moves from `onSurface` in *both* modes
         * (darker than white in the light theme, lighter than the page in the
         * dark one), so a role that clears its floor there clears it everywhere.
         */
        fun of(scheme: MaterialScheme): EmphasisPalette = of(
            onSurface = scheme.onSurface,
            onSurfaceVariant = scheme.onSurfaceVariant,
            surfaceContainerHighest = scheme.surfaceContainerHighest,
            outline = scheme.outline,
            outlineVariant = scheme.outlineVariant,
            primary = scheme.primary,
            secondary = scheme.secondary,
            tertiary = scheme.tertiary,
            error = scheme.error
        )

        /**
         * The same roles from the nine scheme values they are derived from.
         *
         * A narrow overload rather than a second constructor call with 37
         * arguments at the UI boundary: `toEmphasisColors` runs on every frame of
         * a theme cross-fade, and the roles it needs are exactly these.
         */
        fun of(
            onSurface: Int,
            onSurfaceVariant: Int,
            surfaceContainerHighest: Int,
            outline: Int,
            outlineVariant: Int,
            primary: Int,
            secondary: Int,
            tertiary: Int,
            error: Int
        ): EmphasisPalette {
            val worst = surfaceContainerHighest
            val strongest = onSurface

            /**
             * The quietest tone of [role] that still clears [floor].
             *
             * First strengthened, because a role that already fails cannot be
             * dimmed into compliance, then dimmed back down to the floor - which
             * is what gives the ladder its ordering instead of four roles that
             * all resolve to the same strengthened grey.
             */
            fun rung(role: Int, floor: Double): Int = Contrast.dimmedTo(
                foreground = Contrast.strengthenedTo(
                    foreground = role,
                    anchor = strongest,
                    background = worst,
                    floor = floor
                ),
                background = worst,
                floor = floor
            )

            return EmphasisPalette(
                secondary = rung(onSurfaceVariant, FLOOR_SECONDARY),
                muted = rung(onSurfaceVariant, FLOOR_MUTED),
                disabled = rung(onSurfaceVariant, FLOOR_CONTROL),
                // Anchored on `onSurfaceVariant`, not on `outline`: the outline is
                // itself only ~1.4:1 from the worst surface, so blending toward it
                // could never reach the 2:1 a drawn line needs - the first version
                // of this returned the anchor and measured 1.2:1, the same as the
                // alpha it replaced.
                divider = Contrast.strengthenedTo(
                    foreground = outlineVariant,
                    anchor = onSurfaceVariant,
                    background = worst,
                    floor = FLOOR_DIVIDER
                ),
                boundary = Contrast.strengthenedTo(
                    foreground = outline,
                    anchor = onSurfaceVariant,
                    background = worst,
                    floor = FLOOR_CONTROL
                ),
                track = rung(onSurfaceVariant, FLOOR_CONTROL),
                skeleton = rung(onSurfaceVariant, FLOOR_SKELETON),
                charts = listOf(primary, secondary, tertiary, error).map { series ->
                    Contrast.strengthenedTo(
                        foreground = series,
                        anchor = strongest,
                        background = worst,
                        floor = FLOOR_CONTROL
                    )
                }
            )
        }
    }
}
