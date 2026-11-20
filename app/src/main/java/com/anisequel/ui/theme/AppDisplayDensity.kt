package com.anisequel.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.anisequel.domain.ui.DisplayDpi
import com.anisequel.domain.ui.DpiMode
import kotlin.math.roundToInt

/**
 * Draws [content] at [dpi] instead of the panel's own density, unless [mode] is
 * [DpiMode.AUTOMATIC], in which case the panel's density is left in force.
 *
 * ## Why the system font scale is carried across
 *
 * [Density] is two numbers, and only one of them is this setting's business. The
 * `fontScale` is the reader's system text size - an accessibility choice made in
 * Android's settings, not in this app - and building a fresh `Density` without it
 * would silently reset every text size to 100% for anyone who had raised it. So the
 * incoming scale is copied through and only the density is replaced.
 *
 * ## Why Automatic reads the incoming density instead of skipping this provider
 *
 * Leaving the provider out would work on first frame but not on the next: this is
 * the app's only composition root, and every screen is inside it. Resolving the
 * device's own dpi from the [LocalDensity] this provider replaces keeps one code
 * path and, crucially, keeps the font scale carried across in both modes. It also
 * keeps the first frame identical to the stored answer, so switching modes does not
 * resize the app a second time on launch.
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
fun AppDisplayDensity(
    dpi: Int,
    mode: DpiMode = DpiMode.MANUAL,
    content: @Composable () -> Unit
) {
    val base = LocalDensity.current
    // `base` is the panel's density: this provider is the only one in the app and
    // reads the value it is about to replace. The round trip through whole dpi is
    // exact for Android's density buckets (2.75 -> 440, 3.0 -> 480).
    val deviceDpi = (base.density * 160f).roundToInt()
    val density = remember(base.density, base.fontScale, dpi, mode) {
        Density(
            density = DisplayDpi.densityFor(mode, dpi, deviceDpi),
            fontScale = base.fontScale
        )
    }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}

/**
 * The density the panel reports, in dpi.
 *
 * Read from the platform rather than from [LocalDensity]: inside the app the local
 * is the density *we* chose, which in Manual mode is not the device's number at all.
 * A Settings page that says "your device reports X dpi" has to ask the platform.
 */
@Composable
internal fun rememberDeviceDensityDpi(): Int {
    val context = LocalContext.current
    return remember(context) { context.resources.displayMetrics.densityDpi }
}
