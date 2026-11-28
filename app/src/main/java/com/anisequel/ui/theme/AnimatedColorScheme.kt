package com.anisequel.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.anisequel.ui.components.expressive.ExpressiveMotion

/**
 * A colour scheme whose roles ease between schemes instead of swapping.
 *
 * Material You does not cut from the wallpaper palette to another one; the app
 * cross-fades. The same effect is what makes opening a title feel like the app
 * is *taking on* the show's colour rather than being handed a second theme: the
 * sheet is already animating in, and a hard colour cut in the same frames reads
 * as a flash.
 *
 * ## Why every role, not just `primary`
 *
 * The old builder only changed a handful of roles. Animating `primary` alone
 * would have made the mistake invisible for fifty milliseconds and then obvious
 * again as the containers and outlines snapped at the end. The scheme below is
 * the complete list, so what the reader sees arriving is the scheme they keep.
 *
 * ## Why this recomposes the theme while it runs
 *
 * `animateColorAsState(...).value` is read during composition, so each frame
 * produces a new [ColorScheme] and `MaterialExpressiveTheme` re-provides it. That
 * is the cost of a cross-fade: 48 short animations, once per colour change, on
 * the composables that actually read a role. It is bounded - a spring settles in
 * a few hundred milliseconds - and the alternative, animating only a draw-time
 * tint, cannot reach the M3 components that consume the roles.
 *
 * ## Two speeds, not one
 *
 * The accent roles move on [ExpressiveMotion.ThemeColorEffects] and the large
 * surfaces on the softer [ExpressiveMotion.SurfaceColorEffects]. That is what makes
 * a recolour read as the app taking on a colour rather than as a picture fading
 * from one palette to another: the buttons and chips arrive first and the page
 * settles under them, which is also the order the eye reads them in. Both are
 * ordinary springs, so an interrupted animation simply springs toward the new
 * target from wherever it is - no queue, no restart, no ordering to get wrong.
 *
 * A text role travels on the same spring as the surface it is read on, not on the
 * accent one. `onSurface` on the accent spring used to arrive a beat before
 * `surface` finished recolouring, so for that beat the page drew next-palette text
 * on the previous palette's background - the one moment in a theme change when
 * contrast was not the pair the scheme says it is. Pairing them keeps every
 * foreground/background pair in step for the whole animation.
 */
