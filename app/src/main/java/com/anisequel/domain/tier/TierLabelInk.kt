package com.anisequel.domain.tier

import kotlin.math.pow

/**
 * The two inks a tier gutter label can be drawn in.
 *
 * The dark one is true black rather than the export background's near-black: the
 * palette's indigo `#6366F1` is 4.70:1 against black and 4.04:1 against a
 * near-black, and 4.5:1 is the floor this whole choice exists to meet. "Nearly
 * black" is exactly the kind of almost that fails the check on one colour in
 * twelve.
 */
internal const val TIER_LABEL_INK_LIGHT = 0xFFFFFFFFL
internal const val TIER_LABEL_INK_DARK = 0xFF000000L

/**
 * The ink for a label drawn on a tier-row gutter of [colorArgb].
 *
 * The gutter used to be white text at every tier colour, which is right for the
 * saturated palette entries and wrong for every light one: `#FFC53D` amber is
 * 1.6:1 against white and the label was effectively invisible, and the grey
 * `#8E8E93` was 3.1:1 - below the 4.5:1 small-text floor, which is exactly the
 * size the count under the label is drawn at. A person who picks a pale colour
 * from the palette gets that colour for a reason, and the label still has to be
 * legible on it.
 *
 * So the ink is whichever of the two has the higher WCAG 2.1 contrast ratio
 * against the fill, ties going to white. The rule is one comparison rather than a
 * hand-tuned table so a custom colour added by the picker - or a palette entry
 * added later - is covered without anybody remembering to add it.
 *
 * Both the Compose board and both exporters call this, because the export is
 * meant to be the same picture as the screen: a fix that only changed the screen
 * would make every shared image disagree with it.
 */
internal fun tierLabelInk(colorArgb: Long): Long {
    val light = contrastRatio(colorArgb, TIER_LABEL_INK_LIGHT)
    val dark = contrastRatio(colorArgb, TIER_LABEL_INK_DARK)
    return if (light >= dark) TIER_LABEL_INK_LIGHT else TIER_LABEL_INK_DARK
}

/**
 * WCAG 2.1 relative luminance of a packed `0xAARRGGBB` colour, alpha ignored.
 *
 * Alpha is ignored on purpose: a tier fill is drawn opaque-ish (the board tints
 * it, the exporters draw it at 0.92-0.94), and the question the ink answers is
 * "what is behind the letter", not "how transparent is the paint".
 */
internal fun relativeLuminance(colorArgb: Long): Double {
    fun channel(shift: Int): Double {
        val value = ((colorArgb shr shift) and 0xFF).toDouble() / 255.0
        return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** WCAG 2.1 contrast ratio between two opaque colours, from 1:1 to 21:1. */
internal fun contrastRatio(first: Long, second: Long): Double {
    val a = relativeLuminance(first)
    val b = relativeLuminance(second)
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + 0.05) / (darker + 0.05)
}
