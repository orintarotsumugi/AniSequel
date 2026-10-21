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
 * is the cost of a cross-fade: 36 short animations, once per colour change, on
 * the composables that actually read a role. It is bounded - a spring settles in
 * a few hundred milliseconds - and the alternative, animating only a draw-time
 * tint, cannot reach the M3 components that consume the roles.
 */
@Composable
internal fun rememberAnimatedColorScheme(target: ColorScheme): ColorScheme {
    // The token itself is `snap()` under MotionStyle.INSTANT, so this stays a
    // stable sequence of composable calls whatever the motion setting is. A
    // conditional early return here would insert and remove 36 animation slots
    // when the style changed, resetting them to their targets.
    val spec: FiniteAnimationSpec<Color> = ExpressiveMotion.ThemeColorEffects

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
        background = target.background.animated("theme_background", spec),
        onBackground = target.onBackground.animated("theme_on_background", spec),
        surface = target.surface.animated("theme_surface", spec),
        onSurface = target.onSurface.animated("theme_on_surface", spec),
        surfaceVariant = target.surfaceVariant.animated("theme_surface_variant", spec),
        onSurfaceVariant = target.onSurfaceVariant.animated("theme_on_surface_variant", spec),
        surfaceTint = target.surfaceTint.animated("theme_surface_tint", spec),
        inverseSurface = target.inverseSurface.animated("theme_inverse_surface", spec),
        inverseOnSurface = target.inverseOnSurface.animated("theme_inverse_on_surface", spec),
        error = target.error.animated("theme_error", spec),
        onError = target.onError.animated("theme_on_error", spec),
        errorContainer = target.errorContainer.animated("theme_error_container", spec),
        onErrorContainer = target.onErrorContainer.animated("theme_on_error_container", spec),
        outline = target.outline.animated("theme_outline", spec),
        outlineVariant = target.outlineVariant.animated("theme_outline_variant", spec),
        scrim = target.scrim.animated("theme_scrim", spec),
        surfaceBright = target.surfaceBright.animated("theme_surface_bright", spec),
        surfaceContainer = target.surfaceContainer.animated("theme_surface_container", spec),
        surfaceContainerHigh = target.surfaceContainerHigh.animated("theme_surface_container_high", spec),
        surfaceContainerHighest = target.surfaceContainerHighest.animated("theme_surface_container_highest", spec),
        surfaceContainerLow = target.surfaceContainerLow.animated("theme_surface_container_low", spec),
        surfaceContainerLowest = target.surfaceContainerLowest.animated("theme_surface_container_lowest", spec),
        surfaceDim = target.surfaceDim.animated("theme_surface_dim", spec)
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