@Composable
internal fun rememberAnimatedColorScheme(target: ColorScheme): ColorScheme {
    // Both tokens are `snap()` under MotionStyle.INSTANT, so this stays a stable
    // sequence of composable calls whatever the motion setting is. A conditional
    // early return here would insert and remove 48 animation slots when the style
    // changed, resetting them to their targets.
    val spec: FiniteAnimationSpec<Color> = ExpressiveMotion.ThemeColorEffects
    val surfaceSpec: FiniteAnimationSpec<Color> = ExpressiveMotion.SurfaceColorEffects

    return target.copy(
        primary = target.primary.animated("theme_primary", spec),
        onPrimary = target.onPrimary.animated("theme_on_primary", spec),
        primaryContainer = target.primaryContainer.animated("theme_primary_container", spec),
        onPrimaryContainer = target.onPrimaryContainer.animated("theme_on_primary_container", spec),
        inversePrimary = target.inversePrimary.animated("theme_inverse_primary", spec),
        secondary = target.secondary.animated("theme_secondary", spec),
        onSecondary = target.onSecondary.animated("theme_on_secondary", spec),
        secondaryContainer = target.secondaryContainer.animated("theme_secondary_container", spec),
        onSecondaryContainer = target.onSecondaryContainer.animated("theme_on_secondary_container", spec),
        tertiary = target.tertiary.animated("theme_tertiary", spec),
        onTertiary = target.onTertiary.animated("theme_on_tertiary", spec),
        tertiaryContainer = target.tertiaryContainer.animated("theme_tertiary_container", spec),
        onTertiaryContainer = target.onTertiaryContainer.animated("theme_on_tertiary_container", spec),
        background = target.background.animated("theme_background", surfaceSpec),
        onBackground = target.onBackground.animated("theme_on_background", surfaceSpec),
        surface = target.surface.animated("theme_surface", surfaceSpec),
        onSurface = target.onSurface.animated("theme_on_surface", surfaceSpec),
        surfaceVariant = target.surfaceVariant.animated("theme_surface_variant", surfaceSpec),
        onSurfaceVariant = target.onSurfaceVariant.animated("theme_on_surface_variant", surfaceSpec),
        // The elevation tint is composited over the surfaces, so it rides the
        // surface spring: on the accent spring it used to finish recolouring a beat
        // before the surface it tints did, which shifted every elevated card's hue
        // for that beat.
        surfaceTint = target.surfaceTint.animated("theme_surface_tint", surfaceSpec),
        inverseSurface = target.inverseSurface.animated("theme_inverse_surface", surfaceSpec),
        inverseOnSurface = target.inverseOnSurface.animated("theme_inverse_on_surface", surfaceSpec),
        error = target.error.animated("theme_error", spec),
        onError = target.onError.animated("theme_on_error", spec),
        errorContainer = target.errorContainer.animated("theme_error_container", spec),
        onErrorContainer = target.onErrorContainer.animated("theme_on_error_container", spec),
        outline = target.outline.animated("theme_outline", surfaceSpec),
        outlineVariant = target.outlineVariant.animated("theme_outline_variant", surfaceSpec),
        scrim = target.scrim.animated("theme_scrim", surfaceSpec),
        surfaceBright = target.surfaceBright.animated("theme_surface_bright", surfaceSpec),
        surfaceContainer = target.surfaceContainer.animated("theme_surface_container", surfaceSpec),
        surfaceContainerHigh = target.surfaceContainerHigh.animated("theme_surface_container_high", surfaceSpec),
        surfaceContainerHighest = target.surfaceContainerHighest.animated("theme_surface_container_highest", surfaceSpec),
        surfaceContainerLow = target.surfaceContainerLow.animated("theme_surface_container_low", surfaceSpec),
        surfaceContainerLowest = target.surfaceContainerLowest.animated("theme_surface_container_lowest", surfaceSpec),
        surfaceDim = target.surfaceDim.animated("theme_surface_dim", surfaceSpec),
        // Fixed roles still move on the accent spring: a component that opts out
        // of the mode switch still sits next to primary/secondary content, so it
        // has to arrive with them.
        primaryFixed = target.primaryFixed.animated("theme_primary_fixed", spec),
        primaryFixedDim = target.primaryFixedDim.animated("theme_primary_fixed_dim", spec),
        onPrimaryFixed = target.onPrimaryFixed.animated("theme_on_primary_fixed", spec),
        onPrimaryFixedVariant = target.onPrimaryFixedVariant.animated("theme_on_primary_fixed_variant", spec),
        secondaryFixed = target.secondaryFixed.animated("theme_secondary_fixed", spec),
        secondaryFixedDim = target.secondaryFixedDim.animated("theme_secondary_fixed_dim", spec),
        onSecondaryFixed = target.onSecondaryFixed.animated("theme_on_secondary_fixed", spec),
        onSecondaryFixedVariant = target.onSecondaryFixedVariant.animated("theme_on_secondary_fixed_variant", spec),
        tertiaryFixed = target.tertiaryFixed.animated("theme_tertiary_fixed", spec),
        tertiaryFixedDim = target.tertiaryFixedDim.animated("theme_tertiary_fixed_dim", spec),
        onTertiaryFixed = target.onTertiaryFixed.animated("theme_on_tertiary_fixed", spec),
        onTertiaryFixedVariant = target.onTertiaryFixedVariant.animated("theme_on_tertiary_fixed_variant", spec)
    )
}

@Composable
private fun Color.animated(
    label: String,
    spec: FiniteAnimationSpec<Color>
): Color = animateColorAsState(
    targetValue = this,
    animationSpec = spec,
    label = label
).value
