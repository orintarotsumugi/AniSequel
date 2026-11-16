package com.anisequel.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.anisequel.domain.ui.DisplayDpi

/**
 * Draws [content] at [dpi] instead of the panel's own density.
 *
 * ## Why the system font scale is carried across
 *
 * [Density] is two numbers, and only one of them is this setting's business. The
 * `fontScale` is the reader's system text size - an accessibility choice made in
 * Android's settings, not in this app - and building a fresh `Density` without it
 * would silently reset every text size to 100% for anyone who had raised it. So the
 * incoming scale is copied through and only the density is replaced.
 *
 * ## Why this sits inside the theme rather than around the whole Activity
 *
 * It has to be above everything the app draws, or half the page would be at one
 * density and half at another. The theme's content is that boundary: the Settings
 * page, the navigation bar and every sheet are all inside it, and a density provided
 * there also re-measures the typography the theme just built, because a `sp` is
 * resolved against the density in scope when it is drawn.
 */
@Composable
fun AppDisplayDensity(dpi: Int, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val density = remember(base.density, base.fontScale, dpi) {
        Density(density = DisplayDpi.density(dpi), fontScale = base.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
