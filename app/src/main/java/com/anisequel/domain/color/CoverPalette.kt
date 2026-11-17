package com.anisequel.domain.color

/**
 * What one cover contributes to the theme: its main hue and, when it has one, a
 * second hue that belongs to the same picture.
 *
 * [seed] is the colour the whole app is painted from. [accent] is not a second theme
 * seed - it is the colour a gradient can pair with it so a hero reads as artwork
 * rather than as one flat wash. It is null for a cover with only one usable hue,
 * which is the honest answer and not a failure.
 */
data class CoverPalette(
    val seed: Int,
    val accent: Int?
)
