package com.anisequel.domain.color

import kotlin.math.pow

/**
 * WCAG 2.1 contrast, and the two moves a palette needs to use it.
 *
 * ## Why this is a domain object and not a helper in the theme
 *
 * The app's colours arrive from four directions at once - the brand scheme, a
 * generated one from an anime cover, a picked canvas colour and the platform's
 * dynamic palette - so "is this pair legible" has to be answerable for *any*
 * scheme, not for the two the designer checked. Every role in
 * [EmphasisPalette] is derived through this file, and every one of them is
 * asserted on the JVM over a spread of seeds, which is only possible because
 * nothing here touches Compose.
 *
 * ## Why blending, and not another alpha
 *
 * The old UI dimmed text by handing `onSurfaceVariant` an alpha - 0.62 for a
 * disabled chip, 0.45 for a progress track, 0.38 for an icon, 0.3 for a
 * skeleton. Alpha is not a contrast unit: `onSurfaceVariant` is defined against
 * `surface`, and every step down the alpha ladder throws away the headroom it
 * had. In the light theme the secondary text is a mid-blue-grey (#51637A);
 * 0.62 of it over white is 2.7:1, so the *disabled* text was the least legible
 * label on the page and a 0.3 skeleton was 1.5:1 - a shimmer of nothing.
 *
 * The functions below blend toward a background or toward a stronger anchor
 * colour until a *measured* ratio is met, so the answer is a colour that clears
 * the floor on the surface it is drawn on, in whichever scheme is current.
 */
object Contrast {

    /** WCAG AA for body text, and for any text under 18.66sp bold / 24sp. */
    const val AA_TEXT: Double = 4.5

    /** WCAG AA for large text and for the non-text parts of a control. */
    const val AA_LARGE: Double = 3.0

    /**
     * The floor used for a quieter caption or an icon that carries meaning.
     *
     * Above [AA_LARGE] because a caption is frequently the *only* label on a
     * control - the under-line of a settings row, the byte count under a cache
     * chip - and AA's large-text exemption assumes 24sp text, which none of this
     * is.
     */
    const val AA_CAPTION: Double = 3.5

    /** Relative luminance of an opaque ARGB colour, per WCAG 2.1. */
    fun relativeLuminance(argb: Int): Double {
        fun channel(component: Int): Double {
            val c = component / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel((argb shr 16) and 0xFF) +
            0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }

    /**
     * The contrast ratio between two opaque colours, 1.0 to 21.0.
     *
     * Symmetric, as WCAG defines it: the lighter colour is always the numerator,
     * so a caller never has to know which of the pair is the background.
     */
    fun ratio(first: Int, second: Int): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /**
     * `foreground` drawn over `background` at its own alpha, as one opaque colour.
     *
     * The compositing every `Color.copy(alpha = …)` in the UI performs on the GPU
     * before the reader's eye sees it, done here so a ratio can be measured on
     * what is actually on screen.
     */
    fun compositeOver(foreground: Int, background: Int): Int {
        val alpha = ((foreground ushr 24) and 0xFF) / 255.0
        if (alpha >= 1.0) return foreground
        fun channel(shift: Int): Int {
            val fg = (foreground shr shift) and 0xFF
            val bg = (background shr shift) and 0xFF
            return (fg * alpha + bg * (1.0 - alpha)).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * `foreground` blended toward [background] as far as [floor] allows.
     *
     * The quiet end of a hierarchy: the result is the *dimmest* colour that still
     * clears the floor against the surface it is drawn on. A colour that already
     * sits on the floor is returned untouched, and one that is dimmer than the
     * floor already cannot be rescued by this direction - use [strengthenedTo]
     * for that.
     */
    fun dimmedTo(foreground: Int, background: Int, floor: Double): Int {
        if (ratio(foreground, background) < floor) return foreground
        // Largest blend that still clears the floor. Scanned rather than solved:
        // the ratio is smooth and monotonic in the blend, so 5% steps land within
        // a channel of the exact answer, and the search stays readable.
        var best = foreground
        var step = 0.05
        while (step <= 0.90) {
            val candidate = blend(foreground, background, step)
            if (ratio(candidate, background) < floor) break
            best = candidate
            step += 0.05
        }
        return best
    }

    /**
     * `foreground` blended toward [anchor] until it clears [floor] against [background].
     *
     * The loud end: used for a role whose declared colour was chosen against one
     * surface and then drawn on a darker (or lighter) container, which is how
     * `onSurfaceVariant` ended up at 4.4:1 on a card. Returns [foreground]
     * unchanged when it already clears the floor, so a competent scheme is never
     * altered - and [anchor] when even a full blend cannot reach it, because
     * "as strong as this palette gets" is a better answer than an invisible role.
     */
    fun strengthenedTo(foreground: Int, anchor: Int, background: Int, floor: Double): Int {
        if (ratio(foreground, background) >= floor) return foreground
        var step = 0.05
        while (step < 1.0) {
            val candidate = blend(foreground, anchor, step)
            if (ratio(candidate, background) >= floor) return candidate
            step += 0.05
        }
        return anchor
    }

    /** Linear sRGB blend, `fraction` of the way from [from] to [to]. */
    fun blend(from: Int, to: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
