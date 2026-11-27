package com.anisequel.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.anisequel.domain.color.EmphasisPalette

/**
 * The emphasis roles Material 3 has no slot for, as Compose colours.
 *
 * The arithmetic is [EmphasisPalette]'s - a plain data class of ARGB ints, so it
 * can be generated for any scheme and contrast-tested on the JVM. This file is
 * only the boundary, exactly like `DynamicThemeBuilder` is for the scheme itself.
 *
 * Read it through `AniSequelTheme.emphasis`, the same shape the app already uses
 * for `AniSequelTheme.statusColors`.
 */
@Immutable
data class EmphasisColors(
    /** Secondary body text: clears 4.5:1 on the worst surface the app draws on. */
    val secondary: Color,
    /** Captions, row under-lines and icons that carry meaning: 3.5:1. */
    val muted: Color,
    /** A control that is present but cannot be used: 3:1, not the 1.7:1 it was. */
    val disabled: Color,
    /** The hairline that separates two rows of one card: 2:1. */
    val divider: Color,
    /** The edge of a card that has to read as an edge: 3:1. */
    val boundary: Color,
    /** A progress bar's unfilled track: 3:1, so the track exists. */
    val track: Color,
    /** A loading skeleton's lines: 2.2:1 - furniture, but visible furniture. */
    val skeleton: Color,
    /** Categorical chart series, each 3:1 on the worst surface. */
    val charts: List<Color>
)

/**
 * Derives the emphasis roles from whatever scheme is current.
 *
 * Called on the *animated* scheme, so a theme change carries these roles with it
 * instead of leaving the page's captions on the previous palette for the length of
 * the cross-fade.
 */
internal fun ColorScheme.toEmphasisColors(): EmphasisColors {
    val palette = EmphasisPalette.of(
        onSurface = onSurface.toSrgbInt(),
        onSurfaceVariant = onSurfaceVariant.toSrgbInt(),
        // Every surface the role can be drawn on, so the floor is measured
        // against the one it reads worst on - not against the highest container,
        // which is not the furthest from onSurface in either mode.
        worstSurface = EmphasisPalette.worstSurface(
            onSurface.toSrgbInt(),
            listOf(
                surface.toSrgbInt(),
                surfaceDim.toSrgbInt(),
                surfaceBright.toSrgbInt(),
                surfaceContainer.toSrgbInt(),
                surfaceContainerHigh.toSrgbInt(),
                surfaceContainerHighest.toSrgbInt(),
                surfaceContainerLow.toSrgbInt(),
                surfaceContainerLowest.toSrgbInt()
            )
        ),
        outline = outline.toSrgbInt(),
        outlineVariant = outlineVariant.toSrgbInt(),
        primary = primary.toSrgbInt(),
        secondary = secondary.toSrgbInt(),
        tertiary = tertiary.toSrgbInt(),
        error = error.toSrgbInt()
    )
    return EmphasisColors(
        secondary = Color(palette.secondary),
        muted = Color(palette.muted),
        disabled = Color(palette.disabled),
        divider = Color(palette.divider),
        boundary = Color(palette.boundary),
        track = Color(palette.track),
        skeleton = Color(palette.skeleton),
        charts = palette.charts.map(::Color)
    )
}

/**
 * The ARGB int of an sRGB colour, without `androidx.compose.ui.graphics.toArgb`.
 *
 * `toArgb()` lives in the Android artifact and drags `android.graphics.Color`
 * into anything that calls it - and this wrapper is called from a composable on
 * every frame of a theme change, so the cheap path matters as well. Compose packs
 * an sRGB colour's ARGB into the high 32 bits of `value`; every colour this app
 * creates comes from `Color(Int)` or a `copy` of one, so the packing is
 * guaranteed.
 */
private fun Color.toSrgbInt(): Int = (value shr 32).toInt()

/**
 * The fallback outside [AniSequelTheme]: the brand light scheme's own roles.
 *
 * A default rather than a thrown error for the same reason `LocalStatusColors`
 * has one - a preview or a test that composes a control without the theme should
 * draw a legible colour, not crash.
 */
internal val LocalEmphasisColors = staticCompositionLocalOf {
    LightColorScheme.toEmphasisColors()
}
