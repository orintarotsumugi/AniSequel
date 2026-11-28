package com.anisequel.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.anisequel.domain.color.Hct
import com.anisequel.domain.color.TonalPalette

/**
 * Fills in the twelve fixed roles a hand-written scheme leaves at Material's purple.
 *
 * `lightColorScheme`/`darkColorScheme` do not leave an unnamed role empty - they
 * default it to Material's baseline, and the fixed set (`primaryFixed`,
 * `primaryFixedDim` and their on-colours, and the secondary and tertiary families) is
 * the one where that default is a different *hue*, not a different shade: the
 * baseline is a purple palette at tones 90/80/10/30, while the app's brand scheme is
 * blue and Sakura's is pink. A date picker's selected day, a snackbar action or a
 * tooltip therefore drew purple on a themed page, which is the same failure
 * `MaterialColorSchemes` fixes for cover- and seed-derived schemes by deriving the
 * twelve from the seed.
 *
 * The tones are Material's and identical in light and dark on purpose: a fixed role
 * is the colour a component keeps when the rest of the scheme flips. Because the
 * accents here are hand-picked rather than sampled from a toner, the roles are
 * derived from the scheme's own three accent values.
 */
internal fun ColorScheme.withFixedRoles(): ColorScheme {
    val primary = fixedPalette(primary)
    val secondary = fixedPalette(secondary)
    val tertiary = fixedPalette(tertiary)
    // The tone solves return ARGB, and a `ColorScheme` holds `Color`; passing the
    // raw ints does not compile, which is the only reason this wraps them.
    return copy(
        primaryFixed = Color(primary.tone(90)),
        primaryFixedDim = Color(primary.tone(80)),
        onPrimaryFixed = Color(primary.tone(10)),
        onPrimaryFixedVariant = Color(primary.tone(30)),
        secondaryFixed = Color(secondary.tone(90)),
        secondaryFixedDim = Color(secondary.tone(80)),
        onSecondaryFixed = Color(secondary.tone(10)),
        onSecondaryFixedVariant = Color(secondary.tone(30)),
        tertiaryFixed = Color(tertiary.tone(90)),
        tertiaryFixedDim = Color(tertiary.tone(80)),
        onTertiaryFixed = Color(tertiary.tone(10)),
        onTertiaryFixedVariant = Color(tertiary.tone(30))
    )
}

/**
 * A tonal palette that can honour the fixed tones.
 *
 * The hand-picked accents sit at whatever chroma looked right at one tone, and a
 * near-grey one would produce a fixed fill indistinguishable from the surface around
 * it. These are the same bounds `MaterialColorSchemes` applies to a generated
 * tertiary, so a hand-authored palette and a cover-derived one agree on how colourful
 * a fixed role may be.
 */
private fun fixedPalette(seed: Color): TonalPalette {
    val hct = Hct.fromInt(seed.toArgb())
    return TonalPalette(hct.hue, hct.chroma.coerceIn(MIN_FIXED_CHROMA, MAX_FIXED_CHROMA))
}

/** Below this a fixed fill stops reading as the palette's own colour. */
private const val MIN_FIXED_CHROMA: Double = 24.0

/** Above this a fixed fill outshouts the primary it belongs to. */
private const val MAX_FIXED_CHROMA: Double = 60.0
