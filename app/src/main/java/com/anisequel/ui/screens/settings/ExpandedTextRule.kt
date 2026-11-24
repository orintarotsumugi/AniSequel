package com.anisequel.ui.screens.settings

/**
 * Whether a collapsed paragraph is worth a "Show more".
 *
 * The Settings pages used to collapse every paragraph at two lines, so a note
 * that ran three lines got a control that revealed the two words that had
 * wrapped onto the third. The reader paid a tap and a layout jump for a
 * sentence ending, which is the worst trade the control can make.
 *
 * A line can only be the *last* one when the line before it filled the width.
 * So if the full text has at least [collapsedLines] + 2 lines, the first hidden
 * line is full and expanding reveals at least one whole line of the paragraph.
 * A text with exactly one line beyond the preview cannot promise that, and is
 * shown in full instead: no toggle, nothing hidden.
 *
 * Kept as a plain function, away from Compose, because it is the rule rather
 * than the drawing, and the failure it prevents is arithmetic.
 */
internal fun hasFullLineToReveal(collapsedLines: Int, totalLines: Int): Boolean =
    collapsedLines > 0 && totalLines > collapsedLines + 1
